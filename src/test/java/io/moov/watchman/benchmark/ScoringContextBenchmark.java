package io.moov.watchman.benchmark;

import io.moov.watchman.trace.Phase;
import io.moov.watchman.trace.ScoringContext;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark to validate zero-overhead design of ScoringContext.
 * <p>
 * Run with: mvn test-compile exec:java -Dexec.classpathScope=test \
 *   -Dexec.mainClass=org.openjdk.jmh.Main \
 *   -Dexec.args="ScoringContextBenchmark"
 * <p>
 * Expected results:
 * - baseline vs disabledContext: < 1% difference (proves zero overhead)
 * - enabledContext: 50-100% slower (shows cost of tracing)
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class ScoringContextBenchmark {

    private static final int OPERATIONS = 1000;

    /**
     * Baseline: Pure computation without any tracing infrastructure.
     */
    @Benchmark
    public double baseline(Blackhole bh) {
        double total = 0.0;
        for (int i = 0; i < OPERATIONS; i++) {
            // Simulate scoring operation
            double score = computeScore("query-" + i, "candidate-" + i);
            total += score;
        }
        bh.consume(total);
        return total;
    }

    /**
     * Disabled context: Should be identical to baseline (zero overhead).
     */
    @Benchmark
    public double disabledContext(Blackhole bh) {
        ScoringContext ctx = ScoringContext.disabled();
        double total = 0.0;

        for (int i = 0; i < OPERATIONS; i++) {
            String query = "query-" + i;
            String candidate = "candidate-" + i;

            // These calls should be completely eliminated by JIT
            ctx.record(Phase.NORMALIZATION, "Normalizing");
            ctx.record(Phase.NAME_COMPARISON, "Comparing", () -> Map.of("score", 0.5));

            double score = ctx.traced(Phase.NAME_COMPARISON, "Computing score",
                    () -> computeScore(query, candidate));

            total += score;
        }

        bh.consume(total);
        return total;
    }

    /**
     * Enabled context: Shows the cost of full tracing.
     */
    @Benchmark
    public double enabledContext(Blackhole bh) {
        double total = 0.0;

        for (int i = 0; i < OPERATIONS; i++) {
            // Create new context per operation (simulating per-request tracing)
            ScoringContext ctx = ScoringContext.enabled(UUID.randomUUID().toString());

            String query = "query-" + i;
            String candidate = "candidate-" + i;

            ctx.record(Phase.NORMALIZATION, "Normalizing");
            ctx.record(Phase.NAME_COMPARISON, "Comparing", () -> Map.of("score", 0.5));

            double score = ctx.traced(Phase.NAME_COMPARISON, "Computing score",
                    () -> computeScore(query, candidate));

            total += score;

            // Consume the trace to prevent dead code elimination
            bh.consume(ctx.toTrace());
        }

        bh.consume(total);
        return total;
    }

    /**
     * Bad design: Shows the cost of eager parameter evaluation.
     * This demonstrates why lazy evaluation (Supplier) is critical.
     */
    @Benchmark
    public double badDesignEagerEvaluation(Blackhole bh) {
        ScoringContext ctx = ScoringContext.disabled();
        double total = 0.0;

        for (int i = 0; i < OPERATIONS; i++) {
            String query = "query-" + i;
            String candidate = "candidate-" + i;

            // BAD: Map is created even though context is disabled
            Map<String, Object> data = Map.of(
                    "query", query,
                    "candidate", candidate,
                    "score", 0.5
            );
            ctx.record(Phase.NAME_COMPARISON, "Comparing", () -> data);

            double score = computeScore(query, candidate);
            total += score;
        }

        bh.consume(total);
        return total;
    }

    /**
     * Simulates a scoring computation.
     */
    private double computeScore(String s1, String s2) {
        // Simulate some computation to prevent complete dead code elimination
        int len1 = s1.length();
        int len2 = s2.length();
        return (double) Math.min(len1, len2) / Math.max(len1, len2);
    }

    /**
     * Benchmark for simple record() calls.
     */
    @Benchmark
    public void simpleRecordDisabled(Blackhole bh) {
        ScoringContext ctx = ScoringContext.disabled();
        for (int i = 0; i < OPERATIONS; i++) {
            ctx.record(Phase.NORMALIZATION, "test");
        }
        bh.consume(ctx);
    }

    /**
     * Benchmark for simple record() calls with enabled context.
     */
    @Benchmark
    public void simpleRecordEnabled(Blackhole bh) {
        ScoringContext ctx = ScoringContext.enabled("test");
        for (int i = 0; i < OPERATIONS; i++) {
            ctx.record(Phase.NORMALIZATION, "test");
        }
        bh.consume(ctx.toTrace());
    }

    /**
     * Benchmark for record() with data supplier - disabled.
     */
    @Benchmark
    public void recordWithDataDisabled(Blackhole bh) {
        ScoringContext ctx = ScoringContext.disabled();
        for (int i = 0; i < OPERATIONS; i++) {
            ctx.record(Phase.NAME_COMPARISON, "test", () -> Map.of("score", 0.5));
        }
        bh.consume(ctx);
    }

    /**
     * Benchmark for record() with data supplier - enabled.
     */
    @Benchmark
    public void recordWithDataEnabled(Blackhole bh) {
        ScoringContext ctx = ScoringContext.enabled("test");
        for (int i = 0; i < OPERATIONS; i++) {
            ctx.record(Phase.NAME_COMPARISON, "test", () -> Map.of("score", 0.5));
        }
        bh.consume(ctx.toTrace());
    }

    /**
     * Benchmark for traced() operation - disabled.
     */
    @Benchmark
    public void tracedOperationDisabled(Blackhole bh) {
        ScoringContext ctx = ScoringContext.disabled();
        double total = 0.0;
        for (int i = 0; i < OPERATIONS; i++) {
            double result = ctx.traced(Phase.NAME_COMPARISON, "test",
                    () -> computeScore("a", "b"));
            total += result;
        }
        bh.consume(total);
    }

    /**
     * Benchmark for traced() operation - enabled.
     */
    @Benchmark
    public void tracedOperationEnabled(Blackhole bh) {
        ScoringContext ctx = ScoringContext.enabled("test");
        double total = 0.0;
        for (int i = 0; i < OPERATIONS; i++) {
            double result = ctx.traced(Phase.NAME_COMPARISON, "test",
                    () -> computeScore("a", "b"));
            total += result;
        }
        bh.consume(ctx.toTrace());
    }
}
