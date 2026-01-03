package io.moov.watchman.parser;

import io.moov.watchman.model.Entity;

import java.io.InputStream;
import java.util.List;

/**
 * Parser for US Consolidated Screening List (CSL).
 * 
 * The CSL combines multiple export control screening lists:
 * - Bureau of Industry and Security (Entity List, Denied Persons, UVL, MEU)
 * - State Department (ITAR Debarred, Nonproliferation)
 * - Treasury (CMIC, SSI, FSE, CAP, NS-MBS, PLC)
 * 
 * Download URL: https://data.trade.gov/downloadable_consolidated_screening_list/v1/consolidated.csv
 */
public interface CSLParser {

    /**
     * Parse the US CSL CSV file.
     *
     * @param csvStream Input stream of consolidated.csv
     * @return List of parsed entities
     */
    List<Entity> parse(InputStream csvStream);
}
