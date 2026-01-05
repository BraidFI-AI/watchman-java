"""
GitHub integration for creating issues and discussions from agent reports.
"""

import json
import requests
from datetime import datetime
from agent_config import GITHUB_TOKEN, GITHUB_REPO, CREATE_GITHUB_ISSUES


def create_issue(title, body, labels=None):
    """Create a GitHub issue."""
    if not CREATE_GITHUB_ISSUES or not GITHUB_TOKEN:
        print("  ⚠️  GitHub integration disabled (no token or CREATE_GITHUB_ISSUES=false)")
        return None
    
    url = f"https://api.github.com/repos/{GITHUB_REPO}/issues"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    payload = {
        "title": title,
        "body": body,
        "labels": labels or []
    }
    
    try:
        response = requests.post(url, headers=headers, json=payload)
        if response.status_code == 201:
            issue_url = response.json()["html_url"]
            print(f"  ✓ Created GitHub issue: {issue_url}")
            return issue_url
        else:
            print(f"  ✗ Failed to create issue: {response.status_code}")
            print(f"    {response.text}")
            return None
    except Exception as e:
        print(f"  ✗ Error creating issue: {e}")
        return None


def format_nemesis_issue(data, report_file):
    """Format Nemesis report as GitHub issue."""
    total = data.get('total_issues_found', 0)
    divergences = data.get('divergence_issues', 0)
    by_priority = data.get('by_priority', {})
    
    title = f"Nemesis Report: {total} issues found ({divergences} Go/Java divergences)"
    
    # Build body
    body = f"""## Nemesis Agent Report
**Date:** {data.get('analysis_date', 'Unknown')}
**Report:** `{report_file}`

### Summary
- **Total Issues:** {total}
- **Go/Java Divergences:** {divergences}
- **Critical (P0):** {by_priority.get('P0', 0)}
- **High (P1):** {by_priority.get('P1', 0)}
- **Medium (P2):** {by_priority.get('P2', 0)}
- **Low (P3):** {by_priority.get('P3', 0)}

"""
    
    # Add top critical issues
    issues = data.get('issues', [])
    critical = [i for i in issues if i.get('priority') in ['P0', 'P1']]
    
    if critical:
        body += f"### Top {min(10, len(critical))} Critical Issues\n\n"
        for issue in critical[:10]:
            body += f"#### [{issue['id']}] {issue['title']} ({issue['priority']})\n"
            body += f"**Category:** {issue.get('category', 'Unknown')}\n\n"
            
            if 'test_case' in issue:
                tc = issue['test_case']
                body += f"**Test Case:**\n"
                body += f"- Query: `{tc.get('query', 'N/A')}`\n"
                
                if 'java_top_result' in tc and 'go_top_result' in tc:
                    body += f"- Java: `{tc['java_top_result']}` (score: {tc.get('java_score', 0):.3f})\n"
                    body += f"- Go: `{tc['go_top_result']}` (score: {tc.get('go_score', 0):.3f})\n"
                    body += f"- **Divergence:** {tc.get('divergence_magnitude', 0):.3f}\n"
            
            body += f"\n**Root Cause:** {issue.get('root_cause', 'Unknown')}\n"
            body += f"\n**Fix:** {issue.get('actionable_fix', 'Unknown')}\n"
            body += f"\n---\n\n"
    
    body += f"\n### Next Steps\n"
    body += f"1. Review full report: `{report_file}`\n"
    body += f"2. Run Strategic Analyzer to create fix roadmap\n"
    body += f"3. Prioritize P0/P1 divergences for immediate fixing\n"
    body += f"\n**Full report available on Fly VM at:** `/data/reports/{report_file.split('/')[-1]}`"
    
    return title, body


