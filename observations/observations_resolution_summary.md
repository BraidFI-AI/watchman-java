# Observations Resolution Summary

## Row 1: ABBAS, Abu - Related Entities
**Status**: ✅ FALSE POSITIVE - All entities returned correctly

**Issue Claim**: Related entities "ABU AL-ABBAS", "PLF-ABU ABBAS", "KATA'IB ABU FADL AL-ABBAS" not returned

**Test Results**:
- **ABU AL-ABBAS**: ✅ Found (entityID 23043) - Person alias of ABBAS, Abu  
- **PLF-ABU ABBAS**: ✅ Found (entityID 4708) - Business entity  
- **KATA'IB ABU FADL AL-ABBAS**: ✅ Found (entityID 46348) - Business alias  

**Conclusion**: All 4 claimed entities ARE properly returned. Retest comment was incorrect.

---

## Row 6-7: ARELLANO FELIX, Ramon Eduardo - Token Sequence Tie-Breaker
**Status**: ✅ FIXED - Implemented OFAC name reordering

**Issue**: Query "Ramon Eduardo ARELLANO FELIX" returned wrong person first (Eduardo Ramon YOB 1956 instead of Ramon Eduardo YOB 1964)

**Root Cause**: `hasTokenSequenceMatch()` compared raw entity names ("ARELLANO FELIX, Ramon Eduardo") against normalized queries ("ramon eduardo arellano felix"), causing sequence mismatches.

**Solution**: Implemented `reorderOFACName()` method in [SearchServiceImpl.java](src/main/java/io/moov/watchman/search/SearchServiceImpl.java#L775-L798):
- Converts "LAST, FIRST" → "FIRST LAST" before token sequence comparison
- Applied to both query and entity names in `hasTokenSequenceMatch()`

**Verification**: 
- Query "Ramon Eduardo ARELLANO FELIX" now correctly ranks "ARELLANO FELIX, Ramon Eduardo" (YOB 1964) first
- Test: [TokenSequenceMatchDebugTest.java](src/test/java/io/moov/watchman/search/TokenSequenceMatchDebugTest.java)

---

## Row 15: GHAILANI, Ahmed Khalfan - FOOPIE/FUPI Aliases
**Status**: ⚠️ NOT A DEFECT - Aliases missing from OFAC data

**Issue Claim**: Aliases "FOOPIE" and "FUPI" not matching GHAILANI entity

**Investigation**: 
- GHAILANI entity (entityID 6925) confirmed in OFAC data with 17 aliases
- Aliases present: "GHILANI, Ahmad Khalafan", "KHABAR, Abu", "BAKR, Abu", "AHMED, A.", etc.
- **FOOPIE and FUPI NOT in alias list**

**Test Results**:
- Search "FOOPIE": GHAILANI (entityID 6925) NOT found  
- Search "FUPI": GHAILANI (entityID 6925) NOT found

**Conclusion**: Aliases don't exist in current OFAC download. This is a data issue, not a system defect.

**Test**: [Row15GhailaniAliasTest.java](src/test/java/io/moov/watchman/observations/Row15GhailaniAliasTest.java)

---

## Row 50: KIM, Yong Ju - Name Order and Spacing Variations
**Status**: ⚠️ PARTIAL DEFECT - Apostrophe tokenization issue

**Issue**: Name variations (Yong Ju KIM, Yong KIM) and punctuation variations (Yong-chu, Yo'ng chu) not consistently detected

**Test Results**:

| Query | Entity Expected | Result |
|-------|----------------|--------|
| KIM, Yong Ju | KIM, Yong Ju (55451) | ✅ Found |
| Yong Ju KIM | KIM, Yong Ju (55451) | ✅ Found |
| Yong KIM | KIM, Yong* entities | ✅ Found |
| Yong chu KIM | KIM, Yo'ng-chu | ❌ NOT found |
| Yong-chu KIM | KIM, Yo'ng-chu | ❌ NOT found |
| Yo'ng chu KIM | KIM, Yo'ng-chu | ❌ NOT found |

**Root Cause**: Apostrophe tokenization mismatch
- OFAC entity: "KIM, Yo'ng-chu" → normalizes to "kim yo ng chu" (apostrophe → space)
- Search query: "Yong chu KIM" → normalizes to "yong chu kim"
- Token comparison: "yong" vs "yo" = low similarity (different words)

**Analysis**: [TextNormalizer.java](src/main/java/io/moov/watchman/similarity/TextNormalizer.java#L175-L177) replaces apostrophes with spaces, splitting "Yo'ng" into two tokens ["yo", "ng"]. This prevents matching Korean name romanizations where "Yo'ng" and "Yong" are equivalent.

**Impact**: This is a legitimate defect affecting Korean/Asian name matching where apostrophes indicate tone marks or romanization conventions.

**Test**: [Row50KimNameVariationsTest.java](src/test/java/io/moov/watchman/observations/Row50KimNameVariationsTest.java)

---

## Summary Statistics

| Row | Issue | Status | Code Change Required |
|-----|-------|--------|---------------------|
| 1 | ABBAS related entities | ✅ False Positive | None - works correctly |
| 6-7 | ARELLANO FELIX ranking | ✅ Fixed | Yes - added reorderOFACName() |
| 15 | FOOPIE/FUPI aliases | ⚠️ Not a Defect | None - missing OFAC data |
| 50 | KIM name variations | ⚠️ Partial Defect | Future - apostrophe handling |

**Fixes Implemented**: 1
**False Positives Identified**: 1  
**Data Issues Identified**: 1
**Remaining Defects**: 1 (apostrophe tokenization)
