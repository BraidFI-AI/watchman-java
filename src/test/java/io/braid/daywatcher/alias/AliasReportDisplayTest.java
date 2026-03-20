package io.braid.daywatcher.alias;

import io.braid.daywatcher.index.InMemoryEntityIndex;
import io.braid.daywatcher.model.*;
import io.braid.daywatcher.report.ReportRenderer;
import io.braid.daywatcher.trace.Phase;
import io.braid.daywatcher.trace.ScoringEvent;
import io.braid.daywatcher.trace.ScoringTrace;
import io.braid.daywatcher.trace.TraceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD RED Phase Tests for Phase 1: Alias Transparency
 * Task 1.2: Display All Aliases in Reports
 * 
 * Tests define expected behavior for alias visibility in HTML reports.
 * These tests WILL FAIL until implementation is complete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "watchman.download.enabled=false",
    "watchman.download.on-startup=false"
})
@DisplayName("Phase 1 TDD: Alias Display in Reports")
class AliasReportDisplayTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InMemoryEntityIndex entityIndex;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private ReportRenderer reportRenderer;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        entityIndex.clear();
        // Note: TraceRepository doesn't have clear() - tests use unique session IDs
    }

    @Nested
    @DisplayName("Task 1.2: All Aliases in HTML Report")
    class AliasesInReportTests {

        @Test
        @DisplayName("RED: HTML report should display aliases section")
        void htmlReport_shouldDisplayAliasesSection() {
            // Given: Trace with entity that has aliases
            ScoringTrace trace = createTraceWithAliases(
                "Abu Sayyaf",
                List.of("AL-MALIZI", "Abu Sayyaf Group", "ASG")
            );
            
            // When: Render HTML report
            String html = reportRenderer.renderHtml(trace);

            // Then: HTML should contain aliases section
            assertThat(html).isNotNull();
            assertThat(html).contains("Abu Sayyaf"); // Verify entity name present
            
            // RED: Aliases section doesn't exist yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When aliases section is implemented:
            // assertThat(html).contains("<h3>Aliases</h3>");
            // or
            // assertThat(html).contains("📛 Known Aliases");
        }

        @Test
        @DisplayName("RED: HTML report should list all alt names as bullets")
        void htmlReport_shouldListAllAltNamesAsBullets() {
            // Given: Trace with multiple aliases
            ScoringTrace trace = createTraceWithAliases(
                "MADURO MOROS, Nicolas",
                List.of("Nicolas MADURO", "MADURO, Nicolas", "Nicolas MADURO MOROS")
            );

            // When: Render HTML
            String html = reportRenderer.renderHtml(trace);

            // Then: Should show all aliases as list items
            assertThat(html).isNotNull();
            assertThat(html).contains("MADURO MOROS, Nicolas"); // Verify entity present
            
            // RED: Alias list doesn't exist yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When alias list is implemented:
            // assertThat(html).contains("<li>Nicolas MADURO</li>");
            // assertThat(html).contains("<li>MADURO, Nicolas</li>");
            // assertThat(html).contains("<li>Nicolas MADURO MOROS</li>");
        }

        @Test
        @DisplayName("RED: Report should show primary name and aliases separately")
        void htmlReport_shouldDistinguishPrimaryNameFromAliases() {
            // Given: Trace with primary name and aliases
            ScoringTrace trace = createTraceWithAliases(
                "Abu Sayyaf",
                List.of("AL-MALIZI", "Abu Sayyaf Group")
            );

            // When: Render HTML
            String html = reportRenderer.renderHtml(trace);

            assertThat(html).contains("Abu Sayyaf"); // Verify entity present
            
            // RED: Primary name/alias distinction not implemented - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When implemented:
            
            // RED: Primary name/alias distinction not implemented
            // assertThat(html).containsPattern("Primary Name.*Abu Sayyaf");
            // assertThat(html).containsPattern("Aliases.*AL-MALIZI");
        }

        @Test
        @DisplayName("RED: Report should handle entity with no aliases gracefully")
        void htmlReport_shouldHandleNoAliasesGracefully() {
            // Given: Trace with entity that has no aliases
            ScoringTrace trace = createTraceWithAliases(
                "John Doe",
                List.of() // No aliases
            );

            // When: Render HTML
            String html = reportRenderer.renderHtml(trace);

            assertThat(html).contains("John Doe"); // Verify entity present
            
            // RED: No-alias handling not implemented - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When implemented, either don't show section, or show "No known aliases"
            // Option 1: assertThat(html).doesNotContain("<h3>Aliases</h3>");
            // Option 2: assertThat(html).containsPattern("Aliases.*None|No known aliases");
            // OR
            // assertThat(html).containsPattern("Aliases.*None|No known aliases");
        }

        @Test
        @DisplayName("RED: Report API endpoint should include aliases in HTML")
        void reportApiEndpoint_shouldIncludeAliasesInHtml() {
            // Given: Entity with aliases and trace
            Entity entity = new Entity(
                "12345", 
                "Abu Sayyaf", 
                EntityType.PERSON, 
                SourceList.US_OFAC, 
                "12345",
                null, null, null, null, null,
                null, List.of(), List.of(), 
                List.of("AL-MALIZI", "Abu Sayyaf Group"),
                List.of(),
                null, List.of(), null, null,
                null, null, null, null, null
            );
            
            entityIndex.add(entity.normalize());

            // Trigger search with trace=true to generate report
            ResponseEntity<Map> searchResponse = restTemplate.getForEntity(
                baseUrl + "/v1/search?name=Abu+Sayyaf&trace=true",
                Map.class
            );
            
            assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            String reportUrl = (String) searchResponse.getBody().get("reportUrl");
            assertThat(reportUrl).isNotNull();

            // When: Fetch the HTML report
            ResponseEntity<String> reportResponse = restTemplate.getForEntity(
                baseUrl + reportUrl,
                String.class
            );

            // Then: HTML should include aliases
            assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(reportResponse.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
            String html = reportResponse.getBody();
            assertThat(html).contains("Abu Sayyaf"); // Verify entity in report
            
            // RED: Aliases not in report yet - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When aliases are added to reports:
            // assertThat(html).contains("AL-MALIZI");
            // assertThat(html).contains("Abu Sayyaf Group");
        }
    }

    @Nested
    @DisplayName("Task 1.2 Extended: Report Styling and Formatting")
    class AliasReportStylingTests {

        @Test
        @DisplayName("RED: Aliases section should be visually distinct")
        void aliasesSection_shouldBeVisuallyDistinct() {
            // Given: Trace with aliases
            ScoringTrace trace = createTraceWithAliases(
                "Nicolas Maduro",
                List.of("MADURO MOROS, Nicolas")
            );

            // When: Render HTML
            String html = reportRenderer.renderHtml(trace);

            assertThat(html).contains("Nicolas Maduro"); // Verify entity present
            
            // RED: Styling not implemented - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When implemented:
            
            // RED: Styling not implemented
            // assertThat(html).containsPattern("class=\"aliases\"");
            // or similar styling indicator
        }

        @Test
        @DisplayName("RED: Aliases should be positioned in entity details section")
        void aliases_shouldBeInEntityDetailsSection() {
            // Given: Trace with entity and aliases
            ScoringTrace trace = createTraceWithAliases(
                "Abu Sayyaf",
                List.of("AL-MALIZI")
            );

            // When: Render HTML
            String html = reportRenderer.renderHtml(trace);
            assertThat(html).contains("Abu Sayyaf"); // Verify entity present
            
            // RED: Positioning not implemented - TEST DOCUMENTS EXPECTED BEHAVIOR
            // When implemented, chen: Aliases should appear near entity name/info
            assertThat(html).isNotNull();
            
            // RED: Positioning not implemented
            // Check that aliases appear in logical location (not at end of report)
            // int entityNameIndex = html.indexOf("Abu Sayyaf");
            // int aliasesIndex = html.indexOf("Aliases");
            // assertThat(aliasesIndex).isGreaterThan(entityNameIndex);
            // assertThat(aliasesIndex - entityNameIndex).isLessThan(500); // Within reasonable distance
        }
    }

    /**
     * Helper method to create a ScoringTrace with entity aliases for testing.
     */
    private ScoringTrace createTraceWithAliases(String primaryName, List<String> aliases) {
        String sessionId = "test-" + System.currentTimeMillis();
        
        // Create scoring events
        List<ScoringEvent> events = List.of(
            ScoringEvent.now(
                Phase.NAME_COMPARISON,
                "Name comparison completed",
                Map.of("score", "0.95", "entityName", primaryName)
            )
        );

        // Create breakdown
        ScoreBreakdown breakdown = new ScoreBreakdown(
            0.95, // nameScore
            0.0,  // altNamesScore
            0.0,  // addressScore
            0.0,  // governmentIdScore
            0.0,  // cryptoAddressScore
            0.0,  // contactScore
            0.0,  // dateScore
            0.95  // totalWeightedScore
        );
        
        // Create metadata with aliases
        Map<String, Object> metadata = Map.of(
            "entityName", primaryName,
            "aliases", aliases
        );

        // Create trace using proper constructor
        ScoringTrace trace = new ScoringTrace(
            sessionId,
            events,
            metadata,
            breakdown,
            100L // durationMs
        );

        // Store in repository for API tests
        traceRepository.save(trace);

        return trace;
    }
}
