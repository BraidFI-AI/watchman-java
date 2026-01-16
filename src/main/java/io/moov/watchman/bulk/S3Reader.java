package io.moov.watchman.bulk;

import io.moov.watchman.api.dto.BatchSearchRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.InputStream;
import java.util.List;

/**
 * Reads NDJSON files from S3 using AWS SDK.
 */
@Component
public class S3Reader {

    private static final Logger log = LoggerFactory.getLogger(S3Reader.class);
    
    private final S3Client s3Client;
    private final NdjsonReader ndjsonReader;

    public S3Reader(S3Client s3Client) {
        this.s3Client = s3Client;
        this.ndjsonReader = new NdjsonReader();
    }

    /**
     * Read NDJSON file from S3 and parse into search items.
     * 
     * @param s3Path Full S3 path like "s3://bucket/key/file.ndjson"
     * @return List of search items parsed from NDJSON
     */
    public List<BatchSearchRequestDTO.SearchItem> readFromS3(String s3Path) {
        // Validate S3 path
        if (!s3Path.startsWith("s3://")) {
            throw new IllegalArgumentException("S3 path must start with s3://. Got: " + s3Path);
        }

        // Parse bucket and key from s3://bucket/key/file.ndjson
        String pathWithoutProtocol = s3Path.substring(5); // Remove "s3://"
        int firstSlash = pathWithoutProtocol.indexOf('/');
        
        if (firstSlash == -1) {
            throw new IllegalArgumentException("Invalid S3 path format. Expected s3://bucket/key. Got: " + s3Path);
        }

        String bucket = pathWithoutProtocol.substring(0, firstSlash);
        String key = pathWithoutProtocol.substring(firstSlash + 1);

        log.info("Reading from S3: bucket={}, key={}", bucket, key);

        try {
            // Get object from S3
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            
            // Parse NDJSON stream
            List<BatchSearchRequestDTO.SearchItem> items = ndjsonReader.parseItems(response);
            
            log.info("Successfully read {} items from S3: {}", items.size(), s3Path);
            return items;
            
        } catch (NoSuchKeyException e) {
            String message = "S3 file not found: " + s3Path;
            log.error(message, e);
            throw new RuntimeException(message, e);
        } catch (Exception e) {
            String message = "Failed to read from S3: " + s3Path;
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }
}
