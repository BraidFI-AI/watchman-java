#!/usr/bin/env python3
"""
Fix Applicator for Nemesis Repair Agent

Creates GitHub PRs with generated code fixes for human review.
Phase 2: Automated PR creation with approval workflow.
"""

import json
import os
import sys
import subprocess
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime
from github import Github, GithubException


class FixApplicator:
    """Applies generated fixes by creating GitHub PRs."""
    
    def __init__(self, project_root: str = ".", dry_run: bool = False):
        self.project_root = Path(project_root).resolve()
        self.dry_run = dry_run
        
        # Initialize GitHub client
        github_token = os.getenv('GITHUB_TOKEN')
        if not github_token:
            raise ValueError("GITHUB_TOKEN environment variable required")
        
        self.github = Github(github_token)
        
        # Get repo from env or config
        repo_name = os.getenv('GITHUB_REPO', 'BraidFI-AI/watchman-java')
        self.repo = self.github.get_repo(repo_name)
        
        print(f"📦 Repository: {repo_name}")
        print(f"🔧 Mode: {'DRY RUN' if dry_run else 'LIVE'}")
    
    def apply_fix(self, fix: Dict) -> Dict:
        """
        Apply a single fix by creating a GitHub PR.
        
        Args:
            fix: Fix data from fix_generator.py
        
        Returns:
            Result dict with PR URL and status
        """
        issue_id = fix['issue_id']
        proposal = fix['proposal']
        changes = proposal['changes']
        
        print(f"\n{'=' * 80}")
        print(f"Applying fix for {issue_id}")
        print(f"{'=' * 80}")
        
        # Validate fix first
        if not self._validate_fix(fix):
            return {
                'issue_id': issue_id,
                'status': 'error',
                'error': 'Fix validation failed'
            }
        
        # Create branch
        branch_name = self._create_branch(issue_id)
        if not branch_name:
            return {
                'issue_id': issue_id,
                'status': 'error',
                'error': 'Failed to create branch'
            }
        
        # Apply changes to files
        applied = self._apply_changes(changes, branch_name)
        if not applied:
            return {
                'issue_id': issue_id,
                'status': 'error',
                'error': 'Failed to apply changes'
            }
        
        # Create PR
        pr_url = self._create_pull_request(fix, branch_name)
        if not pr_url:
            return {
                'issue_id': issue_id,
                'status': 'error',
                'error': 'Failed to create PR'
            }
        
        return {
            'issue_id': issue_id,
            'status': 'success',
            'branch': branch_name,
            'pr_url': pr_url
        }
    
    def _validate_fix(self, fix: Dict) -> bool:
        """Validate fix before applying."""
        proposal = fix['proposal']
        validation = proposal['validation']
        
        if not validation['valid']:
            print(f"   ❌ Validation failed: {', '.join(validation['errors'])}")
            return False
        
        if validation['warnings']:
            print(f"   ⚠️  Warnings: {', '.join(validation['warnings'])}")
        
        changes = proposal['changes']
        if not changes:
            print("   ❌ No changes to apply")
            return False
        
        print(f"   ✓ Validation passed ({len(changes)} file(s))")
        return True
    
    def _create_branch(self, issue_id: str) -> Optional[str]:
        """Create a new git branch for this fix."""
        branch_name = f"nemesis/{issue_id.lower()}/{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        
        print(f"\n📝 Creating branch: {branch_name}")
        
        if self.dry_run:
            print("   [DRY RUN] Would create branch")
            return branch_name
        
        try:
            # Get main branch
            main_branch = self.repo.get_branch("main")
            base_sha = main_branch.commit.sha
            
            # Create new branch reference
            ref = f"refs/heads/{branch_name}"
            self.repo.create_git_ref(ref=ref, sha=base_sha)
            
            print(f"   ✓ Branch created")
            return branch_name
            
        except GithubException as e:
            print(f"   ❌ GitHub error: {e}")
            return None
        except Exception as e:
            print(f"   ❌ Error: {e}")
            return None
    
    def _apply_changes(self, changes: List[Dict], branch_name: str) -> bool:
        """Apply code changes to files in the branch."""
        print(f"\n📝 Applying {len(changes)} change(s)...")
        
        if self.dry_run:
            for change in changes:
                print(f"   [DRY RUN] Would update: {change['file']}")
            return True
        
        try:
            for change in changes:
                file_path = change['file']
                new_content = change['new_content']
                explanation = change['explanation']
                
                print(f"   Updating: {file_path}")
                print(f"   Reason: {explanation}")
                
                # Get current file (to get SHA)
                try:
                    file_obj = self.repo.get_contents(file_path, ref=branch_name)
                    
                    # Update file
                    commit_message = f"Fix: {explanation}"
                    self.repo.update_file(
                        path=file_path,
                        message=commit_message,
                        content=new_content,
                        sha=file_obj.sha,
                        branch=branch_name
                    )
                    
                    print(f"   ✓ Updated successfully")
                    
                except GithubException as e:
                    if e.status == 404:
                        # File doesn't exist, create it
                        commit_message = f"Create: {explanation}"
                        self.repo.create_file(
                            path=file_path,
                            message=commit_message,
                            content=new_content,
                            branch=branch_name
                        )
                        print(f"   ✓ Created successfully")
                    else:
                        raise
            
            return True
            
        except Exception as e:
            print(f"   ❌ Error applying changes: {e}")
            import traceback
            traceback.print_exc()
            return False
    
    def _create_pull_request(self, fix: Dict, branch_name: str) -> Optional[str]:
        """Create a GitHub Pull Request for this fix."""
        issue_id = fix['issue_id']
        proposal = fix['proposal']
        analysis = fix.get('analysis', {})
        
        print(f"\n📬 Creating Pull Request...")
        
        # Build PR title
        title = f"[Nemesis] Fix {issue_id}"
        
        # Build PR body
        body = self._build_pr_body(fix)
        
        if self.dry_run:
            print("   [DRY RUN] Would create PR:")
            print(f"   Title: {title}")
            print(f"   Base: main")
            print(f"   Head: {branch_name}")
            print(f"   Body preview:\n{body[:200]}...")
            return f"https://github.com/{self.repo.full_name}/pull/dry-run"
        
        try:
            pr = self.repo.create_pull(
                title=title,
                body=body,
                head=branch_name,
                base="main",
                draft=False
            )
            
            # Add labels
            labels = self._get_labels(fix)
            if labels:
                pr.add_to_labels(*labels)
            
            # Request review (optional - configure reviewers)
            reviewers = self._get_reviewers()
            if reviewers:
                pr.create_review_request(reviewers=reviewers)
            
            print(f"   ✓ PR created: {pr.html_url}")
            return pr.html_url
            
        except Exception as e:
            print(f"   ❌ Error creating PR: {e}")
            import traceback
            traceback.print_exc()
            return None
    
    def _build_pr_body(self, fix: Dict) -> str:
        """Build comprehensive PR description."""
        issue_id = fix['issue_id']
        proposal = fix['proposal']
        analysis = fix.get('analysis', {})
        changes = proposal['changes']
        validation = proposal['validation']
        
        body = f"""## Nemesis Auto-Fix: {issue_id}

### Summary
{proposal['summary']}

### Code Analysis
- **Files affected:** {len(analysis.get('affected_files', []))}
- **Test coverage:** {analysis.get('test_coverage', 0):.1f}%
- **Blast radius:** {analysis.get('blast_radius', 'unknown')}
- **Confidence:** {analysis.get('confidence', 0):.0%}

### Changes

"""
        
        for change in changes:
            body += f"#### {change['file']}\n"
            body += f"**Reason:** {change['explanation']}\n\n"
        
        # Add validation info
        body += "### Validation\n\n"
        if validation['valid']:
            body += "✅ **Validation passed**\n\n"
        else:
            body += "❌ **Validation failed**\n\n"
        
        if validation['errors']:
            body += "**Errors:**\n"
            for error in validation['errors']:
                body += f"- {error}\n"
            body += "\n"
        
        if validation['warnings']:
            body += "**Warnings:**\n"
            for warning in validation['warnings']:
                body += f"- {warning}\n"
            body += "\n"
        
        # Add review checklist
        body += """### Review Checklist

- [ ] Code changes are correct and follow best practices
- [ ] Tests pass locally
- [ ] No regressions introduced
- [ ] Documentation updated if needed

### Testing

```bash
# Run tests locally
mvn test

# Test specific classes
mvn test -Dtest=EntityScorerTest
```

---
🤖 *This PR was automatically generated by Nemesis Repair Agent*
"""
        
        return body
    
    def _get_labels(self, fix: Dict) -> List[str]:
        """Get appropriate labels for the PR."""
        labels = ['nemesis', 'auto-fix']
        
        analysis = fix.get('analysis', {})
        blast_radius = analysis.get('blast_radius', '')
        
        if blast_radius == 'single-file':
            labels.append('simple')
        elif blast_radius == 'many-files':
            labels.append('complex')
        
        coverage = analysis.get('test_coverage', 0)
        if coverage >= 80:
            labels.append('well-tested')
        elif coverage < 50:
            labels.append('needs-tests')
        
        return labels
    
    def _get_reviewers(self) -> List[str]:
        """Get list of reviewers (configure as needed)."""
        reviewers_env = os.getenv('NEMESIS_REVIEWERS', '')
        if reviewers_env:
            return [r.strip() for r in reviewers_env.split(',')]
        return []


