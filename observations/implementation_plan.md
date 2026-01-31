# Implementation Plan: BSA/AML Observations Remediation

## Phase 1: Alias Transparency (Critical)

### Task 1.1: Expose Matched Alias in SearchResult

**Files:**
- `src/main/java/com/watchman/search/SearchResult.java`
- `src/main/java/com/watchman/search/SearchService.java`

**RED Phase:**
```java
// SearchResultAliasTest.java
@Test
void searchResult_shouldIncludeMatchedAlias() {
    SearchResult result = searchService.search("AL-MALIZI");
    assertNotNull(result.getMatchedAlias());
    assertEquals("AL-MALIZI", result.getMatchedAlias());
}
```

**GREEN Phase:**
- Add `matchedAlias` field to SearchResult
- Modify scoring logic to track which alias scored
- Populate field in response

**Acceptance:**
- API returns matched alias in JSON response
- Test passes

---

### Task 1.2: Display All Aliases in Reports

**Files:**
- `src/main/resources/templates/report.html`
- `src/main/java/com/watchman/report/ReportRenderer.java`

**RED Phase:**
```java
@Test
void htmlReport_shouldDisplayAllAliases() {
    String html = reportRenderer.generateReport(sessionId);
    assertTrue(html.contains("Aliases:"));
    assertTrue(html.contains("AL-MALIZI"));
}
```

**GREEN Phase:**
- Update report.html template to show aliases section
- Pass aliases list to template
- Format as bulleted list

**Acceptance:**
- HTML report includes "Aliases" section
- All entity aliases visible

---

## Phase 2: Name Normalization (Critical) ✅ COMPLETED (Jan 30, 2026)

### Task 2.1: ~~Create Name Parser Utility~~ → Phonetic Set Matching (Alternate Solution) ✅

**Status:** COMPLETED - Implemented phonetic set matching instead of permutation generation

**Actual Implementation:**
- `src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java`
  - Added `phoneticSetsMatch()` helper method (lines ~172-188)
  - Uses existing Soundex infrastructure from `PhoneticFilter`
  - Replaced exact string set equality with phonetic equality (2 locations)

**Decision Rationale:**
- Soundex infrastructure already existed but wasn't integrated into word order logic
- More robust than permutation generation - handles spelling variations (Muhammad/Mohammad, Husayn/Hussein)
- Maintains 0.88 threshold precision while improving recall

**Test Coverage:**
- `src/test/java/io/moov/watchman/similarity/JaroWinklerSimilarityTest.java`
- 3 TDD tests in `PhoneticWordOrderTests` class (all passing):
  1. Exact token reordering (AEROCARIBBEAN AIRLINES)
  2. Phonetic variations with reordering (Muhammad/Mohammad, Husayn/Hussein)
  3. Different token counts edge case

**Acceptance:** ✅
- Phonetic equivalence provides word order insensitivity
- All tests passing (31/31 in JaroWinklerSimilarityTest)

---

### Task 2.2: ~~Apply Permutations in Search~~ → Phonetic Matching in Tokenized Similarity ✅

**Status:** COMPLETED - Integrated phonetic matching into existing tokenized similarity

**Actual Implementation:**
- Modified `tokenizedSimilarity()` and `tokenizedSimilarityWithPrepared()` methods
- Both query orders now return score 1.0 when phonetically equivalent
- No changes needed to SearchService - fix at algorithm layer

**Real-World Validation:** ✅
```bash
# Both queries now score 1.0 at 0.88 threshold
curl ".../v1/search?name=Muhammad+Husayn+AL-JASIM&minMatch=0.88"  # score: 1.0
curl ".../v1/search?name=AL-JASIM+Muhammad+Husayn&minMatch=0.88"  # score: 1.0
```

**Acceptance:** ✅
- Name order does not affect match when phonetically equivalent
- BSA consultant observation #2 RESOLVED
- Git commit: 072836b "Fix BSA consultant observation: phonetic word order insensitivity"

---

## Phase 3: Identifying Attributes (High Priority)

### Task 3.1: Parse OFAC Remarks Field

**Files:**
- `src/main/java/com/watchman/ofac/SDNEntity.java`
- `src/main/java/com/watchman/ofac/OfacParser.java`

**RED Phase:**
```java
@Test
void sdnEntity_shouldParseIdentifyingAttributes() {
    SDNEntity entity = ofacParser.parse(csvLineWithRemarks);
    assertNotNull(entity.getDateOfBirth());
    assertNotNull(entity.getNationality());
}
```

