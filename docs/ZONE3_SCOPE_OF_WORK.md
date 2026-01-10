# Zone 3: Entity Models & Data Structures - Scope of Work

**Generated:** 2026-01-10
**Zone**: 3 (Entity Models & Data Structures)
**Current Completion**: 19% (3/16 features fully implemented)
**Gap**: 56% completely missing (9/16), 25% partial (4/16)
**Priority**: **HIGH** - Foundation for data quality and deduplication

---

## EXECUTIVE SUMMARY

Zone 3 focuses on **entity data model utilities** - specifically deduplication, merging, and normalization helpers. The primary gap is **entity merging functionality** which allows combining duplicate entities from multiple sanctions lists into a single canonical representation.

**Current State:**
- ✅ Core `Entity` record exists with 18 fields
- ✅ `PreparedFields` optimization complete (Phase 1)
- ✅ `Entity.normalize()` pipeline complete (Phase 0)
- ❌ **NO merge functionality** - duplicates remain separate
- ❌ **NO deduplication logic** - same entity appears multiple times if on multiple lists

**Business Impact:**
- **False positives**: Same entity counted as multiple matches
- **Data quality**: Incomplete information spread across duplicates
- **Compliance risk**: Missing relationships between related entities
- **User experience**: Confusing duplicate results

---

## FEATURE INVENTORY

### ✅ Already Implemented (3 features - 19%)

| Feature | Status | Implementation | Value |
|---------|--------|----------------|-------|
| **98. Entity[T] struct** | ✅ Complete | `Entity` record with 18 fields | Core model |
| **99. PreparedFields** | ✅ Complete | Separated primary/alt names (Phase 1) | 10-100x performance |
| **100. Entity.normalize()** | ✅ Complete | Full pipeline: reorder → normalize → combinations → stopwords → titles | Index-time optimization |

**No action needed** - these are production-ready.

---

### ⚠️ Partially Implemented (4 features - 25%)

| # | Feature | Current | Gap | Recommendation |
|---|---------|---------|-----|----------------|
| **102** | `removeStopwords()` helper | Runs in `bestPairJaro()` at search time | Not cached at index time | **DEFER** - current approach works |
| **103** | `normalizeNames()` | `TextNormalizer` per-search | Not cached | **DEFER** - PreparedFields caching solves this |
| **104** | `normalizePhoneNumbers()` | Basic `normalizeId()` | Missing E.164, country codes | **LOW PRIORITY** - 4h if needed |
| **105** | `normalizeAddresses()` | Basic in `Entity.normalize()` | Missing libpostal, abbreviation expansion | **MEDIUM** - 6h if needed |

**Recommendation**: **DEFER** all partial features. Current implementations are adequate for sanctions screening. Only implement phone/address normalization if users report matching issues.

---

### ❌ Completely Missing (9 features - 56%)

## CATEGORY 1: ENTITY MERGING (CORE DEDUPLICATION)

### **Feature 101: Entity.merge() - Primary Merge Method**

**Purpose**: Combine two Entity instances into one canonical representation

**Use Case**: Same person appears on both OFAC SDN and EU CSL lists
```java
Entity ofacEntity = // "John Doe" from OFAC SDN
Entity euEntity = // "John Doe" from EU CSL

Entity merged = ofacEntity.merge(euEntity);
// Result: Single entity with:
//   - All alternate names combined (OFAC + EU)
//   - All addresses combined (OFAC + EU)
//   - All government IDs combined (US passport + EU tax ID)
//   - Sources: [OFAC_SDN, EU_CSL]
```

