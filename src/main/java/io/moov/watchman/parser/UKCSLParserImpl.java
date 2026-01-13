package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implementation of UK CSL (UK Consolidated Financial Sanctions List) parser.
 * 
 * UK CSV has 36 columns:
 * 0-5: Name parts (Name 1-6)
 * 6: Title
 * 7: Other Names
 * 8: DOB
 * 9: Town of Birth
 * 10: Country of Birth
 * 11: Nationality
 * 12: Passport Details
 * 13: NI Number
 * 14: Position
 * 15-20: Address 1-6
 * 21: Post/Zip Code
 * 22: Country
 * 23: Other Information
 * 24: Group Type (Individual, Entity, Ship)
 * 25: Alias Quality
 * 26: Listed Date
 * 27: UK Sanctions List Date
 * 28: Last Updated
 * 29: Group ID (merge key)
 */
public class UKCSLParserImpl implements UKCSLParser {

    private static final int COL_NAME1 = 0;
    private static final int COL_NAME2 = 1;
    private static final int COL_NAME3 = 2;
    private static final int COL_NAME4 = 3;
    private static final int COL_NAME5 = 4;
    private static final int COL_NAME6 = 5;
    private static final int COL_TITLE = 6;
    private static final int COL_DOB = 8;
    private static final int COL_TOWN_OF_BIRTH = 9;
    private static final int COL_COUNTRY_OF_BIRTH = 10;
    private static final int COL_NATIONALITY = 11;
    private static final int COL_ADDR1 = 15;
    private static final int COL_ADDR2 = 16;
    private static final int COL_ADDR3 = 17;
    private static final int COL_ADDR4 = 18;
    private static final int COL_ADDR5 = 19;
    private static final int COL_ADDR6 = 20;
    private static final int COL_POSTCODE = 21;
    private static final int COL_COUNTRY = 22;
    private static final int COL_OTHER_INFO = 23;
    private static final int COL_GROUP_TYPE = 24;
    private static final int COL_GROUP_ID = 29;
    private static final int MIN_COLUMNS = 30;