def main():
    """CLI interface for fix applicator."""
    if len(sys.argv) < 2:
        print("Usage: fix_applicator.py <fix-proposal.json> [--dry-run]")
        print("\nCreates GitHub PRs with generated code fixes")
        sys.exit(1)
    
    proposal_file = sys.argv[1]
    dry_run = '--dry-run' in sys.argv
    
    if not os.path.exists(proposal_file):
        print(f"❌ File not found: {proposal_file}")
        sys.exit(1)
    
    # Load fix proposal
    with open(proposal_file) as f:
        proposal_data = json.load(f)
    
    fixes = proposal_data.get('fixes', [])
    
    if not fixes:
        print("⚠️  No fixes found in proposal")
        sys.exit(0)
    
    print("=" * 80)
    print("FIX APPLICATOR - Phase 2 (Create PRs)")
    print("=" * 80)
    print(f"\nCreating PRs for {len(fixes)} fix(es)...\n")
    
    # Apply fixes
    applicator = FixApplicator(dry_run=dry_run)
    results = []
    
    for fix in fixes:
        result = applicator.apply_fix(fix)
        results.append(result)
    
    # Save results
    output_file = proposal_file.replace('fix-proposal', 'pr-results')
    with open(output_file, 'w') as f:
        json.dump({
            'timestamp': datetime.now().isoformat(),
            'proposal_file': proposal_file,
            'dry_run': dry_run,
            'results': results
        }, f, indent=2)
    
    print("\n" + "=" * 80)
    print("PR CREATION SUMMARY")
    print("=" * 80)
    
    success_count = sum(1 for r in results if r['status'] == 'success')
    error_count = sum(1 for r in results if r['status'] == 'error')
    
    print(f"\n✅ Successful: {success_count}")
    print(f"❌ Failed: {error_count}")
    
    for result in results:
        if result['status'] == 'success':
            print(f"\n{result['issue_id']}: {result['pr_url']}")
        else:
            print(f"\n{result['issue_id']}: Error - {result.get('error', 'Unknown')}")
    
    print(f"\n✓ Results saved: {output_file}")
    print("=" * 80)
    
    if not dry_run:
        print("\n🎉 PRs created! Review them on GitHub before merging.")
    else:
        print("\n💡 This was a dry run. Remove --dry-run to create actual PRs.")


if __name__ == '__main__':
    main()
