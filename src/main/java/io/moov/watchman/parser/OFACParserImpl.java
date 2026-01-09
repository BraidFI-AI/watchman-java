package io.moov.watchman.parser;

import io.moov.watchman.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Implementation of OFAC SDN file parser.
 * 
 * Parses the three OFAC CSV files:
 * - SDN.CSV: Main entity data (12 columns)
 * - ADD.CSV: Address data (6 columns)
 * - ALT.CSV: Alternate names (5 columns)
 * 
 * Ported from Go implementation: pkg/sources/ofac/reader.go
 */
public class OFACParserImpl implements OFACParser {

    private final EntityTypeParser typeParser;

    public OFACParserImpl() {
        this.typeParser = new EntityTypeParser();
    }

    @Override
    public List<Entity> parse(Path sdnFile, Path addressFile, Path altNamesFile) {
        try {
            InputStream sdnStream = sdnFile != null ? Files.newInputStream(sdnFile) : null;
            InputStream addressStream = addressFile != null ? Files.newInputStream(addressFile) : null;
            InputStream altNamesStream = altNamesFile != null ? Files.newInputStream(altNamesFile) : null;
            
            return parse(sdnStream, addressStream, altNamesStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OFAC files", e);
        }
    }

    @Override
    public List<Entity> parse(InputStream sdnStream, InputStream addressStream, InputStream altNamesStream) {
        // Parse addresses and alt names first (they reference entity IDs)
        Map<String, List<Address>> addressesByEntity = new HashMap<>();
        Map<String, List<String>> altNamesByEntity = new HashMap<>();
        
        if (addressStream != null) {
            addressesByEntity = parseAddresses(addressStream);
        }
        
        if (altNamesStream != null) {
            altNamesByEntity = parseAltNames(altNamesStream);
        }
        
        // Parse main SDN file and merge addresses/alt names
        return parseSdn(sdnStream, addressesByEntity, altNamesByEntity);
    }

    @Override
    public List<Entity> parseSdnOnly(InputStream sdnStream) {
        return parseSdn(sdnStream, Map.of(), Map.of());
    }

    /**
     * Parse the main SDN.CSV file.
     * 
     * Columns (12 total):
     * 0: Ent_num (Entity ID)
     * 1: SDN_Name (Name)
     * 2: SDN_Type (Individual, Entity, Vessel, Aircraft)
     * 3: Program (sanctions program)
     * 4: Title
     * 5: Call_Sign (vessel)
     * 6: Vess_type (vessel type)
     * 7: Tonnage
     * 8: GRT (gross registered tonnage)
     * 9: Vess_flag (vessel flag/country)
     * 10: Vess_owner
     * 11: Remarks
     */
    private List<Entity> parseSdn(
            InputStream sdnStream,
            Map<String, List<Address>> addressesByEntity,
            Map<String, List<String>> altNamesByEntity) {
        
        if (sdnStream == null) {
            return List.of();
        }
        
        List<Entity> entities = new ArrayList<>();
        
        try (Reader reader = new InputStreamReader(sdnStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT)) {
            
            for (CSVRecord record : parser) {
                if (record.size() < 12) {
                    continue; // Skip malformed rows
                }
                
                String entityId = cleanField(record.get(0));
                String name = cleanField(record.get(1));
                String sdnType = cleanField(record.get(2));
                String program = cleanField(record.get(3));
                String title = cleanField(record.get(4));
                String callSign = cleanField(record.get(5));
                String vesselType = cleanField(record.get(6));
                String tonnage = cleanField(record.get(7));
                String grt = cleanField(record.get(8));
                String vesselFlag = cleanField(record.get(9));
                String vesselOwner = cleanField(record.get(10));
                String remarks = cleanField(record.get(11));
                
                if (entityId.isEmpty() || name.isEmpty()) {
                    continue; // Skip empty records
                }
                
                // Skip header rows (entity ID should be numeric)
                if (entityId.equalsIgnoreCase("ent_num") || !isNumericOrValidId(entityId)) {
                    continue;
                }
                
                EntityType type = typeParser.parse(sdnType);
                List<Address> addresses = addressesByEntity.getOrDefault(entityId, List.of());
                List<String> altNames = altNamesByEntity.getOrDefault(entityId, List.of());
                
                // Build the entity based on type
                Entity entity = buildEntity(
                    entityId, name, type, program, title,
                    callSign, vesselType, tonnage, grt, vesselFlag, vesselOwner,
                    remarks, addresses, altNames
                );
                
                entities.add(entity);
            }
            
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse SDN file", e);
        }
        
        return entities;
    }

    /**
     * Parse the ADD.CSV file (addresses).
     * 
     * Columns (6 total):
     * 0: Ent_num (Entity ID)
     * 1: Add_num (Address ID)
     * 2: Address
     * 3: City/State/Province/Postal Code
     * 4: Country
     * 5: Add_remarks
     */
    private Map<String, List<Address>> parseAddresses(InputStream addressStream) {
        Map<String, List<Address>> result = new HashMap<>();
        
        try (Reader reader = new InputStreamReader(addressStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT)) {
            
            for (CSVRecord record : parser) {
                if (record.size() < 6) {
                    continue;
                }
                
                String entityId = cleanField(record.get(0));
                String addressLine = cleanField(record.get(2));
                String cityStateZip = cleanField(record.get(3));
                String country = cleanField(record.get(4));
                
                if (entityId.isEmpty()) {
                    continue;
                }
                
                // Skip header rows
                if (entityId.equalsIgnoreCase("ent_num") || !isNumericOrValidId(entityId)) {
                    continue;
                }
                
                // Parse city/state/zip if possible
                String city = "";
                String state = "";
                String postalCode = "";
                
                if (!cityStateZip.isEmpty()) {
                    // Try to extract components - this is best effort
                    String[] parts = cityStateZip.split(",");
                    if (parts.length >= 1) {
                        city = parts[0].trim();
                    }
                    if (parts.length >= 2) {
                        state = parts[1].trim();
                    }
                }
                
                Address address = new Address(
                    addressLine,
                    null, // line2
                    city,
                    state,
                    postalCode,
                    country
                );
                
                result.computeIfAbsent(entityId, k -> new ArrayList<>()).add(address);
            }
            
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse address file", e);
        }
        
        return result;
    }

    /**
     * Parse the ALT.CSV file (alternate names).
     * 
     * Columns (5 total):
     * 0: Ent_num (Entity ID)
     * 1: Alt_num (Alt ID)
     * 2: Alt_type (aka, fka, nka)
     * 3: Alt_name
     * 4: Alt_remarks
     */
    private Map<String, List<String>> parseAltNames(InputStream altStream) {
        Map<String, List<String>> result = new HashMap<>();
        
        try (Reader reader = new InputStreamReader(altStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT)) {
            
            for (CSVRecord record : parser) {
                if (record.size() < 5) {
                    continue;
                }
                
                String entityId = cleanField(record.get(0));
                String altName = cleanField(record.get(3));
                
                if (entityId.isEmpty() || altName.isEmpty()) {
                    continue;
                }
                
                // Skip header rows
                if (entityId.equalsIgnoreCase("ent_num") || !isNumericOrValidId(entityId)) {
                    continue;
                }
                
                result.computeIfAbsent(entityId, k -> new ArrayList<>()).add(altName);
            }
            
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse alt names file", e);
        }
        
        return result;
    }

    private Entity buildEntity(
            String entityId, String name, EntityType type, String program,
            String title, String callSign, String vesselType, String tonnage,
            String grt, String vesselFlag, String vesselOwner, String remarks,
            List<Address> addresses, List<String> altNames) {
        
        // Build type-specific details
        Person person = null;
        Business business = null;
        Vessel vessel = null;
        Aircraft aircraft = null;
        
        switch (type) {
            case PERSON -> person = Person.of(name);
            case BUSINESS -> business = Business.of(name);
            case VESSEL -> vessel = new Vessel(
                name, altNames, null, vesselType, vesselFlag, null, null, callSign, tonnage, vesselOwner
            );
            case AIRCRAFT -> aircraft = new Aircraft(
                name, altNames, vesselType, vesselFlag, null, callSign, null, null
            );
            default -> { }
        }
        
        // Parse sanctions info from program
        List<String> programs = parsePrograms(program);
        SanctionsInfo sanctions = SanctionsInfo.of(programs);
        
        return new Entity(
            entityId,
            name,
            type,
            SourceList.US_OFAC,
            entityId,
            person,
            business,
            null, // organization
            aircraft,
            vessel,
            null, // contact
            addresses,
            List.of(), // cryptoAddresses
            altNames,
            List.of(), // governmentIds
            sanctions,
            remarks,
            null // preparedFields - computed at index time
        );
    }
    
    private String cleanField(String value) {
        if (value == null) {
            return "";
        }
        // Remove -0- prefix used in OFAC for null/empty
        String cleaned = value.trim();
        if (cleaned.equals("-0-") || cleaned.equals("null")) {
            return "";
        }
        // Remove leading -0- markers
        if (cleaned.startsWith("-0-")) {
            cleaned = cleaned.substring(3).trim();
        }
        return cleaned;
    }
    
    private List<String> parsePrograms(String programField) {
        if (programField == null || programField.isBlank()) {
            return List.of();
        }
        // Programs can be semicolon or space separated
        String[] parts = programField.trim().split("[;\\s]+");
        List<String> programs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                programs.add(trimmed);
            }
        }
        return programs;
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
    
    /**
     * Check if a string looks like a valid entity ID (numeric or alphanumeric code).
     * Used to skip header rows where ID column contains "ent_num" etc.
     */
    private boolean isNumericOrValidId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        // Most OFAC IDs are purely numeric, but we also allow alphanumeric
        // Header values like "ent_num", "Add_num" contain underscores
        return !id.contains("_") && !id.contains(" ");
    }
}
