#!/usr/bin/env python3
"""
Fix Generator for Nemesis Repair Agent

Uses AI to generate code fixes based on divergence analysis.
Phase 1: Generate fixes with human approval required.
"""

import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime
import anthropic
import openai


class FixGenerator:
    """Generates code fixes using AI based on code analysis."""
    
    def __init__(self, project_root: str = ".", ai_provider: str = "anthropic"):
        self.project_root = Path(project_root).resolve()
        self.ai_provider = ai_provider
        
        # Load API reference if available
        api_ref_path = self.project_root / "API-REFERENCE.md"
        if api_ref_path.exists():
            self.api_reference = api_ref_path.read_text()
            print("✓ Loaded API reference")
        else:
            self.api_reference = None
            print("⚠️  No API reference found (will rely on code context only)")
        
        # Initialize AI client
        if ai_provider == "anthropic":
            api_key = os.getenv('ANTHROPIC_API_KEY') or os.getenv('CLAUDE_API_KEY')
            if not api_key:
                raise ValueError("ANTHROPIC_API_KEY or CLAUDE_API_KEY required")
            self.client = anthropic.Anthropic(api_key=api_key)
            self.model = "claude-sonnet-4-20250514"
        elif ai_provider == "openai":
            api_key = os.getenv('OPENAI_API_KEY')
            if not api_key:
                raise ValueError("OPENAI_API_KEY required")
            self.client = openai.OpenAI(api_key=api_key)
            self.model = "gpt-4-turbo-preview"
        else:
            raise ValueError(f"Unknown AI provider: {ai_provider}")
    
    def generate_fix(self, analysis: Dict, report: Dict) -> Dict:
        """
        Generate a fix for a single issue.
        
        Args:
            analysis: Code analysis result from code_analyzer.py
            report: Original Nemesis report with divergences
        
        Returns:
            Fix proposal with diffs and explanation
        """
        issue_id = analysis['issue_id']
        print(f"\n🤖 Generating fix for {issue_id}...")
        
        # Build context for AI
        context = self._build_fix_context(analysis, report)
        
        # Generate fix using AI
        fix_proposal = self._call_ai(context)
        
        # Parse and validate fix
        parsed_fix = self._parse_fix_proposal(fix_proposal, analysis)
        
        return {
            'issue_id': issue_id,
            'timestamp': datetime.now().isoformat(),
            'analysis': analysis,
            'proposal': parsed_fix,
            'ai_provider': self.ai_provider,
            'model': self.model
        }
    
    def _build_fix_context(self, analysis: Dict, report: Dict) -> Dict:
        """Build comprehensive context for AI to generate fix."""
        issue_id = analysis['issue_id']
        
        # Find matching AI issue from report
        ai_issue = self._find_ai_issue(issue_id, report)
        
        # Load affected file contents
        file_contents = {}
        for file_path in analysis['affected_files']:
            full_path = self.project_root / file_path
            try:
                file_contents[file_path] = full_path.read_text()
            except Exception as e:
                print(f"   ⚠️  Could not read {file_path}: {e}")
        
        # Load related interface/model files for better context
        related_files = self._find_related_files(analysis['affected_files'])
        for file_path in related_files:
            full_path = self.project_root / file_path
            try:
                if file_path not in file_contents:  # Don't duplicate
                    file_contents[file_path] = full_path.read_text()
                    print(f"   📎 Including related file: {file_path}")
            except Exception as e:
                pass
        
        # Load test file contents
        test_contents = {}
        for test_path in analysis['test_files']:
            full_path = self.project_root / test_path
            try:
                test_contents[test_path] = full_path.read_text()
            except Exception as e:
                print(f"   ⚠️  Could not read {test_path}: {e}")
        
        # Get sample divergences
        divergences = self._get_sample_divergences(issue_id, report)
        
        return {
            'issue': ai_issue,
            'analysis': analysis,
            'file_contents': file_contents,
            'test_contents': test_contents,
            'divergences': divergences,
            'report_date': report.get('timestamp', 'unknown')
        }
    
    def _find_related_files(self, affected_files: List[str]) -> List[str]:
        """Find related interface, model, and dependency files."""
        related = []
        
        for file_path in affected_files:
            # If it's an Impl class, find the interface
            if 'Impl.java' in file_path:
                interface_path = file_path.replace('Impl.java', '.java')
                if (self.project_root / interface_path).exists():
                    related.append(interface_path)
            
            # Find related model classes mentioned in the file
            try:
                full_path = self.project_root / file_path
                content = full_path.read_text()
                
                # Extract imported classes from same package
                import re
                imports = re.findall(r'import\s+io\.moov\.watchman\.(\w+)\.(\w+);', content)
                for package, class_name in imports:
                    related_path = f'src/main/java/io/moov/watchman/{package}/{class_name}.java'
                    if (self.project_root / related_path).exists():
                        related.append(related_path)
            except Exception:
                pass
        
        return list(set(related))[:5]  # Limit to 5 most relevant files
    
    def _find_ai_issue(self, issue_id: str, report: Dict) -> Optional[Dict]:
        """Find the AI issue from report that matches this analysis."""
        # First try to find in action plan (more structured)
        action_plan_file = str(self.project_root / 'data' / 'reports' / f'action-plan-*.json')
        import glob
        action_plans = sorted(glob.glob(action_plan_file), reverse=True)
        
        if action_plans:
            try:
                with open(action_plans[0]) as f:
                    action_plan = json.load(f)
                
                # Check auto_fix_actions
                for issue in action_plan.get('auto_fix_actions', []):
                    if issue.get('issue_id') == issue_id:
                        return {
                            'id': issue_id,
                            'category': issue.get('pattern', 'unknown'),
                            'priority': 'P1' if 'auto' in issue.get('next_step', '') else 'P2',
                            'description': f"Pattern: {issue.get('pattern')} - Confidence: {issue.get('confidence', 0):.0%}",
                            'recommendation': issue.get('next_step', 'Apply automated fix'),
                            'affected_queries': issue.get('affected_queries', 0)
                        }
                
                # Check human_review_actions
                for issue in action_plan.get('human_review_actions', []):
                    if issue.get('issue_id') == issue_id:
                        return {
                            'id': issue_id,
                            'category': issue.get('pattern', 'unknown'),
                            'priority': 'P2',
                            'description': f"Pattern: {issue.get('pattern')} - Confidence: {issue.get('confidence', 0):.0%}",
                            'recommendation': issue.get('next_step', 'Requires human review'),
                            'affected_queries': issue.get('affected_queries', 0),
                            'reasons': issue.get('reasons', [])
                        }
            except Exception as e:
                print(f"   ⚠️  Could not load action plan: {e}")
        
        # Fallback: Try AI analysis from report
        ai_analysis = report.get('ai_analysis', {})
        issues = ai_analysis.get('issues', [])
        
        for issue in issues:
            if issue.get('id') == issue_id:
                return issue
        
        # If nothing found, return a placeholder
        return {
            'id': issue_id,
            'category': 'unknown',
            'priority': 'P2',
            'description': 'Issue details not available',
            'recommendation': 'Review divergences and apply appropriate fix'
        }
    
    def _get_sample_divergences(self, issue_id: str, report: Dict, max_samples: int = 5) -> List[Dict]:
        """Get sample divergences for this issue."""
        divergences = report.get('divergences', [])
        
        # Filter divergences that match this issue pattern
        # For now, just return first few divergences
        # TODO: Better matching based on issue pattern
        
        return divergences[:max_samples]
    
    def _call_ai(self, context: Dict) -> str:
        """Call AI to generate fix proposal."""
        prompt = self._build_prompt(context)
        
        print(f"   Calling {self.ai_provider} {self.model}...")
        
        if self.ai_provider == "anthropic":
            response = self.client.messages.create(
                model=self.model,
                max_tokens=4000,
                messages=[{
                    "role": "user",
                    "content": prompt
                }]
            )
            return response.content[0].text
        
        elif self.ai_provider == "openai":
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[{
                    "role": "user",
                    "content": prompt
                }],
                max_tokens=4000
            )
            return response.choices[0].message.content
        
        else:
            raise ValueError(f"Unknown AI provider: {self.ai_provider}")
    
    def _build_prompt(self, context: Dict) -> str:
        """Build AI prompt for fix generation."""
        issue = context['issue'] or {}  # Ensure issue is never None
        analysis = context['analysis']
        files = context['file_contents']
        tests = context['test_contents']
        divergences = context['divergences']
        
        # Build API reference section
        api_section = ""
        if self.api_reference:
            api_section = f"""
API REFERENCE (USE ONLY THESE METHODS/CLASSES):
{self.api_reference[:15000]}  

CRITICAL: Only use methods and classes from the API reference above.
"""
        
        prompt = f"""You are an expert Java developer working on Moov Watchman Java - a critical financial compliance system.

CONTEXT:
You are implementing parity with the Go implementation while leveraging insights from OFAC-API (a commercial gold-standard service).
This is NOT about blindly copying Go - it's about achieving technical parity while recognizing better approaches when evidence suggests them.

THREE IMPLEMENTATIONS IN PLAY:
1. **OFAC-API** (Commercial) - Production-proven, used by major financial institutions - the gold standard
2. **Moov/Go** (Open Source) - Our parity baseline, but has known issues from multiple contributors with competing priorities
3. **Watchman-Java** (This codebase) - Our implementation that must match Go behavior, but can propose improvements

{api_section}

ISSUE DETAILS:
ID: {issue.get('id', 'Unknown')}
Category: {issue.get('category', 'Unknown')}
Priority: {issue.get('priority', 'Unknown')}
Description: {issue.get('description', 'No description')}
Recommendation: {issue.get('recommendation', 'No recommendation')}

CODE ANALYSIS:
- Affected files: {len(files)}
- Test coverage: {analysis['test_coverage']:.1f}%
- Blast radius: {analysis['blast_radius']}
- Dependencies: {len(analysis['dependencies'])}

SAMPLE DIVERGENCES (with 3-way comparison):
{self._format_divergences(divergences)}

CURRENT IMPLEMENTATION:

{self._format_files(files)}

TEST FILES:

{self._format_files(tests)}

YOUR TASK - TWO BUCKETS:

**BUCKET 1: PARITY FIX (Required - Ready to Merge)**
Generate code changes to make Java match Go behavior exactly.
- This is objective #1: Technical parity
- Must be measurable: Java score now matches Go score
- Include JUnit test cases proving parity is achieved
- Tests should use actual divergence data from above

**BUCKET 2: OFAC-API INTELLIGENCE (Optional - For Discussion)**
If OFAC-API shows significantly different behavior (score difference >0.3):
- Analyze trace data to understand WHY OFAC-API scored differently
- Propose what Java COULD do differently to align with commercial best practices
- Explain risks/benefits of the proposed change
- This is NOT ready to merge - it's for team discussion

CRITICAL CONSTRAINTS - YOU MUST FOLLOW THESE:
1. ONLY use methods and classes that exist in the provided code above
2. DO NOT invent or hallucinate methods - check the actual method signatures in the code
3. DO NOT add new classes unless they exist in the codebase
4. If a class/method doesn't exist, use what's actually available or note it needs manual implementation
5. Method names MUST match exactly what's in the provided code (case-sensitive)
6. Check field/property access - use actual getters/setters that exist
7. Verify imports - only use classes that are shown in the code or are standard Java

VALIDATION CHECKLIST (verify before responding):
- [ ] Every method call exists in the provided code
- [ ] Every class reference exists or is standard Java
- [ ] All field accesses use actual getter/setter methods
- [ ] No hallucinated Contact, normalize(), similarity() or other non-existent methods
- [ ] Code will compile with provided classes
- [ ] Test cases use actual divergence data and prove parity

OUTPUT FORMAT:

## BUCKET 1: PARITY FIX ✅ (Ready to Merge)

### Root Cause Analysis
<Brief explanation of why Java and Go diverge based on trace data>

### Code Changes

FILE: <relative/path/to/File.java>
EXPLANATION: <what changed and why>
```java
<complete modified file content>
```

### Test Cases (Parity Verification)

FILE: src/test/java/io/moov/watchman/<TestFile>Test.java
```java
<JUnit test class with test cases proving parity>
// Test structure:
// - Use actual divergence queries from above
// - Assert Java now matches Go score
// - Include trace breakdown validation
```

### Parity Achievement
- Before: Java={java_score}, Go={go_score}, Difference={diff}
- After:  Java={go_score}, Go={go_score}, ✓ Parity achieved

---

## BUCKET 2: OFAC-API INTELLIGENCE 💡 (Discussion Only)

<Only include this section if OFAC-API shows significant divergence (>0.3 score difference)>

### OFAC-API Behavior Analysis
<What does OFAC-API do differently based on scores and trace data?>

### Observations
- OFAC-API Score: {ofac_score}
- Go Score: {go_score}
- Java Score (after parity): {go_score}
- Divergence: OFAC-API is {higher/lower} by {difference}

### Trace Analysis
<Use trace data to hypothesize WHY OFAC-API scored differently>
Examples:
- "OFAC-API appears to weight exact name matches more heavily (score jumped from 0.4 to 0.9 on exact match)"
- "OFAC-API may use different phonetic algorithm (similar names scored very differently)"

### Proposed Improvement (Optional)
<Only if there's clear evidence OFAC-API approach is better>
- Suggested change: <what to modify>
- Expected impact: <how scores would change>
- Risk assessment: <potential for false positives/negatives>
- Requires: Further investigation and team discussion

---

## SUMMARY
<Overall explanation covering both buckets>

Begin your response:"""
        
        return prompt
    
    def _format_divergences(self, divergences: List[Dict]) -> str:
        """Format divergences for prompt, including 3-way comparison data."""
        if not divergences:
            return "No sample divergences available"
        
        formatted = []
        for i, div in enumerate(divergences[:5], 1):
            # Check if this is a 3-way comparison (includes external_data)
            has_external = 'external_data' in div and div['external_data']
            
            entry = f"""
Example {i}:
  Query: {div.get('query', 'N/A')}
  Type: {div.get('type', 'unknown')}
  Severity: {div.get('severity', 'N/A')}
  
  Java Score:    {div.get('java_data', {}).get('score', 'N/A')}
  Go Score:      {div.get('go_data', {}).get('score', 'N/A')}"""
            
            if has_external:
                external_score = div.get('external_data', {}).get('score', 'N/A')
                agreement = div.get('agreement_pattern', 'unknown')
                entry += f"""
  OFAC-API Score: {external_score}
  Agreement Pattern: {agreement}
  
  Analysis: """
                if agreement == "go_external_agree":
                    entry += "Go and OFAC-API agree - Java should match them"
                elif agreement == "java_external_agree":
                    entry += "Java and OFAC-API agree - Go may be wrong"
                elif agreement == "java_go_agree":
                    entry += "Java and Go agree - OFAC-API differs (investigate why)"
                else:
                    entry += "All three implementations disagree - needs investigation"
            
            entry += f"""
  
  Java Trace: {div.get('java_trace', 'Not available')}
  Details: {div.get('description', 'N/A')}
"""
            formatted.append(entry)
        
        return "\n".join(formatted)
    
    def _format_files(self, files: Dict[str, str]) -> str:
        """Format file contents for prompt."""
        if not files:
            return "No files available"
        
        formatted = []
        for path, content in files.items():
            # Truncate very long files
            if len(content) > 5000:
                content = content[:5000] + "\n... (truncated)"
            
            formatted.append(f"""
--- {path} ---
{content}
""")
        
        return "\n".join(formatted)
    
    def _parse_fix_proposal(self, ai_response: str, analysis: Dict) -> Dict:
        """Parse AI response into structured fix proposal."""
        import re
        
        # Extract file changes
        file_pattern = r'FILE:\s*(.+?)\n.*?```java\n(.*?)```'
        matches = re.finditer(file_pattern, ai_response, re.DOTALL)
        
        changes = []
        for match in matches:
            file_path = match.group(1).strip()
            new_content = match.group(2).strip()
            
            # Find explanation for this file
            explanation = self._extract_explanation(ai_response, file_path)
            
            changes.append({
                'file': file_path,
                'new_content': new_content,
                'explanation': explanation
            })
        
        # Extract summary
        summary_match = re.search(r'SUMMARY:\s*(.+?)(?:\n\n|\Z)', ai_response, re.DOTALL)
        summary = summary_match.group(1).strip() if summary_match else "No summary provided"
        
        # Validate changes
        validation = self._validate_changes(changes, analysis)
        
        return {
            'changes': changes,
            'summary': summary,
            'validation': validation,
            'raw_response': ai_response
        }
    
    def _extract_explanation(self, response: str, file_path: str) -> str:
        """Extract explanation for a specific file."""
        import re
        
        # Look for EXPLANATION: after FILE: <path>
        pattern = rf'FILE:\s*{re.escape(file_path)}.*?EXPLANATION:\s*(.+?)(?:\n```|\Z)'
        match = re.search(pattern, response, re.DOTALL)
        
        if match:
            return match.group(1).strip()
        
        return "No explanation provided"
    
    def _validate_changes(self, changes: List[Dict], analysis: Dict) -> Dict:
        """Validate proposed changes."""
        validation = {
            'valid': True,
            'errors': [],
            'warnings': []
        }
        
        # Check if all affected files have changes
        affected_files = set(analysis['affected_files'])
        changed_files = {c['file'] for c in changes}
        
        missing_files = affected_files - changed_files
        if missing_files:
            validation['warnings'].append(f"No changes for: {', '.join(missing_files)}")
        
        extra_files = changed_files - affected_files
        if extra_files:
            validation['warnings'].append(f"Unexpected changes to: {', '.join(extra_files)}")
        
        # Basic syntax check
        for change in changes:
            content = change['new_content']
            
            # Check for balanced braces
            if content.count('{') != content.count('}'):
                validation['errors'].append(f"{change['file']}: Unbalanced braces")
                validation['valid'] = False
            
            # Check for basic Java structure
            if 'class ' not in content and 'interface ' not in content and 'record ' not in content:
                validation['warnings'].append(f"{change['file']}: No class/interface/record found")
            
            # Check for common hallucination patterns
            hallucination_patterns = [
                ('Contact', 'Contact class (may not exist)'),
                ('.normalize(', 'normalize() method (check actual method name)'),
                ('.similarity(', 'similarity() method (check actual method name)'),
                ('.id()', 'id() method (check actual getter name)'),
                ('.birthDate()', 'birthDate() method (check if exists)')
            ]
            
            for pattern, warning in hallucination_patterns:
                if pattern in content:
                    validation['warnings'].append(f"{change['file']}: Uses {warning}")
        
        return validation


