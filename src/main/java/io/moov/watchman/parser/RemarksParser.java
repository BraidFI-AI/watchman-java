package io.moov.watchman.parser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Parses the Remarks field from OFAC SDN data to extract structured information.
 * 
 * The Remarks field contains semi-structured text with patterns like:
 * - DOB 15 May 1963
 * - POB Caracas, Venezuela
 * - Passport V12345678 (Venezuela)
 * - Tax ID No. 52-2083095
 * 
 * TODO: Implement after EntityTypeParser is complete
 */
public class RemarksParser {

    /**
     * Parse a complete remarks string and return all extracted data.
     */
    public ParsedRemarks parse(String remarks) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Extract date of birth from remarks.
     */
    public Optional<LocalDate> extractDateOfBirth(String remarks) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Extract place of birth from remarks.
     */
    public Optional<String> extractPlaceOfBirth(String remarks) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Extract government IDs (passports, tax IDs, etc.) from remarks.
     */
    public List<ExtractedId> extractGovernmentIds(String remarks) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Parsed result containing all extracted data.
     */
    public record ParsedRemarks(
        Optional<LocalDate> dateOfBirth,
        Optional<String> placeOfBirth,
        List<ExtractedId> governmentIds,
        Optional<String> nationality
    ) {
        public ParsedRemarks() {
            this(Optional.empty(), Optional.empty(), List.of(), Optional.empty());
        }
    }

    /**
     * Extracted government ID with type and value.
     */
    public record ExtractedId(String type, String value, Optional<String> country) {}
}
