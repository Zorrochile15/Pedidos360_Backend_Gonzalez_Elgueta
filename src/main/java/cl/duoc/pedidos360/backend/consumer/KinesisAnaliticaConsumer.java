package cl.duoc.pedidos360.backend.consumer;

import cl.duoc.pedidos360.backend.dto.EventoStreamDto;
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
public class KinesisAnaliticaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KinesisAnaliticaConsumer.class);

    private final KinesisClient kinesisClient;
    private final StreamAdminService adminService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.kinesis.stream.analitica}")
    private String streamAnalitica;

    @Value("${aws.kinesis.consumer.grupo-inventario}")
    private String grupoInventario;

    private final Map<String, String> iteradores = new ConcurrentHashMap<>();
    private final Set<String> procesados = ConcurrentHashMap.newKeySet();

    public KinesisAnaliticaConsumer(KinesisClient kinesisClient, StreamAdminService adminService) {
        this.kinesisClient = kinesisClient;
        this.adminService = adminService;
    }

    @Scheduled(fixedDelay = 5000)
    public void consumirAnaliticaInventario() {
        try {
            List<Shard> shards = kinesisClient.listShards(ListShardsRequest.builder().streamName(streamAnalitica).build()).shards();
            for (Shard shard : shards) {
                String shardId = shard.shardId();
                String iterator = iteradores.computeIfAbsent(shardId, id -> kinesisClient.getShardIterator(
                        GetShardIteratorRequest.builder()
                                .streamName(streamAnalitica)
                                .shardId(id)
                                .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
                                .build()).shardIterator());

                GetRecordsResponse response = kinesisClient.getRecords(GetRecordsRequest.builder()
                        .shardIterator(iterator)
                        .limit(25)
                        .build());

                iteradores.put(shardId, response.nextShardIterator());
                long lagMs = response.millisBehindLatest() != null ? response.millisBehindLatest() : 0L;

                for (Record record : response.records()) {
                    EventoStreamDto dto = objectMapper.readValue(record.data().asUtf8String(), EventoStreamDto.class);
                    if (procesados.add(dto.getEventoId())) {
                        log.info("[{}] Analitica procesada -> id={}, localId={}, cantidad={}, shard={}",
                                grupoInventario, dto.getEventoId(), dto.getLocalId(), dto.getCantidad(), shardId);
                    }
                    adminService.registrarCheckpoint(grupoInventario, shardId, record.sequenceNumber(), lagMs);
                }
            }
        } catch (ExpiredIteratorException e) {
            iteradores.clear();
        } catch (Exception e) {
            log.debug("Esperando stream analitica: {}", e.getMessage());
        }
    }
}