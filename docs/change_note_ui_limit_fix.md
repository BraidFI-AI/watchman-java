## Summary

Fixed BSA observation retest issues for rows 6, 21, 22, and 52 where consultant reported entities "not listed in Watchman." Root cause: UI hardcoded `limit=5`, hiding entities beyond position 5. All entities exist in OFAC data and appear in search results when limit increased.

## Scope

- [admin.html](src/main/resources/static/admin.html#L748-L752): Added configurable limit input field (default 50)
- [admin.html](src/main/resources/static/admin.html#L1664): Updated API call to use configurable limit
- [MissingEntityVerificationTest.java](src/test/java/io/moov/watchman/observations/MissingEntityVerificationTest.java): TDD RED phase verification tests

## Design notes

**UI Change**:
- Added `<input type="number" id="testLimit" value="50" min="1" max="100">` after entity type field
- Updated `runTestSearch()` to read `limit` value: `const limit = document.getElementById('testLimit').value || 50;`
- Changed API URL from `&limit=5` to `&limit=${limit}`

**Test Results** (MissingEntityVerificationTest.java):
- Row 6 (CIMEX): IDs 8125, 576, 30630 all FOUND in data and results (positions 6-8)
- Row 21 (AL QA'IDA): IDs 6366, 8759, 27318 all FOUND in data and results (positions 6, 8, 9)
- Row 22 (TALIBAN): ID 12206 FOUND in data and results (position 6)
- Row 52 (OTKRITIE): IDs 34497, 34509, 34499 all FOUND in data and results (positions 6-8)

**Backend unchanged**: [SearchController.java](src/main/java/io/moov/watchman/api/SearchController.java#L71) and [SearchServiceImpl.java](src/main/java/io/moov/watchman/search/SearchServiceImpl.java#L176) already support dynamic limit parameter.

## How to validate

1. Start application: `./mvnw spring-boot:run`
2. Navigate to admin UI test search tab
3. Enter "CIMEX" with limit=50, threshold=0.70
4. Verify all 7 CIMEX entities appear (including IDs 8125, 576, 30630)
5. Run tests: `./mvnw test -Dtest=MissingEntityVerificationTest`

## Assumptions and open questions

**Assumption**: Consultant tested with default UI (limit=5), not direct API calls.

**Question**: Row 35 (OFFICE 39) marked Pass after consultant clarified token-based OR logic not required. Close observation or investigate specific OFAC IDs 30184, 34657, 40776?
