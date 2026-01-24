# Admin UI - MVP Implementation

## Summary
Simple web-based Admin UI for managing ScoreConfig (23 parameters), viewing ScoreTrace sessions, and testing search functionality. Built with REST API-first approach using vanilla HTML/JavaScript consuming Spring Boot endpoints.

## Scope
- REST API endpoints for config management (GET, PUT, POST)
- SPA frontend at /admin.html (no framework dependencies)
- View and edit SimilarityConfig (10 params) + WeightConfig (13 params)
- Reset to defaults functionality
- Test search with ScoreTrace integration
- **In scope**: Runtime config changes (in-memory, affects current session only)
- **Out of scope**: Persistence to application.yml, authentication/authorization, ScoreTrace list endpoint (placeholder UI ready), historical reporting

## Design notes

**REST API** (AdminConfigController.java):
- GET /api/admin/config - Returns all 23 config parameters
- PUT /api/admin/config/similarity - Update 10 similarity parameters
- PUT /api/admin/config/weights - Update 13 weight parameters
- POST /api/admin/config/reset - Reset all to application.yml defaults
- Validation: threshold ranges (0-1), no negative weights, prefix size >= 0
- Error handling: IllegalArgumentException → 400 Bad Request via GlobalExceptionHandler

**DTOs**:
- AdminConfigResponse - combines SimilarityConfigDTO + WeightConfigDTO
- Immutable records following existing pattern (ReportSummary, etc)

**Frontend** (/admin.html):
- Vanilla JavaScript (no build step required)
- Fetch API for REST calls
- Three tabs: ScoreConfig, ScoreTrace (placeholder), Test Search
- Gradient purple background matching existing ReportRenderer style
- Real-time config editing with save/reload/reset
- Test search integrates with existing /v1/search endpoint

**State management**:
- Changes apply immediately to running SimilarityConfig/WeightConfig beans
- Config is singleton @Configuration beans - changes affect all subsequent searches
- No persistence - restart reloads from application.yml
- @BeforeEach in tests resets config to defaults (prevents test pollution)

**TDD Approach**:
- RED: 7 failing tests in AdminConfigControllerTest
- GREEN: Minimal controller + DTO implementation
- All tests pass (verified via `mvn test -Dtest=AdminConfigControllerTest`)

## How to validate

**Start application**:
```bash
./mvnw spring-boot:run
```

**Open Admin UI**:
```
http://localhost:8084/admin.html
```

**Test REST API**:
```bash
# Get all config
curl http://localhost:8084/api/admin/config | jq '.'

# Update weights
curl -X PUT http://localhost:8084/api/admin/config/weights \
  -H "Content-Type: application/json" \
  -d '{"nameWeight":40.0,"addressWeight":30.0,"criticalIdWeight":50.0,"supportingInfoWeight":15.0,"minimumScore":0.75,"exactMatchThreshold":0.99,"nameComparisonEnabled":true,"altNameComparisonEnabled":true,"addressComparisonEnabled":true,"govIdComparisonEnabled":true,"cryptoComparisonEnabled":true,"contactComparisonEnabled":true,"dateComparisonEnabled":true}'

# Reset to defaults  
curl -X POST http://localhost:8084/api/admin/config/reset

# Verify change
curl http://localhost:8084/api/admin/config | jq '.weights.nameWeight'
```

**Run tests**:
```bash
./mvnw test -Dtest=AdminConfigControllerTest
```

**Test end-to-end**:
1. Open /admin.html in browser
2. Change nameWeight from 35 to 40
3. Click "Save Weights" - expect success message
4. Click "Reload" - verify nameWeight still 40
5. Go to Test Search tab
6. Enter "Nicolas Maduro", check "Enable ScoreTrace"
7. Click "Run Search" - should show results + trace link
8. Click trace link - opens HTML report in new tab
9. Return to Configuration tab
10. Click "Reset to Defaults" - confirm, reload - verify nameWeight back to 35

## Assumptions and open questions

**Assumptions**:
- Changes apply immediately but do not persist across restarts (acceptable for MVP)
- Config beans are singletons - concurrent updates may cause race conditions
- No authentication required for MVP (can add @PreAuthorize later)
- SPA approach preferred over Thymeleaf templates (follows modern pattern)
- Minimal UI styling acceptable (gradient background, responsive grid)

**Open questions**:
- Should config changes persist to application.yml? (requires file write + reload mechanism)
- Need A/B testing capability (save named configs, compare results)?
- Require audit trail for who changed what when?
- Should add optimistic locking for concurrent updates?
- Need validation for weight sum = 100? (currently no constraint)
- Should /api/admin/* endpoints require authentication? (decision deferred to implementer)
- Need ScoreTrace list endpoint? (UI has placeholder, backend not implemented)
- Should changes trigger notification/webhook? (e.g., alert ops team)

**Future enhancements** (not in MVP):
- ScoreTrace list API: GET /api/admin/traces with pagination
- Named config profiles: save/load/compare different tunings
- Config history/audit log
- Batch test runner: run search against saved test cases, compare before/after
- Export/import config as JSON/YAML
- Real-time metrics dashboard (searches/sec, avg match score, etc)
