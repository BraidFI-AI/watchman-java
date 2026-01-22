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
 */
@SpringBootTest
class AwsConfigIntegrationTest {

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
        // Assert
        assertThat(context.containsBean("batchClient")).isTrue();
        
        BatchClient batchClient = context.getBean(BatchClient.class);
        assertThat(batchClient).isNotNull();
    }

    // AwsBatchJobSubmitter archived with AWS Batch POC - test disabled
    // @Test
    // void shouldHaveAwsBatchJobSubmitterBean() {
    //     assertThat(context.containsBean("awsBatchJobSubmitter")).isTrue();
    //     AwsBatchJobSubmitter submitter = context.getBean(AwsBatchJobSubmitter.class);
    //     assertThat(submitter).isNotNull();
    // }
}
