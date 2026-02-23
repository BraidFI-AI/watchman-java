# YAML Configuration Migration - Project Checklist
**Project Start:** February 22, 2026  
**Goal:** Complete configuration transparency for all scoring parameters  
**Current Status:** 26/76 values surfaced (34% complete)

---

## Overview

**What We're Doing:**
Moving 50 hidden scoring parameters from code into the YAML configuration file so they can be controlled via the Admin UI.

**Why It Matters:**
- Faster tuning (30 min vs 2 days)
- Admin UI shows all parameters
- BSA consultant can see all thresholds
- Different deployments can use different configs

**Risk Level:** Low (just moving existing values, not changing behavior)

---

## Phase 1: Alias Boost Migration (PRIORITY)
**Timeline:** 1-2 hours  
**Values:** 4 parameters  
**Files:** 1 file (EntityScorerImpl.java)

### Deliverables:
- [ ] 1.1 Add 4 fields to WeightConfig.java
  - `aliasBoostRatio` (1.2)
  - `aliasMinThreshold` (0.45)
  - `aliasBoostAmount` (0.50)
  - Documentation: "BSA compliance - Row 17/24 fix"

- [ ] 1.2 Add 4 values to application.yml
  - Under `watchman.weights` section
  - Include comments explaining each value's purpose

- [ ] 1.3 Update EntityScorerImpl.java
  - Replace hard-coded `1.2` with `weightConfig.getAliasBoostRatio()`
  - Replace hard-coded `0.45` with `weightConfig.getAliasMinThreshold()`
  - Replace hard-coded `0.50` with `weightConfig.getAliasBoostAmount()`
  - Location: Lines 575-586

- [ ] 1.4 Run test suite
  - Expected: 1574 passing, 47 failing (no change)
  - If different: STOP and investigate

- [ ] 1.5 Manual verification
  - Start server
  - Test Row 17: "CK ID" returns entity 55865 at position 1
  - Test Row 24: "SMARTMET LLC" returns entity 47769

- [ ] 1.6 Update Admin UI (automatic)
  - Verify new fields appear in GET /api/admin/config
  - Test changing values via PUT /api/admin/config/weights

- [ ] 1.7 Git commit
  - Message: "feat: migrate alias boost values to YAML config"
  - Tag: `v1.0-alias-boost-yaml`

**Success Criteria:**
- Tests pass with same counts
- Admin UI shows 4 new parameters (30 total)
- Row 17/24 behavior unchanged
- Server starts without errors

---

## Phase 2: Address Component Weights
**Timeline:** 30 minutes  
**Values:** 3 parameters  
**Files:** 1 file (EntityScorerImpl.java)

### Deliverables:
- [ ] 2.1 Add 3 fields to WeightConfig.java
  - `addressCountryWeight` (0.3 = 30%)
  - `addressCityWeight` (0.3 = 30%)
  - `addressStreetWeight` (0.4 = 40%)

- [ ] 2.2 Add 3 values to application.yml
  - Document that country/city/street percentages must sum to 1.0

- [ ] 2.3 Update EntityScorerImpl.java
  - Location: Lines 443-462 (compareAddress method)
  - Replace 3 hard-coded values

- [ ] 2.4 Run test suite
  - Expected: 1574 passing, 47 failing

- [ ] 2.5 Git commit
  - Message: "feat: migrate address component weights to YAML"

**Success Criteria:**
- Tests pass
- Admin UI shows 33 total parameters
- Address scoring behavior unchanged

---

## Phase 3: Alias Selection Logic
**Timeline:** 30 minutes  
**Values:** 3 parameters  
**Files:** 1 file (EntityScorerImpl.java)

### Deliverables:
- [ ] 3.1 Add 3 fields to WeightConfig.java
  - `aliasHighScoreThreshold` (0.95)
  - `aliasCloseScoreTolerance` (0.05)
  - `aliasTokenLogicMinScore` (0.45)

- [ ] 3.2 Add 3 values to application.yml

- [ ] 3.3 Update EntityScorerImpl.java
  - Location: Lines 131, 311-320

- [ ] 3.4 Test and commit

**Success Criteria:**
- EntityScorerImpl now 90% YAML-controlled (10/13 values)

---

