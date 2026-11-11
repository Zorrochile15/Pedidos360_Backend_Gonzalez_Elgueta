package cl.duoc.pedidos360.backend.service;

import cl.duoc.pedidos360.backend.dto.EventoStreamDto;
import cl.duoc.pedidos360.backend.entity.Pedido;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KinesisProducerService {

    private static final Logger log = LoggerFactory.getLogger(KinesisProducerService.class);

    private final KinesisClient kinesisClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.kinesis.stream.pedidos}")
    private String streamPedidos;

    @Value("${aws.kinesis.stream.analitica}")
    private String streamAnalitica;

    @Value("${aws.kinesis.stream.fallidos}")
    private String streamFallidos;

    public KinesisProducerService(KinesisClient kinesisClient) {
        this.kinesisClient = kinesisClient;
    }

    public Map<String, String> publicarEventoPedido(Pedido pedido) {
        Long localId = (pedido.getLocal() != null && pedido.getLocal().getId() != null)
                ? pedido.getLocal().getId()
                : 0L;

        int cantidadSimulada = "ERROR_CANTIDAD".equalsIgnoreCase(pedido.getEstado()) ? 0 : 1;

        EventoStreamDto dto = new EventoStreamDto(
                UUID.randomUUID().toString(),
                pedido.getId(),
                localId,
                1L,
                cantidadSimulada,
                pedido.getEstado(),
                "OPERACION_PEDIDO",
                LocalDateTime.now().toString()
        );

        String partitionKey = "local-" + localId;
        Map<String, String> resPedidos = enviarAlStream(streamPedidos, partitionKey, dto);

        EventoStreamDto dtoAnalitica = new EventoStreamDto(
                UUID.randomUUID().toString(),
                pedido.getId(),
                localId,
                1L,
                cantidadSimulada,
                pedido.getEstado(),
                "ANALITICA_DEMANDA",
                LocalDateTime.now().toString()
        );
        enviarAlStream(streamAnalitica, partitionKey, dtoAnalitica);

        return resPedidos;
    }

    public Map<String, String> enviarAlStream(String streamName, String partitionKey, EventoStreamDto dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            PutRecordResponse response = kinesisClient.putRecord(PutRecordRequest.builder()
                    .streamName(streamName)
                    .partitionKey(partitionKey)
                    .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
                    .build());

            log.info("Kinesis PutRecord -> stream={}, eventoId={}, key={}, shard={}, seq={}",
                    streamName, dto.getEventoId(), partitionKey, response.shardId(), response.sequenceNumber());

            return Map.of(
                    "eventoId", dto.getEventoId(),
                    "shardId", response.shardId(),
                    "sequenceNumber", response.sequenceNumber()
            );
        } catch (Exception e) {
            log.error("Error publicando en Kinesis stream={} -> causa={}", streamName, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    public int publicarLoteEventos(String streamName, List<EventoStreamDto> eventos) {
        try {
            List<PutRecordsRequestEntry> entries = new ArrayList<>();
            for (EventoStreamDto dto : eventos) {
                String json = objectMapper.writeValueAsString(dto);
                entries.add(PutRecordsRequestEntry.builder()
                        .partitionKey("local-" + (dto.getLocalId() != null ? dto.getLocalId() : 0))
                        .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
                        .build());
            }
            PutRecordsResponse response = kinesisClient.putRecords(PutRecordsRequest.builder()
                    .streamName(streamName)
                    .records(entries)
                    .build());

            if (response.failedRecordCount() > 0) {
                log.warn("Envio por lote con registros fallidos -> fallidos={}", response.failedRecordCount());
            }
            return response.failedRecordCount();
        } catch (Exception e) {
            log.error("Error en envio por lote Kinesis: {}", e.getMessage());
            return eventos.size();
        }
    }

    public void enviarAStreamFallidos(String payloadOriginal, String eventoId, String causa, int intentos) {
        try {
            Map<String, Object> deadLetterEvent = Map.of(
                    "eventoId", eventoId,
                    "causa", causa,
                    "intentos", intentos,
                    "payload", payloadOriginal,
                    "timestamp", LocalDateTime.now().toString()
            );
            String json = objectMapper.writeValueAsString(deadLetterEvent);
            kinesisClient.putRecord(PutRecordRequest.builder()
                    .streamName(streamFallidos)
                    .partitionKey(eventoId)
                    .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
                    .build());
            log.error("Evento enviado a Stream de Fallidos -> id={}, intentos={}, causa={}", eventoId, intentos, causa);
        } catch (Exception e) {
            log.error("Fallo critico enviando a stream de fallidos: {}", e.getMessage());
        }
    }
}