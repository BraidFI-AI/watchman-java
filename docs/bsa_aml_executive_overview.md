# Watchman Sanctions Screening: Evidence Summary for BSA/AML Review

**Date:** January 22, 2026  
**Purpose:** Evidence package for consultant review of sanctions screening effectiveness

---

## System Under Review

**Watchman** is a sanctions screening platform that checks names against OFAC SDN, US CSL, EU CSL, and UK Sanctions Lists. Two implementations exist:

- **Go implementation** (original system, currently in production on Fly.io)
- **Java implementation** (port of Go system, deployed on AWS ECS)

The organization is evaluating whether the Java implementation provides adequate sanctions screening capabilities for BSA/AML compliance requirements.

---

## Testing Methodology

To evaluate screening effectiveness, we conducted cross-validation testing against four independent systems:

1. **Java Watchman** - System under evaluation (AWS ECS deployment)
2. **Go Watchman** - Original reference implementation (Fly.io deployment)
3. **OFAC-API** - Commercial sanctions screening provider (third-party benchmark)
4. **Braid Sandbox** - Existing production compliance system

**Test Approach:**  
Direct name searches using exact SDN entity names from official OFAC lists to measure detection accuracy and scoring consistency across systems.

---

## Observed Test Results

**Wave 1: High-Risk Sanctioned Entities (Direct Name Matches)**

| Test Input | Java Score | Go Score | OFAC-API Score | Braid Status |
|------------|-----------|----------|----------------|--------------|
| TALIBAN | 1.0 (100%) | 0.812 (81%) | 100 | BLOCKED |
| AL-QAIDA | 1.0 (100%) | 0.812 (81%) | 95 | BLOCKED |
| HAMAS | 1.0 (100%) | 0.812 (81%) | 100 | BLOCKED |
| HEZBOLLAH | 0.895 (89%) | 0.769 (77%) | No exact match | BLOCKED |
| ISLAMIC STATE | 1.0 (100%) | 0.690 (69%) | 99 | BLOCKED |

**Observations:**

- All four systems successfully identified and flagged all five test entities
- Java and Go scores differ consistently (Java scores higher in all cases)
- Typical scoring gap: 19 percentage points (Java 100% vs. Go 81%)
- Largest scoring gap: 31 percentage points on "ISLAMIC STATE" (Java 100% vs. Go 69%)
- OFAC-API commercial system scores align more closely with Java (within 1-5 points) than Go (19-30 point difference)
- Braid production system blocked all entities despite scoring variations

---

## Questions for Consultant Evaluation

We seek your expert opinion on the following:

**1. Scoring Consistency**  
Is the observed 19-31% scoring difference between implementations a material concern for compliance effectiveness? All systems blocked the entities, but scores varied significantly.

**2. Benchmark Alignment**  
Java scores align more closely with the commercial OFAC-API provider. Does this alignment indicate better calibration, or is the scoring difference immaterial if all systems detect the entity?

**3. Edge Cases**  
The "HEZBOLLAH" test showed no exact match in OFAC-API (returned related entity "ANSAR-E HEZBOLLAH" instead), yet Braid blocked it. What are the compliance implications of these alias/related entity matching variations?

**4. False Negative Risk**  
Given that Go scored "ISLAMIC STATE" at 69% while Java and OFAC-API scored 99-100%, could the Go implementation's lower scoring pose false negative risk if name variations or typos were introduced?

**5. Adequacy Determination**  
Based on the evidence provided, does the Java implementation demonstrate adequate sanctions screening capability for BSA/AML compliance purposes?

---

## Available Evidence Documentation

Detailed test results and technical documentation are available for your review:

- **[divergence_evidence.md](divergence_evidence.md)** - Complete test case results with additional entity tests
- **[feature_parity_gaps.md](feature_parity_gaps.md)** - Comparison of algorithm implementations
- **[test_coverage.md](test_coverage.md)** - Testing methodology documentation

We welcome your questions and are available for technical walkthroughs of the screening algorithms or additional test scenarios you may recommend.
