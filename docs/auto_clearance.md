# Auto-Clearance

## Summary

Auto-Clearance automatically identifies and clears false positive OFAC matches, reducing manual review workload while maintaining compliance standards. It uses a two-phase approach: Phase 1 detects candidates based solely on name similarity; Phase 2 clears matches that are distinguishable by address, date of birth, or government ID.

**Status:** Production
**Endpoint:** `POST /v1/search/autoclearance`

---

## Discriminators

A **discriminator** is an identity attribute that can definitively distinguish two people who share the same name. For a discriminator to participate in clearance logic, the attribute must be present in **both** the search query and the sanctioned entity record. If either side is missing the attribute, the discriminator is skipped.

The system implements exactly three discriminators:

| Discriminator | Evaluation type | Clears when |
|---|---|---|
| `address` | Fuzzy — produces a similarity score | Score < `address-mismatch-threshold` (default 0.50) |
| `dob` | Exact — year comparison only | Difference > `dob-difference-threshold-years` (default 1) |
| `governmentId` | Exact — case-insensitive string comparison | IDs are present on both sides but do not match |

**Evaluation mode details:**
- **Fuzzy** (`address`): `DiscriminatorScore` carries a numeric `score`, a `threshold`, and `matched = (score >= threshold)`. See `DiscriminatorScore.fuzzy()` in [DiscriminatorScore.java](../src/main/java/io/moov/watchman/search/DiscriminatorScore.java).
- **Exact** (`dob`, `governmentId`): `DiscriminatorScore` carries only a boolean `matched`. No score or threshold is surfaced. See `DiscriminatorScore.exact()`.

**Clearance logic:** if **any** discriminator returns `matched = false`, the entity is `AUTO_CLEARED`. If all available discriminators return `matched = true`, or no discriminators are available, the entity requires `MANUAL_REVIEW`.

---

## How It Works

### Phase 1: Name Detection

Evaluates name similarity against the full sanctions index (~49,955 entities). Address, DOB, and government IDs are excluded from scoring at this phase.

- Threshold: 85% name similarity (configurable via `watchman.auto-clearance.phase1-threshold`)
- All entities scoring ≥85% become Phase 1 matches and proceed to Phase 2
- Entities below threshold are excluded from the response

### Phase 2: Discriminator-Based Clearance

For each Phase 1 match, available discriminators are evaluated. If **any** discriminator indicates a clear mismatch, the match is auto-cleared. If all discriminators either match or are unavailable, the match requires manual review.

**Address clearance**
- Fuzzy match between query address and all entity address types
- Auto-clear condition: similarity < 50% (configurable via `address-mismatch-threshold`)

**Date of birth clearance**
- Exact year comparison (no fuzzy logic)
- Auto-clear condition: DOB difference > 1 year (configurable via `dob-difference-threshold-years`)
- Example: query DOB 1985, entity DOB 1990 → 5-year difference → AUTO_CLEARED

**Government ID clearance**
- Case-insensitive exact match for passport, SSN, national ID
- Auto-clear condition: IDs are present in both query and entity but do not match
- Example: query "A12345678", entity "B98765432" → AUTO_CLEARED

When no discriminators are available, the match status is `PENDING` (defaults to manual review).

---

## API

### Request

```http
POST /v1/search/autoclearance
Content-Type: application/json

{
  "name": "John Smith",
  "address": "123 Main St, New York, NY 10001",
  "dob": "1985-06-15",
  "governmentId": "123-45-6789"
}
```

### Response

