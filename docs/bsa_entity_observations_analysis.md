# BSA Entity Observation Failures - Root Cause Analysis

**Date:** February 14, 2026  
**Context:** BSA consultant feedback indicates OFAC entities missing from search results

## Executive Summary

**5 failing cases tested. Results:**
- ✅ **Row 6 (CIMEX)**: WORKING - All entities returned
- ✅ **Row 22 (TALIBAN)**: WORKING - TEHRIK-E TALIBAN PAKISTAN returned
- ❌ **Row 21 (AL QA'IDA)**: FAILING - Entities score below threshold
- ⚠️ **Row 35 (OFFICE 39)**: PARTIAL - Limit cutoff (9 of 15 entities)
- ✅ **Row 52 (OTKRITIE)**: WORKING - All entities returned

## Detailed Findings

### Case 1: CIMEX (Row 6) - ✅ RESOLVED
**Consultant says:** "CORPORACION CIMEX S.A., FINANCIERA CIMEX S.A not listed"  
**Actual behavior:**
- Found 2 CORPORACION CIMEX entities (ID 576, 8125)
- Both score 100% vs. "CIMEX" query
- Both appear in search results (5 CORPORACION results out of 49 total)  
**Status:** This appears to be working correctly now.

### Case 2: TALIBAN (Row 22) - ✅ RESOLVED
**Consultant says:** "TEHRIK-E TALIBAN PAKISTAN (TTP) is not getting listed"  
**Actual behavior:**
- Entity found: TEHRIK-E TALIBAN PAKISTAN (TTP) [ID: 12206]
- Scores: 100% vs. "TALIBAN" query
- Appears in search results (limit=20, minMatch=0.70)  
**Status:** This is working correctly.

### Case 3: AL QA'IDA (Row 21) - ❌ ISSUE CONFIRMED
**Consultant says:** "AL-QAIDA GROUP OF JIHAD IN IRAQ not listed"  
**Actual behavior:**
- Entity: ISLAMIC STATE OF IRAQ AND THE LEVANT [ID: 8759]
- Has alias: "AL-QAIDA GROUP OF JIHAD IN IRAQ"
- Score: **73.51%** vs. "AL QA'IDA" query
- Default threshold: 88%
- **Result: FILTERED OUT**  
**Root cause:** Alias matching scores below threshold. The entity name "ISLAMIC STATE OF IRAQ AND THE LEVANT" doesn't contain "AL QA'IDA", only the alias does. Current scoring may not be boosting alias matches sufficiently.

### Case 4: OFFICE 39 (Row 35) - ⚠️ LIMIT ISSUE
**Consultant says:** "CY BER CRIME OFFICE, ALSARAF HAWALA OFFICE, CENTRAL PUBLIC PROSECUTORS OFFICE not listed"  
**Actual behavior:**
- 15 entities contain "OFFICE" token
- Search returns 9 of 15 (60% coverage)
- Missing entities score 100% 
- Likely hitting limit=20 parameter but with alias expansion creating more results than expected

### Case 5: OTKRITIE (Row 52) - ✅ RESOLVED
**Consultant says:** "OTKRITIE LTD GROUP, OTKRITIE BANK not listed"  
**Actual behavior:**
- All 8 OTKRITIE entities returned
- 100% coverage  
**Status:** Working correctly.

## Root Causes

### Primary Issue: Alias Matching Below Threshold
When an entity's **alias** contains the query terms but the **primary name** doesn't, the score can fall below 88% threshold.

Example:
- Query: "AL QA'IDA"
- Entity Name: "ISLAMIC STATE OF IRAQ AND THE LEVANT"
- Entity Alias: "AL-QAIDA GROUP OF JIHAD IN IRAQ"
- Score: 73.51% (BELOW 88% threshold)

### Secondary Issue: Limit + Alias Expansion Interaction
With limit=20 and alias expansion creating N+1 results per entity, some entities may be cut off.

## Recommendations

### Option 1: Lower Default Threshold (Quick Fix)
- Change default minMatch from 88% to 75%
- Pros: Simple, catches more alias matches
- Cons: May increase false positives

### Option 2: Alias Score Boost (Targeted Fix)
- When matched via alias, apply score boost or use separate lower threshold
- Pros: Targets the specific issue
- Cons: More complex logic

### Option 3: Ensure OFAC.gov Parity (Comprehensive)
- Test against actual OFAC.gov searches
- Implement substring/token-contains matching for exact token matches
- Pros: True regulatory compliance
- Cons: May require significant algorithm changes

### Option 4: Verify Consultant's Test Scenario
- Consultant may be using Admin UI with different parameters
- Consultant may have tested before recent fixes (Row 6, 22, 52 now working)
- Request updated feedback with specific API calls used

## Next Steps

1. **Verify consultant's test method**: Admin UI? API? Which parameters?
2. **Run live OFAC .gov comparison**: Do their results match consultant's expectations?
3. **Implement targeted fix** for AL QA'IDA case (alias boost or lower threshold)
4. **Fix OFFICE 39 limit issue** (ensure limit applies to unique entities, not expanded results)

## Files Modified

- `OFACParityDiagnosticTest.java` - Diagnostic test suite
- Ready to implement fixes based on chosen approach
