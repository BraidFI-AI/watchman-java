# Nemesis 3-Way Comparison - Implementation Summary

## What Was Added

### 1. External Provider Support (ofac-api.com)
**File:** `scripts/nemesis/external_provider_adapter.py`
- Adapter for ofac-api.com v4 API
- Batch query support (send multiple names in one request)
- Format translation: ofac-api.com → standardized format
- SDN-only filtering for apples-to-apples comparison
- Score normalization (0-100 → 0.0-1.0)

### 2. Query Executor Enhancement
**File:** `scripts/nemesis/query_executor.py`
- Added `external_adapter` parameter
- Added `compare_external` flag to execute methods
- New `_call_external()` method for external provider queries
- Updated `QueryResult` to include external results/timings/errors

### 3. 3-Way Result Analyzer
**File:** `scripts/nemesis/result_analyzer.py`
- New `compare_three_way()` method
- New divergence types:
  - `THREE_WAY_SPLIT` - All three differ
  - `TWO_VS_ONE` - Two agree, one differs
  - `EXTERNAL_EXTRA_RESULT` - External found something others didn't
- Agreement pattern tracking:
  - `all_agree_entity` - All three found same entity
  - `java+go vs external` - Implementations agree, external differs
  - `java+external vs go` - Java matches external, Go differs
  - `go+external vs java` - Go matches external, Java differs
  - `all_differ` - No consensus

### 4. Updated Main Runner
**File:** `scripts/nemesis/run_nemesis.py`
- Added `COMPARE_EXTERNAL`, `EXTERNAL_PROVIDER`, `OFAC_API_KEY` config
- Initialize OFACAPIAdapter when enabled
- Pass external_adapter to QueryExecutor
- Use 3-way comparison when external enabled
- Fallback to 2-way comparison otherwise

### 5. On-Demand Trigger Script
**File:** `scripts/trigger-nemesis.sh`
- CLI script for manual Nemesis runs
- Options:
  - `--queries N` - Custom query count
  - `--compare-external` - Enable 3-way comparison
  - `--external-only` - Java vs External (skip Go)
  - `--no-go` - Skip Go comparison
  - `--output-dir PATH` - Custom report directory
- Validates OFAC_API_KEY when external comparison enabled
- Sets environment variables and runs Nemesis

### 6. Updated Documentation
**File:** `docs/NEMESIS.md`
- Added "Comparison Modes" section explaining 2-way vs 3-way
- Added external provider configuration
- Added on-demand trigger usage examples
- Updated deployment section with trigger script instructions

## Usage Examples

### Run with External Provider (3-way)
```bash
export OFAC_API_KEY='your-key-here'
./scripts/trigger-nemesis.sh --queries 50 --compare-external
```

### Run Java vs External Only
```bash
export OFAC_API_KEY='your-key-here'
./scripts/trigger-nemesis.sh --queries 100 --external-only
```

### Run Standard 2-Way (Java vs Go)
```bash
./scripts/trigger-nemesis.sh --queries 100
```

## Configuration

### Environment Variables
```bash
# Required for external comparison
export OFAC_API_KEY='your-api-key'

# Optional - provider selection (currently only ofac-api supported)
export EXTERNAL_PROVIDER='ofac-api'

# Optional - enable external comparison
export COMPARE_EXTERNAL=true
```

## Report Format Changes

Divergence entries now include:
```json
{
  "query": "Nicolas Maduro",
  "type": "two_vs_one",
  "severity": "moderate",
  "description": "Java+Go agree but External differs",
  "java_data": {...},
  "go_data": {...},
  "external_data": {...},
  "agreement_pattern": "java+go vs external"
}
```

## Technical Details

### API Format Differences
- **Java/Go:** Return entities with `match` score (0.0-1.0)
- **ofac-api.com:** Returns matches with `score` (0-100)
- **Adapter:** Normalizes external scores to 0.0-1.0 range

### ID Normalization
External provider may prefix IDs (e.g., "sdn-14121")
Analyzer strips prefixes for comparison:
- `sdn-` → removed
- `ofac-` → removed
- `un-` → removed
- `eu-` → removed

### SDN-Only Filtering
All three providers configured to search only OFAC SDN list:
- Java: Built-in SDN-only data
- Go: Built-in SDN-only data
- External: `sources: ["sdn"]` parameter

## Files Modified
- ✅ `scripts/nemesis/external_provider_adapter.py` (new)
- ✅ `scripts/nemesis/query_executor.py` (updated)
- ✅ `scripts/nemesis/result_analyzer.py` (updated)
- ✅ `scripts/nemesis/run_nemesis.py` (updated)
- ✅ `scripts/trigger-nemesis.sh` (new)
- ✅ `docs/NEMESIS.md` (updated)

## Testing Checklist
- [ ] Test trigger script without external provider (2-way)
- [ ] Test trigger script with external provider (3-way)
- [ ] Verify ofac-api.com adapter with real API key
- [ ] Check 3-way comparison logic with various agreement patterns
- [ ] Validate report format with external data
