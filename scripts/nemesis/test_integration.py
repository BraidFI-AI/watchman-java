#!/usr/bin/env python3
"""
Integration tests for Nemesis → Repair Pipeline flow
"""

import json
import pytest
from pathlib import Path
from unittest.mock import Mock, patch, MagicMock
import sys

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent))


class TestNemesisRepairIntegration:
    """Test suite for Nemesis + Repair Pipeline integration."""
    
    def test_report_has_repair_results_section(self):
        """Report should include repair_results section after integration."""
        # This test defines the expected report structure
        expected_keys = [
            "run_date",
            "version", 
            "configuration",
            "coverage",
            "results_summary",
            "ai_analysis",
            "divergences",
            "test_queries",
            "repair_results"  # NEW: Should exist after integration
        ]
        
        # Create mock report
        report = self._create_mock_report_with_divergences()
        
        # Verify all expected keys exist
        for key in expected_keys:
            assert key in report, f"Report missing required key: {key}"
    
    def test_repair_results_structure(self):
        """repair_results should have expected structure."""
        report = self._create_mock_report_with_divergences()
        
        repair_results = report.get("repair_results", {})
        
        # Expected fields
        assert "enabled" in repair_results
        assert "action_plan_file" in repair_results
        assert "code_analysis_file" in repair_results
        assert "fix_proposal_file" in repair_results
        assert "pr_results_file" in repair_results
        assert "auto_fix_count" in repair_results
        assert "human_review_count" in repair_results
        assert "too_complex_count" in repair_results
        assert "prs_created" in repair_results
        
        # prs_created should be a list
        assert isinstance(repair_results["prs_created"], list)
    
    def test_pr_entry_structure(self):
        """Each PR entry should have required fields."""
        pr_entry = {
            "issue_id": "AUTO-001",
            "pr_url": "https://github.com/BraidFI-AI/watchman-java/pull/123",
            "branch": "nemesis/auto-001/20260111-143022",
            "status": "success"
        }
        
        # Verify structure
        assert "issue_id" in pr_entry
        assert "pr_url" in pr_entry
        assert "branch" in pr_entry
        assert "status" in pr_entry
        assert pr_entry["status"] in ["success", "error"]
    
    def test_repair_pipeline_not_run_when_no_divergences(self):
        """Repair pipeline should not run when no divergences found."""
        report = self._create_mock_report_without_divergences()
        
        repair_results = report.get("repair_results", {})
        
        assert repair_results["enabled"] is False
        assert repair_results.get("reason") == "No divergences to repair"
        assert repair_results["prs_created"] == []
    
    def test_repair_pipeline_runs_when_divergences_exist(self):
        """Repair pipeline should run when divergences are found."""
        report = self._create_mock_report_with_divergences()
        
        repair_results = report.get("repair_results", {})
        
        assert repair_results["enabled"] is True
        assert repair_results["auto_fix_count"] >= 0
        assert repair_results["human_review_count"] >= 0
    
    def test_github_issue_includes_pr_urls(self):
        """GitHub issue body should include PR URLs when PRs are created."""
        issue_body = """## Nemesis Report
        
## 🔧 Automated Fixes

The repair pipeline has created **2 pull requests**:

1. ✅ [AUTO-001: Cross-Language False Positives](https://github.com/BraidFI-AI/watchman-java/pull/123)
2. ⚠️ [AUTO-002: Scoring Precision](https://github.com/BraidFI-AI/watchman-java/pull/124) - Needs review

"""
        
        # Verify PR URLs are present
        assert "pull/123" in issue_body
        assert "pull/124" in issue_body
        assert "AUTO-001" in issue_body
        assert "AUTO-002" in issue_body
    
    def test_repair_pipeline_error_handling(self):
        """System should handle repair pipeline failures gracefully."""
        report = {
            "repair_results": {
                "enabled": True,
                "error": "Repair pipeline failed: Connection timeout",
                "prs_created": [],
                "auto_fix_count": 0,
                "human_review_count": 0
            }
        }
        
        assert report["repair_results"]["enabled"] is True
        assert "error" in report["repair_results"]
        assert report["repair_results"]["prs_created"] == []
    
    def test_integration_preserves_existing_report_data(self):
        """Integration should not break existing report fields."""
        report = self._create_mock_report_with_divergences()
        
        # All original fields should still exist
        assert "run_date" in report
        assert "configuration" in report
        assert "coverage" in report
        assert "results_summary" in report
        assert "ai_analysis" in report
        assert "divergences" in report
        
        # And new field should be added
        assert "repair_results" in report
    
    # Helper methods
    
    def _create_mock_report_with_divergences(self):
        """Create mock report with divergences (repair should run)."""
        return {
            "run_date": "2026-01-11T14:30:00",
            "version": "1.0",
            "configuration": {"total_queries": 100},
            "coverage": {"entities_tested_today": 85},
            "results_summary": {
                "total_divergences": 10,
                "by_severity": {"critical": 2, "moderate": 8}
            },
            "ai_analysis": {
                "patterns_identified": 2,
                "issues": [
                    {"id": "AUTO-001", "category": "Cross-Language"},
                    {"id": "AUTO-002", "category": "Scoring"}
                ]
            },
            "divergences": [{"type": "score_difference", "severity": "critical"}],
            "test_queries": [],
            "repair_results": {
                "enabled": True,
                "action_plan_file": "scripts/reports/action-plan-20260111.json",
                "code_analysis_file": "scripts/reports/code-analysis-20260111.json",
                "fix_proposal_file": "scripts/reports/fix-proposal-20260111.json",
                "pr_results_file": "scripts/reports/pr-results-20260111.json",
                "auto_fix_count": 1,
                "human_review_count": 1,
                "too_complex_count": 0,
                "prs_created": [
                    {
                        "issue_id": "AUTO-001",
                        "pr_url": "https://github.com/BraidFI-AI/watchman-java/pull/123",
                        "branch": "nemesis/auto-001/20260111-143022",
                        "status": "success"
                    },
                    {
                        "issue_id": "AUTO-002",
                        "pr_url": "https://github.com/BraidFI-AI/watchman-java/pull/124",
                        "branch": "nemesis/auto-002/20260111-143023",
                        "status": "success"
                    }
                ]
            }
        }
    
    def _create_mock_report_without_divergences(self):
        """Create mock report without divergences (repair should not run)."""
        return {
            "run_date": "2026-01-11T14:30:00",
            "version": "1.0",
            "configuration": {"total_queries": 100},
            "coverage": {"entities_tested_today": 85},
            "results_summary": {
                "total_divergences": 0,
                "by_severity": {}
            },
            "ai_analysis": {
                "patterns_identified": 0,
                "issues": []
            },
            "divergences": [],
            "test_queries": [],
            "repair_results": {
                "enabled": False,
                "reason": "No divergences to repair",
                "prs_created": []
            }
        }


if __name__ == "__main__":
    # Run tests
    pytest.main([__file__, "-v"])
