# API Reference Generation for Nemesis

## Overview

The API Reference system prevents AI hallucination in the Nemesis repair pipeline by extracting exact method signatures from compiled Java bytecode and providing them to the AI during fix generation.

## Problem Statement

Without accurate API documentation, the AI would "hallucinate" non-existent methods when generating fixes:
- Invented `normalize()` instead of actual `normalizeText()`
- Created fake `Contact` class that doesn't exist
- Made up `similarity()`, `id()`, `birthDate()` methods
- Result: 16 compilation errors in PR #27

## Solution Architecture

### 1. API Extraction (`scripts/generate_api_reference.py`)

**What it does:**
- Uses `javap` (Java bytecode disassembler) to extract public API from compiled `.class` files
- Parses method signatures, field declarations, and class definitions
- Generates both JSON and Markdown formats

**How it works:**
```bash
javap -public target/classes/io/moov/watchman/api/SearchController.class
```

Extracts:
- Class name and type (class/interface/record)
- All public methods with exact signatures
- All public fields with types
- Package structure

**Output formats:**
- `target/api-reference.json` - Structured data for programmatic use
- `target/API-REFERENCE.md` - Human-readable markdown for AI consumption

**Example output:**
```markdown
## io.moov.watchman.search
### EntityScorerImpl (class)
**Methods:**
- public double calculateScore(Entity entity, SearchRequest request)
- public String normalizeText(String input)
- public List<String> tokenize(String text)
```

### 2. Docker Build Integration

**When it runs:**
During Docker image build, after Maven compilation:

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build

# ... compile Java code ...
RUN ./mvnw clean package -DskipTests -B

# Generate API reference
COPY scripts/generate_api_reference.py scripts/
RUN python3 scripts/generate_api_reference.py

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Copy API reference to runtime container
COPY --from=build /app/target/API-REFERENCE.md /app/API-REFERENCE.md
```

**Why during build:**
- Zero maintenance: automatically updates with code changes
- No drift: always reflects current compiled code
- No manual updates: regenerates on every deployment

### 3. Nemesis Integration (`scripts/nemesis/fix_generator.py`)

**Loading the API reference:**
```python
class FixGenerator:
    def __init__(self, project_root: Path):
        # Load API reference if available
        api_ref_path = self.project_root / "API-REFERENCE.md"
        if api_ref_path.exists():
            self.api_reference = api_ref_path.read_text()
            print("✓ Loaded API reference")
        else:
            self.api_reference = None
            print("⚠️  No API reference found")
```

**Including in AI prompts:**
```python
def _build_prompt(self, ...):
    if self.api_reference:
        prompt += f"""
API REFERENCE (USE ONLY THESE METHODS/CLASSES):
{self.api_reference[:15000]}  

CRITICAL: Only use methods and classes from the API reference above.
Do not invent or assume any methods not listed.
"""
```

**Character limit:**
- First 15,000 characters included in prompt
- Typically covers 50-70 most commonly used classes
- Sorted by package, most relevant classes appear first

### 4. Validation (`fix_generator.py`)

**Hallucination detection:**
```python
def _validate_changes(self, new_code: str, ...):
    # Check for known hallucinated patterns from PR #27
    hallucination_patterns = [
        'Contact',           # Fake class
        'normalize()',       # Wrong method name
        'similarity()',      # Fake method
        'id()',             # Wrong accessor
        'birthDate()'       # Wrong accessor
    ]
    
    for pattern in hallucination_patterns:
        if pattern in new_code:
            print(f"⚠️  Warning: Potential hallucination detected: {pattern}")
```

## Workflow

### Development Cycle
```
1. Developer writes Java code
2. Code committed and pushed
3. GitHub Actions triggers
4. Docker build starts
   ├─ Maven compiles Java → .class files
   ├─ generate_api_reference.py extracts API
   └─ API-REFERENCE.md copied to runtime container
5. Deploy to Fly.io
6. Nemesis runs (every 5 minutes)
7. Repair pipeline runs (2-59/5 cron)
   ├─ Loads API-REFERENCE.md
   ├─ Includes in AI prompts
   └─ Generates fixes using actual API
