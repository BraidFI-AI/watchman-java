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

## Phase 2: Name Normalization (Critical)

### Task 2.1: Create Name Parser Utility

**Files:**
- `src/main/java/com/watchman/util/NameParser.java` (new)
- `src/test/java/com/watchman/util/NameParserTest.java` (new)

**RED Phase:**
```java
@Test
void nameParserpermutations_forwardAndReverse() {
    List<String> permutations = NameParser.generatePermutations("AL-JASIM, Muhammad Husayn");
    assertTrue(permutations.contains("AL-JASIM, Muhammad Husayn"));
    assertTrue(permutations.contains("Muhammad Husayn AL-JASIM"));
}
```

**GREEN Phase:**
```java
public class NameParser {
    public static List<String> generatePermutations(String name) {
        // Parse components: prefix, first, middle, last
        // Generate: [last, first middle], [first middle last]
        // Handle commas, hyphens
    }
}
```

**Acceptance:**
- Generates both "last, first" and "first last" forms
- Handles hyphenated names
- Test passes

---

### Task 2.2: Apply Permutations in Search

**Files:**
- `src/main/java/com/watchman/search/SearchService.java`

**RED Phase:**
```java
@Test
void search_nameOrderInsensitive() {
    SearchResult r1 = searchService.search("AL-JASIM, Muhammad Husayn");
    SearchResult r2 = searchService.search("Muhammad Husayn AL-JASIM");
    assertEquals(r1.getEntityId(), r2.getEntityId());
    assertEquals(r1.getMatchScore(), r2.getMatchScore(), 0.01);
}
```

**GREEN Phase:**
- In searchInternal(), generate permutations
- Search each permutation
- Return highest scoring match

**Acceptance:**
- Name order does not affect match
- Test passes

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
- [ ] All new tests passing
- [ ] Existing test suite still passes
- [ ] API contract updated (OpenAPI spec)
- [ ] Documentation updated (docs/api_spec.md)
- [ ] No performance regression

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
