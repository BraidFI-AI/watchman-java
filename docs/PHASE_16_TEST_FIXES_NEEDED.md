# Phase 16 Test Constructor Fixes Needed

## Status
**BLOCKING**: Phase16ZoneOneCompletionTest.java has 80+ compilation errors preventing test execution.

## Root Cause
Phase 16 tests were written during RED phase but never updated when model constructors changed. The file uses old constructor signatures from earlier phases.

## Error Categories

### 1. Person Constructor (20+ instances)
**Current (Wrong)**:
```java
Person person = new Person(
    "P1",
    "John Smith",          // ❌ name is second param
    List.of(),            // ❌ altNames is third
    "male",               // gender correct
    null, null,           // dob/deceased correct
    "New York",           // birthPlace correct
    List.of(),            // remarks correct  
    List.of()             // governmentIds WRONG TYPE
);
```

**Correct**:
```java
Person person = new Person(
    "P1",                 // ✅ id first
    List.of("John Smith"), // ✅ altNames is List<String>
    "male",               // ✅ gender  
    null, null,           // ✅ dob, deceased (LocalDate)
    "New York",           // ✅ birthPlace
    List.of(),            // ✅ remarks (List<String>)
    List.of()             // ✅ governmentIds (List<GovernmentId>)
);
```

**Person signature (8 params)**:
```java
public record Person(
    String id,                          // 1
    List<String> altNames,              // 2 - NOT name!
    String gender,                      // 3
    LocalDate dob,                      // 4
    LocalDate deceased,                 // 5
    String birthPlace,                  // 6
    List<String> remarks,               // 7
    List<GovernmentId> governmentIds    // 8
)
```

### 2. Business Constructor (6+ instances)
**Current (Wrong)**:
```java
Business business = new Business(
    "B1",
    "Acme Corp",          // ❌ name is second param
    List.of(),            // ❌ altNames is third
    null, null,           // incorporated/dissolved correct
    List.of()             // governmentIds WRONG TYPE
);
```

**Correct**:
```java
Business business = new Business(
    "B1",                // ✅ id first
    List.of("Acme Corp"), // ✅ altNames is List<String>
    null, null,          // ✅ incorporated, dissolved (LocalDate)
    List.of()            // ✅ governmentIds (List<GovernmentId>)
);
```

**Business signature (5 params)**:
```java
public record Business(
    String id,                          // 1
    List<String> altNames,              // 2 - NOT name!
    LocalDate incorporated,             // 3
    LocalDate dissolved,                // 4
    List<GovernmentId> governmentIds    // 5
)
```

### 3. Aircraft Constructor (4+ instances)
**Current (Wrong)**:
```java
Aircraft aircraft = new Aircraft(
    "A1",
    "Boeing 737",        // ❌ name is second param
    List.of(),           // ❌ altNames is third
    "N12345",            // tailNumber correct
    "737-800",           // model correct
    "Boeing",            // manufacturer correct
    "USA",               // operator correct
    null, null           // ❌ built/destroyed wrong position
);
```

**Correct**:
```java
Aircraft aircraft = new Aircraft(
    "A1",                       // ✅ id first
    List.of("Boeing 737"),      // ✅ altNames is List<String>
    "N12345",                   // ✅ tailNumber
    "737-800",                  // ✅ model
    "Boeing",                   // ✅ manufacturer
    "USA",                      // ✅ operator
    null,                       // ✅ built (String, not LocalDate!)
    null                        // ✅ destroyed (String, not LocalDate!)
);
```

**Aircraft signature (8 params)**:
```java
public record Aircraft(
    String id,              // 1
    List<String> altNames,  // 2 - NOT name!
    String tailNumber,      // 3
    String model,           // 4
    String manufacturer,    // 5
    String operator,        // 6
    String built,           // 7 - String!
    String destroyed        // 8 - String!
)
```

### 4. Entity Constructor (All instances)
**Issue**: Entity constructor takes `EntityType type` (enum), NOT `String type`

**Current (Wrong)**:
```java
Entity entity = new Entity(
    "E1",
    "John Smith",
    "person",              // ❌ String
    SourceList.US_OFAC,
    ...
);
```

**Correct**:
```java
Entity entity = new Entity(
    "E1",
    "John Smith",
    EntityType.PERSON,     // ✅ Enum
    SourceList.US_OFAC,
    ...
);
```

### 5. ScorePiece Method Calls (10+ instances)
**Issue**: ScorePiece is a record - use field accessors, not methods

**Current (Wrong)**:
```java
double score = result.score();        // ❌ Not a method
boolean matched = result.matched();   // ❌ Not a method
String type = result.pieceType();     // ❌ Not a method
```

**Correct**:
```java
double score = result.score;          // ✅ Field accessor
boolean matched = result.matched;     // ✅ Field accessor
String type = result.pieceType;       // ✅ Field accessor
```

**ScorePiece record**:
```java
public record ScorePiece(
    double score,
    boolean matched,
    boolean exact,
    String pieceType,
    double weight,
    int fieldsCompared
)
```

### 6. GovernmentIdType Usage (4+ instances)
**Issue**: GovernmentId constructor takes `GovernmentIdType` enum, not String

**Current (Wrong)**:
```java
new GovernmentId("SSN", "123-45-6789", "USA")  // ❌ String
```

**Correct**:
```java
new GovernmentId(GovernmentIdType.SSN, "123-45-6789", "USA")  // ✅ Enum
```

## Fix Strategy

### Recommended Approach
Use `multi_replace_string_in_file` with comprehensive replacements. Group by test method for accuracy.

### Reference File
Use `/Users/randysannicolas/Documents/GitHub/watchman-java/src/test/java/io/moov/watchman/search/EntityNormalizationTest.java` - has correct constructor patterns for all model types.

### Verification Steps
1. Fix all Person constructors (convert name → altNames List, fix governmentIds type)
2. Fix all Business constructors (convert name → altNames List, fix governmentIds type)
3. Fix all Aircraft constructors (convert name → altNames List, built/destroyed are String)
4. Fix Entity type parameter (String → EntityType enum)
5. Fix ScorePiece accessors (methods → fields)
6. Fix GovernmentIdType (String → enum)
7. Run: `./mvnw test -Dtest=Phase16ZoneOneCompletionTest`

## Lines Affected
Based on compilation errors:
- Lines 46, 79, 195, 228, 271, 304, 426, 459, 505, 538, 618, 651, 698, 731, 777, 810, 863, 897 (Person)
- Lines 125, 155, 942, 973 (Business)
- Lines 348, 381 (Aircraft)
- Lines 59, 92, 135, 165, 208, 241, 284, 317, 361, 394, 439, 472, 518, 551, 631, 664, 711, 744, 790, 823, 876, 910, 952, 983, 1015, 1037, 1071, 1094 (Entity type)
- Lines 115, 116, 117, 118, 119, 188, 189, 264, 265, 340, 341, 342, 417, 418, 419, 495, 496, 498, 574, 575 (ScorePiece)
- Lines 862, 896, 941, 972 (GovernmentIdType)

## Time Estimate
- Multi-replace operations: ~10-15 replacements
- Compilation verification: ~5 minutes
- Total: ~20-30 minutes

## Next Session Priority
**IMMEDIATE**: Fix Phase 16 constructors so full test suite can run (currently 958 tests, but Phase 16 blocks compilation).

Phase 17 tests are syntactically correct (committed as cb5354e) and ready to run once Phase 16 compiles.
