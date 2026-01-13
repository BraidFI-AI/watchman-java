package io.moov.watchman.trace;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of TraceRepository using ConcurrentHashMap.
 * <p>
 * This is suitable for:
 * - Local development and testing
 * - Single-instance deployments
 * - Short-lived traces (not persistent across restarts)
 * <p>
 * For production multi-instance deployments, consider Redis-backed implementation.
 */
@Repository
public class InMemoryTraceRepository implements TraceRepository {
    
    private final ConcurrentHashMap<String, ScoringTrace> traces = new ConcurrentHashMap<>();
    
    @Override
    public void save(ScoringTrace trace) {
        if (trace == null || trace.sessionId() == null) {
            throw new IllegalArgumentException("Trace and sessionId must not be null");
        }
        traces.put(trace.sessionId(), trace);
    }
    
    @Override
    public Optional<ScoringTrace> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(traces.get(sessionId));
    }
    
    @Override
    public boolean deleteBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return traces.remove(sessionId) != null;
    }
    
    @Override
    public void deleteAll() {
        traces.clear();
    }
}
