package io.moov.watchman.parser;

import io.moov.watchman.model.Entity;

import java.io.InputStream;
import java.util.List;

/**
 * Parser for EU Consolidated Sanctions List (EU CSL).
 * 
 * EU sanctions list uses semicolon-delimited CSV with ~90 columns.
 * 
 * Download URL: https://webgate.ec.europa.eu/fsd/fsf/public/files/csvFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw
 */
public interface EUCSLParser {

    /**
     * Parse the EU CSL CSV file.
     *
     * @param csvStream Input stream of EU sanctions CSV (semicolon-delimited)
     * @return List of parsed entities
     */
    List<Entity> parse(InputStream csvStream);
}
