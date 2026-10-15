package cl.duoc.pedidos360.backend.service;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ColaAdminService {

    private final SqsClient sqsClient;
    private final SnsClient snsClient;
    private final MessageListenerContainerRegistry registry;

    public ColaAdminService(SqsClient sqsClient, SnsClient snsClient, MessageListenerContainerRegistry registry) {
        this.sqsClient = sqsClient;
        this.snsClient = snsClient;
        this.registry = registry;
    }

    public String crearCola(String nombre) {
        return sqsClient.createQueue(CreateQueueRequest.builder().queueName(nombre).build()).queueUrl();
    }

    public void eliminarCola(String nombre) {
        String url = sqsClient.getQueueUrl(r -> r.queueName(nombre)).queueUrl();
        sqsClient.deleteQueue(r -> r.queueUrl(url));
    }

    public String crearTopico(String nombre) {
        return snsClient.createTopic(CreateTopicRequest.builder().name(nombre).build()).topicArn();
    }

    public void eliminarTopico(String topicArn) {
        snsClient.deleteTopic(DeleteTopicRequest.builder().topicArn(topicArn).build());
    }

    public String suscribir(String topicArn, String queueArn) {
        return snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn).protocol("sqs").endpoint(queueArn).returnSubscriptionArn(true).build())
                .subscriptionArn();
    }

    public Map<String, String> estadoCola(String nombre) {
        String url = sqsClient.getQueueUrl(r -> r.queueName(nombre)).queueUrl();
        Map<QueueAttributeName, String> attrs = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(url)
                .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
                ).build()).attributes();

        Map<String, String> res = new HashMap<>();
        res.put("disponibles", attrs.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"));
        res.put("enProceso", attrs.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0"));
        return res;
    }

    public void pausarConsumidor(String id) {
        MessageListenerContainer<?> c = registry.getContainerById(id);
        if (c != null) c.stop();
    }

    public void reanudarConsumidor(String id) {
        MessageListenerContainer<?> c = registry.getContainerById(id);
        if (c != null) c.start();
    }

    public int reprocesarDlq(String nombreDlq, String nombreColaDestino) {
        String dlqUrl = sqsClient.getQueueUrl(r -> r.queueName(nombreDlq)).queueUrl();
        String destUrl = sqsClient.getQueueUrl(r -> r.queueName(nombreColaDestino)).queueUrl();

        List<Message> msgs = sqsClient.receiveMessage(r -> r.queueUrl(dlqUrl).maxNumberOfMessages(10)).messages();
        for (Message m : msgs) {
            sqsClient.sendMessage(r -> r.queueUrl(destUrl).messageBody(m.body()));
            sqsClient.deleteMessage(r -> r.queueUrl(dlqUrl).receiptHandle(m.receiptHandle()));
        }
        return msgs.size();
    }

    public void purgarCola(String nombre) {
        String url = sqsClient.getQueueUrl(r -> r.queueName(nombre)).queueUrl();
        sqsClient.purgeQueue(r -> r.queueUrl(url));
    }
}