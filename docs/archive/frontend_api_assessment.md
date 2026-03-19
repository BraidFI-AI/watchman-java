# Frontend API Assessment for Customer-Facing Product

**Session Goal:** Review Postman APIs and ensure we have all endpoints required to build a customer-facing frontend.

**Product Decision:** Watchman-Java as **standalone screening engine only** (no case management). External systems (Braid, bank compliance systems) handle case workflow.

## Summary

Current API coverage provides **complete screening API surface** for external products. All screening operations ready. Case management intentionally excluded (external system responsibility).

## Scope

- Single/batch entity screening ✅
- Score trace reports ✅
- System health and data freshness ✅
- Configuration management (admin) ✅

## Available Endpoints (Ready for Frontend)

### Core Screening Operations ✅

**GET /v1/search**
- Single name screening
- Query params: `name`, `minMatch`, `limit`, `source`, `type`, `trace`
- Returns: entities list, uniqueEntities count, totalResults, requestID
- **Status:** Ready for frontend

**POST /v1/search/batch**
- Synchronous batch screening (up to 1000 items)
- Request: items array with requestId + name per item
- Returns: results per itemId with matches, statistics
- **Status:** Ready for frontend

**POST /v1/search/batch/async**
- Asynchronous batch processing
- Returns: jobId for status polling
- **Status:** Ready for frontend

**GET /v1/search/batch/config**
- Batch configuration limits
- Returns: maxBatchSize, defaultMinMatch, defaultLimit
- **Status:** Ready for frontend

### Score Transparency ✅

**GET /api/reports/{sessionId}**
- HTML score report (human-readable)
- Accessed via `trace=true` in search request
- **Status:** Ready for frontend display

**GET /api/reports/{sessionId}/summary**
- JSON summary (programmatic access)
- Phase contributions, timings, insights
- **Status:** Ready for frontend dashboards

### System Information ✅

**GET /health**
- Service health check
- Returns: status, entityCount
- **Status:** Ready for monitoring

**GET /v1/listinfo**
- Available sanctions lists
- Returns: list names, counts, lastUpdated
- **Status:** Ready for frontend

## Screening API Completeness ✅

### Core Screening Operations - COMPLETE

All screening operations have full API coverage:

1. **Single Entity Screening** ✅
   - `GET /v1/search?name=...&minMatch=...&source=...&type=...&trace=...`
   - Filtering by source list (OFAC, EU, UK, US CSL)
   - Filtering by entity type (PERSON, BUSINESS, VESSEL, AIRCRAFT)
   - Configurable match threshold
   - Trace mode for debugging

2. **Batch Screening** ✅
   - `POST /v1/search/batch` - Synchronous (up to 1000 items)
   - `POST /v1/search/batch/async` - Asynchronous for large batches
   - `GET /v1/search/batch/config` - Batch limits and defaults
   - Per-item filtering (source, type)
   - Statistics aggregation

3. **Score Transparency** ✅
   - `GET /api/reports/{sessionId}` - HTML report for humans
   - `GET /api/reports/{sessionId}/summary` - JSON for automation
   - Phase-level breakdown (name, address, DOB, ID matching)
   - Alias matching attribution
   - Processing timeline

4. **System Information** ✅
   - `GET /health` - Service health and entity count
   - `GET /v1/listinfo` - Available lists with counts and timestamps
   - Data refresh status

5. **Configuration Management** ✅
   - `GET /api/admin/config` - View all 26 parameters
   - `PUT /api/admin/config/similarity` - Algorithm tuning (10 params)
   - `PUT /api/admin/config/weights` - Business rules (13 params)
   - `PUT /api/admin/config/auto-clearance` - Clearance thresholds (3 params)
   - `POST /api/admin/config/reset` - Reset to defaults

### Data Management ✅

**Manual Data Refresh:**
- `POST /v2/download` - Trigger manual refresh
- `GET /v2/download/status` - Check refresh status

**Note:** Auto-refresh scheduled internally, manual refresh for urgent updates.

### Intentionally External (Not in Scope)

These features belong in the integrating system (Braid, bank compliance platform):

1. **Case Management** ❌ External System Responsibility
   - Review status (pending/cleared/escalated)
   - Disposition tracking
   - Reviewer assignment
   - Whitelist management
   - SAR integration

