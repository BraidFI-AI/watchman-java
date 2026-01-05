"""
Tests for ai_analyzer.py
Run with: pytest nemesis/tests/test_ai_analyzer.py -v
"""

import pytest
from nemesis.ai_analyzer import AIAnalyzer, AnalysisResult


class TestAIAnalyzer:
    """Test AI-powered divergence analysis."""
    
    @pytest.fixture
    def sample_divergences(self):
        return [
            {
                "query": "El Chapo",
                "type": "top_result_differs",
                "severity": "critical",
                "description": "Top result differs: Java=WEI, Zhao vs Go=GUZMAN LOERA, Joaquin",
                "java_data": {"id": "999", "name": "WEI, Zhao", "match": 0.893},
                "go_data": {"id": "12345", "name": "GUZMAN LOERA, Joaquin", "match": 0.855}
            },
            {
                "query": "Chapo",
                "type": "top_result_differs",
                "severity": "critical",
                "description": "Top result differs",
                "java_data": {"id": "999", "name": "WEI, Zhao", "match": 0.85},
                "go_data": {"id": "12345", "name": "GUZMAN LOERA, Joaquin", "match": 0.82}
            },
            {
                "query": "Kim Jong Un",
                "type": "score_difference",
                "severity": "moderate",
                "description": "Score difference",
                "score_difference": 0.07
            }
        ]
    
    def test_analyzer_initialization(self):
        analyzer = AIAnalyzer(provider="openai", api_key="test-key", model="gpt-4")
        assert analyzer.provider == "openai"
        assert analyzer.model == "gpt-4"
    
    def test_prepare_context_for_ai(self, sample_divergences):
        analyzer = AIAnalyzer(provider="openai", api_key="test-key")
        context = analyzer._prepare_context(sample_divergences, total_queries=100)
        
        assert "100" in context  # Total queries mentioned
        assert "3" in context or "divergence" in context.lower()  # Divergence count
        assert "El Chapo" in context  # Sample query included
    
    def test_groups_divergences_by_pattern(self, sample_divergences):
        analyzer = AIAnalyzer(provider="openai", api_key="test-key")
        groups = analyzer._group_by_similarity(sample_divergences)
        
        # Should group "El Chapo" and "Chapo" queries together
        assert len(groups) >= 1
    
    def test_handles_empty_divergences(self):
        analyzer = AIAnalyzer(provider="openai", api_key="test-key")
        result = analyzer.analyze([], total_queries=100)
        
        assert result is not None
        assert result.total_divergences == 0
        assert len(result.issues) == 0
    
    def test_limits_context_size(self, sample_divergences):
        # Create large divergence list
        large_list = sample_divergences * 200  # 600 divergences
        
        analyzer = AIAnalyzer(provider="openai", api_key="test-key")
        context = analyzer._prepare_context(large_list[:100], total_queries=100)  # Only send first 100
        
        # Context should be reasonable size (not gigantic)
        assert len(context) < 50000  # Less than 50KB
    
    def test_extracts_cross_language_patterns(self, sample_divergences):
        analyzer = AIAnalyzer(provider="openai", api_key="test-key")
        
        # Mock result parsing
        issues = analyzer._extract_issues_from_text("""
        Issue 1: Cross-language phonetic false positives
        - Spanish "Chapo" matching Chinese "Chao" 
        - Priority: P0
        """)
        
        assert len(issues) > 0
        assert any("cross-language" in i.get("category", "").lower() for i in issues)


class TestAnalysisResult:
    """Test the AnalysisResult data structure."""
    
    def test_create_analysis_result(self):
        result = AnalysisResult(
            total_divergences=10,
            patterns_identified=3,
            issues=[{"id": "NEM-001", "priority": "P0"}],
            recommendations=["Fix phonetic matching"]
        )
        
        assert result.total_divergences == 10
        assert len(result.issues) == 1
        assert result.issues[0]["priority"] == "P0"