**Implementation Strategy:**
```java
public Entity merge(Entity other) {
    // Validate: Can only merge same person/business
    if (!this.type.equals(other.type)) {
        throw new IllegalArgumentException("Cannot merge different entity types");
    }

    // Merge strategy:
    // 1. Keep primary name from THIS entity (preferred)
    // 2. Combine all lists (addresses, altNames, govIds, etc.)
    // 3. Prefer non-null type-specific data (person/business/vessel/aircraft/org)
    // 4. Combine sources
    // 5. Re-normalize with merged data

    return new Entity(
        this.id,  // Keep first ID
        this.name,  // Keep first name as primary
        this.type,
        combineSources(this.source, other.source),  // NEW: SourceList can be multiple
        this.sourceId,
        mergePerson(this.person, other.person),  // Helper function
        mergeBusiness(this.business, other.business),
        // ... merge all nested objects
        mergeAddresses(this.addresses, other.addresses),  // FEATURE 106
        mergeCryptoAddresses(this.cryptoAddresses, other.cryptoAddresses),  // FEATURE 108
        mergeStrings(this.altNames, other.altNames),  // FEATURE 111
        mergeGovernmentIDs(this.governmentIds, other.governmentIds),  // FEATURE 109
        // ...
        null  // Clear preparedFields, will re-normalize
    ).normalize();  // Re-run normalization with merged data
}
```

**Effort**: 8-12 hours
- Design merge strategy (2h)
- Implement merge() method (4h)
- Implement helper methods (see below) (4h)
- Write tests (25+ test cases) (6h)

**Priority**: **P0 CRITICAL**
**Blocking**: Features 106-111 (all merge helpers)

---

### **Feature 106: mergeAddresses() - Address Deduplication**

**Purpose**: Combine two address lists, removing duplicates

**Algorithm**:
```java
public static List<Address> mergeAddresses(List<Address> list1, List<Address> list2) {
    // 1. Combine both lists
    // 2. Deduplicate by normalized key
    // 3. Return sorted (primary address first)

    Map<String, Address> addressMap = new LinkedHashMap<>();

    for (Address addr : Stream.concat(list1.stream(), list2.stream()).toList()) {
        String key = getAddressKey(addr);  // Normalized form
        addressMap.putIfAbsent(key, addr);  // Keep first occurrence
    }

    return new ArrayList<>(addressMap.values());
}

private static String getAddressKey(Address addr) {
    // Normalize for deduplication
    // "123 Main St" == "123 Main Street" == "123 MAIN ST"
    return String.format("%s|%s|%s|%s",
        normalize(addr.line1()),
        normalize(addr.city()),
        normalize(addr.state()),
        normalize(addr.country())
    ).toLowerCase();
}
```

**Test Cases**:
- Identical addresses → 1 result
- "123 Main St" + "123 Main Street" → 1 result (normalized)
- Different addresses → both kept
- Empty lists → empty result
- One empty, one with data → data kept

**Effort**: 3-4 hours
**Priority**: **P1 HIGH**

---

### **Feature 107: mergeAffiliations() - Affiliation Deduplication**

**Purpose**: Combine affiliation lists, removing duplicates

**Algorithm**:
```java
public static List<Affiliation> mergeAffiliations(List<Affiliation> list1, List<Affiliation> list2) {
    Map<String, Affiliation> affiliationMap = new LinkedHashMap<>();

    for (Affiliation aff : Stream.concat(list1.stream(), list2.stream()).toList()) {
        String key = getAffiliationKey(aff);
        affiliationMap.putIfAbsent(key, aff);
    }

    return new ArrayList<>(affiliationMap.values());
}

private static String getAffiliationKey(Affiliation aff) {
    // Key: normalized name + type
    return String.format("%s|%s",
        normalize(aff.name()),
        normalize(aff.type())
    ).toLowerCase();
}
```

**Effort**: 2-3 hours
**Priority**: **P1 HIGH**

---

### **Feature 108: mergeCryptoAddresses() - Crypto Address Deduplication**

**Purpose**: Combine crypto address lists, removing duplicates

**Algorithm**:
```java
public static List<CryptoAddress> mergeCryptoAddresses(List<CryptoAddress> list1, List<CryptoAddress> list2) {
    Map<String, CryptoAddress> cryptoMap = new LinkedHashMap<>();

    for (CryptoAddress crypto : Stream.concat(list1.stream(), list2.stream()).toList()) {
        // Crypto addresses are case-sensitive, but we still dedupe exact matches
        String key = crypto.currency() + "|" + crypto.address();
        cryptoMap.putIfAbsent(key, crypto);
    }

    return new ArrayList<>(cryptoMap.values());
}
```

