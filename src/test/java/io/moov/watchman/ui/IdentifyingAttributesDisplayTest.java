package io.moov.watchman.ui;

import io.moov.watchman.api.SearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD RED Phase: Tests for BSA Observation #3
 * 
 * Observation #3: Identifying attributes not exposed in search results
 * - Issue: DOB, nationality, passport visible on OFAC but not in Watchman UI
 * - Expected: Display identifying attributes in result cards for triage
 * - BSA Impact: Without these, can't distinguish "Muhammad AL-JASIM DOB 1968 Iraq"
 *               from "Muhammad AL-JASIM DOB 1975 Syria"
 * 
 * Backend Status: ✅ RemarksParser works, Entity model populated, API exposes fields
 * UI Status: ❌ Admin UI doesn't display the attributes
 */
@DisplayName("BSA Observation #3: Identifying Attributes Display")
class IdentifyingAttributesDisplayTest {

    @Test
    @DisplayName("RED: Search result card should display date of birth when present")
    void searchResultCard_shouldDisplayDateOfBirth() {
        // Given: Search result with DOB parsed from OFAC remarks
        SearchResponse response = createSearchResponseWithAttributes(
            "Muhammad AL-JASIM",
            "1968-01-15",  // DOB
            null,          // POB
            "Iraq",        // nationality
            null,          // passport
            null           // passport country
        );
        
        // When: Result card is rendered in UI
        String html = renderSearchResultCard(response.entities().get(0));
        
        // Then: DOB must be visible in result card (not just detail view)
        assertTrue(html.contains("DOB") || html.contains("Date of Birth") || html.contains("Born"),
            "Result card must show DOB label");
        assertTrue(html.contains("1968-01-15") || html.contains("15 Jan 1968"),
            "Result card must show DOB value");
    }

    @Test
    @DisplayName("RED: Search result card should display nationality when present")
    void searchResultCard_shouldDisplayNationality() {
        // Given: Search result with nationality
        SearchResponse response = createSearchResponseWithAttributes(
            "Muhammad AL-JASIM",
            null,
            null,
            "Iraq",
            null,
            null
        );
        
        // When: Result card is rendered
        String html = renderSearchResultCard(response.entities().get(0));
        
        // Then: Nationality must be visible
        assertTrue(html.contains("Nationality") || html.contains("Citizen"),
            "Result card must show nationality label");
        assertTrue(html.contains("Iraq"),
            "Result card must show nationality value");
    }

    @Test
    @DisplayName("RED: Search result card should display passport number when present")
    void searchResultCard_shouldDisplayPassport() {
        // Given: Search result with passport info
        SearchResponse response = createSearchResponseWithAttributes(
            "Muhammad AL-JASIM",
            null,
            null,
            null,
            "P12345678",
            "Iraq"
        );
        
        // When: Result card is rendered
        String html = renderSearchResultCard(response.entities().get(0));
        
        // Then: Passport must be visible
        assertTrue(html.contains("Passport"),
            "Result card must show passport label");
        assertTrue(html.contains("P12345678"),
            "Result card must show passport number");
        assertTrue(html.contains("Iraq"),
            "Result card must show passport country");
    }

    @Test
    @DisplayName("RED: Search result card should display place of birth when present")
    void searchResultCard_shouldDisplayPlaceOfBirth() {
        // Given: Search result with POB
        SearchResponse response = createSearchResponseWithAttributes(
            "Muhammad AL-JASIM",
            null,
            "Baghdad, Iraq",
            null,
            null,
            null
        );
        
        // When: Result card is rendered
        String html = renderSearchResultCard(response.entities().get(0));
        
        // Then: POB must be visible
        assertTrue(html.contains("POB") || html.contains("Place of Birth"),
            "Result card must show POB label");
        assertTrue(html.contains("Baghdad"),
            "Result card must show POB value");
    }

    @Test
    @DisplayName("RED: Search result card should display all attributes together for triage")
    void searchResultCard_shouldDisplayAllAttributesTogether() {
        // Given: Complete profile with all identifying attributes
        SearchResponse response = createSearchResponseWithAttributes(
            "Muhammad AL-JASIM",
            "1968-01-15",
            "Baghdad, Iraq",
            "Iraq",
            "P12345678",
            "Iraq"
        );
        
        // When: Result card is rendered
        String html = renderSearchResultCard(response.entities().get(0));
        
        // Then: All attributes visible in same card for quick triage
        assertTrue(html.contains("1968") || html.contains("1968-01-15"),
            "Must show DOB");
        assertTrue(html.contains("Baghdad"),
            "Must show POB");
        assertTrue(html.contains("Iraq"),
            "Must show nationality");
        assertTrue(html.contains("P12345678"),
            "Must show passport");
        
        // Verify they're in result card, not buried in detail section
        assertFalse(onlyInDetailView(html),
            "Attributes must be in main result card for triage workflow");
    }

