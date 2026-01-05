#!/usr/bin/env python3
"""
Strategic Analyzer Agent - Decision Support
Triages Nemesis findings and creates fix roadmap.
"""

import json
import sys
import argparse
from datetime import datetime
from pathlib import Path

# Import configuration
from agent_config import (
    get_ai_client, validate_config, AI_PROVIDER, AI_MODEL,
    REPORT_DIR, COMPARE_IMPLEMENTATIONS, GO_IS_BASELINE, CREATE_GITHUB_ISSUES
)
from github_integration import create_roadmap_issue

STRATEGIC_ANALYZER_PROMPT = """You are a strategic technical product manager analyzing search algorithm issues.

Your task:
1. Group related issues into logical "fix themes"
2. Estimate effort (hours) for each fix
3. Estimate impact (# queries improved)
4. Calculate ROI (impact / effort)
5. Recommend fix order and approach
6. Identify "quick wins" vs "strategic investments"

Input: List of {total} issues found by adversarial testing agent "Nemesis"

Nemesis Report:
{nemesis_data}

Additional context:
- Team size: 1-2 developers
- Sprint length: 2 weeks
- Current test coverage: ~70%
- Production query volume: ~1000/day
- Algorithm: Java implementation with Jaro-Winkler and phonetic filtering
- IMPORTANT: Java implementation must achieve parity with Go implementation (baseline)
- Prioritize fixes that align Java behavior with Go behavior

Output ONLY valid JSON in this exact format:
{{
  "analysis_date": "{today}",
  "nemesis_report_analyzed": "{nemesis_file}",
  "executive_summary": {{
    "total_issues": {total},
    "critical_issues": 0,
    "recommended_sprint_1": ["NEM-001", "NEM-003"],
    "estimated_sprint_1_impact": "30% improvement in cross-language queries",
    "quick_wins": ["NEM-007"],
    "defer_to_later": ["NEM-042"]
  }},
  "fix_themes": [
    {{
      "theme": "Cross-Language False Positives",
      "issues": ["NEM-001", "NEM-002"],
      "combined_priority": "P1",
      "total_affected_queries": "~150/day",
      "effort_estimate_hours": 16,
      "approach": "Add language detection and cross-script penalty",
      "roi_score": 9.4,,
      "go_java_parity": "Fixes divergence - aligns with Go behavior"
      "dependencies": "None",
      "risks": "May reduce recall for legitimate transliteration matches"
    }}
  ],
  "sprint_roadmap": [
    {{
      "sprint": 1,
      "issues": ["NEM-001", "NEM-003"],
      "theme": "Cross-Language False Positives",
      "effort_hours": 16,
      "impact": "High",
      "rationale": "Highest ROI, solves critical user-reported issue"
    }}
  ],
  "issue_analysis": [
    {{
      "id": "NEM-001",
      "decision": "Fix in Sprint 1",
      "rationale": "High impact, clear root cause, straightforward fix",
      "effort_hours": 8,
      "impact_queries_per_day": 150,
      "roi": 9.4,
      "approach": "Add language detection to PhoneticFilter",
      "test_strategy": "Add 20 cross-language test cases",
      "rollout": "Canary deploy, monitor for 48h"
    }}
  ],
  "defer_recommendations": [
    {{
      "id": "NEM-042",
      "reason": "P3 priority, affects <5 queries/month, complex fix (24h effort)",
      "revisit_after": "Sprint 3 or when P1/P2 issues resolved"
    }}
  ]
}}
"""


def find_latest_nemesis_report():
    """Find the most recent Nemesis report."""
    report_dir = Path(REPORT_DIR)
    if not report_dir.exists():
        return None
    
    nemesis_files = list(report_dir.glob("nemesis-*.json"))
    if not nemesis_files:
        return None
    
    # Sort by filename (date) and get latest
    return sorted(nemesis_files)[-1]


def load_nemesis_report(file_path):
    """Load Nemesis report from file."""
    print(f"Loading Nemesis report: {file_path}")
    
    try:
        with open(file_path, 'r') as f:
            data = json.load(f)
        
        issue_count = len(data.get('issues', []))
        print(f"  ✓ Loaded {issue_count} issues")
        return data
    
    except Exception as e:
        print(f"  ✗ Failed to load report: {e}")
        return None


def call_ai_for_analysis(nemesis_data, nemesis_file):
    """Call AI model to analyze Nemesis findings."""
    print(f"\nCalling {AI_PROVIDER} ({AI_MODEL}) for strategic analysis...")
    
    today = datetime.now().strftime("%Y-%m-%d")
    total_issues = len(nemesis_data.get('issues', []))
    
    prompt = STRATEGIC_ANALYZER_PROMPT.format(
        total=total_issues,
        nemesis_data=json.dumps(nemesis_data, indent=2),
        today=today,
        nemesis_file=str(nemesis_file)
    )
    
    client = get_ai_client()
    
    if AI_PROVIDER == 'anthropic':
        response = client.messages.create(
            model=AI_MODEL,
            max_tokens=8000,
            messages=[{"role": "user", "content": prompt}]
        )
        return response.content[0].text
    
    elif AI_PROVIDER in ['openai', 'ollama']:
        response = client.chat.completions.create(
            model=AI_MODEL,
            messages=[{"role": "user", "content": prompt}],
            max_tokens=4000  # OpenAI limit for gpt-4-turbo
        )
        return response.choices[0].message.content
    
    else:
        raise ValueError(f"Unsupported provider: {AI_PROVIDER}")



