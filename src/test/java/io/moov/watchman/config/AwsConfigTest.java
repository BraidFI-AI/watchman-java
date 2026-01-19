package io.moov.watchman.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import software.amazon.awssdk.services.batch.BatchClient;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test AWS configuration beans.
 * RED phase: These tests define required AWS SDK beans.
 */
@SpringBootTest
class AwsConfigTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldHaveS3ClientBean() {
        // Assert
        assertThat(context.containsBean("s3Client")).isTrue();
        
        S3Client s3Client = context.getBean(S3Client.class);
        assertThat(s3Client).isNotNull();
    }

    @Test
    void shouldHaveBatchClientBean() {
        // Assert - RED phase: This will fail until we implement BatchClient bean
        assertThat(context.containsBean("batchClient")).isTrue();
        
        BatchClient batchClient = context.getBean(BatchClient.class);
        assertThat(batchClient).isNotNull();
    }
}