def main():
    """CLI interface for fix generator."""
    if len(sys.argv) < 2:
        print("Usage: fix_generator.py <code-analysis.json> [--ai-provider openai|anthropic]")
        print("\nGenerates code fixes based on code analysis results")
        sys.exit(1)
    
    analysis_file = sys.argv[1]
    
    if not os.path.exists(analysis_file):
        print(f"❌ File not found: {analysis_file}")
        sys.exit(1)
    
    # Determine AI provider from command line or environment
    ai_provider = "openai"  # Default to OpenAI
    if '--ai-provider' in sys.argv:
        provider_idx = sys.argv.index('--ai-provider')
        if provider_idx + 1 < len(sys.argv):
            ai_provider = sys.argv[provider_idx + 1]
    elif os.getenv('ANTHROPIC_API_KEY') or os.getenv('CLAUDE_API_KEY'):
        ai_provider = "anthropic"
    
    # Load code analysis
    with open(analysis_file) as f:
        analysis_data = json.load(f)
    
    # Load original report
    report_file = analysis_data.get('report_file')
    if not report_file or not os.path.exists(report_file):
        print(f"❌ Report file not found: {report_file}")
        sys.exit(1)
    
    with open(report_file) as f:
        report = json.load(f)
    
    analyses = analysis_data.get('analyses', [])
    
    if not analyses:
        print("⚠️  No analyses found")
        sys.exit(0)
    
    print("=" * 80)
    print("FIX GENERATOR - Phase 1 (Human Approval Required)")
    print("=" * 80)
    print(f"\nAI Provider: {ai_provider}")
    print(f"Generating fixes for {len(analyses)} issue(s)...\n")
    
    # Generate fixes
    generator = FixGenerator(ai_provider=ai_provider)
    fixes = []
    
    for analysis in analyses:
        try:
            fix = generator.generate_fix(analysis, report)
            fixes.append(fix)
            
            # Show validation results
            validation = fix['proposal']['validation']
            if not validation['valid']:
                print(f"   ❌ Validation failed: {', '.join(validation['errors'])}")
            elif validation['warnings']:
                print(f"   ⚠️  Warnings: {', '.join(validation['warnings'])}")
            else:
                print(f"   ✓ Fix generated successfully")
            
        except Exception as e:
            print(f"   ❌ Error: {e}")
            import traceback
            traceback.print_exc()
    
    # Save fixes
    output_file = analysis_file.replace('code-analysis', 'fix-proposal')
    with open(output_file, 'w') as f:
        json.dump({
            'timestamp': datetime.now().isoformat(),
            'analysis_file': analysis_file,
            'report_file': report_file,
            'fixes': fixes
        }, f, indent=2)
    
    print("\n" + "=" * 80)
    print("FIX GENERATION SUMMARY")
    print("=" * 80)
    
    for fix in fixes:
        issue_id = fix['issue_id']
        proposal = fix['proposal']
        changes = proposal['changes']
        validation = proposal['validation']
        
        print(f"\n{issue_id}:")
        print(f"  Files changed: {len(changes)}")
        print(f"  Valid: {'✓' if validation['valid'] else '✗'}")
        if validation['errors']:
            print(f"  Errors: {', '.join(validation['errors'])}")
        if validation['warnings']:
            print(f"  Warnings: {', '.join(validation['warnings'])}")
        print(f"  Summary: {proposal['summary'][:100]}...")
    
    print(f"\n✓ Fix proposals saved: {output_file}")
    print("\n⚠️  IMPORTANT: Review fixes before applying!")
    print("=" * 80)


if __name__ == '__main__':
    main()
