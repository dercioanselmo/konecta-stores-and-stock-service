package com.konecta.stores_stock_service.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Credentials come from our own {@code aws.*} properties (backed by
 * {@code .env}), not the AWS SDK's default env-var chain — {@code .env} is
 * loaded into Spring's Environment, not into actual OS environment
 * variables, so {@code EnvironmentVariableCredentialsProvider} wouldn't see
 * them.
 */
@Configuration
public class AwsS3Config {

    @Bean
    public StaticCredentialsProvider awsCredentialsProvider(
            @Value("${aws.access-key-id}") String accessKeyId,
            @Value("${aws.secret-access-key}") String secretAccessKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    @Bean
    public S3Client s3Client(StaticCredentialsProvider credentialsProvider, @Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StaticCredentialsProvider credentialsProvider, @Value("${aws.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