2. **Search History** ❌ External System Responsibility
   - Audit trail storage
   - Search replay
   - Historical analytics

3. **User Management** ❌ External System Responsibility
   - User preferences
   - Role-based access control
   - Team/organization structure

4. **Export/Reporting** ❌ External System Responsibility
   - PDF/CSV generation
   - Custom report templates
   - Scheduled reports

**Rationale:** Screening engine provides real-time API responses. External systems persist data, manage workflow, and generate reports based on business needs.

## Admin Features (Already Available, Not Customer-Facing)

These endpoints exist but should be restricted to admin users only:

- **GET /api/admin/config** - View all 26 configuration parameters
- **PUT /api/admin/config/similarity** - Update algorithm parameters
- **PUT /api/admin/config/weights** - Update scoring weights
- **PUT /api/admin/config/auto-clearance** - Update auto-clearance thresholds
- **POST /api/admin/config/reset** - Reset to defaults
- **POST /v2/download** - Manual data refresh
- **GET /v2/download/status** - Download status

**Recommendation:** Auth layer should restrict these to admin role only.

## Design Notes

### Minimal Viable Product (MVP) Approach

**Phase 1: Core Screening (Implemented ✅)**
- Single/batch search
- Score reports
- Health checks

**Phase 2: User Workflow (Missing ❌)**
- Case management (pending/cleared/escalated)
- Search history for audit trail
- User preferences

**Phase 3: Compliance Features (Missing ❌)**
- Export to CSV/PDF
- Audit trail queries
- Watchlist toggle per user

### External Auth Integration

User auth handled externally means:
- Backend receives authenticated `userId` in request headers/token
- No user registration/login endpoints needed
- Backend validates userId exists but doesn't manage auth
- All endpoints accept optional `userId` param or header for filtering

### Current Postman Collection Coverage

Postman collection documents ~95% of **functional screening endpoints**:
- ✅ Standalone Screening Engine Architecture

**Watchman-Java Responsibility:**
- ✅ Real-time entity screening (single/batch)
- ✅ Score transparency (trace reports, breakdowns)
- ✅ Data refresh from official sources
- ✅ Algorithm configuration and tuning
Complete Screening API Surface

**1. Single Entity Screening:**
```bash
# Basic search
curl "http://localhost:8084/v1/search?name=Nicolas%20Maduro"

# With filters
curl "http://localhost:8084/v1/search?name=Putin&source=US_OFAC&type=PERSON&minMatch=0.9"

# With trace for debugging
curl "http://localhost:8084/v1/search?name=El%20Chapo&trace=true&limit=5"
```

**2. Batch Screening:**
```bash
# Sync batch (up to 1000 items)
curl -X POST http://localhost:8084/v1/search/batch \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"requestId":"tx-001","name":"John Smith"},
      {"requestId":"tx-002","name":"Acme Corp","entityType":"BUSINESS"}
    ],
    "minMatch": 0.85,
    "limit": 10
  }'

# Async batch (large batches)
curl -X POST http://localhost:8084/v1/search/batch/async \
  -H "Content-Type: application/json" \
  -d '{"items":[...]}'

# Check batch config
curl "http://localhost:8084/v1/search/batch/config"
```

**3. Score Transparency:**
```bash
# Get HTML report (sessionId from trace=true search)
curl "http://localhost:8084/api/reports/{sessionId}"

# Get JSON summary for automation
curl "http://localhost:8084/api/reports/{sessionId}/summary"
```

**4. System Health:**
```bash
# Health check
curl "http://localhost:8084/health"

# List info (available sanctions lists)
curl "http://localhost:8084/v1/listinfo"
```

**5. Configuration Management:**
```bash
# View all config (26 parameters)
curl "http://localhost:8084/api/admin/config"

# Update similarity config
curl -X PUT http://localhost:8084/api/admin/config/similarity \
  -H "Content-Type: application/json" \
  -d '{
    "jaroWinklerBoostThreshold": 0.8,
    "jaroWinklerPrefixSize": 4,
    "lengthDifferencePenaltyWeight": 0.3,
    ...
  }'

# Reset to defaults
curl -X POST http://localhost:8084/api/admin/config/reset
```

**6. Data Refresh:**
```bash
# Trigger manual refresh
curl -X POST http://localhost:8084/v2/download

# Check status
curl "http://localhost:8084/v2/download/status"
```

