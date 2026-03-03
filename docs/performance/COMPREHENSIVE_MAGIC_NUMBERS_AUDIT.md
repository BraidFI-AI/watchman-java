# Magic Numbers Audit - Watchman Java
**Date:** February 22, 2026 | **Status:** 🔴 CRITICAL

## Summary
- **50+ hard-coded values** found across 10 files
- **~42 new WeightConfig fields** needed (currently have 13)
- **ScoreConfig achieved ~20% coverage** of design intent
- **~451 BSA test cases** validated against opaque scoring system

---

## Files & Values

| File | Count | Critical Values |
|------|-------|----------------|
| **DateComparer.java** | 11+ | Year decay (0.1), month tolerance (0.9/0.7/0.3), day tolerance (0.95/0.7/0.3), component weights (40%/30%/30%) |
| **EntityScorerImpl.java** | 13 | Alias boost (1.2, 0.45, 0.50), address weights (0.3/0.3/0.4), exact ID (0.7/0.3), alias selection (0.95/0.05/0.45) |
| **AddressComparer.java** | 7 | Field weights: line1(5.0), line2(2.0), city(4.0), state(2.0), postal(3.0), country(4.0), high confidence(0.92) |
| **SupportingInfoComparer.java** | 3 | Matched (0.5), exact (0.99), secondary penalty (0.8) |
| **AffiliationComparer.java** | 3 | Affiliation name (0.85), exact match (0.95), type (0.9) |
| **SearchService.java** | 2 | Default limit (10), default minMatch (0.88) |
| **EntityTitleComparer.java** | 2 | Matched (0.5), exact (0.99) |
| **IntegrationFunctions.java** | 2 | Exact threshold (0.99) - duplicates YAML value |
| **NameScorer.java** | 1 | Early exit threshold (0.4) |

---

## Root Cause
1. Pre-ScoreConfig implementation used inline constants
2. ScoreConfig extraction only captured top-level weights (13/55 values)
3. Alias boost code (Feb 14) followed existing hard-coded pattern
4. No automated checks prevented magic numbers

---

## Actions Required

### Immediate (This Week):
1. **Add 42 fields to WeightConfig.java** - Extract all values
2. **Update application.yml** - Document BSA-validated defaults
3. **Replace references** - Change hard-coded to `weightConfig.getXXX()`
4. **Add safeguards** - Checkstyle rule to fail on numeric literals in scoring code

### Validation:
- Results are valid (no functional bugs)
- Test suite will prevent regressions
- BSA test cases remain correct

---

**Impact:** ~80% of scoring logic bypassed configuration architecture  
**Risk:** Future tuning requires recompilation, BSA testing compromised  
**Priority:** Extract to YAML before next BSA testing round
