"""
Tests for coverage_tracker.py
Run with: pytest nemesis/tests/test_coverage_tracker.py -v
"""

import pytest
import json
from pathlib import Path
from nemesis.coverage_tracker import CoverageTracker


class TestCoverageTracker:
    """Test coverage tracking with persistent state."""
    
    @pytest.fixture
    def temp_state_file(self, tmp_path):
        return tmp_path / "coverage.json"
    
    def test_initializes_empty_state(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=100)
        assert tracker.coverage_percentage() == 0.0
        
    def test_records_tested_entity(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=100)
        tracker.record_test("SDN-1", "Test Entity")
        
        assert tracker.was_tested("SDN-1")
        assert tracker.coverage_percentage() > 0
        
    def test_coverage_percentage_calculation(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=100)
        
        for i in range(25):
            tracker.record_test(f"SDN-{i}", f"Entity {i}")
        
        assert tracker.coverage_percentage() == 25.0
        
    def test_persists_state_to_file(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=100)
        tracker.record_test("SDN-1", "Entity 1")
        tracker.record_test("SDN-2", "Entity 2")
        tracker.save()
        
        # File should exist and contain data
        assert temp_state_file.exists()
        with open(temp_state_file) as f:
            data = json.load(f)
        assert len(data["tested_entity_ids"]) == 2
        
    def test_loads_existing_state(self, temp_state_file):
        # Create first tracker and save state
        tracker1 = CoverageTracker(state_file=temp_state_file, total_entities=100)
        tracker1.record_test("SDN-1", "Entity 1")
        tracker1.save()
        
        # Create second tracker - should load existing state
        tracker2 = CoverageTracker(state_file=temp_state_file, total_entities=100)
        assert tracker2.was_tested("SDN-1")
        assert tracker2.coverage_percentage() == 1.0
        
    def test_prioritizes_untested_entities(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=100)
        tracker.record_test("SDN-1", "Entity 1")
        tracker.record_test("SDN-2", "Entity 2")
        
        all_entities = [
            {"id": "SDN-1", "name": "Tested 1"},
            {"id": "SDN-2", "name": "Tested 2"},
            {"id": "SDN-3", "name": "Untested 3"},
            {"id": "SDN-4", "name": "Untested 4"}
        ]
        
        prioritized = tracker.get_prioritized_entities(all_entities, count=2)
        
        # Should return untested entities first
        assert len(prioritized) == 2
        assert prioritized[0]["id"] in ["SDN-3", "SDN-4"]
        assert prioritized[1]["id"] in ["SDN-3", "SDN-4"]
        
    def test_handles_all_entities_tested(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=3)
        tracker.record_test("SDN-1", "E1")
        tracker.record_test("SDN-2", "E2")
        tracker.record_test("SDN-3", "E3")
        
        all_entities = [
            {"id": "SDN-1", "name": "E1"},
            {"id": "SDN-2", "name": "E2"},
            {"id": "SDN-3", "name": "E3"}
        ]
        
        # Should still return entities (can test same entity multiple times)
        prioritized = tracker.get_prioritized_entities(all_entities, count=2)
        assert len(prioritized) == 2
        
    def test_increments_test_count(self, temp_state_file):
        tracker = CoverageTracker(state_file=temp_state_file, total_entities=100)
        tracker.record_test("SDN-1", "Test")
        tracker.record_test("SDN-1", "Test")  # Test same entity twice
        
        stats = tracker.get_entity_stats("SDN-1")
        assert stats["test_count"] == 2
