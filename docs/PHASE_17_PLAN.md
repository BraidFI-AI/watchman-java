# PHASE 17: Zone 2 Quality - Upgrade Partial Implementations to Full Parity

**Date:** January 10, 2026  
**Goal:** Upgrade 4 partial implementations in Zone 2 (Entity Models) to full parity  
**Target:** 10/16 (62.5%) → 14/16 (87.5%), Overall 101/179 (56%) → 105/179 (59%)  
**Test Impact:** 938 → 958 tests (+20 tests)

---

## OBJECTIVE

Phase 17 targets quality improvements in Zone 2 (Entity Models & Data Structures) by upgrading partial implementations to match Go behavior exactly. While current implementations work, they differ from Go's approach in timing, caching, or algorithm details.

**Milestone Target:** Move Zone 2 from "HIGH QUALITY" to "NEARLY COMPLETE" status.

---

## SCOPE

### Functions to Upgrade (4 partial → full)

1. **Row 102: removeStopwords() helper** (⚠️ → ✅)
   - **Go Location:** `pkg/search/models.go`
   - **Current Java:** Inline in `bestPairJaro()` during scoring
   - **Go Behavior:** Applied during entity normalization, cached in PreparedFields
   - **Issue:** Java applies stopword removal at search time, Go applies at index time
   - **Impact:** Performance (Go's approach is faster) and transparency

2. **Row 103: normalizeNames()** (⚠️ → ✅)
   - **Go Location:** `pkg/search/models.go`
   - **Current Java:** `TextNormalizer` used per-search
   - **Go Behavior:** Applied during entity normalization, cached in PreparedFields
   - **Issue:** Java doesn't cache normalized names separately
   - **Impact:** Performance and field-level transparency

3. **Row 104: normalizePhoneNumbers()** (⚠️ → ✅)
   - **Go Location:** `pkg/search/models.go`
   - **Current Java:** `TextNormalizer.normalizeId()` - generic ID normalization
   - **Go Behavior:** Phone-specific normalization (removes +, -, spaces, parentheses)
   - **Issue:** Java uses generic normalization, Go has phone-specific logic
   - **Impact:** May miss phone-specific patterns

4. **Row 105: normalizeAddresses()** (⚠️ → ✅)
   - **Go Location:** `pkg/search/models.go`
   - **Current Java:** Basic normalization in `Entity.normalize()` pipeline
   - **Go Behavior:** Comprehensive address normalization with field-level processing
   - **Issue:** Java's normalization may be incomplete compared to Go
   - **Impact:** Address matching accuracy

---

## GO CODE ANALYSIS

### 1. removeStopwords() Helper

**Go Implementation (pkg/search/models.go):**
```go
func removeStopwords(input string) string {
    return prepare.RemoveStopwords(input)
}
```

**Usage in Go:**
- Called during `Entity.Normalize()` on primary name and alt names
- Results cached in `PreparedFields.Name` and `PreparedFields.AltNames`
- NOT applied again during search/scoring

**Current Java Behavior:**
- Stopwords removed inline in `JaroWinklerSimilarity.bestPairJaro()`
- Applied during each search operation
- Not cached in PreparedFields

**Required Change:**
- Move stopword removal to `Entity.normalize()` if not already there
- Ensure PreparedFields stores stopword-free names
- Remove inline stopword removal from scoring (if present)

### 2. normalizeNames()

**Go Implementation (pkg/search/models.go):**
```go
func normalizeNames(names []string) []string {
    normalized := make([]string, 0, len(names))
    for _, name := range names {
        n := prepare.LowerAndRemovePunctuation(name)
        if n != "" {
            normalized = append(normalized, n)
        }
    }
    return normalized
}
```

**Usage in Go:**
- Called during `Entity.Normalize()` on primary name and alt names
- Results cached in `PreparedFields` for reuse
- During search, uses cached values

**Current Java Behavior:**
- `TextNormalizer.lowerAndRemovePunctuation()` exists
- May be called per-search rather than cached
- PreparedFields might not store fully normalized names separately

**Required Change:**
- Verify PreparedFields caches normalized names (separate from stopword-removed names)
- Ensure normalization happens once during `Entity.normalize()`
- Search operations use cached normalized values

### 3. normalizePhoneNumbers()

**Go Implementation (pkg/search/models.go):**
```go
func normalizePhoneNumbers(phones []string) []string {
    normalized := make([]string, 0, len(phones))
    for _, phone := range phones {
        // Remove common phone formatting characters
        cleaned := strings.Map(func(r rune) rune {
            switch r {
            case '+', '-', ' ', '(', ')', '.':
                return -1  // Remove character
            default:
                return r
            }
        }, phone)
        if cleaned != "" {
            normalized = append(normalized, cleaned)
        }
    }
    return normalized
}
```

**Current Java:**
- `TextNormalizer.normalizeId()` removes hyphens generically
- Doesn't specifically handle phone formatting: `+`, spaces, `()`, `.`

**Required Change:**
- Create `PhoneNormalizer.normalizePhoneNumbers()` method
- Remove: `+`, `-`, space, `(`, `)`, `.`
- Apply during entity normalization
- Consider caching normalized phones in PreparedFields (if used for matching)

### 4. normalizeAddresses()

**Go Implementation (pkg/search/models.go):**
```go
func normalizeAddresses(addresses []Address) []Address {
    normalized := make([]Address, 0, len(addresses))
    for _, addr := range addresses {
        normalized = append(normalized, Address{
            Line1:      prepare.LowerAndRemovePunctuation(addr.Line1),
            Line2:      prepare.LowerAndRemovePunctuation(addr.Line2),
            City:       prepare.LowerAndRemovePunctuation(addr.City),
            State:      strings.ToLower(addr.State),
            PostalCode: strings.ToLower(addr.PostalCode),
            Country:    normalizeCountry(addr.Country),
        })
    }
    return normalized
}
```

**Current Java:**
- Phase 7 added `AddressNormalizer.normalizeAddress()` for searching
- `Entity.normalize()` may not normalize addresses in the entity itself
- Addresses may be normalized on-demand during search

**Required Change:**
- Ensure `Entity.normalize()` normalizes all addresses in entity
- Apply `LowerAndRemovePunctuation` to line1, line2, city
- Apply lowercase to state, postalCode
- Apply country normalization (if available, otherwise lowercase)
- Addresses in normalized entity should be pre-processed

---

## JAVA IMPLEMENTATION PLAN

### 1. removeStopwords() Helper - Ensure Caching

**Goal:** Verify stopword removal is cached, not applied during search.

**Implementation:**
- Review `Entity.normalize()` - confirm stopwords removed from primary/alt names
- Review `PreparedFields` - confirm stores stopword-free names
- Review `JaroWinklerSimilarity.bestPairJaro()` - ensure NOT removing stopwords inline
- If inline removal found, remove it (cached values should be used)

**Test Strategy:**
- Verify normalized entity has stopwords removed
- Verify PreparedFields contains stopword-free names
- Verify scoring uses cached values without re-processing

### 2. normalizeNames() - Verify Caching

**Goal:** Ensure name normalization is cached in PreparedFields.

**Implementation:**
- Review `PreparedFields` structure - should have normalized names
- Review `Entity.normalize()` - should apply `lowerAndRemovePunctuation()` to names
- Verify search operations use cached normalized names

**Test Strategy:**
- Create entity with punctuated names
- Verify `normalize()` produces clean names
- Verify PreparedFields stores normalized names
- Verify scoring uses cached values

### 3. normalizePhoneNumbers() - Phone-Specific Logic

**Goal:** Implement phone-specific normalization matching Go's algorithm.

**New Class: PhoneNormalizer.java**
```java
public class PhoneNormalizer {
    
    public static String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        
        // Remove phone formatting characters: +, -, space, (, ), .
        StringBuilder result = new StringBuilder(phone.length());
        for (char c : phone.toCharArray()) {
            if (c != '+' && c != '-' && c != ' ' && 
                c != '(' && c != ')' && c != '.') {
                result.append(c);
            }
        }
        
        String normalized = result.toString();
        return normalized.isEmpty() ? null : normalized;
    }
    
    public static List<String> normalizePhoneNumbers(List<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return phones;
        }
        
        return phones.stream()
            .map(PhoneNormalizer::normalizePhoneNumber)
            .filter(p -> p != null && !p.isEmpty())
            .collect(Collectors.toList());
    }
}
```

**Integration:**
- Apply during `Entity.normalize()` if ContactInfo has phone numbers
- Consider caching in PreparedFields if used for matching
- Use in `IntegrationFunctions.compareExactContactInfo()` if not already normalized

**Test Strategy:**
- Test various formats: "+1-555-123-4567", "(555) 123-4567", "555.123.4567"
- Verify all formats normalize to "15551234567" or "5551234567"
- Test empty/null handling
- Test integration with entity normalization

### 4. normalizeAddresses() - Field-Level Processing

**Goal:** Ensure addresses are fully normalized during entity normalization.

**Implementation:**
- Enhance `Entity.normalize()` to normalize all addresses
- Apply `LowerAndRemovePunctuation` to line1, line2, city
- Apply lowercase to state, postalCode, country
- Return entity with normalized addresses

**Update AddressNormalizer (if needed):**
```java
public static Address normalizeAddress(Address address) {
    if (address == null) {
        return null;
    }
    
    return new Address(
        TextNormalizer.lowerAndRemovePunctuation(address.line1()),
        TextNormalizer.lowerAndRemovePunctuation(address.line2()),
        TextNormalizer.lowerAndRemovePunctuation(address.city()),
        address.state() != null ? address.state().toLowerCase() : null,
        address.postalCode() != null ? address.postalCode().toLowerCase() : null,
        address.country() != null ? address.country().toLowerCase() : null
    );
}

public static List<Address> normalizeAddresses(List<Address> addresses) {
    if (addresses == null || addresses.isEmpty()) {
        return addresses;
    }
    
    return addresses.stream()
        .map(AddressNormalizer::normalizeAddress)
        .collect(Collectors.toList());
}
```

**Integration:**
- Call `AddressNormalizer.normalizeAddresses()` in `Entity.normalize()`
- Ensure normalized entity has clean addresses
- Search operations use pre-normalized addresses

**Test Strategy:**
- Test address with punctuation, uppercase
- Verify all fields normalized correctly
- Test null handling for optional fields
- Verify integration with entity normalization pipeline

---

## TEST STRATEGY

### Phase 17 Test Structure

**File:** `Phase17ZoneTwoQualityTest.java`  
**Estimated Size:** ~800 lines, 20 tests

**Test Classes:**

1. **StopwordCachingTests** (5 tests)
   - testStopwordsRemovedDuringNormalization()
   - testPreparedFieldsContainsStopwordFreeNames()
   - testScoringUsesStopwordFreeNames()
   - testMultipleNormalizationsConsistent()
   - testEmptyAfterStopwordRemoval()

2. **NameNormalizationCachingTests** (5 tests)
   - testNamesNormalizedDuringEntityNormalize()
   - testPreparedFieldsContainsNormalizedNames()
   - testPunctuationRemovedFromNames()
   - testCaseLoweredForNames()
   - testScoringUsesNormalizedNames()

3. **PhoneNormalizationTests** (5 tests)
   - testPhoneNumberFormattingRemoved()
   - testInternationalPhoneNormalization()
   - testVariousPhoneFormats()
   - testEmptyPhoneHandling()
   - testPhoneNormalizationIntegration()

4. **AddressNormalizationTests** (5 tests)
   - testAddressFieldsNormalized()
   - testAddressNormalizationIntegration()
   - testNullAddressFieldsHandled()
   - testPunctuationRemovedFromAddresses()
   - testAddressMatchingUsesNormalizedValues()

---

## SUCCESS CRITERIA

Phase 17 will be considered complete when:

1. **All 4 partial implementations upgraded to full (⚠️ → ✅)**
   - removeStopwords() helper - caching verified
   - normalizeNames() - caching verified
   - normalizePhoneNumbers() - phone-specific logic implemented
   - normalizeAddresses() - field-level processing implemented

2. **Test Coverage**
   - 20 new tests pass (938 → 958 total)
   - Full test suite shows 958/958 passing
   - Zero regressions in existing functionality

3. **Feature Parity**
   - Zone 2: 62.5% → 87.5% complete (10/16 → 14/16)
   - Overall: 101/179 (56%) → 105/179 (59%)
   - Only 2 pending in Zone 2 (NormalizeGender, Country - assigned to Zone 3 agent)

4. **Documentation**
   - FEATURE_PARITY_GAPS.md updated with ✅ status for all 4 functions
   - Implementation notes added for each function
   - Phase 17 section added to Implementation History

5. **Code Quality**
   - PhoneNormalizer class created with comprehensive phone formatting removal
   - AddressNormalizer enhanced for field-level normalization
   - Entity.normalize() pipeline includes all normalization steps
   - PreparedFields properly caches all normalized values

---

## IMPLEMENTATION ORDER

**TDD Phases:**

1. **RED Phase:**
   - Create Phase17ZoneTwoQualityTest.java with 20 failing tests
   - Test stopword caching, name normalization caching, phone normalization, address normalization
   - Commit RED phase

2. **GREEN Phase:**
   - Implement PhoneNormalizer.java
   - Enhance AddressNormalizer.java (if needed)
   - Verify Entity.normalize() caching behavior
   - Update any scoring functions to use cached values
   - Run tests until all pass
   - Commit GREEN phase

3. **DOCUMENTATION Phase:**
   - Update FEATURE_PARITY_GAPS.md rows 102-105
   - Add Phase 17 section to Implementation History
   - Update Zone 2 summary statistics
   - Commit documentation

---

## RISKS & CONSIDERATIONS

1. **Breaking Changes:** Changing normalization timing could affect existing tests
   - Mitigation: Run full test suite frequently, fix regressions immediately

2. **PreparedFields Changes:** May need to enhance PreparedFields structure
   - Mitigation: Make additive changes, don't remove existing fields

3. **Performance Impact:** More normalization during entity creation
   - Expected: Positive impact (normalize once, use many times)
   - Monitor: Test execution time before/after

4. **Phone Normalization Scope:** Go's phone normalization is simple
   - Don't over-engineer: Match Go's behavior exactly
   - International formats: Handle what Go handles, no more

5. **Address Normalization Complexity:** Addresses have many fields
   - Focus: Match Go's field-by-field normalization
   - Don't add: Advanced address parsing (that's Zone 6 - libpostal)

