# POST /v2/search - Runtime Config Override API

## Overview

The POST `/v2/search` endpoint extends the existing GET endpoint with the ability to override configuration parameters at runtime. This feature is designed for **admin and testing use cases** where users need to tune parameters, validate the model, and understand scoring behavior.

**Use Cases:**
- 🧪 Model validation and "what-if" scenario testing
- 🔧 Parameter tuning without restarting the service
- 📊 Understanding scoring behavior with different configs
- 🎯 Testing individual score calculations
- 🏦 Helping bank users tune thresholds for their use cases

## Key Design Principles

1. **Non-invasive**: GET endpoint unchanged - production traffic unaffected
2. **Request-scoped**: Each request creates isolated component instances (thread-safe)
3. **Partial overrides**: Only specify parameters you want to change
4. **Full observability**: Trace opt-in for debugging, config values always in metadata
5. **TDD approach**: Tests written first, implementation follows

## Architecture

### Request Flow

```
POST /v2/search
    ↓
SearchController.searchWithConfig()
    ↓
ConfigResolver.resolve(overrides)
    ├─ Merge similarity config (default + overrides)
    ├─ Merge scoring config (default + overrides)
    └─ Merge search config (default + overrides)
    ↓
Create request-scoped components:
    ├─ JaroWinklerSimilarity (with custom similarity config)
    └─ EntityScorerImpl (with custom scoring config)
    ↓
Score candidates with custom config
    ↓
Add config metadata to trace
    ↓
Return results with full trace
```

### Component Diagram

```
┌─────────────────────────────────────────────────────┐
│             SearchController                         │
│  ┌────────────┐    ┌─────────────────┐             │
│  │ GET search │    │ POST search     │             │
│  │  (prod)    │    │ (admin/testing) │             │
│  └────────────┘    └─────────────────┘             │
│                            ↓                         │
│                    ┌───────────────┐                │
│                    │ ConfigResolver │                │
│                    └───────────────┘                │
│                            ↓                         │
│            ┌───────────────┴───────────────┐        │
│            ↓                               ↓        │
│   ┌──────────────────┐        ┌──────────────────┐ │
│   │ JaroWinkler      │        │ EntityScorerImpl │ │
│   │ Similarity       │        │ (request-scoped) │ │
│   │ (request-scoped) │        │                  │ │
│   └──────────────────┘        └──────────────────┘ │
└─────────────────────────────────────────────────────┘
```

## API Specification

### Request Format

```json
POST /v2/search
Content-Type: application/json

{
  "query": {
    "name": "Juan Garcia",              // Required
    "addresses": ["123 Main St"],       // Optional
    "govIds": ["ABC123"],               // Optional
    "dateOfBirth": "1990-01-15",        // Optional
    "source": "OFAC_SDN",               // Optional filter
    "type": "PERSON"                    // Optional filter
  },
  "config": {                            // Optional - omit to use defaults
    "similarity": {
      "jaroWinklerBoostThreshold": 0.8,
      "jaroWinklerPrefixSize": 5,
      "phoneticFilteringDisabled": true,
      // ... 7 more optional fields
    },
    "scoring": {
      "nameWeight": 50.0,
      "addressWeight": 120.0,
      "addressEnabled": true,
      "dateEnabled": false,
      // ... 7 more optional fields
    },
    "search": {
      "minMatch": 0.85,
      "limit": 25
    }
  },
  "trace": false                         // Optional, defaults to false (enable for debugging)
}
```

### Response Format

```json
{
  "entities": [
    {
      "id": "56789",
      "name": "JUAN GARCIA RODRIGUEZ",
      "type": "PERSON",
      "source": "OFAC_SDN",
      "score": 0.91,
      "breakdown": {
        "primaryNameScore": 0.91,
        "components": [...]
      }
    }
  ],
  "totalResults": 1,
  "requestID": "req-456",
  "debug": {
    "traceId": "trace-def-456",
    "metadata": {
      "config.similarity.boost-threshold": 0.8,
      "config.similarity.prefix-size": 5,
      "config.similarity.phonetic-disabled": true,
      "config.scoring.name-weight": 50.0,
      "config.scoring.address-weight": 120.0,
      "config.scoring.address-enabled": true,
      "config.search.min-match": 0.85,
      "config.search.limit": 25
    },
    "events": [
      {
        "timestamp": "2026-01-14T00:00:00Z",
        "phase": "NORMALIZATION",
        "message": "Normalized query name: Juan Garcia -> juan garcia"
      }
    ]
  }
}
```

## Configuration Parameters

### Similarity Config (10 parameters)

