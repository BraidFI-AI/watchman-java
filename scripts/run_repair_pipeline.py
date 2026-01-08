#!/usr/bin/env python3
"""
Automated Repair Pipeline

Runs the complete repair agent workflow:
1. Classify divergences (repair_agent.py)
2. Analyze affected code (code_analyzer.py)
3. Generate fixes with AI (fix_generator.py)
4. Create GitHub PRs (fix_applicator.py)

Designed to run automatically after Nemesis.
"""

import os
import sys
import subprocess
import json
from pathlib import Path
from datetime import datetime


def run_command(cmd, description):
    """Run a command and return success status."""
    print(f"\n{'=' * 80}")
    print(f"Step: {description}")
    print(f"{'=' * 80}")
    
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            check=True,
            capture_output=True,
            text=True
        )
        print(result.stdout)
        if result.stderr:
            print(f"Warnings: {result.stderr}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ Error: {e}")
        print(f"stdout: {e.stdout}")
        print(f"stderr: {e.stderr}")
        return False


def main():
    """Run the complete repair pipeline."""
    print("=" * 80)
    print("AUTOMATED REPAIR PIPELINE")
    print("=" * 80)
    print(f"Started: {datetime.now().isoformat()}")
    
    # Check required environment variables
    required_vars = ['ANTHROPIC_API_KEY', 'GITHUB_TOKEN']
    missing = [var for var in required_vars if not os.getenv(var)]
    
    if missing:
        print(f"\n⚠️  Missing required environment variables: {', '.join(missing)}")
        print("Pipeline will skip steps requiring these variables.")
    
    # Find latest Nemesis report
    reports_dir = Path('/data/reports') if Path('/data/reports').exists() else Path('scripts/reports')
    
    nemesis_reports = sorted(reports_dir.glob('nemesis-*.json'), reverse=True)
    if not nemesis_reports:
        print("\n⚠️  No Nemesis reports found. Nothing to process.")
        return
    
    latest_report = nemesis_reports[0]
    print(f"\n📄 Processing: {latest_report}")
    
    # Check if report has divergences
    try:
        with open(latest_report) as f:
            report_data = json.load(f)
        
        divergence_count = len(report_data.get('divergences', []))
        if divergence_count == 0:
            print(f"\n✅ No divergences found in report. Nothing to fix!")
            return
        
        print(f"\n📊 Found {divergence_count} divergence(s) to analyze")
    except Exception as e:
        print(f"\n❌ Error reading report: {e}")
        return
    
    # Step 1: Classify divergences
    step1 = run_command(
        f"python3 scripts/nemesis/repair_agent.py {latest_report}",
        "1. Classify Divergences"
    )
    
    if not step1:
        print("\n❌ Pipeline stopped: Classification failed")
        return
    
    # Find action plan
    action_plans = sorted(reports_dir.glob('action-plan-*.json'), reverse=True)
    if not action_plans:
        print("\n❌ No action plan generated")
        return
    
    action_plan = action_plans[0]
    print(f"\n✓ Action plan: {action_plan}")
    
    # Step 2: Analyze code
    step2 = run_command(
        f"python3 scripts/nemesis/code_analyzer.py {action_plan}",
        "2. Analyze Affected Code"
    )
    
    if not step2:
        print("\n❌ Pipeline stopped: Code analysis failed")
        return
    
    # Find code analysis
    code_analyses = sorted(reports_dir.glob('code-analysis-*.json'), reverse=True)
    if not code_analyses:
        print("\n❌ No code analysis generated")
        return
    
    code_analysis = code_analyses[0]
    print(f"\n✓ Code analysis: {code_analysis}")
    
    # Step 3: Generate fixes (requires ANTHROPIC_API_KEY)
    if not os.getenv('ANTHROPIC_API_KEY'):
        print("\n⚠️  Skipping fix generation: ANTHROPIC_API_KEY not set")
        print("Pipeline complete (analysis only)")
        return
    
    step3 = run_command(
        f"python3 scripts/nemesis/fix_generator.py {code_analysis}",
        "3. Generate Fixes with AI"
    )
    
    if not step3:
        print("\n❌ Pipeline stopped: Fix generation failed")
        return
    
    # Find fix proposal
    fix_proposals = sorted(reports_dir.glob('fix-proposal-*.json'), reverse=True)
    if not fix_proposals:
        print("\n❌ No fix proposal generated")
        return
    
    fix_proposal = fix_proposals[0]
    print(f"\n✓ Fix proposal: {fix_proposal}")
    
    # Step 4: Create PRs (requires GITHUB_TOKEN)
    if not os.getenv('GITHUB_TOKEN'):
        print("\n⚠️  Skipping PR creation: GITHUB_TOKEN not set")
        print("Pipeline complete (fixes generated, manual PR creation needed)")
        return
    
    step4 = run_command(
        f"python3 scripts/nemesis/fix_applicator.py {fix_proposal}",
        "4. Create GitHub PRs"
    )
    
    if not step4:
        print("\n❌ Pipeline stopped: PR creation failed")
        return
    
    # Success!
    print("\n" + "=" * 80)
    print("✅ PIPELINE COMPLETE")
    print("=" * 80)
    print(f"Finished: {datetime.now().isoformat()}")
    print("\nGitHub PRs created. Review and merge on GitHub.")


if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        print(f"\n❌ Pipeline error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
