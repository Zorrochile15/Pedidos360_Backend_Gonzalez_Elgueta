package cl.duoc.pedidos360.backend.consumer;

import cl.duoc.pedidos360.backend.dto.EventoStreamDto;
import cl.duoc.pedidos360.backend.service.KinesisProducerService;
import cl.duoc.pedidos360.backend.service.StreamAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ExpiredIteratorException;
import software.amazon.awssdk.services.kinesis.model.GetRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorRequest;
import software.amazon.awssdk.services.kinesis.model.ListShardsRequest;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KinesisPedidosConsumers {

    private static final Logger log = LoggerFactory.getLogger(KinesisPedidosConsumers.class);

    private final KinesisClient kinesisClient;
    private final KinesisProducerService producerService;
    private final StreamAdminService adminService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.kinesis.stream.pedidos}")
    private String streamPedidos;

    @Value("${aws.kinesis.consumer.grupo-auditoria}")
    private String grupoAuditoria;

    @Value("${aws.kinesis.consumer.grupo-metricas}")
    private String grupoMetricas;

    private final Map<String, String> iteradoresAuditoria = new ConcurrentHashMap<>();
    private final Map<String, String> iteradoresMetricas = new ConcurrentHashMap<>();

    private final Set<String> procesadosAuditoria = ConcurrentHashMap.newKeySet();
    private final Set<String> procesadosMetricas = ConcurrentHashMap.newKeySet();

    public KinesisPedidosConsumers(KinesisClient kinesisClient, KinesisProducerService producerService, StreamAdminService adminService) {
        this.kinesisClient = kinesisClient;
        this.producerService = producerService;
        this.adminService = adminService;
    }

    @Scheduled(fixedDelay = 4000)
    public void consumirGrupoAuditoria() {
        procesarStreamPorGrupo(streamPedidos, grupoAuditoria, iteradoresAuditoria, procesadosAuditoria);
    }

    @Scheduled(fixedDelay = 4000)
    public void consumirGrupoMetricas() {
        procesarStreamPorGrupo(streamPedidos, grupoMetricas, iteradoresMetricas, procesadosMetricas);
    }

    private void procesarStreamPorGrupo(String streamName, String consumerGroup, Map<String, String> iteradores, Set<String> procesados) {
        try {
            List<Shard> shards = kinesisClient.listShards(ListShardsRequest.builder().streamName(streamName).build()).shards();
            for (Shard shard : shards) {
                String shardId = shard.shardId();
                String iterator = iteradores.computeIfAbsent(shardId, id -> obtenerIteradorInicial(streamName, id));
                if (iterator == null) continue;

                GetRecordsResponse response = kinesisClient.getRecords(GetRecordsRequest.builder()
                        .shardIterator(iterator)
                        .limit(25)
                        .build());

                iteradores.put(shardId, response.nextShardIterator());
                long lagMs = response.millisBehindLatest() != null ? response.millisBehindLatest() : 0L;

                for (Record record : response.records()) {
                    procesarRegistroConCheckpoint(consumerGroup, shardId, record, procesados, lagMs);
                }
            }
        } catch (ExpiredIteratorException e) {
            iteradores.clear();
        } catch (Exception e) {
            log.debug("Esperando stream activo {}: {}", streamName, e.getMessage());
        }
    }

    private void procesarRegistroConCheckpoint(String consumerGroup, String shardId, Record record, Set<String> procesados, long lagMs) {
        String payload = record.data().asUtf8String();
        String seq = record.sequenceNumber();

        try {
            EventoStreamDto dto = objectMapper.readValue(payload, EventoStreamDto.class);
            String eventoId = dto.getEventoId() != null ? dto.getEventoId() : seq;

            if (!procesados.add(eventoId)) {
                log.warn("[{}] Evento duplicado ignorado -> id={}, shard={}", consumerGroup, eventoId, shardId);
                adminService.registrarCheckpoint(consumerGroup, shardId, seq, lagMs);
                return;
            }

            if (dto.getCantidad() == null || dto.getCantidad() <= 0 || "ERROR_FATAL".equalsIgnoreCase(dto.getEstado())) {
                log.error("[{}] Error irrecuperable de validacion -> id={}, intento=1, causa=Datos invalidos o estado ERROR_FATAL", consumerGroup, eventoId);
                producerService.enviarAStreamFallidos(payload, eventoId, "Validacion fallida: cantidad invalida o ERROR_FATAL", 1);
                adminService.registrarCheckpoint(consumerGroup, shardId, seq, lagMs);
                return;
            }

            int intentos = 0;
            boolean exito = false;
            while (intentos < 3 && !exito) {
                intentos++;
                try {
                    if ("ERROR_TRANSITORIO".equalsIgnoreCase(dto.getEstado()) && intentos < 3) {
                        throw new RuntimeException("Timeout transitorio simulado");
                    }
                    log.info("[{}] Evento procesado -> id={}, pedidoId={}, shard={}, intento={}, seq={}",
                            consumerGroup, eventoId, dto.getPedidoId(), shardId, intentos, seq);
                    exito = true;
                } catch (Exception ex) {
                    log.warn("[{}] Reintentando error transitorio -> id={}, intento={}, causa={}",
                            consumerGroup, eventoId, intentos, ex.getMessage());
                    Thread.sleep(200L * intentos);
                }
            }

            if (!exito) {
                producerService.enviarAStreamFallidos(payload, eventoId, "Agoto reintentos transitorios", intentos);
            }

            adminService.registrarCheckpoint(consumerGroup, shardId, seq, lagMs);
        } catch (Exception e) {
            log.error("[{}] JSON corrupto enviado a fallidos -> seq={}, intento=1, causa={}", consumerGroup, seq, e.getMessage());
            producerService.enviarAStreamFallidos(payload, seq, "JSON malformado: " + e.getMessage(), 1);
            adminService.registrarCheckpoint(consumerGroup, shardId, seq, lagMs);
        }
    }

    private String obtenerIteradorInicial(String streamName, String shardId) {
        try {
            return kinesisClient.getShardIterator(GetShardIteratorRequest.builder()
                    .streamName(streamName)
                    .shardId(shardId)
                    .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
                    .build()).shardIterator();
        } catch (Exception e) {
            return null;
        }
    }
}