Controls string matching behavior (Jaro-Winkler algorithm):

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `jaroWinklerBoostThreshold` | Double | 0.7 | Threshold for prefix boost (0.0-1.0) |
| `jaroWinklerPrefixSize` | Integer | 4 | Prefix length to favor (1-10) |
| `phoneticFilteringDisabled` | Boolean | false | Disable Soundex/Metaphone matching |
| `lengthDifferenceCutoffFactor` | Double | 3.0 | Reject candidates with length ratio > factor |
| `lengthDifferencePenaltyWeight` | Double | 0.15 | Penalty for length mismatch (0.0-1.0) |
| `differentLetterPenaltyWeight` | Double | 0.1 | Penalty for different letters (0.0-1.0) |
| `unmatchedIndexTokenWeight` | Double | 1.0 | Weight for unmatched tokens |
| `exactMatchFavoritism` | Double | 0.05 | Bonus for exact matches (0.0-1.0) |
| `keepStopwords` | Boolean | false | Keep stopwords in normalization |
| `logStopwordDebugging` | Boolean | false | Log stopword removal in trace |

### Scoring Config (11 parameters)

Controls component weights and feature toggles:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `nameWeight` | Double | 100.0 | Weight for primary name match |
| `addressWeight` | Double | 80.0 | Weight for address match |
| `criticalIdWeight` | Double | 200.0 | Weight for critical IDs (passport, SSN) |
| `supportingInfoWeight` | Double | 60.0 | Weight for supporting info |
| `nameEnabled` | Boolean | true | Include primary name in scoring |
| `altNamesEnabled` | Boolean | true | Include alternate names in scoring |
| `governmentIdEnabled` | Boolean | true | Include government IDs in scoring |
| `cryptoEnabled` | Boolean | true | Include crypto addresses in scoring |
| `contactEnabled` | Boolean | true | Include contact info in scoring |
| `addressEnabled` | Boolean | true | Include addresses in scoring |
| `dateEnabled` | Boolean | true | Include date of birth in scoring |

### Search Config (2 parameters)

Controls result filtering and limits:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `minMatch` | Double | 0.88 | Minimum score threshold (0.0-1.0) |
| `limit` | Integer | 10 | Maximum results to return (1-100) |

## Implementation Details

### DTOs (Data Transfer Objects)

**SearchRequestBody** - Main request wrapper
```java
public record SearchRequestBody(
    EntityQuery query,
    ConfigOverride config,
    Boolean trace
) {
    public SearchRequestBody {
        trace = trace != null ? trace : false;  // Default to false (opt-in for debugging)
    }
}
```

**EntityQuery** - Query parameters
```java
public record EntityQuery(
    String name,                // Required
    List<String> addresses,     // Optional
    List<String> govIds,        // Optional
    String dateOfBirth,         // Optional
    String source,              // Optional filter (OFAC_SDN, etc.)
    String type                 // Optional filter (PERSON, etc.)
) {}
```

**ConfigOverride** - Top-level config container
```java
public record ConfigOverride(
    SimilarityConfigOverride similarity,
    ScoringConfigOverride scoring,
    SearchConfigOverride search
) {}
```

All override fields are **nullable** - null means "use default".

### Configuration Resolution

**ConfigResolver** service merges defaults with overrides:

```java
@Service
public class ConfigResolver {
    private final SimilarityConfig defaultSimilarityConfig;
    private final ScoringConfig defaultScoringConfig;

    public ResolvedConfig resolve(ConfigOverride override) {
        if (override == null) {
            return defaults();
        }

        // Merge each section independently
        SimilarityConfig similarity = mergeSimilarity(override.similarity());
        ScoringConfig scoring = mergeScoring(override.scoring());
        SearchParams search = mergeSearch(override.search());

        return new ResolvedConfig(similarity, scoring, search);
    }

    private SimilarityConfig mergeSimilarity(SimilarityConfigOverride o) {
        return new SimilarityConfig(
            o.jaroWinklerBoostThreshold() != null
                ? o.jaroWinklerBoostThreshold()
                : defaultSimilarityConfig.getJaroWinklerBoostThreshold(),
            // ... repeat for all 10 fields
        );
    }
}
```

### Request-Scoped Component Creation

Components are created per-request with custom config (thread-safe):

```java
@PostMapping("/search")
public ResponseEntity<SearchResponse> searchWithConfig(@RequestBody SearchRequestBody body) {
    // 1. Resolve config
    ResolvedConfig config = configResolver.resolve(body.config());

    // 2. Create request-scoped components with custom config
    SimilarityService similarity = new JaroWinklerSimilarity(
        textNormalizer,
        phoneticFilter,
        config.similarity()  // Custom config
    );

    EntityScorer scorer = new EntityScorerImpl(
        similarity,
        config.scoring()  // Custom config
    );

    // 3. Score with custom components
    List<SearchResult> results = candidates.stream()
        .map(entity -> scorer.scoreWithBreakdown(body.query(), entity, ctx))
        .filter(result -> result.score() >= config.search().minMatch())
        .sorted(byScoreDesc)
        .limit(config.search().limit())
        .toList();

    return ResponseEntity.ok(response);
}
```