```json
{
  "query": {
    "name": "John Smith",
    "address": { "line1": "123 Main St", "city": "New York", "state": "NY", "zip": "10001" }
  },
  "phase1": {
    "totalMatches": 3,
    "threshold": 0.85,
    "results": [
      { "entity": { "..." : "..." }, "score": 0.95, "detection": "INCLUDED" }
    ]
  },
  "autoClearance": {
    "autoClearedCount": 2,
    "manualReviewCount": 1,
    "results": [
      {
        "entityId": "12345",
        "entityName": "JOHN SMITH",
        "phase1Score": 0.95,
        "status": "AUTO_CLEARED",
        "reason": "Address mismatch",
        "discriminators": {
          "address": { "score": 0.25, "threshold": 0.50, "matched": false },
          "dob": { "matched": false, "differenceYears": 5 },
          "governmentId": { "matched": false }
        }
      }
    ]
  },
  "summary": {
    "phase1Matches": 3,
    "autoClearedMatches": 2,
    "manualReviewRequired": 1
  },
  "finalStatus": "MANUAL_REVIEW_REQUIRED"
}
```

### Status values

| Status | Meaning |
|--------|---------|
| `AUTO_CLEARED` | At least one discriminator confirmed a mismatch |
| `MANUAL_REVIEW` | No discriminator mismatch found |
| `PENDING` | No discriminators available for evaluation |

### finalStatus values

| finalStatus | Meaning |
|-------------|---------|
| `NO_MATCH` | No Phase 1 matches found |
| `AUTO_CLEARED` | All Phase 1 matches were auto-cleared |
| `MANUAL_REVIEW_REQUIRED` | At least one match requires review |

---

## Configuration

All thresholds are externalized in `application.yml` and tunable at runtime via the Admin API.

```yaml
watchman:
  auto-clearance:
    phase1-threshold: 0.85
    address-mismatch-threshold: 0.50
    dob-difference-threshold-years: 1
```

Runtime update (no restart required):
```bash
PUT /api/admin/config/auto-clearance
{ "phase1Threshold": 0.85, "addressMismatchThreshold": 0.50, "dobDifferenceThresholdYears": 1 }
```

Admin UI: `http://localhost:8080/admin` — Auto-Clearance Thresholds section.

---

## Implementation

**Key files:**
- `src/main/java/io/moov/watchman/search/SearchServiceImpl.java` — two-phase orchestration
- `src/main/java/io/moov/watchman/config/AutoClearanceConfig.java` — configuration bean
- `src/main/java/io/moov/watchman/search/AutoClearanceResponse.java` — API response DTO
- `src/main/java/io/moov/watchman/search/AutoClearanceStatus.java` — status + reason + discriminators
- `src/main/java/io/moov/watchman/search/Phase1Detection.java` — Phase 1 result

**SearchServiceImpl methods:**
- `searchWithAutoClearance()` — main entry point
- `applyAutoClearance()` — Phase 2 orchestrator
- `applyAddressClearance()` — address fuzzy comparison
- `applyDobClearance()` — DOB year comparison
- `applyGovIdClearance()` — government ID exact comparison

**Performance:**
- Phase 1: ~10–50ms (name matching against index)
- Phase 2: +5–20ms per Phase 1 match (address comparison)
- Phase 2 is skipped entirely when Phase 1 returns 0 matches

---

## Test coverage

```bash
./mvnw test -Dtest=AutoClearancePhase1Test          # Phase 1 detection (3 cases)
./mvnw test -Dtest=AutoClearancePhase2AddressTest   # Address clearance (3 cases)
./mvnw test -Dtest=AutoClearancePhase2DobTest       # DOB clearance (5 cases)
./mvnw test -Dtest=AutoClearancePhase2GovIdTest     # Gov ID clearance (5 cases)
./mvnw test -Dtest=AdminConfigControllerTest        # Admin API config (9 cases)
```

---

## Troubleshooting

**No `autoClearance` field in response**
- Verify endpoint includes `/autoclearance` (not `/search`)
- Check `phase1.totalMatches` — must be > 0 for Phase 2 to run

**All matches show `PENDING`**
- Query is missing address field, or entity has no address data
- Check entity data for address availability

**Expected auto-clear but got `MANUAL_REVIEW`**
- Check `discriminators.address.score` — may be ≥ 0.50
- Verify the entity doesn't have multiple addresses with one partially matching
- Test with a clearly different address (different state or country)
