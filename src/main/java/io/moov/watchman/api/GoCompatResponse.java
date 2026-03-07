package io.moov.watchman.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.moov.watchman.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Go Watchman-compatible response format for legacy integration.
 * Used by /go/search endpoint to provide drop-in replacement for Go Watchman.
 * 
 * Response structure matches moov-io/watchman v0.28.2 API format expected by Braid.
 */
public record GoCompatResponse(
    @JsonProperty("SDNs") List<GoEntity> SDNs,
    @JsonProperty("altNames") List<GoEntity> altNames,
    @JsonProperty("addresses") List<GoEntity> addresses,
    @JsonProperty("sectoralSanctions") List<GoEntity> sectoralSanctions,
    @JsonProperty("deniedPersons") List<GoEntity> deniedPersons,
    @JsonProperty("bisEntities") List<GoEntity> bisEntities
) {
    /**
     * Individual entity in Go Watchman format.
     * Maps from our enriched Entity model to Go's flat structure.
     */
    public record GoEntity(
        String entityID,
        String sdnName,
        String sdnType,
        String program,
        String title,
        String callSign,
        String vesselType,
        String tonnage,
        String grossRegisteredTonnage,
        String vesselFlag,
        String vesselOwner,
        String remarks,
        double match
    ) {
        /**
         * Convert from Java Entity to Go format.
         */
        public static GoEntity from(Entity entity, double score, String matchedAlias) {
            // Extract vessel details if present
            String callSign = "";
            String vesselType = "";
            String tonnage = "";
            String grossRegisteredTonnage = "";
            String vesselFlag = "";
            String vesselOwner = "";
            
            if (entity.vessel() != null) {
                callSign = entity.vessel().callSign() != null ? entity.vessel().callSign() : "";
                vesselType = entity.vessel().type() != null ? entity.vessel().type() : "";
                tonnage = entity.vessel().tonnage() != null ? entity.vessel().tonnage() : "";
                // Go uses "tonnage" for GRT
                grossRegisteredTonnage = entity.vessel().tonnage() != null ? entity.vessel().tonnage() : "";
                vesselFlag = entity.vessel().flag() != null ? entity.vessel().flag() : "";
                vesselOwner = entity.vessel().owner() != null ? entity.vessel().owner() : "";
            }
            
            // Extract title from person
            String title = "";
            if (entity.person() != null && entity.person().titles() != null && !entity.person().titles().isEmpty()) {
                title = String.join("; ", entity.person().titles());
            }
            
            // Get program (Go uses single string, we have List)
            String program = "";
            if (entity.sanctionsInfo() != null && entity.sanctionsInfo().programs() != null 
                    && !entity.sanctionsInfo().programs().isEmpty()) {
                program = String.join("; ", entity.sanctionsInfo().programs());
            }
            
            return new GoEntity(
                entity.sourceId() != null ? entity.sourceId() : entity.id(),
                entity.name(),
                mapEntityTypeToGoSdnType(entity.type()),
                program,
                title,
                callSign,
                vesselType,
                tonnage,
                grossRegisteredTonnage,
                vesselFlag,
                vesselOwner,
                entity.remarks() != null ? entity.remarks() : "",
                score
            );
        }
        
        /**
         * Map Java EntityType enum to Go Watchman sdnType string values.
         * Go Watchman uses "individual" for persons, "entity" for businesses/orgs.
         */
        private static String mapEntityTypeToGoSdnType(EntityType type) {
            if (type == null) return "";
            return switch (type) {
                case PERSON -> "individual";
                case BUSINESS, ORGANIZATION -> "entity";
                case VESSEL -> "vessel";
                case AIRCRAFT -> "aircraft";
                case UNKNOWN -> "";
            };
        }
    }
    
    /**
     * Create response from search results, categorizing by match type.
     */
    public static GoCompatResponse from(List<io.moov.watchman.model.SearchResult> results) {
        List<GoEntity> sdns = new ArrayList<>();
        List<GoEntity> altNames = new ArrayList<>();
        // Other categories remain empty for now (can be populated based on source/type)
        List<GoEntity> addresses = new ArrayList<>();
        List<GoEntity> sectoralSanctions = new ArrayList<>();
        List<GoEntity> deniedPersons = new ArrayList<>();
        List<GoEntity> bisEntities = new ArrayList<>();
        
        for (var result : results) {
            GoEntity goEntity = GoEntity.from(result.entity(), result.score(), result.matchedAlias());
            
            // Categorize: if matched via alias, goes to altNames; otherwise SDNs
            if (result.matchedAlias() != null && !result.matchedAlias().equals(result.entity().name())) {
                altNames.add(goEntity);
            } else {
                sdns.add(goEntity);
            }
        }
        
        return new GoCompatResponse(sdns, altNames, addresses, sectoralSanctions, deniedPersons, bisEntities);
    }
}
