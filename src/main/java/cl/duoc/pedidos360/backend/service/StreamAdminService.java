package cl.duoc.pedidos360.backend.service;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StreamAdminService {

    private final KinesisClient kinesisClient;
    private final Map<String, Map<String, String>> checkpointsPorGrupo = new ConcurrentHashMap<>();
    private final Map<String, Long> retrasoPorGrupoMs = new ConcurrentHashMap<>();

    public StreamAdminService(KinesisClient kinesisClient) {
        this.kinesisClient = kinesisClient;
    }

    public void crearStream(String nombre, int shards) {
        kinesisClient.createStream(CreateStreamRequest.builder()
                .streamName(nombre)
                .shardCount(shards)
                .streamModeDetails(StreamModeDetails.builder().streamMode(StreamMode.PROVISIONED).build())
                .build());
    }

    public List<String> listarStreams() {
        return kinesisClient.listStreams().streamNames();
    }

    public Map<String, Object> describirStream(String nombre) {
        StreamDescriptionSummary summary = kinesisClient.describeStreamSummary(
                DescribeStreamSummaryRequest.builder().streamName(nombre).build()
        ).streamDescriptionSummary();

        List<Shard> shards = kinesisClient.listShards(
                ListShardsRequest.builder().streamName(nombre).build()
        ).shards();

        Map<String, Object> info = new HashMap<>();
        info.put("streamName", summary.streamName());
        info.put("status", summary.streamStatusAsString());
        info.put("openShardCount", summary.openShardCount());
        info.put("retentionPeriodHours", summary.retentionPeriodHours());
        info.put("streamMode", summary.streamModeDetails().streamModeAsString());
        info.put("shardIds", shards.stream().map(Shard::shardId).toList());
        return info;
    }

    public void modificarShards(String nombre, int nuevoConteoShards) {
        kinesisClient.updateShardCount(UpdateShardCountRequest.builder()
                .streamName(nombre)
                .targetShardCount(nuevoConteoShards)
                .scalingType(ScalingType.UNIFORM_SCALING)
                .build());
    }

    public void modificarRetencion(String nombre, int nuevasHoras) {
        StreamDescriptionSummary summary = kinesisClient.describeStreamSummary(
                DescribeStreamSummaryRequest.builder().streamName(nombre).build()
        ).streamDescriptionSummary();

        int actual = summary.retentionPeriodHours();
        if (nuevasHoras > actual) {
            kinesisClient.increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest.builder()
                    .streamName(nombre)
                    .retentionPeriodHours(nuevasHoras)
                    .build());
        } else if (nuevasHoras < actual) {
            kinesisClient.decreaseStreamRetentionPeriod(DecreaseStreamRetentionPeriodRequest.builder()
                    .streamName(nombre)
                    .retentionPeriodHours(nuevasHoras)
                    .build());
        }
    }

    public void eliminarStream(String nombre) {
        kinesisClient.deleteStream(DeleteStreamRequest.builder()
                .streamName(nombre)
                .enforceConsumerDeletion(true)
                .build());
    }

    public void registrarCheckpoint(String consumerGroup, String shardId, String sequenceNumber, long lagMs) {
        checkpointsPorGrupo.computeIfAbsent(consumerGroup, k -> new ConcurrentHashMap<>())
                .put(shardId, sequenceNumber);
        retrasoPorGrupoMs.put(consumerGroup + ":" + shardId, lagMs);
    }

    public Map<String, Object> inspeccionarConsumidores() {
        Map<String, Object> estado = new HashMap<>();
        estado.put("checkpointsPorAplicacionConsumidora", checkpointsPorGrupo);
        estado.put("retrasoConsumoMsPorShard", retrasoPorGrupoMs);
        return estado;
    }
}