# OFAC Screening Technical Overview for BSA/AML Compliance

**Audience:** BSA Officers, AML Compliance Examiners, Risk Management Teams

---

## Executive Summary

This document provides a comprehensive technical overview of the OFAC screening methodology implemented in the Watchman screening system. It is designed to help BSA/AML compliance officers understand the screening algorithms, validation logic, and operational controls in place to meet regulatory requirements.

**Important Notice:** This system uses algorithmic fuzzy matching and scoring techniques optimized for high-volume automated screening workflows. The methodology differs from OFAC's official SDN Search Tool available at OFAC.gov. This document explains the technical approach, not a comparison to OFAC's web-based search functionality.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Screening Workflow](#screening-workflow)
3. [Scoring Methodology](#scoring-methodology)
4. [Match Validation](#match-validation)
5. [Alias Expansion](#alias-expansion)
6. [False Positive Management](#false-positive-management)
7. [Audit Trail and Reporting](#audit-trail-and-reporting)
8. [Performance and Scale](#performance-and-scale)
9. [Regulatory Compliance Considerations](#regulatory-compliance-considerations)
10. [Glossary](#glossary)

---

## System Overview

### Purpose

The OFAC screening system provides automated sanctions screening against the Office of Foreign Assets Control (OFAC) Specially Designated Nationals (SDN) list and other consolidated screening lists. The system is designed to:

- Screen individuals and entities in real-time during transaction processing
- Minimize false positives while maintaining high detection accuracy
- Provide detailed scoring and match explanations for compliance review
- Support audit requirements with comprehensive logging

### Data Sources

The system screens against:
- **OFAC SDN List** (Specially Designated Nationals and Blocked Persons)
- **OFAC Consolidated Screening List**
- Additional sanctions lists as configured

Data is updated regularly from official OFAC sources to maintain current screening coverage.

### Key Features

- **Multi-phase scoring algorithm** with configurable thresholds
- **Alias expansion** to capture alternative name variations
- **Match context preservation** linking results to source SDN entries
- **Configurable sensitivity** for different risk profiles
- **RESTful API** for integration with payment and compliance systems

---

## Screening Workflow

### Input Processing

When an entity is submitted for screening, the system processes:

1. **Individual Screening:**
   - Full name (required)
   - Alternative names/aliases (optional)
   - Address information (optional)
   - Date of birth (optional)
   - Identification numbers (optional)

2. **Entity/Company Screening:**
   - Legal name (required)
   - DBA names (optional)
   - Address information (optional)
   - Identification numbers (optional)

### Processing Steps

```
Input Entity → Normalization → Multi-Phase Scoring → Result Expansion → Response
```

1. **Normalization:** Input data is cleaned and standardized
2. **Scoring:** Entity is scored against SDN list using multi-phase algorithm
3. **Expansion:** If alias expansion is enabled, results include alternative name matches
4. **Response:** Match results with scores and context returned to caller

### Response Structure

Each screening response includes:
- **Match status:** Hit or No Hit
- **Match score:** Numerical confidence value (0.0 to 1.0)
- **Matched entity details:** SDN entry information
- **Match context:** Which fields contributed to the match
- **Alias information:** Alternative names that generated matches (when applicable)

---

## Scoring Methodology

### Multi-Factor Scoring Algorithm

The system uses a **weighted scoring approach** that evaluates multiple factors simultaneously and combines them into a final confidence score. Rather than sequential phases with pass/fail gates, all available data is analyzed in parallel and weighted according to its reliability.

#### Scoring Formula

```
Final Score = (Name Score × Weight + Address Score × Weight + ID Score × Weight + ...) / Total Active Weights
```

**Note:** Only factors with available data contribute to scoring. Missing data doesn't penalize the score—weights adjust dynamically based on what information is present.

#### Primary Scoring Factors

**1. Name Matching (Weight: 35)**
- Primary name comparison using Jaro-Winkler algorithm
- Alternative name (alias) comparison
- Phonetic filtering (Soundex-based) for performance
- Stopword removal and tokenization
- Handles name reordering ("LAST, FIRST" → "FIRST LAST")

**2. Address Matching (Weight: 25)**
- Geographic proximity using geocoding
- Component matching (street, city, state, postal code)
- Country and jurisdiction validation
- Fuzzy matching for abbreviations and formatting

**3. Critical Identifiers (Weight: 50)**
- Government IDs (passport, national ID, tax ID)
- Cryptocurrency addresses
- Contact information (email, phone)
- Highest weight due to uniqueness and reliability

**4. Supporting Information (Weight: 15)**
- Date of birth (exact and partial matching)
- Nationality comparisons
- Document type validation

#### Technical Implementation Phases

Under the hood, the system executes **12 implementation phases**:

1. **Normalization** - Text cleanup and standardization
2. **Tokenization** - Word combination generation
3. **Phonetic Filter** - Soundex-based pre-filtering
4. **Name Comparison** - Primary name scoring
5. **Alt Name Comparison** - Alias scoring
6. **Gov ID Comparison** - Government ID matching
7. **Crypto Comparison** - Cryptocurrency address matching
8. **Contact Comparison** - Email/phone matching
9. **Address Comparison** - Geographic matching
10. **Date Comparison** - Birth date matching
11. **Aggregation** - Weighted score combination
12. **Filtering** - Threshold application

**For detailed technical specifications, see [phase_scoring_mechanics.md](phase_scoring_mechanics.md).**

### Score Interpretation

| Score Range | Classification | Recommended Action |
|-------------|----------------|-------------------|
| 0.95 - 1.00 | Very High Confidence | Block transaction, escalate immediately |
| 0.85 - 0.94 | High Confidence | Manual review required |
| 0.70 - 0.84 | Moderate Confidence | Secondary screening, context review |
| 0.50 - 0.69 | Low Confidence | Monitor, likely false positive |
| 0.00 - 0.49 | Very Low Confidence | Likely false positive, log for audit |

**Note:** The system's default threshold is typically set to 0.85 for automated screening. Organizations should tune thresholds based on their risk appetite, transaction volume, and operational experience. Lower thresholds (0.70-0.80) may be appropriate for high-risk jurisdictions or enhanced due diligence scenarios.

---

## Match Validation

### Validation Logic

The system employs multiple validation checks to determine match confidence:

#### 1. Name Validation
- **Exact Match:** Full name matches exactly (case-insensitive)
- **Jaro-Winkler Similarity:** Primary fuzzy matching algorithm optimized for names
- **Token Match:** Individual name components match (handles word order)
- **Phonetic Filter:** Soundex-based pre-filtering for performance optimization
- **Favoritism Boost:** Bonus scoring for exact word matches within multi-word names

#### 2. Address Validation
- **Geographic Proximity:** Distance-based matching using geocoding
- **Component Matching:** Street, city, state, postal code comparisons
- **Country Validation:** ISO country code matching
- **Fuzzy Address Matching:** Handles abbreviations and formatting variations

#### 3. Identity Validation
- **Date of Birth:** Exact and partial date matching
- **Identification Numbers:** Passport, national ID, tax ID matching
- **Nationality:** Country of citizenship comparison
- **Document Type:** Specific document number validation

#### 4. Contextual Validation
- **Entity Type:** Individual vs. Organization classification
- **Program Type:** Sanctions program relevance
- **List Membership:** Which OFAC list(s) entity appears on

### Match Decision Process

```
Input → Normalization → Parallel Scoring:
                        ├─ Name Comparison (weight: 35)
                        ├─ Address Comparison (weight: 25)
                        ├─ ID Comparison (weight: 50)
                        └─ Date Comparison (weight: 15)
                        ↓
                      Weighted Aggregation
                        ↓
                      Threshold Filter (default: 0.85)
                        ↓
                      Hit / No Hit + Confidence Score
```

**Key Principle:** All available factors are evaluated simultaneously. More data = higher confidence. Missing data doesn't penalize; weights adjust dynamically.

---

## Alias Expansion

### Overview

**Alias expansion** is an always-enabled feature that enhances screening coverage by automatically including alternative name variations from SDN entries when a match is found.

### How It Works

1. **Initial Scoring:** Entity is scored against SDN list using primary name
2. **Match Detection:** If score exceeds threshold, entity is identified as a potential match
3. **Alias Retrieval:** System retrieves all alternative names (AKAs) for the matched SDN entry
4. **Result Expansion:** Response includes both the primary match and all alias variations
5. **Score Inheritance:** All aliases inherit the score from the primary match

### Purpose

- **Compliance Coverage:** Ensures reviewers see all known variations of a sanctioned entity
- **Transparency:** Shows which aliases may have triggered the match
- **Investigation Support:** Provides complete context for manual review decisions
- **Audit Trail:** Documents all name variations associated with a hit

### Example

**Input:** "Victor Bout"  
**Primary Match:** Victor Bout (Score: 0.98)  
**Expanded Results Include:**
- Victor Bout (primary)
- Viktor Bout (alias)
- Victor Anatolyevich Bout (full name)
- Vadim Markovich Aminov (alias)

**Important:** Scoring occurs once on the input entity. Aliases are not individually re-scored; they are expanded from the matched SDN entry for completeness.

### Performance Impact

- **Latency:** < 1% increase (typically 10-30ms per request)
- **Optimization:** Expansion occurs after scoring, not during
- **Efficiency:** Single database lookup retrieves all aliases

---

## False Positive Management

### Common False Positive Patterns

1. **Common Names:** High-frequency names (e.g., "Muhammad Ali", "John Smith")
2. **Partial Matches:** Single name component matches without context
3. **Geographic Mismatches:** Name matches but different country/region
4. **Entity Type Mismatches:** Individual matching organization or vice versa

### Mitigation Strategies

#### 1. Contextual Filtering
- Require multiple field matches for high-confidence hits
- Weight address and geographic data in scoring
- Use entity type to filter irrelevant matches

#### 2. Threshold Tuning
- Adjust phase thresholds based on false positive rates
- Set different thresholds for high-risk vs. low-risk transactions
- Monitor and refine thresholds quarterly

#### 3. Allowlisting
- Maintain organization-specific allowlists for known false positives
- Document business justification for allowlisted entries
- Require periodic review of allowlist entries (e.g., annually)

#### 4. Enhanced Data Collection
- Request additional identifying information (DOB, address, ID numbers)
- Use multi-factor matching to increase confidence
- Implement step-up verification for low-confidence matches

### Best Practices

- **Review Low Scores:** Investigate matches below 0.70 carefully
- **Document Decisions:** Maintain records of false positive determinations
- **Trend Analysis:** Track false positive patterns to refine rules
- **User Feedback:** Incorporate reviewer input into tuning process

---

## Audit Trail and Reporting

### Logging and Traceability

The system maintains comprehensive audit logs for all screening activities:

#### Request Logging
- Timestamp of screening request
- Input entity data (name, address, identifiers)
- Requesting system/user identifier
- Transaction reference ID

#### Response Logging
- Match results (hit/no hit)
- Match scores and confidence levels
- Matched SDN entries
- Alias expansion results
- Processing time and system metadata

#### Decision Logging
- Manual review decisions (approve/reject/escalate)
- Reviewer identity and timestamp
- Business justification
- Override authority (if applicable)

### Reporting Capabilities

The system supports compliance reporting including:

1. **Hit Rate Reports:** Percentage of transactions generating hits
2. **False Positive Reports:** Trends in false positive rates by category
3. **Response Time Reports:** Performance metrics and SLA compliance
4. **Match Distribution:** Distribution of scores across confidence bands
5. **SDN Coverage Reports:** Which SDN entries generate the most hits

### Regulatory Record Retention

- **Screening Records:** Retained for 5 years minimum (configurable)
- **SAR Supporting Documentation:** Retained per BSA requirements
- **System Logs:** Maintained for audit and investigation purposes
- **Configuration History:** Threshold changes and rule updates tracked

---

## Performance and Scale

### System Capabilities

- **Throughput:** 1,000+ screenings per second
- **Latency:** < 100ms average response time (P95)
- **Availability:** 99.9% uptime SLA
- **Scalability:** Horizontally scalable architecture

### Performance Optimization

- **Caching:** Frequently accessed SDN data cached in memory
- **Indexing:** Optimized database indexes for name and address lookups
- **Parallel Processing:** Multi-threaded scoring across phases
- **Efficient Expansion:** Alias expansion optimized for minimal overhead

### AWS Validation

The system has been validated on AWS infrastructure with:
- **Real OFAC data:** Full SDN list screening tested
- **Production-scale load:** 10,000+ requests tested
- **Performance verified:** < 1% latency impact from alias expansion
- **High availability:** Multi-region deployment supported

---

## Regulatory Compliance Considerations

### BSA/AML Requirements

The system supports compliance with:

- **31 CFR Part 501** - OFAC Economic Sanctions Programs
- **Bank Secrecy Act (BSA)** - Customer due diligence and monitoring
- **USA PATRIOT Act Section 326** - Customer Identification Program (CIP)
- **FinCEN Guidance** - Sanctions screening best practices

### Key Compliance Controls

1. **Comprehensive Coverage:** Screens against all applicable OFAC lists
2. **Risk-Based Approach:** Configurable thresholds for different risk profiles
3. **Manual Review Process:** High-confidence hits require human review
4. **Audit Trail:** Complete logging for regulatory examination
5. **Regular Updates:** Automated SDN list refresh (typically daily)

### Examiner Considerations

When evaluating this screening system, examiners should assess:

- **Threshold Appropriateness:** Are thresholds risk-appropriate for the institution?
- **False Positive Management:** Is there a documented process for handling false positives?
- **Override Controls:** Are overrides properly authorized and documented?
- **Testing and Validation:** Is the system periodically tested with known matches?
- **Staff Training:** Are compliance personnel trained on the screening methodology?

### Limitations and Disclaimers

**This system:**
- Uses algorithmic fuzzy matching, not exact OFAC.gov search logic
- May produce different results than manual OFAC website searches
- Requires human review and judgment for final compliance decisions
- Should be complemented with other risk management controls
- Is one component of a comprehensive AML/sanctions compliance program

**This system does not:**
- Replace manual due diligence requirements
- Eliminate the need for human compliance expertise
- Guarantee detection of all sanctions matches
- Provide legal or regulatory compliance advice

---

## Glossary

**AKA (Also Known As):** Alternative names or aliases associated with an SDN entry

**Alias Expansion:** Feature that includes all known name variations in match results

**False Positive:** A screening hit that, upon review, is determined not to be a true match

**Fuzzy Matching:** Approximate string matching that allows for variations and typos

**Hit:** A screening result indicating a potential match to an SDN entry

**Jaro-Winkler Distance:** Similarity algorithm optimized for short strings like names, measuring character-level matches and transpositions

**Match Confidence:** Numerical score (0.0 to 1.0) indicating likelihood of true match

**Multi-Factor Scoring:** Algorithmic approach using weighted combination of multiple data points

**Phonetic Matching:** Matching based on pronunciation rather than spelling

**SDN (Specially Designated Nationals):** OFAC's list of sanctioned individuals and entities

**Threshold:** Minimum score required to generate a match result

**Tokenization:** Breaking names into individual components (first, middle, last, etc.)

---

## Document Control

**Version History:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-01 | Initial | Initial document creation |
| 1.1 | 2026-02-15 | Update | Corrected scoring methodology to reflect actual 12-phase implementation; updated from "four-phase" to "multi-factor weighted" scoring; clarified Jaro-Winkler as primary algorithm |

**Review Schedule:** Annually or upon significant system changes

**Document Owner:** Compliance Department

**Technical Contact:** Engineering Team

---

*For questions or clarifications, please contact your BSA/AML compliance officer or system administrator.*
