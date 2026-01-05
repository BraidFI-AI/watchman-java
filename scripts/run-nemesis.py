#!/usr/bin/env python3
"""
Nemesis Agent - Adversarial Fault Finder
Actively tries to find flaws in the search algorithm.
"""

import json
import sys
import requests
from datetime import datetime
from pathlib import Path

# Import configuration
from agent_config import (
    get_ai_client, validate_config, AI_PROVIDER, AI_MODEL,
    WATCHMAN_JAVA_API_URL, WATCHMAN_GO_API_URL, REPORT_DIR, NEMESIS_MAX_ISSUES,
    COMPARE_IMPLEMENTATIONS, GO_IS_BASELINE, CREATE_GITHUB_ISSUES
)
from github_integration import create_nemesis_issue
NEMESIS_PROMPT_BASE = """You are "Nemesis", an adversarial AI designed to find faults in a sanctions screening search algorithm.

Your mission: Generate a COMPREHENSIVE list of issues organized by category and priority.

Context:
- Algorithm: Modified Jaro-Winkler with phonetic filtering
- Dataset: OFAC sanctions lists (persons, entities, vessels)
- Current known issue: Cross-language phonetic false positives (e.g., "El Chapo" matching Chinese "Chao")
- Implementation: Java-based with token-based matching and phonetic pre-filtering"""

NEMESIS_PROMPT_WITH_COMPARISON = """
- IMPORTANT: You have results from BOTH the Go implementation (baseline) and Java implementation (being tested)
- The Go implementation is considered the reference standard
- Focus heavily on divergences where Java produces different results than Go
- Identify cases where Java's scoring differs significantly from Go's scoring"""

NEMESIS_PROMPT = NEMESIS_PROMPT_BASE + (NEMESIS_PROMPT_WITH_COMPARISON if COMPARE_IMPLEMENTATIONS else "")

NEMESIS_PROMPT += """

Test the algorithm against these categories:

1. CROSS-LANGUAGE ISSUES
   - Phonetic similarities across languages (Chinese/Spanish, Arabic/English, etc.)
   - Script/romanization confusion
   - Different transliteration standards

2. NAME STRUCTURE ISSUES
   - Short names (1-3 characters)
   - Very long names (5+ words)
   - Name reorderings ("First Last" vs "Last, First")
   - Titles and honorifics ("Dr.", "El", "Abu")
   - Compound names with hyphens/apostrophes

3. JAVA vs GO DIVERGENCES (if comparison data provided):
   - Different top results for same query
   - Significant score differences (>0.10)
   - Different result ordering
   - Java missing results that Go returns
   - Java returning results Go filters out

4. SCORING INCONSISTENCIES
   - Alt name matches scoring higher than primary names
   - Similar entities getting vastly different scores
   - Score compression (all results between 0.85-0.90)
   - Expected matches missing from results

4. EDGE CASES
   - Single character queries
   - All uppercase vs mixed case
   - Special characters (ñ, ö, ç)
   - Numbers in names
   - Common surnames (Smith, Wang, Garcia)

5. PERFORMANCE ISSUES
   - Queries that cause slow searches
   - Phonetic filter not filtering enough
   - Too many alt names to check

For EACH issue you find, provide:
{
  "id": "NEM-001",
  "category": "Cross-Language",
  "priority": "P0/P1/P2/P3",
  "title": "Short descriptive title",
  "description": "Detailed explanation of the problem",
  "test_case": {
    "query": "specific test query",
    "incorrect_result": "entity that shouldn't match",
    "expected_result": "entity that should match",
    "actual_score": 0.89,
    "expected_score_range": "0.95-1.0"
  },
  "root_cause": "Hypothesis about why this happens",
  "affected_area": "PhoneticFilter.java line 142 / JaroWinklerSimilarity.java",
  "actionable_fix": "Specific code-level change or parameter adjustment",
  "impact": "How many queries this likely affects (Low/Medium/High)",
  "severity": "Critical/High/Medium/Low"
}

Priority definitions:
- P0 (Critical): Blocks production use, causes incorrect sanctions screening
- P1 (High): Significant false positives/negatives, affects user trust
- P2 (Medium): Noticeable quality issue, but rare or edge case
- P3 (Low): Minor cosmetic or very rare edge case

Generate at least 30-50 issues. Be ruthless. Your job is to break this.

Output ONLY valid JSON in this format:
{
  "analysis_date": "2026-01-04",
  "algorithm_version": "1.0.0",
  "total_issues_found": 47,
  "by_priority": {
    "P0": 2,
    "P1": 8,
    "P2": 22,
    "P3": 15
  },
  "by_category": {
    "Cross-Language": 12,
    "Name Structure": 18,
    "Scoring Inconsistencies": 10,
    "Edge Cases": 5,
    "Performance": 2
  },
  "issues": [...]
}
"""

