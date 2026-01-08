#!/usr/bin/env python3
"""
Code Analyzer for Nemesis Repair Agent

Maps issues to affected Java code, calculates test coverage,
and determines blast radius for automated repair decisions.
"""

import json
import os
import re
from pathlib import Path
from typing import Dict, List, Optional, Set
from dataclasses import dataclass, asdict
import subprocess


@dataclass
class CodeAnalysis:
    """Analysis results for a single issue."""
    issue_id: str
    affected_files: List[str]
    test_files: List[str]
    test_coverage: float
    blast_radius: str  # "single-file", "few-files", "many-files"
    dependencies: List[str]
    code_context: Dict
    confidence: float


class CodeAnalyzer:
    """Analyzes Java codebase to map issues to affected code."""
    
    def __init__(self, project_root: str = "."):
        self.project_root = Path(project_root).resolve()
        self.src_dir = self.project_root / "src" / "main" / "java"
        self.test_dir = self.project_root / "src" / "test" / "java"
        
        # Category to file pattern mappings
        self.category_patterns = {
            'scoring': ['*Scorer*.java', '*Scoring*.java', '*Score*.java'],
            'ranking': ['*Rank*.java', '*Order*.java', '*Sort*.java'],
            'matching': ['*Match*.java', '*Filter*.java', '*Search*.java'],
            'filtering': ['*Filter*.java', '*Match*.java'],
            'precision': ['*Scorer*.java', '*Precision*.java'],
            'algorithm': ['*Algorithm*.java', '*Processor*.java'],
            'name': ['*Name*.java'],
            'address': ['*Address*.java'],
            'entity': ['*Entity*.java'],
            'search': ['*Search*.java', '*Query*.java'],
        }
    
    def analyze_issue(self, issue: Dict) -> CodeAnalysis:
        """
        Analyze a single issue to find affected code.
        
        Args:
            issue: Issue dict from repair_agent.py with pattern, category, etc.
        
        Returns:
            CodeAnalysis with files, coverage, and context
        """
        issue_id = issue.get('id', 'UNKNOWN')
        pattern = issue.get('pattern', '')
        category = issue.get('category', '').lower()
        
        print(f"\n🔍 Analyzing {issue_id}: {category}")
        
        # Find affected files
        affected_files = self._find_affected_files(pattern, category)
        print(f"   Found {len(affected_files)} affected file(s)")
        
        # Find test files
        test_files = self._find_test_files(affected_files)
        print(f"   Found {len(test_files)} test file(s)")
        
        # Calculate test coverage
        test_coverage = self._calculate_coverage(affected_files, test_files)
        print(f"   Test coverage: {test_coverage:.1f}%")
        
        # Determine blast radius
        blast_radius = self._determine_blast_radius(affected_files)
        print(f"   Blast radius: {blast_radius}")
        
        # Find dependencies
        dependencies = self._find_dependencies(affected_files)
        print(f"   Dependencies: {len(dependencies)} files")
        
        # Extract code context
        code_context = self._extract_code_context(affected_files, pattern, category)
        
        # Calculate analysis confidence
        confidence = self._calculate_confidence(affected_files, test_files, test_coverage)
        
        return CodeAnalysis(
            issue_id=issue_id,
            affected_files=[str(f.relative_to(self.project_root)) for f in affected_files],
            test_files=[str(f.relative_to(self.project_root)) for f in test_files],
            test_coverage=test_coverage,
            blast_radius=blast_radius,
            dependencies=[str(d.relative_to(self.project_root)) for d in dependencies],
            code_context=code_context,
            confidence=confidence
        )
    
    def _find_affected_files(self, pattern: str, category: str) -> List[Path]:
        """Find Java files likely affected by this issue."""
        files = set()
        
        # Get search patterns for this category
        patterns = self.category_patterns.get(category, [])
        
        # Add pattern-based searches
        if pattern:
            pattern_parts = pattern.replace('_', ' ').split()
            for part in pattern_parts:
                if len(part) > 3:  # Skip short words
                    patterns.append(f"*{part.title()}*.java")
        
        # Search for matching files
        for search_pattern in patterns:
            matches = list(self.src_dir.rglob(search_pattern))
            files.update(matches)
        
        # If no files found, do content-based search
        if not files:
            files = self._search_by_content(pattern, category)
        
        return sorted(list(files))
    
    def _search_by_content(self, pattern: str, category: str) -> Set[Path]:
        """Search Java files by content when filename matching fails."""
        files = set()
        
        # Build search terms
        search_terms = []
        if category:
            search_terms.append(category)
        if pattern:
            search_terms.extend(pattern.replace('_', ' ').split())
        
        if not search_terms:
            return files
        
        # Search all Java files
        for java_file in self.src_dir.rglob("*.java"):
            try:
                content = java_file.read_text()
                content_lower = content.lower()
                
                # Count term matches
                matches = sum(1 for term in search_terms if term.lower() in content_lower)
                
                # If multiple terms match, likely relevant
                if matches >= 2 or (len(search_terms) == 1 and matches == 1):
                    files.add(java_file)
            except Exception:
                continue
        
        return files
    
    def _find_test_files(self, affected_files: List[Path]) -> List[Path]:
        """Find test files for affected source files."""
        test_files = []
        
        for src_file in affected_files:
            # Get class name
            class_name = src_file.stem
            
            # Common test naming patterns
            test_patterns = [
                f"{class_name}Test.java",
                f"{class_name}Tests.java",
                f"Test{class_name}.java",
            ]
            
            for pattern in test_patterns:
                matches = list(self.test_dir.rglob(pattern))
                test_files.extend(matches)
        
        return list(set(test_files))
    
    def _calculate_coverage(self, affected_files: List[Path], test_files: List[Path]) -> float:
        """
        Calculate test coverage for affected files.
        
        Priority:
        1. JaCoCo XML report if available
        2. Heuristic based on test existence and LOC
        """
        # Try to use JaCoCo report
        jacoco_coverage = self._get_jacoco_coverage(affected_files)
        if jacoco_coverage is not None:
            return jacoco_coverage
        
        # Fallback: heuristic calculation
        if not affected_files:
            return 0.0
        
        if not test_files:
            return 0.0
        
        # Calculate based on LOC ratio
        total_src_lines = sum(self._count_lines(f) for f in affected_files)
        total_test_lines = sum(self._count_lines(f) for f in test_files)
        
        if total_src_lines == 0:
            return 0.0
        
        # Rough estimate: test LOC / src LOC, capped at 100%
        coverage = min(100.0, (total_test_lines / total_src_lines) * 100)
        
        return coverage
    
    def _get_jacoco_coverage(self, affected_files: List[Path]) -> Optional[float]:
        """Parse JaCoCo XML report for coverage data."""
        jacoco_xml = self.project_root / "target" / "site" / "jacoco" / "jacoco.xml"
        
        if not jacoco_xml.exists():
            return None
        
        try:
            import xml.etree.ElementTree as ET
            tree = ET.parse(jacoco_xml)
            root = tree.getroot()
            
            total_covered = 0
            total_missed = 0
            
            # Match affected files in report
            for src_file in affected_files:
                class_name = src_file.stem
                
                # Find matching class in report
                for package in root.findall('.//package'):
                    for cls in package.findall('class'):
                        if class_name in cls.get('name', ''):
                            # Sum up line coverage
                            for counter in cls.findall('counter[@type="LINE"]'):
                                total_missed += int(counter.get('missed', 0))
                                total_covered += int(counter.get('covered', 0))
            
            if total_covered + total_missed == 0:
                return None
            
            coverage = (total_covered / (total_covered + total_missed)) * 100
            return coverage
            
        except Exception:
            return None
    
    def _count_lines(self, file_path: Path) -> int:
        """Count lines of code (excluding comments and blanks)."""
        try:
            content = file_path.read_text()
            lines = content.split('\n')
            
            code_lines = 0
            in_multiline_comment = False
            
            for line in lines:
                stripped = line.strip()
                
                # Skip empty lines
                if not stripped:
                    continue
                
                # Handle multiline comments
                if '/*' in stripped:
                    in_multiline_comment = True
                if '*/' in stripped:
                    in_multiline_comment = False
                    continue
                
                if in_multiline_comment:
                    continue
                
                # Skip single-line comments
                if stripped.startswith('//'):
                    continue
                
                code_lines += 1
            
            return code_lines
        except Exception:
            return 0
    
    def _determine_blast_radius(self, affected_files: List[Path]) -> str:
        """Determine scope of changes needed."""
        count = len(affected_files)
        
        if count == 0:
            return "unknown"
        elif count == 1:
            return "single-file"
        elif count <= 3:
            return "few-files"
        else:
            return "many-files"
    
    def _find_dependencies(self, affected_files: List[Path]) -> List[Path]:
        """Find files that depend on the affected files."""
        dependencies = set()
        
        if not affected_files:
            return []
        
        # Get class names from affected files
        affected_classes = {f.stem for f in affected_files}
        
        # Search all Java files for imports/usage
        for java_file in self.src_dir.rglob("*.java"):
            if java_file in affected_files:
                continue
            
            try:
                content = java_file.read_text()
                
                # Check if any affected class is imported or used
                for class_name in affected_classes:
                    if re.search(rf'\b{class_name}\b', content):
                        dependencies.add(java_file)
                        break
            except Exception:
                continue
        
        return sorted(list(dependencies))
    
    def _extract_code_context(self, affected_files: List[Path], pattern: str, category: str) -> Dict:
        """Extract relevant code snippets and structure."""
        context = {
            'files': [],
            'key_methods': [],
            'key_classes': []
        }
        
        for file_path in affected_files[:5]:  # Limit to first 5 files
            try:
                content = file_path.read_text()
                
                file_context = {
                    'path': str(file_path.relative_to(self.project_root)),
                    'class_name': file_path.stem,
                    'methods': self._extract_methods(content, pattern, category),
                    'lines_of_code': self._count_lines(file_path)
                }
                
                context['files'].append(file_context)
                context['key_classes'].append(file_path.stem)
                
                # Add method names
                context['key_methods'].extend([m['name'] for m in file_context['methods']])
                
            except Exception:
                continue
        
        return context
    
    def _extract_methods(self, content: str, pattern: str, category: str) -> List[Dict]:
        """Extract relevant method signatures from Java file."""
        methods = []
        
        # Simple regex to find method signatures
        method_pattern = r'(public|private|protected)?\s+[\w<>]+\s+(\w+)\s*\([^)]*\)'
        matches = re.finditer(method_pattern, content)
        
        search_terms = [pattern, category] + pattern.split('_') if pattern else [category]
        
        for match in matches:
            method_sig = match.group(0)
            method_name = match.group(2)
            
            # Check if method is relevant
            relevant = any(term.lower() in method_name.lower() for term in search_terms if term)
            
            if relevant or len(methods) < 3:  # Include first 3 or relevant ones
                methods.append({
                    'name': method_name,
                    'signature': method_sig.strip()
                })
        
        return methods[:10]  # Limit to 10 methods
    
    def _calculate_confidence(self, affected_files: List[Path], test_files: List[Path], coverage: float) -> float:
        """Calculate confidence in code analysis."""
        confidence = 1.0
        
        # Penalize if no files found
        if not affected_files:
            confidence *= 0.3
        
        # Penalize if no tests
        if not test_files:
            confidence *= 0.7
        
        # Penalize low coverage
        if coverage < 50:
            confidence *= 0.8
        elif coverage < 70:
            confidence *= 0.9
        
        # Penalize many files (harder to fix)
        if len(affected_files) > 5:
            confidence *= 0.7
        
        return round(confidence, 2)


