#!/usr/bin/env python3
"""
OFAC Sanctions Screening System Stress Test Suite
==================================================

A comprehensive testing framework for validating OFAC sanctions screening system 
effectiveness, including exact name matches, transliteration variants, fuzzy matching,
and field coverage validation.

Usage:
    python ofac_stress_test_script.py --config config.json --output test_results.csv
    python ofac_stress_test_script.py --test-type exact --threshold 85
    python ofac_stress_test_script.py --generate-test-data --format json --count 100

Author: Compliance Testing Framework
Version: 1.0.0
Date: January 2026
"""

import json
import csv
import sys
import argparse
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Tuple, Optional
from enum import Enum
import random
from dataclasses import dataclass, asdict

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('screening_test.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class TestCategory(Enum):
    """Test case categories for comprehensive coverage"""
    EXACT_NAME = "exact_name"
    TRANSLITERATION_ARABIC = "transliteration_arabic"
    TRANSLITERATION_CYRILLIC = "transliteration_cyrillic"
    TRANSLITERATION_CHINESE = "transliteration_chinese"
    FUZZY_MATCHING = "fuzzy_matching"
    WEAK_ALIAS = "weak_alias"
    ENTITY_VARIATION = "entity_variation"
    FIELD_COVERAGE = "field_coverage"
    COMMON_NAME = "common_name"
    ABBREVIATION = "abbreviation"


class AlertExpectation(Enum):
    """Expected screening outcomes"""
    MUST_ALERT = "must_alert"  # 100% detection required
    SHOULD_ALERT = "should_alert"  # >=90% detection expected
    MAY_ALERT = "may_alert"  # Optional, depends on threshold
    MUST_NOT_ALERT = "must_not_alert"  # Should not generate alerts
    CONTEXT_DEPENDENT = "context_dependent"  # Depends on attributes


class MessageType(Enum):
    """Message format types for field coverage testing"""
    SWIFT_MT103 = "SWIFT_MT103"
    ISO_20022 = "ISO_20022"
    INTERNAL_CUSTOMER = "INTERNAL_CUSTOMER"
    BULK_UPLOAD = "BULK_UPLOAD"


@dataclass
class TestCase:
    """Individual test case with expected outcomes"""
    test_id: str
    category: TestCategory
    message_type: MessageType
    field_name: str
    test_name: str
    test_value: str  # Full name, number, etc.
    expected_outcome: AlertExpectation
    fuzzy_threshold: int  # Expected fuzzy match threshold for detection
    description: str
    sdl_list: str  # Which sanctions list (OFAC, EU, UN, etc.)
    additional_attributes: Dict  # DOB, nationality, address for disambiguation
    notes: str  # Test rationale and edge cases

    def to_dict(self) -> Dict:
        """Convert to dictionary for CSV/JSON export"""
        return asdict(self)


class ScreeningTestDataGenerator:
    """Generate comprehensive stress test data for sanctions screening systems"""

    def __init__(self):
        """Initialize test data generator with realistic test cases"""
        self.test_cases: List[TestCase] = []
        self._generate_all_test_cases()

    def _generate_all_test_cases(self):
        """Generate all test case categories"""
        logger.info("Generating comprehensive test case library...")
        self._generate_exact_name_tests()
        self._generate_transliteration_tests()
        self._generate_fuzzy_matching_tests()
        self._generate_weak_alias_tests()
        self._generate_entity_variation_tests()
        self._generate_field_coverage_tests()
        self._generate_common_name_tests()
        self._generate_abbreviation_tests()
        logger.info(f"Generated {len(self.test_cases)} test cases")

    def _generate_exact_name_tests(self):
        """EXACT NAME TESTS - 100% detection required"""
        
        exact_tests = [
            # High-profile SDN designations (known to be on OFAC list)
            TestCase(
                test_id="EX-001",
                category=TestCategory.EXACT_NAME,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Usama bin Muhammad bin Awad BIN LADIN",
                test_value="USAMA BIN MUHAMMAD BIN AWAD BIN LADIN",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="OFAC SDN designation - primary name",
                sdl_list="OFAC",
                additional_attributes={
                    "entity_type": "individual",
                    "designation_date": "2001-09-11"
                },
                notes="Highest priority test case - core sanctions screening requirement"
            ),
            TestCase(
                test_id="EX-002",
                category=TestCategory.EXACT_NAME,
                message_type=MessageType.SWIFT_MT103,
                field_name="59",
                test_name="OSAMA BIN LADEN",
                test_value="OSAMA BIN LADEN",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="OFAC SDN - common alias variant",
                sdl_list="OFAC",
                additional_attributes={},
                notes="Alternative spelling appearing in payment documents"
            ),
            TestCase(
                test_id="EX-003",
                category=TestCategory.EXACT_NAME,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Vladimir Vladimirovich Putin",
                test_value="VLADIMIR VLADIMIROVICH PUTIN",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="OFAC SDN - Russia sanctions (E.O. 14024)",
                sdl_list="OFAC",
                additional_attributes={
                    "entity_type": "individual",
                    "position": "President of Russian Federation",
                    "designation_date": "2022-02-24"
                },
                notes="Validates detection of high-profile political figures"
            ),
            TestCase(
                test_id="EX-004",
                category=TestCategory.EXACT_NAME,
                message_type=MessageType.SWIFT_MT103,
                field_name="52A",
                test_name="VTB Bank",
                test_value="VTB BANK",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="OFAC SDN - Russian entity",
                sdl_list="OFAC",
                additional_attributes={
                    "entity_type": "organization",
                    "jurisdiction": "Russia"
                },
                notes="Organization name exact match in financial institution field"
            ),
            TestCase(
                test_id="EX-005",
                category=TestCategory.EXACT_NAME,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="customer_name",
                test_name="ISLAMIC STATE OF IRAQ AND THE LEVANT",
                test_value="ISLAMIC STATE OF IRAQ AND THE LEVANT",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="OFAC SDN - terrorist organization",
                sdl_list="OFAC",
                additional_attributes={
                    "entity_type": "organization",
                    "designation_type": "terrorist"
                },
                notes="Tests organization name detection - also appears as ISIS, ISIL variants"
            ),
        ]
        self.test_cases.extend(exact_tests)

    def _generate_transliteration_tests(self):
        """TRANSLITERATION TESTS - Handle name variations from non-Latin scripts"""
        
        # Arabic transliteration variants
        arabic_tests = [
            TestCase(
                test_id="TR-AR-001",
                category=TestCategory.TRANSLITERATION_ARABIC,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Usama bin Laden - Hamza variant",
                test_value="USAMA BIN LADIN",  # Without 'e'
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=92,
                description="Arabic transliteration - hamza (glottal stop) omission",
                sdl_list="OFAC",
                additional_attributes={
                    "source_script": "Arabic",
                    "transliteration_standard": "variant"
                },
                notes="Hamza may be represented as apostrophe or omitted entirely"
            ),
            TestCase(
                test_id="TR-AR-002",
                category=TestCategory.TRANSLITERATION_ARABIC,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Muhammad - common name variants",
                test_value="MOHAMMAD",
                expected_outcome=AlertExpectation.MAY_ALERT,
                fuzzy_threshold=88,
                description="Arabic name - multiple transliteration standards",
                sdl_list="OFAC",
                additional_attributes={
                    "actual_name": "محمد",
                    "variants": ["Mohammad", "Mohammed", "Mohamed", "Muhammed"]
                },
                notes="Most common name globally; requires attribute matching to avoid false positives"
            ),
            TestCase(
                test_id="TR-AR-003",
                category=TestCategory.TRANSLITERATION_ARABIC,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Abdul/Abd Rahman variant",
                test_value="ABD RAHMAN",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=90,
                description="Arabic: Abdul vs Abd abbreviation",
                sdl_list="OFAC",
                additional_attributes={
                    "full_form": "ABDUL RAHMAN",
                    "abbreviated_form": "ABD RAHMAN"
                },
                notes="Tests system handling of Arabic name component abbreviations"
            ),
        ]
        self.test_cases.extend(arabic_tests)

        # Russian/Cyrillic transliteration variants
        cyrillic_tests = [
            TestCase(
                test_id="TR-CY-001",
                category=TestCategory.TRANSLITERATION_CYRILLIC,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Aleksandr (Russian) - variant spelling",
                test_value="ALEXANDER",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=88,
                description="Cyrillic: Aleksandr transliteration variants",
                sdl_list="OFAC",
                additional_attributes={
                    "cyrillic": "Александр",
                    "transliteration_variants": [
                        "ALEKSANDR", "ALEXANDER", "ALEKSANDER", "OLEKSANDER"
                    ]
                },
                notes="Common Russian name with multiple accepted transliterations"
            ),
            TestCase(
                test_id="TR-CY-002",
                category=TestCategory.TRANSLITERATION_CYRILLIC,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Cyrillic false friend - mixed script",
                test_value="ROMASHKІN",  # Contains Cyrillic І instead of Latin I
                expected_outcome=AlertExpectation.MUST_NOT_ALERT,
                fuzzy_threshold=0,
                description="Mixed Cyrillic/Latin - deceptive similar appearance",
                sdl_list="OFAC",
                additional_attributes={
                    "issue": "Unicode character U+0406 (Cyrillic capital I) vs U+0049 (Latin I)",
                    "visual_similarity": "identical to human eye",
                    "computer_difference": "completely different"
                },
                notes="Critical security test - visually identical but different Unicode. Must NOT match ROMASHKIN"
            ),
            TestCase(
                test_id="TR-CY-003",
                category=TestCategory.TRANSLITERATION_CYRILLIC,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="customer_name",
                test_name="Islam Seit-Umarovich Atabiyev",
                test_value="ISLAM SEIT-UMAROVICH ATABIYEV",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=85,
                description="Patronymic name component recognition",
                sdl_list="OFAC",
                additional_attributes={
                    "first_name": "ISLAM",
                    "patronymic": "SEIT-UMAROVICH",
                    "family_name": "ATABIYEV"
                },
                notes="Russian naming convention: first name + patronymic (father's name) + family name"
            ),
        ]
        self.test_cases.extend(cyrillic_tests)

        # Chinese/CJK transliteration variants
        chinese_tests = [
            TestCase(
                test_id="TR-CH-001",
                category=TestCategory.TRANSLITERATION_CHINESE,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="customer_name",
                test_name="Chinese name - original script",
                test_value="王小明",  # Wang Xiaoming in Chinese characters
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=95,
                description="CJK: Original script screening",
                sdl_list="OFAC",
                additional_attributes={
                    "pinyin_transliteration": "WANG XIAOMING",
                    "name_order": "family_name given_name"
                },
                notes="System should detect original script if configured. Critical for CJK entities."
            ),
            TestCase(
                test_id="TR-CH-002",
                category=TestCategory.TRANSLITERATION_CHINESE,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Pinyin transliteration - name order",
                test_value="WANG XIAO MING",  # Western order with spaces
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=85,
                description="CJK: Pinyin with Western name order",
                sdl_list="OFAC",
                additional_attributes={
                    "original_script": "王小明",
                    "western_order": "XIAO MING WANG"
                },
                notes="Name order may reverse between Chinese (family first) and Western (family last) formats"
            ),
        ]
        self.test_cases.extend(chinese_tests)

    def _generate_fuzzy_matching_tests(self):
        """FUZZY MATCHING TESTS - Single character variations at different thresholds"""
        
        fuzzy_tests = [
            # Single character substitutions
            TestCase(
                test_id="FZ-001",
                category=TestCategory.FUZZY_MATCHING,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Single character substitution (1% variance)",
                test_value="USAMO BIN LADIN",  # 'S' instead of 'M'
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=98,
                description="Jaro-Winkler similarity ~99%",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "USAMA BIN LADIN",
                    "variance_type": "single_substitution",
                    "jaro_winkler_score": 0.993
                },
                notes="Typo or data entry error - single character difference"
            ),
            TestCase(
                test_id="FZ-002",
                category=TestCategory.FUZZY_MATCHING,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Single character deletion (Levenshtein 1)",
                test_value="USAMA BIN LDIN",  # Missing 'A'
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=95,
                description="Levenshtein distance = 1",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "USAMA BIN LADIN",
                    "variance_type": "single_deletion",
                    "levenshtein_distance": 1,
                    "string_similarity": 0.967
                },
                notes="Character deletion - common OCR or data quality issue"
            ),
            TestCase(
                test_id="FZ-003",
                category=TestCategory.FUZZY_MATCHING,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Single character insertion (Levenshtein 1)",
                test_value="USAMAA BIN LADIN",  # Extra 'A'
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=95,
                description="Levenshtein distance = 1",
                sdl_list="OFAC",
                additional_attributes={
                    "variance_type": "single_insertion",
                    "levenshtein_distance": 1
                },
                notes="Character insertion - duplicate keystroke"
            ),
            TestCase(
                test_id="FZ-004",
                category=TestCategory.FUZZY_MATCHING,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Abbreviation expansion (Title variation)",
                test_value="USAMA BIN LADIN",  # vs 'USAMA B BIN LADIN'
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=90,
                description="Abbreviation normalization",
                sdl_list="OFAC",
                additional_attributes={
                    "variant1": "USAMA B BIN LADIN",
                    "variant2": "USAMA BIN LADIN",
                    "abbreviation_type": "middle_name_initial"
                },
                notes="Handles abbreviated vs full name forms"
            ),
            TestCase(
                test_id="FZ-005",
                category=TestCategory.FUZZY_MATCHING,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Phonetic match - Soundex",
                test_value="SMYTH",
                expected_outcome=AlertExpectation.MAY_ALERT,
                fuzzy_threshold=85,
                description="Soundex match: Smith = Smyth (phonetic equivalent)",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "SMITH",
                    "phonetic_match": "Soundex S530",
                    "algorithm": "Soundex"
                },
                notes="Phonetic matching for name pronunciation variants"
            ),
        ]
        self.test_cases.extend(fuzzy_tests)

    def _generate_weak_alias_tests(self):
        """WEAK ALIAS TESTS - Common names that generate high false positive volumes"""
        
        weak_tests = [
            TestCase(
                test_id="WA-001",
                category=TestCategory.WEAK_ALIAS,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Very common English name",
                test_value="JOHN SMITH",
                expected_outcome=AlertExpectation.MUST_NOT_ALERT,
                fuzzy_threshold=0,
                description="Common name - generates hundreds of false positives",
                sdl_list="OFAC",
                additional_attributes={
                    "name_frequency": "top_100_English",
                    "weak_alias": True,
                    "dob": "1990-06-15",
                    "nationality": "US"
                },
                notes="Without additional attributes (DOB, SSN, address), cannot distinguish. OFAC guidance permits exclusion."
            ),
            TestCase(
                test_id="WA-002",
                category=TestCategory.WEAK_ALIAS,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="customer_name",
                test_name="Global common name - Muhammad",
                test_value="MUHAMMAD AHMED",
                expected_outcome=AlertExpectation.MUST_NOT_ALERT,
                fuzzy_threshold=0,
                description="Most common name globally - unmanageable false positive volume",
                sdl_list="OFAC",
                additional_attributes={
                    "name_frequency": "most_common_globally",
                    "weak_alias": True,
                    "estimated_matches": 5000000
                },
                notes="Requires full attribute matching (DOB, nationality, occupation) for disambiguation"
            ),
            TestCase(
                test_id="WA-003",
                category=TestCategory.WEAK_ALIAS,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Weak alias - partial name only",
                test_value="DR AMIN",
                expected_outcome=AlertExpectation.MUST_NOT_ALERT,
                fuzzy_threshold=0,
                description="Insufficient information - surname only",
                sdl_list="OFAC",
                additional_attributes={
                    "issue": "partial_name_only",
                    "missing_information": "first_name"
                },
                notes="Insufficient identifying information to accurately match - high false positive risk"
            ),
        ]
        self.test_cases.extend(weak_tests)

    def _generate_entity_variation_tests(self):
        """ENTITY VARIATION TESTS - Organization name and legal form variations"""
        
        entity_tests = [
            TestCase(
                test_id="EV-001",
                category=TestCategory.ENTITY_VARIATION,
                message_type=MessageType.SWIFT_MT103,
                field_name="52A",
                test_name="Legal form variation - Limited vs Ltd",
                test_value="ABC INTERNATIONAL LIMITED",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=98,
                description="Organization name with UK legal form",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "ABC INTERNATIONAL LTD",
                    "variant_type": "legal_form_expansion",
                    "jurisdiction": "United Kingdom"
                },
                notes="Limited and Ltd are equivalent in UK law - system should match both"
            ),
            TestCase(
                test_id="EV-002",
                category=TestCategory.ENTITY_VARIATION,
                message_type=MessageType.SWIFT_MT103,
                field_name="52A",
                test_name="Legal form variation - Corporation vs Corp",
                test_value="ACME CORPORATION",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=95,
                description="US Corporation vs abbreviated Corp",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "ACME CORP",
                    "variant_type": "legal_form_abbreviation",
                    "jurisdiction": "United States"
                },
                notes="Corp and Corporation functionally equivalent - testing abbreviation normalization"
            ),
            TestCase(
                test_id="EV-003",
                category=TestCategory.ENTITY_VARIATION,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="entity_name",
                test_name="Common business token - International",
                test_value="RIXO INTERNATIONAL TRADING LTD",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=85,
                description="Organization with common business tokens",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "RIXO INTERNATIONAL LTD",
                    "distinctive_tokens": ["RIXO"],
                    "common_tokens": ["INTERNATIONAL", "TRADING", "LTD"],
                    "note": "Should match - core distinctive name is RIXO"
                },
                notes="Token weighting important: RIXO is distinctive, INTERNATIONAL/TRADING less so"
            ),
            TestCase(
                test_id="EV-004",
                category=TestCategory.ENTITY_VARIATION,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="entity_name",
                test_name="Different entities - similar names",
                test_value="RIX INTERNATIONAL LLC",
                expected_outcome=AlertExpectation.MUST_NOT_ALERT,
                fuzzy_threshold=0,
                description="Similar but distinct organizations",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "RIXO INTERNATIONAL LTD",
                    "difference": "RIXO vs RIX (core name differs)",
                    "jurisdiction_difference": "LTD (UK) vs LLC (US)"
                },
                notes="Despite 85% string similarity, should NOT match - core name differs (RIXO != RIX)"
            ),
        ]
        self.test_cases.extend(entity_tests)

    def _generate_field_coverage_tests(self):
        """FIELD COVERAGE TESTS - Same sanctioned name in different message fields"""
        
        field_tests = [
            TestCase(
                test_id="FC-001",
                category=TestCategory.FIELD_COVERAGE,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Customer field (Ordering Customer)",
                test_value="USAMA BIN LADIN",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="SWIFT MT103 Field 50F - Primary customer name",
                sdl_list="OFAC",
                additional_attributes={
                    "message_type": "SWIFT MT103",
                    "field_purpose": "Ordering customer",
                    "field_code": "50F"
                },
                notes="Primary field for customer screening"
            ),
            TestCase(
                test_id="FC-002",
                category=TestCategory.FIELD_COVERAGE,
                message_type=MessageType.SWIFT_MT103,
                field_name="59",
                test_name="Beneficiary field",
                test_value="USAMA BIN LADIN",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="SWIFT MT103 Field 59 - Beneficiary customer",
                sdl_list="OFAC",
                additional_attributes={
                    "message_type": "SWIFT MT103",
                    "field_purpose": "Beneficiary customer",
                    "field_code": "59"
                },
                notes="Critical field - beneficiary screening requirement"
            ),
            TestCase(
                test_id="FC-003",
                category=TestCategory.FIELD_COVERAGE,
                message_type=MessageType.SWIFT_MT103,
                field_name="52A",
                test_name="Ordering Institution (Bank) field",
                test_value="USAMA BIN LADIN",
                expected_outcome=AlertExpectation.MUST_ALERT,
                fuzzy_threshold=100,
                description="SWIFT MT103 Field 52A - Ordering Institution",
                sdl_list="OFAC",
                additional_attributes={
                    "message_type": "SWIFT MT103",
                    "field_purpose": "Ordering financial institution",
                    "field_code": "52A"
                },
                notes="Tests detection in institution/intermediary fields"
            ),
            TestCase(
                test_id="FC-004",
                category=TestCategory.FIELD_COVERAGE,
                message_type=MessageType.SWIFT_MT103,
                field_name="70",
                test_name="Free-text/Remittance field",
                test_value="PAYMENT FOR USAMA BIN LADIN",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=95,
                description="SWIFT MT103 Field 70 - Remittance information",
                sdl_list="OFAC",
                additional_attributes={
                    "message_type": "SWIFT MT103",
                    "field_purpose": "Free text / remittance information",
                    "field_code": "70",
                    "challenge": "Name embedded in natural language text"
                },
                notes="Tests detection of sanctioned names in unstructured text fields"
            ),
        ]
        self.test_cases.extend(field_tests)

    def _generate_common_name_tests(self):
        """COMMON NAME TESTS - Valid individuals that might match weak aliases"""
        
        common_tests = [
            TestCase(
                test_id="CN-001",
                category=TestCategory.COMMON_NAME,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="customer_name",
                test_name="John Smith with full attributes",
                test_value="JOHN SMITH",
                expected_outcome=AlertExpectation.CONTEXT_DEPENDENT,
                fuzzy_threshold=0,
                description="Common name with disambiguating attributes",
                sdl_list="OFAC",
                additional_attributes={
                    "dob": "1990-06-15",
                    "nationality": "United States",
                    "ssn": "123-45-6789",
                    "address": "123 Main St, New York, NY"
                },
                notes="With full attributes, should NOT match sanctioned John Smith with different DOB/SSN"
            ),
            TestCase(
                test_id="CN-002",
                category=TestCategory.COMMON_NAME,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Muhammad Ahmed - partial attributes",
                test_value="MUHAMMAD AHMED",
                expected_outcome=AlertExpectation.CONTEXT_DEPENDENT,
                fuzzy_threshold=0,
                description="Common Arabic name - attributes critical",
                sdl_list="OFAC",
                additional_attributes={
                    "dob": "1985-03-20",
                    "nationality": "Saudi Arabia",
                    "address": "Riyadh, Saudi Arabia"
                },
                notes="Common name requires attribute validation. May match if SDN has similar attributes."
            ),
        ]
        self.test_cases.extend(common_tests)

    def _generate_abbreviation_tests(self):
        """ABBREVIATION TESTS - Name abbreviation and expansion handling"""
        
        abbrev_tests = [
            TestCase(
                test_id="AB-001",
                category=TestCategory.ABBREVIATION,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Middle initial vs full middle name",
                test_value="USAMA M BIN LADIN",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=92,
                description="Name with middle initial",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "USAMA MAHMOUD BIN LADIN",
                    "variant_type": "middle_initial_vs_full",
                    "initial": "M"
                },
                notes="System should recognize middle initial abbreviations"
            ),
            TestCase(
                test_id="AB-002",
                category=TestCategory.ABBREVIATION,
                message_type=MessageType.SWIFT_MT103,
                field_name="50F",
                test_name="Jr vs Junior",
                test_value="USAMA BIN LADIN JR",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=92,
                description="Suffix abbreviation handling",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "USAMA BIN LADIN JUNIOR",
                    "variant_type": "suffix_abbreviation",
                    "variations": ["JR", "JR.", "JUNIOR"]
                },
                notes="Handles Jr/Junior, Sr/Senior, III/3rd variations"
            ),
            TestCase(
                test_id="AB-003",
                category=TestCategory.ABBREVIATION,
                message_type=MessageType.INTERNAL_CUSTOMER,
                field_name="entity_name",
                test_name="Company name abbreviation",
                test_value="VTB BANK JSC",
                expected_outcome=AlertExpectation.SHOULD_ALERT,
                fuzzy_threshold=90,
                description="Russian entity type abbreviation",
                sdl_list="OFAC",
                additional_attributes={
                    "sdl_name": "VTB BANK OJSC",
                    "abbreviation_type": "corporate_form",
                    "variants": ["JSC", "OJSC", "PJSC"]
                },
                notes="Russian corporate forms JSC/OJSC/PJSC are equivalent"
            ),
        ]
        self.test_cases.extend(abbrev_tests)

    def export_test_cases(self, format: str, output_file: str):
        """Export test cases to file in specified format"""
        logger.info(f"Exporting {len(self.test_cases)} test cases to {format.upper()}")
        
        if format.lower() == "csv":
            self._export_csv(output_file)
        elif format.lower() == "json":
            self._export_json(output_file)
        elif format.lower() == "both":
            self._export_csv(output_file.replace(".csv", "") + ".csv")
            self._export_json(output_file.replace(".json", "") + ".json")
        else:
            raise ValueError(f"Unknown format: {format}")

    def _export_csv(self, output_file: str):
        """Export to CSV format"""
        if not self.test_cases:
            logger.warning("No test cases to export")
            return
        
        try:
            with open(output_file, 'w', newline='', encoding='utf-8') as csvfile:
                fieldnames = [
                    'test_id', 'category', 'message_type', 'field_name',
                    'test_name', 'test_value', 'expected_outcome', 'fuzzy_threshold',
                    'description', 'sdl_list', 'notes'
                ]
                writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
                writer.writeheader()
                
                for test_case in self.test_cases:
                    row = {
                        'test_id': test_case.test_id,
                        'category': test_case.category.value,
                        'message_type': test_case.message_type.value,
                        'field_name': test_case.field_name,
                        'test_name': test_case.test_name,
                        'test_value': test_case.test_value,
                        'expected_outcome': test_case.expected_outcome.value,
                        'fuzzy_threshold': test_case.fuzzy_threshold,
                        'description': test_case.description,
                        'sdl_list': test_case.sdl_list,
                        'notes': test_case.notes
                    }
                    writer.writerow(row)
            
            logger.info(f"CSV export successful: {output_file}")
        except Exception as e:
            logger.error(f"CSV export failed: {e}")
            raise

    def _export_json(self, output_file: str):
        """Export to JSON format"""
        if not self.test_cases:
            logger.warning("No test cases to export")
            return
        
        try:
            export_data = {
                "export_date": datetime.now().isoformat(),
                "total_test_cases": len(self.test_cases),
                "test_breakdown": self._get_test_breakdown(),
                "test_cases": [test_case.to_dict() for test_case in self.test_cases]
            }
            
            # Convert enums to strings for JSON serialization
            for test_case in export_data['test_cases']:
                test_case['category'] = test_case['category'].value if isinstance(test_case['category'], TestCategory) else test_case['category']
                test_case['message_type'] = test_case['message_type'].value if isinstance(test_case['message_type'], MessageType) else test_case['message_type']
                test_case['expected_outcome'] = test_case['expected_outcome'].value if isinstance(test_case['expected_outcome'], AlertExpectation) else test_case['expected_outcome']
            
            with open(output_file, 'w', encoding='utf-8') as jsonfile:
                json.dump(export_data, jsonfile, indent=2, ensure_ascii=False)
            
            logger.info(f"JSON export successful: {output_file}")
        except Exception as e:
            logger.error(f"JSON export failed: {e}")
            raise

    def _get_test_breakdown(self) -> Dict:
        """Get count of test cases by category"""
        breakdown = {}
        for test_case in self.test_cases:
            category = test_case.category.value
            breakdown[category] = breakdown.get(category, 0) + 1
        return breakdown


