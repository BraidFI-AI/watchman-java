package io.moov.watchman.parser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Remarks field from OFAC SDN data to extract structured information.
 * 
 * The Remarks field contains semi-structured text with patterns like:
 * - DOB 15 May 1963
 * - POB Caracas, Venezuela
 * - Passport V12345678 (Venezuela)
 * - nationality Egypt
 */
public class RemarksParser {

    // Regex patterns for extraction
    private static final Pattern DOB_PATTERN = Pattern.compile("DOB\\s+(\\d{1,2}\\s+\\w+\\s+\\d{4}|\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern POB_PATTERN = Pattern.compile("POB\\s+([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NATIONALITY_PATTERN = Pattern.compile("nationality\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("(?:alt\\.\\s+)?Passport\\s+([A-Z0-9]+)(?:\\s+\\(([^)]+)\\))?", Pattern.CASE_INSENSITIVE);
    
    // Alias patterns - a.k.a. (also known as) and f.k.a. (formerly known as)
    // Format: a.k.a. 'NAME' or a.k.a. ''NAME'' (single or double single quotes)
    // Updated to handle GHAILANI case (Row 15): a.k.a. ''FOOPIE''
    private static final Pattern ALIAS_PATTERN = Pattern.compile("(?:a\\.k\\.a\\.|f\\.k\\.a\\.)\\s+'+([^']+)'+", Pattern.CASE_INSENSITIVE);
    
    // Date formatters for various OFAC date patterns
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
    };

    /**
     * Parse a complete remarks string and return all extracted data.
     */
    public ParsedRemarks parse(String remarks) {
        if (remarks == null || remarks.isEmpty() || remarks.equals("-0-")) {
            return new ParsedRemarks();
        }

        Optional<LocalDate> dob = extractDateOfBirth(remarks);
        Optional<String> pob = extractPlaceOfBirth(remarks);
        Optional<String> nationality = extractNationality(remarks);
        List<ExtractedId> ids = extractGovernmentIds(remarks);

        return new ParsedRemarks(dob, pob, ids, nationality);
    }

    /**
     * Extract date of birth from remarks.
     */
    public Optional<LocalDate> extractDateOfBirth(String remarks) {
        if (remarks == null || remarks.isEmpty() || remarks.equals("-0-")) {
            return Optional.empty();
        }

        Matcher matcher = DOB_PATTERN.matcher(remarks);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String dateStr = matcher.group(1).trim();
        
        // Handle year-only format
        if (dateStr.matches("\\d{4}")) {
            return Optional.of(LocalDate.of(Integer.parseInt(dateStr), 1, 1));
        }

        // Try various date formats
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return Optional.of(LocalDate.parse(dateStr, formatter));
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        return Optional.empty();
    }

    /**
     * Extract place of birth from remarks.
     */
    public Optional<String> extractPlaceOfBirth(String remarks) {
        if (remarks == null || remarks.isEmpty() || remarks.equals("-0-")) {
            return Optional.empty();
        }

        Matcher matcher = POB_PATTERN.matcher(remarks);
        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(1).trim());
    }

    /**
     * Extract nationality from remarks.
     */
    private Optional<String> extractNationality(String remarks) {
        if (remarks == null || remarks.isEmpty() || remarks.equals("-0-")) {
            return Optional.empty();
        }

        Matcher matcher = NATIONALITY_PATTERN.matcher(remarks);
        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.of(matcher.group(1).trim());
    }

    /**
     * Extract government IDs (passports, tax IDs, etc.) from remarks.
     */
    public List<ExtractedId> extractGovernmentIds(String remarks) {
        if (remarks == null || remarks.isEmpty() || remarks.equals("-0-")) {
            return List.of();
        }

        List<ExtractedId> ids = new ArrayList<>();
        Matcher matcher = PASSPORT_PATTERN.matcher(remarks);
        
        while (matcher.find()) {
            String passportNumber = matcher.group(1);
            String country = matcher.group(2);
            
            ids.add(new ExtractedId(
                "Passport",
                passportNumber,
                country != null ? Optional.of(country) : Optional.empty()
            ));
        }

        return ids;
    }

    /**
     * Extract aliases from remarks field.
     * 
     * OFAC data contains aliases using patterns:
     * - a.k.a. 'NAME' (also known as)
     * - f.k.a. 'NAME' (formerly known as)
     * 
     * Examples:
     * - "a.k.a. 'PACHO'; a.k.a. 'H7'" → ["PACHO", "H7"]
     * - "f.k.a. 'ANSAR INSTITUTE'" → ["ANSAR INSTITUTE"]
     * - "a.k.a. 'AL-MALIZI, Abu Sayyaf'" → ["AL-MALIZI, Abu Sayyaf"]
     * 
     * @param remarks The remarks field from OFAC SDN data
     * @return List of extracted aliases (empty if none found)
     */
    public List<String> extractAliases(String remarks) {
        if (remarks == null || remarks.isEmpty() || remarks.equals("-0-")) {
            return List.of();
        }

        List<String> aliases = new ArrayList<>();
        Matcher matcher = ALIAS_PATTERN.matcher(remarks);
        
        while (matcher.find()) {
            String alias = matcher.group(1);
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias.trim());
            }
        }

        return aliases;
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
    public record ExtractedId(String type, String number, Optional<String> country) {}
}