### Validation Checklist ✅

- ✅ Can screen single entities with filtering
- ✅ Can screen batches (sync and async)
- ✅ Can get detailed score breakdowns
- ✅ Can tune algorithm parameters at runtime
- ✅ Can refresh sanctions data manually
- ✅ Can monitor system health and data freshness
- ✅ **All screening operations covered - ready for external products**
External System → POST /v1/search/batch → Watchman-Java
                ← Response with matches    ←
                → Store results in DB
                → Create cases for matches
                → Assign to compliance officers
```

**Async Screening Workflow:**
```
External System → POST /v1/search/batch/async → Watchman-Java
                ← jobId                         ←
                → Poll for completion
                ← Results when ready            ←
                → Process and store
```

**Trace Analysis:**
```
External System → GET /v1/search?trace=true → Watchman-Java
                ← sessionId + results       ←
                → GET /api/reports/{sessionId} → 
                ← HTML report                  ←
                → Store/display for compliance review
```
- ❌ Cannot export results to CSV/PDF
- ❌ Cannot save user preferences

## Product Positioning Decision: Case Management

### Current State: Braid Already Has Case Management

Evidence from [braid-integration/OfacController.java](../braid-integration/OfacController.java):
- **Status tracking**: OFACEnums.Status (BLOCKED, APPROVED, PENDING)
- **Case updates**: `PUT /OFAC/{id}` - updateOFACCheckStatus with reviewer, status, notes
- **Alert workflow**: Integration with alertActivityService (DECLINE/APPROVE)
- **Whitelist management**: POST /OFAC/whitelist to create whitelist entries
- **Case retrieval**: `GET /OFAC` with status filtering, date ranges, pagination
- **Retry logic**: POST /OFAC/{id}/retry for re-screening

**Implication:** Braid customers already have full case management via Braid's OFAC service.

### Two Product Architecture Options

#### Option A: Watchman-Java as Pure Screening Engine (Recommended)

**Scope:** Screening API only, no case management

**Architecture:**
```
┌─────────────────┐
│  Braid Core     │ ← Case management, alerts, workflow
└────────┬────────┘
         │ HTTP calls
         ↓
┌─────────────────┐
│ Watchman-Java   │ ← Screening only (GET /v1/search)
└─────────────────┘
         ↓
    OFAC/EU/UK data
```

**Pros:**
- ✅ Single responsibility: screening accuracy only
- ✅ Easy integration with any system (Braid, other cores, standalone apps)
- ✅ No duplicate case management logic
- ✅ Smaller codebase, faster iteration on screening features
- ✅ Banks using Braid → use Braid's case management
- ✅ Banks without Braid → build their own or integrate with existing compliance systems

**Cons:**
- ❌ Not a standalone compliance product
- ❌ Banks without case management need to build it themselves
- ❌ Cannot sell to banks as complete solution

**Implementation effort:** None (current state)

---

#### Option B: Watchman-Java as Complete Compliance Product

**Scope:** Screening + case management + audit trail + reporting

**Architecture:**
```
┌────────────────────────────┐
│  Watchman-Java             │
│  ┌──────────────────────┐  │
│  │ Case Management      │  │ ← NEW: Build this
│  │ - Status tracking    │  │
│  │ - Review workflow    │  │
│  │ - Whitelist          │  │
│  │ - Audit trail        │  │
│  └──────────────────────┘  │
│  ┌──────────────────────┐  │
│  │ Screening Engine     │  │ ← EXISTS: Current code
│  └──────────────────────┘  │
└────────────────────────────┘
```

**Pros:**
- ✅ Standalone product - sell to any bank
- ✅ Complete compliance solution
- ✅ Revenue opportunity from banks without Braid
- ✅ Own the entire customer workflow

**Cons:**
- ❌ Duplicates Braid's OFAC case management (maintenance burden)
- ❌ Two code paths to maintain (Braid integration vs. standalone)
- ❌ Competes with Braid's positioning as full-service core
- ❌ Case management is complex: SAR filing, escalation rules, compliance officer assignment, audit requirements
- ❌ Need persistence layer (PostgreSQL/RDS), not just in-memory screening

**Implementation effort:** 3-4 weeks (6 new controllers, DB schema, migrations, tests)

---

### Hybrid Option C: Screening Engine + Optional Case Management Module

**Architecture:** Modular design - case management is opt-in feature

```
Watchman-Java Core (always included):
  - GET /v1/search
  - POST /v1/search/batch
  - Score reports