    @Override
    public List<Entity> parse(InputStream csvStream) {
        if (csvStream == null) {
            return List.of();
        }

        // Group records by GroupID for merging
        Map<String, UKCSLRecord> recordsById = new LinkedHashMap<>();

        try (Reader reader = new InputStreamReader(csvStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT)) {

            for (CSVRecord record : parser) {
                if (record.size() < MIN_COLUMNS) {
                    continue;
                }

                String groupId = cleanField(safeGet(record, COL_GROUP_ID));
                String groupType = cleanField(safeGet(record, COL_GROUP_TYPE));
                
                // Skip header row
                if (groupId.isEmpty() || groupId.equalsIgnoreCase("Group ID") ||
                    groupType.equalsIgnoreCase("Group Type")) {
                    continue;
                }

                // Build name from parts
                String name = buildName(
                    cleanField(safeGet(record, COL_NAME1)),
                    cleanField(safeGet(record, COL_NAME2)),
                    cleanField(safeGet(record, COL_NAME3)),
                    cleanField(safeGet(record, COL_NAME4)),
                    cleanField(safeGet(record, COL_NAME5)),
                    cleanField(safeGet(record, COL_NAME6))
                );

                String title = cleanField(safeGet(record, COL_TITLE));
                String dob = cleanField(safeGet(record, COL_DOB));
                String townOfBirth = cleanField(safeGet(record, COL_TOWN_OF_BIRTH));
                String countryOfBirth = cleanField(safeGet(record, COL_COUNTRY_OF_BIRTH));
                String nationality = cleanField(safeGet(record, COL_NATIONALITY));
                String otherInfo = cleanField(safeGet(record, COL_OTHER_INFO));

                // Build address
                String addressLine = buildAddressLine(
                    cleanField(safeGet(record, COL_ADDR1)),
                    cleanField(safeGet(record, COL_ADDR2)),
                    cleanField(safeGet(record, COL_ADDR3)),
                    cleanField(safeGet(record, COL_ADDR4)),
                    cleanField(safeGet(record, COL_ADDR5)),
                    cleanField(safeGet(record, COL_ADDR6))
                );
                String postCode = cleanField(safeGet(record, COL_POSTCODE));
                String country = cleanField(safeGet(record, COL_COUNTRY));

                // Merge or create record
                UKCSLRecord ukRecord = recordsById.computeIfAbsent(groupId,
                    id -> new UKCSLRecord(id, groupType));

                if (!name.isEmpty()) {
                    ukRecord.addName(name);
                }
                if (!title.isEmpty()) {
                    ukRecord.setTitle(title);
                }
                if (!dob.isEmpty()) {
                    ukRecord.setDob(dob);
                }
                if (!townOfBirth.isEmpty()) {
                    ukRecord.setTownOfBirth(townOfBirth);
                }
                if (!countryOfBirth.isEmpty()) {
                    ukRecord.setCountryOfBirth(countryOfBirth);
                }
                if (!nationality.isEmpty()) {
                    ukRecord.setNationality(nationality);
                }
                if (!otherInfo.isEmpty()) {
                    ukRecord.setOtherInfo(otherInfo);
                }
                if (!addressLine.isEmpty() || !postCode.isEmpty() || !country.isEmpty()) {
                    ukRecord.addAddress(addressLine, postCode, country);
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse UK CSL file", e);
        }

        // Convert records to entities
        List<Entity> entities = new ArrayList<>();
        for (UKCSLRecord record : recordsById.values()) {
            Entity entity = record.toEntity();
            if (entity != null) {
                entities.add(entity);
            }
        }

        return entities;
    }

    private String safeGet(CSVRecord record, int index) {
        if (index < record.size()) {
            return record.get(index);
        }
        return "";
    }

    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String buildName(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private String buildAddressLine(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    /**
     * Internal record for accumulating UK CSL data before entity creation.
     */
    private static class UKCSLRecord {
        private final String groupId;
        private final String groupType;
        private final List<String> names = new ArrayList<>();
        private final List<Address> addresses = new ArrayList<>();
        private String title;
        private String dob;
        private String townOfBirth;
        private String countryOfBirth;
        private String nationality;
        private String otherInfo;

        UKCSLRecord(String groupId, String groupType) {
            this.groupId = groupId;
            this.groupType = groupType;
        }

        void addName(String name) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }

        void addAddress(String line, String postCode, String country) {
            Address addr = new Address(line, null, "", "", postCode, country);
            if (!addresses.contains(addr)) {
                addresses.add(addr);
            }
        }

        void setTitle(String title) {
            this.title = title;
        }

        void setDob(String dob) {
            this.dob = dob;
        }

        void setTownOfBirth(String townOfBirth) {
            this.townOfBirth = townOfBirth;
        }

        void setCountryOfBirth(String countryOfBirth) {
            this.countryOfBirth = countryOfBirth;
        }

        void setNationality(String nationality) {
            this.nationality = nationality;
        }

        void setOtherInfo(String otherInfo) {
            this.otherInfo = otherInfo;
        }

        Entity toEntity() {
            if (names.isEmpty()) {
                return null;
            }

            String primaryName = names.get(0);
            List<String> altNames = names.size() > 1 ? new ArrayList<>(names.subList(1, names.size())) : List.of();

            EntityType type = switch (groupType.toLowerCase()) {
                case "individual" -> EntityType.PERSON;
                case "ship" -> EntityType.VESSEL;
                case "entity" -> EntityType.BUSINESS;
                default -> EntityType.UNKNOWN;
            };

            Person person = null;
            Vessel vessel = null;
            Business business = null;

            if (type == EntityType.PERSON) {
                person = new Person(primaryName, altNames, null, null, null,
                    townOfBirth, List.of(), List.of());
            } else if (type == EntityType.VESSEL) {
                vessel = new Vessel(primaryName, altNames, null, null, null, 
                    null, null, null, null, null);
            } else if (type == EntityType.BUSINESS) {
                business = new Business(primaryName, altNames, null, null, null);
            }

            return new Entity(
                groupId,
                primaryName,
                type,
                SourceList.UK_CSL,
                groupId,
                person,
                business,
                null, // organization
                null, // aircraft
                vessel,
                null, // contact
                addresses,
                List.of(), // cryptoAddresses
                altNames,
                List.of(), // governmentIds
                new SanctionsInfo(List.of("UK"), false, null),
                List.of(), // historicalInfo
                otherInfo,
                null // preparedFields - computed at index time
            );
        }
    }
}
