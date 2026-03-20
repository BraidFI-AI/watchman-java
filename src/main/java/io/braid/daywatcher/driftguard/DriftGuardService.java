package io.braid.daywatcher.driftguard;

import io.braid.daywatcher.model.SearchResult;
import io.braid.daywatcher.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DriftGuard — Model validation service for Day Watcher.
 *
 * Loads test cases from CSV files and validates scoring behavior:
 * - True positives: expected entity must score within [scoreFloor, scoreCeiling]
 * - True negatives: no result may score >= scoreCeiling (false positive detection)
 *
 * Used by both the JUnit test suite (CI) and the admin UI (on-demand).
 */
@Service
public class DriftGuardService {

    private static final Logger logger = LoggerFactory.getLogger(DriftGuardService.class);

    private static final double SEARCH_MIN_MATCH = 0.50;
    private static final int SEARCH_LIMIT = 20;

    private static final String INDIVIDUALS_CSV = "observations/internal-individuals.csv";
    private static final String ENTITIES_CSV = "observations/internal-entities.csv";

    private final SearchService searchService;

    public DriftGuardService(SearchService searchService) {
        this.searchService = searchService;
    }

    public DriftGuardReport runAll() {
        List<DriftGuardResult> individualResults = runSuite("individuals", INDIVIDUALS_CSV);
        List<DriftGuardResult> entityResults = runSuite("entities", ENTITIES_CSV);

        List<DriftGuardResult> all = new ArrayList<>();
        all.addAll(individualResults);
        all.addAll(entityResults);

        long passed = all.stream().filter(DriftGuardResult::passed).count();
        long failed = all.size() - passed;

        return new DriftGuardReport(
                Instant.now().toString(),
                all.size(),
                (int) passed,
                (int) failed,
                individualResults,
                entityResults
        );
    }

    public List<DriftGuardResult> runSuite(String suite, String csvPath) {
        List<TestCase> cases;
        try {
            cases = loadCases(csvPath);
        } catch (IOException e) {
            logger.error("DriftGuard: failed to load {}: {}", csvPath, e.getMessage());
            return List.of();
        }

        List<DriftGuardResult> results = new ArrayList<>();
        for (TestCase tc : cases) {
            results.add(validate(suite, tc));
        }
        return results;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private DriftGuardResult validate(String suite, TestCase tc) {
        List<SearchResult> results = searchService.search(tc.query(), SEARCH_LIMIT, SEARCH_MIN_MATCH);

        boolean passed;
        String detail;
        double actualScore = 0.0;

        if (tc.shouldMatch()) {
            SearchResult best = results.stream()
                    .filter(r -> nameContains(r, tc.expectedName()))
                    .max((a, b) -> Double.compare(a.score(), b.score()))
                    .orElse(null);

            if (best == null) {
                passed = false;
                detail = "'" + tc.expectedName() + "' not found in top " + SEARCH_LIMIT + " results";
            } else {
                actualScore = best.score();
                if (actualScore < tc.scoreFloor()) {
                    passed = false;
                    detail = String.format("Score %.1f%% below floor %.1f%%", actualScore * 100, tc.scoreFloor() * 100);
                } else if (actualScore > tc.scoreCeiling()) {
                    passed = false;
                    detail = String.format("Score %.1f%% above ceiling %.1f%%", actualScore * 100, tc.scoreCeiling() * 100);
                } else {
                    passed = true;
                    detail = String.format("Score %.1f%% in [%.1f%%, %.1f%%]",
                            actualScore * 100, tc.scoreFloor() * 100, tc.scoreCeiling() * 100);
                }
            }
        } else {
            actualScore = results.stream().mapToDouble(SearchResult::score).max().orElse(0.0);
            passed = actualScore < tc.scoreCeiling();
            if (passed) {
                detail = String.format("Max score %.1f%% below threshold %.1f%%", actualScore * 100, tc.scoreCeiling() * 100);
            } else {
                detail = String.format("False positive: %.1f%% >= threshold %.1f%%", actualScore * 100, tc.scoreCeiling() * 100);
            }
        }

        return new DriftGuardResult(
                suite,
                tc.row(),
                tc.query(),
                tc.expectedName(),
                tc.shouldMatch(),
                tc.scoreFloor(),
                tc.scoreCeiling(),
                tc.variantType(),
                tc.notes(),
                passed,
                actualScore,
                detail
        );
    }

    private boolean nameContains(SearchResult r, String expectedName) {
        if (expectedName == null || expectedName.isBlank()) return false;
        String upper = expectedName.toUpperCase();
        if (r.entity().name().toUpperCase().contains(upper)) return true;
        if (r.matchedAlias() != null && r.matchedAlias().toUpperCase().contains(upper)) return true;
        if (r.entity().altNames() != null) {
            return r.entity().altNames().stream().anyMatch(a -> a.toUpperCase().contains(upper));
        }
        return false;
    }

    // ── CSV loader ────────────────────────────────────────────────────────────

    public List<TestCase> loadCases(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
        List<String> lines = Files.readAllLines(path);
        List<TestCase> cases = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            String[] parts = trimmed.split("\\|", -1);
            if (parts.length < 8) continue;

            try {
                int row = Integer.parseInt(parts[0].trim()); // throws for header "row" — caught below
                String query = parts[1].trim();
                String expectedName = parts[2].trim();
                boolean shouldMatch = Boolean.parseBoolean(parts[3].trim());
                double scoreFloor = parts[4].trim().isEmpty() ? 0.0 : Double.parseDouble(parts[4].trim());
                double scoreCeiling = parts[5].trim().isEmpty() ? 1.01 : Double.parseDouble(parts[5].trim());
                String variantType = parts[6].trim();
                String notes = parts[7].trim();

                cases.add(new TestCase(row, query, expectedName, shouldMatch, scoreFloor, scoreCeiling, variantType, notes));
            } catch (NumberFormatException e) {
                logger.warn("DriftGuard: skipping malformed row: {}", line);
            }
        }

        return cases;
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    public record TestCase(
            int row,
            String query,
            String expectedName,
            boolean shouldMatch,
            double scoreFloor,
            double scoreCeiling,
            String variantType,
            String notes
    ) {}
}
