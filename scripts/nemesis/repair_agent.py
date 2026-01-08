#!/usr/bin/env python3
"""
Nemesis Repair Agent
Analyzes divergences and classifies them for auto-fix vs human review.
"""

import json
import sys
from pathlib import Path
from dataclasses import dataclass
from typing import List, Dict, Optional
from datetime import datetime
from collections import defaultdict


@dataclass
class IssueClassification:
    """Classification result for a detected issue."""
    classification: str  # 'auto-fix', 'human-review', or 'too-complex'
    confidence: float
    reasons: List[str]
    auto_fix_eligible: bool
    issue_data: Dict


class IssueClassifier:
    """Classifies divergences into auto-fix vs human review categories."""
    
    # Risk keywords that require human review
    RISK_KEYWORDS = ['security', 'auth', 'compliance', 'business logic', 'validation']
    
    # Thresholds
    MIN_PATTERN_CONFIDENCE = 0.90
    MAX_FILES_AFFECTED = 3
    MIN_TEST_COVERAGE = 0.70
    MIN_CONSISTENCY = 0.85
    
    def classify(self, issue: Dict) -> IssueClassification:
        """
        Classify an issue for repair strategy.
        
        Returns IssueClassification with:
        - classification: 'auto-fix', 'human-review', or 'too-complex'
        - confidence: 0.0-1.0
        - reasons: List of factors influencing decision
        - auto_fix_eligible: Boolean
        """
        classification = "auto-fix"
        confidence = 1.0
        reasons = []
        
        # Extract issue metadata
        pattern_confidence = issue.get('pattern_confidence', 0.0)
        files_affected = issue.get('files_affected', 999)
        test_coverage = issue.get('test_coverage', 0.0)
        category = issue.get('category', '').lower()
        root_causes = issue.get('root_causes', [])
        affected_queries = issue.get('affected_queries', 0)
        consistency = issue.get('consistency_score', 0.0)
        
        # Check pattern confidence
        if pattern_confidence < self.MIN_PATTERN_CONFIDENCE:
            classification = "human-review"
            confidence = pattern_confidence
            reasons.append(f"Low pattern confidence ({pattern_confidence:.1%})")
        
        # Check scope complexity
        if files_affected > self.MAX_FILES_AFFECTED:
            classification = "human-review"
            reasons.append(f"Multiple files affected ({files_affected} files)")
        
        # Check test coverage
        if test_coverage < self.MIN_TEST_COVERAGE:
            classification = "human-review"
            reasons.append(f"Insufficient test coverage ({test_coverage:.1%})")
        
        # Check for risk categories
        if any(keyword in category for keyword in self.RISK_KEYWORDS):
            classification = "human-review"
            reasons.append(f"High-risk category: {category}")
        
        # Check consistency across divergences
        if consistency < self.MIN_CONSISTENCY:
            classification = "human-review"
            reasons.append(f"Inconsistent pattern ({consistency:.1%})")
        
        # Check for multiple root causes (ambiguity)
        if len(root_causes) > 1:
            classification = "too-complex"
            reasons.append(f"Multiple root causes ({len(root_causes)})")
        
        # If low confidence human-review, escalate to too-complex
        if classification == "human-review" and confidence < 0.80:
            classification = "too-complex"
            reasons.append("High ambiguity - requires investigation")
        
        # If no reasons, it's eligible for auto-fix
        if not reasons:
            reasons.append("Meets all auto-fix criteria")
        
        return IssueClassification(
            classification=classification,
            confidence=confidence,
            reasons=reasons,
            auto_fix_eligible=(classification == "auto-fix"),
            issue_data=issue
        )


