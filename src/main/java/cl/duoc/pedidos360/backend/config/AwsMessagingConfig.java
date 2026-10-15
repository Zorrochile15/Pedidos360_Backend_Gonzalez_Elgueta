package cl.duoc.pedidos360.backend.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AwsMessagingConfig {

    @Value("${aws.sns.topic.pedidos}")
    private String topicPedidos;

    @Value("${aws.sqs.cola.notificaciones}")
    private String colaNotificaciones;

    @Value("${aws.sqs.cola.documentos}")
    private String colaDocumentos;

    @Value("${aws.sqs.cola.prioritario}")
    private String colaPrioritario;

    @Value("${aws.sqs.dlq.notificaciones}")
    private String dlqNotificaciones;

    @Value("${aws.sqs.dlq.documentos}")
    private String dlqDocumentos;

    @Value("${aws.sqs.dlq.prioritario}")
    private String dlqPrioritario;

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options.acknowledgementMode(AcknowledgementMode.MANUAL))
                .build();
    }

    @Bean
    public CommandLineRunner inicializarTopologia(SnsClient snsClient, SqsClient sqsClient) {
        return args -> {
            try {
                String topicArn = snsClient.createTopic(CreateTopicRequest.builder()
                        .name(topicPedidos).build()).topicArn();

                String arnNotif = crearColaConDlq(sqsClient, colaNotificaciones, dlqNotificaciones, topicArn);
                String arnDocs = crearColaConDlq(sqsClient, colaDocumentos, dlqDocumentos, topicArn);
                String arnPrio = crearColaConDlq(sqsClient, colaPrioritario, dlqPrioritario, topicArn);

                suscribirCola(snsClient, topicArn, arnNotif, null);
                suscribirCola(snsClient, topicArn, arnDocs, null);
                suscribirCola(snsClient, topicArn, arnPrio, "{\"prioridad\": [\"ALTA\"]}");
            } catch (Exception e) {
                System.out.println("Aviso al iniciar topologia AWS: " + e.getMessage());
            }
        };
    }

    private String crearColaConDlq(SqsClient sqsClient, String nombreCola, String nombreDlq, String topicArn) {
        Map<QueueAttributeName, String> dlqAttrs = new HashMap<>();
        dlqAttrs.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600");

        String dlqUrl = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(nombreDlq).attributes(dlqAttrs).build()).queueUrl();

        String dlqArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(dlqUrl).attributeNames(QueueAttributeName.QUEUE_ARN).build())
                .attributes().get(QueueAttributeName.QUEUE_ARN);

        String redrivePolicy = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}";

        Map<QueueAttributeName, String> colaAttrs = new HashMap<>();
        colaAttrs.put(QueueAttributeName.VISIBILITY_TIMEOUT, "30");
        colaAttrs.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "345600");
        colaAttrs.put(QueueAttributeName.REDRIVE_POLICY, redrivePolicy);

        String colaUrl = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(nombreCola).attributes(colaAttrs).build()).queueUrl();

        String colaArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(colaUrl).attributeNames(QueueAttributeName.QUEUE_ARN).build())
                .attributes().get(QueueAttributeName.QUEUE_ARN);

        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"sns.amazonaws.com\"},\"Action\":\"sqs:SendMessage\",\"Resource\":\"" + colaArn + "\",\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"" + topicArn + "\"}}}]}";

        Map<QueueAttributeName, String> policyMap = new HashMap<>();
        policyMap.put(QueueAttributeName.POLICY, policy);
        sqsClient.setQueueAttributes(r -> r.queueUrl(colaUrl).attributes(policyMap));

        return colaArn;
    }

    private void suscribirCola(SnsClient snsClient, String topicArn, String queueArn, String filtroJson) {
        String subArn = snsClient.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn).protocol("sqs").endpoint(queueArn).returnSubscriptionArn(true).build())
                .subscriptionArn();

        snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                .subscriptionArn(subArn).attributeName("RawMessageDelivery").attributeValue("true").build());

        if (filtroJson != null) {
            snsClient.setSubscriptionAttributes(SetSubscriptionAttributesRequest.builder()
                    .subscriptionArn(subArn).attributeName("FilterPolicy").attributeValue(filtroJson).build());
        }
    }
}