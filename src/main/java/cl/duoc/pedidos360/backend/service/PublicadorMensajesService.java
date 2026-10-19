package cl.duoc.pedidos360.backend.service;

import cl.duoc.pedidos360.backend.entity.Pedido;
import io.awspring.cloud.sns.core.SnsNotification;
import io.awspring.cloud.sns.core.SnsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PublicadorMensajesService {

    private final SnsTemplate snsTemplate;

    @Value("${aws.sns.topic.pedidos}")
    private String topicPedidos;

    public PublicadorMensajesService(SnsTemplate snsTemplate) {
        this.snsTemplate = snsTemplate;
    }

    public void publicarPedido(Pedido pedido, String prioridad) {

        String nivelPrioridad = (prioridad == null || prioridad.isBlank())
                ? "NORMAL"
                : prioridad.trim().toUpperCase();

        SnsNotification<Pedido> notificacion = SnsNotification.builder(pedido)
                .header("prioridad", nivelPrioridad)
                .build();

        snsTemplate.sendNotification(topicPedidos, notificacion);
    }
}