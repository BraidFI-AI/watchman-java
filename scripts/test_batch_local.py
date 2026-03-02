#!/usr/bin/env python3
"""
Local batch API testing - measure performance of batch processing locally
to isolate AWS/network overhead from actual code performance issues.
"""

import requests
import json
import time
from pathlib import Path

def load_test_names(count=1000):
    """Load test names from static dataset"""
    data_file = Path(__file__).parent.parent / "test-data" / "clean_names_9000.json"
    if data_file.exists():
        with open(data_file) as f:
            all_names = json.load(f)
        return all_names[:count]
    else:
        # Fallback to small set
        return ["John Smith", "Jane Doe", "Bob Johnson"] * (count // 3 + 1)

def test_batch_local(batch_size=100, num_batches=10):
    """Test batch API locally"""
    url = "http://localhost:8084/v1/search/batch"
    
    test_names = load_test_names(batch_size * num_batches)
    
    print(f"Testing batch API locally:")
    print(f"  - {num_batches} batches")
    print(f"  - {batch_size} names per batch")
    print(f"  - {batch_size * num_batches} total names")
    print()
    
    results = []
    total_start = time.time()
    
    for batch_num in range(num_batches):
        batch_names = test_names[batch_num * batch_size:(batch_num + 1) * batch_size]
        payload = {
            'items': [{'name': name} for name in batch_names],
            'minMatch': 0.88,
            'limit': 10
        }
        
        print(f"[{batch_num + 1}/{num_batches}] Sending batch with {len(batch_names)} names...")
        
        batch_start = time.time()
        try:
            resp = requests.post(url, json=payload, timeout=300)
            batch_elapsed = time.time() - batch_start
            
            if resp.status_code == 200:
                data = resp.json()
                processing_time = data.get('processingTime', 'N/A')
                print(f"  ✅ Success - {batch_elapsed:.1f}s total, {processing_time} server processing")
                results.append({
                    'batch': batch_num + 1,
                    'success': True,
                    'time': batch_elapsed,
                    'names_per_sec': len(batch_names) / batch_elapsed
                })
            else:
                print(f"  ❌ HTTP {resp.status_code} - {batch_elapsed:.1f}s")
                results.append({
                    'batch': batch_num + 1,
                    'success': False,
                    'time': batch_elapsed,
                    'error': f"HTTP {resp.status_code}"
                })
        except Exception as e:
            batch_elapsed = time.time() - batch_start
            print(f"  ❌ Error - {batch_elapsed:.1f}s - {str(e)[:80]}")
            results.append({
                'batch': batch_num + 1,
                'success': False,
                'time': batch_elapsed,
                'error': str(e)
            })
    
    total_elapsed = time.time() - total_start
    successful = sum(1 for r in results if r.get('success'))
    total_names = batch_size * sum(1 for r in results if r.get('success'))
    
    print()
    print("=" * 60)
    print(f"RESULTS:")
    print(f"  Successful batches: {successful}/{num_batches}")
    print(f"  Total names processed: {total_names}")
    print(f"  Total time: {total_elapsed:.1f}s")
    print(f"  Overall throughput: {total_names / total_elapsed:.2f} names/sec")
    print()
    
    if successful > 0:
        avg_time = sum(r['time'] for r in results if r.get('success')) / successful
        avg_throughput = sum(r.get('names_per_sec', 0) for r in results if r.get('success')) / successful
        print(f"  Avg batch time: {avg_time:.1f}s")
        print(f"  Avg batch throughput: {avg_throughput:.2f} names/sec")
    
    print("=" * 60)

if __name__ == '__main__':
    import argparse
    
    parser = argparse.ArgumentParser(description='Test batch API locally')
    parser.add_argument('--batch-size', type=int, default=100, help='Names per batch (default: 100)')
    parser.add_argument('--batches', type=int, default=10, help='Number of batches (default: 10)')
    
    args = parser.parse_args()
    
    # Quick health check
    try:
        health = requests.get('http://localhost:8084/health', timeout=5).json()
        print(f"Server status: {health.get('status')}, Entities: {health.get('entityCount')}")
        print()
    except Exception as e:
        print(f"⚠️  Health check failed: {e}")
        print("Make sure server is running on port 8084")
        exit(1)
    
    test_batch_local(args.batch_size, args.batches)
