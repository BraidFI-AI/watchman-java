"""
GitHub Integration for Watchman-Java
Creates and manages GitHub issues for:
- Nemesis automated testing reports (ALWAYS creates as proposal package)
- Strategic Analyzer roadmaps
- Repair pipeline results
"""

import os
import sys
import json
from pathlib import Path
from datetime import datetime
import requests

# Add parent directory to path for agent_config import
sys.path.insert(0, str(Path(__file__).parent))
from agent_config import GITHUB_TOKEN, GITHUB_REPO


def format_nemesis_issue(data, report_file):
    """
    Format Nemesis report as GitHub issue markdown.
    
    Args:
        data: Report dict from run_nemesis.py with structure:
              {"results_summary": {"total_divergences": N, "by_severity": {...}}, 
               "coverage": {...}, 
               "repair_results": {"prs_created": [...]}}
        report_file: Path to JSON report file
        
    Returns:
        Tuple of (title, body) strings
    """
    # Extract summary data
    results_summary = data.get('results_summary', {})
    total_divergences = results_summary.get('total_divergences', 0)
    by_severity = results_summary.get('by_severity', {})
    critical_count = by_severity.get('critical', 0)
    moderate_count = by_severity.get('moderate', 0)
    
    repair_results = data.get('repair_results', {})
    analysis_date = data.get('metadata', {}).get('timestamp', 'N/A')
    
    # Create title
    if total_divergences == 0:
        title = f"✅ Nemesis Report - Clean Run ({analysis_date})"
    else:
        title = f"🔍 Nemesis Report - {total_divergences} Divergences Found ({analysis_date})"
    
    # Create body
    body = f"# Nemesis Automated Testing Report\n\n"
    body += f"**Analysis Date:** {analysis_date}\n"
    body += f"**Report File:** `{report_file}`\n\n"
    body += f"---\n\n"
    
    if total_divergences == 0:
        body += f"## ✅ Clean Run\n\n"
        body += f"No divergences detected between Java and Go implementations.\n\n"
        body += f"### Coverage\n"
        body += f"- **Queries Tested:** {data.get('coverage', {}).get('total_queries_tested', 0)}\n"
        body += f"- **Cumulative Coverage:** {data.get('coverage', {}).get('cumulative_coverage_pct', 0):.2f}%\n"
    else:
        body += f"## 📊 Summary\n\n"
        body += f"- **Total Divergences:** {total_divergences}\n"
        body += f"- **Critical:** {critical_count} (score differences > 0.5)\n"
        body += f"- **Moderate:** {moderate_count} (score differences 0.05-0.5)\n"
        body += f"- **Coverage:** {data.get('coverage', {}).get('cumulative_coverage_pct', 0):.2f}%\n\n"
        
        # Severity explanations
        if critical_count > 0 or moderate_count > 0:
            body += f"### Severity Breakdown\n\n"
            
            severity_explanations = {
                "critical": "🔴 **CRITICAL** - Score differences > 0.5, likely causing wrong matches.",
                "moderate": "🟡 **MODERATE** - Score differences (0.05-0.5) that may affect matching quality."
            }
            
            for severity in ["critical", "moderate"]:
                count = by_severity.get(severity, 0)
                if count > 0:
                    body += f"{severity_explanations[severity]}\n"
                    body += f"- **Count:** {count} divergences\n\n"
    
    # Footer with JSON summary
    body += f"\n---\n\n## 📁 Full Report\n\n"
    
    # Upload report and add link
    gist_url = upload_report_as_gist(report_file, f"Nemesis Report - {analysis_date}")
    if gist_url:
        body += f"**[📄 View Full Report on GitHub]({gist_url})**\n\n"
    else:
        body += f"Complete technical details: `{report_file}`\n\n"
    
    body += f"\n```json\n"
    body += f"{{\n"
    body += f'  "total_divergences": {total_divergences},\n'
    body += f'  "critical": {critical_count},\n'
    body += f'  "moderate": {moderate_count},\n'
    body += f'  "coverage_pct": {data.get("coverage", {}).get("cumulative_coverage_pct", 0):.2f}\n'
    body += f"}}\n"
    body += f"```\n"
    
    # Add PR links if repair results exist
    if repair_results and repair_results.get("prs_created"):
        prs = repair_results["prs_created"]
        body += f"\n---\n\n## 🔧 Automated Fixes\n\n"
        body += f"The repair pipeline has created **{len(prs)} pull request(s)**:\n\n"
        
        for i, pr in enumerate(prs, 1):
            issue_id = pr.get("issue_id", "Unknown")
            pr_url = pr.get("pr_url", "")
            status_icon = "✅" if pr.get("status") == "success" else "⚠️"
            
            body += f"{i}. {status_icon} [{issue_id}]({pr_url})\n"
        
        body += f"\n**Repair Summary:**\n"
        body += f"- Auto-fix eligible: {repair_results.get('auto_fix_count', 0)}\n"
        body += f"- Needs human review: {repair_results.get('human_review_count', 0)}\n"
        body += f"- Too complex: {repair_results.get('too_complex_count', 0)}\n"
        body += f"\n💡 *Review PRs above before merging*\n\n"
    
    body += f"\n### Next Steps\n"
    body += f"1. Review full report: `{report_file}`\n"
    body += f"2. Approve/merge automated fix PRs (if any)\n"
    body += f"3. Investigate remaining divergences\n"
    
    return title, body


def upload_report_as_gist(report_file, description="Nemesis Test Report"):
    """Upload report file as a public GitHub Gist."""
    if not GITHUB_TOKEN:
        return None
    
    try:
        with open(report_file, 'r') as f:
            content = f.read()
        
        filename = Path(report_file).name
        
        payload = {
            "description": description,
            "public": False,  # Private gist
            "files": {
                filename: {
                    "content": content
                }
            }
        }
        
        response = requests.post(
            "https://api.github.com/gists",
            headers={
                "Authorization": f"Bearer {GITHUB_TOKEN}",
                "Accept": "application/vnd.github.v3+json"
            },
            json=payload
        )
        
        if response.status_code == 201:
            gist_url = response.json()["html_url"]
            print(f"  ✓ Uploaded report to: {gist_url}")
            return gist_url
        else:
            print(f"  ✗ Failed to upload report: {response.status_code}")
            return None
    except Exception as e:
        print(f"  ✗ Error uploading report: {e}")
        return None


def create_issue(title, body, labels=None):
    """Create a GitHub issue using GitHub API."""
    if not GITHUB_TOKEN:
        print("  ⚠️  GitHub integration disabled (no GITHUB_TOKEN)")
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


def create_nemesis_issue(data, report_file):
    """
    Create GitHub issue from Nemesis report (ALWAYS creates issue as proposal package).
    
    Args:
        data: Report dict from run_nemesis.py
        report_file: Path to the JSON report file
    
    Returns:
        GitHub issue URL or None if failed
    """
    # Always create issue - serves as proposal package for human review
    total_divergences = data.get('results_summary', {}).get('total_divergences', 0)
    by_severity = data.get('results_summary', {}).get('by_severity', {})
    
    title, body = format_nemesis_issue(data, report_file)
    labels = ["nemesis", "automated-testing"]
    
    # Add priority labels based on severity
    if by_severity.get('critical', 0) > 0:
        labels.append("priority:critical")
    elif total_divergences == 0:
        labels.append("status:clean")
    
    return create_issue(title, body, labels)


# Module exports
__all__ = ['create_issue', 'create_nemesis_issue', 'format_nemesis_issue', 'upload_report_as_gist']
