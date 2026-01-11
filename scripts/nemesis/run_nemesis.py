#!/usr/bin/env python3
"""
Nemesis 1.0 - Dynamic Adversarial Testing System
Generates 100+ dynamic queries per run, tracks coverage, detects divergences
"""

import json
import sys
from datetime import datetime
from pathlib import Path

# Import our new modular components
from nemesis.test_generator import RandomSamplingGenerator
from nemesis.query_executor import QueryExecutor
from nemesis.result_analyzer import ResultAnalyzer
from nemesis.coverage_tracker import CoverageTracker
from nemesis.ai_analyzer import AIAnalyzer
from nemesis.external_provider_adapter import OFACAPIAdapter

# Import configuration (simplified for v2)
import os

# Get config from environment or use defaults
WATCHMAN_JAVA_API_URL = os.environ.get("WATCHMAN_JAVA_API_URL", "https://watchman-java.fly.dev")
WATCHMAN_GO_API_URL = os.environ.get("WATCHMAN_GO_API_URL", "https://watchman-go.fly.dev")
COMPARE_IMPLEMENTATIONS = os.environ.get("COMPARE_IMPLEMENTATIONS", "true").lower() == "true"

# External provider configuration
COMPARE_EXTERNAL = os.environ.get("COMPARE_EXTERNAL", "false").lower() == "true"
EXTERNAL_PROVIDER = os.environ.get("EXTERNAL_PROVIDER", "ofac-api")  # Currently only ofac-api supported
OFAC_API_KEY = os.environ.get("OFAC_API_KEY", "")

# Query configuration
QUERIES_PER_RUN = int(os.environ.get("QUERIES_PER_RUN", "100"))

# Setup directories (local or Fly)
if Path("/data").exists():
    REPORT_DIR = Path("/data/reports")
else:
    REPORT_DIR = Path(__file__).parent.parent / "reports"


def fetch_ofac_entities(api_url: str, max_entities: int = 1000) -> list:
    """Fetch OFAC entities from API."""
    import requests
    
    print(f"Fetching OFAC entities from {api_url}...")
    entities = []
    seen_ids = set()
    
    try:
        # Sample diverse entities by searching common letters
        for letter in ['A', 'B', 'C', 'M', 'K', 'S', 'W', 'G', 'L', 'H', 'R', 'T']:
            response = requests.get(
                f"{api_url}/v2/search",
                params={"name": letter, "limit": 100, "minMatch": 0.1},
                timeout=10
            )
            
            if response.status_code == 200:
                for entity in response.json().get("entities", []):
                    entity_id = entity.get("id")
                    if entity_id and entity_id not in seen_ids:
                        seen_ids.add(entity_id)
                        entities.append({
                            "id": entity_id,
                            "name": entity.get("name", ""),
                            "altNames": entity.get("altNames", [])
                        })
                        
            if len(entities) >= max_entities:
                break
        
        print(f"✓ Fetched {len(entities)} unique OFAC entities")
        return entities
        
    except Exception as e:
        print(f"✗ Error fetching entities: {e}")
        return []


