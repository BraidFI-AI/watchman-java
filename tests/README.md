# Unit Test Implementation Plan

Based on the project structure and dependencies, here's the testing order (bottom-up by dependency):

---

## Phase 1: Core Similarity Engine (No Dependencies)

**Module:** `io.moov.watchman.similarity`

| Test Class | Purpose | Test Count | Priority |
|------------|---------|------------|----------|
| `JaroWinklerSimilarityTest` | Core fuzzy matching algorithm | ~80 | 🔴 Critical |
| `TextNormalizerTest` | Lowercase, punctuation removal, stopwords | ~20 | 🔴 Critical |
| `PhoneticFilterTest` | Soundex-based first-character filtering | ~15 | 🟡 High |

**Key test cases to implement:**
- Exact string matching → 1.0
- Case insensitivity
- Punctuation normalization ("CO., LTD." → "CO LTD")
- Name variations ("Mohammad" vs "Muhammad")
- Length penalties
- Token reordering ("John Smith" ↔ "Smith John")

---

## Phase 2: OFAC Parser (No Dependencies)

**Module:** `io.moov.watchman.parser`

| Test Class | Purpose | Test Count | Priority |
|------------|---------|------------|----------|
| `OFACParserTest` | SDN/ADD/ALT CSV parsing | ~25 | 🔴 Critical |
| `EntityTypeParserTest` | "individual" → PERSON mapping | ~10 | 🟡 High |
| `RemarksParserTest` | Extract DOB, POB from remarks field | ~15 | 🟡 High |

**Key test cases:**
- Parse single entity row
- Parse each entity type (person, business, vessel, aircraft)
- Merge addresses from add.csv
- Merge alt names from alt.csv
- Handle malformed/missing data
- Extract sanctions programs

---

## Phase 3: Entity Index (Depends on Models)

**Module:** `io.moov.watchman.index`

| Test Class | Purpose | Test Count | Priority |
|------------|---------|------------|----------|
| `EntityIndexTest` | In-memory storage and retrieval | ~15 | 🟡 High |
| `ConcurrentAccessTest` | Thread-safety verification | ~10 | 🟡 High |

**Key test cases:**
- Add/retrieve entities
- Filter by source (OFAC, CSL)
- Filter by type (person, business)
- Concurrent read/write safety
- Replace all (atomic refresh)

---

## Phase 4: Search Service (Depends on Similarity + Index)

**Module:** `io.moov.watchman.search`

| Test Class | Purpose | Test Count | Priority |
|------------|---------|------------|----------|
| `EntityScorerTest` | Weighted multi-factor scoring | ~30 | 🔴 Critical |
| `SearchServiceTest` | Search orchestration | ~20 | 🔴 Critical |
| `SearchRankingTest` | Result ordering and filtering | ~15 | 🟡 High |

**Key test cases:**
- Empty query → 0.0 score
- Exact sourceId match → 1.0
- Name similarity contributes to score
- Government ID matching (weight 50)
- Address matching (weight 25)
- Results sorted by score descending
- minMatch threshold filtering
- limit parameter respected

---

## Phase 5: Integration / Simulation (Depends on All)

**Module:** `io.moov.watchman.simulation`

| Test Class | Purpose | Test Count | Priority |
|------------|---------|------------|----------|
| `ScreeningSimulationTest` | End-to-end with real OFAC data | ~25 | 🔴 Critical |
| `FalsePositiveTest` | Common names don't match | ~15 | 🟡 High |
| `TransliterationTest` | Arabic/Cyrillic name handling | ~10 | 🟢 Medium |

---

## Implementation Schedule

```
Week 1: Phase 1 (Similarity)
├── Day 1-2: TextNormalizerTest + impl
├── Day 3-4: JaroWinklerSimilarityTest + impl
└── Day 5: PhoneticFilterTest + impl

Week 2: Phase 2 (Parser)
├── Day 1-2: OFACParserTest + impl (SDN only)
├── Day 3: Address/AltName merging
└── Day 4-5: Edge cases, remarks parsing

Week 3: Phase 3-4 (Index + Search)
├── Day 1: EntityIndexTest + impl
├── Day 2-3: EntityScorerTest + impl
└── Day 4-5: SearchServiceTest + impl

Week 4: Phase 5 (Integration)
├── Day 1-2: Download real OFAC data in tests
├── Day 3-4: ScreeningSimulationTest
└── Day 5: Performance tuning
```

---

## Test Execution

```bash
# Run all tests
./mvnw test

# Run specific phase
./mvnw test -Dtest="*Normalizer*,*JaroWinkler*,*Phonetic*"  # Phase 1
./mvnw test -Dtest="*Parser*"                                # Phase 2
./mvnw test -Dtest="*Index*"                                 # Phase 3
./mvnw test -Dtest="*Scorer*,*Search*"                       # Phase 4
./mvnw test -Dtest="*Simulation*,*FalsePositive*"            # Phase 5
```

---

## Reference Implementation

Test cases are ported from the Go implementation:
- `internal/stringscore/jaro_winkler_test.go`
- `internal/prepare/pipeline_test.go`
- `pkg/search/similarity_fuzzy_test.go`
- `pkg/search/similarity_exact_test.go`
- `internal/download/download_test.go`
