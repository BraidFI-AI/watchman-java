package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implementation of US CSL (Consolidated Screening List) parser.
 * 
 * CSV Columns (29 total):
 * 0: _id - Unique record identifier
 * 1: source - Source list name (Entity List, Denied Persons, etc.)
 * 2: entity_number - Entity number from source
 * 3: type - Entity type: individual, entity, vessel, aircraft
 * 4: programs - Sanction programs (semicolon-separated)
 * 5: name - Primary name
 * 6: title - Title of individual
 * 7: addresses - Addresses (semicolon-separated)
 * 8-13: federal_register_notice, start_date, end_date, standard_order, license_requirement, license_policy
 * 14: call_sign - Vessel/aircraft call sign
 * 15: vessel_type - Vessel/aircraft type
 * 16: gross_tonnage
 * 17: gross_registered_tonnage
 * 18: vessel_flag
 * 19: vessel_owner
 * 20: remarks
 * 21: source_list_url
 * 22: alt_names - Alternate names (semicolon-separated)
 * 23: citizenships
 * 24: dates_of_birth
 * 25: nationalities
 * 26: places_of_birth
 * 27: source_information_url
 * 28: ids - Government IDs (semicolon-separated)
 */
public class CSLParserImpl implements CSLParser {

    // Keywords to detect business entities
    private static final Set<String> COMPANY_NEEDLES = Set.of(
        "academy", "aviation", "bank", "business", "co.", "commission",
        "committee", "company", "corporation", "defense", "electronics",
        "equipment", "export", "group", "guard", "holding", "import",
        "industrial", "industries", "industry", "institute", "intelligence",
        "international", "investment", "lab", "limited", "llc", "logistics",
        "ltd", "ltd.", "partnership", "revolutionary", "solutions",
        "subsidiary", "supply", "technology", "trading", "university"
    );

    @Override
    public List<Entity> parse(InputStream csvStream) {
        if (csvStream == null) {
            return List.of();
        }

        List<Entity> entities = new ArrayList<>();

        try (Reader reader = new InputStreamReader(csvStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT)) {

            for (CSVRecord record : parser) {
                if (record.size() < 29) {
                    continue; // Skip malformed rows
                }

                String id = cleanField(record.get(0));
                String source = cleanField(record.get(1));
                String entityNumber = cleanField(record.get(2));
                String type = cleanField(record.get(3));
                String programs = cleanField(record.get(4));
                String name = cleanField(record.get(5));
                String title = cleanField(record.get(6));
                String addresses = cleanField(record.get(7));
                String callSign = cleanField(record.get(14));
                String vesselType = cleanField(record.get(15));
                String grossTonnage = cleanField(record.get(16));
                String vesselFlag = cleanField(record.get(18));
                String vesselOwner = cleanField(record.get(19));
                String remarks = cleanField(record.get(20));
                String altNames = cleanField(record.get(22));
                String citizenships = cleanField(record.get(23));
                String birthDates = cleanField(record.get(24));
                String nationalities = cleanField(record.get(25));
                String birthPlaces = cleanField(record.get(26));
                String ids = cleanField(record.get(28));

                // Skip header row
                if (id.equalsIgnoreCase("_id") || name.isEmpty()) {
                    continue;
                }

                Entity entity = buildEntity(id, source, type, name, title, programs, 
                    addresses, callSign, vesselType, grossTonnage, vesselFlag, vesselOwner,
                    remarks, altNames, birthDates, birthPlaces, ids);
                
                if (entity != null) {
                    entities.add(entity);
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse CSL file", e);
        }

        return entities;
    }

    private Entity buildEntity(String id, String source, String type, String name, String title,
                                String programs, String addressesField, String callSign, 
                                String vesselType, String grossTonnage, String vesselFlag,
                                String vesselOwner, String remarks, String altNamesField,
                                String birthDates, String birthPlaces, String idsField) {
        
        EntityType entityType = determineEntityType(type, source, name);
        List<String> programList = parseSemicolonSeparated(programs);
        List<String> altNames = parseSemicolonSeparated(altNamesField);
        List<Address> addresses = parseAddresses(addressesField);
        
        Person person = null;
        Business business = null;
        Vessel vessel = null;
        Aircraft aircraft = null;

        switch (entityType) {
            case PERSON -> person = new Person(name, altNames, null, null, null, null, List.of(), List.of());
            case BUSINESS -> business = new Business(name, altNames, null, null, null);
            case VESSEL -> vessel = new Vessel(name, altNames, null, vesselType, vesselFlag, null, null, 
                callSign, grossTonnage, vesselOwner);
            case AIRCRAFT -> aircraft = new Aircraft(name, altNames, vesselType, vesselFlag, null, 
                callSign, null, null);
            default -> { }
        }

        SanctionsInfo sanctions = new SanctionsInfo(programList, false, null);

        return new Entity(
            id,
            name,
            entityType,
            SourceList.US_CSL,
            id,
            person,
            business,
            entityType == EntityType.ORGANIZATION ? new Organization(name, altNames, null, null, List.of()) : null,
            aircraft,
            vessel,
            null, // contact
            addresses,
            List.of(), // cryptoAddresses
            altNames,
            List.of(), // governmentIds
            sanctions,
            List.of(), // historicalInfo
            remarks,
            null // preparedFields - computed at index time
        );
    }

    private EntityType determineEntityType(String type, String source, String name) {
        String typeLower = type.toLowerCase();
        String sourceLower = source.toLowerCase();
        String nameLower = name.toLowerCase();

        return switch (typeLower) {
            case "individual" -> EntityType.PERSON;
            case "vessel" -> EntityType.VESSEL;
            case "aircraft" -> EntityType.AIRCRAFT;
            case "entity" -> {
                // Determine if business or organization based on source/name
                if (sourceLower.contains("military-industrial") || 
                    sourceLower.contains("cmic")) {
                    yield EntityType.ORGANIZATION;
                }
                // Check for company keywords
                if (isLikelyBusiness(nameLower)) {
                    yield EntityType.BUSINESS;
                }
                yield EntityType.BUSINESS;
            }
            default -> {
                // Try to infer from name
                if (isLikelyBusiness(nameLower)) {
                    yield EntityType.BUSINESS;
                }
                yield EntityType.UNKNOWN;
            }
        };
    }

    private boolean isLikelyBusiness(String nameLower) {
        for (String needle : COMPANY_NEEDLES) {
            if (nameLower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<Address> parseAddresses(String addressesField) {
        if (addressesField == null || addressesField.isBlank()) {
            return List.of();
        }

        List<Address> addresses = new ArrayList<>();
        String[] parts = addressesField.split(";");
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            
            // Try to parse "street, city, country" format
            String[] components = trimmed.split(",");
            String line1 = components.length > 0 ? components[0].trim() : "";
            String city = components.length > 1 ? components[1].trim() : "";
            String country = components.length > 2 ? components[2].trim() : "";
            
            if (!line1.isEmpty()) {
                addresses.add(new Address(line1, null, city, "", "", country));
            }
        }
        
        return addresses;
    }

    private List<String> parseSemicolonSeparated(String field) {
        if (field == null || field.isBlank()) {
            return List.of();
        }
        
        List<String> result = new ArrayList<>();
        String[] parts = field.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Integer parseTonnage(String tonnage) {
        if (tonnage == null || tonnage.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(tonnage.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.equals("-0-") || cleaned.equalsIgnoreCase("null")) {
            return "";
        }
        return cleaned;
    }
}
