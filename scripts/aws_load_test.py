#!/usr/bin/env python3
"""
AWS Load Test for Watchman Java API
====================================

Executes load tests against AWS ECS deployment for search and batch screening endpoints.
Measures throughput, latency, error rates under sustained load.

Usage:
    python aws_load_test.py --endpoint <AWS-ALB-URL> --test search --concurrent 10 --duration 60
    python aws_load_test.py --endpoint <AWS-ALB-URL> --test batch --requests 100
    python aws_load_test.py --endpoint <AWS-ALB-URL> --test all --output load_test_results.json
"""

import argparse
import requests
import json
import csv
import time
import statistics
from datetime import datetime
from typing import List, Dict, Tuple
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, asdict
from pathlib import Path
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


@dataclass
class LatencyStats:
    """Latency statistics in milliseconds"""
    min: float
    max: float
    mean: float
    median: float
    p95: float
    p99: float


@dataclass
class TestResult:
    """Result of a load test execution"""
    test_name: str
    endpoint: str
    total_requests: int
    successful_requests: int
    failed_requests: int
    duration_seconds: float
    requests_per_second: float
    latency_stats: LatencyStats
    error_details: Dict[str, int]
    timestamp: str


class WatchmanLoadTester:
    """Load tester for Watchman Java API"""

    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip('/')
        self.results: List[TestResult] = []
        
        # OFAC matches (for positive hits)
        self.OFAC_NAMES = [
            "Vladimir Vladimirovich Putin",
            "Usama bin Muhammad bin Awad BIN LADIN",
            "Kim Jong Un",
            "Bashar Hafiz al-Assad",
            "Nicolas Maduro Moros",
            "Ali Hosseini Khamenei",
            "Alexander Lukashenko",
            "VTB Bank",
            "Islamic State of Iraq and the Levant",
            "Taliban",
            "Al-Qaida",
            "Hezbollah",
            "Gazprom",
            "Rosneft"
        ]
        
        # Load 9000 clean names from static dataset
        data_file = Path(__file__).parent.parent / "test-data" / "clean_names_9000.json"
        if data_file.exists():
            with open(data_file) as f:
                self.CLEAN_NAMES_9000 = json.load(f)
            logger.info(f"Loaded {len(self.CLEAN_NAMES_9000)} clean names from {data_file.name}")
        else:
            logger.warning(f"Static dataset not found at {data_file}. Using small fallback dataset.")
            self.CLEAN_NAMES_9000 = [
                "Sarah Johnson", "Michael Chen", "Emily Rodriguez", "James Williams",
                "Jessica Martinez", "David Kim", "Ashley Thompson", "Christopher Lee"
            ]
        
        # Generate fuzzy/typo variations of OFAC names (500 variations)
        self.FUZZY_NAMES = self._generate_fuzzy_names(500)
        
        # Build 10k test dataset: 9000 clean + 500 OFAC matches + 500 fuzzy
        self.TEST_DATASET_10K = (
            self.CLEAN_NAMES_9000 +
            [self.OFAC_NAMES[i % len(self.OFAC_NAMES)] for i in range(500)] +
            self.FUZZY_NAMES
        )
        
        logger.info(f"10k test dataset prepared: {len(self.CLEAN_NAMES_9000)} clean + 500 matches + {len(self.FUZZY_NAMES)} fuzzy")
        
        # Legacy datasets for duration-based testing
        self.SEARCH_QUERIES = self.CLEAN_NAMES_9000[:99] + [self.OFAC_NAMES[0]]
        self.BATCH_TEST_ITEMS = (
            [{"name": name, "type": "individual"} for name in self.CLEAN_NAMES_9000[:985]] +
            [{"name": self.OFAC_NAMES[i % len(self.OFAC_NAMES)], "type": "individual"} for i in range(15)]
        )
    
    def _generate_fuzzy_names(self, count: int) -> List[str]:
        """Generate fuzzy/typo variations of OFAC names"""
        fuzzy_patterns = [
            ("Vladimir Putin", "Vladmir Putin"),
            ("Vladimir Putin", "Vladimir Puttin"),
            ("Vladimir Putin", "Vlad Putin"),
            ("Usama bin Laden", "Osama Bin Laden"),
            ("Usama bin Laden", "Usama Bin Ladin"),
            ("Bashar al-Assad", "Bashar Assad"),
            ("Bashar al-Assad", "Bashar Asad"),
            ("Nicolas Maduro", "Nicholas Maduro"),
            ("Nicolas Maduro", "Nicolas Madoro"),
            ("Kim Jong Un", "Kim Jong-Un"),
        ]
        
        fuzzy_names = []
        for i in range(count):
            base_name, fuzzy_version = fuzzy_patterns[i % len(fuzzy_patterns)]
            fuzzy_names.append(fuzzy_version)
        
        return fuzzy_names

    def test_search_endpoint(self, concurrent_users: int, duration_seconds: int) -> TestResult:
        """
        Load test the /v1/search endpoint with concurrent requests.
        
        Args:
            concurrent_users: Number of concurrent threads
            duration_seconds: Test duration in seconds
        """
        logger.info(f"Starting search endpoint load test: {concurrent_users} concurrent users, {duration_seconds}s duration")
        
        latencies = []
        successful = 0
        failed = 0
        errors: Dict[str, int] = {}
        start_time = time.time()
        
        def make_search_request(query: str) -> Tuple[bool, float, str]:
            """Make a single search request. Returns (success, latency_ms, error_msg)"""
            url = f"{self.base_url}/v1/search"
            params = {"name": query, "limit": 10}
            
            req_start = time.time()
            try:
                response = requests.get(url, params=params, timeout=90)
                latency_ms = (time.time() - req_start) * 1000
                
                if response.status_code == 200:
                    return True, latency_ms, ""
                else:
                    return False, latency_ms, f"HTTP {response.status_code}"
            except requests.exceptions.Timeout:
                latency_ms = (time.time() - req_start) * 1000
                return False, latency_ms, "Timeout"
            except Exception as e:
                latency_ms = (time.time() - req_start) * 1000
                return False, latency_ms, str(e)

        # Run load test until duration expires
        with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
            futures = []
            query_index = 0
            last_progress_log = time.time()
            
            while time.time() - start_time < duration_seconds:
                # Submit new request
                query = self.SEARCH_QUERIES[query_index % len(self.SEARCH_QUERIES)]
                future = executor.submit(make_search_request, query)
                futures.append(future)
                query_index += 1
                
                # Process completed requests
                done_futures = [f for f in futures if f.done()]
                for future in done_futures:
                    success, latency, error = future.result()
                    latencies.append(latency)
                    
                    if success:
                        successful += 1
                    else:
                        failed += 1
                        errors[error] = errors.get(error, 0) + 1
                    
                    futures.remove(future)
                
                # Progress logging every 10 seconds
                if time.time() - last_progress_log >= 10:
                    elapsed = time.time() - start_time
                    total = successful + failed
                    logger.info(f"Progress: {total} requests ({successful} success, {failed} failed) - {elapsed:.0f}s elapsed")
                    last_progress_log = time.time()
                
                # Small delay to control request rate
                time.sleep(0.01)
            
            # Wait for remaining requests
            logger.info(f"Waiting for {len(futures)} remaining requests to complete (max 90s timeout each)...")
            for i, future in enumerate(as_completed(futures)):
                success, latency, error = future.result()
                latencies.append(latency)
                
                if (i + 1) % 5 == 0:
                    logger.info(f"Completed {i + 1}/{len(futures)} remaining requests")
                
                if success:
                    successful += 1
                else:
                    failed += 1
                    errors[error] = errors.get(error, 0) + 1

        actual_duration = time.time() - start_time
        total_requests = successful + failed
        
        latency_stats = LatencyStats(
            min=min(latencies) if latencies else 0,
            max=max(latencies) if latencies else 0,
            mean=statistics.mean(latencies) if latencies else 0,
            median=statistics.median(latencies) if latencies else 0,
            p95=statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else (max(latencies) if latencies else 0),
            p99=statistics.quantiles(latencies, n=100)[98] if len(latencies) >= 100 else (max(latencies) if latencies else 0)
        )
        
        result = TestResult(
            test_name="Search Endpoint Load Test",
            endpoint=f"{self.base_url}/v1/search",
            total_requests=total_requests,
            successful_requests=successful,
            failed_requests=failed,
            duration_seconds=actual_duration,
            requests_per_second=total_requests / actual_duration,
            latency_stats=latency_stats,
            error_details=errors,
            timestamp=datetime.now().isoformat()
        )
        
        self.results.append(result)
        return result

    def test_search_fixed_requests(self, concurrent_users: int, total_requests: int) -> TestResult:
        """
        Load test with a fixed number of requests (e.g., 10k for production simulation).
        Uses the 10k static dataset for repeatable testing.
        
        Args:
            concurrent_users: Number of concurrent threads
            total_requests: Exact number of requests to execute
        """
        logger.info(f"Starting fixed-request search test: {total_requests} requests with {concurrent_users} concurrent threads")
        
        latencies = []
        successful = 0
        failed = 0
        errors: Dict[str, int] = {}
        start_time = time.time()
        
        def make_search_request(query: str) -> Tuple[bool, float, str]:
            """Make a single search request. Returns (success, latency_ms, error_msg)"""
            url = f"{self.base_url}/v1/search"
            params = {"name": query, "limit": 10}
            
            req_start = time.time()
            try:
                response = requests.get(url, params=params, timeout=90)
                latency_ms = (time.time() - req_start) * 1000
                
                if response.status_code == 200:
                    return True, latency_ms, ""
                else:
                    return False, latency_ms, f"HTTP {response.status_code}"
            except requests.exceptions.Timeout:
                latency_ms = (time.time() - req_start) * 1000
                return False, latency_ms, "Timeout"
            except Exception as e:
                latency_ms = (time.time() - req_start) * 1000
                return False, latency_ms, str(e)

        # Submit all requests using thread pool
        with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
            futures = []
            last_progress_log = time.time()
            
            # Submit all requests from the 10k dataset
            for i in range(total_requests):
                query = self.TEST_DATASET_10K[i % len(self.TEST_DATASET_10K)]
                future = executor.submit(make_search_request, query)
                futures.append(future)
                
                # Progress logging every 10 seconds
                if time.time() - last_progress_log >= 10:
                    completed = sum(1 for f in futures if f.done())
                    logger.info(f"Progress: {completed}/{total_requests} requests submitted ({successful} success, {failed} failed)")
                    last_progress_log = time.time()
            
            logger.info(f"All {total_requests} requests submitted. Waiting for completion...")
            
            # Process results as they complete
            for i, future in enumerate(as_completed(futures)):
                success, latency, error = future.result()
                latencies.append(latency)
                
                if success:
                    successful += 1
                else:
                    failed += 1
                    errors[error] = errors.get(error, 0) + 1
                
                # Progress updates every 500 requests
                if (i + 1) % 500 == 0:
                    elapsed = time.time() - start_time
                    logger.info(f"Completed {i + 1}/{total_requests} requests ({successful} success, {failed} failed) - {elapsed:.1f}s elapsed")

        actual_duration = time.time() - start_time
        total_completed = successful + failed
        
        latency_stats = LatencyStats(
            min=min(latencies) if latencies else 0,
            max=max(latencies) if latencies else 0,
            mean=statistics.mean(latencies) if latencies else 0,
            median=statistics.median(latencies) if latencies else 0,
            p95=statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else (max(latencies) if latencies else 0),
            p99=statistics.quantiles(latencies, n=100)[98] if len(latencies) >= 100 else (max(latencies) if latencies else 0)
        )
        
        result = TestResult(
            test_name=f"Search Endpoint Fixed-Request Test ({total_requests} requests)",
            endpoint=f"{self.base_url}/v1/search",
            total_requests=total_completed,
            successful_requests=successful,
            failed_requests=failed,
            duration_seconds=actual_duration,
            requests_per_second=total_completed / actual_duration if actual_duration > 0 else 0,
            latency_stats=latency_stats,
            error_details=errors,
            timestamp=datetime.now().isoformat()
        )
        
        self.results.append(result)
        return result

    def test_batch_endpoint(self, num_requests: int, batch_size: int = 10) -> TestResult:
        """
        Load test the /v1/search/batch endpoint.
        
        Args:
            num_requests: Number of batch requests to send
            batch_size: Number of items per batch
        """
        logger.info(f"Starting batch endpoint load test: {num_requests} requests, {batch_size} items per batch")
        
        latencies = []
        successful = 0
        failed = 0
        errors: Dict[str, int] = {}
        start_time = time.time()
        
        def make_batch_request() -> Tuple[bool, float, str]:
            """Make a single batch request. Returns (success, latency_ms, error_msg)"""
            url = f"{self.base_url}/v1/search/batch"
            
            # Use fixed test items, cycle if batch_size > available
            items = [self.BATCH_TEST_ITEMS[i % len(self.BATCH_TEST_ITEMS)] 
                     for i in range(batch_size)]
            
            payload = {
                "items": items,
                "minMatch": 0.88,
                "limit": 10,
                "trace": False
            }
            
            req_start = time.time()
            try:
                response = requests.post(url, json=payload, timeout=180)
                latency_ms = (time.time() - req_start) * 1000
                
                if response.status_code == 200:
                    return True, latency_ms, ""
                else:
                    return False, latency_ms, f"HTTP {response.status_code}"
            except requests.exceptions.Timeout:
                latency_ms = (time.time() - req_start) * 1000
                return False, latency_ms, "Timeout"
            except Exception as e:
                latency_ms = (time.time() - req_start) * 1000
                return False, latency_ms, str(e)

        # Execute batch requests sequentially (batch is already heavy)
        for i in range(num_requests):
            logger.info(f"[{i+1}/{num_requests}] Sending batch request with {batch_size} names...")
            req_start_time = time.time()
            success, latency, error = make_batch_request()
            latencies.append(latency)
            
            if success:
                successful += 1
                logger.info(f"[{i+1}/{num_requests}] ✓ Completed in {latency/1000:.1f}s ({batch_size} names processed)")
            else:
                failed += 1
                errors[error] = errors.get(error, 0) + 1
                logger.error(f"[{i+1}/{num_requests}] ✗ Failed: {error} (took {latency/1000:.1f}s)")
            
            # Show running statistics
            elapsed = time.time() - start_time
            completed = i + 1
            names_processed = completed * batch_size
            names_per_sec = names_processed / elapsed if elapsed > 0 else 0
            logger.info(f"   Running stats: {names_processed} names in {elapsed:.1f}s = {names_per_sec:.1f} names/sec")

        actual_duration = time.time() - start_time
        total_requests = successful + failed
        
        latency_stats = LatencyStats(
            min=min(latencies) if latencies else 0,
            max=max(latencies) if latencies else 0,
            mean=statistics.mean(latencies) if latencies else 0,
            median=statistics.median(latencies) if latencies else 0,
            p95=statistics.quantiles(latencies, n=20)[18] if len(latencies) >= 20 else (max(latencies) if latencies else 0),
            p99=statistics.quantiles(latencies, n=100)[98] if len(latencies) >= 100 else (max(latencies) if latencies else 0)
        )
        
        result = TestResult(
            test_name=f"Batch Endpoint Load Test (batch_size={batch_size})",
            endpoint=f"{self.base_url}/v1/search/batch",
            total_requests=total_requests,
            successful_requests=successful,
            failed_requests=failed,
            duration_seconds=actual_duration,
            requests_per_second=total_requests / actual_duration,
            latency_stats=latency_stats,
            error_details=errors,
            timestamp=datetime.now().isoformat()
        )
        
        self.results.append(result)
        return result

    def test_health_endpoint(self) -> Dict:
        """Test the health endpoint to verify service is operational"""
        url = f"{self.base_url}/v1/health"
        try:
            response = requests.get(url, timeout=10)
            if response.status_code == 200:
                return response.json()
            else:
                logger.error(f"Health check failed: HTTP {response.status_code}")
                return {"status": "DOWN", "error": f"HTTP {response.status_code}"}
        except Exception as e:
            logger.error(f"Health check failed: {e}")
            return {"status": "DOWN", "error": str(e)}

    def generate_report(self) -> str:
        """Generate human-readable report"""
        report = f"""
AWS WATCHMAN JAVA LOAD TEST REPORT
===================================
Generated: {datetime.now().isoformat()}
Base URL: {self.base_url}

"""
        for result in self.results:
            success_rate = (result.successful_requests / result.total_requests * 100) if result.total_requests > 0 else 0
            
            report += f"""
{result.test_name}
{'-' * len(result.test_name)}
Endpoint:           {result.endpoint}
Duration:           {result.duration_seconds:.2f}s
Total Requests:     {result.total_requests}
Successful:         {result.successful_requests} ({success_rate:.1f}%)
Failed:             {result.failed_requests}
Throughput:         {result.requests_per_second:.2f} req/s

Latency Statistics (ms):
  Min:      {result.latency_stats.min:.2f}
  Max:      {result.latency_stats.max:.2f}
  Mean:     {result.latency_stats.mean:.2f}
  Median:   {result.latency_stats.median:.2f}
  P95:      {result.latency_stats.p95:.2f}
  P99:      {result.latency_stats.p99:.2f}
"""
            if result.error_details:
                report += "\nErrors:\n"
                for error, count in result.error_details.items():
                    report += f"  {error}: {count}\n"
        
        return report

    def export_results(self, output_file: str, format: str = 'json'):
        """Export results to JSON or CSV file"""
        if format == 'json':
            data = {
                "base_url": self.base_url,
                "timestamp": datetime.now().isoformat(),
                "results": [asdict(r) for r in self.results]
            }
            
            with open(output_file, 'w') as f:
                json.dump(data, f, indent=2)
            
            logger.info(f"Results exported to {output_file}")
        
        elif format == 'csv':
            with open(output_file, 'w', newline='') as f:
                writer = csv.writer(f)
                
                # Header
                writer.writerow([
                    'Test Name', 'Endpoint', 'Total Requests', 'Successful', 'Failed',
                    'Success Rate %', 'Duration (s)', 'Throughput (req/s)',
                    'Latency Min (ms)', 'Latency Max (ms)', 'Latency Mean (ms)',
                    'Latency Median (ms)', 'Latency P95 (ms)', 'Latency P99 (ms)',
                    'Errors'
                ])
                
                # Data rows
                for result in self.results:
                    success_rate = (result.successful_requests / result.total_requests * 100) if result.total_requests > 0 else 0
                    errors_str = '; '.join([f"{k}: {v}" for k, v in result.error_details.items()]) if result.error_details else 'None'
                    
                    writer.writerow([
                        result.test_name,
                        result.endpoint,
                        result.total_requests,
                        result.successful_requests,
                        result.failed_requests,
                        f"{success_rate:.2f}",
                        f"{result.duration_seconds:.2f}",
                        f"{result.requests_per_second:.2f}",
                        f"{result.latency_stats.min:.2f}",
                        f"{result.latency_stats.max:.2f}",
                        f"{result.latency_stats.mean:.2f}",
                        f"{result.latency_stats.median:.2f}",
                        f"{result.latency_stats.p95:.2f}",
                        f"{result.latency_stats.p99:.2f}",
                        errors_str
                    ])
            
            logger.info(f"Results exported to {output_file}")
        
        else:
            raise ValueError(f"Unsupported format: {format}. Use 'json' or 'csv'")


