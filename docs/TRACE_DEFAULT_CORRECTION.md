# CORRECTION: Trace Default Value Analysis

**Date:** 2026-01-27
**Issue:** Documentation incorrectly states trace defaults to TRUE for POST endpoint

---

## Current Behavior (CORRECT)

**SearchRequestBody.java:16**
```java
public SearchRequestBody {
    // Default trace to false if not specified
    trace = trace != null ? trace : false;  // ✅ Correctly defaults to FALSE
}
```

**Actual behavior:**
- User sends `{}` (no trace field) → `trace = false` ✅
- User sends `"trace": true` → `trace = true` ✅
- User sends `"trace": false` → `trace = false` ✅

**Why FALSE is correct:**
- Trace is only for debugging and tuning issues
- Not needed for normal operation
- Adds overhead to response processing
- Should be opt-in, not opt-out

---

## Bug in Controller

**SearchController.java:182-183**
```java
// Enable tracing (always enabled for POST, but respects request flag)
boolean enableTrace = requestBody.trace() != null ? requestBody.trace() : true;
```

**Problems:**
1. **Misleading comment:** Says "always enabled for POST" but that's not true
2. **Dead code:** The `: true` fallback is NEVER reached because:
   - Constructor always sets `trace` to non-null (either user value or `false`)
   - So `requestBody.trace() != null` is always `true`
   - The ternary always uses `requestBody.trace()`, never the fallback

**This code should be simplified to:**
```java
// Enable tracing if requested (defaults to false)
boolean enableTrace = requestBody.trace();
```

---

## Documentation Errors

### ❌ POST_SEARCH_CONFIG_OVERRIDES.md (Line 41, 52)

**Wrong:**
```markdown
- Trace is enabled by default (unlike GET)
```
```markdown
- trace is enabled by default for POST
```

**Should be:**
```markdown
- Trace is disabled by default (same as GET) - enable for debugging/tuning
```

### ❌ Postman Collection (Multiple locations)

**Wrong descriptions saying:**
- "Always get trace data (enabled by default for POST)"
- "Trace is enabled by default (unlike GET)"
- "trace enabled by default for POST"

**Should say:**
- "Trace disabled by default - set `trace: true` for debugging"
- "Trace defaults to false (same as GET)"

---

## Corrections Needed

1. **Update POST_SEARCH_CONFIG_OVERRIDES.md:**
   - Change all references from "trace enabled by default" to "trace disabled by default"
   - Emphasize trace is opt-in for debugging

2. **Update Postman collection:**
   - Fix all 5 POST request descriptions
   - Change "trace enabled by default" to "trace disabled by default"
   - Update request body JSON comments to show `"trace": false` as default

3. **Fix SearchController.java (optional cleanup):**
   - Simplify line 183 to just `boolean enableTrace = requestBody.trace();`
   - Update comment to reflect correct default behavior

4. **Update CODE_REVIEW_POST_SEARCH.md:**
   - Remove "Critical Issue #1: Inconsistent trace default"
   - Add note: "Controller line 183 has dead code but behavior is correct"

---

## Summary

**Current State:**
- ✅ Code correctly defaults trace to FALSE
- ❌ Documentation incorrectly says TRUE
- ⚠️ Controller has misleading comment and dead code

**Correct Behavior:**
- Trace defaults to FALSE (opt-in for debugging/tuning)
- User must explicitly set `"trace": true` to enable
- Same as GET endpoint behavior (consistency)

**User Confirmation:**
> "trace aka scoretrace this should be false by default. This is only enable for debugging and tuning issues."

✅ Code is correct, documentation is wrong.
