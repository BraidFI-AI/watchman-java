package io.moov.watchman.api.dto;

import io.moov.watchman.model.EntityType;
import io.moov.watchman.model.SourceList;

import java.util.List;

/**
 * DTO for batch search/screening requests.
 */
public record BatchSearchRequestDTO(
    List<SearchItem> items,
    Double minMatch,
    Integer limit
) {
    public record SearchItem(
        String requestId,
        String name,
        String entityType,
        String source
    ) {
        public EntityType toEntityType() {
            if (entityType == null || entityType.isBlank()) {
                return null;
            }
            try {
                return EntityType.valueOf(entityType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public SourceList toSourceList() {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return SourceList.valueOf(source.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
