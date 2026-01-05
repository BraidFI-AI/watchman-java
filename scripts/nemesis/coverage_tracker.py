"""
Coverage Tracker - Tracks which OFAC entities have been tested
Maintains persistent state to ensure comprehensive coverage over time
"""

import json
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional
import random


class CoverageTracker:
    """Tracks test coverage of OFAC entities with persistent state."""
    
    def __init__(self, state_file: Path, total_entities: int):
        """
        Initialize coverage tracker.
        
        Args:
            state_file: Path to JSON state file
            total_entities: Total number of OFAC entities
        """
        self.state_file = Path(state_file)
        self.total_entities = total_entities
        self.state = self._load_state()
        
    def _load_state(self) -> Dict:
        """Load state from file or create new state."""
        if self.state_file.exists():
            try:
                with open(self.state_file) as f:
                    return json.load(f)
            except Exception:
                pass
        
        # Initialize new state
        return {
            "total_ofac_entities": self.total_entities,
            "entities_tested": 0,
            "tested_entity_ids": [],
            "entity_stats": {},
            "last_updated": None
        }
    
    def save(self):
        """Save state to file."""
        self.state["last_updated"] = datetime.now().isoformat()
        self.state["entities_tested"] = len(self.state["tested_entity_ids"])
        
        # Ensure directory exists
        self.state_file.parent.mkdir(parents=True, exist_ok=True)
        
        with open(self.state_file, 'w') as f:
            json.dump(self.state, f, indent=2)
    
    def record_test(self, entity_id: str, entity_name: str):
        """
        Record that an entity was tested.
        
        Args:
            entity_id: Entity ID (e.g., "SDN-12345")
            entity_name: Entity name
        """
        # Add to tested set if not already present
        if entity_id not in self.state["tested_entity_ids"]:
            self.state["tested_entity_ids"].append(entity_id)
        
        # Update entity stats
        if entity_id not in self.state["entity_stats"]:
            self.state["entity_stats"][entity_id] = {
                "entity_name": entity_name,
                "test_count": 0,
                "first_tested": datetime.now().isoformat(),
                "last_tested": None
            }
        
        self.state["entity_stats"][entity_id]["test_count"] += 1
        self.state["entity_stats"][entity_id]["last_tested"] = datetime.now().isoformat()
    
    def was_tested(self, entity_id: str) -> bool:
        """Check if an entity has been tested."""
        return entity_id in self.state["tested_entity_ids"]
    
    def coverage_percentage(self) -> float:
        """Calculate coverage percentage."""
        if self.total_entities == 0:
            return 0.0
        return (len(self.state["tested_entity_ids"]) / self.total_entities) * 100
    
    def get_entity_stats(self, entity_id: str) -> Optional[Dict]:
        """Get statistics for a specific entity."""
        return self.state["entity_stats"].get(entity_id)
    
    def get_prioritized_entities(self, all_entities: List[Dict], count: int) -> List[Dict]:
        """
        Get prioritized list of entities to test.
        Prioritizes untested entities, then least-recently tested.
        
        Args:
            all_entities: List of all available entities
            count: Number of entities to return
            
        Returns:
            List of prioritized entities
        """
        # Separate into tested and untested
        untested = [e for e in all_entities if e["id"] not in self.state["tested_entity_ids"]]
        tested = [e for e in all_entities if e["id"] in self.state["tested_entity_ids"]]
        
        # Prioritize untested
        prioritized = []
        
        if untested:
            # Shuffle untested for variety
            random.shuffle(untested)
            prioritized.extend(untested[:count])
        
        # Fill remaining with tested entities (least recently tested first)
        if len(prioritized) < count and tested:
            # Sort tested by last_tested date (oldest first)
            tested_with_stats = []
            for entity in tested:
                stats = self.state["entity_stats"].get(entity["id"], {})
                last_tested = stats.get("last_tested", "1970-01-01")
                tested_with_stats.append((entity, last_tested))
            
            tested_with_stats.sort(key=lambda x: x[1])
            remaining = count - len(prioritized)
            prioritized.extend([e[0] for e in tested_with_stats[:remaining]])
        
        return prioritized[:count]
    
    def get_summary(self) -> Dict:
        """Get summary statistics."""
        return {
            "total_entities": self.total_entities,
            "entities_tested": len(self.state["tested_entity_ids"]),
            "coverage_percentage": self.coverage_percentage(),
            "last_updated": self.state.get("last_updated")
        }
