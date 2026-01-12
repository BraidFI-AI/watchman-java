"""
Tests for GitHub integration - issue creation
"""

import pytest
from unittest.mock import Mock, patch
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from github_integration import create_nemesis_issue, format_nemesis_issue


class TestGitHubIntegration:
    
    def test_create_nemesis_issue_with_zero_divergences(self):
        data = {
            'run_date': '2026-01-11T10:00:00Z',
            'version': '1.0',
            'configuration': {'total_queries': 10},
            'metadata': {'timestamp': '2026-01-11T10:00:00Z'},
            'results_summary': {'total_divergences': 0, 'by_severity': {'critical': 0, 'moderate': 0}},
            'coverage': {'total_queries_tested': 10, 'cumulative_coverage_pct': 50.0},
            'divergences': []
        }
        
        with patch('github_integration.GITHUB_TOKEN', 'fake-token'):
            with patch('requests.post') as mock_post:
                mock_response = Mock()
                mock_response.status_code = 201
                mock_response.json.return_value = {"html_url": "https://github.com/moov-io/watchman-java/issues/123"}
                mock_post.return_value = mock_response
                
                result = create_nemesis_issue(data, '/data/reports/nemesis-20260111.json')
                
                assert result == "https://github.com/moov-io/watchman-java/issues/123"
                assert mock_post.called
                
                # Verify correct labels for clean run
                call_kwargs = mock_post.call_args[1]
                labels = call_kwargs['json']['labels']
                assert "nemesis" in labels
                assert "status:clean" in labels
    
    def test_format_nemesis_issue_with_prs(self):
        data = {
            'run_date': '2026-01-11T10:00:00Z',
            'configuration': {'total_queries': 100},
            'results_summary': {'total_divergences': 3, 'by_severity': {'critical': 1, 'moderate': 2}},
            'coverage': {'entities_tested_today': 100, 'cumulative_tested': 400, 'cumulative_coverage_pct': 70.0, 'total_entities': 571},
            'divergences': [],
            'repair_results': {
                'prs_created': [
                    {'issue_id': 'NEM-001', 'pr_url': 'https://github.com/moov-io/watchman-java/pull/10', 'status': 'success'}
                ]
            }
        }
        
        title, body = format_nemesis_issue(data, '/data/reports/nemesis-20260111.json')
        assert 'pull/10' in body

if __name__ == "__main__":
    pytest.main([__file__, "-v"])
