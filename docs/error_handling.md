# Error Handling & Logging Guide

This guide covers the error handling infrastructure and logging configuration for the Watchman Java API.

---

## Table of Contents

1. [Error Response Format](#error-response-format)
2. [Global Exception Handler](#global-exception-handler)
3. [Custom Exceptions](#custom-exceptions)
4. [Request ID Tracking](#request-id-tracking)
5. [Logging Configuration](#logging-configuration)
6. [Best Practices](#best-practices)

---

## Error Response Format

All API errors return a consistent JSON structure:

```json
{
  "error": "Bad Request",
  "message": "Missing required parameter: q",
  "status": 400,
  "path": "/v2/search",
  "requestId": "a1b2c3d4",
  "timestamp": "2026-01-03T12:34:56.789Z"
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `error` | string | HTTP status text (e.g., "Bad Request", "Not Found") |
| `message` | string | Human-readable error description |
| `status` | int | HTTP status code |
| `path` | string | Request URI that caused the error |
| `requestId` | string | Correlation ID for tracing |
| `timestamp` | ISO-8601 | When the error occurred |

### ErrorResponse DTO

Located at `io.moov.watchman.api.ErrorResponse`:

```java
public record ErrorResponse(
    String error,
    String message,
    int status,
    String path,
    String requestId,
    Instant timestamp
) {
    // Factory methods
    public static ErrorResponse badRequest(String message, String path, String requestId);
    public static ErrorResponse notFound(String message, String path, String requestId);
    public static ErrorResponse internalError(String message, String path, String requestId);
    public static ErrorResponse serviceUnavailable(String message, String path, String requestId);
}
```

---

## Global Exception Handler

The `GlobalExceptionHandler` class (`@ControllerAdvice`) catches exceptions across all controllers and converts them to standardized error responses.

### Handled Exceptions

| Exception | HTTP Status | When Thrown |
|-----------|-------------|-------------|
| `IllegalArgumentException` | 400 | Invalid input values |
| `MissingServletRequestParameterException` | 400 | Required query param missing |
| `MethodArgumentTypeMismatchException` | 400 | Wrong parameter type |
| `MethodArgumentNotValidException` | 400 | Bean validation failure |
| `HttpMessageNotReadableException` | 400 | Malformed JSON body |
| `HttpMediaTypeNotSupportedException` | 415 | Wrong Content-Type |
| `HttpRequestMethodNotSupportedException` | 405 | Wrong HTTP method |
| `NoHandlerFoundException` | 404 | Endpoint not found |
| `EntityNotFoundException` | 404 | Entity doesn't exist |
| `ServiceUnavailableException` | 503 | Dependency unavailable |
| `Exception` (catch-all) | 500 | Unexpected errors |

### Adding New Exception Handlers

To handle a new exception type:

```java
@ExceptionHandler(YourCustomException.class)
public ResponseEntity<ErrorResponse> handleYourException(
        YourCustomException ex, HttpServletRequest request) {
    logger.warn("Your exception occurred: {}", ex.getMessage());
    return ResponseEntity
            .status(HttpStatus.YOUR_STATUS)
            .body(ErrorResponse.of(
                "Your Error Type", 
                ex.getMessage(), 
                YOUR_STATUS_CODE, 
                request.getRequestURI(), 
                getRequestId()));
}
```

---

## Custom Exceptions

### EntityNotFoundException

Throw when a requested resource doesn't exist:

```java
// Usage in service layer
public Entity findById(String id) {
    return repository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Entity", id));
}

// Results in:
// { "error": "Not Found", "message": "Entity not found: 12345", "status": 404, ... }
```

### ServiceUnavailableException

Throw when an external dependency is unavailable:

```java
// Usage when calling external service
try {
    return externalService.call();
} catch (ConnectionException e) {
    throw new ServiceUnavailableException("External sanctions API", e);
}

// Results in:
// { "error": "Service Unavailable", "message": "Service unavailable: External sanctions API", "status": 503, ... }
```

### Creating New Custom Exceptions

```java
package io.moov.watchman.api;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}

// Then add handler in GlobalExceptionHandler:
@ExceptionHandler(RateLimitExceededException.class)
public ResponseEntity<ErrorResponse> handleRateLimit(
        RateLimitExceededException ex, HttpServletRequest request) {
    logger.warn("Rate limit exceeded: {}", ex.getMessage());
    return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.of("Too Many Requests", ex.getMessage(), 429, 
                    request.getRequestURI(), getRequestId()));
}
```

---

## Request ID Tracking

Every request is assigned a unique correlation ID for distributed tracing.

### How It Works

1. **RequestIdFilter** intercepts all incoming requests
2. Uses `X-Request-ID` header if provided by client, otherwise generates UUID
3. Stores ID in MDC (Mapped Diagnostic Context) for logging
4. Returns ID in response header for client correlation

### Client Usage

```bash
# Provide your own request ID
curl -H "X-Request-ID: my-trace-123" https://api.example.com/v2/search?q=test

# Response headers include:
# X-Request-ID: my-trace-123

# Or let the server generate one
curl https://api.example.com/v2/search?q=test

# Response headers include:
# X-Request-ID: a1b2c3d4
```

### Using Request ID in Code

```java
import org.slf4j.MDC;

// In any class, the request ID is available via MDC
String requestId = MDC.get("requestId");

// All log statements automatically include requestId when configured
logger.info("Processing request");  
// Output: 2026-01-03 12:34:56.789 [http-nio-8084-exec-1] [a1b2c3d4] INFO ...
```

---

## Logging Configuration

### Development Mode (Default)

Human-readable console output:

```
2026-01-03 12:34:56.789 [http-nio-8084-exec-1] [a1b2c3d4] INFO  i.m.w.api.SearchController - Search request: name=test, source=null
```

**Pattern:** `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{requestId:-}] %-5level %logger{36} - %msg%n`

### Production Mode

Structured JSON logs for log aggregation (ELK, Datadog, etc.):

```json
{
  "timestamp": "2026-01-03T12:34:56.789+0000",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "io.moov.watchman.api.SearchController",
  "message": "Search request: name=test, source=null",
  "mdc": {
    "requestId": "a1b2c3d4",
    "method": "GET",
    "path": "/v2/search"
  }
}
```

**Activate:** Set `SPRING_PROFILES_ACTIVE=production`

### Configuration File

Located at `src/main/resources/logback-spring.xml`:

```xml
<!-- Development: human-readable -->
<springProfile name="default,dev,test">
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
    <logger name="io.moov.watchman" level="DEBUG"/>
</springProfile>

<!-- Production: JSON structured -->
<springProfile name="production,prod">
    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
    <logger name="io.moov.watchman" level="INFO"/>
</springProfile>
```

### Log Levels by Package

| Package | Dev Level | Prod Level |
|---------|-----------|------------|
| `io.moov.watchman` | DEBUG | INFO |
| `org.springframework` | WARN | WARN |
| `org.hibernate` | WARN | WARN |
| `com.zaxxer.hikari` | WARN | WARN |

---

## Endpoint-Specific Error Handling

### Nemesis Testing Endpoints

**POST /v2/nemesis/trigger**

| Error | Status | Message | Cause |
|-------|--------|---------|-------|
| Invalid query count | 400 | "queries must be between 1 and 1000" | `queries` param outside valid range |
| Malformed JSON | 400 | "JSON parse error: Cannot construct..." | Invalid request body |
| Process failure | 500 | Internal error | Python script execution failed |

**GET /v2/nemesis/status/{jobId}**

| Error | Status | Message | Cause |
|-------|--------|---------|-------|
| Job not found | 404 | - | Invalid or expired jobId |

**GET /v2/nemesis/reports**

No specific errors - returns empty array if no reports exist.

**Example Error Response:**
```json
{
  "error": "Bad Request",
  "message": "JSON parse error: Cannot construct instance of `io.moov.watchman.api.NemesisController$TriggerRequest`, problem: queries must be between 1 and 1000",
  "status": 400,
  "path": "/v2/nemesis/trigger",
  "requestId": "51b8f2ba",
  "timestamp": "2026-01-11T14:49:52.752Z"
}
```

### Search Endpoints

**GET /v2/search**

| Error | Status | Message | Cause |
|-------|--------|---------|-------|
| Missing query | 400 | "Missing required parameter: q" | No `q` parameter |
| Invalid minScore | 400 | "Parameter type mismatch: minScore" | Non-numeric minScore |

**POST /v2/search/batch**

| Error | Status | Message | Cause |
|-------|--------|---------|-------|
| Empty batch | 400 | "Batch request cannot be empty" | No queries provided |
| Invalid request | 400 | "Malformed request body" | Invalid JSON |

### Download Endpoints

**POST /v2/data/download**

| Error | Status | Message | Cause |
|-------|--------|---------|-------|
| Download failure | 500 | "Failed to download OFAC data" | Network/source unavailable |
| I/O error | 500 | "Failed to write data files" | Disk space/permissions |

**POST /v2/data/refresh**

| Error | Status | Message | Cause |
|-------|--------|---------|-------|
| No data | 503 | "No downloaded data available" | Must download first |
| Parse error | 500 | "Failed to parse SDN file" | Corrupt data |

---

## Best Practices

### 1. Log at Appropriate Levels

```java
// DEBUG: Detailed diagnostic info (dev only)
logger.debug("Scoring entity {} against query '{}'", entity.getId(), query);

// INFO: Normal operations worth noting
logger.info("Search completed: query='{}', results={}, duration={}ms", query, count, duration);

// WARN: Recoverable issues
logger.warn("Retry attempt {} for download: {}", attempt, url);

// ERROR: Failures requiring attention (include exception)
logger.error("Failed to parse OFAC file: {}", filename, exception);
```

### 2. Use Structured Parameters

```java
// Good: Structured parameters
logger.info("Download completed: source={}, entities={}, duration={}ms", 
    source, entityCount, duration);

// Bad: String concatenation
logger.info("Download completed: " + source + " with " + entityCount + " entities");
```

### 3. Include Context in Exceptions

```java
// Good: Context in message
throw new EntityNotFoundException("SDN Entity", entityId);

// Bad: Generic message
throw new EntityNotFoundException("Not found");
```

### 4. Don't Log Sensitive Data

```java
// Bad: Logging full request body
logger.info("Request: {}", requestBody);

// Good: Log only what's needed
logger.info("Batch screening request: items={}", request.items().size());
```

### 5. Use MDC for Cross-Cutting Context

```java
// The RequestIdFilter already sets these, but you can add more:
MDC.put("userId", authenticatedUser.getId());
try {
    // All logs in this scope will include userId
    processRequest();
} finally {
    MDC.remove("userId");
}
```

### 6. Health Check Logging Suppression

The `RequestIdFilter` automatically suppresses logging for health check endpoints to reduce noise:

```java
private boolean isHealthCheck(String uri) {
    return uri.equals("/health") || 
           uri.equals("/v2/health") || 
           uri.equals("/actuator/health");
}
```

---

## Testing Error Handling

### Example Test Cases

```java
@Test
void shouldReturnErrorResponseForMissingParameter() throws Exception {
    mockMvc.perform(get("/v2/search"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value(containsString("q")))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.requestId").exists())
            .andExpect(jsonPath("$.timestamp").exists());
}

@Test
void shouldReturnRequestIdHeader() throws Exception {
    mockMvc.perform(get("/v2/search").param("q", "test"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-ID"));
}
```

---

## Troubleshooting

### Missing Request ID in Logs

Ensure `logback-spring.xml` includes `%X{requestId}` in the pattern:

```xml
<pattern>... [%X{requestId:-}] ...</pattern>
```

### JSON Logging Not Working

1. Check profile is set: `SPRING_PROFILES_ACTIVE=production`
2. Verify dependencies in `pom.xml`:
   - `logback-json-classic`
   - `logback-jackson`

### Exceptions Not Being Caught

Ensure your exception handler is in a package scanned by Spring:
- Package should be under `io.moov.watchman`
- Class needs `@ControllerAdvice` annotation

---

## Related Files

| File | Purpose |
|------|---------|
| [GlobalExceptionHandler.java](../src/main/java/io/moov/watchman/api/GlobalExceptionHandler.java) | Central exception handling |
| [ErrorResponse.java](../src/main/java/io/moov/watchman/api/ErrorResponse.java) | Error response DTO |
| [RequestIdFilter.java](../src/main/java/io/moov/watchman/api/RequestIdFilter.java) | Request correlation |
| [EntityNotFoundException.java](../src/main/java/io/moov/watchman/api/EntityNotFoundException.java) | 404 exception |
| [ServiceUnavailableException.java](../src/main/java/io/moov/watchman/api/ServiceUnavailableException.java) | 503 exception |
| [logback-spring.xml](../src/main/resources/logback-spring.xml) | Logging configuration |
| [GlobalExceptionHandlerTest.java](../src/test/java/io/moov/watchman/api/GlobalExceptionHandlerTest.java) | Error handling tests |
