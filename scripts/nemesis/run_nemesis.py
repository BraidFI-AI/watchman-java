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

# Import configuration (simplified for v2)
import os

# Get config from environment or use defaults
WATCHMAN_JAVA_API_URL = os.environ.get("WATCHMAN_JAVA_API_URL", "https://watchman-java.fly.dev")
WATCHMAN_GO_API_URL = os.environ.get("WATCHMAN_GO_API_URL", "https://watchman-go.fly.dev")
COMPARE_IMPLEMENTATIONS = os.environ.get("COMPARE_IMPLEMENTATIONS", "true").lower() == "true"

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
    print(f"  Java API: {WATCHMAN_JAVA_API_URL}")
    print(f"  Go API:   {WATCHMAN_GO_API_URL}")
    print(f"  Compare:  {COMPARE_IMPLEMENTATIONS}")
    print(f"  Output:   {output_file}")
    print(f"  Coverage: {coverage_file}")
    
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
    test_cases = generator.generate(count=100)
    
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
        go_url=WATCHMAN_GO_API_URL if COMPARE_IMPLEMENTATIONS else None
    )
    
    results = executor.execute_batch(
        test_cases, 
        compare_go=COMPARE_IMPLEMENTATIONS,
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
        if COMPARE_IMPLEMENTATIONS and result.java_results and result.go_results:
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
                issue_body = f"""## Nemesis 1.0 Report

**Date:** {datetime.now().strftime('%Y-%m-%d %H:%M UTC')}
**Queries Executed:** {len(test_cases)}
**Divergences Found:** {len(all_divergences)}
**Coverage:** {new_coverage['coverage_percentage']:.1f}%

### Divergences by Severity
"""
                
                for severity, count in sorted(by_severity.items()):
                    issue_body += f"- **{severity}**: {count}\n"
                
                # Add AI analysis if available
                if analysis and analysis.issues:
                    issue_body += f"\n### Top Issues Identified\n\n"
                    for i, issue in enumerate(analysis.issues[:5], 1):
                        priority = issue.get('priority', 'P?')
                        category = issue.get('category', 'Unknown')
                        description = issue.get('description', '')
                        recommendation = issue.get('recommendation', '')
                        
                        issue_body += f"#### {i}. [{priority}] {category}\n\n"
                        issue_body += f"{description}\n\n"
                        if recommendation:
                            issue_body += f"**Recommendation:** {recommendation}\n\n"
                
                # Add link to full report
                issue_body += f"\n### Full Report\n\nSee `/data/reports/nemesis-{today}.json` for complete details.\n"
                
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