def format_roadmap_issue(data, report_file):
    """Format Strategic Analyzer roadmap as GitHub issue."""
    summary = data.get('executive_summary', {})
    total = summary.get('total_issues', 0)
    critical = summary.get('critical_issues', 0)
    
    title = f"Sprint Planning: {total} issues analyzed, {critical} critical"
    
    body = f"""## Strategic Analyzer Roadmap
**Date:** {data.get('analysis_date', 'Unknown')}
**Report:** `{report_file}`

### Executive Summary
- **Total Issues Analyzed:** {total}
- **Critical Issues:** {critical}
- **Pass Rate Goal:** Achieve Go/Java parity

"""
    
    # Sprint 1 recommendations
    sprint1_issues = summary.get('recommended_sprint_1', [])
    if sprint1_issues:
        body += f"### 🎯 Recommended for Sprint 1\n\n"
        body += f"**Issues:** {', '.join(sprint1_issues)}\n"
        body += f"**Expected Impact:** {summary.get('estimated_sprint_1_impact', 'N/A')}\n\n"
        
        # Get details for each issue
        issue_analysis = data.get('issue_analysis', [])
        for issue_id in sprint1_issues[:5]:  # Top 5
            issue = next((i for i in issue_analysis if i['id'] == issue_id), None)
            if issue:
                body += f"#### {issue_id}: {issue.get('decision', 'Unknown')}\n"
                body += f"- **Rationale:** {issue.get('rationale', 'N/A')}\n"
                body += f"- **Effort:** {issue.get('effort_hours', 'N/A')} hours\n"
                body += f"- **Impact:** {issue.get('impact_queries_per_day', 'N/A')} queries/day\n"
                body += f"- **ROI:** {issue.get('roi', 'N/A')}\n"
                body += f"- **Approach:** {issue.get('approach', 'N/A')}\n\n"
    
    # Quick wins
    quick_wins = summary.get('quick_wins', [])
    if quick_wins:
        body += f"### ⚡ Quick Wins\n"
        body += f"{', '.join(quick_wins)}\n\n"
    
    # Fix themes
    themes = data.get('fix_themes', [])
    if themes:
        body += f"### 🎨 Fix Themes\n\n"
        for theme in themes[:3]:  # Top 3 themes
            body += f"#### {theme['theme']}\n"
            body += f"- **Priority:** {theme.get('combined_priority', 'Unknown')}\n"
            body += f"- **Effort:** {theme.get('effort_estimate_hours', 'Unknown')} hours\n"
            body += f"- **ROI Score:** {theme.get('roi_score', 'N/A')}\n"
            body += f"- **Approach:** {theme.get('approach', 'N/A')}\n"
            body += f"- **Issues:** {', '.join(theme.get('issues', []))}\n\n"
    
    body += f"\n### 📋 Action Items\n"
    body += f"1. Review full roadmap: `{report_file}`\n"
    body += f"2. Assign Sprint 1 issues to developers\n"
    body += f"3. Create individual issues for each recommended fix\n"
    body += f"4. After fixes, re-run Nemesis to validate\n"
    body += f"\n**Full roadmap available on Fly VM at:** `/data/reports/{report_file.split('/')[-1]}`"
    
    return title, body


def create_nemesis_issue(data, report_file):
    """Create GitHub issue from Nemesis report (only if divergences or critical issues found)."""
    # Only create issue if there are divergences or critical issues
    divergences = data.get('divergence_issues', 0)
    by_priority = data.get('by_priority', {})
    critical_count = by_priority.get('P0', 0) + by_priority.get('P1', 0)
    
    if divergences == 0 and critical_count == 0:
        print("  ℹ️  No divergences or critical issues - skipping GitHub issue creation")
        return None
    
    title, body = format_nemesis_issue(data, report_file)
    labels = ["nemesis", "search-quality", "automated"]
    
    # Add priority labels (only if critical issues recommended)."""
    summary = data.get('executive_summary', {})
    critical = summary.get('critical_issues', 0)
    sprint1_issues = summary.get('recommended_sprint_1', [])
    
    # Only create issue if there are actionable recommendations
    if critical == 0 and len(sprint1_issues) == 0:
        print("  ℹ️  No critical issues or Sprint 1 recommendations - skipping GitHub issue creation")
        return None
    
    if by_priority.get('P0', 0) > 0:
        labels.append("priority:critical")
    
    return create_issue(title, body, labels)


def create_roadmap_issue(data, report_file):
    """Create GitHub issue from Strategic Analyzer roadmap."""
    title, body = format_roadmap_issue(data, report_file)
    labels = ["strategic-analyzer", "sprint-planning", "automated"]
    
    return create_issue(title, body, labels)


if __name__ == "__main__":
    print("GitHub Integration Configuration:")
    print(f"  Repo: {GITHUB_REPO}")
    print(f"  Token Set: {'Yes' if GITHUB_TOKEN else 'No'}")
    print(f"  Create Issues: {CREATE_GITHUB_ISSUES}")
    
    if not GITHUB_TOKEN:
        print("\n⚠️  Set GITHUB_TOKEN environment variable to enable issue creation")
        print("   export GITHUB_TOKEN=ghp_your_token_here")
