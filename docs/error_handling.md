# Error Handling - Global Exception Handling

## Summary
Consistent error response format across all API endpoints using GlobalExceptionHandler (@ControllerAdvice). Returns structured JSON with error details, HTTP status, request path, and correlation ID.

## Scope
- GlobalExceptionHandler.java - Catches all exceptions, returns ErrorResponse
- ErrorResponse.java - Standard error DTO with 6 fields
- Request ID propagation via MDC for log correlation
- Out of scope: Custom error pages, i18n error messages

## Design notes
**Key classes:**
- src/main/java/io/moov/watchman/api/GlobalExceptionHandler.java
- src/main/java/io/moov/watchman/api/ErrorResponse.java

**Error response format:**
```json
{
  "error": "Bad Request",
  "message": "Missing required parameter: name",
  "status": 400,
  "path": "/v2/search",
  "requestId": "abc-123",
  "timestamp": "2026-01-14T12:00:00Z"
}
```

**Exception mappings:**
- IllegalArgumentException → 400 (bad input)
- MissingServletRequestParameterException → 400 (missing param)
- MethodArgumentTypeMismatchException → 400 (wrong type)
- HttpMediaTypeNotSupportedException → 415 (wrong Content-Type)
- Exception (catch-all) → 500 (internal error)

**Request ID tracking:**
- Extracted from X-Request-ID header or query param requestID
- Stored in MDC (SLF4J Mapped Diagnostic Context)
- Included in all log statements and error responses
- Enables log correlation across services

## How to validate
**Test 1:** Verify error format
```bash
curl "http://localhost:8080/v2/search"
# Verify: 400 response with error/message/status/path/requestId/timestamp fields
```

**Test 2:** Request ID propagation
```bash
curl -H "X-Request-ID: test-123" "http://localhost:8080/v2/search?name=Test"
# Check logs: grep "test-123" logs/application.log
# Verify: Request ID appears in all log statements
```

**Test 3:** Exception handling tests
```bash
./mvnw test -Dtest=GlobalExceptionHandlerTest
# Verify: 12 tests pass for different exception types
```

**Test 4:** Unsupported media type
```bash
curl -X POST -H "Content-Type: text/plain" "http://localhost:8080/v2/search/batch" -d "invalid"
# Verify: 415 error with "Unsupported Media Type" message
```

## Assumptions and open questions
- Assumes SLF4J/Logback for logging infrastructure
- Request ID max length: 64 characters (truncated if longer)
- Unknown: Need internationalized error messages (i18n)?
- Unknown: Should we add error codes (e.g., WATCHMAN-ERR-001)?