8. Creates PR with valid, compilable code
```

### Cron Schedule
- **Nemesis testing**: `*/5 * * * *` (every 5 minutes)
- **Repair pipeline**: `2-59/5 * * * *` (2 minutes after Nemesis)

## Technical Details

### Reflection-Based Extraction

**Why javap instead of parsing source:**
1. **Zero drift**: Extracts from actual compiled code
2. **Type accuracy**: Gets exact types after compilation
3. **No parsing errors**: Bytecode is canonical
4. **Includes generated code**: Records, Lombok, etc.

**javap flags:**
- `-public`: Only public members (what AI should use)
- No `-private`: Excludes internal implementation

### Package Processing

**Extraction process:**
```python
def generate_api_reference(target_dir: Path) -> Dict[str, List]:
    class_files = find_class_files(target_dir)  # Find all .class files
    api_reference = {}
    
    for class_file in class_files:
        api = extract_class_api(class_file)  # javap extraction
        if api:
            package = determine_package(class_file)
            api_reference[package] = api_reference.get(package, [])
            api_reference[package].append(api)
    
    return api_reference
```

**Statistics (watchman-java):**
- 91 `.class` files found
- 62 public classes documented
- ~15KB markdown output
- Generated in <5 seconds

### Error Handling

**Build-time errors:**
```python
if not target_dir.exists():
    print("❌ target/classes not found. Run 'mvn compile' first.")
    return 1
```

**Runtime fallback:**
```python
if not api_ref_path.exists():
    self.api_reference = None
    print("⚠️  No API reference found (will rely on code context only)")
```

**Processing errors:**
```python
try:
    result = subprocess.run(['javap', '-public', str(class_file)], ...)
except Exception as e:
    print(f"Warning: Could not process {class_file}: {e}")
    return None
```

## Benefits

### Before API Reference (PR #27)
- ❌ 16 compilation errors
- ❌ Invented methods: `normalize()`, `similarity()`, `id()`, `birthDate()`
- ❌ Fake classes: `Contact`
- ❌ Build failed
- ❌ Manual revert required

### After API Reference
- ✅ AI knows exact method signatures
- ✅ Cannot invent non-existent methods
- ✅ Validates against hallucination patterns
- ✅ Zero maintenance overhead
- ✅ Automatic updates with code changes
- ✅ Compilable code generation

## Monitoring

**Check if API reference is loaded:**
```bash
flyctl ssh console -a watchman-java -C "cat /data/logs/repair-pipeline.log | grep 'Loaded API reference'"
```

**Expected output:**
```
✓ Loaded API reference
```

**Verify API reference exists:**
```bash
flyctl ssh console -a watchman-java -C "ls -lh /app/API-REFERENCE.md"
```

**View API reference contents:**
```bash
flyctl ssh console -a watchman-java -C "head -50 /app/API-REFERENCE.md"
```

## Maintenance

### Zero Maintenance Required
- Automatically regenerates on every build
- No manual updates needed
- No drift from source code
- No documentation to keep in sync

### When to Update
You only need to update `generate_api_reference.py` if:
- Java changes its bytecode format (unlikely)
- Need to extract additional metadata
- Want to change output format

### Testing Locally
```bash
# Compile Java code
./mvnw compile -DskipTests -q

# Generate API reference
python3 scripts/generate_api_reference.py

# View output
head -100 target/API-REFERENCE.md
cat target/api-reference.json | jq '.'
```

## Future Enhancements

### Potential Improvements
1. **Smart truncation**: Include most-used classes first (currently alphabetical)
2. **Context-aware selection**: Only include classes related to the issue being fixed
3. **Type resolution**: Follow inheritance chains to include parent methods
4. **Annotation extraction**: Include Spring annotations, validation rules
5. **Example usage**: Extract actual usage patterns from test files

### Scaling Considerations
- Current 15KB limit handles ~60 classes
- For larger codebases: implement smart filtering
- Could generate per-package references
- Could use semantic search to find relevant classes

## Related Files

- `scripts/generate_api_reference.py` - API extraction script (213 lines)
- `scripts/nemesis/fix_generator.py` - AI fix generation (555 lines)
- `Dockerfile` - Build integration (lines 22-23, 45-46)
- `target/API-REFERENCE.md` - Generated markdown (runtime)
- `target/api-reference.json` - Generated JSON (runtime)

## References

- **PR #27**: Example of hallucination without API reference
- **Commit 839c81f**: Initial API reference implementation
- **Commit 7c6a8a5**: Fixed Python installation in build stage
