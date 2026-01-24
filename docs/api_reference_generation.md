# API Reference Generation - Preventing AI Hallucination

## Summary
Extracts exact method signatures from compiled bytecode using javap. Provides ground truth API documentation to AI repair agent, preventing hallucinated methods. Regenerates automatically on every Docker build.

## Scope
- scripts/generate_api_reference.py - Extracts API from .class files using javap
- Outputs: target/API-REFERENCE.md (AI consumption), target/api-reference.json (programmatic)
- Docker build integration: runs after Maven compile
- Repair agent reads API-REFERENCE.md before generating fixes
- Out of scope: Private methods, implementation details

## Design notes
**Generation script:** scripts/generate_api_reference.py

**Extraction process:**
```bash
javap -public target/classes/io/moov/watchman/**/*.class
# Parses output to extract: class name, methods, fields, signatures
```

**Docker integration:**
```dockerfile
RUN ./mvnw clean package -DskipTests
COPY scripts/generate_api_reference.py scripts/
RUN python3 scripts/generate_api_reference.py
# API-REFERENCE.md bundled in runtime container
```

**Repair agent usage (fix_generator.py):**
```python
with open('/app/API-REFERENCE.md') as f:
    api_ref = f.read()
prompt = f"Use only these APIs:\\n{api_ref}\\n\\nGenerate fix for: {issue}"
```

**Prevents hallucination examples:**
- Before: AI invented Contact class → compilation error
- After: AI uses actual Customer class from API ref
- Before: AI called fake normalize() method → error
- After: AI calls real normalizeText(String) from reference

## How to validate
**Test 1:** Generate API reference
```bash
./mvnw clean package -DskipTests
python3 scripts/generate_api_reference.py
# Verify: target/API-REFERENCE.md created
# Verify: Contains EntityScorerImpl, SearchController
```

**Test 2:** Check completeness
```bash
grep "EntityScorerImpl" target/API-REFERENCE.md
# Verify: Lists scoreWithBreakdown, calculateScore, normalizeText methods
```

**Test 3:** Docker build includes API ref
```bash
docker build -t watchman-java .
docker run --rm watchman-java ls /app/API-REFERENCE.md
# Verify: File exists in runtime container
```

**Test 4:** Repair agent reads API ref
```bash
docker run --rm watchman-java python3 -c "
with open('/app/API-REFERENCE.md') as f: print(len(f.read()))
"
# Verify: Outputs character count (>10000)
```

## Implementation Details

**Build integration:** Regenerates API reference on every Docker build (no caching)

**Requirements:** javap must be available in build image (included in eclipse-temurin:21-jdk-alpine)
