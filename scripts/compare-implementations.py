#!/usr/bin/env python3
"""
Watchman Go vs Java Implementation Comparison Tool

Sends identical requests to both Go and Java Watchman APIs and generates
a detailed comparison report.

Usage:
    python3 scripts/compare-implementations.py

    # Custom endpoints
    python3 scripts/compare-implementations.py \
        --go-url https://watchman-go.fly.dev \
        --java-url https://watchman-java.fly.dev

    # Output formats
    python3 scripts/compare-implementations.py --output html
    python3 scripts/compare-implementations.py --output csv
    python3 scripts/compare-implementations.py --output json
"""

import argparse
import csv
import json
import os
import sys
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import urlencode
from pathlib import Path

try:
    import requests
except ImportError:
    print("Error: 'requests' library not installed.")
    print("Install with: pip3 install requests")
    sys.exit(1)


@dataclass
class SearchResult:
    """Normalized search result from either implementation"""
    name: str
    entity_type: str
    source: str
    entity_id: str
    score: float
    alt_names: List[str] = field(default_factory=list)


@dataclass
class ComparisonResult:
    """Result of comparing a single name search"""
    test_id: int
    name: str
    category: str
    expected: str
    
    go_status: int = 0
    go_results_count: int = 0
    go_top_match: str = ""
    go_top_score: float = 0.0
    go_response_ms: float = 0.0
    go_error: str = ""
    
    java_status: int = 0
    java_results_count: int = 0
    java_top_match: str = ""
    java_top_score: float = 0.0
    java_response_ms: float = 0.0
    java_error: str = ""
    
    score_diff: float = 0.0
    count_diff: int = 0
    top_match_same: bool = False
    passed: bool = False
    notes: str = ""


