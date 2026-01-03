package io.moov.watchman.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void shouldCreateErrorResponse() {
        ErrorResponse response = ErrorResponse.of(
                "Test Error", 
                "Test message", 
                400, 
                "/test/path",
                "req-123"
        );

        assertThat(response.error()).isEqualTo("Test Error");
        assertThat(response.message()).isEqualTo("Test message");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.path()).isEqualTo("/test/path");
        assertThat(response.requestId()).isEqualTo("req-123");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldCreateBadRequestResponse() {
        ErrorResponse response = ErrorResponse.badRequest(
                "Invalid input", 
                "/api/search",
                "abc-123"
        );

        assertThat(response.error()).isEqualTo("Bad Request");
        assertThat(response.message()).isEqualTo("Invalid input");
        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void shouldCreateNotFoundResponse() {
        ErrorResponse response = ErrorResponse.notFound(
                "Entity not found", 
                "/api/entities/123",
                "def-456"
        );

        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.message()).isEqualTo("Entity not found");
        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void shouldCreateInternalErrorResponse() {
        ErrorResponse response = ErrorResponse.internalError(
                "Something went wrong", 
                "/api/process",
                "ghi-789"
        );

        assertThat(response.error()).isEqualTo("Internal Server Error");
        assertThat(response.message()).isEqualTo("Something went wrong");
        assertThat(response.status()).isEqualTo(500);
    }

    @Test
    void shouldCreateServiceUnavailableResponse() {
        ErrorResponse response = ErrorResponse.serviceUnavailable(
                "Database offline", 
                "/api/data",
                "jkl-012"
        );

        assertThat(response.error()).isEqualTo("Service Unavailable");
        assertThat(response.message()).isEqualTo("Database offline");
        assertThat(response.status()).isEqualTo(503);
    }
}