**GREEN Phase:**
- Add fields to SDNEntity: dob, nationality, passportNumber, etc
- Parse remarks field (regex extraction)
- Populate during CSV parsing

**Acceptance:**
- SDNEntity contains identifying attributes
- Test passes

---

### Task 3.2: Expose in SearchResult

**Files:**
- `src/main/java/com/watchman/search/SearchResult.java`

**RED Phase:**
```java
@Test
void searchResult_includesIdentifyingAttributes() {
    SearchResult result = searchService.search("Nicolas Maduro");
    assertNotNull(result.getDateOfBirth());
    assertNotNull(result.getNationality());
}
```

**GREEN Phase:**
- Add fields to SearchResult
- Map from SDNEntity
- Include in JSON serialization

**Acceptance:**
- API response includes DOB, nationality
- Test passes

---

## Phase 4: Alias Matching Verification

### Task 4.1: Debug Alias Ingestion

**Files:**
- `src/main/java/com/watchman/ofac/OfacService.java`
- `src/test/java/com/watchman/ofac/OfacServiceTest.java`

**RED Phase:**
```java
@Test
void aliasSearch_returnsParentEntity() {
    SearchResult result = searchService.search("AL-MALIZI");
    assertEquals("Abu Sayyaf", result.getName());
}
```

**Investigation:**
- Verify alt.csv parsing in OfacService
- Check AlternateIdentity → SDNEntity linkage
- Validate search index includes aliases

**GREEN Phase:**
- Fix alias indexing bug (if found)
- Ensure aliases searchable

**Acceptance:**
- Searching alias returns parent entity
- Test passes

---

### Task 4.2: Match Count Validation

**Files:**
- `src/test/java/com/watchman/search/MatchCountTest.java` (new)

**RED Phase:**
```java
@Test
void matchCount_alignsWithOFAC() {
    List<SearchResult> results = searchService.search("ABU BAKR AL-BAGHDADI");
    // Known OFAC count: 4 aliases
    assertTrue(results.size() >= 4 || hasMultipleAliases(results.get(0)));
}
```

**Investigation:**
- Compare Watchman vs OFAC for known entities
- Identify deduplication logic

**GREEN Phase:**
- Adjust logic to match OFAC behavior
- May be "working as intended" if deduplicating by parent entity

**Acceptance:**
- Match counts documented and explainable
- Test passes or intentional difference documented

---

## Testing Strategy

### Red Phase Tests (Create First)
1. AliasTransparencyTest.java
2. NameNormalizationTest.java
3. IdentifyingAttributesTest.java
4. AliasMatchingTest.java

### Green Phase Implementation
- Minimal code to pass tests
- No refactoring until tests pass

### Refactor Phase
- Clean code structure
- Extract utilities
- Remove duplication

---

## Success Criteria

- [ ] All 4 critical observations addressed
  - [ ] Observation #1: Match-level transparency (alias visibility)
  - [x] **Observation #2: Name order sensitivity** ✅ RESOLVED (Jan 30, 2026)
  - [ ] Observation #3: Missing identifying attributes
  - [ ] Observation #4: Alias matching gap
  - [ ] Observation #5: Match count discrepancy
- [x] Phase 2 (Name Normalization) tests passing ✅
- [x] Existing test suite still passes (1,126/1,126) ✅
- [ ] API contract updated (OpenAPI spec)
- [ ] Documentation updated (docs/api_spec.md)
- [x] No performance regression ✅

---

## Timeline Estimate

| Phase | Tasks | Estimated Effort |
|-------|-------|-----------------|
| Phase 1 (Alias Transparency) | 2 tasks | 2-3 days |
| Phase 2 (Name Normalization) | 2 tasks | 3-4 days |
| Phase 3 (Identifying Attributes) | 2 tasks | 2-3 days |
| Phase 4 (Alias Verification) | 2 tasks | 2-3 days |
| **Total** | **8 tasks** | **9-13 days** |

---

## Risk Mitigation

1. **Name normalization complexity:**
   - Start with simple permutations (last/first order)
   - Expand to prefixes, suffixes later
   - Test with real OFAC data

2. **Performance impact:**
   - Monitor search latency after permutations
   - May need caching layer

3. **Compliance validation:**
   - Legal review of name normalization approach
   - Document deviations from original Go implementation

4. **Data model changes:**
   - Backward compatibility for API consumers
   - Version bump if breaking changes

---

## Dependencies

- None (all work internal)
- May need legal review for name normalization
- Coordinate with ops team for deployment

---

## Follow-up Work (Out of Scope)

- Performance optimization
- Advanced name parsing (non-Latin scripts)
- Address matching
- Machine learning scoring
