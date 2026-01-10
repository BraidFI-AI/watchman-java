# PHASE 13: Entity Merging Functions

**Status:** 🔴 RED Phase  
**Date:** January 10, 2026  
**Target:** Implement 9 entity merge functions to close 56% gap in Entity Models

---

## OBJECTIVE

Implement the entity merging infrastructure from Go's `pkg/search/merge.go`. This allows combining multiple partial entity records (e.g., from CSV rows with same ID) into a single consolidated entity.

**Current Status:** 3/16 Entity Model features complete (19%)  
**Target:** 12/16 complete (75%) - 9 new functions

---

## GO IMPLEMENTATION ANALYSIS

**Source File:** `/Users/randysannicolas/Documents/GitHub/watchman/pkg/search/merge.go` (240 lines)

### Core Functions

1. **`Merge(entities []Entity) []Entity`** - Top-level merge orchestrator
   - Groups entities by merge key (Source/SourceID/Type)
   - Merges each group using `entity.merge()`
   - Normalizes merged results
   
2. **`getMergeKey(entity Entity) string`** - Generate unique merge key
   - Format: `"source/sourceId/type"` (lowercase)
   - Example: `"us_ofac/12345/person"`

3. **`entity.merge(other Entity) Entity`** - Main merge logic (160 lines)
   - Uses `cmp.Or()` for scalar field selection (first non-empty wins)
   - Merges type-specific fields (Person/Business/Organization/Aircraft/Vessel)
   - Delegates collection merging to helper functions
   - **Critical:** Handles nil type fields correctly

4. **Helper Merge Functions** (all use `github.com/adamdecaf/merge.Slices`):
   - `mergeStrings(ss ...[]string) []string` - Deduplicate strings (case-insensitive)
   - `mergeGovernmentIDs(ids1, ids2) []GovernmentID` - Unique by country/type/identifier
   - `mergeAddresses(a1, a2) []Address` - Unique by line1/line2, merge fields
   - `mergeCryptoAddresses(c1, c2) []CryptoAddress` - Unique by currency/address
   - `mergeAffiliations(a1, a2) []Affiliation` - Unique by entityName/type
   - `mergeHistoricalInfo(h1, h2) []HistoricalInfo` - Unique by type/value

### Key Patterns

**Pattern 1: First-Wins Scalar Selection**
```go
out.Name = cmp.Or(e.Name, other.Name)  // Returns first non-zero value
out.Type = cmp.Or(e.Type, other.Type)
out.Source = cmp.Or(e.Source, other.Source)
```

**Pattern 2: Nil-Safe Type Field Merging**
```go
case other.Person != nil:
    if e.Person == nil {
        e.Person = &Person{}  // Initialize if nil
    }
    out.Person = &Person{
        Name: cmp.Or(e.Person.Name, other.Person.Name, e.Name),
        // ...
    }
```

**Pattern 3: Collection Deduplication with Key Function**
```go
func mergeGovernmentIDs(ids1, ids2 []GovernmentID) []GovernmentID {
    return merge.Slices(
        func(id GovernmentID) string {
            return strings.ToLower(fmt.Sprintf("%s/%s/%s", id.Country, id.Type, id.Identifier))
        },
        nil, // don't merge items, just unique
        ids1, ids2,
    )
}
```

**Pattern 4: Address Merge with Field Combination**
```go
func mergeAddresses(a1, a2 []Address) []Address {
    return merge.Slices(
        func(addr Address) string {
            return strings.ToLower(fmt.Sprintf("%s/%s", addr.Line1, addr.Line2))
        },
        func(a1 *Address, a2 Address) {
            a1.Line1 = cmp.Or(a1.Line1, a2.Line1)  // Fill missing fields
            a1.City = cmp.Or(a1.City, a2.City)
            // ...
        },
        a1, a2,
    )
}
```

### External Dependency

**`github.com/adamdecaf/merge` package:**
```go
// Generic slice merging with deduplication
func Slices[T any](
    keyFn func(T) string,           // Extract unique key
    mergeFn func(*T, T),            // Optional: merge two items with same key
    slices ...[]T,                  // Variadic slices to merge
) []T
```

