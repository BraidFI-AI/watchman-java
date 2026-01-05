"""
Query Executor - Runs queries against Watchman APIs
Handles API calls with retry logic and performance tracking
"""

from dataclasses import dataclass
from typing import List, Optional, Dict
import requests
import time


@dataclass
class QueryResult:
    """Results from executing a query against Java and/or Go APIs."""
    query: str
    java_results: Optional[List[Dict]] = None
    go_results: Optional[List[Dict]] = None
    java_time_ms: Optional[float] = None
    go_time_ms: Optional[float] = None
    java_error: Optional[str] = None
    go_error: Optional[str] = None


class QueryExecutor:
    """Executes queries against Watchman APIs with retry logic."""
    
    def __init__(self, java_url: str, go_url: Optional[str] = None):
        """
        Initialize executor with API URLs.
        
        Args:
            java_url: Java Watchman API URL
            go_url: Go Watchman API URL (optional)
        """
        self.java_url = java_url
        self.go_url = go_url
        
    def execute(
        self, 
        query: str, 
        compare_go: bool = False, 
        timeout: float = 10.0, 
        max_retries: int = 3,
        limit: int = 5,
        min_match: float = 0.80
    ) -> QueryResult:
        """
        Execute a single query.
        
        Args:
            query: Search query string
            compare_go: Whether to also query Go API
            timeout: Request timeout in seconds
            max_retries: Number of retry attempts
            limit: Maximum results to return
            min_match: Minimum match score threshold
            
        Returns:
            QueryResult with API responses
        """
        result = QueryResult(query=query)
        
        # Execute Java query
        result.java_results, result.java_time_ms, result.java_error = self._call_api(
            self.java_url, query, timeout, max_retries, limit, min_match
        )
        
        # Execute Go query if comparison enabled
        if compare_go and self.go_url:
            result.go_results, result.go_time_ms, result.go_error = self._call_api(
                self.go_url, query, timeout, max_retries, limit, min_match
            )
            
        return result
        
    def execute_batch(
        self, 
        test_cases: List, 
        compare_go: bool = False,
        timeout: float = 10.0,
        show_progress: bool = True
    ) -> List[QueryResult]:
        """
        Execute a batch of test queries.
        
        Args:
            test_cases: List of TestCase objects
            compare_go: Whether to compare with Go implementation
            timeout: Request timeout in seconds
            show_progress: Whether to print progress
            
        Returns:
            List of QueryResult objects
        """
        results = []
        total = len(test_cases)
        
        for i, tc in enumerate(test_cases):
            if show_progress and (i % 10 == 0 or i == total - 1):
                print(f"  Progress: {i+1}/{total} queries...")
                
            result = self.execute(tc.query, compare_go=compare_go, timeout=timeout)
            results.append(result)
            
        return results
        
    def _call_api(
        self, 
        url: str, 
        query: str, 
        timeout: float, 
        max_retries: int,
        limit: int,
        min_match: float
    ) -> tuple:
        """
        Call Watchman API with retry logic.
        
        Returns:
            Tuple of (results, elapsed_ms, error_message)
        """
        for attempt in range(max_retries):
            try:
                start = time.time()
                response = requests.get(
                    f"{url}/v2/search",
                    params={
                        "name": query, 
                        "limit": limit, 
                        "minMatch": min_match
                    },
                    timeout=timeout
                )
                elapsed = (time.time() - start) * 1000
                
                if response.status_code == 200:
                    entities = response.json().get("entities", [])
                    return entities, elapsed, None
                else:
                    error_msg = f"HTTP {response.status_code}"
                    if attempt == max_retries - 1:
                        return None, None, error_msg
                        
            except requests.Timeout:
                if attempt == max_retries - 1:
                    return None, None, "Timeout"
            except requests.ConnectionError:
                if attempt == max_retries - 1:
                    return None, None, "Connection error"
            except Exception as e:
                if attempt == max_retries - 1:
                    return None, None, str(e)
                    
            # Brief pause before retry
            if attempt < max_retries - 1:
                time.sleep(0.5)
                
        return None, None, "Max retries exceeded"
