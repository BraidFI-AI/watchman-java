package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

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

    // Keywords to detect business entities - normalized to match Go implementation
    private static final Set<String> COMPANY_NEEDLES = Set.of(
        "academy", "aviation", "bank", "business", "co", "commission",
        "committee", "company", "corporation", "defense", "electronics",
        "equipment", "export", "group", "guard", "holding", "import",
        "industrial", "industries", "industry", "institute", "intelligence",
        "international", "investment", "lab", "limited", "liability", "llc", "logistics",
        "ltd", "partnership", "revolutionary", "solutions",
        "subsidiary", "supply", "technology", "trading", "university"
    );

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NORMALIZE_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]");

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
        List<String> programList = parseDelimitedField(programs);
        List<Address> addresses = parseAddresses(addressesField);
        List<String> altNames = parseAlternateNames(altNamesField, name);

        return Entity.builder()
                .id(id)
                .source(source)
                .type(entityType)
                .name(normalizeName(name))
                .title(title)
                .programs(programList)
                .addresses(addresses)
                .alternateNames(altNames)
                .callSign(callSign)
                .vesselType(vesselType)
                .grossTonnage(grossTonnage)
                .vesselFlag(vesselFlag)
                .vesselOwner(vesselOwner)
                .remarks(remarks)
                .birthDates(parseDelimitedField(birthDates))
                .birthPlaces(parseDelimitedField(birthPlaces))
                .ids(parseDelimitedField(idsField))
                .build();
    }

    private EntityType determineEntityType(String type, String source, String name) {
        // Clean and normalize the type field
        String cleanType = cleanField(type).toLowerCase();
        
        // Handle specific type mappings first
        switch (cleanType) {
            case "individual":
                return EntityType.PERSON;
            case "vessel":
                return EntityType.VESSEL;
            case "aircraft":
                return EntityType.AIRCRAFT;
            case "entity":
                return EntityType.BUSINESS;
        }
        
        // For empty/unknown types, use name-based heuristics
        if (cleanType.isEmpty() || cleanType.equals("unknown")) {
            return classifyByName(name);
        }
        
        return EntityType.UNKNOWN;
    }

    private EntityType classifyByName(String name) {
        if (name == null || name.isEmpty()) {
            return EntityType.UNKNOWN;
        }
        
        // Normalize name for comparison - match Go implementation
        String normalized = NORMALIZE_PATTERN.matcher(name.toLowerCase()).replaceAll(" ");
        normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
        
        // Check for business indicators
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            if (COMPANY_NEEDLES.contains(word)) {
                return EntityType.BUSINESS;
            }
        }
        
        // Default to business for unknown entities to match Go behavior
        return EntityType.BUSINESS;
    }

    private String normalizeName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        
        // Normalize whitespace and remove extra punctuation to match Go
        String normalized = WHITESPACE_PATTERN.matcher(name.trim()).replaceAll(" ");
        return normalized;
    }

    private List<String> parseAlternateNames(String altNamesField, String primaryName) {
        List<String> names = new ArrayList<>();
        
        if (altNamesField != null && !altNamesField.isEmpty()) {
            String[] parts = altNamesField.split(";");
            for (String part : parts) {
                String cleaned = normalizeName(part.trim());
                // Only add if different from primary name and not empty
                if (!cleaned.isEmpty() && !cleaned.equalsIgnoreCase(primaryName)) {
                    names.add(cleaned);
                }
            }
        }
        
        return names;
    }

    private List<String> parseDelimitedField(String field) {
        if (field == null || field.isEmpty()) {
            return List.of();
        }
        
        List<String> items = new ArrayList<>();
        String[] parts = field.split(";");
        for (String part : parts) {
            String cleaned = part.trim();
            if (!cleaned.isEmpty()) {
                items.add(cleaned);
            }
        }
        return items;
    }

    private List<Address> parseAddresses(String addressesField) {
        if (addressesField == null || addressesField.isEmpty()) {
            return List.of();
        }
        
        List<Address> addresses = new ArrayList<>();
        String[] parts = addressesField.split(";");
        for (String part : parts) {
            String cleaned = part.trim();
            if (!cleaned.isEmpty()) {
                addresses.add(Address.builder().line1(cleaned).build());
            }
        }
        return addresses;
    }

    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}