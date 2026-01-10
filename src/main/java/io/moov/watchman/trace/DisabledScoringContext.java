package io.moov.watchman.trace;

/**
 * Singleton implementation of ScoringContext that performs no tracing.
 * <p>
 * This implementation uses the Null Object pattern to achieve zero overhead:
 * - All methods inherit no-op default implementations from ScoringContext
 * - JIT compiler will inline these methods to complete no-ops
 * - Zero allocations (singleton instance reused)
 * - Zero GC pressure
 * <p>
 * After JIT warmup (~10k invocations), calls to this context will be eliminated
 * entirely by the HotSpot C2 compiler, resulting in identical performance to code
 * without any tracing infrastructure.
 */
final class DisabledScoringContext implements ScoringContext {

    /**
     * Singleton instance - zero allocation per request.
     */
    static final ScoringContext INSTANCE = new DisabledScoringContext();

    /**
     * Private constructor to enforce singleton pattern.
     */
    private DisabledScoringContext() {
    }

    /**
     * Returns false to allow call-site optimizations.
     */
    @Override
    public boolean isEnabled() {
        return false;
    }

    /**
     * Returns null - no trace is collected when disabled.
     */
    @Override
    public ScoringTrace toTrace() {
        return null;
    }

    @Override
    public String toString() {
        return "DisabledScoringContext{singleton}";
    }
}