### Trace Integration

Config metadata is automatically added to trace output:

```java
private void addConfigMetadata(ScoringContext ctx, ResolvedConfig config) {
    // Similarity config
    ctx.withMetadata("config.similarity.boost-threshold",
        config.similarity().getJaroWinklerBoostThreshold());
    ctx.withMetadata("config.similarity.prefix-size",
        config.similarity().getJaroWinklerPrefixSize());
    // ... 8 more similarity params

    // Scoring config
    ctx.withMetadata("config.scoring.name-weight",
        config.scoring().getNameWeight());
    ctx.withMetadata("config.scoring.address-weight",
        config.scoring().getAddressWeight());
    // ... 9 more scoring params

    // Search config
    ctx.withMetadata("config.search.min-match", config.search().minMatch());
    ctx.withMetadata("config.search.limit", config.search().limit());
}
```

## Testing

### Unit Tests

**ConfigOverrideTest** - JSON deserialization (9 tests)
- Minimal config (query only)
- Partial similarity override
- Partial scoring override
- Partial search override
- Full config override
- All config fields
- Null handling
- Default trace behavior

**ConfigResolverTest** - Config merging (8 tests)
- Returns defaults when override is null
- Returns defaults when all fields are null
- Merges similarity config with partial overrides
- Merges scoring config with partial overrides
- Merges search config with partial overrides
- Handles all similarity fields
- Handles all scoring fields
- Handles edge cases

### Integration Testing

Use Postman collection with 5 comprehensive examples:
1. **POST - Minimal Query** - Uses all defaults
2. **POST - Override Similarity Config** - Test fuzzy matching parameters
3. **POST - Override Scoring Config** - Test component weights
4. **POST - Override Search Config** - Test threshold and limit
5. **POST - Full Config Override** - All three sections

## Usage Examples

### Example 1: Test Phonetic Matching Impact

Disable phonetic filtering to see pure string matching:

```bash
curl -X POST http://localhost:8084/v2/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"name": "Mohammad Ahmed"},
    "config": {
      "similarity": {
        "phoneticFilteringDisabled": true
      }
    }
  }'
```

Compare scores with phonetic enabled vs disabled.

### Example 2: Address-Focused Screening

Weight addresses higher than names:

```bash
curl -X POST http://localhost:8084/v2/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "name": "Jane Smith",
      "addresses": ["123 Main St, New York"]
    },
    "config": {
      "scoring": {
        "nameWeight": 50.0,
        "addressWeight": 120.0
      }
    }
  }'
```

### Example 3: Recall Testing

Lower threshold to find more candidates:

```bash
curl -X POST http://localhost:8084/v2/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"name": "Ahmed"},
    "config": {
      "search": {
        "minMatch": 0.70,
        "limit": 50
      }
    }
  }'
```

### Example 4: Model Validation

Test specific parameter combinations:

```bash
curl -X POST http://localhost:8084/v2/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": {"name": "John Doe"},
    "config": {
      "similarity": {
        "jaroWinklerBoostThreshold": 0.75,
        "exactMatchFavoritism": 0.10
      },
      "scoring": {
        "nameWeight": 80.0,
        "addressEnabled": false
      },
      "search": {
        "minMatch": 0.90
      }
    }
  }'
```

Check `debug.metadata` in response to verify applied config.

## Migration Guide

### For End Users

**No changes required!** The GET endpoint is unchanged:

```bash
# Still works exactly the same
GET /v2/search?name=John%20Doe&minMatch=0.85&trace=true
```

### For Admin/Testing Users

**New capability:** Use POST for config overrides:

```bash
# New admin/testing endpoint
POST /v2/search
{
  "query": {"name": "John Doe"},
  "config": {"search": {"minMatch": 0.85}},
  "trace": true
}
```

### For Developers

**Import the new DTOs:**
```java
import io.moov.watchman.api.SearchRequestBody;
import io.moov.watchman.api.ConfigOverride;
import io.moov.watchman.config.ConfigResolver;
```

**Use ConfigResolver in your code:**
```java
@Autowired
private ConfigResolver configResolver;

public void myMethod(ConfigOverride override) {
    ResolvedConfig config = configResolver.resolve(override);
    // Use resolved config
}
```

## Performance Considerations

### Memory

- **Request-scoped components**: Each POST request creates new component instances
- **Memory overhead**: ~1-2KB per request for component creation
- **GC impact**: Minimal - objects are short-lived

### Latency

