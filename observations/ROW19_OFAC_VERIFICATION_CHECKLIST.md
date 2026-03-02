# Row 19 OFAC Verification Checklist

## Purpose
Verify whether PIJ entity with alias "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS" should appear when searching "HIZBALLAH BAYT AL-MAQDIS" by checking OFAC ground truth.

## Website
https://sanctionssearch.ofac.treas.gov/

## Test Queries

### Query 1: Verify PIJ Entity Exists
**Search**: `PALESTINE ISLAMIC JIHAD`

**Expected Result**: Entity should appear (likely top result)

**Check**:
- [ ] Entity name: "PALESTINE ISLAMIC JIHAD - SHAQAQI FACTION" 
- [ ] Entity ID/Program: Should show "SDGT", "FTO"
- [ ] Click entity → View "a.k.a." (also known as) list
- [ ] Look for: "ABU GHUNAYM SQUAD OF THE HIZBALLAH BAYT AL-MAQDIS"

**Record**:
- Is alias present? YES / NO
- If NO: Note approximate date alias was removed (check OFAC changelog if available)

---

### Query 2: Direct Alias Search
**Search**: `ABU GHUNAYM SQUAD`

**Expected Result**: Should return PIJ if alias is active

**Check**:
- [ ] Does search return any results?
- [ ] If YES: Does "PALESTINE ISLAMIC JIHAD" appear?
- [ ] If YES: What position/rank? (1st, 2nd, 3rd, etc.)

**Record**:
- Position: ______
- Score/Match strength (if shown): ______

---

### Query 3: Row 19 Actual Query (BSA Consultant Test)
**Search**: `HIZBALLAH BAYT AL-MAQDIS`

**Expected Result Per BSA**: PIJ should appear due to alias match

**Check**:
- [ ] What results appear? (List top 5)
  1. ___________________________
  2. ___________________________
  3. ___________________________
  4. ___________________________
  5. ___________________________

- [ ] Does "PALESTINE ISLAMIC JIHAD" appear in results?
- [ ] If YES: Position: ______
- [ ] If YES: Is "ABU GHUNAYM SQUAD..." shown as matched alias?
- [ ] If NO: Does any result mention "ABU GHUNAYM"?

**Record**:
- Top result: ___________________________
- Does "HIZBALLAH" (generic) entity appear? YES / NO
- Position of generic "HIZBALLAH": ______

---

## Decision Matrix

| OFAC Results | Interpretation | Action |
|--------------|---------------|--------|
| PIJ has alias, appears in search | Our scoring broken | Fix scoring algorithm |
| PIJ has alias, does NOT appear in search | OFAC also doesn't match it for this query | Consider test expectations wrong |
| PIJ does NOT have alias anymore | Alias was delisted | Delete Row 19 test as obsolete |
| PIJ entity entirely missing | Entity delisted | Delete Row 19 test as obsolete |

---

## Notes
- OFAC website uses fuzzy matching threshold ~95%
- Compare OFAC ranking to our system: [ComprehensiveBSAValidationTest.java](../src/test/java/io/moov/watchman/observations/ComprehensiveBSAValidationTest.java#L112)
- Our system currently returns: "HIZBALLAH" (generic) at score 1.0, PIJ not found even at minMatch=0.50
- Reference: [QuickPijScoreTest.java](../src/test/java/io/moov/watchman/observations/QuickPijScoreTest.java) shows PIJ scores <0.50

---

## Post-Verification Action

**If PIJ alias exists and OFAC returns it:**
→ Uncomment Row 19 in [ComprehensiveBSAValidationTest.java](../src/test/java/io/moov/watchman/observations/ComprehensiveBSAValidationTest.java#L112-L120)  
→ Debug why alias scores <0.50 instead of ~1.0 with query coverage boost  
→ Reference: [JaroWinklerSimilarity.java:722](../src/main/java/io/moov/watchman/similarity/JaroWinklerSimilarity.java#L722-L734) (boost implementation)

**If PIJ alias removed or never matches:**
→ Delete Row 19 test entirely  
→ Update [ROW19_ISSUE_SUMMARY.md](ROW19_ISSUE_SUMMARY.md) with OFAC findings  
→ Update pass rate to 51/51 (100%)

---

## Files to Update After Verification

- [ ] [ComprehensiveBSAValidationTest.java](../src/test/java/io/moov/watchman/observations/ComprehensiveBSAValidationTest.java#L112-L120)
- [ ] [ROW19_ISSUE_SUMMARY.md](ROW19_ISSUE_SUMMARY.md)
- [ ] [agent-context.md](../agent-context.md) (if test deleted, remove Row 19 reference)

---

**Verification Date**: _____________  
**Verified By**: _____________  
**OFAC Data Version**: _____________ (check website footer or changelog)
