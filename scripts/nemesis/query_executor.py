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
    """Results from executing a query against Java, Go, and/or external provider APIs."""
    query: str
    java_results: Optional[List[Dict]] = None
    go_results: Optional[List[Dict]] = None
    external_results: Optional[List[Dict]] = None
    java_time_ms: Optional[float] = None
    go_time_ms: Optional[float] = None
    external_time_ms: Optional[float] = None
    java_error: Optional[str] = None
    go_error: Optional[str] = None
    external_error: Optional[str] = None
    java_trace: Optional[Dict] = None  # Scoring trace data from Java


class QueryExecutor:
    """Executes queries against Watchman APIs with retry logic."""
    
    def __init__(self, java_url: str, go_url: Optional[str] = None, external_adapter=None):
        """
        Initialize executor with API URLs.
        
        Args:
            java_url: Java Watchman API URL
            go_url: Go Watchman API URL (optional)
            external_adapter: External provider adapter instance (optional)
        """
        self.java_url = java_url
        self.go_url = go_url
        self.external_adapter = external_adapter
        
    def execute(
        self, 
        query: str, 
        compare_go: bool = False,
        compare_external: bool = False,
        enable_trace: bool = False,
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
            compare_external: Whether to also query external provider
            enable_trace: Whether to request scoring trace from Java API
            timeout: Request timeout in seconds
            max_retries: Number of retry attempts
            limit: Maximum results to return
            min_match: Minimum match score threshold
            
        Returns:
            QueryResult with API responses
        """
        result = QueryResult(query=query)
        
        # Execute Java query (with optional trace)
        java_data = self._call_api(
            self.java_url, query, timeout, max_retries, limit, min_match, enable_trace
        )
        result.java_results = java_data[0]
        result.java_time_ms = java_data[1]
        result.java_error = java_data[2]
        result.java_trace = java_data[3] if enable_trace else None
        
        # Execute Go query if comparison enabled
        if compare_go and self.go_url:
            go_data = self._call_api(
                self.go_url, query, timeout, max_retries, limit, min_match, enable_trace=False
            )
            result.go_results = go_data[0]
            result.go_time_ms = go_data[1]
            result.go_error = go_data[2]
        
        # Execute external provider query if comparison enabled
        if compare_external and self.external_adapter:
            result.external_results, result.external_time_ms, result.external_error = self._call_external(
                query, min_match, timeout
            )
            
        return result
        
    def execute_batch(
        self, 
        test_cases: List, 
        compare_go: bool = False,
        compare_external: bool = False,
        timeout: float = 10.0,
        show_progress: bool = True
    ) -> List[QueryResult]:
        """
        Execute a batch of test queries.
        
        Args:
            test_cases: List of TestCase objects
            compare_go: Whether to compare with Go implementation
            compare_external: Whether to compare with external provider
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
                
            result = self.execute(
                tc.query, 
                compare_go=compare_go,
                compare_external=compare_external,
                timeout=timeout
            )
            results.append(result)
            
        return results
        
    def _call_api(
        self, 
        url: str, 
        query: str, 
        timeout: float, 
        max_retries: int,
        limit: int,
        min_match: float,
        enable_trace: bool = False
    ) -> tuple:
        """
        Call Watchman API with retry logic.
        
        Args:
            enable_trace: Whether to request scoring trace (Java only)
        
        Returns:
            Tuple of (results, elapsed_ms, error_message, trace_data)
        """
        for attempt in range(max_retries):
            try:
                start = time.time()
                params = {
                    "name": query, 
                    "limit": limit, 
                    "minMatch": min_match
                }
                
                # Add trace parameter if enabled (Java only feature)
                if enable_trace:
                    params["trace"] = "true"
                
                response = requests.get(
                    f"{url}/v2/search",
                    params=params,
                    timeout=timeout
                )
                elapsed = (time.time() - start) * 1000
                
                if response.status_code == 200:
                    data = response.json()
                    entities = data.get("entities", [])
                    trace_data = data.get("trace") if enable_trace else None
                    return entities, elapsed, None, trace_data
                else:
                    error_msg = f"HTTP {response.status_code}"
                    if attempt == max_retries - 1:
                        return None, None, error_msg, None
                        
            except requests.Timeout:
                if attempt == max_retries - 1:
                    return None, None, "Timeout", None
            except requests.ConnectionError:
                if attempt == max_retries - 1:
                    return None, None, "Connection error", None
            except Exception as e:
                if attempt == max_retries - 1:
                    return None, None, str(e), None
                    
            # Brief pause before retry
            if attempt < max_retries - 1:
                time.sleep(0.5)
                
        return None, None, "Max retries exceeded", None
    
    def _call_external(self, query: str, min_match: float, timeout: float) -> tuple:
        """
        Call external provider (ofac-api.com) with the query.
        
        Args:
            query: Search query string
            min_match: Minimum match score (0.0-1.0)
            timeout: Request timeout in seconds
            
        Returns:
            Tuple of (results, elapsed_ms, error_message)
        """
        if not self.external_adapter:
            return None, None, "No external adapter configured"
            
        # Convert min_match from 0.0-1.0 to 0-100 for ofac-api.com
        min_score = int(min_match * 100)
        
        # Search using adapter
        results_by_query, elapsed, error = self.external_adapter.search(
            query,
            min_score=min_score,
            sources=["sdn"],  # SDN-only for apples-to-apples
            timeout=timeout
        )
        
        if error:
            return None, None, error
            
        # Extract results for this specific query
        results = results_by_query.get(query, [])
        return results, elapsed, None
