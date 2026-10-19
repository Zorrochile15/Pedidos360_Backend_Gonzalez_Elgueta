package cl.duoc.pedidos360.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.awspring.cloud.sns.core.SnsTemplate;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SetSubscriptionAttributesRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;

import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AwsMessagingConfig {

    @Value("${aws.sqs.listeners.auto-startup:true}")
    private boolean listenersAutoStartup;

    @Value("${spring.cloud.aws.region.static}")
    private String awsRegion;

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


    /*
     * OBJECT MAPPER
     * Soporte para LocalDateTime
     */
    @Bean
    public ObjectMapper objectMapper() {

        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        mapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
        );

        return mapper;
    }


    /*
     * SNS TEMPLATE
     * Usa el ObjectMapper con soporte para LocalDateTime
     */
    @Bean
    public SnsTemplate snsTemplate(
            SnsClient snsClient,
            ObjectMapper objectMapper) {

        MappingJackson2MessageConverter converter =
                new MappingJackson2MessageConverter();

        converter.setObjectMapper(objectMapper);
        converter.setSerializedPayloadClass(String.class);
        converter.setStrictContentTypeMatch(false);

        return new SnsTemplate(
                snsClient,
                converter
        );
    }


    /*
     * SQS LISTENER FACTORY
     * ACK manual + soporte LocalDateTime
     */
    @Bean
    public SqsMessageListenerContainerFactory<Object>
    defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient,
            ObjectMapper objectMapper) {

        SqsMessagingMessageConverter messageConverter =
                new SqsMessagingMessageConverter();

        messageConverter.setObjectMapper(objectMapper);

        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> {

                    options.acknowledgementMode(
                            AcknowledgementMode.MANUAL
                    );

                    options.autoStartup(
                            listenersAutoStartup
                    );

                    options.messageConverter(
                            messageConverter
                    );
                })
                .build();
    }


    /*
     * TOPOLOGIA SNS + SQS
     */
    @Bean
    public CommandLineRunner inicializarTopologia(
            SnsClient snsClient,
            SqsClient sqsClient) {

        return args -> {

            try {

                String topicArn = snsClient.createTopic(
                        CreateTopicRequest.builder()
                                .name(topicPedidos)
                                .build()
                ).topicArn();


                String arnNotif = crearColaConDlq(
                        sqsClient,
                        colaNotificaciones,
                        dlqNotificaciones,
                        topicArn
                );


                String arnDocs = crearColaConDlq(
                        sqsClient,
                        colaDocumentos,
                        dlqDocumentos,
                        topicArn
                );


                String arnPrio = crearColaConDlq(
                        sqsClient,
                        colaPrioritario,
                        dlqPrioritario,
                        topicArn
                );


                /*
                 * FANOUT
                 */
                suscribirCola(
                        snsClient,
                        topicArn,
                        arnNotif,
                        null
                );


                suscribirCola(
                        snsClient,
                        topicArn,
                        arnDocs,
                        null
                );


                /*
                 * FILTRO SELECTIVO
                 * Solo prioridad ALTA
                 */
                suscribirCola(
                        snsClient,
                        topicArn,
                        arnPrio,
                        "{\"prioridad\": [\"ALTA\"]}"
                );

            } catch (Exception e) {

                System.out.println(
                        "Aviso al iniciar topologia AWS: "
                                + e.getMessage()
                );
            }
        };
    }


    /*
     * OBTENER O CREAR COLA
     */
    private String obtenerOCrearCola(
            SqsClient sqsClient,
            String nombreCola) {

        try {

            return sqsClient.getQueueUrl(r ->
                    r.queueName(nombreCola)
            ).queueUrl();

        } catch (QueueDoesNotExistException e) {

            try {

                return sqsClient.createQueue(r ->
                        r.queueName(nombreCola)
                ).queueUrl();

            } catch (SqsException ex) {

                String codigoError =
                        ex.awsErrorDetails() != null
                                ? ex.awsErrorDetails().errorCode()
                                : "";

                if ("QueueNameExists".equals(codigoError)) {

                    return sqsClient.getQueueUrl(r ->
                            r.queueName(nombreCola)
                    ).queueUrl();
                }

                throw ex;
            }
        }
    }


    /*
     * CREAR COLA PRINCIPAL + DLQ
     */
    private String crearColaConDlq(
            SqsClient sqsClient,
            String nombreCola,
            String nombreDlq,
            String topicArn) {

        /*
         * DLQ
         * Retencion: 14 dias
         */
        Map<QueueAttributeName, String> dlqAttrs =
                new HashMap<>();

        dlqAttrs.put(
                QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                "1209600"
        );


        String dlqUrl =
                obtenerOCrearCola(
                        sqsClient,
                        nombreDlq
                );


        sqsClient.setQueueAttributes(r ->
                r.queueUrl(dlqUrl)
                        .attributes(dlqAttrs)
        );


        String dlqArn =
                sqsClient.getQueueAttributes(
                                GetQueueAttributesRequest.builder()
                                        .queueUrl(dlqUrl)
                                        .attributeNames(
                                                QueueAttributeName.QUEUE_ARN
                                        )
                                        .build()
                        )
                        .attributes()
                        .get(QueueAttributeName.QUEUE_ARN);


        /*
         * REDRIVE POLICY
         * 3 intentos antes de DLQ
         */
        String redrivePolicy =
                "{\"deadLetterTargetArn\":\""
                        + dlqArn
                        + "\",\"maxReceiveCount\":\"3\"}";


        /*
         * COLA PRINCIPAL
         */
        Map<QueueAttributeName, String> colaAttrs =
                new HashMap<>();

        /*
         * Visibility timeout: 30 segundos
         */
        colaAttrs.put(
                QueueAttributeName.VISIBILITY_TIMEOUT,
                "30"
        );

        /*
         * Retencion: 4 dias
         */
        colaAttrs.put(
                QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                "345600"
        );

        /*
         * Redrive hacia DLQ
         */
        colaAttrs.put(
                QueueAttributeName.REDRIVE_POLICY,
                redrivePolicy
        );


        String colaUrl =
                obtenerOCrearCola(
                        sqsClient,
                        nombreCola
                );


        sqsClient.setQueueAttributes(r ->
                r.queueUrl(colaUrl)
                        .attributes(colaAttrs)
        );


        String colaArn =
                sqsClient.getQueueAttributes(
                                GetQueueAttributesRequest.builder()
                                        .queueUrl(colaUrl)
                                        .attributeNames(
                                                QueueAttributeName.QUEUE_ARN
                                        )
                                        .build()
                        )
                        .attributes()
                        .get(QueueAttributeName.QUEUE_ARN);


        /*
         * PERMISO SNS -> SQS
         */
        String policy =
                "{\"Version\":\"2012-10-17\","
                        + "\"Statement\":[{"
                        + "\"Effect\":\"Allow\","
                        + "\"Principal\":{\"Service\":\"sns.amazonaws.com\"},"
                        + "\"Action\":\"sqs:SendMessage\","
                        + "\"Resource\":\"" + colaArn + "\","
                        + "\"Condition\":{\"ArnEquals\":{"
                        + "\"aws:SourceArn\":\"" + topicArn + "\""
                        + "}}"
                        + "}]}";


        Map<QueueAttributeName, String> policyMap =
                new HashMap<>();

        policyMap.put(
                QueueAttributeName.POLICY,
                policy
        );


        sqsClient.setQueueAttributes(r ->
                r.queueUrl(colaUrl)
                        .attributes(policyMap)
        );


        return colaArn;
    }


    /*
     * SUSCRIPCION SNS -> SQS
     */
    private void suscribirCola(
            SnsClient snsClient,
            String topicArn,
            String queueArn,
            String filtroJson) {

        String subArn =
                snsClient.subscribe(
                                SubscribeRequest.builder()
                                        .topicArn(topicArn)
                                        .protocol("sqs")
                                        .endpoint(queueArn)
                                        .returnSubscriptionArn(true)
                                        .build()
                        )
                        .subscriptionArn();


        /*
         * Entrega directa del mensaje
         */
        snsClient.setSubscriptionAttributes(
                SetSubscriptionAttributesRequest.builder()
                        .subscriptionArn(subArn)
                        .attributeName("RawMessageDelivery")
                        .attributeValue("true")
                        .build()
        );


        /*
         * Filtro opcional
         */
        if (filtroJson != null) {

            snsClient.setSubscriptionAttributes(
                    SetSubscriptionAttributesRequest.builder()
                            .subscriptionArn(subArn)
                            .attributeName("FilterPolicy")
                            .attributeValue(filtroJson)
                            .build()
            );
        }
    }


    /*
     * SQS CLIENT
     */
    @Bean
    public SqsClient sqsClient() {

        return SqsClient.builder()
                .region(
                        Region.of(awsRegion)
                )
                .credentialsProvider(
                        DefaultCredentialsProvider.create()
                )
                .build();
    }
}