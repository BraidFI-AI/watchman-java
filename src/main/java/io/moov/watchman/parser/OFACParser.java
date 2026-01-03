package io.moov.watchman.parser;

import io.moov.watchman.model.Entity;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Parser for OFAC SDN (Specially Designated Nationals) data files.
 * 
 * OFAC provides data in three CSV files:
 * - sdn.csv: Main entity records (name, type, program, etc.)
 * - add.csv: Address records linked by entity ID
 * - alt.csv: Alternate name records linked by entity ID
 */
public interface OFACParser {

    /**
     * Parse OFAC SDN data from CSV files.
     * 
     * @param sdnFile Path to sdn.csv file
     * @param addressFile Path to add.csv file  
     * @param altNamesFile Path to alt.csv file
     * @return List of parsed entities with addresses and alternate names merged
     */
    List<Entity> parse(Path sdnFile, Path addressFile, Path altNamesFile);

    /**
     * Parse OFAC SDN data from input streams.
     */
    List<Entity> parse(InputStream sdnStream, InputStream addressStream, InputStream altNamesStream);

    /**
     * Parse only the main SDN file (no addresses or alt names).
     */
    List<Entity> parseSdnOnly(InputStream sdnStream);
}
