package io.moov.watchman.api.dto;

import io.moov.watchman.batch.BatchScreeningMatch;
import io.moov.watchman.batch.BatchScreeningResponse;
import io.moov.watchman.batch.BatchScreeningResult;
import io.moov.watchman.trace.ScoringTrace;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO for batch search/screening responses.
 */
public record BatchSearchResponseDTO(
    String batchId,
    List<BatchResultDTO> results,
    StatisticsDTO statistics,
    int totalItems,
    int totalMatches,
    int itemsWithMatches,
    long processingTimeMs,
    Instant processedAt
) {
    public record BatchResultDTO(
        String requestId,
        String query,
        String status,
        String errorMessage,
        List<MatchDTO> matches,
        ScoringTrace trace
    ) {
        public static BatchResultDTO from(BatchScreeningResult result) {
            List<MatchDTO> matchDtos = result.matches() != null
                ? result.matches().stream()
                    .map(MatchDTO::from)
                    .collect(Collectors.toList())
                : List.of();

            return new BatchResultDTO(
                result.requestId(),
                result.originalQuery(),
                result.status().name(),
                result.errorMessage(),
                matchDtos,
                result.trace()
            );
        }
    }

    public record MatchDTO(
        String entityId,
        String name,
        String entityType,
        String sourceList,
        double score,
        String remarks,
        Object breakdown
    ) {
        public static MatchDTO from(BatchScreeningMatch match) {
            return new MatchDTO(
                match.entityId(),
                match.name(),
                match.entityType() != null ? match.entityType().name() : null,
                match.sourceList() != null ? match.sourceList().name() : null,
                match.score(),
                match.remarks(),
                match.breakdown()
            );
        }
    }

    public record StatisticsDTO(
        int totalItems,
        int itemsWithMatches,
        int itemsWithoutMatches,
        int itemsWithErrors,
        int totalMatchesFound,
        double averageMatchScore,
        int highConfidenceMatches,
        int mediumConfidenceMatches,
        int lowConfidenceMatches,
        double successRate,
        double matchRate
    ) {
        public static StatisticsDTO from(BatchScreeningResponse.BatchStatistics stats) {
            if (stats == null) {
                return new StatisticsDTO(0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0.0, 0.0);
            }
            return new StatisticsDTO(
                stats.totalItems(),
                stats.itemsWithMatches(),
                stats.itemsWithoutMatches(),
                stats.itemsWithErrors(),
                stats.totalMatchesFound(),
                stats.averageMatchScore(),
                stats.highConfidenceMatches(),
                stats.mediumConfidenceMatches(),
                stats.lowConfidenceMatches(),
                stats.getSuccessRate(),
                stats.getMatchRate()
            );
        }
    }

    public static BatchSearchResponseDTO from(BatchScreeningResponse response) {
        List<BatchResultDTO> resultDtos = response.results() != null
            ? response.results().stream()
                .map(BatchResultDTO::from)
                .collect(Collectors.toList())
            : List.of();

        return new BatchSearchResponseDTO(
            response.batchId(),
            resultDtos,
            StatisticsDTO.from(response.statistics()),
            response.totalItems(),
            response.totalMatches(),
            response.itemsWithMatches(),
            response.processingTimeMs(),
            response.processedAt()
        );
    }
}
