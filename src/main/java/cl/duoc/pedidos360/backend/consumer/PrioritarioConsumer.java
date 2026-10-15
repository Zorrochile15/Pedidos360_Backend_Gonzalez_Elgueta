package cl.duoc.pedidos360.backend.consumer;

import cl.duoc.pedidos360.backend.entity.Pedido;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class PrioritarioConsumer {

    private static final Logger log = LoggerFactory.getLogger(PrioritarioConsumer.class);

    @SqsListener(value = "${aws.sqs.cola.prioritario}", id = "prioritario-consumer")
    public void procesarPrioritario(
            Pedido pedido,
            Acknowledgement ack,
            @Header("id") String msgId,
            @Header(value = "Sqs_Msa_ApproximateReceiveCount", defaultValue = "1") String intento) {

        try {
            if ("ERROR_PRIO".equalsIgnoreCase(pedido.getEstado())) {
                throw new IllegalStateException("Sin stock para despacho prioritario");
            }
            log.info("Despacho Prioritario procesado -> Pedido ID: {}, MsgID: {}, Intento: {}", pedido.getId(), msgId, intento);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error en cola prioritaria -> id={}, intento={}, causa={}", msgId, intento, e.getMessage());
            throw e;
        }
    }

    @SqsListener(value = "${aws.sqs.dlq.prioritario}", id = "prioritario-dlq")
    public void escucharDlq(
            String payload,
            @Header("id") String msgId,
            @Header(value = "Sqs_Msa_ApproximateReceiveCount", defaultValue = "3") String intento) {
        log.error("Mensaje en DLQ Prioritario -> id={}, intento={}, causa=Fallo despacho urgente, body={}", msgId, intento, payload);
    }
}