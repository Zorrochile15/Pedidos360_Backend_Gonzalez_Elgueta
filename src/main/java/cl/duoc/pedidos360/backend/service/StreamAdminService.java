package cl.duoc.pedidos360.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class StreamAdminService {

    private final KinesisClient kinesisClient;
    private final DynamoDbAsyncClient dynamoClient;

    private final Map<String, Long> retrasoPorGrupoMs =
            new ConcurrentHashMap<>();

    @Value("${aws.kinesis.consumer.grupo-auditoria}")
    private String grupoAuditoria;

    @Value("${aws.kinesis.consumer.grupo-metricas}")
    private String grupoMetricas;

    @Value("${aws.kinesis.consumer.grupo-inventario}")
    private String grupoInventario;

    public StreamAdminService(
            KinesisClient kinesisClient,
            DynamoDbAsyncClient dynamoClient) {

        this.kinesisClient = kinesisClient;
        this.dynamoClient = dynamoClient;
    }

    public void crearStream(String nombre, int shards) {

        kinesisClient.createStream(
                CreateStreamRequest.builder()
                        .streamName(nombre)
                        .shardCount(shards)
                        .streamModeDetails(
                                StreamModeDetails.builder()
                                        .streamMode(StreamMode.PROVISIONED)
                                        .build())
                        .build());
    }

    public List<String> listarStreams() {

        return kinesisClient.listStreams().streamNames();
    }

    public Map<String, Object> describirStream(String nombre) {

        StreamDescriptionSummary summary =
                kinesisClient.describeStreamSummary(
                        DescribeStreamSummaryRequest.builder()
                                .streamName(nombre)
                                .build())
                        .streamDescriptionSummary();

        List<Shard> shards = kinesisClient.listShards(
                ListShardsRequest.builder()
                        .streamName(nombre)
                        .build()).shards();

        Map<String, Object> info = new HashMap<>();

        info.put("streamName", summary.streamName());
        info.put("status", summary.streamStatusAsString());
        info.put("openShardCount", summary.openShardCount());
        info.put("retentionPeriodHours",
                summary.retentionPeriodHours());
        info.put("streamMode",
                summary.streamModeDetails().streamModeAsString());

        info.put("shardIds",
                shards.stream().map(Shard::shardId).toList());

        return info;
    }

    public void modificarShards(
            String nombre,
            int nuevoConteoShards) {

        kinesisClient.updateShardCount(
                UpdateShardCountRequest.builder()
                        .streamName(nombre)
                        .targetShardCount(nuevoConteoShards)
                        .scalingType(ScalingType.UNIFORM_SCALING)
                        .build());
    }

    public void modificarRetencion(
            String nombre,
            int nuevasHoras) {

        StreamDescriptionSummary summary =
                kinesisClient.describeStreamSummary(
                        DescribeStreamSummaryRequest.builder()
                                .streamName(nombre)
                                .build())
                        .streamDescriptionSummary();

        int actual = summary.retentionPeriodHours();

        if (nuevasHoras > actual) {

            kinesisClient.increaseStreamRetentionPeriod(
                    IncreaseStreamRetentionPeriodRequest.builder()
                            .streamName(nombre)
                            .retentionPeriodHours(nuevasHoras)
                            .build());

        } else if (nuevasHoras < actual) {

            kinesisClient.decreaseStreamRetentionPeriod(
                    DecreaseStreamRetentionPeriodRequest.builder()
                            .streamName(nombre)
                            .retentionPeriodHours(nuevasHoras)
                            .build());
        }
    }

    public void eliminarStream(String nombre) {

        kinesisClient.deleteStream(
                DeleteStreamRequest.builder()
                        .streamName(nombre)
                        .enforceConsumerDeletion(true)
                        .build());
    }

    // Conserva la compatibilidad con los consumidores.
    // El checkpoint real es administrado por KCL en DynamoDB.

    public void registrarCheckpoint(
            String consumerGroup,
            String shardId,
            String sequenceNumber,
            long lagMs) {

        retrasoPorGrupoMs.put(
                consumerGroup + ":" + shardId,
                lagMs);
    }

    // Consulta los leases y checkpoints persistentes de KCL.

    private List<Map<String, Object>> consultarLeases(
            String grupo) {

        List<Map<String, Object>> leases = new ArrayList<>();

        Map<String, AttributeValue> ultimaClave =
                Collections.emptyMap();

        do {

            ScanRequest.Builder request =
                    ScanRequest.builder()
                            .tableName(grupo)
                            .consistentRead(true);

            if (!ultimaClave.isEmpty()) {
                request.exclusiveStartKey(ultimaClave);
            }

            ScanResponse response =
                    dynamoClient.scan(request.build())
                            .orTimeout(10, TimeUnit.SECONDS)
                            .join();

            for (Map<String, AttributeValue> item :
                    response.items()) {

                String shardId =
                        obtenerTexto(item, "leaseKey");

                if (shardId == null
                        || !shardId.startsWith("shardId-")) {
                    continue;
                }

                Map<String, Object> lease =
                        new LinkedHashMap<>();

                lease.put("shardId", shardId);

                lease.put("workerId",
                        obtenerTexto(item, "leaseOwner"));

                lease.put("checkpoint",
                        obtenerTexto(item, "checkpoint"));

                lease.put("checkpointSubSequenceNumber",
                        obtenerNumero(
                                item,
                                "checkpointSubSequenceNumber"));

                lease.put("leaseCounter",
                        obtenerNumero(item, "leaseCounter"));

                lease.put("ownerSwitchesSinceCheckpoint",
                        obtenerNumero(
                                item,
                                "ownerSwitchesSinceCheckpoint"));

                leases.add(lease);
            }

            ultimaClave = response.lastEvaluatedKey();

        } while (ultimaClave != null
                && !ultimaClave.isEmpty());

        return leases;
    }

    private String obtenerTexto(
            Map<String, AttributeValue> item,
            String atributo) {

        AttributeValue valor = item.get(atributo);

        return valor != null ? valor.s() : null;
    }

    private String obtenerNumero(
            Map<String, AttributeValue> item,
            String atributo) {

        AttributeValue valor = item.get(atributo);

        return valor != null ? valor.n() : null;
    }

    public Map<String, Object> inspeccionarConsumidores() {

        Map<String, Object> estado =
                new LinkedHashMap<>();

        Map<String, Map<String, String>> checkpoints =
                new LinkedHashMap<>();

        Map<String, List<Map<String, Object>>> leasesPorGrupo =
                new LinkedHashMap<>();

        Map<String, String> errores =
                new LinkedHashMap<>();

        List<String> grupos = List.of(
                grupoAuditoria,
                grupoMetricas,
                grupoInventario);

        for (String grupo : grupos) {

            try {

                List<Map<String, Object>> leases =
                        consultarLeases(grupo);

                leasesPorGrupo.put(grupo, leases);

                Map<String, String> checkpointsGrupo =
                        new LinkedHashMap<>();

                for (Map<String, Object> lease : leases) {

                    String shardId =
                            (String) lease.get("shardId");

                    String checkpoint =
                            (String) lease.get("checkpoint");

                    if (checkpoint != null) {

                        checkpointsGrupo.put(
                                shardId, checkpoint);
                    }
                }

                checkpoints.put(grupo, checkpointsGrupo);

            } catch (Exception e) {

                Throwable causa = e.getCause() != null
                        ? e.getCause()
                        : e;

                errores.put(grupo, causa.toString());
            }
        }

        estado.put("fuenteCheckpoints", "DYNAMODB_KCL");

        estado.put(
                "checkpointsPorAplicacionConsumidora",
                checkpoints);

        estado.put(
                "leasesPorAplicacionConsumidora",
                leasesPorGrupo);

        estado.put(
                "retrasoConsumoMsPorShard",
                new HashMap<>(retrasoPorGrupoMs));

        estado.put(
                "fuenteRetrasoConsumo",
                "ULTIMA_MEDICION_LOCAL");

        estado.put("erroresConsulta", errores);

        return estado;
    }
}