---

## POST-PHASE 17 STATE

**Expected Outcomes:**
- Zone 2 at 87.5% (HIGH QUALITY → NEARLY COMPLETE)
- Only 2 pending functions (NormalizeGender, Country) - assigned to Zone 3 agent
- All partial implementations eliminated from Zone 2
- 958 tests passing with zero regressions
- Clean foundation for future phases

**Next Phase Options:**
- Phase 18: Implement remaining Client & API functions (selective based on use cases)
- Phase 18: Help Zone 3 agent with Core Algorithms
- Phase 18: Implement environment variable configuration system

**Zone 2 Completion Path:**
- After Phase 17: 14/16 (87.5%)
- Remaining: NormalizeGender(), Country() (assigned to Zone 3 agent)
- When Zone 3 agent completes these: Zone 2 at 100%! 🎉

---

## APPENDIX: GO CODE REFERENCES

### removeStopwords() Usage in Go

```go
// pkg/search/models.go
func (e Entity[T]) Normalize() Entity[T] {
    // ... name reordering ...
    
    // Remove stopwords from primary name
    normalizedName := removeStopwords(prepare.LowerAndRemovePunctuation(e.Name))
    
    // Remove stopwords from alt names
    normalizedAltNames := make([]string, 0, len(e.AltNames))
    for _, alt := range e.AltNames {
        normalized := removeStopwords(prepare.LowerAndRemovePunctuation(alt))
        if normalized != "" {
            normalizedAltNames = append(normalizedAltNames, normalized)
        }
    }
    
    // Store in PreparedFields
    e.Prepared = &PreparedFields{
        Name:     normalizedName,
        AltNames: normalizedAltNames,
        // ... other fields ...
    }
    
    return e
}
```

### normalizePhoneNumbers() Usage in Go

```go
// pkg/search/models.go
func (e Entity[T]) Normalize() Entity[T] {
    // ... other normalizations ...
    
    // Normalize phone numbers in ContactInfo
    if e.ContactInfo != nil {
        e.ContactInfo.PhoneNumbers = normalizePhoneNumbers(e.ContactInfo.PhoneNumbers)
        e.ContactInfo.FaxNumbers = normalizePhoneNumbers(e.ContactInfo.FaxNumbers)
    }
    
    return e
}
```

### normalizeAddresses() Usage in Go

```go
// pkg/search/models.go
func (e Entity[T]) Normalize() Entity[T] {
    // ... other normalizations ...
    
    // Normalize all addresses
    e.Addresses = normalizeAddresses(e.Addresses)
    
    return e
}
```

---

**Phase 17 Ready for Implementation!** 🚀
