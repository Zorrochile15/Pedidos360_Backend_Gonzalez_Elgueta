package cl.duoc.pedidos360.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsAdminConfig {

    @Value("${spring.cloud.aws.region.static}")
    private String awsRegion;

    @Bean
    public SqsClient sqsClient() {

        return SqsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(
                        DefaultCredentialsProvider.create()
                )
                .build();
    }

    @Bean
    public SnsClient snsClient() {

        return SnsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(
                        DefaultCredentialsProvider.create()
                )
                .build();
    }
}