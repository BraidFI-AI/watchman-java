#!/usr/bin/env python3
"""
Comprehensive fix for Phase16ZoneOneCompletionTest.java constructor issues.
"""

import re
import sys

def fix_phase16_test():
    file_path = "src/test/java/io/moov/watchman/similarity/Phase16ZoneOneCompletionTest.java"
    
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Track changes
    changes = []
    
    # 1. Fix Person constructors - remove first "id" parameter
    # Pattern: new Person(\n    "id",\n    "name",
    old_pattern = r'new Person\(\s*"[^"]+",\s*\n\s*("[^"]+",)'
    new_replacement = r'new Person(\n                    \1'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} Person constructors (removed id param)")
    
    # 2. Fix Business constructors - remove first "id" parameter
    old_pattern = r'new Business\(\s*"[^"]+",\s*\n\s*("[^"]+",)'
    new_replacement = r'new Business(\n                    \1'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} Business constructors (removed id param)")
    
    # 3. Fix Aircraft constructors - remove first "id" parameter  
    old_pattern = r'new Aircraft\(\s*"[^"]+",\s*\n\s*("[^"]+",)'
    new_replacement = r'new Aircraft(\n                    \1'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} Aircraft constructors (removed id param)")
    
    # 4. Fix Entity constructors - this is the complex one
    # Pattern: Entity(\n    "id",\n    EntityType.XXX,\n    "name",\n    "source",
    # Replace with: Entity(\n    "id",\n    "name",\n    EntityType.XXX,\n    SourceList.US_OFAC,\n    "id",
    
    # First pass: swap EntityType and name, fix source
    old_pattern = r'new Entity\(\s*\n\s*("[^"]+"),\s*\n\s*(EntityType\.\w+),\s*\n\s*("[^"]+"),\s*\n\s*"[^"]*",'
    new_replacement = r'new Entity(\n                    \1,\n                    \3,\n                    \2,\n                    SourceList.US_OFAC,\n                    \1,'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} Entity constructor signatures (param order)")
    
    # 5. Fix Entity constructor parameter lists - need to expand from ~14 to 19 params
    # This is tricky - we need to find Entity constructors and fix their param lists
    # Pattern: after the 5 initial params, old has wrong structure
    # We need: person, business, org, aircraft, vessel, contact, addresses, cryptos, altNames, govIds, sanctions, historical, remarks, prepared
    
    # For PERSON entities: person goes in slot 6, rest are null/empty
    # Pattern: EntityType.PERSON, ... \n List.of(),\n List.of(),\n null... queryPerson,
    old_pattern = r'(EntityType\.PERSON,\s*\n\s*SourceList\.US_OFAC,\s*\n\s*"[^"]+",)\s*\n\s*(\w+),\s*null,\s*null,\s*null,\s*null,\s*\n\s*ContactInfo\.empty\(\),\s*List\.of\(\),\s*List\.of\(\),\s*List\.of\(\),\s*List\.of\(\),'
    new_replacement = r'\1\n                    \2, null, null, null, null,\n                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),\n                    null, List.of(), null, null'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} PERSON Entity parameter lists (19 params)")
    
    # For BUSINESS entities: business goes in slot 7
    old_pattern = r'(EntityType\.BUSINESS,\s*\n\s*SourceList\.US_OFAC,\s*\n\s*"[^"]+",)\s*\n\s*null,\s*(\w+),\s*null,\s*null,\s*null,\s*\n\s*ContactInfo\.empty\(\),\s*List\.of\(\),\s*List\.of\(\),\s*List\.of\(\),\s*List\.of\(\),'
    new_replacement = r'\1\n                    null, \2, null, null, null,\n                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),\n                    null, List.of(), null, null'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} BUSINESS Entity parameter lists (19 params)")
    
    # For AIRCRAFT entities: aircraft goes in slot 9
    old_pattern = r'(EntityType\.AIRCRAFT,\s*\n\s*SourceList\.US_OFAC,\s*\n\s*"[^"]+",)\s*\n\s*null,\s*null,\s*null,\s*(\w+),\s*null,\s*\n\s*ContactInfo\.empty\(\),\s*List\.of\(\),\s*List\.of\(\),\s*List\.of\(\),\s*List\.of\(\),'
    new_replacement = r'\1\n                    null, null, null, \2, null,\n                    ContactInfo.empty(), List.of(), List.of(), List.of(), List.of(),\n                    null, List.of(), null, null'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} AIRCRAFT Entity parameter lists (19 params)")
    
    # 6. Fix GovernmentId constructors - String to enum
    # Pattern: new GovernmentId("XXX", becomes new GovernmentId(GovernmentIdType.XXX,
    old_pattern = r'new GovernmentId\("(SSN|PASSPORT|TAXID|NATIONALID|DRIVERS_LICENSE)",'
    new_replacement = r'new GovernmentId(GovernmentIdType.\1,'
    content, count = re.subn(old_pattern, new_replacement, content)
    if count > 0:
        changes.append(f"Fixed {count} GovernmentId constructors (String to enum)")
    
    # 7. Fix IdMatchResult accessors (records use method accessors, not fields)
    content = content.replace('result.getScore()', 'result.score()')
    content = content.replace('result.isMatched()', 'result.matched()')
    content = content.replace('result.isExact()', 'result.exact()')
    content = content.replace('result.getWeight()', 'result.weight()')
    content = content.replace('result.getFieldsCompared()', 'result.fieldsCompared()')
    changes.append("Fixed IdMatchResult accessors (removed get/is prefixes)")
    
    # 8. Fix ContactFieldMatch accessors (records use method accessors)
    content = content.replace('.getScore()', '.score()')  # All cases
    changes.append("Fixed ContactFieldMatch accessors")
    
    # Write back
    with open(file_path, 'w') as f:
        f.write(content)
    
    # Report
    print("Phase 16 Test Fixes Applied:")
    for change in changes:
        print(f"  ✓ {change}")
    print(f"\nTotal lines: {len(content.splitlines())}")
    print("Run './mvnw test -Dtest=Phase16ZoneOneCompletionTest' to verify")

if __name__ == '__main__':
    fix_phase16_test()
