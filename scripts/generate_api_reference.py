#!/usr/bin/env python3
"""
Generate API Reference from compiled Java classes.

Extracts public methods, fields, and signatures from bytecode
to prevent AI hallucination during fix generation.
"""

import subprocess
import json
import re
from pathlib import Path
from typing import Dict, List


def find_class_files(target_dir: Path) -> List[Path]:
    """Find all compiled .class files."""
    return list(target_dir.rglob("*.class"))


def extract_class_api(class_file: Path) -> Dict:
    """Extract public API from a .class file using javap."""
    try:
        result = subprocess.run(
            ['javap', '-public', str(class_file)],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.returncode != 0:
            return None
        
        return parse_javap_output(result.stdout)
    except Exception as e:
        print(f"Warning: Could not process {class_file}: {e}")
        return None


def parse_javap_output(javap_output: str) -> Dict:
    """Parse javap output into structured API info."""
    lines = javap_output.strip().split('\n')
    
    # Extract class declaration
    class_line = None
    for line in lines:
        if 'class ' in line or 'interface ' in line or 'record ' in line:
            class_line = line.strip()
            break
    
    if not class_line:
        return None
    
    # Extract class name
    class_name_match = re.search(r'(?:class|interface|record)\s+(\S+)', class_line)
    if not class_name_match:
        return None
    
    class_name = class_name_match.group(1)
    
    # Determine type
    if 'interface ' in class_line:
        class_type = 'interface'
    elif 'record ' in class_line:
        class_type = 'record'
    else:
        class_type = 'class'
    
    # Extract methods and fields
    methods = []
    fields = []
    
    for line in lines:
        line = line.strip()
        
        # Skip class declaration and empty lines
        if not line or 'class ' in line or 'interface ' in line or 'record ' in line:
            continue
        
        # Skip inner class declarations
        if line.startswith('public static class') or line.startswith('public static interface'):
            continue
        
        # Method signature (has parentheses)
        if '(' in line and ')' in line:
            # Clean up the signature
            signature = re.sub(r'\s+', ' ', line)
            signature = signature.rstrip(';').strip()
            methods.append(signature)
        
        # Field declaration (no parentheses, ends with semicolon or has =)
        elif ';' in line or '=' in line:
            field = re.sub(r'\s+', ' ', line)
            field = field.rstrip(';').strip()
            if field:
                fields.append(field)
    
    return {
        'name': class_name,
        'type': class_type,
        'methods': methods,
        'fields': fields
    }


def generate_api_reference(target_dir: Path) -> Dict:
    """Generate complete API reference from compiled classes."""
    class_files = find_class_files(target_dir)
    print(f"Found {len(class_files)} class files")
    
    api_reference = {}
    
    for class_file in class_files:
        # Get package name from path
        relative_path = class_file.relative_to(target_dir)
        package_path = str(relative_path.parent).replace('/', '.')
        
        # Skip inner classes and test classes
        if '$' in class_file.name or 'Test' in class_file.name:
            continue
        
        api_info = extract_class_api(class_file)
        
        if api_info:
            full_class_name = f"{package_path}.{api_info['name']}"
            api_reference[full_class_name] = api_info
    
    return api_reference


def format_as_markdown(api_reference: Dict) -> str:
    """Format API reference as markdown for AI consumption."""
    lines = [
        "# Java API Reference",
        "",
        "**Auto-generated from compiled bytecode**",
        "**USE ONLY METHODS/CLASSES LISTED BELOW**",
        ""
    ]
    
    # Group by package
    packages = {}
    for full_name, info in sorted(api_reference.items()):
        package = '.'.join(full_name.split('.')[:-1])
        if package not in packages:
            packages[package] = []
        packages[package].append((full_name, info))
    
    for package, classes in sorted(packages.items()):
        lines.append(f"## {package}")
        lines.append("")
        
        for full_name, info in classes:
            class_name = full_name.split('.')[-1]
            lines.append(f"### {class_name} ({info['type']})")
            
            if info['fields']:
                lines.append("**Fields:**")
                for field in info['fields']:
                    lines.append(f"- `{field}`")
                lines.append("")
            
            if info['methods']:
                lines.append("**Methods:**")
                for method in info['methods']:
                    lines.append(f"- `{method}`")
                lines.append("")
            
            lines.append("")
    
    return '\n'.join(lines)


def main():
    """Generate API reference."""
    print("=" * 80)
    print("GENERATING API REFERENCE")
    print("=" * 80)
    
    target_dir = Path('target/classes')
    
    if not target_dir.exists():
        print("❌ target/classes not found. Run 'mvn compile' first.")
        return 1
    
    # Generate reference
    api_reference = generate_api_reference(target_dir)
    print(f"Extracted API from {len(api_reference)} classes")
    
    # Save as JSON
    json_output = Path('target/api-reference.json')
    with open(json_output, 'w') as f:
        json.dump(api_reference, f, indent=2)
    print(f"✓ Saved JSON: {json_output}")
    
    # Save as Markdown
    markdown_output = Path('target/API-REFERENCE.md')
    markdown = format_as_markdown(api_reference)
    with open(markdown_output, 'w') as f:
        f.write(markdown)
    print(f"✓ Saved Markdown: {markdown_output}")
    
    print("\n" + "=" * 80)
    print("API REFERENCE GENERATION COMPLETE")
    print("=" * 80)
    
    return 0


if __name__ == '__main__':
    import sys
    sys.exit(main())