**Effort**: 2 hours
**Priority**: **P2 MEDIUM**

---

### **Feature 109: mergeGovernmentIDs() - Government ID Deduplication**

**Purpose**: Combine government ID lists, removing duplicates

**Algorithm**:
```java
public static List<GovernmentId> mergeGovernmentIDs(List<GovernmentId> list1, List<GovernmentId> list2) {
    Map<String, GovernmentId> idMap = new LinkedHashMap<>();

    for (GovernmentId id : Stream.concat(list1.stream(), list2.stream()).toList()) {
        String key = getGovernmentIdKey(id);
        idMap.putIfAbsent(key, id);
    }

    return new ArrayList<>(idMap.values());
}

private static String getGovernmentIdKey(GovernmentId id) {
    // Key: type + country + normalized identifier
    return String.format("%s|%s|%s",
        id.type(),
        id.country(),
        normalizeId(id.identifier())  // Remove spaces, hyphens
    ).toLowerCase();
}

private static String normalizeId(String id) {
    // "123-45-6789" == "123456789"
    // "AB 12 34 56 C" == "AB123456C"
    return id.replaceAll("[\\s\\-]", "");
}
```

**Effort**: 3 hours
**Priority**: **P1 HIGH**

---

### **Feature 110: mergeHistoricalInfo() - Historical Data Merging**

**Purpose**: Combine historical info (if added to model)

**Current State**: `HistoricalInfo` doesn't exist in Java model yet

**Recommendation**: **DEFER** - Not in current model, low priority

**Effort**: 2 hours (if added later)
**Priority**: **P4 LOW**

---

### **Feature 111: mergeStrings() - Generic String List Deduplication**

**Purpose**: Utility for merging any string lists (altNames, emails, phones)

**Algorithm**:
```java
public static List<String> mergeStrings(List<String> list1, List<String> list2) {
    return Stream.concat(
            list1 != null ? list1.stream() : Stream.empty(),
            list2 != null ? list2.stream() : Stream.empty()
        )
        .filter(s -> s != null && !s.isBlank())
        .map(String::trim)
        .distinct()  // Remove exact duplicates
        .sorted()    // Consistent ordering
        .collect(Collectors.toList());
}
```

**Test Cases**:
- Overlapping lists → deduplicated
- Empty lists → empty result
- Null handling → no NPE
- Whitespace → trimmed
- Case sensitivity → preserved (distinct is exact)

**Effort**: 1 hour
**Priority**: **P1 HIGH**

---

### **Feature 112: Merge() - Batch Entity Merging**

**Purpose**: Merge a list of entities into single canonical entity

**Use Case**: Same entity appears on OFAC, EU, UK lists
```java
List<Entity> duplicates = List.of(ofacEntity, euEntity, ukEntity);
Entity canonical = EntityMerger.merge(duplicates);
```

**Algorithm**:
```java
public class EntityMerger {
    public static Entity merge(List<Entity> entities) {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        if (entities.size() == 1) {
            return entities.get(0);
        }

        // Use first entity as base, merge others into it
        return entities.stream()
            .reduce(Entity::merge)
            .orElseThrow();
    }
}
```

**Effort**: 2 hours
**Priority**: **P2 MEDIUM**

---

### **Feature 113: getMergeKey() - Entity Identity Key**

**Purpose**: Generate unique key for entity deduplication

**Use Case**: Detect which entities should be merged
```java
String key1 = getMergeKey(ofacEntity);
String key2 = getMergeKey(euEntity);

if (key1.equals(key2)) {
    // Same person, should merge
}
```

**Algorithm**:
```java
public static String getMergeKey(Entity entity) {
    // Strategy: Normalize name + type + best identifier

    String normalizedName = entity.preparedFields() != null
        ? entity.preparedFields().normalizedPrimaryName()
        : normalize(entity.name());

    String bestId = getBestIdentifier(entity);

    return String.format("%s|%s|%s",
        entity.type(),
        normalizedName,
        bestId
    ).toLowerCase();
}

private static String getBestIdentifier(Entity entity) {
    // Priority: sourceId > govId > crypto > email > empty
    if (entity.sourceId() != null) return entity.sourceId();
    if (entity.governmentIds() != null && !entity.governmentIds().isEmpty()) {
        return entity.governmentIds().get(0).identifier();
    }
    if (entity.cryptoAddresses() != null && !entity.cryptoAddresses().isEmpty()) {
        return entity.cryptoAddresses().get(0).address();
    }
    if (entity.contact() != null && entity.contact().emails() != null && !entity.contact().emails().isEmpty()) {
        return entity.contact().emails().get(0);
    }
    return "";
}
```