class RepairAgent:
    """Main orchestrator for analyzing and repairing divergences."""
    
    def __init__(self, report_path: Path, dry_run: bool = True):
        self.report_path = report_path
        self.dry_run = dry_run
        self.classifier = IssueClassifier()
        self.report_data = None
        self.classifications = []
        
    def load_report(self) -> Dict:
        """Load Nemesis report."""
        print(f"Loading report: {self.report_path}")
        
        with open(self.report_path) as f:
            self.report_data = json.load(f)
        
        print(f"✓ Loaded report from {self.report_data.get('run_date', 'unknown')}")
        return self.report_data
    
    def analyze_divergences(self) -> List[Dict]:
        """
        Use AI analysis from report to identify issues.
        
        Returns list of issues with:
        - pattern: Common pattern across divergences
        - affected_queries: Count
        - divergences: List of individual divergences
        - root_cause: AI-identified cause
        """
        divergences = self.report_data.get('divergences', [])
        ai_analysis = self.report_data.get('ai_analysis', {})
        ai_issues = ai_analysis.get('issues', [])
        
        print(f"\nAnalyzing {len(divergences)} divergences...")
        print(f"Using AI analysis: {len(ai_issues)} AI-identified patterns")
        
        issues = []
        
        # Use AI-identified issues if available
        if ai_issues:
            for idx, ai_issue in enumerate(ai_issues):
                # Map AI issue to our format
                issue_id = ai_issue.get('id', f"AI-{idx+1}")
                category = ai_issue.get('category', 'Unknown')
                priority = ai_issue.get('priority', 'P3')
                description = ai_issue.get('description', '')
                recommendation = ai_issue.get('recommendation', '')
                affected = ai_issue.get('affected_queries', 0)
                
                # Parse confidence from description or default based on priority
                confidence = self._priority_to_confidence(priority)
                
                # Estimate metrics based on category and priority
                files_affected = self._estimate_files_from_category(category)
                test_coverage = 0.75  # Default, would need code analysis
                
                # Determine if pattern is consistent
                total_divs = len(divergences)
                consistency = affected / total_divs if total_divs > 0 else 0.0
                
                # Extract root causes from recommendation
                root_causes = [recommendation] if recommendation else [description]
                
                issue = {
                    'id': issue_id,
                    'pattern': category.lower().replace(' ', '_'),
                    'category': category,
                    'priority': priority,
                    'affected_queries': affected,
                    'divergences': [],  # Would need to map back to specific divergences
                    'severity': self._priority_to_severity(priority),
                    'pattern_confidence': confidence,
                    'consistency_score': consistency,
                    'files_affected': files_affected,
                    'test_coverage': test_coverage,
                    'root_causes': root_causes,
                    'description': description,
                    'recommendation': recommendation
                }
                
                issues.append(issue)
        
        # If no AI analysis, fall back to grouping by type
        if not issues:
            print("⚠️  No AI analysis found, using fallback grouping...")
            grouped = defaultdict(list)
            for div in divergences:
                key = (div.get('type'), div.get('severity'))
                grouped[key].append(div)
            
            for (div_type, severity), divs in grouped.items():
                consistency = len(divs) / len(divergences) if divergences else 0
                
                issue = {
                    'id': f"FALLBACK-{div_type.upper()}",
                    'pattern': div_type,
                    'category': self._categorize_type(div_type),
                    'priority': 'P2',
                    'affected_queries': len(divs),
                    'divergences': divs,
                    'severity': severity,
                    'pattern_confidence': consistency,
                    'consistency_score': consistency,
                    'files_affected': self._estimate_files_affected(div_type),
                    'test_coverage': 0.75,
                    'root_causes': [self._hypothesize_root_cause(div_type)],
                    'description': self._describe_issue(div_type, len(divs)),
                    'recommendation': 'Requires investigation'
                }
                
                issues.append(issue)
        
        print(f"✓ Identified {len(issues)} distinct issue patterns")
        return issues
    
    def classify_issues(self, issues: List[Dict]) -> List[IssueClassification]:
        """Classify all issues."""
        print(f"\n{'='*80}")
        print("CLASSIFICATION RESULTS")
        print('='*80)
        
        classifications = []
        stats = defaultdict(int)
        
        for issue in issues:
            classification = self.classifier.classify(issue)
            classifications.append(classification)
            stats[classification.classification] += 1
            
            # Print classification
            symbol = "✅" if classification.auto_fix_eligible else "⚠️" if classification.classification == "human-review" else "🔍"
            print(f"\n{symbol} {issue['id']}: {classification.classification.upper()}")
            print(f"   Pattern: {issue['pattern']} ({issue['affected_queries']} queries)")
            print(f"   Confidence: {classification.confidence:.1%}")
            print(f"   Reasons:")
            for reason in classification.reasons:
                print(f"     - {reason}")
        
        print(f"\n{'='*80}")
        print("SUMMARY")
        print('='*80)
        print(f"  Auto-fix eligible: {stats['auto-fix']}")
        print(f"  Needs human review: {stats['human-review']}")
        print(f"  Too complex: {stats['too-complex']}")
        print(f"  Total issues: {sum(stats.values())}")
        
        self.classifications = classifications
        return classifications
    
    def generate_action_plan(self) -> Dict:
        """Generate action plan based on classifications."""
        plan = {
            'timestamp': datetime.now().isoformat(),
            'report_file': str(self.report_path),
            'dry_run': self.dry_run,
            'auto_fix_actions': [],
            'human_review_actions': [],
            'investigation_needed': []
        }
        
        for classification in self.classifications:
            issue = classification.issue_data
            
            action = {
                'issue_id': issue['id'],
                'pattern': issue['pattern'],
                'affected_queries': issue['affected_queries'],
                'confidence': classification.confidence,
                'reasons': classification.reasons
            }
            
            if classification.classification == 'auto-fix':
                action['next_step'] = 'Generate code fix and create PR (auto-merge)'
                plan['auto_fix_actions'].append(action)
            elif classification.classification == 'human-review':
                action['next_step'] = 'Generate code fix and create PR (request review)'
                plan['human_review_actions'].append(action)
            else:  # too-complex
                action['next_step'] = 'Create detailed GitHub issue for investigation'
                plan['investigation_needed'].append(action)
        
        return plan
    
    def save_action_plan(self, plan: Dict, output_path: Optional[Path] = None):
        """Save action plan to file."""
        if output_path is None:
            output_path = self.report_path.parent / f"action-plan-{datetime.now().strftime('%Y%m%d-%H%M%S')}.json"
        
        with open(output_path, 'w') as f:
            json.dump(plan, f, indent=2)
        
        print(f"\n✓ Action plan saved: {output_path}")
        return output_path
    
    def _estimate_files_affected(self, div_type: str) -> int:
        """Estimate number of files affected by divergence type."""
        # Simplified heuristic - would need actual code analysis
        type_complexity = {
            'score_difference': 2,  # Likely scoring classes
            'top_result_differs': 3,  # Scoring + ranking
            'result_order_differs': 2,  # Ranking logic
            'java_extra_result': 2,  # Filtering logic
            'go_extra_result': 2,  # Filtering logic
        }
        return type_complexity.get(div_type, 5)
    
    def _categorize_type(self, div_type: str) -> str:
        """Categorize divergence type."""
        if 'score' in div_type:
            return 'Scoring'
        elif 'order' in div_type or 'top_result' in div_type:
            return 'Ranking'
        elif 'extra' in div_type:
            return 'Filtering'
        return 'Other'
    
    def _hypothesize_root_cause(self, div_type: str) -> str:
        """Hypothesize root cause based on divergence type."""
        causes = {
            'score_difference': 'Scoring algorithm precision or normalization',
            'top_result_differs': 'Different ranking/tiebreaker logic',
            'result_order_differs': 'Sort order or score tiebreaking',
            'java_extra_result': 'Looser matching threshold in Java',
            'go_extra_result': 'Stricter matching threshold in Java',
        }
        return causes.get(div_type, 'Unknown - requires investigation')
    
    def _describe_issue(self, div_type: str, count: int) -> str:
        """Generate human-readable description."""
        return f"Found {count} instances of {div_type.replace('_', ' ')}"
    
    def _priority_to_confidence(self, priority: str) -> float:
        """Convert priority to confidence score."""
        priority_map = {
            'P0': 0.95,  # Critical - very clear pattern
            'P1': 0.90,  # High - clear pattern
            'P2': 0.80,  # Medium - likely pattern
            'P3': 0.70,  # Low - possible pattern
            'P?': 0.50,  # Unknown
        }
        return priority_map.get(priority, 0.75)
    
    def _priority_to_severity(self, priority: str) -> str:
        """Convert priority to severity."""
        if priority in ['P0', 'P1']:
            return 'critical'
        elif priority == 'P2':
            return 'moderate'
        else:
            return 'minor'
    
    def _estimate_files_from_category(self, category: str) -> int:
        """Estimate files affected based on category."""
        category_lower = category.lower()
        
        if 'scoring' in category_lower or 'precision' in category_lower:
            return 2  # Likely 1-2 scoring classes
        elif 'ranking' in category_lower or 'ordering' in category_lower:
            return 3  # Scoring + ranking classes
        elif 'filtering' in category_lower or 'matching' in category_lower:
            return 2  # Filtering logic
        elif 'algorithm' in category_lower or 'core' in category_lower:
            return 5  # Multiple files
        else:
            return 3  # Default
    
    def run(self):
        """Execute full repair agent analysis."""
        print("="*80)
        print("NEMESIS REPAIR AGENT - Phase 1: Classification")
        print("="*80)
        print(f"Mode: {'DRY RUN' if self.dry_run else 'LIVE'}")
        print()
        
        # Load report
        self.load_report()
        
        # Analyze divergences into issues
        issues = self.analyze_divergences()
        
        # Classify each issue
        classifications = self.classify_issues(issues)
        
        # Generate action plan
        plan = self.generate_action_plan()
        
        # Print action plan
        print(f"\n{'='*80}")
        print("ACTION PLAN")
        print('='*80)
        
        if plan['auto_fix_actions']:
            print(f"\n✅ AUTO-FIX ACTIONS ({len(plan['auto_fix_actions'])})")
            for action in plan['auto_fix_actions']:
                print(f"  • {action['issue_id']}: {action['next_step']}")
        
        if plan['human_review_actions']:
            print(f"\n⚠️  HUMAN REVIEW ACTIONS ({len(plan['human_review_actions'])})")
            for action in plan['human_review_actions']:
                print(f"  • {action['issue_id']}: {action['next_step']}")
        
        if plan['investigation_needed']:
            print(f"\n🔍 INVESTIGATION NEEDED ({len(plan['investigation_needed'])})")
            for action in plan['investigation_needed']:
                print(f"  • {action['issue_id']}: {action['next_step']}")
        
        # Save action plan
        plan_path = self.save_action_plan(plan)
        
        print(f"\n{'='*80}")
        print("✅ REPAIR AGENT COMPLETE")
        print('='*80)
        
        return plan


def main():
    """CLI entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(description='Nemesis Repair Agent')
    parser.add_argument('report', type=Path, help='Path to Nemesis report JSON')
    parser.add_argument('--live', action='store_true', help='Run in live mode (default: dry-run)')
    parser.add_argument('--output', type=Path, help='Output path for action plan')
    
    args = parser.parse_args()
    
    if not args.report.exists():
        print(f"Error: Report not found: {args.report}")
        return 1
    
    agent = RepairAgent(args.report, dry_run=not args.live)
    plan = agent.run()
    
    if args.output:
        agent.save_action_plan(plan, args.output)
    
    return 0


if __name__ == '__main__':
    sys.exit(main())
