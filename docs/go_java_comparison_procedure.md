# Go → Java Feature Parity Comparison Procedure

**Document Type:** Process & Methodology  
**Version:** 1.0  
**Date:** January 8, 2026  
**Audience:** Engineering Team

---

## Executive Summary

This document describes our approach to achieving feature parity between the Go and Java implementations of the Watchman entity matching system, addressing the question: **"Are we comparing intermediate pipeline steps, or just final outputs?"**

**Current Status:**
- ✅ **End-to-end validation:** We test final outputs match expected behavior
- ❌ **Step-by-step validation:** We do NOT yet compare intermediate pipeline states between Go and Java
- 📊 **Feature coverage:** 57/200 features fully implemented (28.5%)

---

## Current Comparison Methodology

### Phase 0-1 Approach: Algorithm Implementation + Testing

**How we validate parity:**

1. **Read Go source code** to understand algorithm behavior
2. **Port to Java** with equivalent logic
3. **Write TDD tests** with expected input → output
4. **Verify tests pass** (green phase)

**Example - Multilingual Stopwords:**
```java
@Test
void testSpanishStopwords() {
    // INPUT
    Entity entity = Entity.of("test", "Juan de la Rosa", ...);
    
    // PROCESS (black box)
    Entity normalized = entity.normalize();
    
    // EXPECTED OUTPUT
    assertTrue(normalized.preparedFields()
        .normalizedNamesWithoutStopwords()
        .contains("juan rosa"));  // "de" and "la" removed ✅
}
```

**What this validates:**
- ✅ Final output is correct
- ✅ Algorithm produces expected behavior
- ✅ Java matches documented Go behavior

**What this does NOT validate:**
- ❌ Intermediate pipeline steps match Go
- ❌ Language detection result is same as Go
- ❌ Normalization order matches Go
- ❌ Each transformation step matches Go

---

## Gap Identified: Missing Step-by-Step Validation

### Question from Team Member

> "But did it break it out into each step and compare the result after each step? Like what this string looks like on each side after preparation step?"

**Answer:** No, not yet. Here's what that would look like:

### Example: Step-by-Step Pipeline Comparison

**Input:** `"José de la Cruz Corporation LLC"`

| Step | Description | Go Output | Java Output | Match? |
|------|-------------|-----------|-------------|---------|
| 0 | Raw Input | `"José de la Cruz Corporation LLC"` | `"José de la Cruz Corporation LLC"` | ✅ |
| 1 | Reorder SDN | `"José de la Cruz Corporation LLC"` | `"José de la Cruz Corporation LLC"` | ✅ |
| 2 | Remove Apostrophes | `"José de la Cruz Corporation LLC"` | `"José de la Cruz Corporation LLC"` | ✅ |
| 3 | Normalize (lower + punctuation) | `"jose de la cruz corporation llc"` | `"jose de la cruz corporation llc"` | ✅ |
| 4 | Detect Language | `"es"` | `"es"` | ✅ |
| 5 | Remove Stopwords (ES) | `"jose cruz corporation llc"` | `"jose cruz corporation llc"` | ✅ |
| 6 | Remove Company Titles (1st) | `"jose cruz corporation"` | `"jose cruz corporation"` | ✅ |
| 7 | Remove Company Titles (2nd) | `"jose cruz"` | `"jose cruz"` | ✅ |
| **FINAL** | **PreparedFields** | `"jose cruz"` | `"jose cruz"` | ✅ |

**If there's a mismatch at Step 5:**
- We know the problem is in stopword removal, not later steps
- We can debug: Is it language detection? Stopword list? Algorithm logic?
- Faster root cause analysis

---

## Proposed Enhancement: Step-by-Step Validation

### Option 1: Instrumented Debug Test

Create a test that logs intermediate steps for comparison:

```java
@Test
void testPipelineStepByStep() {
    String input = "José de la Cruz Corporation LLC";
    Entity entity = Entity.of("test", input, EntityType.PERSON, SourceList.US_OFAC);
    
    // Create instrumented normalizer that logs each step
    InstrumentedNormalizer instrumenter = new InstrumentedNormalizer();
    Entity normalized = entity.normalize(instrumenter);
    
    // Get pipeline steps
    List<PipelineStep> steps = instrumenter.getSteps();
    
    // Print for comparison with Go
    System.out.println("=== JAVA PIPELINE ===");
    for (PipelineStep step : steps) {
        System.out.printf("%-25s: %s%n", step.name(), step.output());
    }
    
    // Expected steps (from running Go equivalent)
    assertStep(steps, 0, "Raw Input", "José de la Cruz Corporation LLC");
    assertStep(steps, 1, "Reorder SDN", "José de la Cruz Corporation LLC");
    assertStep(steps, 2, "Remove Apostrophes", "José de la Cruz Corporation LLC");
    assertStep(steps, 3, "Normalize", "jose de la cruz corporation llc");
    assertStep(steps, 4, "Detect Language", "es");
    assertStep(steps, 5, "Remove Stopwords", "jose cruz corporation llc");
    assertStep(steps, 6, "Remove Company Titles", "jose cruz");
}
```

### Option 2: Go-Java Comparison Test Harness

Create a test that runs BOTH Go and Java:

```bash
# Test harness script
./compare-pipelines.sh "José de la Cruz Corporation LLC"

=== GO PIPELINE ===
Step 1 (Reorder):     José de la Cruz Corporation LLC
Step 2 (Normalize):   jose de la cruz corporation llc
Step 3 (Language):    es
Step 4 (Stopwords):   jose cruz corporation llc
Step 5 (Titles):      jose cruz
FINAL:                jose cruz

=== JAVA PIPELINE ===
Step 1 (Reorder):     José de la Cruz Corporation LLC
Step 2 (Normalize):   jose de la cruz corporation llc
Step 3 (Language):    es
Step 4 (Stopwords):   jose cruz corporation llc
Step 5 (Titles):      jose cruz
FINAL:                jose cruz

✅ ALL STEPS MATCH
```

### Option 3: Automated Parity Test Suite

Create a JSON test corpus with intermediate steps:

```json
{
  "test_cases": [
    {
      "name": "spanish_stopwords_with_titles",
      "input": "José de la Cruz Corporation LLC",
      "expected_steps": {
        "raw": "José de la Cruz Corporation LLC",
        "reordered": "José de la Cruz Corporation LLC",
        "normalized": "jose de la cruz corporation llc",
        "language": "es",
        "stopwords_removed": "jose cruz corporation llc",
        "titles_removed": "jose cruz",
        "final": "jose cruz"
      }
    }
  ]
}
```

Test against both:
```java
@ParameterizedTest
@JsonFileSource("pipeline-test-cases.json")
void testPipelineStepsParity(TestCase testCase) {
    // Run Java pipeline
    PipelineResult javaResult = runJavaPipeline(testCase.input);
    
    // Compare each step
    assertEquals(testCase.expected.normalized, javaResult.normalized);
    assertEquals(testCase.expected.language, javaResult.language);
    assertEquals(testCase.expected.stopwords_removed, javaResult.stopwordsRemoved);
    // ... etc
}
```

---

## Recommendation: Phased Approach

### Immediate (Phase 1 Complete) ✅
- Continue current methodology: port features + end-to-end tests
- Document expected behavior in test assertions
- This gets us to functional parity fastest

### Phase 2 (Next): Add Step-by-Step Validation
1. **Week 1:** Create instrumented test for 5 key entities
   - Entity with Spanish stopwords
   - Entity with company titles
   - Entity with SDN name format
   - Entity with Chinese characters
   - Entity with mixed Latin/Cyrillic
2. **Week 2:** Run Go equivalent and capture intermediate steps
3. **Week 3:** Build comparison harness (Option 2)
4. **Week 4:** Document any divergences found

### Phase 3: Automated Continuous Validation
1. Create JSON test corpus with 100+ examples
2. Build CI pipeline that runs both Go and Java
3. Automated step-by-step comparison reports
4. Regression detection: new code changes don't break parity

---

## Benefits of Step-by-Step Validation

### 1. **Faster Debugging**
- **Without:** "Final output is wrong, where's the bug?" (search everywhere)
- **With:** "Step 4 diverges, bug is in stopword removal" (pinpoint immediately)