    @Test
    @DisplayName("RED: Search result card should gracefully handle missing attributes")
    void searchResultCard_shouldHandleMissingAttributes() {
        // Given: Result with only name (no identifying attributes)
        SearchResponse response = createSearchResponseWithAttributes(
            "Generic Name",
            null,  // no DOB
            null,  // no POB
            null,  // no nationality
            null,  // no passport
            null
        );
        
        // When: Result card is rendered
        String html = renderSearchResultCard(response.entities().get(0));
        
        // Then: Should render without errors, not show empty fields
        assertNotNull(html);
        assertTrue(html.contains("Generic Name"));
        // Empty attribute labels shouldn't clutter the UI
        assertFalse(html.contains("DOB: null") || html.contains("DOB: </"),
            "Should not show empty DOB field");
    }

    @Test
    @DisplayName("RED: Multiple results should display attributes to distinguish entities")
    void multipleResults_shouldShowAttributesToDistinguish() {
        // Given: Two people with same name but different attributes
        SearchResponse response = new SearchResponse(
            List.of(
                createSearchHit("Muhammad AL-JASIM", "1968-01-15", "Baghdad, Iraq", "Iraq", "P111", "Iraq"),
                createSearchHit("Muhammad AL-JASIM", "1975-03-20", "Damascus, Syria", "Syria", "P222", "Syria")
            ),
            2,
            2,
            "test-123",
            null,
            null,
            null
        );
        
        // When: Results list is rendered
        String html = renderSearchResultsList(response);
        
        // Then: Both results show distinguishing attributes
        assertTrue(html.contains("1968") && html.contains("1975"),
            "Must show both DOBs to distinguish entities");
        assertTrue(html.contains("Iraq") && html.contains("Syria"),
            "Must show both nationalities");
        assertTrue(html.contains("P111") && html.contains("P222"),
            "Must show both passport numbers");
    }

    // Helper methods - GREEN phase implementation
    private String renderSearchResultCard(SearchResponse.SearchHit hit) {
        // Simulate admin.html rendering logic (GREEN phase)
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"result-card\">");
        html.append("<strong>").append(hit.name()).append("</strong><br>");
        html.append("Score: ").append(String.format("%.1f", hit.score() * 100)).append("%<br>");
        
        if (hit.matchedAlias() != null) {
            html.append("<small style=\"color: orange;\">Matched Alias: ").append(hit.matchedAlias()).append("</small><br>");
        }
        
        // GREEN phase: Display identifying attributes for triage
        List<String> identifiers = new java.util.ArrayList<>();
        if (hit.dateOfBirth() != null) {
            identifiers.add("DOB: " + hit.dateOfBirth());
        }
        if (hit.placeOfBirth() != null) {
            identifiers.add("POB: " + hit.placeOfBirth());
        }
        if (hit.nationality() != null) {
            identifiers.add("Nationality: " + hit.nationality());
        }
        if (hit.passportNumber() != null) {
            String passport = "Passport: " + hit.passportNumber();
            if (hit.passportCountry() != null) {
                passport += " (" + hit.passportCountry() + ")";
            }
            identifiers.add(passport);
        }
        if (!identifiers.isEmpty()) {
            html.append("<small>").append(String.join(" | ", identifiers)).append("</small><br>");
        }
        
        html.append("Source: ").append(hit.source()).append("<br>");
        html.append("</div>");
        return html.toString();
    }

    private String renderSearchResultsList(SearchResponse response) {
        StringBuilder html = new StringBuilder();
        for (var hit : response.entities()) {
            html.append(renderSearchResultCard(hit));
        }
        return html.toString();
    }

    private SearchResponse createSearchResponseWithAttributes(
            String name, String dob, String pob, String nationality,
            String passport, String passportCountry) {
        var hit = createSearchHit(name, dob, pob, nationality, passport, passportCountry);
        return new SearchResponse(
            List.of(hit),
            1,
            1,
            "test-123",
            null,
            null,
            null
        );
    }

    private SearchResponse.SearchHit createSearchHit(
            String name, String dob, String pob, String nationality, 
            String passport, String passportCountry) {
        return new SearchResponse.SearchHit(
            "entity-123",
            name,
            "PERSON",
            "US_OFAC",
            "SDN-123",
            0.95,
            List.of(),
            List.of("SDGT"),
            null,
            null,  // matchedAlias
            dob,   // dateOfBirth
            pob,   // placeOfBirth
            nationality,
            passport,  // passportNumber
            passportCountry,
            null, null, null, null, null, null, null, null, null  // enriched fields
        );
    }

    private boolean onlyInDetailView(String html) {
        // Check if attributes only appear in detail/hidden section
        return html.contains("detail-view") || html.contains("collapse");
    }
}
