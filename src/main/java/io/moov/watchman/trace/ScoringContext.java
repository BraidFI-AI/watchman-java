package io.moov.watchman.trace;

import io.moov.watchman.model.ScoreBreakdown;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Context for tracing the scoring lifecycle of entity matching operations.
 * <p>
 * This interface uses the Null Object pattern to achieve zero overhead when tracing is disabled.
 * All default implementations are no-ops that will be inlined by the JIT compiler.
 * <p>
 * Usage:
 * <pre>
 * // Production mode - zero overhead
 * ScoringContext ctx = ScoringContext.disabled();
 * double score = scorer.score(query, candidate, ctx);
 *
 * // Debug mode - full tracing
 * ScoringContext ctx = ScoringContext.enabled("session-123");
 * double score = scorer.score(query, candidate, ctx);
 * ScoringTrace trace = ctx.toTrace();
 * </pre>
 */
public interface ScoringContext {

    /**
     * Records a simple event without additional data.
     * <p>
     * Default implementation is a no-op for zero overhead when disabled.
     *
     * @param phase the lifecycle phase
     * @param description human-readable description of the event
     * @return this context for method chaining
     */
    default ScoringContext record(Phase phase, String description) {
        return this;
    }

    /**
     * Records an event with additional data using lazy evaluation.
     * <p>
     * The dataSupplier is only evaluated if tracing is enabled, preventing
     * unnecessary allocations in production mode.
     *
     * @param phase the lifecycle phase
     * @param description human-readable description of the event
     * @param dataSupplier lazy supplier of event data (only called if tracing enabled)
     * @return this context for method chaining
     */
    default ScoringContext record(Phase phase, String description, Supplier<Map<String, Object>> dataSupplier) {
        return this;
    }

    /**
     * Executes an operation with timing and tracing.
     * <p>
     * When disabled, this simply executes the operation with no timing overhead.
     * When enabled, records execution time and any errors.
     *
     * @param phase the lifecycle phase
     * @param description human-readable description of the operation
     * @param operation the operation to execute
     * @param <T> return type of the operation
     * @return the result of the operation
     */
    default <T> T traced(Phase phase, String description, Supplier<T> operation) {
        return operation.get();
    }

    /**
     * Executes a void operation with timing and tracing.
     *
     * @param phase the lifecycle phase
     * @param description human-readable description of the operation
     * @param operation the operation to execute
     */
    default void traced(Phase phase, String description, Runnable operation) {
        operation.run();
    }

    /**
     * Adds metadata to the trace context.
     * <p>
     * Metadata is included in the final trace output but doesn't create individual events.
     *
     * @param key metadata key
     * @param value metadata value
     * @return this context for method chaining
     */
    default ScoringContext withMetadata(String key, Object value) {
        return this;
    }

    /**
     * Sets the score breakdown for this context.
     * <p>
     * This should be called after scoring is complete to include the breakdown in the trace.
     *
     * @param breakdown the score breakdown
     * @return this context for method chaining
     */
    default ScoringContext withBreakdown(ScoreBreakdown breakdown) {
        return this;
    }

    /**
     * Returns whether tracing is enabled for this context.
     *
     * @return true if tracing is enabled, false otherwise
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * Converts this context to a final trace output.
     * <p>
     * Should be called after all scoring operations are complete.
     *
     * @return the complete scoring trace
     */
    default ScoringTrace toTrace() {
        return null;
    }

    /**
     * Creates a disabled scoring context (singleton instance).
     * <p>
     * This is the default for production use and has zero overhead.
     *
     * @return a disabled scoring context
     */
    static ScoringContext disabled() {
        return DisabledScoringContext.INSTANCE;
    }

    /**
     * Creates an enabled scoring context with full tracing.
     *
     * @param sessionId unique identifier for this scoring session
     * @return an enabled scoring context
     */
    static ScoringContext enabled(String sessionId) {
        return new EnabledScoringContext(sessionId);
    }
}