**Effort**: 3 hours
**Priority**: **P2 MEDIUM**

---

## IMPLEMENTATION PLAN

### **Recommended Approach: Incremental TDD**

### **Phase 1: Utility Merge Functions (1-2 days)**

**Deliverables:**
1. ✅ mergeStrings() - generic string deduplication (1h)
2. ✅ mergeAddresses() - address deduplication (4h)
3. ✅ mergeGovernmentIDs() - ID deduplication (3h)
4. ✅ mergeCryptoAddresses() - crypto deduplication (2h)
5. ✅ mergeAffiliations() - affiliation deduplication (3h)

**Tests**: 40-50 test cases total
**Total Effort**: 13 hours (1-2 days)

### **Phase 2: Entity Merging (2-3 days)**

**Deliverables:**
1. ✅ getMergeKey() - entity identity key (3h)
2. ✅ Entity.merge() - binary merge (8h)
3. ✅ EntityMerger.merge() - batch merge (2h)

**Tests**: 30+ test cases
**Total Effort**: 13 hours (2 days)

### **Phase 3: Integration & Deduplication Pipeline (1 day)**

**Deliverables:**
1. Deduplication service layer
2. Index-time deduplication (call during data load)
3. API to retrieve merged entities
4. Documentation

**Total Effort**: 6-8 hours

---

## TOTAL EFFORT ESTIMATE

| Phase | Features | Tests | Hours | Days |
|-------|----------|-------|-------|------|
| Phase 1: Utilities | 5 merge helpers | 40-50 | 13h | 1-2 days |
| Phase 2: Core Merge | 3 merge functions | 30+ | 13h | 2 days |
| Phase 3: Integration | Pipeline + API | 15+ | 8h | 1 day |
| **TOTAL** | **8 features** | **85-95 tests** | **34h** | **4-5 days** |

---

## PRIORITY RECOMMENDATIONS

### **Option 1: Implement Full Merge (RECOMMENDED)**
**Effort**: 4-5 days
**Value**: ⭐⭐⭐⭐⭐
**ROI**: **VERY HIGH**

**Why:**
- Eliminates duplicate entities (major UX improvement)
- Combines data quality from multiple sources
- Foundation for relationship tracking
- Matches Go implementation strategy

**Deliverables:**
- All 8 missing merge features
- 85+ comprehensive tests
- Deduplication pipeline
- Full feature parity with Go

### **Option 2: Minimal Merge (Quick Win)**
**Effort**: 1 day
**Value**: ⭐⭐⭐

**Scope:**
- mergeStrings() only
- Simple Entity.merge() (no fancy logic)
- Skip batch merge, skip getMergeKey()

**Deliverables:**
- Basic deduplication
- 20 tests
- Partial parity

### **Option 3: Defer (Current Strategy)**
**Effort**: 0 days
**Value**: ⭐

**Rationale:**
- Focus on scoring algorithms first
- Merge is "nice to have" not critical
- Can add later if users complain about duplicates

---

## RISK ANALYSIS

### **Risk 1: Merge Logic Complexity**
**Impact**: HIGH
**Likelihood**: MEDIUM

