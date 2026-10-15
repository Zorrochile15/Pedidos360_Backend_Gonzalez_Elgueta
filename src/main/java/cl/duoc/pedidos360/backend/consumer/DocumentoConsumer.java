package cl.duoc.pedidos360.backend.consumer;

import cl.duoc.pedidos360.backend.entity.Pedido;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentoConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentoConsumer.class);
    private final Set<String> procesados = ConcurrentHashMap.newKeySet();

    @SqsListener(value = "${aws.sqs.cola.documentos}", id = "documentos-consumer")
    public void generarDocumento(
            Pedido pedido,
            Acknowledgement ack,
            @Header("id") String msgId,
            @Header(value = "Sqs_Msa_ApproximateReceiveCount", defaultValue = "1") String intento) {

        if (!procesados.add(msgId)) {
            ack.acknowledge();
            return;
        }

        try {
            if ("ERROR_DOC".equalsIgnoreCase(pedido.getEstado())) {
                throw new IllegalArgumentException("Datos corruptos para generar documento");
            }
            log.info("Guia/Ticket generado -> Pedido ID: {}, MsgID: {}, Intento: {}", pedido.getId(), msgId, intento);
            ack.acknowledge();
        } catch (Exception e) {
            procesados.remove(msgId);
            log.error("Error generando documento -> id={}, intento={}, causa={}", msgId, intento, e.getMessage());
            throw e;
        }
    }

    @SqsListener(value = "${aws.sqs.dlq.documentos}", id = "documentos-dlq")
    public void escucharDlq(
            String payload,
            @Header("id") String msgId,
            @Header(value = "Sqs_Msa_ApproximateReceiveCount", defaultValue = "3") String intento) {
        log.error("Mensaje en DLQ Documentos -> id={}, intento={}, causa=Fallo generacion ticket, body={}", msgId, intento, payload);
    }
}