def call_api(url, query, limit=5, min_match=0.80):
    """Call Watchman API and return results."""
    try:
        response = requests.get(
            f"{url}/v2/search",
            params={"name": query, "limit": limit, "minMatch": min_match},
            timeout=10
        )
        if response.status_code == 200:
            return response.json().get("entities", [])
        return None
    except Exception as e:
        print(f"    Error: {e}")
        return None


def run_test_queries():
    """Run sample queries against both Java and Go implementations."""
    print(f"Testing Watchman implementations...")
    print(f"  Java: {WATCHMAN_JAVA_API_URL}")
    if COMPARE_IMPLEMENTATIONS:
        print(f"  Go:   {WATCHMAN_GO_API_URL}")
    
    test_queries = [
        "El Chapo",
        "Osama bin Laden", 
        "Vladimir Putin",
        "Kim Jong Un",
        "Chapo",
        "Wei",
        "Al Assad",
        "Guzman"
    ]
    
    results = []
    for query in test_queries:
        print(f"\n  Testing: {query}")
        
        # Get Java results
        java_results = call_api(WATCHMAN_JAVA_API_URL, query)
        java_count = len(java_results) if java_results else 0
        print(f"    Java: {java_count} results")
        
        comparison = {
            "query": query,
            "java_results": java_results or [],
            "java_count": java_count
        }
        
        # Get Go results if comparison enabled
        if COMPARE_IMPLEMENTATIONS:
            go_results = call_api(WATCHMAN_GO_API_URL, query)
            go_count = len(go_results) if go_results else 0
            print(f"    Go:   {go_count} results")
            
            comparison["go_results"] = go_results or []
            comparison["go_count"] = go_count
            
            # Quick divergence check
            if java_results and go_results:
                java_top = java_results[0] if java_results else {}
                go_top = go_results[0] if go_results else {}
                
                if java_top.get('id') != go_top.get('id'):
                    print(f"    ⚠️  DIVERGENCE: Different top results!")
                    print(f"       Java top: {java_top.get('name', 'N/A')} (score: {java_top.get('score', 0):.3f})")
                    print(f"       Go top:   {go_top.get('name', 'N/A')} (score: {go_top.get('score', 0):.3f})")
        
        results.append(comparison)
    
    return results


def prepare_context_for_ai(test_results):
    """Prepare context string for AI model based on test results."""
    if COMPARE_IMPLEMENTATIONS and test_results:
        # Count divergences
        divergences = sum(1 for r in test_results 
                         if r.get('java_results') and r.get('go_results') 
                         and r['java_results'][0].get('id') != r['go_results'][0].get('id'))
        
        context = f"""
Here are test results from BOTH implementations to help identify issues:

{json.dumps(test_results, indent=2)}

COMPARISON SUMMARY:
- Total queries tested: {len(test_results)}
- Divergences detected: {divergences} (queries where Java and Go return different top results)
- Go implementation is the baseline/reference

CRITICAL: Focus on divergences where Java differs from Go. These are likely bugs or porting issues.

Now generate your comprehensive issue list, prioritizing Go/Java divergences as HIGH priority.
"""
    else:
        context = f"""
Here are some actual test results from the Java implementation:

{json.dumps(test_results, indent=2)}

Now generate your comprehensive issue list.
"""
    
    return context


