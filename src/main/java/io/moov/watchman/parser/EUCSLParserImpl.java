package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implementation of EU CSL (European Union Consolidated Sanctions List) parser.
 * 
 * EU CSV uses semicolon delimiter with ~90 columns.
 * Key columns:
 * 1: EntityLogicalID - Unique ID (rows with same ID are merged)
 * 6: EntityRemark
 * 8: EntitySubjectType - "person" for individuals
 * 19: NameAliasWholeName
 * 34: AddressCity
 * 35: AddressStreet
 * 37: AddressZipCode
 * 43: AddressCountryDescription
 * 54: BirthDate
 * 65: BirthCity
 * 67: BirthCountry
 */
public class EUCSLParserImpl implements EUCSLParser {

    private static final int COL_ENTITY_ID = 1;
    private static final int COL_ENTITY_REMARK = 6;
    private static final int COL_SUBJECT_TYPE = 8;
    private static final int COL_NAME = 19;
    private static final int COL_CITY = 34;
    private static final int COL_STREET = 35;
    private static final int COL_ZIPCODE = 37;
    private static final int COL_COUNTRY = 43;
    private static final int COL_BIRTH_DATE = 54;
    private static final int COL_BIRTH_CITY = 65;
    private static final int COL_BIRTH_COUNTRY = 67;
    private static final int MIN_COLUMNS = 68;

    @Override
    public List<Entity> parse(InputStream csvStream) {
        if (csvStream == null) {
            return List.of();
        }

        // Group records by EntityLogicalID for merging
        Map<String, EUCSLRecord> recordsById = new LinkedHashMap<>();

        try (Reader reader = new InputStreamReader(csvStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT
                 .builder()
                 .setDelimiter(';')
                 .setIgnoreEmptyLines(true)
                 .build())) {

            for (CSVRecord record : parser) {
                if (record.size() < MIN_COLUMNS) {
                    continue;
                }

                String entityId = cleanField(safeGet(record, COL_ENTITY_ID));
                if (entityId.isEmpty() || !isNumeric(entityId)) {
                    continue; // Skip header or invalid rows
                }

                String name = cleanField(safeGet(record, COL_NAME));
                String subjectType = cleanField(safeGet(record, COL_SUBJECT_TYPE));
                String remark = cleanField(safeGet(record, COL_ENTITY_REMARK));
                String city = cleanField(safeGet(record, COL_CITY));
                String street = cleanField(safeGet(record, COL_STREET));
                String zipCode = cleanField(safeGet(record, COL_ZIPCODE));
                String country = cleanField(safeGet(record, COL_COUNTRY));
                String birthDate = cleanField(safeGet(record, COL_BIRTH_DATE));
                String birthCity = cleanField(safeGet(record, COL_BIRTH_CITY));
                String birthCountry = cleanField(safeGet(record, COL_BIRTH_COUNTRY));

                // Merge or create record
                EUCSLRecord euRecord = recordsById.computeIfAbsent(entityId, 
                    id -> new EUCSLRecord(id, subjectType));
                
                if (!name.isEmpty()) {
                    euRecord.addName(name);
                }
                if (!remark.isEmpty()) {
                    euRecord.setRemark(remark);
                }
                if (!street.isEmpty() || !city.isEmpty() || !country.isEmpty()) {
                    euRecord.addAddress(street, city, zipCode, country);
                }
                if (!birthDate.isEmpty()) {
                    euRecord.setBirthDate(birthDate);
                }
                if (!birthCity.isEmpty()) {
                    euRecord.setBirthCity(birthCity);
                }
                if (!birthCountry.isEmpty()) {
                    euRecord.setBirthCountry(birthCountry);
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse EU CSL file", e);
        }

        // Convert records to entities
        List<Entity> entities = new ArrayList<>();
        for (EUCSLRecord record : recordsById.values()) {
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

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Internal record for accumulating EU CSL data before entity creation.
     */
    private static class EUCSLRecord {
        private final String entityId;
        private final String subjectType;
        private final List<String> names = new ArrayList<>();
        private final List<Address> addresses = new ArrayList<>();
        private String remark;
        private String birthDate;
        private String birthCity;
        private String birthCountry;

        EUCSLRecord(String entityId, String subjectType) {
            this.entityId = entityId;
            this.subjectType = subjectType;
        }

        void addName(String name) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }

        void addAddress(String street, String city, String zipCode, String country) {
            Address addr = new Address(street, null, city, "", zipCode, country);
            if (!addresses.contains(addr)) {
                addresses.add(addr);
            }
        }

        void setRemark(String remark) {
            this.remark = remark;
        }

        void setBirthDate(String birthDate) {
            this.birthDate = birthDate;
        }

        void setBirthCity(String birthCity) {
            this.birthCity = birthCity;
        }

        void setBirthCountry(String birthCountry) {
            this.birthCountry = birthCountry;
        }

        Entity toEntity() {
            if (names.isEmpty()) {
                return null;
            }

            String primaryName = names.get(0);
            List<String> altNames = names.size() > 1 ? names.subList(1, names.size()) : List.of();
            
            EntityType type = "person".equalsIgnoreCase(subjectType) 
                ? EntityType.PERSON 
                : EntityType.UNKNOWN;

            Person person = null;
            if (type == EntityType.PERSON) {
                person = new Person(primaryName, altNames, null, null, null, 
                    birthCity, List.of(), List.of());
            }

            return new Entity(
                entityId,
                primaryName,
                type,
                SourceList.EU_CSL,
                entityId,
                person,
                null, // business
                null, // organization
                null, // aircraft
                null, // vessel
                null, // contact
                addresses,
                List.of(), // cryptoAddresses
                altNames,
                List.of(), // governmentIds
                new SanctionsInfo(List.of("EU"), false, null),
                remark,
                null // preparedFields - computed at index time
            );
        }
    }
}
