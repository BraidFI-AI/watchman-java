# Watchman Java

A Java port of [Moov Watchman](https://github.com/moov-io/watchman) - a sanctions screening and compliance platform.

## Overview

Watchman provides real-time screening against global sanctions watchlists (OFAC, CSL) with fuzzy name matching using Jaro-Winkler similarity scoring.

## Features

- **OFAC SDN Screening** - Search against US Treasury OFAC Specially Designated Nationals list
- **Fuzzy Name Matching** - Jaro-Winkler algorithm with phonetic filtering and custom modifications
- **Multiple Entity Types** - Person, Business, Organization, Aircraft, Vessel
- **REST API** - Compatible with original Watchman API

## Building

```bash
mvn clean compile
```

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JaroWinklerSimilarityTest

# Run simulation tests only
mvn test -Dtest=ScreeningSimulationTest
```

## Project Structure

```
src/
├── main/java/io/moov/watchman/
│   ├── model/           # Domain models (Entity, Person, Business, etc.)
│   ├── similarity/      # Jaro-Winkler scoring engine
│   ├── parser/          # OFAC CSV file parsers
│   ├── search/          # Search service
│   ├── download/        # Data download service
│   └── index/           # In-memory entity index
└── test/java/io/moov/watchman/
    ├── similarity/      # Similarity algorithm tests
    ├── parser/          # Parser tests
    ├── search/          # Search service tests
    └── simulation/      # End-to-end screening tests
```

## Test-Driven Development

This project follows TDD. Tests are written first based on the Go reference implementation:

1. `JaroWinklerSimilarityTest` - Core fuzzy matching algorithm
2. `EntityNameComparisonTest` - Entity name comparison logic
3. `OFACParserTest` - OFAC CSV file parsing
4. `SearchServiceTest` - Search ranking and scoring
5. `ScreeningSimulationTest` - End-to-end with real OFAC data

## Reference Implementation

Test cases are ported from the Go implementation:
- `internal/stringscore/jaro_winkler_test.go`
- `pkg/search/similarity_fuzzy_test.go`
- `pkg/search/similarity_exact_test.go`

## License

Apache License 2.0
