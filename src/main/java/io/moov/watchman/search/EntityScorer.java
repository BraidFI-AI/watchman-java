package io.moov.watchman.search;

import io.moov.watchman.model.Entity;
import io.moov.watchman.model.ScoreBreakdown;

import java.util.List;

/**
 * Scores entity matches using weighted multi-factor comparison.
 * 
 * Factors include:
 * - Name similarity (primary weight)
 * - Address similarity (if available)
 * - ID matches (exact match bonus)
 * - Date of birth match
 */
public interface EntityScorer {

    /**
     * Calculate overall match score between a query name and candidate entity.
     * 
     * @param queryName The name being searched for
     * @param candidate The candidate entity from the sanctions list
     * @return Score between 0.0 and 1.0
     */
    double score(String queryName, Entity candidate);

    /**
     * Calculate detailed score breakdown for transparency (name query).
     */
    ScoreBreakdown scoreWithBreakdown(String queryName, Entity candidate);

    /**
     * Calculate detailed score breakdown for entity-to-entity comparison.
     * Used when query has structured data (name, IDs, addresses, etc.)
     */
    ScoreBreakdown scoreWithBreakdown(Entity query, Entity index);

    /**
     * Score with additional query context (address, DOB, etc.)
     */
    double score(String queryName, String queryAddress, Entity candidate);

    /**
     * Count how many fields in an entity have non-empty values.
     * Used for coverage calculation.
     * 
     * Ported from Go: pkg/search/similarity.go countAvailableFields()
     */
    static int countAvailableFields(Entity entity) {
        if (entity == null) {
            return 0;
        }

        int count = 0;

        // Count type-specific fields
        if (entity.person() != null) {
            count += countPersonFields(entity.person());
        } else if (entity.business() != null) {
            count += countBusinessFields(entity.business());
        } else if (entity.organization() != null) {
            count += countOrganizationFields(entity.organization());
        } else if (entity.vessel() != null) {
            count += countVesselFields(entity.vessel());
        } else if (entity.aircraft() != null) {
            count += countAircraftFields(entity.aircraft());
        }

        // Count common fields
        count += countCommonFields(entity);

        return count;
    }

    /**
     * Count how many common fields (name, source, contact, etc.) are populated.
     * 
     * Ported from Go: pkg/search/similarity.go countCommonFields()
     */
    static int countCommonFields(Entity entity) {
        if (entity == null) {
            return 0;
        }

        int count = 0;

        if (entity.name() != null && !entity.name().isEmpty()) {
            count++;
        }
        if (entity.source() != null) {
            count++;
        }
        if (entity.contact() != null) {
            if (entity.contact().emailAddress() != null && !entity.contact().emailAddress().isEmpty()) {
                count++;
            }
            if (entity.contact().phoneNumber() != null && !entity.contact().phoneNumber().isEmpty()) {
                count++;
            }
            if (entity.contact().faxNumber() != null && !entity.contact().faxNumber().isEmpty()) {
                count++;
            }
        }
        if (entity.cryptoAddresses() != null && !entity.cryptoAddresses().isEmpty()) {
            count++;
        }
        if (entity.addresses() != null && !entity.addresses().isEmpty()) {
            count++;
        }
        if (entity.altNames() != null && !entity.altNames().isEmpty()) {
            count++;
        }
        if (entity.governmentIds() != null && !entity.governmentIds().isEmpty()) {
            count++;
        }

        return count;
    }

    /**
     * Calculate what percentage of the index entity's fields were actually compared.
     * 
     * Ported from Go: pkg/search/similarity.go calculateCoverage()
     */
    static Coverage calculateCoverage(List<ScorePiece> pieces, Entity indexEntity) {
        int indexFields = countAvailableFields(indexEntity);
        if (indexFields == 0) {
            return new Coverage(1.0, 1.0);
        }

        int fieldsCompared = 0;
        int criticalFieldsCompared = 0;
        int criticalTotal = 0;

        for (ScorePiece piece : pieces) {
            fieldsCompared += piece.getFieldsCompared();
            if (piece.isRequired()) {
                criticalFieldsCompared += piece.getFieldsCompared();
                criticalTotal += piece.getFieldsCompared();
            }
        }

        double ratio = (double) fieldsCompared / indexFields;
        double criticalRatio = criticalTotal > 0 
            ? (double) criticalFieldsCompared / criticalTotal 
            : 1.0;

        return new Coverage(ratio, criticalRatio);
    }

    /**
     * Categorize fields by importance (required, exact IDs, etc.).
     * 
     * Ported from Go: pkg/search/similarity.go countFieldsByImportance()
     */
    static EntityFields countFieldsByImportance(List<ScorePiece> pieces) {
        EntityFields fields = new EntityFields();

        for (ScorePiece piece : pieces) {
            if (piece.getWeight() <= 0 || piece.getFieldsCompared() == 0) {
                continue;
            }

            if (piece.isRequired()) {
                fields.setRequired(fields.getRequired() + piece.getFieldsCompared());
            }

            if (piece.isMatched()) {
                if ("name".equals(piece.getPieceType())) {
                    fields.setHasName(true);
                }
                if (piece.isExact() && ("identifiers".equals(piece.getPieceType()) 
                        || "gov-ids-exact".equals(piece.getPieceType()))) {
                    fields.setHasID(true);
                }
                if ("address".equals(piece.getPieceType())) {
                    fields.setHasAddress(true);
                }
                if (piece.isExact()) {
                    fields.setHasCritical(true);
                }
            }
        }

        return fields;
    }

