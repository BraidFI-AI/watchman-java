# Error Handling - Exception Management

## Summary
Global exception handling with consistent JSON error responses, proper HTTP status codes, request correlation, and database timeout handling. Validated across 13 test scenarios including edge cases and failures.

## Scope
- [GlobalExceptionHandler.java](../src/main/java/io/moov/watchman/api/GlobalExceptionHandler.java) - 10 exception mappings
- [ErrorResponse.java](../src/main/java/io/moov/watchman/api/ErrorResponse.java) - Standard error DTO
- [RequestIdFilter.java](../src/main/java/io/moov/watchman/api/RequestIdFilter.java) - MDC correlation
- [BatchRequestValidator.java](../src/main/java/io/moov/watchman/api/BatchRequestValidator.java) - Request validation
- [EntityNotFoundException.java](../src/main/java/io/moov/watchman/api/EntityNotFoundException.java) - 404 custom exception
- [ServiceUnavailableException.java](../src/main/java/io/moov/watchman/api/ServiceUnavailableException.java) - 503 custom exception
- Out of scope: i18n error messages, custom error codes

## Design notes
**Exception mappings:**
- IllegalArgumentException → 400 (bad input, validation failures)
- MissingServletRequestParameterException → 400 (missing param)
- MethodArgumentTypeMismatchException → 400 (wrong type)
- MethodArgumentNotValidException → 400 (JSR-303 validation)
- HttpMessageNotReadableException → 400 (malformed JSON)
- HttpMediaTypeNotSupportedException → 415 (wrong Content-Type)
- HttpRequestMethodNotSupportedException → 405 (wrong HTTP method)
- NoHandlerFoundException → 404 (endpoint not found)
- EntityNotFoundException → 404 (resource not found)
- ServiceUnavailableException → 503 (service unavailable)
- SQLException → 503 (database timeout/errors)
- Exception (catch-all) → 500 (internal error)

**Error response format:**
```json
{
  "error": "Bad Request",
  "message": "Missing required parameter: name",
  "status": 400,
  "path": "/v1/search",
  "requestId": "abc-123",
  "timestamp": "2026-01-15T12:00:00Z"
}
```

**Request ID tracking:**
- Extracted from X-Request-ID header or generated (UUID prefix)
- Stored in MDC for all log statements
- Returned in response header and error body
- Enables log correlation across services

**Database timeout handling:**
- SQLException with "timeout" or "timed out" → 503 with user-friendly message
- Other SQL errors → 503 with generic message (no internal details leaked)

**Validation refactoring:**
- [BatchRequestValidator](../src/main/java/io/moov/watchman/api/BatchRequestValidator.java) validates batch size (max 1000) and required fields
- Throws IllegalArgumentException with specific error messages
- Injected into [BatchScreeningController](../src/main/java/io/moov/watchman/api/BatchScreeningController.java)

## How to validate
**Test 1:** Verify error format consistency
```bash
curl "http://localhost:8084/v1/search"
# Expect: 400 with error/message/status/path/requestId/timestamp fields
```

**Test 2:** Request ID propagation
```bash
curl -H "X-Request-ID: test-123" "http://localhost:8084/v1/search?name=Test"
# Check logs: grep "test-123" logs/application.log
# Verify: Request ID in all log statements and response header
```

**Test 3:** Batch validation errors
```bash
# Empty batch
curl -X POST -H "Content-Type: application/json" "http://localhost:8084/v1/search/batch" -d '{"items": []}'
# Expect: 400 with "at least one item" message

# Oversized batch (1001 items)
curl -X POST -H "Content-Type: application/json" "http://localhost:8084/v1/search/batch" -d '{"items": [/* 1001 items */]}'
# Expect: 400 with "exceeds maximum limit: 1001 items (max: 1000)" message
```

**Test 4:** Report not found
```bash
curl -H "Accept: application/json" "http://localhost:8084/api/reports/invalid-session"
# Expect: 404 with JSON error (not HTML)
```

**Test 5:** Run test suite
```bash
./mvnw test -Dtest="GlobalExceptionHandler*,BatchScreeningControllerTest,ReportControllerTest"
# Expect: 30 tests pass (8 original + 5 production + 12 batch + 5 report)
```

## Implementation Details

**Logging:** Uses SLF4J/Logback for log output

**Request ID:** Maximum 64 characters (truncated if longer)
