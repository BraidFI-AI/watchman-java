package io.moov.watchman.api;

import java.time.Instant;

/**
 * Standard error response DTO for consistent API error formatting.
 */
public record ErrorResponse(
    String error,
    String message,
    int status,
    String path,
    String requestId,
    Instant timestamp
) {
    public static ErrorResponse of(String error, String message, int status, String path, String requestId) {
        return new ErrorResponse(error, message, status, path, requestId, Instant.now());
    }
    
    public static ErrorResponse badRequest(String message, String path, String requestId) {
        return of("Bad Request", message, 400, path, requestId);
    }
    
    public static ErrorResponse notFound(String message, String path, String requestId) {
        return of("Not Found", message, 404, path, requestId);
    }
    
    public static ErrorResponse internalError(String message, String path, String requestId) {
        return of("Internal Server Error", message, 500, path, requestId);
    }
    
    public static ErrorResponse serviceUnavailable(String message, String path, String requestId) {
        return of("Service Unavailable", message, 503, path, requestId);
    }
}