**Java Equivalent:** We'll implement this logic inline using:
- `Map<String, T>` for deduplication by key
- `firstNonNull()` / `Stream.filter(Objects::nonNull).findFirst().orElse(null)` for scalar selection
- Manual field merging for complex types

---

## JAVA IMPLEMENTATION PLAN

### New Classes/Methods

**File:** `src/main/java/io/moov/watchman/search/EntityMerger.java` (NEW)

```java
package io.moov.watchman.search;

public class EntityMerger {
    // Top-level functions
    public static List<Entity> merge(List<Entity> entities);
    private static String getMergeKey(Entity entity);
    
    // Core merge logic
    private static Entity mergeTwo(Entity e1, Entity e2);
    
    // Helper merge functions
    private static List<String> mergeStrings(List<String>... lists);
    private static List<GovernmentId> mergeGovernmentIds(List<GovernmentId> ids1, List<GovernmentId> ids2);
    private static List<Address> mergeAddresses(List<Address> a1, List<Address> a2);
    private static List<CryptoAddress> mergeCryptoAddresses(List<CryptoAddress> c1, List<CryptoAddress> c2);
    private static List<Affiliation> mergeAffiliations(List<Affiliation> a1, List<Affiliation> a2);
    private static List<HistoricalInfo> mergeHistoricalInfo(List<HistoricalInfo> h1, List<HistoricalInfo> h2);
    
    // Utility: first non-null value
    @SafeVarargs
    private static <T> T firstNonNull(T... values);
}
```

**File:** `src/main/java/io/moov/watchman/search/Entity.java` (MODIFIED)

```java
// Add convenience method
public Entity merge(Entity other) {
    return EntityMerger.mergeTwo(this, other);
}
```

### Implementation Strategy

1. **Generic Deduplication Utility**
   ```java
   private static <T> List<T> uniqueBy(Function<T, String> keyFn, List<T>... lists) {
       Map<String, T> seen = new LinkedHashMap<>();
       for (List<T> list : lists) {
           for (T item : list) {
               String key = keyFn.apply(item).toLowerCase();
               seen.putIfAbsent(key, item);
           }
       }
       return new ArrayList<>(seen.values());
   }
   ```

2. **Address Merge with Field Combination**
   ```java
   private static <T> List<T> uniqueByWithMerge(
       Function<T, String> keyFn,
       BiConsumer<T, T> mergeFn,
       List<T>... lists
   ) {
       Map<String, T> seen = new LinkedHashMap<>();
       for (List<T> list : lists) {
           for (T item : list) {
               String key = keyFn.apply(item).toLowerCase();
               if (seen.containsKey(key)) {
                   mergeFn.accept(seen.get(key), item);
               } else {
                   seen.put(key, item);
               }
           }
       }
       return new ArrayList<>(seen.values());
   }
   ```

3. **Scalar Field Selection**
   ```java
   @SafeVarargs
   private static <T> T firstNonNull(T... values) {
       return Arrays.stream(values)
           .filter(Objects::nonNull)
           .filter(v -> !(v instanceof String) || !((String) v).isEmpty())
           .findFirst()
           .orElse(null);
   }
   ```

---

## TEST PLAN (RED PHASE)

**File:** `src/test/java/io/moov/watchman/search/EntityMergerTest.java` (NEW ~600 lines)

### Test Structure