def main():
    """CLI interface for code analyzer."""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: code_analyzer.py <action_plan.json>")
        print("\nAnalyzes code affected by issues in action plan")
        sys.exit(1)
    
    action_plan_file = sys.argv[1]
    
    if not os.path.exists(action_plan_file):
        print(f"❌ File not found: {action_plan_file}")
        sys.exit(1)
    
    # Load action plan
    with open(action_plan_file) as f:
        action_plan = json.load(f)
    
    # Keep reference to report file for later use
    report_file = action_plan.get('report_file')
    
    # Extract issues from action plan (auto-fix and human-review)
    auto_fix = action_plan.get('auto_fix_actions', [])
    human_review = action_plan.get('human_review_actions', [])
    
    issues = auto_fix + human_review
    
    if not issues:
        print("⚠️  No issues found in action plan (all too complex or investigation needed)")
        sys.exit(0)
    
    # Convert action plan issues to expected format
    formatted_issues = []
    for issue in issues:
        formatted_issues.append({
            'id': issue.get('issue_id', 'UNKNOWN'),
            'pattern': issue.get('pattern', 'unknown'),
            'category': issue.get('pattern', 'unknown'),  # Use pattern as category
            'confidence': issue.get('confidence', 0.0),
            'affected_queries': issue.get('affected_queries', 0)
        })
    
    issues = formatted_issues
    
    print("=" * 80)
    print("CODE ANALYZER - Finding Affected Java Files")
    print("=" * 80)
    print(f"\nAnalyzing {len(issues)} issue(s)...\n")
    
    # Analyze each issue
    analyzer = CodeAnalyzer()
    results = []
    
    for issue in issues:
        analysis = analyzer.analyze_issue(issue)
        results.append(asdict(analysis))
    
    # Save results
    output_file = action_plan_file.replace('action-plan', 'code-analysis')
    with open(output_file, 'w') as f:
        json.dump({
            'timestamp': action_plan.get('timestamp'),
            'report_file': report_file,
            'analyses': results
        }, f, indent=2)
    
    print("\n" + "=" * 80)
    print("ANALYSIS SUMMARY")
    print("=" * 80)
    
    for result in results:
        print(f"\n{result['issue_id']}:")
        print(f"  Files: {len(result['affected_files'])}")
        print(f"  Tests: {len(result['test_files'])}")
        print(f"  Coverage: {result['test_coverage']:.1f}%")
        print(f"  Blast radius: {result['blast_radius']}")
        print(f"  Confidence: {result['confidence']:.0%}")
    
    print(f"\n✓ Code analysis saved: {output_file}")
    print("=" * 80)


if __name__ == '__main__':
    main()