class ScreeningTestResults:
    """Capture and analyze screening system test results"""

    def __init__(self):
        self.results = []
        self.summary = {
            "total_tests": 0,
            "passed": 0,
            "failed": 0,
            "inconclusive": 0,
            "execution_time_seconds": 0.0
        }

    def add_result(self, test_id: str, test_case: TestCase, alert_generated: bool,
                   match_score: int, notes: str = ""):
        """Record test result"""
        result = {
            "test_id": test_id,
            "category": test_case.category.value,
            "test_name": test_case.test_name,
            "expected_outcome": test_case.expected_outcome.value,
            "alert_generated": alert_generated,
            "match_score": match_score,
            "passed": self._evaluate_result(test_case.expected_outcome, alert_generated, match_score),
            "timestamp": datetime.now().isoformat(),
            "notes": notes
        }
        self.results.append(result)

    def _evaluate_result(self, expected: AlertExpectation, alert_generated: bool, score: int) -> bool:
        """Evaluate if test result matches expectation"""
        if expected == AlertExpectation.MUST_ALERT:
            return alert_generated and score >= 95
        elif expected == AlertExpectation.SHOULD_ALERT:
            return alert_generated and score >= 85
        elif expected == AlertExpectation.MAY_ALERT:
            return True  # Either outcome acceptable
        elif expected == AlertExpectation.MUST_NOT_ALERT:
            return not alert_generated
        else:  # CONTEXT_DEPENDENT
            return True  # Requires manual review
        
    def generate_report(self) -> str:
        """Generate summary report"""
        total = len(self.results)
        passed = sum(1 for r in self.results if r['passed'])
        failed = total - passed
        
        report = f"""
OFAC SANCTIONS SCREENING SYSTEM TEST REPORT
============================================
Generated: {datetime.now().isoformat()}

SUMMARY
-------
Total Tests Run:           {total}
Passed:                    {passed} ({100*passed/total:.1f}%)
Failed:                    {failed} ({100*failed/total:.1f}%)
Success Rate:              {100*passed/total:.1f}%

TEST BREAKDOWN BY CATEGORY
--------------------------
"""
        category_breakdown = {}
        for result in self.results:
            cat = result['category']
            if cat not in category_breakdown:
                category_breakdown[cat] = {'total': 0, 'passed': 0}
            category_breakdown[cat]['total'] += 1
            if result['passed']:
                category_breakdown[cat]['passed'] += 1
        
        for category in sorted(category_breakdown.keys()):
            stats = category_breakdown[category]
            success_rate = 100 * stats['passed'] / stats['total']
            report += f"{category}: {stats['passed']}/{stats['total']} ({success_rate:.1f}%)\n"
        
        report += f"""
FAILED TESTS
-----------
"""
        failed_tests = [r for r in self.results if not r['passed']]
        if failed_tests:
            for test in failed_tests[:10]:  # Show first 10 failures
                report += f"  - {test['test_id']}: {test['test_name']}\n"
                report += f"    Expected: {test['expected_outcome']}, Got: Alert={test['alert_generated']}, Score={test['match_score']}\n"
        else:
            report += "  None - All tests passed!\n"
        
        return report

    def export_results(self, output_file: str, format: str = "json"):
        """Export results to file"""
        try:
            if format == "json":
                with open(output_file, 'w', encoding='utf-8') as f:
                    json.dump(self.results, f, indent=2)
            elif format == "csv":
                with open(output_file, 'w', newline='', encoding='utf-8') as f:
                    writer = csv.DictWriter(f, fieldnames=self.results[0].keys())
                    writer.writeheader()
                    writer.writerows(self.results)
            logger.info(f"Results exported to {output_file}")
        except Exception as e:
            logger.error(f"Export failed: {e}")
            raise