```java
@DisplayName("Phase 13: Entity Merging Tests")
class EntityMergerTest {
    
    @Nested
    @DisplayName("Top-Level Merge Function")
    class MergeTests {
        @Test void mergesSingleEntity();
        @Test void mergesTwoEntitiesSameKey();
        @Test void mergesThreeEntitiesSameKey();
        @Test void keepsEntitiesWithDifferentKeys();
        @Test void handlesEmptyList();
        @Test void handlesNullFields();
        @Test void normalizesAfterMerge();
    }
    
    @Nested
    @DisplayName("Merge Key Generation")
    class MergeKeyTests {
        @Test void generatesCorrectKey();
        @Test void keyIsLowercase();
        @Test void keyFormat();
    }
    
    @Nested
    @DisplayName("Entity.merge() - Two Entity Merge")
    class TwoEntityMergeTests {
        @Test void mergesPersonEntities();
        @Test void mergesBusinessEntities();
        @Test void mergesOrganizationEntities();
        @Test void mergesAircraftEntities();
        @Test void mergesVesselEntities();
        @Test void handlesNilTypeFields();
        @Test void firstNonEmptyWinsForScalars();
        @Test void combinesAltNames();
    }
    
    @Nested
    @DisplayName("Helper: mergeStrings()")
    class MergeStringsTests {
        @Test void deduplicatesCaseInsensitive();
        @Test void mergesMultipleLists();
        @Test void handlesEmptyLists();
        @Test void preservesOrder();
    }
    
    @Nested
    @DisplayName("Helper: mergeGovernmentIds()")
    class MergeGovernmentIdsTests {
        @Test void deduplicatesByCountryTypeIdentifier();
        @Test void caseInsensitiveComparison();
        @Test void handlesDifferentCountries();
    }
    
    @Nested
    @DisplayName("Helper: mergeAddresses()")
    class MergeAddressesTests {
        @Test void deduplicatesByLine1Line2();
        @Test void fillsMissingFields();
        @Test void keepsDifferentAddresses();
    }
    
    @Nested
    @DisplayName("Helper: mergeCryptoAddresses()")
    class MergeCryptoAddressesTests {
        @Test void deduplicatesByCurrencyAddress();
        @Test void caseInsensitive();
    }
    
    @Nested
    @DisplayName("Helper: mergeAffiliations()")
    class MergeAffiliationsTests {
        @Test void deduplicatesByEntityNameType();
        @Test void caseInsensitive();
    }
    
    @Nested
    @DisplayName("Helper: mergeHistoricalInfo()")
    class MergeHistoricalInfoTests {
        @Test void deduplicatesByTypeValue();
        @Test void caseInsensitive();
    }
    
    @Nested
    @DisplayName("Contact Info Merging")
    class ContactMergeTests {
        @Test void mergesEmailAddresses();
        @Test void mergesPhoneNumbers();
        @Test void mergesFaxNumbers();
        @Test void mergesWebsites();
    }
    
    @Nested
    @DisplayName("SanctionsInfo Merging")
    class SanctionsInfoMergeTests {
        @Test void mergesPrograms();
        @Test void combinesSecondaryFlags();
        @Test void firstNonEmptyDescription();
    }
    
    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldTests {
        @Test void mergesOFACSDNWithMultipleAddresses();
        @Test void mergesEUCSLEntityWithSameLogicalId();
        @Test void mergesUKCSLEntityWithSameGroupId();
        @Test void identityMerge();  // merge(single) == single
    }
}
```

### Test Data Examples

