package io.moov.watchman.performance;

/**
 * Simple performance timer for profiling scoring operations.
 * 
 * Thread-safe accumulator for timing measurements without external dependencies.
 */
public class PerformanceTimer {
    
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();
    private long totalNanos = 0;
    private int callCount = 0;
    private final Object lock = new Object();
    
    public void start() {
        startTime.set(System.nanoTime());
    }
    
    public void stop() {
        Long start = startTime.get();
        if (start != null) {
            long elapsed = System.nanoTime() - start;
            synchronized (lock) {
                totalNanos += elapsed;
                callCount++;
            }
            startTime.remove();
        }
    }
    
    public void record(long nanos) {
        synchronized (lock) {
            totalNanos += nanos;
            callCount++;
        }
    }
    
    public double getTotalMillis() {
        synchronized (lock) {
            return totalNanos / 1_000_000.0;
        }
    }
    
    public double getAverageMillis() {
        synchronized (lock) {
            return callCount == 0 ? 0 : (totalNanos / 1_000_000.0) / callCount;
        }
    }
    
    public int getCallCount() {
        synchronized (lock) {
            return callCount;
        }
    }
    
    public void reset() {
        synchronized (lock) {
            totalNanos = 0;
            callCount = 0;
        }
    }
    
    @Override
    public String toString() {
        synchronized (lock) {
            if (callCount == 0) {
                return "0 calls, 0.0 ms total";
            }
            double totalMs = totalNanos / 1_000_000.0;
            double avgMs = totalMs / callCount;
            return String.format("%d calls, %.2f ms total, %.2f ms avg", callCount, totalMs, avgMs);
        }
    }
}
