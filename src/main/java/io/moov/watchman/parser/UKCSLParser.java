package io.moov.watchman.parser;

import io.moov.watchman.model.Entity;

import java.io.InputStream;
import java.util.List;

/**
 * Parser for UK Consolidated Financial Sanctions List (UK CSL).
 * 
 * UK sanctions list uses CSV format with 36 columns.
 * GroupType determines entity type: "Individual", "Entity", "Ship"
 * 
 * Download URL: https://ofsistorage.blob.core.windows.net/publishlive/2022format/ConList.csv
 */
public interface UKCSLParser {

    /**
     * Parse the UK CSL CSV file.
     *
     * @param csvStream Input stream of UK ConList.csv
     * @return List of parsed entities
     */
    List<Entity> parse(InputStream csvStream);
}
