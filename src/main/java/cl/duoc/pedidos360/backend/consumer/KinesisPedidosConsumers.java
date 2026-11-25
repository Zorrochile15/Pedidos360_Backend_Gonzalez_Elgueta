package cl.duoc.pedidos360.backend.consumer;

import cl.duoc.pedidos360.backend.dto.EventoStreamDto;
import cl.duoc.pedidos360.backend.service.KinesisProducerService;
import cl.duoc.pedidos360.backend.service.StreamAdminService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.*;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.processor.SingleStreamTracker;
import software.amazon.kinesis.retrieval.KinesisClientRecord;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class KinesisPedidosConsumers {

    private static final Logger log =
            LoggerFactory.getLogger(KinesisPedidosConsumers.class);

    private final KinesisAsyncClient kinesisClient;
    private final DynamoDbAsyncClient dynamoClient;
    private final CloudWatchAsyncClient cloudWatchClient;
    private final KinesisProducerService producerService;
    private final StreamAdminService adminService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.kinesis.stream.pedidos}")
    private String streamPedidos;

    @Value("${aws.kinesis.consumer.grupo-auditoria}")
    private String grupoAuditoria;

    @Value("${aws.kinesis.consumer.grupo-metricas}")
    private String grupoMetricas;

    private final Map<String, Scheduler> schedulers =
            new ConcurrentHashMap<>();

    private final Set<String> procesadosAuditoria =
            ConcurrentHashMap.newKeySet();

    private final Set<String> procesadosMetricas =
            ConcurrentHashMap.newKeySet();

    public KinesisPedidosConsumers(
            KinesisAsyncClient kinesisClient,
            DynamoDbAsyncClient dynamoClient,
            CloudWatchAsyncClient cloudWatchClient,
            KinesisProducerService producerService,
            StreamAdminService adminService) {

        this.kinesisClient = kinesisClient;
        this.dynamoClient = dynamoClient;
        this.cloudWatchClient = cloudWatchClient;
        this.producerService = producerService;
        this.adminService = adminService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarConsumidores() {

        iniciarConsumidor(
                grupoAuditoria, procesadosAuditoria);

        iniciarConsumidor(
                grupoMetricas, procesadosMetricas);
    }

    private void iniciarConsumidor(
            String grupo,
            Set<String> procesados) {

        String workerId = grupo + "-" + UUID.randomUUID();

        // Posicion inicial cuando no existe un checkpoint
        SingleStreamTracker streamTracker =
                new SingleStreamTracker(
                        streamPedidos,
                        InitialPositionInStreamExtended
                                .newInitialPosition(
                                        InitialPositionInStream.TRIM_HORIZON)
                );

        ConfigsBuilder configs = new ConfigsBuilder(
                streamTracker,
                grupo,
                kinesisClient,
                dynamoClient,
                cloudWatchClient,
                workerId,
                () -> new ProcesadorPedidos(
                        grupo, workerId, procesados)
        );

        Scheduler scheduler = new Scheduler(
                configs.checkpointConfig(),
                configs.coordinatorConfig(),
                configs.leaseManagementConfig(),
                configs.lifecycleConfig(),
                configs.metricsConfig(),
                configs.processorConfig(),
                configs.retrievalConfig()
                        .retrievalSpecificConfig(
                                new PollingConfig(
                                        streamPedidos, kinesisClient)
                                        .maxRecords(25))
        );

        schedulers.put(grupo, scheduler);

        Thread thread = new Thread(scheduler);
        thread.setName(workerId);
        thread.setDaemon(true);
        thread.start();

        log.info(
                "[{}] Worker KCL iniciado -> worker={}, stream={}",
                grupo, workerId, streamPedidos);
    }

    private class ProcesadorPedidos
            implements ShardRecordProcessor {

        private final String grupo;
        private final String workerId;
        private final Set<String> procesados;

        private String shardId;
        private boolean bloqueado = false;

        public ProcesadorPedidos(
                String grupo,
                String workerId,
                Set<String> procesados) {

            this.grupo = grupo;
            this.workerId = workerId;
            this.procesados = procesados;
        }

        @Override
        public void initialize(InitializationInput input) {

            shardId = input.shardId();

            log.info(
                    "[{}] SHARD ASIGNADO -> worker={}, shard={}, checkpoint={}",
                    grupo,
                    workerId,
                    shardId,
                    input.extendedSequenceNumber());
        }

        @Override
        public void processRecords(ProcessRecordsInput input) {

            if (bloqueado || input.records().isEmpty()) {
                return;
            }

            long lagMs = input.millisBehindLatest() != null
                    ? input.millisBehindLatest() : 0L;

            try {

                String ultimaSecuencia = null;

                for (KinesisClientRecord record : input.records()) {

                    procesarRegistro(
                            grupo,
                            workerId,
                            shardId,
                            record,
                            procesados);

                    ultimaSecuencia = record.sequenceNumber();
                }

                // Checkpoint persistente en DynamoDB
                guardarCheckpoint(input.checkpointer());

                log.info(
                        "[{}] CHECKPOINT DYNAMODB -> worker={}, shard={}, seq={}, lagMs={}",
                        grupo,
                        workerId,
                        shardId,
                        ultimaSecuencia,
                        lagMs);

                // Registro para el microservicio administrador
                try {

                    adminService.registrarCheckpoint(
                            grupo,
                            shardId,
                            ultimaSecuencia,
                            lagMs);

                } catch (Exception e) {

                    log.warn(
                            "Error actualizando administrador: {}",
                            e.getMessage());
                }

            } catch (ShutdownException e) {

                bloqueado = true;

                log.warn(
                        "[{}] Worker perdio el lease -> shard={}",
                        grupo, shardId);

            } catch (Exception e) {

                bloqueado = true;

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }

                log.error(
                        "[{}] Procesamiento detenido sin avanzar checkpoint -> shard={}",
                        grupo, shardId, e);

                Scheduler scheduler = schedulers.get(grupo);

                if (scheduler != null) {
                    scheduler.startGracefulShutdown();
                }
            }
        }

        @Override
        public void leaseLost(LeaseLostInput input) {

            bloqueado = true;

            log.warn(
                    "[{}] SHARD LIBERADO -> worker={}, shard={}",
                    grupo, workerId, shardId);
        }

        @Override
        public void shardEnded(ShardEndedInput input) {

            if (bloqueado) {
                throw new IllegalStateException(
                        "Shard con procesamiento incompleto");
            }

            try {

                guardarCheckpoint(input.checkpointer());

                log.info(
                        "[{}] SHARD FINALIZADO -> shard={}",
                        grupo, shardId);

            } catch (Exception e) {

                throw new IllegalStateException(
                        "No se pudo finalizar el shard", e);
            }
        }

        @Override
        public void shutdownRequested(
                ShutdownRequestedInput input) {

            if (!bloqueado) {

                try {

                    guardarCheckpoint(input.checkpointer());

                } catch (Exception e) {

                    log.warn(
                            "Error guardando checkpoint final", e);
                }
            }

            log.info(
                    "[{}] Worker detenido -> worker={}, shard={}",
                    grupo, workerId, shardId);
        }
    }

    private void guardarCheckpoint(
            RecordProcessorCheckpointer checkpointer)
            throws Exception {

        for (int intento = 1; intento <= 3; intento++) {

            try {

                checkpointer.checkpoint();
                return;

            } catch (ShutdownException e) {

                throw e;

            } catch (Exception e) {

                if (intento == 3) {
                    throw e;
                }

                Thread.sleep(500L * intento);
            }
        }
    }

    private void procesarRegistro(
            String grupo,
            String workerId,
            String shardId,
            KinesisClientRecord record,
            Set<String> procesados)
            throws InterruptedException {

        String payload = StandardCharsets.UTF_8
                .decode(record.data().asReadOnlyBuffer())
                .toString();

        String seq = record.sequenceNumber();

        EventoStreamDto dto;

        try {

            dto = objectMapper.readValue(
                    payload, EventoStreamDto.class);

        } catch (JsonProcessingException e) {

            log.error(
                    "[{}] JSON corrupto enviado a fallidos -> seq={}",
                    grupo, seq);

            producerService.enviarAStreamFallidos(
                    payload,
                    seq,
                    "JSON malformado: " + e.getMessage(),
                    1);

            return;
        }

        if (dto == null) {

            producerService.enviarAStreamFallidos(
                    payload, seq, "Evento nulo", 1);

            return;
        }

        String eventoId = dto.getEventoId() != null
                ? dto.getEventoId() : seq;

        synchronized (procesados) {

            // Control local de eventos duplicados
            if (procesados.contains(eventoId)) {

                log.warn(
                        "[{}] Evento duplicado ignorado -> id={}, shard={}",
                        grupo, eventoId, shardId);

                return;
            }

            // Validacion de errores fatales
            if (dto.getCantidad() == null
                    || dto.getCantidad() <= 0
                    || "ERROR_FATAL".equalsIgnoreCase(
                            dto.getEstado())) {

                log.error(
                        "[{}] Error irrecuperable -> id={}, intento=1",
                        grupo, eventoId);

                producerService.enviarAStreamFallidos(
                        payload,
                        eventoId,
                        "Validacion fallida: cantidad invalida o ERROR_FATAL",
                        1);

                procesados.add(eventoId);

                return;
            }

            // Reintentos ante errores transitorios
            int intentos = 0;
            boolean exito = false;

            while (intentos < 3 && !exito) {

                intentos++;

                try {

                    if ("ERROR_TRANSITORIO".equalsIgnoreCase(
                            dto.getEstado()) && intentos < 3) {

                        throw new RuntimeException(
                                "Timeout transitorio simulado");
                    }

                    log.info(
                            "[{}] Evento procesado -> id={}, pedidoId={}, worker={}, shard={}, intento={}, seq={}",
                            grupo,
                            eventoId,
                            dto.getPedidoId(),
                            workerId,
                            shardId,
                            intentos,
                            seq);

                    exito = true;

                } catch (Exception e) {

                    log.warn(
                            "[{}] Reintentando error transitorio -> id={}, intento={}, causa={}",
                            grupo,
                            eventoId,
                            intentos,
                            e.getMessage());

                    if (intentos < 3) {
                        Thread.sleep(200L * intentos);
                    }
                }
            }

            if (!exito) {

                producerService.enviarAStreamFallidos(
                        payload,
                        eventoId,
                        "Agoto reintentos transitorios",
                        intentos);
            }

            procesados.add(eventoId);
        }
    }

    @PreDestroy
    public void detenerConsumidores() {

        for (Scheduler scheduler : schedulers.values()) {

            try {

                scheduler.startGracefulShutdown()
                        .get(20, TimeUnit.SECONDS);

            } catch (Exception e) {

                scheduler.shutdown();
            }
        }
    }
}