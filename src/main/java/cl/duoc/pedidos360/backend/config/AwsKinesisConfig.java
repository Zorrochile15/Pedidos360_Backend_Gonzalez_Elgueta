package cl.duoc.pedidos360.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.kinesis.common.KinesisClientUtil;

import java.time.Duration;

@Configuration
@EnableScheduling
public class AwsKinesisConfig {

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Value("${aws.kinesis.stream.pedidos}")
    private String streamPedidos;

    @Value("${aws.kinesis.stream.analitica}")
    private String streamAnalitica;

    @Value("${aws.kinesis.stream.fallidos}")
    private String streamFallidos;

    @Value("${aws.kinesis.shards.negocio}")
    private int shardsNegocio;

    @Value("${aws.kinesis.shards.fallidos}")
    private int shardsFallidos;

    // Cliente original de Kinesis
    @Bean
    public KinesisClient kinesisClient() {
        ClientOverrideConfiguration overrideConfig =
                ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(15))
                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(3).build())
                .build();

        return KinesisClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(overrideConfig)
                .build();
    }

    // Cliente asincrono para KCL
    @Bean
    public KinesisAsyncClient kinesisAsyncClient() {
        return KinesisClientUtil.createKinesisAsyncClient(
                KinesisAsyncClient.builder()
                        .region(Region.of(region))
        );
    }

    // DynamoDB: checkpoints y leases
    @Bean
    public DynamoDbAsyncClient dynamDbAsyncClient() {
        return DynamoDbAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    // CloudWatch: metricas de KCL
    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public CommandLineRunner inicializarStreamsKinesis(
            KinesisClient kinesisClient) {

        return args -> {
            crearStreamSiNoExiste(
                    kinesisClient, streamPedidos, shardsNegocio);

            crearStreamSiNoExiste(
                    kinesisClient, streamAnalitica, shardsNegocio);

            crearStreamSiNoExiste(
                    kinesisClient, streamFallidos, shardsFallidos);
        };
    }

    private void crearStreamSiNoExiste(
            KinesisClient client, String nombre, int shards) {

        try {
            client.describeStreamSummary(
                    DescribeStreamSummaryRequest.builder()
                            .streamName(nombre)
                            .build());

        } catch (ResourceNotFoundException e) {
            try {
                client.createStream(
                        CreateStreamRequest.builder()
                                .streamName(nombre)
                                .shardCount(shards)
                                .streamModeDetails(
                                        StreamModeDetails.builder()
                                                .streamMode(StreamMode.PROVISIONED)
                                                .build())
                                .build());

            } catch (Exception ex) {
                System.out.println(
                        "Aviso creando stream " + nombre
                                + ": " + ex.getMessage());
            }

        } catch (Exception e) {
            System.out.println(
                    "Aviso conectando a Kinesis (" + nombre
                            + "): " + e.getMessage());
        }
    }
}