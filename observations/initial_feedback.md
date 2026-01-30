# Initial BSA/AML Observations Feedback

## Summary

Initial testing reveals baseline sanctions detection is functioning, but **critical gaps in alias handling, name normalization, and hit-level transparency create false-negative and auditability risks** that require immediate remediation.

## Scope

Observations from independent testing of Watchman Java sanctions screening module against known sanctioned entities (OFAC SDN list). Analysis identifies 5 priority areas requiring system modification.

## Critical Observations

### 1. **Insufficient Match-Level Transparency** (Priority: Critical)

**Finding:**
- System does not display which specific alias triggered an alert
- Cannot confirm if match was due to exact alias match or broader name similarity
- Difficult to validate match accuracy without alias-level details

**Example:**
- Searching "AL-MALIZI" (OFAC-listed alias of Abu Sayyaf) returns primary entity name only
- No indication which of the entity's aliases matched

**Risk:**
- Violates core BSA/AML requirement for explainability
- Limits effective alert disposition
- Increases operational and audit risk

**Recommendation:**
- Expose matched alias in API response (matchedAlias field)
- Show all aliases in UI/report output
- Document which specific entity record/alias triggered match

**Evidence:**
- Observation #1, #4, #5 in [Observations 1.xml](Observations%201.xml)
- screenshots show primary name only in results

---

### 2. **Name Order Sensitivity** (Priority: Critical)

**Finding:**
- System exhibits sensitivity to name component ordering
- Searching "AL-JASIM, Muhammad Husayn" vs "Muhammad Husayn AL-JASIM" produces different results

**Risk:**
- **False-negative risk**: Sanctioned individuals not detected when name components in different order
- Violates expectation that fuzzy matching handles name variations

**Recommendation:**
- Implement name normalization before matching:
  - Parse name components (first, middle, last, prefixes)
  - Generate multiple permutations for matching
  - Test all permutations against list entries
- Add test cases for name order variations

**Evidence:**
- Observation #2: "AL-JASIM, Muhammad Husayn" behavior differs from reverse order
- Similar issues likely with other non-Western name formats

---

### 3. **Missing Identifying Attributes** (Priority: High)

**Finding:**
- API response lacks identifying attributes from OFAC data:
  - Date of birth
  - Place of birth  
  - Nationality
  - Location
  - Identification numbers (passport, national ID)

**Risk:**
- Cannot effectively clear false positives
- Limited ability to validate match quality
- Reduces confidence in screening outcomes

**Recommendation:**
- Parse and expose OFAC identifying attributes from source data:
  - add.csv (addresses)
  - alt.csv (alternate names)
  - sdn.csv (remarks field containing DOB, nationality, etc)
- Include in SearchResult response object
- Display in HTML reports

**Evidence:**
- Observation #3
- Current SearchResult only includes: name, score, entityType, programs

---

### 4. **Alias Matching Gap** (Priority: Critical)

**Finding:**
- Searching using OFAC-listed aliases does not produce expected matches
- Example: "AL-MALIZI" (known OFAC alias) did not generate match

**Risk:**
- **False-negative risk**: Sanctioned entities evade detection using listed aliases
- Defeats purpose of maintaining alias data

**Recommendation:**
- Verify alias data ingestion from alt.csv
- Ensure all aliases indexed for search
- Test coverage for alias-only searches
- Debug: Check if AlternateIdentity entities properly linked to parent SDN entity

**Evidence:**
- Observation #4 with screenshots
- "AL-MALIZI" example

---

### 5. **Match Count Discrepancy** (Priority: Medium)

**Finding:**
- Watchman returns fewer matches than expected from OFAC list
- Examples:
  - "ABU BAKR AL-BAGHDADI": 1 match in Watchman vs 4 in OFAC
  - "AL SHABAAB": 4 matches in Watchman vs 11 in OFAC

**Risk:**
- May indicate internal alias handling issues
- Could contribute to false-negative rate

**Recommendation:**
- Investigate entity/alias linkage in data model
- Verify deduplication logic not over-aggressive
- Test against known multi-alias entities
- Compare match counts systematically

**Evidence:**
- Observation #1
- May be related to alias visibility issue (#1)

---

## Design Notes

### Immediate Next Steps

1. **Add alias transparency** (Addresses #1, #4, #5)
   - Modify SearchResult to include matchedAlias field
   - Update HTML report template to show aliases
   - Expose in JSON response

2. **Implement name normalization** (Addresses #2)
   - Create NameParser utility
   - Generate permutations for matching
   - Add permutation tests

3. **Expose identifying attributes** (Addresses #3)
   - Parse OFAC remarks field
   - Add fields to SDNEntity/SearchResult
   - Update API contract

4. **Verify alias ingestion** (Addresses #4, #5)
   - Debug AlternateIdentity linkage
   - Test alt.csv parsing
   - Add alias search tests

### Test Strategy

- RED: Create failing tests for each observation
- GREEN: Implement minimal fix
- REFACTOR: Clean up without changing behavior

### Files to Modify

- [SearchResult.java](../src/main/java/com/watchman/search/SearchResult.java)
- [SDNEntity.java](../src/main/java/com/watchman/ofac/SDNEntity.java)
- [AlternateIdentity.java](../src/main/java/com/watchman/ofac/AlternateIdentity.java)
- [OfacService.java](../src/main/java/com/watchman/ofac/OfacService.java)
- [report.html](../src/main/resources/templates/report.html)

---

## How to Validate

1. **Alias transparency:**
   - Search "AL-MALIZI" → verify response includes matchedAlias field
   - Check HTML report shows all entity aliases

2. **Name order:**
   - Search "AL-JASIM, Muhammad Husayn"
   - Search "Muhammad Husayn AL-JASIM"  
   - Verify same entity returned with same score

3. **Identifying attributes:**
   - Search "Nicolas Maduro"
   - Verify response includes: DOB, nationality, passport info from OFAC

4. **Alias matching:**
   - Search known aliases from alt.csv
   - Verify parent entity returned

5. **Match counts:**
   - Compare Watchman vs OFAC for known entities
   - Verify counts align

---

## Assumptions and Open Questions

### Assumptions
- OFAC data includes alias information in alt.csv (verified: yes)
- Name normalization acceptable for BSA/AML compliance (needs legal review)
- Current fuzzy matching algorithm (Jaro-Winkler) appropriate for name variants

### Open Questions
1. Is current alias data model correct? (SDNEntity → AlternateIdentity relationship)
2. Are all source files (add.csv, alt.csv) being parsed? Check [OfacService.java](../src/main/java/com/watchman/ofac/OfacService.java)
3. What is acceptable false-negative rate for compliance?
4. Should we add phonetic matching (Soundex/Metaphone) for name normalization?
5. Do we need to match addresses separately? (Currently not exposed)

### Out of Scope (This Phase)
- Performance tuning (address after functional fixes)
- UI enhancements beyond basic alias display
- Advanced name parsing (Chinese, Arabic transliteration)
- Address matching
- Ongoing compliance requirements beyond basic explainability