**Example 1: John Doe + Johnny Doe (from Go test)**
```java
Entity johnDoe = Entity.person()
    .name("John Doe")
    .source(Source.US_OFAC)
    .sourceId("12345")
    .person(Person.builder()
        .name("John Doe")
        .gender("male")
        .build())
    .contact(ContactInfo.builder()
        .emailAddresses(List.of("john.doe@example.com"))
        .phoneNumbers(List.of("123.456.7890"))
        .build())
    .addresses(List.of(Address.builder()
        .line1("123 First St")
        .city("Anytown")
        .state("CA")
        .postalCode("90210")
        .country("US")
        .build()))
    .build();

Entity johnnyDoe = Entity.person()
    .name("Johnny Doe")
    .source(Source.US_OFAC)
    .sourceId("12345")  // Same key!
    .person(Person.builder()
        .name("Johnny Doe")
        .birthDate(LocalDate.of(1971, 3, 26))
        .governmentIds(List.of(GovernmentId.builder()
            .type("passport")
            .country("US")
            .identifier("1981204918019")
            .build()))
        .build())
    .contact(ContactInfo.builder()
        .emailAddresses(List.of("johnny.doe@example.com"))
        .phoneNumbers(List.of("123.456.7890"))  // Duplicate
        .websites(List.of("http://johnnydoe.com"))
        .build())
    .addresses(List.of(Address.builder()
        .line1("123 First St")
        .line2("Unit 456")  // Additional detail
        .city("Anytown")
        .state("CA")
        .postalCode("90210")
        .country("US")
        .build()))
    .build();

// Merge
List<Entity> merged = EntityMerger.merge(List.of(johnDoe, johnnyDoe));

// Expected: Single entity with combined data
assertThat(merged).hasSize(1);
Entity result = merged.get(0);

assertThat(result.name()).isEqualTo("John Doe");  // First wins
assertThat(result.person().altNames()).contains("Johnny Doe");  // Other name becomes alt
assertThat(result.person().birthDate()).isEqualTo(LocalDate.of(1971, 3, 26));  // From johnny
assertThat(result.person().governmentIds()).hasSize(1);
assertThat(result.contact().emailAddresses()).hasSize(2);  // Both emails
assertThat(result.contact().phoneNumbers()).hasSize(1);  // Deduplicated
assertThat(result.addresses()).hasSize(2);  // Different Line2 = different address
```

---

## ACCEPTANCE CRITERIA

### RED Phase
- [x] ~60 failing tests covering all merge functions
- [x] Tests verify deduplication logic
- [x] Tests verify nil-safety
- [x] Tests cover all entity types (Person/Business/Organization/Aircraft/Vessel)
- [x] Real-world scenario tests with OFAC/EU/UK data

### GREEN Phase
- [ ] All 60+ tests passing
- [ ] `EntityMerger.merge()` groups and merges entities correctly
- [ ] All helper merge functions deduplicate properly
- [ ] Nil-safety for all type fields
- [ ] `firstNonNull()` utility works correctly

### Validation
- [ ] Full test suite passes (862 → ~920+ tests)
- [ ] Zero regressions in Phases 0-12
- [ ] FEATURE_PARITY_GAPS.md updated: Entity Models 3/16 → 12/16 (75%)

---

## TIMELINE

- **RED Phase:** 30 minutes (write failing tests)
- **GREEN Phase:** 60 minutes (implement EntityMerger + helpers)
- **Validation:** 15 minutes (full test suite + docs)

**Total Estimated Time:** ~2 hours

---

## RISKS & MITIGATION

**Risk 1: Java Records are Immutable**
- **Impact:** Can't modify entity fields in-place
- **Mitigation:** Use builder pattern, create new Entity instances

**Risk 2: Missing `cmp.Or()` Equivalent**
- **Impact:** Need custom utility for scalar selection
- **Mitigation:** Implement `firstNonNull()` with empty string handling

**Risk 3: No Generic Merge Library**
- **Impact:** Need to implement `merge.Slices()` equivalent
- **Mitigation:** Inline implementation using `Map<String, T>` and `LinkedHashMap` for order preservation

**Risk 4: Type-Specific Field Complexity**
- **Impact:** 5 entity types × multiple fields = lots of code
- **Mitigation:** Use builder pattern, test incrementally per type

---

## REFERENCES

- **Go Source:** `/Users/randysannicolas/Documents/GitHub/watchman/pkg/search/merge.go`
- **Go Tests:** `/Users/randysannicolas/Documents/GitHub/watchman/pkg/search/merge_test.go`
- **Feature Tracker:** `docs/FEATURE_PARITY_GAPS.md` lines 212-227
- **External Library:** `github.com/adamdecaf/merge` (inline equivalent needed)

---

## NEXT STEPS

1. Write `EntityMergerTest.java` with ~60 failing tests (RED)
2. Implement `EntityMerger.java` with all helper functions (GREEN)
3. Add `Entity.merge()` convenience method
4. Run full test suite to verify zero regressions
5. Update FEATURE_PARITY_GAPS.md with Phase 13 completion
6. Commit: "feat: Phase 13 GREEN - Entity merging functions (75% Entity Models complete)"