def main():
    parser = argparse.ArgumentParser(
        description='AWS Load Test for Watchman Java API',
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    
    parser.add_argument('--endpoint', required=True,
                        help='AWS ALB endpoint URL (e.g., http://watchman-java-alb-123.us-east-1.elb.amazonaws.com)')
    parser.add_argument('--requests', type=int, default=10,
                        help='Number of batch requests (default: 10)')
    parser.add_argument('--batch-size', type=int, default=1000,
                        help='Items per batch request (default: 1000, max: 1000)')
    parser.add_argument('--output', default='batch_load_test',
                        help='Output file for results (without extension)')
    parser.add_argument('--format', choices=['json', 'csv', 'both'], default='both',
                        help='Output format (default: both)')
    
    args = parser.parse_args()
    
    tester = WatchmanLoadTester(args.endpoint)
    
    # Health check
    logger.info("Running health check...")
    health = tester.test_health_endpoint()
    logger.info(f"Health status: {health}")
    
    if health.get('status') not in ['UP', 'healthy']:
        logger.error("Service is not healthy. Aborting load test.")
        return
    
    # Run BATCH test only (this is what we use in production)
    logger.info("=" * 60)
    logger.info("BATCH API LOAD TEST - Testing /v1/search/batch endpoint")
    logger.info("=" * 60)
    tester.test_batch_endpoint(args.requests, args.batch_size)
    
    # Generate report
    report = tester.generate_report()
    print(report)
    
    # Export results
    if args.format == 'both':
        tester.export_results(f"{args.output}.json", format='json')
        tester.export_results(f"{args.output}.csv", format='csv')
    elif args.format == 'json':
        output_file = args.output if args.output.endswith('.json') else f"{args.output}.json"
        tester.export_results(output_file, format='json')
    else:  # csv
        output_file = args.output if args.output.endswith('.csv') else f"{args.output}.csv"
        tester.export_results(output_file, format='csv')


if __name__ == '__main__':
    main()
