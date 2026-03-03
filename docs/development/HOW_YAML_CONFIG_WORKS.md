# How YAML Configuration Works in Watchman Java
**Date:** February 22, 2026

## Good News First

**You're right - this isn't as bad as it seems!**

- ✅ The values ARE correct (BSA testing validated them)
- ✅ The code works properly (no functional bugs)
- ✅ The ScoreConfig system DOES work (for the 13 values using it)
- ✅ The hard-coded values are just defaults that COULD be extracted

---

## How Spring Boot @ConfigurationProperties Works

### 1. YAML File (application.yml)
```yaml
watchman:
  weights:
    name-weight: 35.0           # ← You can change this
    address-weight: 25.0        # ← You can change this
    minimum-score: 0.88         # ← You can change this
    exact-match-threshold: 0.99 # ← You can change this
```

### 2. Configuration Class (WeightConfig.java)
```java
@Configuration
@ConfigurationProperties(prefix = "watchman.weights")
public class WeightConfig {
    private double nameWeight;        // Spring AUTOMATICALLY injects from YAML
    private double addressWeight;     // Spring AUTOMATICALLY injects from YAML
    private double minimumScore;      // Spring AUTOMATICALLY injects from YAML
    private double exactMatchThreshold; // Spring AUTOMATICALLY injects from YAML
    
    // Getters/setters...
    public double getNameWeight() { return nameWeight; }
}
```

### 3. Using Configuration (EntityScorerImpl.java)
```java
@Service
public class EntityScorerImpl {
    private final WeightConfig weightConfig; // ← Spring injects this
    
    public EntityScorerImpl(WeightConfig weightConfig) {
        this.weightConfig = weightConfig; // ← Constructor injection
    }
    
    public double score(...) {
        // ✅ CORRECT: Uses YAML value (can be changed without recompiling)
        double weight = weightConfig.getNameWeight(); // Gets 35.0 from YAML
        
        // ❌ WRONG: Hard-coded literal (requires recompiling to change)
        boolean matchedViaAlias = altNameScore > nameScore * 1.2;
        //                                                     ^^^^ Magic number!
    }
}
```

---

## What's Actually Happening

### Values Using YAML ✅ (13 values - working correctly)

**Location:** application.yml lines 48-57
```yaml
name-weight: 35.0
address-weight: 25.0
critical-id-weight: 50.0
supporting-info-weight: 15.0
minimum-score: 0.88
exact-match-threshold: 0.99
name-comparison-enabled: true
alt-name-comparison-enabled: true
address-comparison-enabled: true
gov-id-comparison-enabled: true
crypto-comparison-enabled: true
contact-comparison-enabled: true
date-comparison-enabled: true
```

**How They're Used:**
```java
// EntityScorerImpl.java line 532-533
weightedSum += bestNameScore * weightConfig.getNameWeight();  // ✅ Gets 35.0 from YAML
totalWeight += weightConfig.getNameWeight();                   // ✅ Gets 35.0 from YAML

// EntityScorerImpl.java line 169
if (govIdScore >= weightConfig.getExactMatchThreshold()) {     // ✅ Gets 0.99 from YAML
```

**Result:** If you change YAML values and restart, behavior changes immediately. No recompilation needed.

---

### Values NOT Using YAML ❌ (50+ values - bypassing system)

**Example 1: Alias Boost (EntityScorerImpl.java lines 575-586)**
```java
// ❌ Hard-coded - NOT in YAML, NOT in WeightConfig
boolean matchedViaAlias = altNameScore > nameScore * 1.2 && altNameScore > 0.45;
//                                                     ^^^                   ^^^^
//                                          Magic numbers (1.2 = 20% ratio, 0.45 = min threshold)

if (matchedViaAlias && nameOnlyMatch && finalScore < 0.88) {
//                                                   ^^^^ Also hard-coded (duplicates YAML value!)
    finalScore = Math.min(1.0, finalScore + 0.50);
    //                                      ^^^^ Another magic number (+50% boost)
}
```

**What SHOULD happen:**
```java
// ✅ Correct pattern - load from YAML
boolean matchedViaAlias = altNameScore > nameScore * weightConfig.getAliasBoostRatio() 
    && altNameScore > weightConfig.getAliasMinThreshold();

if (matchedViaAlias && nameOnlyMatch && finalScore < weightConfig.getMinimumScore()) {
    finalScore = Math.min(1.0, finalScore + weightConfig.getAliasBoostAmount());
}
```

**Example 2: Address Comparison (EntityScorerImpl.java lines 420-450)**
```java
private double compareAddress(Address a, Address b) {
    double score = 0.0;
    
    // ❌ Hard-coded weights - NOT in YAML
    if (countryMatches) {
        score += 0.3;  // 30% for country
    }
    if (cityMatches) {
        score += cityScore * 0.3;  // 30% for city
    }
    if (streetMatches) {
        score += lineScore * 0.4;  // 40% for street
    }
    // These exact percentages are invisible to operators!
}
```

**What SHOULD happen:**
```java
// ✅ Correct pattern
if (countryMatches) {
    score += weightConfig.getAddressCountryWeight();  // Read from YAML
}
if (cityMatches) {
    score += cityScore * weightConfig.getAddressCityWeight();  // Read from YAML
}
```

---

## The Critical Difference

### YAML Values (13) - Changeable at Runtime
```yaml
# Edit this, restart app → behavior changes
watchman:
  weights:
    name-weight: 35.0  # Change to 40.0? Works immediately
```

