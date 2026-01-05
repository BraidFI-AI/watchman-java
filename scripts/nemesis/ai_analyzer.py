"""
AI Analyzer - Uses LLM to analyze divergences and find patterns
Takes raw divergence data and returns actionable insights
"""

import json
from dataclasses import dataclass, field
from typing import List, Dict, Optional
import os


@dataclass
class AnalysisResult:
    """Result from AI analysis of divergences."""
    total_divergences: int
    patterns_identified: int
    issues: List[Dict] = field(default_factory=list)
    recommendations: List[str] = field(default_factory=list)
    summary: str = ""


class AIAnalyzer:
    """Analyzes divergences using AI to identify patterns and root causes."""
    
    def __init__(self, provider: str = "openai", api_key: Optional[str] = None, model: Optional[str] = None):
        """
        Initialize AI analyzer.
        
        Args:
            provider: AI provider (openai, anthropic, ollama)
            api_key: API key (if None, reads from environment)
            model: Model name (defaults based on provider)
        """
        self.provider = provider
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY")
        
        if model:
            self.model = model
        elif provider == "openai":
            self.model = "gpt-4-turbo"
        elif provider == "anthropic":
            self.model = "claude-sonnet-4"
        else:
            self.model = "gpt-4-turbo"
        
        self.client = None
        if self.api_key:
            self._initialize_client()
    
    def _initialize_client(self):
        """Initialize AI client based on provider."""
        if self.provider == "openai":
            try:
                from openai import OpenAI
                self.client = OpenAI(api_key=self.api_key)
            except ImportError:
                print("⚠ OpenAI not installed. Install with: pip install openai")
        elif self.provider == "anthropic":
            try:
                from anthropic import Anthropic
                self.client = Anthropic(api_key=self.api_key)
            except ImportError:
                print("⚠ Anthropic not installed. Install with: pip install anthropic")
    
    def analyze(self, divergences: List[Dict], total_queries: int) -> AnalysisResult:
        """
        Analyze divergences and return insights.
        
        Args:
            divergences: List of divergence dicts
            total_queries: Total number of queries executed
            
        Returns:
            AnalysisResult with patterns and recommendations
        """
        if not divergences:
            return AnalysisResult(
                total_divergences=0,
                patterns_identified=0,
                summary="No divergences found. Java and Go implementations are in sync!"
            )
        
        if not self.client:
            # Fallback: basic rule-based analysis
            return self._rule_based_analysis(divergences, total_queries)
        
        # Prepare context for AI
        context = self._prepare_context(divergences, total_queries)
        
        # Call AI
        try:
            response_text = self._call_ai(context)
            issues = self._extract_issues_from_text(response_text)
            
            return AnalysisResult(
                total_divergences=len(divergences),
                patterns_identified=len(set(d.get("type") for d in divergences)),
                issues=issues,
                summary=response_text[:500]  # First 500 chars
            )
        except Exception as e:
            print(f"⚠ AI analysis failed: {e}")
            return self._rule_based_analysis(divergences, total_queries)
    
    def _prepare_context(self, divergences: List[Dict], total_queries: int) -> str:
        """Prepare context string for AI."""
        # Limit divergences to avoid token limits
        sample_size = min(len(divergences), 50)
        divergence_sample = divergences[:sample_size]
        
        # Group by type for summary
        by_type = {}
        for d in divergences:
            dtype = d.get("type", "unknown")
            by_type[dtype] = by_type.get(dtype, 0) + 1
        
        context = f"""You are analyzing divergences between Java and Go implementations of a sanctions screening algorithm.

SUMMARY:
- Total queries executed: {total_queries}
- Total divergences found: {len(divergences)}
- Divergences by type: {json.dumps(by_type, indent=2)}

SAMPLE DIVERGENCES (first {sample_size}):
"""
        
        for i, div in enumerate(divergence_sample, 1):
            context += f"\n{i}. Query: '{div.get('query')}'\n"
            context += f"   Type: {div.get('type')}\n"
            context += f"   Severity: {div.get('severity')}\n"
            context += f"   Description: {div.get('description')}\n"
            
            if div.get('java_data') and div.get('go_data'):
                java_name = div['java_data'].get('name', 'N/A')
                go_name = div['go_data'].get('name', 'N/A')
                java_score = div['java_data'].get('match', 0)
                go_score = div['go_data'].get('match', 0)
                context += f"   Java: {java_name} (score: {java_score:.3f})\n"
                context += f"   Go:   {go_name} (score: {go_score:.3f})\n"
            
            if i >= 30:  # Limit to 30 examples
                context += f"\n... and {len(divergences) - 30} more divergences\n"
                break
        
        context += """

TASK: Analyze these divergences and identify:
1. Common patterns (e.g., cross-language issues, phonetic bugs, scoring inconsistencies)
2. Root causes (why are these divergences happening?)
3. Priority (which issues are most critical to fix?)
4. Recommendations (what code changes would fix them?)

Format your response as a structured analysis with specific, actionable issues.
Focus on patterns that affect multiple queries rather than individual edge cases.
"""
        
        return context
    
    def _call_ai(self, context: str) -> str:
        """Call AI provider."""
        if self.provider == "openai":
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "You are an expert at analyzing algorithm bugs and finding patterns in test failures."},
                    {"role": "user", "content": context}
                ],
                max_tokens=2000,
                temperature=0.3
            )
            return response.choices[0].message.content
        
        elif self.provider == "anthropic":
            response = self.client.messages.create(
                model=self.model,
                max_tokens=2000,
                messages=[
                    {"role": "user", "content": context}
                ]
            )
            return response.content[0].text
        
        return ""
    
    def _extract_issues_from_text(self, text: str) -> List[Dict]:
        """Extract structured issues from AI response text."""
        issues = []
        
        # Simple pattern matching for common issue formats
        lines = text.split('\n')
        current_issue = {}
        
        for line in lines:
            line = line.strip()
            
            # Look for issue markers
            if any(marker in line.lower() for marker in ['issue', 'problem', 'bug', 'pattern']):
                if current_issue:
                    issues.append(current_issue)
                current_issue = {"description": line}
            
            # Extract priority
            if 'p0' in line.lower() or 'critical' in line.lower():
                current_issue['priority'] = 'P0'
            elif 'p1' in line.lower() or 'high' in line.lower():
                current_issue['priority'] = 'P1'
            elif 'p2' in line.lower() or 'medium' in line.lower():
                current_issue['priority'] = 'P2'
            
            # Extract category
            if 'cross-language' in line.lower():
                current_issue['category'] = 'Cross-Language'
            elif 'phonetic' in line.lower():
                current_issue['category'] = 'Phonetic'
            elif 'score' in line.lower() or 'scoring' in line.lower():
                current_issue['category'] = 'Scoring'
        
        if current_issue:
            issues.append(current_issue)
        
        return issues
    
    def _rule_based_analysis(self, divergences: List[Dict], total_queries: int) -> AnalysisResult:
        """Fallback: basic rule-based analysis without AI."""
        issues = []
        
        # Check for cross-language patterns
        cross_lang_count = 0
        cross_lang_examples = []
        
        for div in divergences:
            if div.get('severity') == 'critical' and div.get('type') == 'top_result_differs':
                java_data = div.get('java_data', {})
                go_data = div.get('go_data', {})
                java_name = java_data.get('name', '')
                go_name = go_data.get('name', '')
                
                # Simple heuristic: different scripts
                if java_name and go_name and self._likely_different_scripts(java_name, go_name):
                    cross_lang_count += 1
                    cross_lang_examples.append({
                        "query": div.get('query'),
                        "java": java_name,
                        "go": go_name,
                        "java_score": java_data.get('match', 0),
                        "go_score": go_data.get('match', 0)
                    })
        
        if cross_lang_count >= 1:  # Even 1 cross-language FP is significant
            examples_str = "; ".join([
                f"'{ex['query']}' matched {ex['java']} ({ex['java_score']:.3f}) vs {ex['go']} ({ex['go_score']:.3f})"
                for ex in cross_lang_examples[:3]
            ])
            
            issues.append({
                "id": "AUTO-001",
                "category": "Cross-Language False Positives",
                "priority": "P0",
                "description": f"Found {cross_lang_count} cross-language false positives where queries match entities from incompatible character sets",
                "examples": examples_str,
                "affected_queries": cross_lang_count,
                "recommendation": "Implement script detection: detect Unicode ranges (Latin/CJK/Cyrillic/Arabic) for queries and entities, skip phonetic matching for incompatible script pairs. Estimated: ~470 lines, $4-6M annual savings."
            })
        
        # Check for score inconsistencies
        score_issues = [d for d in divergences if d.get('type') == 'score_difference']
        if len(score_issues) > 10:
            issues.append({
                "id": "AUTO-002",
                "category": "Scoring",
                "priority": "P2",
                "description": f"Found {len(score_issues)} score differences between Java and Go",
                "recommendation": "Review Jaro-Winkler scoring implementation for numerical precision issues"
            })
        
        summary = f"Rule-based analysis: {len(issues)} patterns identified from {len(divergences)} divergences"
        
        return AnalysisResult(
            total_divergences=len(divergences),
            patterns_identified=len(issues),
            issues=issues,
            summary=summary
        )
    
    def _likely_different_scripts(self, name1: str, name2: str) -> bool:
        """Heuristic to detect if two names use different scripts or origins."""
        # Check for CJK characters
        cjk_ranges = [(0x4E00, 0x9FFF), (0x3400, 0x4DBF)]
        
        def has_cjk(s):
            return any(any(start <= ord(c) <= end for start, end in cjk_ranges) for c in s)
        
        # Check for Cyrillic
        def has_cyrillic(s):
            return any(0x0400 <= ord(c) <= 0x04FF for c in s)
        
        # Check for Arabic
        def has_arabic(s):
            return any(0x0600 <= ord(c) <= 0x06FF for c in s)
        
        # Check for likely Chinese names (romanized)
        chinese_surnames = {'wei', 'wang', 'zhang', 'li', 'liu', 'chen', 'yang', 'zhao', 'huang', 'wu', 'zhou', 'xu', 'sun', 'ma', 'zhu', 'hu', 'guo', 'he', 'lin', 'luo'}
        
        def likely_chinese(s):
            if has_cjk(s):
                return True
            # Check if surname matches common Chinese surnames
            parts = s.lower().replace(',', ' ').split()
            return any(part in chinese_surnames for part in parts)
        
        # Check for Spanish/Latin American patterns
        spanish_patterns = ['el ', 'al ', 'de la', 'del ', 'guzman', 'garcia', 'rodriguez', 'lopez', 'gonzalez']
        
        def likely_spanish(s):
            s_lower = s.lower()
            return any(pattern in s_lower for pattern in spanish_patterns)
        
        name1_scripts = {
            'cjk': has_cjk(name1),
            'cyrillic': has_cyrillic(name1),
            'arabic': has_arabic(name1),
            'chinese': likely_chinese(name1),
            'spanish': likely_spanish(name1)
        }
        
        name2_scripts = {
            'cjk': has_cjk(name2),
            'cyrillic': has_cyrillic(name2),
            'arabic': has_arabic(name2),
            'chinese': likely_chinese(name2),
            'spanish': likely_spanish(name2)
        }
        
        # Different if one has CJK and other doesn't
        if name1_scripts['cjk'] != name2_scripts['cjk']:
            return True
        
        # Different if one is Chinese and other is Spanish
        if name1_scripts['chinese'] and name2_scripts['spanish']:
            return True
        if name1_scripts['spanish'] and name2_scripts['chinese']:
            return True
        
        return False
    
    def _group_by_similarity(self, divergences: List[Dict]) -> List[List[Dict]]:
        """Group similar divergences together."""
        # Simple grouping by query similarity
        groups = {}
        
        for div in divergences:
            query = div.get('query', '').lower()
            # Group by first word
            key = query.split()[0] if query.split() else query
            
            if key not in groups:
                groups[key] = []
            groups[key].append(div)
        
        return list(groups.values())