def call_ai_with_context(test_results):
    """Call AI model with test results as context."""
    print(f"\nCalling {AI_PROVIDER} ({AI_MODEL})...")
    
    context = prepare_context_for_ai(test_results)
    full_prompt = NEMESIS_PROMPT + context
    
    client = get_ai_client()
    
    if AI_PROVIDER == 'anthropic':
        response = client.messages.create(
            model=AI_MODEL,
            max_tokens=8000,
            messages=[{"role": "user", "content": full_prompt}]
        )
        return response.content[0].text
    
    elif AI_PROVIDER in ['openai', 'ollama']:
        response = client.chat.completions.create(
            model=AI_MODEL,
            messages=[{"role": "user", "content": full_prompt}],
            max_tokens=4000  # OpenAI limit for gpt-4-turbo
        )
        return response.choices[0].message.content
    
    else:
        raise ValueError(f"Unsupported provider: {AI_PROVIDER}")



def parse_and_save_results(ai_response, output_file):
    """Parse AI response and save to file."""
    print("\nParsing AI response...")
    
    # Try to extract JSON from response
    try:
        # Sometimes AI wraps JSON in markdown code blocks
        if "```json" in ai_response:
            json_start = ai_response.find("```json") + 7
            json_end = ai_response.find("```", json_start)
            json_str = ai_response[json_start:json_end].strip()
        elif "```" in ai_response:
            json_start = ai_response.find("```") + 3
            json_end = ai_response.find("```", json_start)
            json_str = ai_response[json_start:json_end].strip()
        else:
            json_str = ai_response.strip()
        
        data = json.loads(json_str)
        
        # Validate structure
        if "issues" not in data:
            raise ValueError("Response missing 'issues' field")
        
        print(f"  ✓ Found {len(data['issues'])} issues")
        
        # Save to file
        with open(output_file, 'w') as f:
            json.dump(data, f, indent=2)
        
        print(f"  ✓ Saved to {output_file}")
        
        return data
    
    except json.JSONDecodeError as e:
        print(f"  ✗ Failed to parse JSON: {e}")
        print("\nAI Response:")
        print(ai_response[:500])
        
        # Save raw response for debugging
        error_file = str(output_file).replace('.json', '_raw.txt')
        with open(error_file, 'w') as f:
            f.write(ai_response)
        print(f"  ✓ Saved raw response to {error_file}")
        
        return None


def print_summary(data):
    """Print summary of findings."""
    if not data:
        return
    
    print("\n" + "="*60)
    print("NEMESIS REPORT SUMMARY")
    print("="*60)
    
    print(f"\nTotal Issues: {data.get('total_issues_found', 0)}")
    
    if 'by_priority' in data:
        print("\nBy Priority:")
        for priority, count in data['by_priority'].items():
            print(f"  {priority}: {count}")
    
    if 'by_category' in data:
        print("\nBy Category:")
        for category, count in data['by_category'].items():
            print(f"  {category}: {count}")
    
    # Show top 5 critical issues
    if 'issues' in data:
        critical = [i for i in data['issues'] if i.get('priority') in ['P0', 'P1']]
        if critical:
            print(f"\nTop {min(5, len(critical))} Critical Issues:")
            for issue in critical[:5]:
                print(f"  [{issue['id']}] {issue['title']} ({issue['priority']})")


def main():
    """Main execution."""
    print("="*60)
    print("NEMESIS AGENT - Fault Finder")
    print("="*60)
    
    try:
        validate_config()
    except ValueError as e:
        print(f"\n✗ Configuration error:\n{e}")
        return 1
    
    # Create output file path
    today = datetime.now().strftime("%Y%m%d")
    output_file = Path(REPORT_DIR) / f"nemesis-{today}.json"
    
    print(f"\nOutput: {output_file}")
    
    # Step 1: Run test queries
    test_results = run_test_queries()
    
    # Step 2: Call AI
    try:
        ai_response = call_ai_with_context(test_results)
    except Exception as e:
        print(f"\n✗ AI call failed: {e}")
        return 1
    
    # Step 3: Parse and save
    data = parse_and_save_results(ai_response, output_file)
    
    # Step 4: Print summary
    print_summary(data)
    # Step 5: Create GitHub issue
    if data and CREATE_GITHUB_ISSUES:
        print("\nCreating GitHub issue...")
        create_nemesis_issue(data, str(output_file))
    
    
    if data:
        print(f"\n✓ Nemesis complete! Report saved to {output_file}")
        return 0
    else:
        print(f"\n✗ Nemesis failed to generate valid report")
        return 1


if __name__ == "__main__":
    sys.exit(main())