Optional Module (enable via config):
### 1. API Documentation Enhancement
- [ ] Generate OpenAPI 3.0 spec from existing controllers
- [ ] Add example responses for all endpoints (200, 400, 404, 500)
- [ ] Document CORS policy (currently `@CrossOrigin(origins = "*")`)
- [ ] Add API versioning strategy (currently /v1, /v2 mixed)

### 2. Postman Collection Updates
- [ ] Add admin config examples (similarity, weights, auto-clearance)
- [ ] Add error scenario examples (validation failures, not found)
- [ ] Add performance testing scenarios (batch with 1000 items)
- [ ] Document trace workflow (search with trace → get report → get summary)

### 3. External Product Integration Guide
- [ ] Create integration quickstart (5-minute "Hello World" screening)
- [ ] Document backwards compatibility policy (major/minor/patch versioning)
- [ ] Provide SDKs or code examples (JavaScript/TypeScript, Python, Java)
- [ ] Rate limiting and authentication recommendations

### 4. Lovable Frontend Prototype
- [ ] Build reference UI using screening APIs only
- [ ] Demonstrate real-time search with score visualization
- [ ] Show batch upload workflow (CSV → screen → display)
- [ ] Mock external case management integration (save to localStorage as POC)

### 5. Production Readiness
- [ ] Add API authentication (API keys, JWT validation)
- [ ] Implement rate limiting (per-API-key quotas)
- [ ] Add request/response logging for audit trail
- [ ] Monitoring: Prometheus metrics endpoint
- [ ] Document deployment guide (Docker, AWS ECS, Kubernetes)ores (each has their own compliance workflow)
- Prefer to avoid building/maintaining case management

**Choose Option B (Complete Product) if:**
- Want to sell directly to banks without Braid
- See revenue opportunity in compliance SaaS market
- Willing to compete with existing compliance vendors (Verafin, NICE Actimize)
- Have resources to build full compliance workflow

**Choose Option C (Modular) if:**
- Want both Braid integration AND standalone sales
- Can handle configuration complexity
- Have clear module boundaries from day one

---

## Assumptions and Open Questions

### Assumptions
1. **Auth handled externally** - Backend receives validated userId but doesn't manage auth lifecycle
2. **Lovable editor** - Frontend builder expects REST APIs, not GraphQL
3. **MVP scope** - Start with core screening, add workflow features incrementally

### Open Questions (Blocking Decision)

**Q1: Who is the primary customer?**
- A) Braid customers screening through Braid → Choose Option A
- B) Banks without Braid buying standalone compliance tool → Choose Option B
- C) Both equally important → Choose Option C

**Q2: What compliance features exist in target banks?**
- Do they have existing case management systems we should integrate with?
- Or do they need us to provide the entire workflow?

**Q3: Revenue model?**
- Sell screening engine as component (per-API-call pricing)?
- Sell complete compliance SaaS (per-user subscription)?
- Both?

**Q4: Maintenance burden tolerance?**
- Core team size and capacity for building compliance workflow features?
- Preference to focus on screening accuracy vs. workflow features?

**Q5: Braid's roadmap?**
- Is Braid planning to standardize on watchman-java for screening?
- Would Braid's OFAC case management migrate to watchman-java eventually?
- Or will Braid keep its own case management indefinitely?

## Next Steps

1. **Implement missing endpoints** (if required for MVP):
   - Case management (POST/GET/PUT /v1/cases)
   - Search history (POST/GET /v1/search-history)
   - User preferences (GET/PUT /v1/users/{userId}/preferences)
   - Export functionality (GET /v1/search/{searchId}/export)

2. **Verify Postman collection completeness**:
   - Confirm POST /api/admin/config/reset exists
   - Add examples for missing endpoints once implemented

3. **Document external auth integration**:
   - Header format for userId
   - JWT validation strategy
   - Error responses for unauthorized requests

4. **Create OpenAPI spec** for frontend code generation:
   - Export current API to openapi.yaml
   - Include all endpoints (core + missing)
   - Document required vs optional fields

5. **Build reference frontend** in Lovable editor:
   - Test core screening workflow
   - Identify additional API gaps during UI implementation
   - Iterate with backend changes as needed
