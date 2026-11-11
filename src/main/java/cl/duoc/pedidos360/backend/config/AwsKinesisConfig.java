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
import software.amazon.awssdk.services.kinesis.model.*;

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

    @Bean
    public KinesisClient kinesisClient() {
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(15))
                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build();

        return KinesisClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(overrideConfig)
                .build();
    }

    @Bean
    public CommandLineRunner inicializarStreamsKinesis(KinesisClient kinesisClient) {
        return args -> {
            crearStreamSiNoExiste(kinesisClient, streamPedidos, shardsNegocio);
            crearStreamSiNoExiste(kinesisClient, streamAnalitica, shardsNegocio);
            crearStreamSiNoExiste(kinesisClient, streamFallidos, shardsFallidos);
        };
    }

    private void crearStreamSiNoExiste(KinesisClient client, String nombre, int shards) {
        try {
            client.describeStreamSummary(DescribeStreamSummaryRequest.builder().streamName(nombre).build());
        } catch (ResourceNotFoundException e) {
            try {
                client.createStream(CreateStreamRequest.builder()
                        .streamName(nombre)
                        .shardCount(shards)
                        .streamModeDetails(StreamModeDetails.builder().streamMode(StreamMode.PROVISIONED).build())
                        .build());
            } catch (Exception ex) {
                System.out.println("Aviso creando stream " + nombre + ": " + ex.getMessage());
            }
        } catch (Exception e) {
            System.out.println("Aviso conectando a Kinesis (" + nombre + "): " + e.getMessage());
        }
    }
}