## Phase 4: Exact ID Match Logic
**Timeline:** 30 minutes  
**Values:** 3 parameters (2 new, 1 existing)  
**Files:** 1 file (EntityScorerImpl.java)

### Deliverables:
- [ ] 4.1 Add 2 fields to WeightConfig.java
  - `exactIdBaseContribution` (0.7 = 70%)
  - `exactIdNameContribution` (0.3 = 30%)
  - Note: exactMatchThreshold (0.99) already exists

- [ ] 4.2 Add 2 values to application.yml

- [ ] 4.3 Update EntityScorerImpl.java
  - Location: Lines 474-476

- [ ] 4.4 Test and commit

**Success Criteria:**
- EntityScorerImpl 100% YAML-controlled (13/13 values) ✅
- Admin UI shows 38 total parameters

---

## Phase 5: API Default Values
**Timeline:** 30 minutes  
**Values:** 2 parameters  
**Files:** 1 file (SearchService.java)

### Deliverables:
- [ ] 5.1 Add 2 fields to WeightConfig.java
  - `defaultSearchLimit` (10)
  - `defaultMinMatch` (0.88)

- [ ] 5.2 Add 2 values to application.yml

- [ ] 5.3 Update SearchService.java
  - Location: Line 41 (default search method)

- [ ] 5.4 Test and commit

**Success Criteria:**
- API defaults now configurable
- Admin UI shows 40 total parameters

---

## Phase 6: Supporting Info & Title Thresholds
**Timeline:** 1 hour  
**Values:** 5 parameters  
**Files:** 2 files

### Deliverables:
- [ ] 6.1 SupportingInfoComparer.java (3 values)
  - `supportingInfoMatchedThreshold` (0.5)
  - `supportingInfoExactThreshold` (0.99)
  - `secondaryStatusPenalty` (0.8)

- [ ] 6.2 EntityTitleComparer.java (2 values)
  - `titleMatchedThreshold` (0.5)
  - `titleExactThreshold` (0.99)

- [ ] 6.3 Add 5 fields to WeightConfig.java

- [ ] 6.4 Add 5 values to application.yml

- [ ] 6.5 Test and commit each file separately

**Success Criteria:**
- Admin UI shows 45 total parameters

---

## Phase 7: Name Scorer & Affiliation Logic
**Timeline:** 1 hour  
**Values:** 4 parameters  
**Files:** 2 files

### Deliverables:
- [ ] 7.1 NameScorer.java (1 value)
  - `nameScorerEarlyExitThreshold` (0.4)
  - Note: Performance optimization - affects speed vs accuracy tradeoff

- [ ] 7.2 AffiliationComparer.java (3 values)
  - `affiliationNameThreshold` (0.85)
  - `affiliationExactThreshold` (0.95)
  - `affiliationTypeThreshold` (0.9)

- [ ] 7.3 Add 4 fields to WeightConfig.java

- [ ] 7.4 Add 4 values to application.yml

- [ ] 7.5 Test and commit

**Success Criteria:**
- Admin UI shows 49 total parameters
- Simple files complete

---

## Phase 8: Address Comparer (COMPLEX)
**Timeline:** 2 hours  
**Values:** 7 parameters  
**Files:** 1 file (AddressComparer.java)

### Deliverables:
- [ ] 8.1 Design decision: WeightConfig or separate AddressConfig?
  - Option A: Add 7 fields to WeightConfig (simpler)
  - Option B: Create AddressConfig class (cleaner separation)
  - Decision: ____________

- [ ] 8.2 Add 7 fields to chosen config class
  - `addressLine1Weight` (5.0)
  - `addressLine2Weight` (2.0)
  - `addressCityWeight` (4.0)
  - `addressStateWeight` (2.0)
  - `addressPostalWeight` (3.0)
  - `addressCountryWeight` (4.0)
  - `addressHighConfidenceThreshold` (0.92)

- [ ] 8.3 Add 7 values to application.yml
  - Document these are internal weights (different from top-level address-weight)

- [ ] 8.4 Update AddressComparer.java
  - Location: Lines 28-36

- [ ] 8.5 Extensive testing
  - Run full test suite
  - Manual test address matching
  - Verify address scoring unchanged

- [ ] 8.6 Git commit

**Success Criteria:**
- Admin UI shows 56 total parameters
- Address matching behavior unchanged
- No performance degradation

---

