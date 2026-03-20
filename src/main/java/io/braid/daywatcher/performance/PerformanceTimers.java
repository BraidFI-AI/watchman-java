package io.braid.daywatcher.performance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Global registry for performance timers.
 * 
 * Enables performance profiling across the application without dependency injection.
 * Timers can be enabled/disabled via system property for production safety.
 */
public class PerformanceTimers {
    
    private static final boolean ENABLED = Boolean.getBoolean("watchman.performance.profiling");
    private static final Map<String, PerformanceTimer> timers = new ConcurrentHashMap<>();
    
    public static PerformanceTimer get(String name) {
        if (!ENABLED) {
            return NoOpTimer.INSTANCE;
        }
        return timers.computeIfAbsent(name, k -> new PerformanceTimer());
    }
    
    public static void reset() {
        timers.values().forEach(PerformanceTimer::reset);
    }
    
    public static void printReport() {
        if (!ENABLED) {
            System.out.println("Performance profiling disabled. Enable with -Dwatchman.performance.profiling=true");
            return;
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE PROFILING REPORT");
        System.out.println("=".repeat(80));
        
        // Group by category
        printCategory("Search Service", "search.");
        printCategory("Entity Scorer", "scorer.");
        printCategory("Similarity Algorithms", "similarity.");
        printCategory("Token Processing", "tokens.");
        printCategory("Phonetic Matching", "phonetic.");
        
        System.out.println("=".repeat(80) + "\n");
    }
    
    private static void printCategory(String categoryName, String prefix) {
        System.out.println("\n" + categoryName + ":");
        System.out.println("-".repeat(80));
        
        timers.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                String name = e.getKey().substring(prefix.length());
                PerformanceTimer timer = e.getValue();
                System.out.printf("  %-40s: %s%n", name, timer);
            });
    }
    
    /**
     * No-op timer for when profiling is disabled.
     */
    private static class NoOpTimer extends PerformanceTimer {
        static final NoOpTimer INSTANCE = new NoOpTimer();
        
        @Override public void start() {}
        @Override public void stop() {}
        @Override public void record(long nanos) {}
        @Override public void reset() {}
    }
}
