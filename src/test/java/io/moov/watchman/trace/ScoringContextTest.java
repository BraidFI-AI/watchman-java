package io.moov.watchman.trace;

import io.moov.watchman.model.ScoreBreakdown;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringContextTest {

    @Test
    void disabled_shouldReturnSingletonInstance() {
        ScoringContext ctx1 = ScoringContext.disabled();
        ScoringContext ctx2 = ScoringContext.disabled();

        assertThat(ctx1).isSameAs(ctx2);
        assertThat(ctx1.isEnabled()).isFalse();
    }

    @Test
    void disabled_shouldNotCollectEvents() {
        ScoringContext ctx = ScoringContext.disabled();

        ctx.record(Phase.NORMALIZATION, "test");
        ctx.record(Phase.NAME_COMPARISON, "test", () -> Map.of("key", "value"));

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace).isNull();
    }

    @Test
    void disabled_shouldNotEvaluateSuppliers() {
        ScoringContext ctx = ScoringContext.disabled();
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        ctx.record(Phase.NORMALIZATION, "test", () -> {
            supplierCalled.set(true);
            return Map.of("key", "value");
        });

        // Supplier should never be called when disabled
        assertThat(supplierCalled.get()).isFalse();
    }

    @Test
    void disabled_tracedShouldExecuteOperation() {
        ScoringContext ctx = ScoringContext.disabled();

        String result = ctx.traced(Phase.NAME_COMPARISON, "test", () -> "hello");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void enabled_shouldCollectEvents() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ctx.record(Phase.NORMALIZATION, "Normalizing text");
        ctx.record(Phase.NAME_COMPARISON, "Comparing names", () -> Map.of("score", 0.85));

        ScoringTrace trace = ctx.toTrace();

        assertThat(trace).isNotNull();
        assertThat(trace.sessionId()).isEqualTo("test-session");
        assertThat(trace.events()).hasSize(2);
        assertThat(trace.events().get(0).phase()).isEqualTo(Phase.NORMALIZATION);
        assertThat(trace.events().get(0).description()).isEqualTo("Normalizing text");
        assertThat(trace.events().get(1).data()).containsEntry("score", 0.85);
    }

    @Test
    void enabled_shouldEvaluateSuppliers() {
        ScoringContext ctx = ScoringContext.enabled("test-session");
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        ctx.record(Phase.NORMALIZATION, "test", () -> {
            supplierCalled.set(true);
            return Map.of("key", "value");
        });

        assertThat(supplierCalled.get()).isTrue();
    }

    @Test
    void enabled_tracedShouldRecordTiming() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        String result = ctx.traced(Phase.NAME_COMPARISON, "Computing score", () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.events()).hasSize(1);
        assertThat(trace.events().get(0).data())
                .containsKey("durationMs")
                .containsEntry("success", true);

        Long durationMs = (Long) trace.events().get(0).data().get("durationMs");
        assertThat(durationMs).isGreaterThanOrEqualTo(10L);
    }

    @Test
    void enabled_tracedShouldRecordErrors() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        assertThatThrownBy(() ->
                ctx.traced(Phase.NAME_COMPARISON, "Failing operation", () -> {
                    throw new RuntimeException("Test error");
                })
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("Test error");

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.events()).hasSize(1);
        assertThat(trace.events().get(0).data())
                .containsEntry("success", false)
                .containsEntry("error", "Test error");
    }

    @Test
    void enabled_shouldSupportMetadata() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ctx.withMetadata("queryName", "John Smith")
                .withMetadata("candidateCount", 100);

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.metadata())
                .containsEntry("queryName", "John Smith")
                .containsEntry("candidateCount", 100);
    }

    @Test
    void enabled_shouldIncludeBreakdown() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ScoreBreakdown breakdown = new ScoreBreakdown(
                0.92, 0.0, 0.85, 0.0, 0.0, 0.0, 0.0, 0.89
        );

        ctx.withBreakdown(breakdown);

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.breakdown()).isEqualTo(breakdown);
    }

    @Test
    void enabled_shouldRecordTotalDuration() throws InterruptedException {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        Thread.sleep(10);

        ctx.record(Phase.NORMALIZATION, "test");

        Thread.sleep(10);

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.durationMs()).isGreaterThanOrEqualTo(20L);
    }

    @Test
    void enabled_shouldSupportMethodChaining() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ScoringContext result = ctx
                .record(Phase.NORMALIZATION, "step1")
                .record(Phase.TOKENIZATION, "step2")
                .withMetadata("key", "value");

        assertThat(result).isSameAs(ctx);

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.events()).hasSize(2);
        assertThat(trace.metadata()).containsEntry("key", "value");
    }

    @Test
    void scoringTrace_shouldFilterEventsByPhase() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ctx.record(Phase.NORMALIZATION, "norm1");
        ctx.record(Phase.NAME_COMPARISON, "name1");
        ctx.record(Phase.NORMALIZATION, "norm2");

        ScoringTrace trace = ctx.toTrace();

        assertThat(trace.eventsForPhase(Phase.NORMALIZATION)).hasSize(2);
        assertThat(trace.eventsForPhase(Phase.NAME_COMPARISON)).hasSize(1);
        assertThat(trace.eventsForPhase(Phase.AGGREGATION)).isEmpty();
    }

    @Test
    void scoringTrace_shouldProvideEventCount() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ctx.record(Phase.NORMALIZATION, "e1");
        ctx.record(Phase.NORMALIZATION, "e2");
        ctx.record(Phase.NORMALIZATION, "e3");

        ScoringTrace trace = ctx.toTrace();
        assertThat(trace.eventCount()).isEqualTo(3);
    }

    @Test
    void scoringTrace_shouldBeImmutable() {
        ScoringContext ctx = ScoringContext.enabled("test-session");

        ctx.record(Phase.NORMALIZATION, "test");

        ScoringTrace trace = ctx.toTrace();

        // Try to modify - should not affect the trace
        ctx.record(Phase.NAME_COMPARISON, "after trace");

        assertThat(trace.events()).hasSize(1);
    }
}