    /**
     * Adjust base score based on match quality (number of matching terms).
     * Applies penalty if too few terms match relative to query complexity.
     * 
     * Ported from Go: pkg/search/similarity_fuzzy.go adjustScoreBasedOnQuality()
     * 
     * @param match The name match result with score and term counts
     * @param queryTermCount Number of terms in the query
     * @return Adjusted score (may be penalized for poor quality)
     */
    static double adjustScoreBasedOnQuality(NameMatch match, int queryTermCount) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Apply penalties and bonuses based on field coverage and match quality.
     * 
     * Penalties:
     * - Low coverage ratio (< 0.35): 0.95x
     * - Low critical coverage (< 0.7): 0.90x
     * - Insufficient required fields (< 2): 0.90x
     * - Name-only match (no ID/address): 0.95x
     * 
     * Bonuses:
     * - Perfect match (name + ID + critical + high coverage + high score): 1.15x
     * 
     * Ported from Go: pkg/search/similarity.go applyPenaltiesAndBonuses()
     * 
     * @param baseScore The base match score before adjustments
     * @param coverage Coverage metrics (ratio, criticalRatio)
     * @param fields Field importance information (hasName, hasID, etc.)
     * @return Adjusted score with penalties/bonuses applied
     */
    static double applyPenaltiesAndBonuses(double baseScore, Coverage coverage, EntityFields fields) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // Helper methods for counting type-specific fields

    private static int countPersonFields(io.moov.watchman.model.Person person) {
        if (person == null) {
            return 0;
        }

        int count = 0;
        if (person.birthDate() != null) count++;
        if (person.deathDate() != null) count++;
        if (person.gender() != null && !person.gender().isEmpty()) count++;
        if (person.placeOfBirth() != null && !person.placeOfBirth().isEmpty()) count++;
        if (person.titles() != null && !person.titles().isEmpty()) count++;
        if (person.governmentIds() != null && !person.governmentIds().isEmpty()) count++;
        if (person.altNames() != null && !person.altNames().isEmpty()) count++;

        return count;
    }

    private static int countBusinessFields(io.moov.watchman.model.Business business) {
        if (business == null) {
            return 0;
        }

        int count = 0;
        if (business.name() != null && !business.name().isEmpty()) count++;
        if (business.altNames() != null && !business.altNames().isEmpty()) count++;
        if (business.created() != null) count++;
        if (business.dissolved() != null) count++;
        if (business.governmentIds() != null && !business.governmentIds().isEmpty()) count++;

        return count;
    }

    private static int countOrganizationFields(io.moov.watchman.model.Organization organization) {
        if (organization == null) {
            return 0;
        }

        int count = 0;
        if (organization.name() != null && !organization.name().isEmpty()) count++;
        if (organization.altNames() != null && !organization.altNames().isEmpty()) count++;
        if (organization.created() != null) count++;
        if (organization.dissolved() != null) count++;
        if (organization.governmentIds() != null && !organization.governmentIds().isEmpty()) count++;

        return count;
    }

    private static int countVesselFields(io.moov.watchman.model.Vessel vessel) {
        if (vessel == null) {
            return 0;
        }

        int count = 0;
        if (vessel.name() != null && !vessel.name().isEmpty()) count++;
        if (vessel.altNames() != null && !vessel.altNames().isEmpty()) count++;
        if (vessel.type() != null && !vessel.type().isEmpty()) count++;
        if (vessel.flag() != null && !vessel.flag().isEmpty()) count++;
        if (vessel.callSign() != null && !vessel.callSign().isEmpty()) count++;
        if (vessel.tonnage() != null && !vessel.tonnage().isEmpty()) count++;
        if (vessel.owner() != null && !vessel.owner().isEmpty()) count++;
        if (vessel.imoNumber() != null && !vessel.imoNumber().isEmpty()) count++;
        if (vessel.built() != null && !vessel.built().isEmpty()) count++;
        if (vessel.mmsi() != null && !vessel.mmsi().isEmpty()) count++;

        return count;
    }

    private static int countAircraftFields(io.moov.watchman.model.Aircraft aircraft) {
        if (aircraft == null) {
            return 0;
        }

        int count = 0;
        if (aircraft.name() != null && !aircraft.name().isEmpty()) count++;
        if (aircraft.altNames() != null && !aircraft.altNames().isEmpty()) count++;
        if (aircraft.type() != null && !aircraft.type().isEmpty()) count++;
        if (aircraft.flag() != null && !aircraft.flag().isEmpty()) count++;
        if (aircraft.serialNumber() != null && !aircraft.serialNumber().isEmpty()) count++;
        if (aircraft.model() != null && !aircraft.model().isEmpty()) count++;
        if (aircraft.built() != null && !aircraft.built().isEmpty()) count++;
        if (aircraft.icaoCode() != null && !aircraft.icaoCode().isEmpty()) count++;

        return count;
    }
}