class WatchmanClient:
    """Client for calling Watchman API"""
    
    def __init__(self, base_url: str, name: str, timeout: int = 30):
        self.base_url = base_url.rstrip('/')
        self.name = name
        self.timeout = timeout
    
    def health_check(self) -> Tuple[bool, str]:
        """Check if service is healthy"""
        # Try Go-style endpoint first
        try:
            resp = requests.get(f"{self.base_url}/v2/listinfo", timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                lists = data.get('lists', {})
                if isinstance(lists, dict):
                    total = sum(lists.values())
                else:
                    total = sum(l.get('entityCount', 0) for l in lists)
                return True, f"Entities: {total}"
        except:
            pass
        
        # Try Java-style health endpoint
        try:
            resp = requests.get(f"{self.base_url}/health", timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                return True, f"Entities: {data.get('entityCount', 'unknown')}"
        except:
            pass
        
        return False, "Not reachable"
    
    def search(self, name: str, limit: int = 10, min_match: float = 0.70) -> Tuple[int, List[SearchResult], float, str]:
        """
        Search for a name and return (status_code, results, response_time_ms, error)
        """
        params = {
            "name": name,
            "limit": limit,
            "minMatch": min_match
        }
        
        url = f"{self.base_url}/v2/search?{urlencode(params)}"
        
        start = time.time()
        try:
            resp = requests.get(url, timeout=self.timeout)
            elapsed_ms = (time.time() - start) * 1000
            
            if resp.status_code != 200:
                return resp.status_code, [], elapsed_ms, f"HTTP {resp.status_code}"
            
            data = resp.json()
            results = self._parse_results(data)
            return resp.status_code, results, elapsed_ms, ""
            
        except requests.exceptions.Timeout:
            elapsed_ms = (time.time() - start) * 1000
            return 0, [], elapsed_ms, "Timeout"
        except requests.exceptions.ConnectionError as e:
            elapsed_ms = (time.time() - start) * 1000
            return 0, [], elapsed_ms, f"Connection error"
        except Exception as e:
            elapsed_ms = (time.time() - start) * 1000
            return 0, [], elapsed_ms, str(e)
    
    def _parse_results(self, data: Dict) -> List[SearchResult]:
        """Parse API response into normalized SearchResult objects"""
        results = []
        
        # Try Go format (entities array)
        entities = data.get('entities', [])
        if entities:
            for e in entities:
                results.append(SearchResult(
                    name=e.get('name', ''),
                    entity_type=self._normalize_type(e.get('entityType', '')),
                    source=self._normalize_source(e.get('sourceList', '')),
                    entity_id=e.get('sourceID', ''),
                    score=round(e.get('match', 0), 4),
                ))
            return results
        
        # Try Java format (results array)
        items = data.get('results', [])
        for r in items:
            results.append(SearchResult(
                name=r.get('name', ''),
                entity_type=self._normalize_type(r.get('type', '')),
                source=self._normalize_source(r.get('source', '')),
                entity_id=r.get('entityId', ''),
                score=round(r.get('score', 0), 4),
            ))
        
        return results
    
    def _normalize_type(self, t: str) -> str:
        return t.lower() if t else ""
    
    def _normalize_source(self, s: str) -> str:
        if not s:
            return ""
        s = s.lower().replace('_', '').replace('-', '')
        mappings = {
            'usofac': 'ofac',
            'ofacsdn': 'ofac',
            'uscsl': 'csl',
            'eucsl': 'eucsl',
            'ukcsl': 'ukcsl',
        }
        return mappings.get(s, s)


class ComparisonEngine:
    """Engine to compare Go and Java implementations"""
    
    def __init__(self, go_url: str, java_url: str, min_match: float = 0.70, limit: int = 10):
        self.go_client = WatchmanClient(go_url, "Go")
        self.java_client = WatchmanClient(java_url, "Java")
        self.min_match = min_match
        self.limit = limit
        self.results: List[ComparisonResult] = []
    
    def check_health(self) -> Dict[str, Tuple[bool, str]]:
        """Check health of both services"""
        return {
            "go": self.go_client.health_check(),
            "java": self.java_client.health_check()
        }
    
    def compare_name(self, test_id: int, name: str, category: str, expected: str) -> ComparisonResult:
        """Compare search results for a single name"""
        result = ComparisonResult(
            test_id=test_id,
            name=name,
            category=category,
            expected=expected
        )
        
        # Search Go
        go_status, go_results, go_time, go_error = self.go_client.search(
            name, self.limit, self.min_match
        )
        result.go_status = go_status
        result.go_results_count = len(go_results)
        result.go_response_ms = round(go_time, 1)
        result.go_error = go_error
        if go_results:
            result.go_top_match = go_results[0].name
            result.go_top_score = go_results[0].score
        
        # Search Java
        java_status, java_results, java_time, java_error = self.java_client.search(
            name, self.limit, self.min_match
        )
        result.java_status = java_status
        result.java_results_count = len(java_results)
        result.java_response_ms = round(java_time, 1)
        result.java_error = java_error
        if java_results:
            result.java_top_match = java_results[0].name
            result.java_top_score = java_results[0].score
        
        # Compare results
        result.score_diff = round(abs(result.go_top_score - result.java_top_score), 4)
        result.count_diff = abs(result.go_results_count - result.java_results_count)
        result.top_match_same = (
            result.go_top_match.upper() == result.java_top_match.upper()
            if result.go_top_match and result.java_top_match else
            result.go_results_count == result.java_results_count == 0
        )
        
        # Determine pass/fail
        result.passed = (
            go_status == java_status == 200 and
            result.score_diff <= 0.10 and  # Allow 10% score difference
            result.count_diff <= 3 and     # Allow 3 result count difference
            result.top_match_same
        )
        
        # Add notes
        notes = []
        if go_error:
            notes.append(f"Go: {go_error}")
        if java_error:
            notes.append(f"Java: {java_error}")
        if result.score_diff > 0.10:
            notes.append(f"Score diff: {result.score_diff:.2%}")
        if not result.top_match_same and result.go_top_match and result.java_top_match:
            notes.append("Different top match")
        result.notes = "; ".join(notes)
        
        self.results.append(result)
        return result
    
    def run_comparison(self, test_names: List[Dict], progress_callback=None) -> List[ComparisonResult]:
        """Run comparison for all test names"""
        total = len(test_names)
        
        for i, test in enumerate(test_names):
            result = self.compare_name(
                test_id=test['id'],
                name=test['name'],
                category=test.get('category', 'unknown'),
                expected=test.get('expected', 'unknown')
            )
            
            if progress_callback:
                progress_callback(i + 1, total, result)
        
        return self.results
    
    def get_summary(self) -> Dict:
        """Generate summary statistics"""
        if not self.results:
            return {}
        
        passed = sum(1 for r in self.results if r.passed)
        failed = len(self.results) - passed
        
        go_times = [r.go_response_ms for r in self.results if r.go_status == 200]
        java_times = [r.java_response_ms for r in self.results if r.java_status == 200]
        
        by_category = {}
        for r in self.results:
            cat = r.category
            if cat not in by_category:
                by_category[cat] = {"total": 0, "passed": 0}
            by_category[cat]["total"] += 1
            if r.passed:
                by_category[cat]["passed"] += 1
        
        return {
            "total_tests": len(self.results),
            "passed": passed,
            "failed": failed,
            "pass_rate": round(passed / len(self.results) * 100, 1),
            "go_avg_response_ms": round(sum(go_times) / len(go_times), 1) if go_times else 0,
            "java_avg_response_ms": round(sum(java_times) / len(java_times), 1) if java_times else 0,
            "go_errors": sum(1 for r in self.results if r.go_error),
            "java_errors": sum(1 for r in self.results if r.java_error),
            "by_category": by_category,
        }


class ReportGenerator:
    """Generate comparison reports in various formats"""
    
    def __init__(self, results: List[ComparisonResult], summary: Dict, 
                 go_url: str, java_url: str):
        self.results = results
        self.summary = summary
        self.go_url = go_url
        self.java_url = java_url
        self.timestamp = datetime.now().isoformat()
    
    def generate_html(self, output_path: str):
        """Generate HTML report"""
        html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Watchman Comparison Report</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; background: #f5f5f5; }}
        .container {{ max-width: 1400px; margin: 0 auto; }}
        h1 {{ color: #333; }}
        .summary {{ background: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        .summary-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; }}
        .stat {{ background: #f8f9fa; padding: 15px; border-radius: 6px; text-align: center; }}
        .stat-value {{ font-size: 24px; font-weight: bold; color: #333; }}
        .stat-label {{ color: #666; font-size: 14px; }}
        .pass {{ color: #28a745; }}
        .fail {{ color: #dc3545; }}
        table {{ width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }}
        th {{ background: #343a40; color: white; padding: 12px 8px; text-align: left; font-size: 13px; }}
        td {{ padding: 10px 8px; border-bottom: 1px solid #dee2e6; font-size: 13px; }}
        tr:hover {{ background: #f8f9fa; }}
        .status-pass {{ background: #d4edda; }}
        .status-fail {{ background: #f8d7da; }}
        .mono {{ font-family: monospace; }}
        .category {{ background: #e9ecef; padding: 2px 6px; border-radius: 3px; font-size: 11px; }}
        .score {{ font-weight: bold; }}
        .endpoints {{ background: #e7f3ff; padding: 10px 15px; border-radius: 6px; margin-bottom: 20px; }}
        .endpoints code {{ background: #d1e7ff; padding: 2px 6px; border-radius: 3px; }}
        .filters {{ margin-bottom: 15px; }}
        .filters button {{ margin-right: 5px; padding: 5px 10px; cursor: pointer; }}
        .hidden {{ display: none; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>🔍 Watchman Go vs Java Comparison Report</h1>
        
        <div class="endpoints">
            <strong>Endpoints:</strong> 
            Go: <code>{self.go_url}</code> | 
            Java: <code>{self.java_url}</code> |
            Generated: <code>{self.timestamp}</code>
        </div>
        
        <div class="summary">
            <h2>Summary</h2>
            <div class="summary-grid">
                <div class="stat">
                    <div class="stat-value">{self.summary['total_tests']}</div>
                    <div class="stat-label">Total Tests</div>
                </div>
                <div class="stat">
                    <div class="stat-value pass">{self.summary['passed']}</div>
                    <div class="stat-label">Passed</div>
                </div>
                <div class="stat">
                    <div class="stat-value fail">{self.summary['failed']}</div>
                    <div class="stat-label">Failed</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{self.summary['pass_rate']}%</div>
                    <div class="stat-label">Pass Rate</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{self.summary['go_avg_response_ms']}ms</div>
                    <div class="stat-label">Go Avg Response</div>
                </div>
                <div class="stat">
                    <div class="stat-value">{self.summary['java_avg_response_ms']}ms</div>
                    <div class="stat-label">Java Avg Response</div>
                </div>
            </div>
        </div>
        
        <div class="filters">
            <button onclick="filterTable('all')">All</button>
            <button onclick="filterTable('pass')">Passed</button>
            <button onclick="filterTable('fail')">Failed</button>
        </div>
        
        <table id="results-table">
            <thead>
                <tr>
                    <th>#</th>
                    <th>Name</th>
                    <th>Category</th>
                    <th>Status</th>
                    <th>Go Results</th>
                    <th>Java Results</th>
                    <th>Go Top Match</th>
                    <th>Java Top Match</th>
                    <th>Go Score</th>
                    <th>Java Score</th>
                    <th>Score Diff</th>
                    <th>Go Time</th>
                    <th>Java Time</th>
                    <th>Notes</th>
                </tr>
            </thead>
            <tbody>
"""
        
        for r in self.results:
            status_class = "status-pass" if r.passed else "status-fail"
            status_text = "✅ PASS" if r.passed else "❌ FAIL"
            score_diff_display = f"{r.score_diff:.2%}" if r.score_diff > 0 else "-"
            
            html += f"""                <tr class="{status_class}" data-status="{'pass' if r.passed else 'fail'}">
                    <td>{r.test_id}</td>
                    <td class="mono">{self._escape_html(r.name)}</td>
                    <td><span class="category">{r.category}</span></td>
                    <td>{status_text}</td>
                    <td>{r.go_results_count}</td>
                    <td>{r.java_results_count}</td>
                    <td class="mono">{self._escape_html(r.go_top_match[:30])}</td>
                    <td class="mono">{self._escape_html(r.java_top_match[:30])}</td>
                    <td class="score">{r.go_top_score:.2f}</td>
                    <td class="score">{r.java_top_score:.2f}</td>
                    <td>{score_diff_display}</td>
                    <td>{r.go_response_ms}ms</td>
                    <td>{r.java_response_ms}ms</td>
                    <td>{self._escape_html(r.notes)}</td>
                </tr>
"""
        
        html += """            </tbody>
        </table>
    </div>
    
    <script>
        function filterTable(status) {
            const rows = document.querySelectorAll('#results-table tbody tr');
            rows.forEach(row => {
                if (status === 'all') {
                    row.classList.remove('hidden');
                } else {
                    row.classList.toggle('hidden', row.dataset.status !== status);
                }
            });
        }
    </script>
</body>
</html>
"""
        
        with open(output_path, 'w') as f:
            f.write(html)
        
        return output_path
    
    def generate_csv(self, output_path: str):
        """Generate CSV report"""
        with open(output_path, 'w', newline='') as f:
            writer = csv.writer(f)
            writer.writerow([
                'ID', 'Name', 'Category', 'Expected', 'Passed',
                'Go Status', 'Go Results', 'Go Top Match', 'Go Score', 'Go Time (ms)',
                'Java Status', 'Java Results', 'Java Top Match', 'Java Score', 'Java Time (ms)',
                'Score Diff', 'Count Diff', 'Top Match Same', 'Notes'
            ])
            
            for r in self.results:
                writer.writerow([
                    r.test_id, r.name, r.category, r.expected, r.passed,
                    r.go_status, r.go_results_count, r.go_top_match, r.go_top_score, r.go_response_ms,
                    r.java_status, r.java_results_count, r.java_top_match, r.java_top_score, r.java_response_ms,
                    r.score_diff, r.count_diff, r.top_match_same, r.notes
                ])
        
        return output_path
    
    def generate_json(self, output_path: str):
        """Generate JSON report"""
        report = {
            "metadata": {
                "timestamp": self.timestamp,
                "go_url": self.go_url,
                "java_url": self.java_url,
            },
            "summary": self.summary,
            "results": [asdict(r) for r in self.results]
        }
        
        with open(output_path, 'w') as f:
            json.dump(report, f, indent=2)
        
        return output_path
    
    def _escape_html(self, text: str) -> str:
        if not text:
            return ""
        return (text
                .replace('&', '&amp;')
                .replace('<', '&lt;')
                .replace('>', '&gt;')
                .replace('"', '&quot;'))


def print_progress(current: int, total: int, result: ComparisonResult):
    """Print progress to console"""
    status = "✅" if result.passed else "❌"
    print(f"[{current:3d}/{total}] {status} {result.name[:40]:<40} "
          f"Go:{result.go_top_score:.2f} Java:{result.java_top_score:.2f}")


def main():
    parser = argparse.ArgumentParser(
        description="Compare Watchman Go and Java implementations",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument('--go-url', default='https://watchman-go.fly.dev',
                        help='Go Watchman URL (default: https://watchman-go.fly.dev)')
    parser.add_argument('--java-url', default='https://watchman-java.fly.dev',
                        help='Java Watchman URL (default: https://watchman-java.fly.dev)')
    parser.add_argument('--test-file', default=None,
                        help='Path to test names JSON file')
    parser.add_argument('--output', choices=['html', 'csv', 'json', 'all'], default='all',
                        help='Output format (default: all)')
    parser.add_argument('--output-dir', default='./comparison-reports',
                        help='Output directory for reports')
    parser.add_argument('--min-match', type=float, default=0.70,
                        help='Minimum match score (default: 0.70)')
    parser.add_argument('--limit', type=int, default=10,
                        help='Result limit per search (default: 10)')
    parser.add_argument('--quiet', action='store_true',
                        help='Suppress progress output')
    
    args = parser.parse_args()
    
    print("=" * 70)
    print("Watchman Go vs Java Comparison Tool")
    print("=" * 70)
    print(f"Go URL:    {args.go_url}")
    print(f"Java URL:  {args.java_url}")
    print(f"Min Match: {args.min_match}")
    print(f"Limit:     {args.limit}")
    print()
    
    # Create comparison engine
    engine = ComparisonEngine(args.go_url, args.java_url, args.min_match, args.limit)
    
    # Health check
    print("Checking service health...")
    health = engine.check_health()
    
    go_healthy, go_info = health['go']
    java_healthy, java_info = health['java']
    
    print(f"  Go:   {'✅ ' + go_info if go_healthy else '❌ ' + go_info}")
    print(f"  Java: {'✅ ' + java_info if java_healthy else '❌ ' + java_info}")
    print()
    
    if not go_healthy or not java_healthy:
        print("⚠️  One or both services are not healthy!")
        if not go_healthy and not java_healthy:
            print("Cannot proceed without at least one healthy service.")
            sys.exit(1)
        print("Proceeding with available services...\n")
    
    # Load test names
    test_file = args.test_file
    if not test_file:
        # Look for default test file
        script_dir = Path(__file__).parent
        default_paths = [
            script_dir.parent / 'test-data' / 'comparison-test-names.json',
            script_dir / 'comparison-test-names.json',
            Path('test-data/comparison-test-names.json'),
        ]
        for p in default_paths:
            if p.exists():
                test_file = str(p)
                break
    
    if not test_file or not Path(test_file).exists():
        print(f"Error: Test file not found. Specify with --test-file")
        sys.exit(1)
    
    print(f"Loading test names from: {test_file}")
    with open(test_file) as f:
        test_data = json.load(f)
    
    test_names = test_data['test_names']
    print(f"Loaded {len(test_names)} test names\n")
    
    # Run comparison
    print("Running comparison tests...")
    print("-" * 70)
    
    progress_fn = None if args.quiet else print_progress
    results = engine.run_comparison(test_names, progress_fn)
    
    # Get summary
    summary = engine.get_summary()
    
    print("-" * 70)
    print("\nSummary:")
    print(f"  Total:     {summary['total_tests']}")
    print(f"  Passed:    {summary['passed']} ✅")
    print(f"  Failed:    {summary['failed']} ❌")
    print(f"  Pass Rate: {summary['pass_rate']}%")
    print(f"\n  Go Avg Response:   {summary['go_avg_response_ms']}ms")
    print(f"  Java Avg Response: {summary['java_avg_response_ms']}ms")
    
    if summary.get('by_category'):
        print("\n  By Category:")
        for cat, stats in summary['by_category'].items():
            rate = stats['passed'] / stats['total'] * 100 if stats['total'] > 0 else 0
            print(f"    {cat}: {stats['passed']}/{stats['total']} ({rate:.0f}%)")
    
    # Generate reports
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    generator = ReportGenerator(results, summary, args.go_url, args.java_url)
    
    print(f"\nGenerating reports in: {output_dir}")
    
    if args.output in ['html', 'all']:
        path = generator.generate_html(output_dir / f'comparison_{timestamp}.html')
        print(f"  HTML: {path}")
    
    if args.output in ['csv', 'all']:
        path = generator.generate_csv(output_dir / f'comparison_{timestamp}.csv')
        print(f"  CSV:  {path}")
    
    if args.output in ['json', 'all']:
        path = generator.generate_json(output_dir / f'comparison_{timestamp}.json')
        print(f"  JSON: {path}")
    
    print("\n✅ Comparison complete!")
    
    # Exit with error if any tests failed
    sys.exit(0 if summary['failed'] == 0 else 1)


if __name__ == "__main__":
    main()