def parse_and_save_results(ai_response, output_file):
    """Parse AI response and save to file."""
    print("\nParsing strategic analysis...")
    
    try:
        # Extract JSON from response
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
        required_fields = ['executive_summary', 'fix_themes', 'sprint_roadmap', 'issue_analysis']
        for field in required_fields:
            if field not in data:
                print(f"  ⚠ Warning: Missing field '{field}'")
        
        # Save to file
        with open(output_file, 'w') as f:
            json.dump(data, f, indent=2)
        
        print(f"  ✓ Saved to {output_file}")
        
        return data
    
    except json.JSONDecodeError as e:
        print(f"  ✗ Failed to parse JSON: {e}")
        
        # Save raw response
        error_file = output_file.replace('.json', '_raw.txt')
        with open(error_file, 'w') as f:
            f.write(ai_response)
        print(f"  ✓ Saved raw response to {error_file}")
        
        return None


def print_roadmap(data):
    """Print strategic roadmap summary."""
    if not data:
        return
    
    print("\n" + "="*60)
    print("STRATEGIC ANALYSIS - FIX ROADMAP")
    print("="*60)
    
    summary = data.get('executive_summary', {})
    
    print(f"\nTotal Issues Analyzed: {summary.get('total_issues', 0)}")
    print(f"Critical Issues: {summary.get('critical_issues', 0)}")
    
    # Sprint 1 recommendations
    sprint1 = summary.get('recommended_sprint_1', [])
    if sprint1:
        print(f"\n📋 Recommended for Sprint 1: {len(sprint1)} issues")
        for issue_id in sprint1:
            issue = next((i for i in data.get('issue_analysis', []) if i['id'] == issue_id), None)
            if issue:
                print(f"  • {issue_id}: {issue.get('rationale', '')}")
        print(f"  Impact: {summary.get('estimated_sprint_1_impact', 'N/A')}")
    
    # Quick wins
    quick_wins = summary.get('quick_wins', [])
    if quick_wins:
        print(f"\n⚡ Quick Wins: {len(quick_wins)} issues")
        for issue_id in quick_wins:
            print(f"  • {issue_id}")
    
    # Fix themes
    themes = data.get('fix_themes', [])
    if themes:
        print(f"\n🎯 Fix Themes ({len(themes)} total):")
        for theme in themes[:5]:  # Show top 5
            print(f"\n  {theme['theme']}")
            print(f"    Priority: {theme['combined_priority']}")
            print(f"    Effort: {theme['effort_estimate_hours']}h")
            print(f"    ROI: {theme.get('roi_score', 'N/A')}")
            print(f"    Issues: {', '.join(theme['issues'])}")
    
    # Deferred
    deferred = data.get('defer_recommendations', [])
    if deferred:
        print(f"\n⏸  Deferred: {len(deferred)} issues")


def main():
    """Main execution."""
    parser = argparse.ArgumentParser(description='Strategic Analyzer - Triage Nemesis findings')
    parser.add_argument('--input', help='Path to Nemesis report (default: latest)')
    parser.add_argument('--latest', action='store_true', help='Use latest Nemesis report')
    args = parser.parse_args()
    
    print("="*60)
    print("STRATEGIC ANALYZER - Decision Support")
    print("="*60)
    
    try:
        validate_config()
    except ValueError as e:
        print(f"\n✗ Configuration error:\n{e}")
        return 1
    
    # Find input file
    if args.input:
        nemesis_file = Path(args.input)
    else:
        nemesis_file = find_latest_nemesis_report()
    
    if not nemesis_file or not nemesis_file.exists():
        print(f"\n✗ No Nemesis report found. Run run-nemesis.py first.")
        return 1
    
    # Load Nemesis data
    nemesis_data = load_nemesis_report(nemesis_file)
    if not nemesis_data:
        return 1
    
    # Create output file path
    today = datetime.now().strftime("%Y%m%d")
    output_file = Path(REPORT_DIR) / f"fix-roadmap-{today}.json"
    print(f"\nOutput: {output_file}")
    
    # Call AI for analysis
    try:
        ai_response = call_ai_for_analysis(nemesis_data, nemesis_file)
    except Exception as e:
        print(f"\n✗ AI call failed: {e}")
        return 1
    
    # Parse and save
    data = parse_and_save_results(ai_response, output_file)
    
    # Print roadmap
    print_roadmap(data)
    # Create GitHub issue
    if data and CREATE_GITHUB_ISSUES:
        print("\nCreating GitHub issue...")
        create_roadmap_issue(data, str(output_file))
    
    
    if data:
        print(f"\n✓ Strategic analysis complete! Roadmap saved to {output_file}")
        return 0
    else:
        print(f"\n✗ Strategic analysis failed")
        return 1


if __name__ == "__main__":
    sys.exit(main())
