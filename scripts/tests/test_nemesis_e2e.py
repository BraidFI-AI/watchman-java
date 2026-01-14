#!/usr/bin/env python3
"""
End-to-end smoke test for Nemesis system.
Tests the full flow: trigger -> execute -> report generation
"""

import pytest
import requests
import time
import json
from pathlib import Path


@pytest.mark.skipif(
    not Path("/tmp/watchman-java.log").exists(),
    reason="Java service not running locally"
)
class TestNemesisEndToEnd:
    """Smoke tests for full Nemesis pipeline."""
    
    BASE_URL = "http://localhost:8084"
    
    def test_health_check(self):
        """Verify Java service is healthy before running tests."""
        response = requests.get(f"{self.BASE_URL}/health", timeout=5)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["entityCount"] > 0
    
    def test_nemesis_trigger_sync(self):
        """Test synchronous Nemesis execution via REST API."""
        payload = {
            "queries": 2,
            "includeOfacApi": False,
            "async": False
        }
        
        response = requests.post(
            f"{self.BASE_URL}/v2/nemesis/trigger",
            json=payload,
            timeout=60
        )
        
        assert response.status_code == 200
        data = response.json()
        
        # Verify response structure
        assert "jobId" in data
        assert "status" in data
        assert data["status"] in ["completed", "failed"]
        
        if data["status"] == "failed":
            # Check failure logs for debugging
            status_response = requests.get(
                f"{self.BASE_URL}{data['statusUrl']}",
                timeout=5
            )
            logs = status_response.json().get("logs", [])
            pytest.fail(f"Nemesis run failed. Last logs: {logs[-10:]}")
    
    def test_nemesis_trigger_async(self):
        """Test asynchronous Nemesis execution via REST API."""
        payload = {
            "queries": 2,
            "includeOfacApi": False,
            "async": True
        }
        
        response = requests.post(
            f"{self.BASE_URL}/v2/nemesis/trigger",
            json=payload,
            timeout=10
        )
        
        assert response.status_code == 202  # Accepted
        data = response.json()
        
        assert "jobId" in data
        assert "statusUrl" in data
        assert data["status"] == "running"
        
        # Poll status until complete (max 60 seconds)
        job_id = data["jobId"]
        for _ in range(12):
            time.sleep(5)
            status_response = requests.get(
                f"{self.BASE_URL}/v2/nemesis/status/{job_id}",
                timeout=5
            )
            status_data = status_response.json()
            
            if status_data["status"] in ["completed", "failed"]:
                assert status_data["status"] == "completed"
                return
        
        pytest.fail("Nemesis job did not complete within 60 seconds")
    
    def test_nemesis_status_endpoint(self):
        """Test status endpoint returns proper structure."""
        # First trigger a run
        payload = {"queries": 2, "includeOfacApi": False, "async": False}
        trigger_response = requests.post(
            f"{self.BASE_URL}/v2/nemesis/trigger",
            json=payload,
            timeout=60
        )
        
        job_id = trigger_response.json()["jobId"]
        
        # Check status
        response = requests.get(
            f"{self.BASE_URL}/v2/nemesis/status/{job_id}",
            timeout=5
        )
        
        assert response.status_code == 200
        data = response.json()
        
        # Verify status structure
        assert "jobId" in data
        assert "status" in data
        assert "queries" in data
        assert "includeOfacApi" in data
        assert "logs" in data
        assert isinstance(data["logs"], list)
    
    def test_environment_variables_set(self):
        """Verify Java service has correct environment variables."""
        # Trigger a small run
        payload = {"queries": 1, "includeOfacApi": False, "async": False}
        response = requests.post(
            f"{self.BASE_URL}/v2/nemesis/trigger",
            json=payload,
            timeout=60
        )
        
        job_id = response.json()["jobId"]
        
        # Check logs for configuration
        status_response = requests.get(
            f"{self.BASE_URL}/v2/nemesis/status/{job_id}",
            timeout=5
        )
        logs = status_response.json()["logs"]
        
        # Find Java API URL in logs
        java_api_line = [l for l in logs if "Java API:" in l]
        assert len(java_api_line) > 0
        
        # Should use localhost for local dev
        assert "localhost" in java_api_line[0] or "127.0.0.1" in java_api_line[0]


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
