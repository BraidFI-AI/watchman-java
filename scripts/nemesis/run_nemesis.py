#!/usr/bin/env python3
"""
Nemesis 1.0 - Dynamic Adversarial Testing System
Generates 100+ dynamic queries per run, tracks coverage, detects divergences
"""

import json
import sys
import subprocess
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


def fetch_ofac_entities(api_url: str, max_entities: int = None) -> list:
    """Fetch complete OFAC SDN list from API (~12,500+ entities)."""
    import requests
    
    print(f"Fetching complete OFAC SDN list from {api_url}...")
    entities = []
    seen_ids = set()
    
    try:
        # Fetch ALL entities by searching common letters with high limit
        # This ensures we get the complete OFAC SDN list for comprehensive testing
        for letter in ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 
                      'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z']:
            response = requests.get(
                f"{api_url}/v2/search",
                params={"name": letter, "limit": 1000, "minMatch": 0.01},
                timeout=30
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
                        
            if max_entities and len(entities) >= max_entities:
                break
        
        print(f"✓ Fetched {len(entities)} unique OFAC entities from complete SDN list")
        return entities
        
    except Exception as e:
        print(f"✗ Error fetching entities: {e}")
        return []


def run_repair_pipeline(report_file: Path) -> dict:
    """
    Run the repair pipeline on a Nemesis report.
    
    Args:
        report_file: Path to nemesis report JSON
        
    Returns:
        Dict with repair results including PR URLs
    """
    script_dir = Path(__file__).parent.parent
    pipeline_script = script_dir / "run_repair_pipeline.py"
    
    if not pipeline_script.exists():
        raise FileNotFoundError(f"Repair pipeline script not found: {pipeline_script}")
    
    print(f"\n  Running: {pipeline_script}")
    
    # Run repair pipeline script
    result = subprocess.run(
        [sys.executable, str(pipeline_script), str(report_file)],
        capture_output=True,
        text=True,
        timeout=300  # 5 minute timeout
    )
    
    if result.returncode != 0:
        raise RuntimeError(f"Repair pipeline exited with code {result.returncode}:\n{result.stderr}")
    
    print(result.stdout)
    
    # Load PR results file
    reports_dir = report_file.parent
    pr_results_files = sorted(reports_dir.glob("pr-results-*.json"), reverse=True)
    
    if not pr_results_files:
        return {
            "enabled": True,
            "action_plan_file": None,
            "code_analysis_file": None,
            "fix_proposal_file": None,
            "pr_results_file": None,
            "auto_fix_count": 0,
            "human_review_count": 0,
            "too_complex_count": 0,
            "prs_created": []
        }
    
    # Load most recent PR results
    pr_results_file = pr_results_files[0]
    with open(pr_results_file) as f:
        pr_data = json.load(f)
    
    # Extract PR information
    prs_created = []
    for result in pr_data.get("results", []):
        if result.get("status") == "success":
            prs_created.append({
                "issue_id": result.get("issue_id"),
                "pr_url": result.get("pr_url"),
                "branch": result.get("branch"),
                "status": "success"
            })
    
    # Load action plan to get counts
    action_plan_files = sorted(reports_dir.glob("action-plan-*.json"), reverse=True)
    auto_fix_count = 0
    human_review_count = 0
    too_complex_count = 0
    
    if action_plan_files:
        with open(action_plan_files[0]) as f:
            action_plan = json.load(f)
            auto_fix_count = len(action_plan.get("auto_fix_actions", []))
            human_review_count = len(action_plan.get("human_review_actions", []))
            too_complex_count = len(action_plan.get("investigation_needed", []))
    
    return {
        "enabled": True,
        "action_plan_file": str(action_plan_files[0]) if action_plan_files else None,
        "code_analysis_file": str(sorted(reports_dir.glob("code-analysis-*.json"), reverse=True)[0]) if list(reports_dir.glob("code-analysis-*.json")) else None,
        "fix_proposal_file": str(sorted(reports_dir.glob("fix-proposal-*.json"), reverse=True)[0]) if list(reports_dir.glob("fix-proposal-*.json")) else None,
        "pr_results_file": str(pr_results_file),
        "auto_fix_count": auto_fix_count,
        "human_review_count": human_review_count,
        "too_complex_count": too_complex_count,
        "prs_created": prs_created
    }


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
    print("STEP 1: Fetching Complete OFAC SDN List")
    print('='*80)
    entities = fetch_ofac_entities(WATCHMAN_JAVA_API_URL)
    
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
        enable_trace=True,
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
                    # Limit trace data to essential fields only (avoid 2GB+ reports)
                    java_trace_summary = None
                    if result.java_trace:
                        java_trace_summary = {
                            "sessionId": result.java_trace.get("sessionId"),
                            "durationMs": result.java_trace.get("durationMs"),
                            "eventCount": len(result.java_trace.get("events", [])),
                            "metadata": result.java_trace.get("metadata"),
                            # Store only first 3 events as samples (not all 1000+)
                            "sampleEvents": result.java_trace.get("events", [])[:3]
                        }
                    
                    all_divergences.append({
                        "query": result.query,
                        "type": div.type.value,
                        "severity": div.severity,
                        "description": div.description,
                        # Limit entity data to essential fields only
                        "java_data": {
                            "id": div.java_data.get("id") if div.java_data else None,
                            "name": div.java_data.get("name") if div.java_data else None,
                            "score": div.java_data.get("match") if div.java_data else None
                        },
                        "go_data": {
                            "id": div.go_data.get("id") if div.go_data else None,
                            "name": div.go_data.get("name") if div.go_data else None,
                            "score": div.go_data.get("match") if div.go_data else None
                        },
                        "external_data": {
                            "id": div.external_data.get("id") if div.external_data else None,
                            "name": div.external_data.get("name") if div.external_data else None,
                            "score": div.external_data.get("match") if div.external_data else None
                        } if div.external_data else None,
                        "score_difference": div.score_difference,
                        "agreement_pattern": div.agreement_pattern,
                        "java_trace": java_trace_summary  # Summary instead of full trace
                    })
        # 2-way comparison (Java vs Go) if external not enabled
        elif COMPARE_IMPLEMENTATIONS and result.java_results and result.go_results:
            divergences = analyzer.compare(result.java_results, result.go_results)
            if divergences:
                for div in divergences:
                    # Limit trace data to essential fields only
                    java_trace_summary = None
                    if result.java_trace:
                        java_trace_summary = {
                            "sessionId": result.java_trace.get("sessionId"),
                            "durationMs": result.java_trace.get("durationMs"),
                            "eventCount": len(result.java_trace.get("events", [])),
                            "metadata": result.java_trace.get("metadata"),
                            "sampleEvents": result.java_trace.get("events", [])[:3]
                        }
                    
                    all_divergences.append({
                        "query": result.query,
                        "type": div.type.value,
                        "severity": div.severity,
                        "description": div.description,
                        # Limit entity data to essential fields only
                        "java_data": {
                            "id": div.java_data.get("id") if div.java_data else None,
                            "name": div.java_data.get("name") if div.java_data else None,
                            "score": div.java_data.get("match") if div.java_data else None
                        },
                        "go_data": {
                            "id": div.go_data.get("id") if div.go_data else None,
                            "name": div.go_data.get("name") if div.go_data else None,
                            "score": div.go_data.get("match") if div.go_data else None
                        },
                        "score_difference": div.score_difference,
                        "java_trace": java_trace_summary
                    })
    
    print(f"✓ Found {len(all_divergences)} divergences")
    
    # Count by severity
    by_severity = {}
    for div in all_divergences:
        sev = div["severity"]
        by_severity[sev] = by_severity.get(sev, 0) + 1
    
    if by_severity:
        print(f"  By severity: {', '.join(f'{k}={v}' for k, v in sorted(by_severity.items()))}")
    
    # Count traces captured
    trace_count = sum(1 for div in all_divergences if div.get("java_trace"))
    if trace_count > 0:
        print(f"  ✓ Captured {trace_count} trace(s) for root cause analysis")
    
    # Step 5a: AI Analysis of divergences
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
        "test_queries": [{"query": tc.query, "variation": tc.variation_type} for tc in test_cases[:20]],
        "repair_results": {
            "enabled": False,
            "reason": "Pending integration",
            "action_plan_file": None,
            "code_analysis_file": None,
            "fix_proposal_file": None,
            "pr_results_file": None,
            "auto_fix_count": 0,
            "human_review_count": 0,
            "too_complex_count": 0,
            "prs_created": []
        }
    }
    
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with open(output_file, 'w') as f:
        json.dump(report, f, indent=2)
    
    print(f"✓ Report saved: {output_file}")
    
    # === STEP 8: Run Repair Pipeline (if divergences found and enabled) ===
    
    if len(all_divergences) > 0 and os.environ.get('REPAIR_PIPELINE_ENABLED', 'false').lower() == 'true':
        print(f"\n{'='*80}")
        print("STEP 8: Running Repair Pipeline")
        print('='*80)
        
        try:
            repair_results = run_repair_pipeline(output_file)
            report["repair_results"] = repair_results
            
            # Update report file with repair results
            with open(output_file, 'w') as f:
                json.dump(report, f, indent=2)
            
            print(f"✓ Repair pipeline complete")
            print(f"  Auto-fix: {repair_results['auto_fix_count']}")
            print(f"  Human review: {repair_results['human_review_count']}")
            print(f"  Too complex: {repair_results['too_complex_count']}")
            print(f"  PRs created: {len(repair_results['prs_created'])}")
            
        except Exception as e:
            print(f"⚠️  Repair pipeline failed: {e}")
            import traceback
            traceback.print_exc()
            report["repair_results"] = {
                "enabled": True,
                "error": str(e),
                "prs_created": [],
                "auto_fix_count": 0,
                "human_review_count": 0,
                "too_complex_count": 0
            }
            # Update report with error
            with open(output_file, 'w') as f:
                json.dump(report, f, indent=2)
    else:
        if len(all_divergences) == 0:
            report["repair_results"]["reason"] = "No divergences to repair"
            print(f"\n✓ No divergences found - repair pipeline skipped")
        else:
            report["repair_results"]["reason"] = "Repair pipeline disabled (set REPAIR_PIPELINE_ENABLED=true)"
            print(f"\n⚠️  Repair pipeline disabled. Set REPAIR_PIPELINE_ENABLED=true to enable.")
    
    # === STEP 9: Create GitHub Issue (if divergences found and enabled) ===
    
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
                from github import Github, Auth
                
                gh = Github(auth=Auth.Token(github_token))
                repo = gh.get_repo(github_repo)
                
                # Create issue with summary of divergences
                critical_count = report["results_summary"]["by_severity"].get("critical", 0)
                moderate_count = report["results_summary"]["by_severity"].get("moderate", 0)
                total_divergences = report["results_summary"]["total_divergences"]
                
                issue_title = f"Nemesis: {total_divergences} divergences found - {critical_count} critical ({today})"
                
                # Build issue body
                issue_body = f"""## Nemesis 1.0 Report - Automated Testing Results

**Date:** {report['run_date']}  
**Version:** {report['version']}  
**Queries Tested:** {report['configuration']['total_queries']} random samples  
**Strategy:** {report['configuration']['strategy']}  
**Total Divergences:** {total_divergences}

---

## 📊 Coverage Metrics

- **Entities Tested Today:** {report['coverage']['entities_tested_today']}
- **Cumulative Tested:** {report['coverage']['cumulative_tested']}
- **Coverage:** {report['coverage']['cumulative_coverage_pct']:.2f}% of {report['coverage']['total_entities']} total entities

---

## 🎯 Issues by Severity

"""
                
                severity_explanations = {
                    "critical": "🔴 **CRITICAL** - Large score differences (>0.5) or wrong top matches. Potential compliance risks.",
                    "moderate": "🟡 **MODERATE** - Score differences (0.05-0.5) that may affect matching quality."
                }
                
                for severity in ["critical", "moderate"]:
                    count = report["results_summary"]["by_severity"].get(severity, 0)
                    if count > 0:
                        issue_body += f"{severity_explanations[severity]}\n"
                        issue_body += f"- **Count:** {count} divergences\n\n"
                
                # Add AI analysis if available
                if report.get("ai_analysis"):
                    ai = report["ai_analysis"]
                    issue_body += f"\n## 🤖 AI Analysis\n\n"
                    issue_body += f"**Patterns Identified:** {ai['patterns_identified']}\n"
                    issue_body += f"**Summary:** {ai['summary']}\n\n"
                    
                    if ai.get("issues"):
                        issue_body += "### Identified Issues:\n\n"
                        for issue in ai["issues"]:
                            issue_body += f"**{issue['id']}** - {issue['category']} ({issue['priority']})\n"
                            issue_body += f"- {issue['description']}\n"
                            issue_body += f"- _Recommendation:_ {issue['recommendation']}\n\n"
                
                # Add divergence type breakdown
                divergence_types = {}
                for div in report.get("divergences", [])[:20]:  # Sample first 20
                    dtype = div.get("type", "unknown")
                    divergence_types[dtype] = divergence_types.get(dtype, 0) + 1
                
                issue_body += f"\n## 🔍 Divergence Types (sample)\n\n"
                
                type_explanations = {
                    "score_difference": "**Score Mismatch** - Same entity but different confidence scores between Java and Go"
                }
                
                for dtype, count in sorted(divergence_types.items(), key=lambda x: -x[1]):
                    explanation = type_explanations.get(dtype, dtype)
                    issue_body += f"- {explanation}: **{count} occurrences**\n"
                
                # Add repair pipeline results if available
                repair_results = report.get("repair_results", {})
                if repair_results.get("enabled") and repair_results.get("prs_created"):
                    prs = repair_results["prs_created"]
                    issue_body += f"\n---\n\n## 🔧 Automated Fixes\n\n"
                    issue_body += f"The repair pipeline has created **{len(prs)} pull request(s)**:\n\n"
                    
                    for i, pr in enumerate(prs, 1):
                        issue_id = pr.get("issue_id", "Unknown")
                        pr_url = pr.get("pr_url", "")
                        status_icon = "✅" if pr.get("status") == "success" else "⚠️"
                        
                        issue_body += f"{i}. {status_icon} [{issue_id}]({pr_url})\n"
                    
                    issue_body += f"\n**Repair Summary:**\n"
                    issue_body += f"- Auto-fix eligible: {repair_results.get('auto_fix_count', 0)}\n"
                    issue_body += f"- Needs human review: {repair_results.get('human_review_count', 0)}\n"
                    issue_body += f"- Too complex: {repair_results.get('too_complex_count', 0)}\n"
                    issue_body += f"\n💡 *Review PRs above before merging*\n"
                
                # Add sample divergences
                issue_body += f"\n---\n\n## 📋 Example Divergences (Top 10)\n\n"
                issue_body += f"*Sample showing patterns. Full {total_divergences} divergences in report file.*\n\n"
                
                for i, div in enumerate(report.get("divergences", [])[:10], 1):
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
                
                if total_divergences > 10:
                    issue_body += f"\n*... plus {total_divergences - 10} more divergences in the full report*\n"
                
                # Add footer with action items
                issue_body += f"\n---\n\n## 🔧 Recommended Actions\n\n"
                
                if critical_count > 0:
                    issue_body += f"1. **Priority 1:** Fix {critical_count} critical divergences (large score differences >0.5)\n"
                
                if moderate_count > 0:
                    issue_body += f"2. **Review:** Investigate {moderate_count} moderate divergences (score differences 0.05-0.5)\n"
                
                issue_body += f"\n## 📁 Full Report Location\n\n"
                issue_body += f"Complete technical details: `/scripts/reports/nemesis-{today}.json`\n\n"
                issue_body += f"```json\n"
                issue_body += f"{{\n"
                issue_body += f'  "total_divergences": {total_divergences},\n'
                issue_body += f'  "critical": {critical_count},\n'
                issue_body += f'  "moderate": {moderate_count},\n'
                issue_body += f'  "coverage_pct": {report["coverage"]["cumulative_coverage_pct"]:.2f},\n'
                issue_body += f'  "entities_tested": {report["coverage"]["cumulative_tested"]}\n'
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