### Hard-coded Values (50+) - Require Recompilation
```java
// Edit this, MUST recompile entire app, rebuild Docker image, redeploy
boolean matchedViaAlias = altNameScore > nameScore * 1.2;
//                                       Change to 1.3? ^^^^ Must recompile!
```

---

## Are Hard-coded Values Defaults?

**Short answer: NO**

In Spring Boot, defaults work like this:
```java
@ConfigurationProperties(prefix = "watchman.weights")
public class WeightConfig {
    private double aliasBoostRatio = 1.2;  // ← This would be a default
    
    public double getAliasBoostRatio() {
        return aliasBoostRatio;  // Returns YAML value if present, else 1.2
    }
}
```

**What you have:**
```java
// WeightConfig.java - NO aliasBoostRatio field at all!
// EntityScorerImpl.java - Just uses 1.2 directly
boolean matchedViaAlias = altNameScore > nameScore * 1.2;  // ← Ignores YAML completely
```

**The hard-coded values DON'T get overwritten by YAML because they're not connected to the config system at all.**

---

## Verification Test

**To prove this, let's trace one value through the system:**

### Name Weight (WORKING ✅)

1. **YAML defines it:**
```yaml
# application.yml line 48
name-weight: 35.0
```

2. **WeightConfig loads it:**
```java
// WeightConfig.java
@ConfigurationProperties(prefix = "watchman.weights")
public class WeightConfig {
    private double nameWeight;  // ← Spring injects 35.0 here at startup
    
    public double getNameWeight() { return nameWeight; }
}
```

3. **EntityScorerImpl uses it:**
```java
// EntityScorerImpl.java line 532
weightedSum += bestNameScore * weightConfig.getNameWeight();  // ← Gets 35.0
```

4. **Change the value:**
```yaml
# Change to 40.0, restart app
name-weight: 40.0
```

5. **Result:** Scoring behavior changes immediately (name gets more weight)

---

### Alias Boost Ratio (BROKEN ❌)

1. **YAML doesn't define it:**
```yaml
# application.yml - NO alias-boost-ratio field exists!
```

2. **WeightConfig doesn't load it:**
```java
// WeightConfig.java - NO aliasBoostRatio field exists!
```

3. **EntityScorerImpl uses magic number:**
```java
// EntityScorerImpl.java line 575
boolean matchedViaAlias = altNameScore > nameScore * 1.2;  // ← Hard-coded!
```

4. **Try to change the value:**
```yaml
# Add this to YAML, restart app
alias-boost-ratio: 1.5
```

5. **Result:** NOTHING CHANGES (field doesn't exist in WeightConfig, code ignores YAML)

---

## What Needs to Happen

### Step 1: Add Field to WeightConfig.java
```java
@ConfigurationProperties(prefix = "watchman.weights")
public class WeightConfig {
    // Existing fields...
    private double nameWeight;
    
    // NEW FIELDS (need 42 of these)
    private double aliasBoostRatio;
    private double aliasMinThreshold;
    private double aliasBoostAmount;
    
    public double getAliasBoostRatio() { return aliasBoostRatio; }
    public double getAliasMinThreshold() { return aliasMinThreshold; }
    public double getAliasBoostAmount() { return aliasBoostAmount; }
}
```

### Step 2: Add Value to application.yml
```yaml
watchman:
  weights:
    # Existing values...
    name-weight: 35.0
    
    # NEW VALUES (need 42 of these)
    alias-boost-ratio: 1.2          # 20% better requirement
    alias-min-threshold: 0.45       # Minimum alias score
    alias-boost-amount: 0.50        # +50% boost to final score
```

### Step 3: Replace Hard-coded References
```java
// BEFORE (hard-coded)
boolean matchedViaAlias = altNameScore > nameScore * 1.2 && altNameScore > 0.45;
if (matchedViaAlias && nameOnlyMatch && finalScore < 0.88) {
    finalScore = Math.min(1.0, finalScore + 0.50);
}

// AFTER (using YAML)
boolean matchedViaAlias = altNameScore > nameScore * weightConfig.getAliasBoostRatio() 
    && altNameScore > weightConfig.getAliasMinThreshold();
if (matchedViaAlias && nameOnlyMatch && finalScore < weightConfig.getMinimumScore()) {
    finalScore = Math.min(1.0, finalScore + weightConfig.getAliasBoostAmount());
}
```

---

## Why This Matters

### Current State (Hard-coded):
- ❌ BSA consultant can't see thresholds
- ❌ Production tuning requires recompilation
- ❌ Different deployments can't have different values
- ❌ Compliance audits show opaque algorithm
- ❌ Experiments require code changes

### After Extraction to YAML:
- ✅ All 55 values visible in one place
- ✅ Change value, restart app, see effect immediately
- ✅ Dev/staging/prod can use different configs
- ✅ Compliance audits see transparent algorithm
- ✅ Experiments via config changes (no code)

---

## Summary

**Current Architecture:**
- 13 values: YAML → WeightConfig → Code ✅ (working)
- 50+ values: Hard-coded in code ❌ (bypassing system)

**The hard-coded values are NOT defaults that get overwritten. They're completely separate from the config system.**

**The fix is straightforward:**
1. Add 42 fields to WeightConfig.java
2. Add 42 values to application.yml
3. Replace 50+ hard-coded literals with `weightConfig.getXXX()` calls
4. Run tests to verify no behavior changes

**Estimated effort:** 1-2 days  
**Risk:** Low (just moving constants, tests will catch any mistakes)  
**Benefit:** Complete configuration transparency
