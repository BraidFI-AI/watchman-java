"""
Tests for result_analyzer.py
Run with: pytest nemesis/tests/test_result_analyzer.py -v
"""

import pytest
from nemesis.result_analyzer import ResultAnalyzer, Divergence, DivergenceType


class TestResultAnalyzer:
    """Test divergence detection between Java and Go results."""
    
    def test_no_divergence_when_identical(self):
        java_results = [{"id": "SDN-1", "name": "Test", "match": 0.95}]
        go_results = [{"id": "SDN-1", "name": "Test", "match": 0.95}]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        assert len(divergences) == 0
        
    def test_detect_different_top_result(self):
        java_results = [{"id": "SDN-1", "name": "Java Top", "match": 0.95}]
        go_results = [{"id": "SDN-2", "name": "Go Top", "match": 0.95}]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        
        # Should detect TOP_RESULT_DIFFERS (also creates extra result divergences)
        top_result_divs = [d for d in divergences if d.type == DivergenceType.TOP_RESULT_DIFFERS]
        assert len(top_result_divs) == 1
        assert top_result_divs[0].severity == "critical"
        
    def test_detect_score_difference_critical(self):
        java_results = [{"id": "SDN-1", "name": "Test", "match": 0.95}]
        go_results = [{"id": "SDN-1", "name": "Test", "match": 0.80}]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        assert len(divergences) >= 1
        
        score_divs = [d for d in divergences if d.type == DivergenceType.SCORE_DIFFERENCE]
        assert len(score_divs) > 0
        assert score_divs[0].severity == "critical"  # >0.10 diff
        
    def test_detect_score_difference_moderate(self):
        java_results = [{"id": "SDN-1", "name": "Test", "match": 0.90}]
        go_results = [{"id": "SDN-1", "name": "Test", "match": 0.85}]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        
        score_divs = [d for d in divergences if d.type == DivergenceType.SCORE_DIFFERENCE]
        assert len(score_divs) > 0
        assert score_divs[0].severity == "moderate"  # 0.05-0.10 diff
        
    def test_detect_java_extra_results(self):
        java_results = [
            {"id": "SDN-1", "name": "Both", "match": 0.95},
            {"id": "SDN-2", "name": "Java Only", "match": 0.85}
        ]
        go_results = [
            {"id": "SDN-1", "name": "Both", "match": 0.95}
        ]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        
        extra_divs = [d for d in divergences if d.type == DivergenceType.JAVA_EXTRA_RESULT]
        assert len(extra_divs) > 0
        
    def test_detect_go_extra_results(self):
        java_results = [
            {"id": "SDN-1", "name": "Both", "match": 0.95}
        ]
        go_results = [
            {"id": "SDN-1", "name": "Both", "match": 0.95},
            {"id": "SDN-2", "name": "Go Only", "match": 0.85}
        ]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        
        extra_divs = [d for d in divergences if d.type == DivergenceType.GO_EXTRA_RESULT]
        assert len(extra_divs) > 0
        
    def test_handles_empty_results(self):
        analyzer = ResultAnalyzer()
        
        # Both empty - no divergence
        divergences = analyzer.compare([], [])
        assert len(divergences) == 0
        
        # Java empty, Go has results
        divergences = analyzer.compare([], [{"id": "SDN-1", "name": "Test", "match": 0.95}])
        assert len(divergences) > 0
        
    def test_calculates_summary_stats(self):
        java_results = [
            {"id": "SDN-1", "name": "Different", "match": 0.95},
            {"id": "SDN-2", "name": "Java", "match": 0.85}
        ]
        go_results = [
            {"id": "SDN-3", "name": "Different", "match": 0.90},
            {"id": "SDN-4", "name": "Go", "match": 0.80}
        ]
        
        analyzer = ResultAnalyzer()
        summary = analyzer.get_summary(java_results, go_results)
        
        assert "total_divergences" in summary
        assert "by_type" in summary
        assert "by_severity" in summary
        
    def test_identify_multiple_divergences(self):
        java_results = [
            {"id": "SDN-1", "name": "Test1", "match": 0.95},
            {"id": "SDN-2", "name": "Test2", "match": 0.85}
        ]
        go_results = [
            {"id": "SDN-3", "name": "Test3", "match": 0.90}
        ]
        
        analyzer = ResultAnalyzer()
        divergences = analyzer.compare(java_results, go_results)
        
        # Should detect: top result differs + extra results
        assert len(divergences) >= 2
