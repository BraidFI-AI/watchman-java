# Nemesis Implementation Summary

## Recent Updates

### January 11, 2026
- ✅ **Trace Simplification**: Enable trace from start, removed Step 5a re-querying
- ✅ **Two-Bucket Repair Pipeline**: Parity fixes + OFAC-API intelligence with test generation

---

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

# Required for repair pipeline
export GITHUB_TOKEN='your-github-token'
export OPENAI_API_KEY='your-openai-key'  # or ANTHROPIC_API_KEY
export GITHUB_REPO='BraidFI-AI trace data and 3-way comparison:
```json
{
  "query": "Nicolas Maduro",
  "type": "two_vs_one",
  "severity": "moderate",
  "description": "Java+Go agree but External differs",
  "java_data": {
    "id": "14121",
    "name": "MADURO MOROS, Nicolas",
    "score": 0.92
  },
  "go_data": {
    "id": "14121",
    "name": "MADURO MOROS, Nicolas",
    "score": 0.92
  },
  "external_data": {
    "id": "14121",
    "name": "MADURO MOROS, Nicolas",
    "score": 0.95
  },
  "agreement_pattern": "java+go vs external",
  "score_difference": 0.03,
  "java_trace": {
    "sessionId": "uuid",
    "durationMs": 45,
    "breakdown": {
      "nameScore": 0.92,
      "addressScore": 0.0,
      "totalWeightedScore": 0.92
    },
    "events": [
      {
        "phase": "NAME_COMPARISON",
        "description": "Comparing query name with candidate primary name",
        "timestamp": "2026-01-11T10:15:23.456Z",
        "data": {
          "durationMs": 12,
          "queryName": "Nicolas Maduro",
          "candidateName": "MADURO MOROS, Nicolas",
          "similarity": 0.92
        }
      }
    ]
  }

**Status:** Both currently DISABLED (commented out in `scripts/crontab`)  
**To Enable:** Uncomment lines 5 and 8 in crontab

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

### Core Nemesis Features
- ✅ `scripts/nemesis/external_provider_adapter.py` (new)
- ✅ `scripts/nemesis/query_executor.py` (updated - trace support)
- ✅ `scripts/nemesis/result_analyzer.py` (updated - 3-way comparison)
- ✅ `scripts/nemesis/run_nemesis.py` (updated - trace from start, Step 5a removed)
- ✅ `scripts/trigger-nemesis.sh` (new)
- ✅ `docs/NEMESIS.md` (updated)

### Repair Pipeline Enhancements
- ✅ `scripts/nemesis/fix_generator.py` (updated - two-bucket approach, test generation)
- ✅ `scripts/nemesis/fix_applicator.py` (updated - enhanced PR template)
- ✅ `scripts/nemesis/repair_agent.py` (existing - issue classification)
- ✅ `scripts/nemesis/code_analyzer.py` (existing - maps issues to code)
- ✅ `scripts/nemesis/ai_analyzer.py` (existing - pattern identification)
- ✅ `scripts/run_repair_pipeline.py` (existing - orchestrator)

### Deployment Configuration
- ✅ `scripts/crontab` (DISABLED - needs re-enabling for automated runs)

### 7. Trace Integration (January 2026)
**Files:** `query_executor.py`, `run_nemesis.py`

**Changes:**
- Enable `trace=true` for ALL queries from the start (not just divergences)
- Removed Step 5a (re-querying with trace) - no longer needed
- Every divergence now includes trace data automatically
- Trace data stored in `java_trace` field of divergence report

**Benefits:**
- Faster execution (~30s for 5 queries vs ~60s with re-querying)
- Cleaner code (no duplicate query logic)
- All divergences have trace data for analysis
- Zero overhead when trace disabled in production

**Configuration:**
```python
# In execute_batch() call
results = executor.execute_batch(
    queries=queries,
    compare_go=True,
    compare_external=True,
    enable_trace=True  # Now enabled from start
)
```

### 8. Two-Bucket Repair Pipeline (January 11, 2026)
**Files:** `fix_generator.py`, `fix_applicator.py`

**Philosophy:**
- **OFAC-API**: Commercial gold standard (ground truth)
- **Moov/Go**: Parity baseline (but may have bugs from multiple contributors)
- **Java**: Must match Go (parity), but can propose improvements based on OFAC-API

**Bucket 1: Parity Fix (Ready to Merge)**
- Generate code changes to make Java match Go exactly
- Include JUnit test cases using actual divergence data
- Tests prove Java now matches Go scores
- Validate trace breakdown correctness
- This is objective #1: measurable technical parity

**Bucket 2: OFAC-API Intelligence (Discussion Only)**
- Only when OFAC-API differs significantly (>0.3 score difference)
- Analyze trace data to understand WHY OFAC-API scored differently
- Propose improvements based on commercial best practices
- Include risk assessment (false positives/negatives)
- Clearly marked "for discussion" - NOT ready to merge

**AI System Prompt Enhancements:**
- Context about three implementations and their roles
- 3-way comparison with agreement patterns
- Test case generation requirements
- Two-bucket output format

**PR Template Structure:**
```markdown
## ✅ BUCKET 1: PARITY FIX (Ready to Merge)
- Root cause analysis
- Code changes
- Test cases
- Parity achievement metrics

## 💡 BUCKET 2: OFAC-API INTELLIGENCE (Discussion Only)
- OFAC-API behavior analysis
- Observations from trace data
- Proposed improvement (optional)
- Risk assessment

## Review Checklist
- Code review
- Testing
- Parity verification
- OFAC-API consideration
```

**Enhanced Divergence Formatting:**
- Shows 3-way comparison (Java, Go, OFAC-API)
- Agreement patterns with interpretation:
  - "Go and OFAC-API agree - Java should match them"
  - "Java and OFAC-API agree - Go may be wrong"
  - "All three disagree - needs investigation"
- Includes trace data for each divergence

## Testing Checklist
- [x] Test trigger script without external provider (2-way)
- [x] Test trigger script with external provider (3-way)
- [x] Verify ofac-api.com adapter with real API key
- [x] Check 3-way comparison logic with various agreement patterns
- [x] Validate report format with external data
- [x] Verify trace integration works from start
- [x] Test repair pipeline with two-bucket approach
- [ ] Run full Nemesis with repair pipeline in production