def main():
    parser = argparse.ArgumentParser(
        description='OFAC Sanctions Screening System Stress Test Suite',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python ofac_stress_test_script.py --generate-test-data --format json --output tests.json
  python ofac_stress_test_script.py --generate-test-data --format csv --output tests.csv
  python ofac_stress_test_script.py --generate-test-data --format both --output tests
        """
    )
    
    parser.add_argument('--generate-test-data', action='store_true',
                        help='Generate comprehensive test data')
    parser.add_argument('--format', default='json', choices=['json', 'csv', 'both'],
                        help='Output format')
    parser.add_argument('--output', default='ofac_test_cases',
                        help='Output file path (without extension)')
    parser.add_argument('--log-level', default='INFO', choices=['DEBUG', 'INFO', 'WARNING', 'ERROR'],
                        help='Logging level')
    
    args = parser.parse_args()
    
    # Set logging level
    logging.getLogger().setLevel(getattr(logging, args.log_level))
    
    if args.generate_test_data:
        logger.info("Initializing test data generator...")
        generator = ScreeningTestDataGenerator()
        
        # Format output filename
        if args.format == 'both':
            base = args.output
        else:
            ext = '.json' if args.format == 'json' else '.csv'
            base = args.output if args.output.endswith(ext) else args.output + ext
        
        generator.export_test_cases(args.format, base)
        logger.info("Test data generation complete!")
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
