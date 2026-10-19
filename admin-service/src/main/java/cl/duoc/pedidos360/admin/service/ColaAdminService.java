package cl.duoc.pedidos360.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ColaAdminService {

    private final SqsClient sqsClient;
    private final SnsClient snsClient;
    private final RestClient restClient;

    @Value("${backend.url}")
    private String backendUrl;

    public ColaAdminService(
            SqsClient sqsClient,
            SnsClient snsClient) {

        this.sqsClient = sqsClient;
        this.snsClient = snsClient;
        this.restClient = RestClient.create();
    }


    /*
     * CREAR COLA
     */
    public String crearCola(String nombre) {

        return sqsClient.createQueue(
                CreateQueueRequest.builder()
                        .queueName(nombre)
                        .build()
        ).queueUrl();
    }


    /*
     * ELIMINAR COLA
     */
    public void eliminarCola(String nombre) {

        String url = sqsClient.getQueueUrl(r ->
                r.queueName(nombre)
        ).queueUrl();

        sqsClient.deleteQueue(r ->
                r.queueUrl(url)
        );
    }


    /*
     * ESTADO DE COLA
     */
    public Map<String, String> estadoCola(String nombre) {

        String url = sqsClient.getQueueUrl(r ->
                r.queueName(nombre)
        ).queueUrl();

        Map<QueueAttributeName, String> attrs =
                sqsClient.getQueueAttributes(
                        GetQueueAttributesRequest.builder()
                                .queueUrl(url)
                                .attributeNames(
                                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
                                )
                                .build()
                ).attributes();

        Map<String, String> resultado = new HashMap<>();

        resultado.put(
                "disponibles",
                attrs.getOrDefault(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        "0"
                )
        );

        resultado.put(
                "enProceso",
                attrs.getOrDefault(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                        "0"
                )
        );

        return resultado;
    }


    /*
     * CREAR TOPICO SNS
     */
    public String crearTopico(String nombre) {

        return snsClient.createTopic(
                CreateTopicRequest.builder()
                        .name(nombre)
                        .build()
        ).topicArn();
    }


    /*
     * ELIMINAR TOPICO SNS
     */
    public void eliminarTopico(String topicArn) {

        snsClient.deleteTopic(
                DeleteTopicRequest.builder()
                        .topicArn(topicArn)
                        .build()
        );
    }


    /*
     * CREAR SUSCRIPCION SNS -> SQS
     */
    public String suscribir(
            String topicArn,
            String queueArn) {

        return snsClient.subscribe(
                SubscribeRequest.builder()
                        .topicArn(topicArn)
                        .protocol("sqs")
                        .endpoint(queueArn)
                        .returnSubscriptionArn(true)
                        .build()
        ).subscriptionArn();
    }


    /*
     * ELIMINAR SUSCRIPCION
     */
    public void eliminarSuscripcion(String subscriptionArn) {

        snsClient.unsubscribe(
                UnsubscribeRequest.builder()
                        .subscriptionArn(subscriptionArn)
                        .build()
        );
    }


    /*
     * PAUSAR CONSUMIDOR
     *
     * El consumidor vive en el backend principal.
     * El Admin Service reenvia el JWT al backend.
     */
    public void pausarConsumidor(
            String id,
            String authorizationHeader) {

        restClient.post()
                .uri(
                        backendUrl
                                + "/admin/consumers/"
                                + id
                                + "/pause"
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        authorizationHeader
                )
                .retrieve()
                .toBodilessEntity();
    }


    /*
     * REANUDAR CONSUMIDOR
     */
    public void reanudarConsumidor(
            String id,
            String authorizationHeader) {

        restClient.post()
                .uri(
                        backendUrl
                                + "/admin/consumers/"
                                + id
                                + "/resume"
                )
                .header(
                        HttpHeaders.AUTHORIZATION,
                        authorizationHeader
                )
                .retrieve()
                .toBodilessEntity();
    }


    /*
     * REPROCESAR DLQ
     */
    public int reprocesarDlq(
            String nombreDlq,
            String nombreColaDestino) {

        String dlqUrl = sqsClient.getQueueUrl(r ->
                r.queueName(nombreDlq)
        ).queueUrl();

        String destinoUrl = sqsClient.getQueueUrl(r ->
                r.queueName(nombreColaDestino)
        ).queueUrl();


        List<Message> mensajes =
                sqsClient.receiveMessage(r ->
                        r.queueUrl(dlqUrl)
                                .maxNumberOfMessages(10)
                ).messages();


        for (Message mensaje : mensajes) {

            sqsClient.sendMessage(r ->
                    r.queueUrl(destinoUrl)
                            .messageBody(mensaje.body())
            );

            sqsClient.deleteMessage(r ->
                    r.queueUrl(dlqUrl)
                            .receiptHandle(
                                    mensaje.receiptHandle()
                            )
            );
        }

        return mensajes.size();
    }


    /*
     * PURGAR DLQ / COLA
     */
    public void purgarCola(String nombre) {

        String url = sqsClient.getQueueUrl(r ->
                r.queueName(nombre)
        ).queueUrl();

        sqsClient.purgeQueue(r ->
                r.queueUrl(url)
        );
    }
}