def main():
    print("=" * 80)
    print("NEMESIS 1.0 - Dynamic Adversarial Testing System")
    print("=" * 80)
    
    # Setup paths
    today = datetime.now().strftime("%Y%m%d")
    output_file = Path(REPORT_DIR) / f"nemesis-{today}.json"
    state_dir = Path("/data/state") if Path("/data").exists() else Path(REPORT_DIR).parent / "state"
    state_dir.mkdir(parents=True, exist_ok=True)
    coverage_file = state_dir / "nemesis_coverage.json"
    
    print(f"\nConfiguration:")
    print(f"  Java API:   {WATCHMAN_JAVA_API_URL}")
    print(f"  Go API:     {WATCHMAN_GO_API_URL}")
    print(f"  Compare:    {COMPARE_IMPLEMENTATIONS}")
    print(f"  External:   {COMPARE_EXTERNAL}")
    if COMPARE_EXTERNAL:
        print(f"  Provider:   {EXTERNAL_PROVIDER}")
        print(f"  API Key:    {'***' + OFAC_API_KEY[-4:] if OFAC_API_KEY and len(OFAC_API_KEY) > 4 else 'Not set'}")
    print(f"  Output:     {output_file}")
    print(f"  Coverage:   {coverage_file}")
    
    # Initialize external provider adapter if enabled
    external_adapter = None
    if COMPARE_EXTERNAL:
        if not OFAC_API_KEY:
            print("\n✗ ERROR: COMPARE_EXTERNAL=true but OFAC_API_KEY not set")
            return 1
        
        print(f"\n  Initializing {EXTERNAL_PROVIDER} adapter...")
        external_adapter = OFACAPIAdapter(api_key=OFAC_API_KEY)
        print(f"  ✓ External provider ready")
    
    # Step 1: Fetch OFAC entities
    print(f"\n{'='*80}")
    print("STEP 1: Fetching OFAC Entities")
    print('='*80)
    entities = fetch_ofac_entities(WATCHMAN_JAVA_API_URL, max_entities=1000)
    
    if not entities:
        print("✗ Failed to fetch entities")
        return 1
    
    # Step 2: Initialize coverage tracker
    print(f"\n{'='*80}")
    print("STEP 2: Coverage Tracking")
    print('='*80)
    tracker = CoverageTracker(state_file=coverage_file, total_entities=len(entities))
    coverage_summary = tracker.get_summary()
    print(f"  Entities tested: {coverage_summary['entities_tested']}/{coverage_summary['total_entities']}")
    print(f"  Coverage: {coverage_summary['coverage_percentage']:.1f}%")
    
    # Step 3: Generate test queries (prioritize untested entities)
    print(f"\n{'='*80}")
    print("STEP 3: Generating Test Queries")
    print('='*80)
    
    # Use prioritized entities for better coverage
    prioritized_entities = tracker.get_prioritized_entities(entities, count=min(200, len(entities)))
    generator = RandomSamplingGenerator(entities=prioritized_entities)
    test_cases = generator.generate(count=QUERIES_PER_RUN)
    
    print(f"✓ Generated {len(test_cases)} dynamic test queries")
    
    # Show sample
    print(f"\nSample queries:")
    for i, tc in enumerate(test_cases[:10], 1):
        print(f"  {i:2}. {tc.query:40} ({tc.variation_type})")
    
    # Step 4: Execute queries
    print(f"\n{'='*80}")
    print("STEP 4: Executing Queries")
    print('='*80)
    
    executor = QueryExecutor(
        java_url=WATCHMAN_JAVA_API_URL,
        go_url=WATCHMAN_GO_API_URL if COMPARE_IMPLEMENTATIONS else None,
        external_adapter=external_adapter if COMPARE_EXTERNAL else None
    )
    
    results = executor.execute_batch(
        test_cases, 
        compare_go=COMPARE_IMPLEMENTATIONS,
        compare_external=COMPARE_EXTERNAL,
        show_progress=True
    )
    
    print(f"✓ Executed {len(results)} queries")
    
    # Step 5: Analyze results and detect divergences
    print(f"\n{'='*80}")
    print("STEP 5: Analyzing Results")
    print('='*80)
    
    analyzer = ResultAnalyzer()
    all_divergences = []
    
    for result in results:
        # 3-way comparison if external provider enabled
        if COMPARE_EXTERNAL and result.java_results and result.go_results and result.external_results:
            divergences = analyzer.compare_three_way(
                result.java_results, 
                result.go_results,
                result.external_results
            )
            if divergences:
                for div in divergences:
                    all_divergences.append({
                        "query": result.query,
                        "type": div.type.value,
                        "severity": div.severity,
                        "description": div.description,
                        "java_data": div.java_data,
                        "go_data": div.go_data,
                        "external_data": div.external_data,
                        "score_difference": div.score_difference,
                        "agreement_pattern": div.agreement_pattern
                    })
        # 2-way comparison (Java vs Go) if external not enabled
        elif COMPARE_IMPLEMENTATIONS and result.java_results and result.go_results:
            divergences = analyzer.compare(result.java_results, result.go_results)
            if divergences:
                for div in divergences:
                    all_divergences.append({
                        "query": result.query,
                        "type": div.type.value,
                        "severity": div.severity,
                        "description": div.description,
                        "java_data": div.java_data,
                        "go_data": div.go_data,
                        "score_difference": div.score_difference
                    })
    
    print(f"✓ Found {len(all_divergences)} divergences")
    
    # Count by severity
    by_severity = {}
    for div in all_divergences:
        sev = div["severity"]
        by_severity[sev] = by_severity.get(sev, 0) + 1
    
    if by_severity:
        print(f"  By severity: {', '.join(f'{k}={v}' for k, v in sorted(by_severity.items()))}")
    
    # Step 5a: Re-query divergences with trace enabled (Java only feature)
    if len(all_divergences) > 0:
        print(f"\n  Re-querying divergences with trace enabled for root cause analysis...")
        
        # Track unique queries to avoid duplicates
        traced_queries = set()
        trace_count = 0
        
        for div in all_divergences:
            query = div["query"]
            
            # Only trace each query once, and only for critical/moderate divergences
            if query not in traced_queries and div["severity"] in ["critical", "moderate"]:
                traced_queries.add(query)
                
                # Re-run query with trace enabled (Java only)
                trace_result = executor.execute(
                    query,
                    compare_go=False,  # Don't need Go for trace
                    compare_external=False,  # Don't need external for trace
                    enable_trace=True,  # Enable detailed scoring trace
                    timeout=15.0  # Longer timeout for trace query
                )
                
                # Attach trace data to this divergence
                if trace_result.java_trace:
                    div["java_trace"] = trace_result.java_trace
                    trace_count += 1
        
        if trace_count > 0:
            print(f"  ✓ Captured {trace_count} trace(s) for root cause analysis")
    
    # Step 5b: AI Analysis of divergences
    if len(all_divergences) > 0:
        print(f"\n  Running AI analysis...")
        
        ai_analyzer = AIAnalyzer(
            provider=os.environ.get("AI_PROVIDER", "openai"),
            api_key=os.environ.get("OPENAI_API_KEY"),
            model=os.environ.get("AI_MODEL", "gpt-4-turbo")
        )
        
        analysis = ai_analyzer.analyze(all_divergences, total_queries=len(test_cases))
        
        print(f"  ✓ AI identified {analysis.patterns_identified} patterns")
        print(f"  ✓ Generated {len(analysis.issues)} prioritized issues")
        
        if analysis.issues:
            print(f"\n  Top Issues:")
            for i, issue in enumerate(analysis.issues[:3], 1):
                priority = issue.get('priority', 'P?')
                category = issue.get('category', 'Unknown')
                desc = issue.get('description', '')[:80]
                print(f"    {i}. [{priority}] {category}: {desc}...")
    else:
        analysis = None
    
    # Step 6: Update coverage
    print(f"\n{'='*80}")
    print("STEP 6: Updating Coverage")
    print('='*80)
    
    for tc in test_cases:
        if tc.source_entity_id:
            entity_name = next((e["name"] for e in entities if e["id"] == tc.source_entity_id), "Unknown")
            tracker.record_test(tc.source_entity_id, entity_name)
    
    tracker.save()
    
    new_coverage = tracker.get_summary()
    print(f"✓ Coverage updated: {new_coverage['coverage_percentage']:.1f}% ({new_coverage['entities_tested']} entities)")
    
    # Step 7: Save report
    print(f"\n{'='*80}")
    print("STEP 7: Generating Report")
    print('='*80)
    
    report = {
        "run_date": datetime.now().isoformat(),
        "version": "1.0",
        "configuration": {
            "total_queries": len(test_cases),
            "strategy": "random_sampling",
            "compare_implementations": COMPARE_IMPLEMENTATIONS
        },
        "coverage": {
            "entities_tested_today": len(set(tc.source_entity_id for tc in test_cases if tc.source_entity_id)),
            "cumulative_tested": new_coverage['entities_tested'],
            "cumulative_coverage_pct": new_coverage['coverage_percentage'],
            "total_entities": len(entities)
        },
        "results_summary": {
            "total_divergences": len(all_divergences),
            "by_severity": by_severity
        },
        "ai_analysis": {
            "patterns_identified": analysis.patterns_identified if analysis else 0,
            "issues": analysis.issues if analysis else [],
            "summary": analysis.summary if analysis else "No AI analysis performed"
        },
        "divergences": all_divergences[:50],  # Limit to first 50 for report size
        "test_queries": [{"query": tc.query, "variation": tc.variation_type} for tc in test_cases[:20]]
    }
    
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with open(output_file, 'w') as f:
        json.dump(report, f, indent=2)
    
    print(f"✓ Report saved: {output_file}")
    
    # Step 8: Create GitHub issue if divergences found
    if len(all_divergences) > 0:
        print(f"\n{'='*80}")
        print("STEP 8: Creating GitHub Issue")
        print('='*80)
        
        github_token = os.environ.get("GITHUB_TOKEN")
        create_issues = os.environ.get("CREATE_GITHUB_ISSUES", "false").lower() == "true"
        github_repo = os.environ.get("GITHUB_REPO", "moov-io/watchman-java")
        
        if create_issues and github_token:
            try:
                from github import Github
                
                gh = Github(github_token)
                repo = gh.get_repo(github_repo)
                
                # Create issue with summary of divergences
                issue_title = f"Nemesis: {len(all_divergences)} divergences found ({today})"
                
                # Build issue body
                issue_body = f"""## Nemesis 1.0 Report - Automated Testing Results

**Date:** {datetime.now().strftime('%Y-%m-%d %H:%M UTC')}  
**Queries Tested:** {len(test_cases)} diverse name searches  
**Issues Found:** {len(all_divergences)} differences between Java and Go implementations  
**Test Coverage:** {new_coverage['coverage_percentage']:.1f}% of OFAC entities ({new_coverage['entities_tested']}/{len(entities)} tested)

---

## 🎯 What This Report Means

Nemesis tests the **Java implementation** against the **Go baseline** (our production-proven system). Any difference is a potential bug in Java that needs investigation.

---

## 📊 Issues by Impact

"""
                
                severity_explanations = {
                    "critical": "🔴 **CRITICAL** - Java returns wrong entity or significantly different scores. Could lead to compliance failures.",
                    "moderate": "🟡 **MODERATE** - Java returns extra/missing results compared to Go. May affect match quality.",
                    "minor": "🟢 **MINOR** - Small scoring differences (<5%). Low priority."
                }
                
                for severity in ["critical", "moderate", "minor"]:
                    count = by_severity.get(severity, 0)
                    if count > 0:
                        issue_body += f"{severity_explanations[severity]}\n"
                        issue_body += f"- **Count:** {count} queries affected\n\n"
                
                # Add divergence type breakdown with explanations
                by_type = {}
                for div in all_divergences:
                    dtype = div.get("type", "unknown")
                    by_type[dtype] = by_type.get(dtype, 0) + 1
                
                issue_body += f"\n## 🔍 Issue Types\n\n"
                
                type_explanations = {
                    "top_result_differs": "**Wrong Top Match** - Java returns a different entity than Go as the #1 result",
                    "score_difference": "**Score Mismatch** - Same entity but Java calculates a different confidence score",
                    "java_extra_result": "**Java Over-Matching** - Java returns results Go filters out (possible false positives)",
                    "go_extra_result": "**Java Under-Matching** - Go returns results Java misses (possible false negatives)",
                    "result_order_differs": "**Different Ranking** - Same entities but different order"
                }
                
                for dtype, count in sorted(by_type.items(), key=lambda x: -x[1]):
                    explanation = type_explanations.get(dtype, dtype)
                    issue_body += f"- {explanation}: **{count} occurrences**\n"
                
                # Add AI analysis if available
                if analysis and analysis.issues:
                    issue_body += f"\n---\n\n### 🤖 AI Analysis - Top Issues\n\n"
                    for i, issue in enumerate(analysis.issues[:5], 1):
                        priority = issue.get('priority', 'P?')
                        category = issue.get('category', 'Unknown')
                        description = issue.get('description', '')
                        recommendation = issue.get('recommendation', '')
                        affected = issue.get('affected_queries', 0)
                        
                        issue_body += f"#### {i}. [{priority}] {category}\n\n"
                        if affected:
                            issue_body += f"**Affected Queries:** {affected}\n\n"
                        issue_body += f"{description}\n\n"
                        if recommendation:
                            issue_body += f"**💡 Recommendation:** {recommendation}\n\n"
                        issue_body += "---\n\n"
                
                # Add sample divergences with clearer descriptions
                issue_body += f"\n---\n\n## 📋 Example Issues (Top 10)\n\n"
                issue_body += f"*These examples show the most common problems. Full details in report file.*\n\n"
                
                for i, div in enumerate(all_divergences[:10], 1):
                    query = div.get('query', 'N/A')
                    dtype = div.get('type', 'unknown')
                    severity = div.get('severity', 'unknown')
                    description = div.get('description', '')
                    
                    # Create human-readable type label
                    type_labels = {
                        "top_result_differs": "🔴 Wrong Top Match",
                        "score_difference": "📊 Score Mismatch",
                        "java_extra_result": "➕ Java Over-Matching",
                        "go_extra_result": "➖ Java Missing Result",
                        "result_order_differs": "🔄 Different Ranking"
                    }
                    type_label = type_labels.get(dtype, dtype)
                    
                    issue_body += f"### {i}. {type_label}\n\n"
                    issue_body += f"**Search Query:** `{query}`\n\n"
                    
                    # Add result details if available
                    java_data = div.get('java_data')
                    go_data = div.get('go_data')
                    
                    if java_data or go_data:
                        issue_body += f"**What Happened:**\n"
                        if java_data:
                            java_name = java_data.get('name', 'N/A')
                            java_score = java_data.get('match', 0)
                            issue_body += f"- Java returned: `{java_name}` (confidence: {java_score:.1%})\n"
                        if go_data:
                            go_name = go_data.get('name', 'N/A')
                            go_score = go_data.get('match', 0)
                            issue_body += f"- Go returned: `{go_name}` (confidence: {go_score:.1%})\n"
                        
                        # Add interpretation
                        if dtype == "top_result_differs":
                            issue_body += f"\n**Why This Matters:** Java and Go disagree on which entity matches best. This could cause compliance misses.\n"
                        elif dtype == "score_difference":
                            score_diff = div.get('score_difference', 0)
                            issue_body += f"\n**Why This Matters:** Score difference of {score_diff:.1%}. Scoring algorithm may need calibration.\n"
                        elif dtype == "java_extra_result":
                            issue_body += f"\n**Why This Matters:** Java may be creating false positives - matching names that shouldn't match.\n"
                        elif dtype == "go_extra_result":
                            issue_body += f"\n**Why This Matters:** Java may be missing legitimate matches that Go finds.\n"
                    
                    issue_body += "\n"
                
                if len(all_divergences) > 10:
                    issue_body += f"\n*... plus {len(all_divergences) - 10} more issues in the full report*\n"
                
                # Add footer with action items
                issue_body += f"\n---\n\n## 🔧 Recommended Actions\n\n"
                
                critical_count = by_severity.get('critical', 0)
                if critical_count > 0:
                    issue_body += f"1. **Priority 1:** Fix {critical_count} critical issues first (wrong top matches, major score differences)\n"
                
                java_extra = by_type.get('java_extra_result', 0)
                if java_extra > 0:
                    issue_body += f"2. **Investigate Over-Matching:** Java returns {java_extra} extra results - review matching thresholds\n"
                
                go_extra = by_type.get('go_extra_result', 0)
                if go_extra > 0:
                    issue_body += f"3. **Investigate Under-Matching:** Java misses {go_extra} results that Go finds - check scoring logic\n"
                
                issue_body += f"\n## 📁 Full Report Location\n\n"
                issue_body += f"Complete technical details: `/data/reports/nemesis-{today}.json`\n\n"
                issue_body += f"```json\n"
                issue_body += f"{{\n"
                issue_body += f'  "total_issues": {len(all_divergences)},\n'
                issue_body += f'  "critical": {by_severity.get("critical", 0)},\n'
                issue_body += f'  "moderate": {by_severity.get("moderate", 0)},\n'
                issue_body += f'  "minor": {by_severity.get("minor", 0)},\n'
                issue_body += f'  "coverage": "{new_coverage["coverage_percentage"]:.1f}%",\n'
                issue_body += f'  "entities_tested": {new_coverage["entities_tested"]}\n'
                issue_body += f"}}\n"
                issue_body += f"```\n"
                
                # Create the issue
                created_issue = repo.create_issue(
                    title=issue_title,
                    body=issue_body,
                    labels=["nemesis", "automated-testing"]
                )
                
                print(f"✓ Created GitHub issue: {created_issue.html_url}")
                
            except ImportError:
                print(f"⚠ PyGithub not installed. Run: pip install PyGithub")
            except Exception as e:
                print(f"⚠ Failed to create GitHub issue: {e}")
        else:
            if not github_token:
                print(f"⚠ GitHub integration disabled: GITHUB_TOKEN not set")
            elif not create_issues:
                print(f"⚠ GitHub integration disabled: CREATE_GITHUB_ISSUES not set to 'true'")
            print(f"  To enable: set GITHUB_TOKEN and CREATE_GITHUB_ISSUES=true")
    
    # Final summary
    print(f"\n{'='*80}")
    print("SUMMARY")
    print('='*80)
    print(f"  Queries executed: {len(test_cases)}")
    print(f"  Divergences found: {len(all_divergences)}")
    print(f"  Coverage: {new_coverage['coverage_percentage']:.1f}%")
    print(f"  Report: {output_file}")
    print(f"\n✅ Nemesis 1.0 complete!")
    
    return 0


if __name__ == "__main__":
    sys.exit(main())
