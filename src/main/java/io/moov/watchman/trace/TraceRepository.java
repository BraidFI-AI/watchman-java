package io.moov.watchman.trace;

import java.util.Optional;

/**
 * Repository for storing and retrieving scoring traces by session ID.
 * <p>
 * Implementations can use in-memory storage (HashMap), Redis (production),
 * or a database for persistent storage.
 */
public interface TraceRepository {
    
    /**
     * Store a trace with its session ID.
     * 
     * @param trace the scoring trace to store
     */
    void save(ScoringTrace trace);
    
    /**
     * Retrieve a trace by its session ID.
     * 
     * @param sessionId the unique session identifier
     * @return the trace if found, empty otherwise
     */
    Optional<ScoringTrace> findBySessionId(String sessionId);
    
    /**
     * Delete a trace by its session ID.
     * 
     * @param sessionId the unique session identifier
     * @return true if the trace was deleted, false if it didn't exist
     */
    boolean deleteBySessionId(String sessionId);
    
    /**
     * Delete all traces (useful for testing).
     */
    void deleteAll();
}
