"""
Tests for test_generator.py - TDD approach
Run with: pytest nemesis/tests/test_test_generator.py -v
"""

import pytest
from nemesis.test_generator import (
    TestCase,
    RandomSamplingGenerator,
    VariationGenerator
)


class TestTestCaseDataClass:
    """Test the TestCase data structure."""
    
    def test_create_basic_test_case(self):
        tc = TestCase(
            query="El Chapo",
            strategy="random_sampling",
            source_entity_id="SDN-12345",
            variation_type="full_name"
        )
        assert tc.query == "El Chapo"
        assert tc.strategy == "random_sampling"
        assert tc.source_entity_id == "SDN-12345"
        assert tc.variation_type == "full_name"
        
    def test_test_case_with_expectations(self):
        tc = TestCase(
            query="Guzman",
            strategy="adversarial",
            expected_matches=["GUZMAN LOERA, Joaquin"],
            should_not_match=["WEI, Zhao"],
            min_score=0.85
        )
        assert "GUZMAN LOERA, Joaquin" in tc.expected_matches
        assert "WEI, Zhao" in tc.should_not_match
        assert tc.min_score == 0.85


class TestRandomSamplingGenerator:
    """Test random sampling from OFAC entities."""
    
    @pytest.fixture
    def mock_ofac_entities(self):
        return [
            {"id": "SDN-1", "name": "GUZMAN LOERA, Joaquin", "altNames": ["El Chapo", "Chapo Guzman"]},
            {"id": "SDN-2", "name": "BIN LADEN, Osama", "altNames": ["Osama bin Laden"]},
            {"id": "SDN-3", "name": "ASSAD, Bashar al", "altNames": ["Al-Assad", "Bashar Assad"]},
            {"id": "SDN-4", "name": "KIM, Jong Un", "altNames": []},
            {"id": "SDN-5", "name": "WEI, Zhao", "altNames": []}
        ]
    
    def test_generate_returns_correct_count(self, mock_ofac_entities):
        generator = RandomSamplingGenerator(entities=mock_ofac_entities)
        test_cases = generator.generate(count=10)
        assert len(test_cases) == 10
        
    def test_all_test_cases_have_source_entity(self, mock_ofac_entities):
        generator = RandomSamplingGenerator(entities=mock_ofac_entities)
        test_cases = generator.generate(count=5)
        for tc in test_cases:
            assert tc.source_entity_id is not None
            assert tc.source_entity_id.startswith("SDN-")
            
    def test_generates_name_variations(self, mock_ofac_entities):
        generator = RandomSamplingGenerator(entities=mock_ofac_entities)
        test_cases = generator.generate(count=30)
        
        variation_types = {tc.variation_type for tc in test_cases}
        # Should see multiple variation types (not just full_name)
        assert len(variation_types) > 1
        assert "full_name" in variation_types
        
    def test_uses_alt_names(self, mock_ofac_entities):
        generator = RandomSamplingGenerator(entities=mock_ofac_entities)
        test_cases = generator.generate(count=100)  # Increase to 100 for better probability
        
        queries = [tc.query for tc in test_cases]
        # Should eventually sample "El Chapo" or other alt names
        has_alt_name = any("Chapo" in q or "Assad" in q or "Osama" in q for q in queries)
        assert has_alt_name, "Expected to find at least one alt name in 100 queries"
        
    def test_expected_match_set_correctly(self, mock_ofac_entities):
        generator = RandomSamplingGenerator(entities=mock_ofac_entities)
        test_cases = generator.generate(count=10)
        
        for tc in test_cases:
            # If testing a variation of an entity, expect that entity to match
            assert tc.expected_matches is not None
            assert len(tc.expected_matches) > 0


class TestVariationGenerator:
    """Test name variation generation."""
    
    def test_extract_first_name_standard_format(self):
        variations = VariationGenerator.generate_variations("GUZMAN LOERA, Joaquin")
        queries = [v.query for v in variations]
        assert "Joaquin" in queries
        
    def test_extract_last_name_standard_format(self):
        variations = VariationGenerator.generate_variations("GUZMAN LOERA, Joaquin")
        queries = [v.query for v in variations]
        assert "GUZMAN LOERA" in queries
        
    def test_reversed_format(self):
        variations = VariationGenerator.generate_variations("BIN LADEN, Osama")
        queries = [v.query for v in variations]
        assert "Osama BIN LADEN" in queries
        
    def test_full_name_included(self):
        variations = VariationGenerator.generate_variations("ASSAD, Bashar al")
        queries = [v.query for v in variations]
        assert "ASSAD, Bashar al" in queries
        
    def test_handles_alt_names(self):
        variations = VariationGenerator.generate_variations(
            "GUZMAN LOERA, Joaquin",
            alt_names=["El Chapo", "El Chapo Guzman"]
        )
        queries = [v.query for v in variations]
        assert "El Chapo" in queries
        
    def test_alt_name_partials(self):
        variations = VariationGenerator.generate_variations(
            "GUZMAN LOERA, Joaquin",
            alt_names=["El Chapo Guzman"]
        )
        queries = [v.query for v in variations]
        # Should extract "Chapo" and "Guzman" from alt name
        assert "Chapo" in queries or "Guzman" in queries
        
    def test_all_variations_have_metadata(self):
        variations = VariationGenerator.generate_variations("TEST, Name")
        for v in variations:
            assert v.strategy == "variation"
            assert v.variation_type is not None
            assert v.query is not None
