package io.moov.watchman.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for REST client beans.
 */
@Configuration
public class RestClientConfig {

    /**
     * RestTemplate bean for webhook notifications and HTTP operations.
     * Configured with custom ObjectMapper for proper ISO-8601 timestamp serialization.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Configure ObjectMapper for ISO-8601 timestamp format
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        return builder
            .messageConverters(converter)
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build();
    }
}
