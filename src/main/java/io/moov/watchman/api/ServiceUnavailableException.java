package io.moov.watchman.api;

/**
 * Exception thrown when a dependent service is unavailable.
 */
public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String message) {
        super(message);
    }
    
    public ServiceUnavailableException(String service, Throwable cause) {
        super(String.format("Service unavailable: %s", service), cause);
    }
}
