"""
Test Generator - Creates dynamic test queries for Nemesis
Generates test cases from OFAC entities with variations
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict
import random
import re


@dataclass
class TestCase:
    """Represents a single test query with metadata."""
    query: str
    strategy: str
    source_entity_id: Optional[str] = None
    variation_type: Optional[str] = None
    expected_matches: List[str] = field(default_factory=list)
    should_not_match: List[str] = field(default_factory=list)
    min_score: float = 0.85
    language_context: Optional[Dict] = None


class RandomSamplingGenerator:
    """Generates test cases by randomly sampling OFAC entities."""
    
    def __init__(self, entities: List[Dict]):
        """
        Initialize with OFAC entities.
        
        Args:
            entities: List of entity dicts with 'id', 'name', 'altNames' keys
        """
        self.entities = entities
        
    def generate(self, count: int) -> List[TestCase]:
        """
        Generate random test cases by sampling entities.
        
        Args:
            count: Number of test cases to generate
            
        Returns:
            List of TestCase objects
        """
        test_cases = []
        for _ in range(count):
            entity = random.choice(self.entities)
            variation = self._pick_variation(entity)
            test_cases.append(variation)
        return test_cases
        
    def _pick_variation(self, entity: Dict) -> TestCase:
        """Pick a random variation of an entity's name."""
        name = entity["name"]
        alt_names = entity.get("altNames", [])
        
        # Choose variation type with weights
        variation_type = random.choice([
            "full_name", "full_name", "full_name",  # Weight towards full name
            "first_name", "last_name", "reversed", "alt_name"
        ])
        
        # Generate query based on variation type
        if variation_type == "alt_name" and alt_names:
            query = random.choice(alt_names)
        elif variation_type == "first_name":
            query = self._extract_first_name(name)
        elif variation_type == "last_name":
            query = self._extract_last_name(name)
        elif variation_type == "reversed":
            query = self._reverse_name(name)
        else:
            query = name
            
        return TestCase(
            query=query,
            strategy="random_sampling",
            source_entity_id=entity["id"],
            variation_type=variation_type,
            expected_matches=[name]
        )
    
    def _extract_first_name(self, name: str) -> str:
        """Extract first name from 'LAST, First' or 'First Last' format."""
        if ", " in name:
            # "LAST, First" format
            return name.split(", ")[1].split()[0]  # Get first word of first name
        # "First Last" format
        return name.split()[0]
    
    def _extract_last_name(self, name: str) -> str:
        """Extract last name from 'LAST, First' or 'First Last' format."""
        if ", " in name:
            # "LAST, First" format
            return name.split(", ")[0]
        # "First Last" format
        return name.split()[-1]
    
    def _reverse_name(self, name: str) -> str:
        """Reverse name format from 'LAST, First' to 'First LAST'."""
        if ", " in name:
            parts = name.split(", ")
            return f"{parts[1]} {parts[0]}"
        # Already in 'First Last' format
        return name


class VariationGenerator:
    """Generates all possible variations of a given name."""
    
    @staticmethod
    def generate_variations(name: str, alt_names: List[str] = None) -> List[TestCase]:
        """
        Generate all variations of a name.
        
        Args:
            name: Primary entity name
            alt_names: List of alternative names
            
        Returns:
            List of TestCase objects with all variations
        """
        variations = []
        alt_names = alt_names or []
        
        # 1. Full name
        variations.append(TestCase(
            query=name,
            strategy="variation",
            variation_type="full_name"
        ))
        
        # 2. First name (if comma format)
        if ", " in name:
            first = name.split(", ")[1]
            variations.append(TestCase(
                query=first,
                strategy="variation",
                variation_type="first_name"
            ))
            
        # 3. Last name (if comma format)
        if ", " in name:
            last = name.split(", ")[0]
            variations.append(TestCase(
                query=last,
                strategy="variation",
                variation_type="last_name"
            ))
            
        # 4. Reversed format
        if ", " in name:
            parts = name.split(", ")
            reversed_name = f"{parts[1]} {parts[0]}"
            variations.append(TestCase(
                query=reversed_name,
                strategy="variation",
                variation_type="reversed"
            ))
            
        # 5. Alt names
        for alt in alt_names:
            variations.append(TestCase(
                query=alt,
                strategy="variation",
                variation_type="alt_name"
            ))
            
            # 6. Alt name partials (extract significant words)
            if " " in alt:
                parts = alt.split()
                for part in parts:
                    # Skip short words like "El", "al", "de"
                    if len(part) > 2 and part.lower() not in ['the', 'von', 'van', 'del']:
                        variations.append(TestCase(
                            query=part,
                            strategy="variation",
                            variation_type="alt_name_partial"
                        ))
        
        return variations