## Phase 9: Date Comparer (MOST COMPLEX)
**Timeline:** 3-4 hours  
**Values:** 11+ parameters  
**Files:** 1 file (DateComparer.java)

### Deliverables:
- [ ] 9.1 Design decision: DateConfig class recommended
  - Rationale: 11+ related date parameters deserve own config
  - Create DateConfig.java with @ConfigurationProperties

- [ ] 9.2 Design date tolerance structure
  - Year comparison: decay rate, distant year floor
  - Month comparison: 3 tolerance bands (±1, ±2-3, >3)
  - Day comparison: 3 tolerance bands
  - Component weights: year, month, day percentages

- [ ] 9.3 Add fields to DateConfig.java
  - `dateYearDecayRate` (0.1 = 10% per year)
  - `dateDistantYearScore` (0.2)
  - `dateMonthTolerance1` (0.9)
  - `dateMonthTolerance2` (0.7)
  - `dateMonthTolerance3Plus` (0.3)
  - `dateDayTolerance0to3Start` (0.95)
  - `dateDayTolerance0to3Decay` (0.05)
  - `dateDayTolerance4to7` (0.7)
  - `dateDayTolerance8Plus` (0.3)
  - `dateYearWeight` (0.4 = 40%)
  - `dateMonthWeight` (0.3 = 30%)
  - `dateDayWeight` (0.3 = 30%)

- [ ] 9.4 Add DateConfig section to application.yml
  - Nest under `watchman.date-comparison`
  - Extensive documentation for each parameter

- [ ] 9.5 Update DateComparer.java
  - Inject DateConfig
  - Replace all hard-coded values
  - Location: Lines 44-88

- [ ] 9.6 Comprehensive testing
  - Run full test suite
  - Manual test date matching with various scenarios
  - Test edge cases (exact match, 1 year diff, 5+ years)

- [ ] 9.7 Update Admin UI
  - Add DateConfig endpoints if separate config class
  - Or extend existing weight config endpoint

- [ ] 9.8 Git commit
  - Message: "feat: migrate date comparison logic to DateConfig"

**Success Criteria:**
- Admin UI shows 67+ total parameters
- Date matching behavior unchanged
- All date tolerance bands configurable
- BSA consultant can tune birth date tolerances

---

## Phase 10: Integration & Documentation
**Timeline:** 2-3 hours

### Deliverables:
- [ ] 10.1 Update AdminConfigController.java
  - Add endpoints for any new config classes (DateConfig, AddressConfig if created)
  - Update AdminConfigResponse.from() to include all configs

- [ ] 10.2 Create CONFIG_REFERENCE.md
  - Document all 67+ parameters
  - Explain purpose of each value
  - Provide tuning guidance
  - Include valid ranges

- [ ] 10.3 Update Admin UI documentation
  - List all available configuration endpoints
  - Provide examples of changing values
  - Document reset-to-defaults behavior

- [ ] 10.4 Run full regression test suite
  - Expected: 1574 passing, 47 failing
  - Any change requires investigation

- [ ] 10.5 Performance testing
  - Benchmark search performance before/after
  - Verify no degradation from config lookups

- [ ] 10.6 Create migration summary report
  - Before/after comparison
  - List all new Admin UI parameters
  - Document validation steps taken

**Success Criteria:**
- All 76 parameters surfaced (100% complete)
- Admin UI fully functional
- Documentation complete
- Performance unchanged

---

## Phase 11: Safeguards (Prevent Regression)
**Timeline:** 2 hours

### Deliverables:
- [ ] 11.1 Add checkstyle rule
  - Fail build on numeric literals in scoring files
  - Exceptions: 0, 1, -1, array indices
  - Files to check: EntityScorerImpl, *Comparer, *Scorer

- [ ] 11.2 Create build-time test
  - Scan Java source files for magic numbers
  - Fail if literals found outside allowed patterns
  - Add to CI/CD pipeline

- [ ] 11.3 Update CONTRIBUTING.md
  - Add rule: "All scoring thresholds MUST be in config"
  - Provide examples of correct pattern
  - Code review checklist item

- [ ] 11.4 Create configuration validation tests
  - Test that YAML changes affect behavior
  - Test config edge cases (zero weights, negative values)
  - Test config validation/bounds checking

**Success Criteria:**
- Future hard-coding prevented
- CI fails if magic numbers added
- Clear documentation for developers