- **Config resolution**: < 1ms (simple object creation and merging)
- **Component creation**: < 1ms (lightweight constructors)
- **Overall impact**: < 5ms per request vs GET endpoint

### Concurrency

- **Thread-safe**: Each request has isolated components
- **No synchronization**: No shared mutable state
- **Scalability**: Same as GET endpoint (stateless)

## Security Considerations

### Access Control

⚠️ **Recommendation**: Restrict POST endpoint to admin/internal users only.

```java
@PostMapping("/search")
@PreAuthorize("hasRole('ADMIN')")  // Add authorization
public ResponseEntity<SearchResponse> searchWithConfig(...) {
    // ...
}
```

### Input Validation

Current validation:
- ✅ Query name is required and non-blank
- ✅ All config fields are optional (nullable)
- ✅ Spring Boot validates JSON structure

Future enhancements:
- Range validation (e.g., minMatch between 0.0 and 1.0)
- Limit max values (e.g., limit ≤ 100)
- Source/type enum validation

### Rate Limiting

Consider rate limiting POST endpoint separately from GET:

```java
@RateLimit(key = "post-search", limit = 100, window = "1m")
@PostMapping("/search")
public ResponseEntity<SearchResponse> searchWithConfig(...) {
    // ...
}
```

## Future Enhancements

### Potential Improvements

1. **Config Presets**: Save/load named config presets
   ```json
   {
     "query": {"name": "Test"},
     "configPreset": "high-precision"
   }
   ```

2. **Batch POST**: Support multiple queries with same config
   ```json
   {
     "queries": [
       {"name": "John Doe"},
       {"name": "Jane Smith"}
     ],
     "config": {...}
   }
   ```

3. **Config Validation**: Return validation errors for invalid values
   ```json
   {
     "error": "minMatch must be between 0.0 and 1.0, got: 1.5"
   }
   ```

4. **A/B Testing**: Compare two configs side-by-side
   ```json
   {
     "query": {"name": "Test"},
     "configs": [
       {"search": {"minMatch": 0.85}},
       {"search": {"minMatch": 0.90}}
     ]
   }
   ```

## Troubleshooting

### Issue: Config not being applied

**Symptom**: Trace metadata shows default values instead of overrides

**Solution**: Ensure field names match exactly (case-sensitive):
```json
{
  "config": {
    "similarity": {
      "jaroWinklerBoostThreshold": 0.8  // Correct: camelCase
      // NOT "jaro_winkler_boost_threshold" (snake_case)
    }
  }
}
```

### Issue: Trace not enabled

**Symptom**: `debug` field is null in response

**Solution**: Trace defaults to `false` (opt-in for debugging). Explicitly enable it:
```json
{
  "query": {"name": "Test"},
  "trace": true  // Must explicitly enable for debugging
}
```

**Note**: Trace is disabled by default because it adds overhead and is only needed for debugging/tuning.

### Issue: Null pointer exception

**Symptom**: Server returns 500 error

**Solution**: Ensure query.name is provided:
```json
{
  "query": {
    "name": "John Doe"  // Required!
  }
}
```

## FAQ

**Q: Can I use POST for production traffic?**
A: Yes, but GET is recommended for production. POST is designed for admin/testing use cases.

**Q: Does POST affect GET endpoint behavior?**
A: No. POST creates request-scoped components - zero impact on GET endpoint.

**Q: What happens if I provide invalid config values?**
A: Currently, invalid values are accepted (no validation). Future enhancement: return 400 errors.

**Q: Can I override just one parameter?**
A: Yes! All fields are nullable. Only specify what you want to change.

**Q: How do I know which config was actually used?**
A: Check `debug.metadata` in the response - it shows all applied config values.

**Q: Is trace enabled by default for POST?**
A: No, trace defaults to `false` (same as GET). Explicitly set `"trace": true` to enable for debugging/tuning.

**Q: Can I use this for batch screening?**
A: Not yet. Currently single-query only. Batch support is a future enhancement.

## References

- **Postman Collection**: `postman/Watchman-Java-API.postman_collection.json`
- **Source Code**: `src/main/java/io/moov/watchman/api/SearchController.java`
- **Tests**: `src/test/java/io/moov/watchman/api/ConfigOverrideTest.java`
- **Config Resolution**: `src/main/java/io/moov/watchman/config/ConfigResolver.java`

## Changelog

### 2026-01-14 - Initial Release

- ✅ POST /v2/search endpoint
- ✅ 23 configurable parameters (10 similarity, 11 scoring, 2 search)
- ✅ Request-scoped component creation
- ✅ Automatic trace metadata injection
- ✅ 17 unit tests (9 DTO + 8 resolver)
- ✅ 5 Postman examples
- ✅ Comprehensive documentation

---

**Questions or feedback?** Open an issue on GitHub or reach out to the team.
