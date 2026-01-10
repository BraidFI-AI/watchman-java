package io.moov.watchman.search;

import io.moov.watchman.model.*;

import java.time.LocalDate;

/**
 * Integration functions that tie together exact matching, date comparison, and contact info.
 * Phase 10 implementation.
 */
public class IntegrationFunctions {

    /**
     * Compares source lists between query and index entities.
     * Returns exact match if sources match (case-insensitive).
     * <p>
     * Go equivalent: compareExactSourceList() in similarity_exact.go
     *
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with source comparison result
     */
    public static ScorePiece compareExactSourceList(Entity query, Entity index, double weight) {
        // Early return if query has no source
        if (query.source() == null) {
            return ScorePiece.builder()
                    .pieceType("source-list")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .exact(false)
                    .fieldsCompared(0)
                    .build();
        }

        // Always count as field compared if query has a source
        int fieldsCompared = 1;

        // Handle case where index has no source
        if (index.source() == null) {
            return ScorePiece.builder()
                    .pieceType("source-list")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .exact(false)
                    .fieldsCompared(fieldsCompared)
                    .build();
        }

        // Compare sources (enum equality check)
        boolean hasMatch = query.source().equals(index.source());

        return ScorePiece.builder()
                .pieceType("source-list")
                .score(hasMatch ? 1.0 : 0.0)
                .weight(weight)
                .matched(hasMatch)
                .exact(hasMatch)
                .fieldsCompared(fieldsCompared)
                .build();
    }

    /**
     * Compares contact information (emails, phones, fax) between entities.
     * Performs exact matching across all contact fields and averages the results.
     * <p>
     * Go equivalent: compareExactContactInfo() in similarity_exact.go
     *
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with contact comparison result
     */
    public static ScorePiece compareExactContactInfo(Entity query, Entity index, double weight) {
        int fieldsCompared = 0;
        java.util.List<ContactFieldMatch> matches = new java.util.ArrayList<>();

        // Compare email addresses (exact match, case-insensitive)
        if (hasValue(query.contact().emailAddress()) && hasValue(index.contact().emailAddress())) {
            fieldsCompared++;
            boolean match = query.contact().emailAddress().equalsIgnoreCase(index.contact().emailAddress());
            matches.add(new ContactFieldMatch(match ? 1 : 0, 1, match ? 1.0 : 0.0));
        }

        // Compare phone numbers
        if (hasValue(query.contact().phoneNumber()) && hasValue(index.contact().phoneNumber())) {
            fieldsCompared++;
            boolean match = query.contact().phoneNumber().equalsIgnoreCase(index.contact().phoneNumber());
            matches.add(new ContactFieldMatch(match ? 1 : 0, 1, match ? 1.0 : 0.0));
        }

        // Compare fax numbers
        if (hasValue(query.contact().faxNumber()) && hasValue(index.contact().faxNumber())) {
            fieldsCompared++;
            boolean match = query.contact().faxNumber().equalsIgnoreCase(index.contact().faxNumber());
            matches.add(new ContactFieldMatch(match ? 1 : 0, 1, match ? 1.0 : 0.0));
        }

        if (fieldsCompared == 0) {
            return ScorePiece.builder()
                    .pieceType("contact-exact")
                    .score(0.0)
                    .weight(weight)
                    .matched(false)
                    .exact(false)
                    .fieldsCompared(0)
                    .build();
        }

        // Calculate final scores
        double totalScore = 0.0;
        int totalMatches = 0;

        for (ContactFieldMatch m : matches) {
            totalScore += m.score;
            totalMatches += m.matches;
        }

        double finalScore = totalScore / matches.size();

        return ScorePiece.builder()
                .pieceType("contact-exact")
                .score(finalScore)
                .weight(weight)
                .matched(totalMatches > 0)
                .exact(finalScore > 0.99)
                .fieldsCompared(fieldsCompared)
                .build();
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * Dispatcher function that routes date comparisons based on entity type.
     * Calls the appropriate date comparison function for Person, Business, Organization, or Asset types.
     * <p>
     * Go equivalent: compareEntityDates() in similarity_close.go
     *
     * @param query  Query entity
     * @param index  Index entity
     * @param weight Score weight
     * @return ScorePiece with date comparison result
     */
    public static ScorePiece compareEntityDates(Entity query, Entity index, double weight) {
        io.moov.watchman.scorer.DateComparer.DateComparisonResult result = switch (query.type()) {
            case PERSON -> {
                if (query.person() == null || index.person() == null) {
                    yield new io.moov.watchman.scorer.DateComparer.DateComparisonResult(0.0, false, 0);
                }
                yield io.moov.watchman.scorer.DateComparer.comparePersonDates(
                        query.person().birthDate(), query.person().deathDate(),
                        index.person().birthDate(), index.person().deathDate()
                );
            }
            case BUSINESS -> {
                if (query.business() == null || index.business() == null) {
                    yield new io.moov.watchman.scorer.DateComparer.DateComparisonResult(0.0, false, 0);
                }
                yield io.moov.watchman.scorer.DateComparer.compareBusinessDates(
                        query.business().created(), query.business().dissolved(),
                        index.business().created(), index.business().dissolved()
                );
            }
            case ORGANIZATION -> {
                if (query.organization() == null || index.organization() == null) {
                    yield new io.moov.watchman.scorer.DateComparer.DateComparisonResult(0.0, false, 0);
                }
                yield io.moov.watchman.scorer.DateComparer.compareOrgDates(
                        query.organization().created(), query.organization().dissolved(),
                        index.organization().created(), index.organization().dissolved()
                );
            }
            case VESSEL -> {
                if (query.vessel() == null || index.vessel() == null) {
                    yield new io.moov.watchman.scorer.DateComparer.DateComparisonResult(0.0, false, 0);
                }
                LocalDate queryBuilt = parseDate(query.vessel().built());
                LocalDate indexBuilt = parseDate(index.vessel().built());
                yield io.moov.watchman.scorer.DateComparer.compareAssetDates(queryBuilt, indexBuilt, "Vessel");
            }
            case AIRCRAFT -> {
                if (query.aircraft() == null || index.aircraft() == null) {
                    yield new io.moov.watchman.scorer.DateComparer.DateComparisonResult(0.0, false, 0);
                }
                LocalDate queryBuilt = parseDate(query.aircraft().built());
                LocalDate indexBuilt = parseDate(index.aircraft().built());
                yield io.moov.watchman.scorer.DateComparer.compareAssetDates(queryBuilt, indexBuilt, "Aircraft");
            }
            default -> new io.moov.watchman.scorer.DateComparer.DateComparisonResult(0.0, false, 0);
        };

        return ScorePiece.builder()
                .pieceType("dates")
                .score(result.score())
                .weight(weight)
                .matched(result.matched())
                .exact(result.score() > 0.99)
                .fieldsCompared(result.fieldsCompared())
                .build();
    }

    /**
     * Parse a date string (typically just a year like "2010") to LocalDate.
     * Returns null if the string is null, empty, or cannot be parsed.
     */
    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            // Handle year-only format (common for vessels/aircraft)
            if (dateStr.length() == 4) {
                return LocalDate.of(Integer.parseInt(dateStr), 1, 1);
            }
            // Could add more date parsing formats here if needed
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Result of comparing a single contact field type.
     */
    record ContactFieldMatch(int matches, int totalQuery, double score) {
    }
}