---

## Phase 12: BSA Consultant Handoff
**Timeline:** 1 hour

### Deliverables:
- [ ] 12.1 Create CONFIGURATION_FOR_CONSULTANT.md
  - Simple language (non-technical)
  - List all tunable parameters with explanations
  - Current values based on BSA testing rounds 1-2
  - How to request configuration changes

- [ ] 12.2 Provide Admin UI access
  - URL: http://[server]:8084/api/admin/config
  - Instructions for viewing current config
  - Note: Changes are in-memory only (requires restart to persist)

- [ ] 12.3 Schedule review meeting
  - Walk through new transparency
  - Explain what each parameter does
  - Discuss if any values should change based on testing results

**Success Criteria:**
- Consultant understands full scoring transparency
- Can request tuning changes via YAML config
- Future testing more collaborative

---

## Project Milestones

### Milestone 1: Core Scoring Migrated (Phases 1-4)
- EntityScorerImpl 100% YAML-controlled
- 13 new parameters surfaced
- Alias boost + address + ID matching configurable
- **ETA:** 3-4 hours

### Milestone 2: Simple Files Complete (Phases 5-7)
- SearchService, SupportingInfo, Title, Name, Affiliation migrated
- 11 additional parameters surfaced
- **ETA:** 1 day total

### Milestone 3: Complex Logic Migrated (Phases 8-9)
- Address and Date comparison fully configurable
- 18+ additional parameters surfaced
- Most complex business logic transparent
- **ETA:** 1.5 days total

### Milestone 4: Production Ready (Phases 10-12)
- All 76 parameters in YAML
- Admin UI complete
- Documentation finished
- Safeguards in place
- Consultant handoff complete
- **ETA:** 2 days total

---

## Risk Management

### Low Risk Items (Do First):
- Phase 1: Alias boost (just modified, well understood)
- Phase 2: Address weights (simple percentages)
- Phase 5: API defaults (straightforward)

### Medium Risk Items:
- Phase 3-4: Alias selection + exact ID (more complex logic)
- Phase 6-7: Supporting info + affiliations (less frequently used)

### High Risk Items (Do Last, Most Testing):
- Phase 8: Address comparer (many interactions)
- Phase 9: Date comparer (most complex algorithm)

### Rollback Strategy:
- Git commit after each phase
- Test suite validates each step
- Can revert to any previous milestone
- Admin UI reset-to-defaults as safety net

---

## Success Metrics

### Quantitative:
- [ ] 76/76 parameters in YAML configuration (100%)
- [ ] 1574 tests passing (maintained)
- [ ] 47 tests failing (unchanged - known tech debt)
- [ ] 0 new test failures
- [ ] Admin UI response time < 100ms
- [ ] Search performance unchanged (within 5%)

### Qualitative:
- [ ] BSA consultant can see all scoring thresholds
- [ ] Configuration changes require restart, not recompile
- [ ] Future tuning takes 30 min instead of 2 days
- [ ] Compliance audits can review transparent algorithm
- [ ] Different deployments can use different configs

---

## Timeline Summary

**Conservative Estimate:**
- Week 1: Phases 1-7 (simple migrations)
- Week 2: Phases 8-9 (complex migrations)
- Week 3: Phases 10-12 (integration, docs, handoff)

**Aggressive Estimate:**
- Days 1-2: Phases 1-7
- Days 3-4: Phases 8-9
- Day 5: Phases 10-12

**Recommended Approach:**
- Do Milestone 1 (Phases 1-4) this week
- Let consultant continue testing
- Complete remaining phases next sprint

---

## Sign-Off

- [ ] Phase 1 complete - Sign-off: __________ Date: __________
- [ ] Phase 4 complete (Milestone 1) - Sign-off: __________ Date: __________
- [ ] Phase 7 complete (Milestone 2) - Sign-off: __________ Date: __________
- [ ] Phase 9 complete (Milestone 3) - Sign-off: __________ Date: __________
- [ ] Phase 12 complete (Milestone 4) - Sign-off: __________ Date: __________

---

**Project Owner:** Randy Sannicolas  
**Technical Lead:** GitHub Copilot (Claude Sonnet 4.5)  
**Start Date:** February 22, 2026  
**Target Completion:** March 15, 2026 (3 weeks)
