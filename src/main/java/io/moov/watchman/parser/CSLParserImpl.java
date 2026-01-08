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

    // Keywords to detect business entities - aligned with Go implementation
    private static final Set<String> COMPANY_NEEDLES = Set.of(
        "academy", "aviation", "bank", "business", "co", "co.", "commission",
        "committee", "company", "corp", "corp.", "corporation", "defense", "electronics",
        "equipment", "export", "group", "guard", "holding", "import",
        "industrial", "industries", "industry", "institute", "intelligence",
        "international", "investment", "lab", "laboratory", "limited", "llc", "logistics",
        "ltd", "ltd.", "partnership", "revolutionary", "solutions",
        "subsidiary", "supply", "technology", "trading", "university", "inc", "inc."
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
        List<String> programList = parseList(programs);
        List<String> altNamesList = parseList(altNamesField);
        List<Address> addresses = parseAddresses(addressesField);
        List<String> idsList = parseList(idsField);

        return Entity.builder()
                .id(id)
                .name(name)
                .source(SourceList.CSL)
                .type(entityType)
                .programs(programList)
                .title(cleanField(title))
                .addresses(addresses)
                .altNames(altNamesList)
                .citizenships(parseList(""))
                .dates(parseList(birthDates))
                .places(parseList(birthPlaces))
                .ids(idsList)
                .remarks(cleanField(remarks))
                .callSign(cleanField(callSign))
                .vesselType(cleanField(vesselType))
                .tonnage(cleanField(grossTonnage))
                .flag(cleanField(vesselFlag))
                .owner(cleanField(vesselOwner))
                .build();
    }

    private EntityType determineEntityType(String type, String source, String name) {
        // Handle explicit types first
        if (type != null && !type.isEmpty()) {
            String normalizedType = type.toLowerCase().trim();
            
            switch (normalizedType) {
                case "individual":
                    return EntityType.PERSON;
                case "entity":
                    return EntityType.BUSINESS;
                case "vessel":
                    return EntityType.VESSEL;
                case "aircraft":
                    return EntityType.AIRCRAFT;
            }
        }
        
        // For empty or unrecognized types, use name-based detection
        if (name != null && !name.isEmpty()) {
            String lowerName = name.toLowerCase();
            
            // Check for business indicators in name - more strict matching
            for (String needle : COMPANY_NEEDLES) {
                if (containsWholeWord(lowerName, needle)) {
                    return EntityType.BUSINESS;
                }
            }
        }
        
        // Default for CSL - if no clear indicators, assume business entity
        return EntityType.BUSINESS;
    }
    
    private boolean containsWholeWord(String text, String word) {
        // Match whole words only, not partial matches
        String pattern = "\\b" + word.replace(".", "\\.") + "\\b";
        return text.matches(".*" + pattern + ".*");
    }

    private List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        
        String[] parts = value.split(";");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String cleaned = part.trim();
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<Address> parseAddresses(String addressesField) {
        if (addressesField == null || addressesField.trim().isEmpty()) {
            return List.of();
        }

        String[] addresses = addressesField.split(";");
        List<Address> result = new ArrayList<>();
        
        for (String addr : addresses) {
            String cleaned = addr.trim();
            if (!cleaned.isEmpty()) {
                result.add(Address.of(cleaned));
            }
        }
        
        return result;
    }

    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}