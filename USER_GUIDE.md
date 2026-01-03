# Watchman Java User Guide

A practical guide for compliance teams and business users implementing sanctions screening.

---

## Table of Contents

1. [What is Sanctions Screening?](#what-is-sanctions-screening)
2. [Why It Matters](#why-it-matters)
3. [Getting Started](#getting-started)
4. [Understanding Search Results](#understanding-search-results)
5. [Screening Workflows](#screening-workflows)
6. [Best Practices](#best-practices)
7. [Compliance Considerations](#compliance-considerations)
8. [FAQs](#faqs)

---

## What is Sanctions Screening?

Sanctions screening is the process of checking individuals, companies, and other entities against government-maintained watchlists to identify potential matches with sanctioned parties.

### Watchlists Covered

Watchman Java screens against these official government sanctions lists:

| List | Authority | Coverage |
|------|-----------|----------|
| **OFAC SDN** | US Treasury | Specially Designated Nationals - individuals and entities with blocked assets |
| **US CSL** | US Commerce/State | Consolidated Screening List - export control and trade restrictions |
| **EU CSL** | European Union | EU financial sanctions and asset freezes |
| **UK Sanctions** | UK Government | UK financial sanctions list |

### Entity Types

The system identifies and screens these entity types:

| Type | Description | Examples |
|------|-------------|----------|
| **Person** | Individuals | Government officials, criminals, terrorists |
| **Business** | Companies and corporations | Shell companies, sanctioned banks |
| **Organization** | Non-commercial entities | Political parties, terrorist groups |
| **Vessel** | Ships and maritime vessels | Vessels involved in sanctions evasion |
| **Aircraft** | Planes and helicopters | Aircraft used for illicit purposes |

---

## Why It Matters

### Regulatory Requirements

Financial institutions, exporters, and many businesses are legally required to screen:

- **Banks & Financial Services**: Must screen all customers, transactions, and counterparties
- **Exporters**: Must verify end-users against denied party lists
- **Insurance Companies**: Must screen policyholders and claimants
- **Real Estate**: Must screen buyers in high-value transactions
- **Legal Services**: Must screen clients for AML compliance

### Consequences of Non-Compliance

| Risk | Potential Impact |
|------|------------------|
| **Civil Penalties** | Up to $330,000 per violation (OFAC) |
| **Criminal Penalties** | Up to $1M and 20 years imprisonment |
| **Reputational Damage** | Loss of customer trust, media exposure |
| **License Revocation** | Loss of ability to operate |
| **Transaction Blocking** | Frozen assets, failed payments |

---

## Getting Started

### Quick Start: Single Search

Search for a person or entity:

```
https://watchman-java.fly.dev/v2/search?name=John%20Smith&limit=10
```

**Parameters:**
- `name` - The name to search (required)
- `limit` - Maximum results (default: 10)
- `minMatch` - Minimum match score 0-100% (default: 85%)

### Quick Start: Batch Screening

Screen multiple entities at once (up to 1,000):

```bash
curl -X POST https://watchman-java.fly.dev/v2/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"id": "CUST-001", "name": "John Smith"},
      {"id": "CUST-002", "name": "Acme Corporation"}
    ],
    "minMatch": 0.85
  }'
```

---

## Understanding Search Results

### Match Scores

Every result includes a **match score** from 0% to 100%:

| Score Range | Interpretation | Recommended Action |
|-------------|----------------|-------------------|
| **95-100%** | Very High Match | Likely true positive - escalate immediately |
| **90-95%** | High Match | Probable match - requires review |
| **85-90%** | Moderate Match | Possible match - investigate further |
| **80-85%** | Low Match | Potential match - consider context |
| **Below 80%** | Weak Match | Likely false positive - review if risk warrants |

### What the Score Means

The score indicates how similar the searched name is to names on the watchlist:

- **Exact match** (100%): "JOHN SMITH" matches "JOHN SMITH"
- **Near match** (95%): "JOHN SMITH" matches "JON SMITH" (typo)
- **Partial match** (85%): "JOHN SMITH" matches "JOHN R SMITH" (middle name)
- **Fuzzy match** (80%): "JOHN SMITH" matches "JOHAN SMYTH" (variations)

### Sample Search Result

```json
{
  "entities": [
    {
      "id": "22790",
      "name": "MADURO MOROS, Nicolas",
      "type": "PERSON",
      "source": "US_OFAC",
      "score": 0.95,
      "altNames": ["MADURO, Nicolas"],
      "programs": ["VENEZUELA"],
      "remarks": "DOB 23 Nov 1962; President of Venezuela"
    }
  ],
  "totalResults": 1
}
```

**Key Fields:**

| Field | Description |
|-------|-------------|
| `name` | Primary name on the watchlist |
| `type` | Entity type (Person, Business, etc.) |
| `source` | Which watchlist (OFAC, EU, UK, etc.) |
| `score` | Match confidence (0.0 - 1.0) |
| `altNames` | Alternative names/aliases |
| `programs` | Sanctions programs (VENEZUELA, IRAN, etc.) |
| `remarks` | Additional identifying information |

---

## Screening Workflows

### Customer Onboarding

Screen all new customers before establishing a relationship:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Customer       │────▶│   Watchman      │────▶│   Review        │
│  Application    │     │   Screening     │     │   Results       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                        │
                        ┌───────────────────────────────┼───────────────────────────────┐
                        ▼                               ▼                               ▼
                ┌───────────────┐              ┌───────────────┐              ┌───────────────┐
                │   No Match    │              │  Potential    │              │  Confirmed    │
                │   (Clear)     │              │  Match        │              │  Match        │
                └───────────────┘              └───────────────┘              └───────────────┘
                        │                               │                               │
                        ▼                               ▼                               ▼
                ┌───────────────┐              ┌───────────────┐              ┌───────────────┐
                │   Approve     │              │  Enhanced     │              │   Reject &    │
                │   Customer    │              │  Due Diligence│              │   Report      │
                └───────────────┘              └───────────────┘              └───────────────┘
```

### Transaction Screening

Screen parties to a transaction before processing:

1. **Pre-Transaction**: Screen sender, receiver, and any intermediaries
2. **Threshold-Based**: Screen all transactions above a certain value
3. **Risk-Based**: Enhanced screening for high-risk corridors or entity types

### Periodic Rescreening

Regularly rescreen your existing customer base:

| Customer Risk Level | Rescreening Frequency |
|--------------------|----------------------|
| High Risk | Monthly |
| Medium Risk | Quarterly |
| Low Risk | Annually |

### Batch Processing for Rescreening

Use batch screening for efficient periodic reviews:

```json
{
  "items": [
    {"id": "CUST-001", "name": "Customer One"},
    {"id": "CUST-002", "name": "Customer Two"},
    // ... up to 1,000 customers
  ],
  "minMatch": 0.85
}
```

**Response includes statistics:**
```json
{
  "statistics": {
    "totalItems": 1000,
    "itemsWithMatches": 3,
    "itemsWithoutMatches": 997,
    "processingTimeMs": 2500
  }
}
```

---

## Best Practices

### 1. Set Appropriate Thresholds

| Use Case | Recommended minMatch | Rationale |
|----------|---------------------|-----------|
| High-value transactions | 0.75 | Cast wider net for more review |
| Standard onboarding | 0.85 | Balance between catches and false positives |
| Low-risk, high-volume | 0.90 | Reduce false positives |
| Name-only screening | 0.80 | Account for variations |

### 2. Use Additional Identifiers

When available, verify matches using:
- **Date of Birth** - Compare against DOB in remarks
- **Address/Country** - Match against known addresses
- **ID Numbers** - Passport, national ID, tax ID
- **Aliases** - Check all known names

### 3. Document Your Process

Maintain records of:
- Screening date and time
- Search parameters used
- Results received
- Review decision and rationale
- Reviewer name and date

### 4. Handle False Positives

Common causes of false positives:
- **Common names** - "John Smith", "Mohammed Ali"
- **Partial matches** - Business names containing common words
- **Transliteration** - Names translated from non-Latin scripts

**Resolution steps:**
1. Compare additional identifiers (DOB, address, ID)
2. Review the full sanctions entry remarks
3. Document the determination
4. Add to an internal whitelist if appropriate

### 5. Handle True Positives

When a match is confirmed:
1. **Stop** - Do not proceed with the transaction/relationship
2. **Report** - File required regulatory reports (SAR, blocking report)
3. **Escalate** - Notify compliance officer and management
4. **Document** - Maintain complete records
5. **Block** - Freeze assets if required by sanctions

---

## Compliance Considerations

### OFAC Reporting Requirements

If you identify a true match against OFAC lists:

1. **Block the transaction/property** within 10 business days
2. **File a Blocking Report** with OFAC within 10 business days
3. **File annual reports** for blocked property
4. **Maintain records** for at least 5 years

**OFAC Hotline**: 1-800-540-6322

### Risk-Based Approach

Regulators expect a risk-based screening program:

| Factor | Lower Risk | Higher Risk |
|--------|------------|-------------|
| Customer Type | Established business | New/unknown entity |
| Geography | Low-risk countries | High-risk jurisdictions |
| Product/Service | Standard offerings | Complex structures |
| Transaction Size | Small/routine | Large/unusual |

### Audit Trail

Maintain complete records for regulatory examination:

- Search queries performed
- Results returned
- Decisions made
- Supporting documentation
- Reviewer identification
- Timestamps

---

## FAQs

### How often is the data updated?

Sanctions data is automatically refreshed **daily** from official government sources. You can also trigger a manual refresh if needed.

### What if I get too many false positives?

- Increase the `minMatch` threshold (e.g., from 0.85 to 0.90)
- Use entity type filters to narrow results
- Implement secondary verification using DOB, address, or ID numbers

### What if I miss a true positive?

- Decrease the `minMatch` threshold for higher-risk scenarios
- Screen against all lists, not just one
- Implement periodic rescreening of existing customers

### Can I screen non-English names?

Yes. The system handles:
- Transliterated names (Arabic, Chinese, Cyrillic to Latin)
- Name variations and aliases
- Common misspellings

### How do I handle partial name matches?

The fuzzy matching algorithm accounts for:
- Missing middle names
- Reversed name order (First Last vs Last, First)
- Nicknames vs formal names
- Minor spelling variations

### What's the difference between OFAC SDN and US CSL?

| List | Focus | Consequence |
|------|-------|-------------|
| **OFAC SDN** | Asset blocking | Cannot conduct ANY business |
| **US CSL** | Export control | Cannot export controlled items |

Both require screening, but the compliance response differs.

### Is 85% match threshold enough?

85% is a common industry standard, but consider:
- **Higher risk** = Lower threshold (more manual review)
- **Lower risk** = Higher threshold (fewer false positives)

Always calibrate based on your risk appetite and regulatory requirements.

---

## Support & Resources

### Official Government Resources

- [OFAC Sanctions Programs](https://ofac.treasury.gov/sanctions-programs-and-country-information)
- [OFAC SDN Search](https://sanctionssearch.ofac.treas.gov/)
- [US Consolidated Screening List](https://www.trade.gov/consolidated-screening-list)
- [EU Sanctions Map](https://sanctionsmap.eu/)
- [UK Sanctions List](https://www.gov.uk/government/publications/financial-sanctions-consolidated-list-of-targets)

### When to Contact OFAC

- Potential match requiring guidance
- Licensing questions
- Voluntary self-disclosure

**OFAC Hotline**: 1-800-540-6322  
**OFAC Licensing**: [https://ofac.treasury.gov/ofac-license-application-page](https://ofac.treasury.gov/ofac-license-application-page)

---

## Glossary

| Term | Definition |
|------|------------|
| **SDN** | Specially Designated National - person or entity whose assets are blocked |
| **OFAC** | Office of Foreign Assets Control - US sanctions administrator |
| **CSL** | Consolidated Screening List - combined US export control lists |
| **AML** | Anti-Money Laundering |
| **KYC** | Know Your Customer |
| **SAR** | Suspicious Activity Report |
| **EDD** | Enhanced Due Diligence |
| **False Positive** | Match result that is not actually a sanctioned party |
| **True Positive** | Match result that is confirmed as a sanctioned party |
| **Fuzzy Matching** | Algorithm that finds similar but not identical names |
