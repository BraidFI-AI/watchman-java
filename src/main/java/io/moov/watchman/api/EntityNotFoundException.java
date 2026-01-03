package io.moov.watchman.api;

/**
 * Exception thrown when a requested entity is not found.
 */
public class EntityNotFoundException extends RuntimeException {
    
    public EntityNotFoundException(String message) {
        super(message);
    }
    
    public EntityNotFoundException(String entityType, String id) {
        super(String.format("%s not found: %s", entityType, id));
    }
}
