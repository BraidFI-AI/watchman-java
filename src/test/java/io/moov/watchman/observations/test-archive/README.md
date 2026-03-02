# Test Archive - Historical Debugging Tests

This directory contains individual test files created during active debugging with the BSA consultant.

## Purpose

These tests were created iteratively while fixing specific issues in Rounds 1 and 2 of BSA observations. They represent the detailed investigation and debugging history.

## Organization

- **Row* files**: Tests for specific observation rows (e.g., Row17CkIdPartialNameTest.java)
- **Debug tests**: Detailed scoring breakdowns and diagnostics
- **Investigation tests**: Root cause analysis for specific issues

## Current Status

These tests are **archived for historical reference**. Active validation uses comprehensive test suites:

- Parent directory: **R2EntityValidationTest.java** (50 entity rows)
- Parent directory: **R2IndividualValidationTest.java** (50 individual rows)
- Parent directory: **ComprehensiveBSAValidationTest.java** (52 R1 rows)

## Contents

Total: 28 test files covering:
- Row 1 (ABBAS): Row1AbbasSearchTest.java, Row01RelatedEntitiesTest.java
- Row 15 (GHAILANI): Row15GhailaniAliasTest.java
- Row 17 (CK ID): 5 test files - investigation, partial name, debugging
- Row 24 (SMARTMET): 7 test files - complete debugging history
- Row 31 (TEG): Row31DebugTest.java
- Row 50 (KIM): 7 test files - apostrophe fix investigation

## Usage

Keep these files for:
- Understanding fix history and rationale
- Detailed debugging when similar issues resurface
- Reference for specific edge cases
- Context for decisions documented in agent-decisions.md

Do not delete - these represent institutional knowledge of the debugging process.
