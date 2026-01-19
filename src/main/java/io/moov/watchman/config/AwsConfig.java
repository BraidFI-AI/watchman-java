package io.moov.watchman.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS SDK configuration.
 * Clients use default credential provider chain:
 * 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * 2. System properties
 * 3. ~/.aws/credentials file
 * 4. IAM role (when running on ECS/EC2)
 */
@Configuration
public class AwsConfig {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.US_EAST_1)  // Default region, can be overridden via env var AWS_REGION
            .build();
    }

    @Bean
    public BatchClient batchClient() {
        return BatchClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
}