**Mitigation:**
- Start with simple merge (prefer first entity's data)
- Add sophisticated merge strategies later
- Extensive testing (85+ test cases)

### **Risk 2: Data Loss During Merge**
**Impact**: HIGH
**Likelihood**: LOW

**Mitigation:**
- Preserve all data (union, not intersection)
- Log all merge operations
- Add rollback capability

### **Risk 3: Performance Impact**
**Impact**: MEDIUM
**Likelihood**: LOW

**Mitigation:**
- Only run at index time (not search time)
- Use efficient deduplication (HashMap)
- Benchmark with real data (100K+ entities)

---

## SUCCESS CRITERIA

### **Phase 1 Complete:**
- ✅ All 5 helper merge functions implemented
- ✅ 40-50 tests passing (100%)
- ✅ No data loss (all input data preserved)
- ✅ Correct deduplication ("123 Main St" == "123 Main Street")

### **Phase 2 Complete:**
- ✅ Entity.merge() works for all entity types
- ✅ getMergeKey() correctly identifies duplicates
- ✅ Batch merge handles 3+ entities
- ✅ 30+ tests passing (100%)

### **Phase 3 Complete:**
- ✅ Deduplication runs at index time
- ✅ Search results show merged entities
- ✅ Performance: <1ms per merge operation
- ✅ Documentation complete

---

## DEPENDENCIES

**Blocked By:**
- None - can start immediately

**Blocks:**
- Deduplication UI features
- Entity relationship tracking
- Data quality reporting
- Compliance audit trails

**Requires:**
- Existing Entity model (✅ complete)
- PreparedFields optimization (✅ complete)
- Entity.normalize() pipeline (✅ complete)

---

## BUSINESS VALUE

### **Before Merge (Current State):**
```
Search: "John Doe"

Results:
1. John Doe (OFAC SDN) - 0.95 match
   - Address: 123 Main St, New York
   - Gov ID: US Passport #12345

2. John Doe (EU CSL) - 0.95 match
   - Address: 123 Main Street, NY  (DUPLICATE!)
   - Gov ID: EU Tax ID #67890

3. John Doe (UK CSL) - 0.95 match
   - Address: 123 Main St, New York, USA  (DUPLICATE!)
   - Gov ID: None

Problem: Same person counted 3x, incomplete information spread across entries
```

### **After Merge (Zone 3 Complete):**
```
Search: "John Doe"

Results:
1. John Doe (OFAC SDN, EU CSL, UK CSL) - 0.95 match
   - Addresses:
     - 123 Main St, New York, USA
   - Government IDs:
     - US Passport #12345
     - EU Tax ID #67890
   - Sources: OFAC SDN, EU CSL, UK CSL

Benefits:
✅ Single result (not 3 duplicates)
✅ Complete information (all IDs, all sources)
✅ Clear provenance (which lists included this person)
✅ Better compliance (see full picture)
```

---

## RECOMMENDATION

**PROCEED WITH OPTION 1: Full Merge Implementation**

**Rationale:**
1. **High ROI**: 4-5 days effort for major UX improvement
2. **Foundation**: Enables relationship tracking, data quality features
3. **Parity**: Matches Go implementation (probably why it's missing there too - they may not have implemented it yet)
4. **Timing**: Can implement while core algorithms are being tested/validated
5. **Independence**: Doesn't block or depend on other work

**Next Steps:**
1. ✅ Review and approve this scope of work
2. Start Phase 1 (merge utilities) using TDD
3. Complete Phase 2 (core merge)
4. Integrate into data loading pipeline (Phase 3)
5. Update feature parity tracker

---

## QUESTIONS FOR DISCUSSION

1. **Merge Strategy**: When entities conflict (different primary names), which should win?
   - Option A: Keep first entity's data (OFAC preferred over EU)
   - Option B: Longest/most complete name
   - Option C: Configurable preference order

2. **Merge Key Sensitivity**: How strict should duplicate detection be?
   - Option A: Strict (exact name + ID match)
   - Option B: Fuzzy (similar name + any matching field)
   - Option C: Configurable threshold

3. **SourceList Model**: Should we modify `SourceList` enum to support multiple sources?
   - Current: `SourceList source` (single value)
   - Needed: `List<SourceList> sources` (multiple values)
   - Impact: Breaking change to Entity model

4. **Performance Target**: What's acceptable for merge operations?
   - 100K entities × 10% duplicates = 10K merge operations
   - Target: <1ms per merge = 10 seconds total
   - Acceptable?

---

**Author**: Claude (Sonnet 4.5)
**Date**: 2026-01-10
**Status**: Ready for Review
