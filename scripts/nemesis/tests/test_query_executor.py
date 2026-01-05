"""
Tests for query_executor.py
Run with: pytest nemesis/tests/test_query_executor.py -v
"""

import pytest
from nemesis.query_executor import QueryExecutor, QueryResult
from nemesis.test_generator import TestCase


class TestQueryResult:
    """Test the QueryResult data structure."""
    
    def test_create_basic_result(self):
        result = QueryResult(query="El Chapo")
        assert result.query == "El Chapo"
        assert result.java_results is None
        assert result.go_results is None
        
    def test_result_with_data(self):
        result = QueryResult(
            query="Test",
            java_results=[{"id": "SDN-1", "name": "Test", "match": 0.95}],
            java_time_ms=45.2
        )
        assert len(result.java_results) == 1
        assert result.java_time_ms == 45.2


class TestQueryExecutor:
    """Test query execution against APIs."""
    
    @pytest.fixture
    def executor(self):
        return QueryExecutor(
            java_url="http://localhost:8080",
            go_url="https://watchman-go.fly.dev"
        )
    
    def test_executor_initialization(self, executor):
        assert executor.java_url == "http://localhost:8080"
        assert executor.go_url == "https://watchman-go.fly.dev"
        
    def test_execute_single_query_returns_result(self, executor):
        # This will actually call the real API
        result = executor.execute("Putin", compare_go=False, timeout=5.0)
        assert result.query == "Putin"
        assert isinstance(result, QueryResult)
        
    def test_execute_batch_returns_correct_count(self, executor):
        test_cases = [
            TestCase(query="Putin", strategy="test"),
            TestCase(query="Assad", strategy="test"),
            TestCase(query="Kim", strategy="test")
        ]
        results = executor.execute_batch(test_cases, compare_go=False)
        assert len(results) == 3
        
    def test_execute_batch_preserves_query_order(self, executor):
        test_cases = [
            TestCase(query="Query1", strategy="test"),
            TestCase(query="Query2", strategy="test"),
            TestCase(query="Query3", strategy="test")
        ]
        results = executor.execute_batch(test_cases, compare_go=False)
        assert results[0].query == "Query1"
        assert results[1].query == "Query2"
        assert results[2].query == "Query3"
        
    def test_handles_invalid_url_gracefully(self):
        executor = QueryExecutor(
            java_url="http://nonexistent-host-12345.local:8080"
        )
        result = executor.execute("Test", compare_go=False, timeout=2.0, max_retries=1)
        # Should not crash, should return result with error indication
        assert result.query == "Test"
        assert result.java_results is None or len(result.java_results) == 0