### 2. **Higher Confidence**
- **Without:** "Tests pass, but are we *really* equivalent?"
- **With:** "Every intermediate step matches, proven parity"

### 3. **Easier Refactoring**
- **Without:** Change algorithm → hope tests still pass
- **With:** Change algorithm → see exactly which steps changed

### 4. **Documentation**
- Step-by-step output becomes living documentation
- New team members can see exactly what each function does

### 5. **Compliance/Audit Trail**
- Regulators ask: "How does this entity get normalized?"
- Show them: "Here's the pipeline, step by step, with examples"

---

## Current Status & Next Steps

### ✅ Completed (Phase 0-1)
- End-to-end testing methodology established
- 60/60 Phase 1 tests passing
- Feature parity tracking document (FEATURE_PARITY_GAPS.md)

### 🎯 Recommended Next Steps

**Option A: Quick Win (2-3 days)**
- Create instrumented test for 5 entities
- Manually run Go equivalent
- Document any divergences found
- **Goal:** Validate current implementation is accurate

**Option B: Full Automation (2 weeks)**
- Build Go-Java comparison harness
- Create JSON test corpus (50+ cases)
- Automated CI pipeline
- **Goal:** Continuous validation for all future work

**Option C: Defer Until Phase 3**
- Continue current methodology through Phase 2
- Add step-by-step validation when 80%+ feature parity achieved
- **Goal:** Balance speed vs. rigor

---

## Conclusion

**Current approach (end-to-end testing) is appropriate for:**
- Rapid feature implementation
- Getting to functional parity quickly
- Validating business requirements

**Step-by-step validation adds value for:**
- Debugging complex mismatches
- Proving algorithmic equivalence
- Compliance and audit requirements
- Long-term maintenance confidence

**Recommendation:** Continue current methodology for Phase 2, plan step-by-step validation for Phase 3 when we have 80%+ feature parity and need to prove exact algorithmic equivalence.

---

## Appendix: Example Pipeline Visualization

### Entity: "BANCO DE LA REPÚBLICA, S.A."

```
┌─────────────────────────────────────────────────────────────────┐
│ INPUT: "BANCO DE LA REPÚBLICA, S.A."                            │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 1: Reorder SDN Name                                        │
│ Go:   "BANCO DE LA REPÚBLICA, S.A."                             │
│ Java: "BANCO DE LA REPÚBLICA, S.A."                             │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 2: Remove Apostrophes                                      │
│ Go:   "BANCO DE LA REPÚBLICA, S.A."                             │
│ Java: "BANCO DE LA REPÚBLICA, S.A."                             │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 3: Lower + Remove Punctuation                              │
│ Go:   "banco de la republica sa"                                │
│ Java: "banco de la republica sa"                                │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 4: Detect Language                                         │
│ Go:   "es" (Spanish)                                            │
│ Java: "es" (Spanish)                                            │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: Remove Stopwords (Spanish)                              │
│ Go:   "banco republica sa"  (removed: de, la)                   │
│ Java: "banco republica sa"  (removed: de, la)                   │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ Step 6: Remove Company Titles (Iteration 1)                     │
│ Go:   "banco republica"  (removed: sa)                          │
│ Java: "banco republica"  (removed: sa)                          │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ FINAL OUTPUT: PreparedFields.normalizedNamesWithoutCompanyTitles│
│ Go:   ["banco republica"]                                       │
│ Java: ["banco republica"]                                       │
│ ✅ MATCH                                                         │
└─────────────────────────────────────────────────────────────────┘
```

**If Step 5 showed a mismatch:**
```
┌─────────────────────────────────────────────────────────────────┐
│ Step 5: Remove Stopwords (Spanish)                              │
│ Go:   "banco republica sa"  (removed: de, la)                   │
│ Java: "banco de la republica sa"  (removed: nothing!)           │
│ ❌ MISMATCH - Bug in Java stopword removal!                     │
└─────────────────────────────────────────────────────────────────┘
```
→ We know exactly where to look: Java's Spanish stopword list or language-aware removal logic.

---

## Questions or Feedback?

Contact: [Your Name/Team]  
Document Location: `/docs/GO_JAVA_COMPARISON_PROCEDURE.md`  
Last Updated: January 8, 2026
