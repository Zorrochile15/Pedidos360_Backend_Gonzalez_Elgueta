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
public class NotificacionConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionConsumer.class);
    private final Set<String> procesados = ConcurrentHashMap.newKeySet();

    @SqsListener(value = "${aws.sqs.cola.notificaciones}", id = "notificaciones-consumer")
    public void recibirNotificacion(
            Pedido pedido,
            Acknowledgement ack,
            @Header("id") String msgId,
            @Header(value = "Sqs_Msa_ApproximateReceiveCount", defaultValue = "1") String intento) {

        if (!procesados.add(msgId)) {
            log.warn("Mensaje duplicado ignorado: id={}", msgId);
            ack.acknowledge();
            return;
        }

        try {
            if ("ERROR_FATAL".equalsIgnoreCase(pedido.getEstado())) {
                throw new IllegalArgumentException("Estado invalido para notificar");
            }
            if ("ERROR_TEMPORAL".equalsIgnoreCase(pedido.getEstado()) && Integer.parseInt(intento) < 2) {
                throw new RuntimeException("Fallo temporal de red");
            }

            log.info("Notificacion enviada -> Pedido ID: {}, MsgID: {}, Intento: {}", pedido.getId(), msgId, intento);
            ack.acknowledge();
        } catch (Exception e) {
            procesados.remove(msgId);
            log.error("Error procesando notificacion -> id={}, intento={}, causa={}", msgId, intento, e.getMessage());
            throw e;
        }
    }

    @SqsListener(value = "${aws.sqs.dlq.notificaciones}", id = "notificaciones-dlq")
    public void escucharDlq(
            String payload,
            @Header("id") String msgId,
            @Header(value = "Sqs_Msa_ApproximateReceiveCount", defaultValue = "3") String intento) {
        log.error("Mensaje en DLQ Notificaciones -> id={}, intento={}, causa=Supero reintentos, body={}", msgId, intento, payload);
    }
}