#!/usr/bin/env python3
"""Fix indentation in handler.py"""

# Read the backup file
with open('handler.py.bak', 'r') as f:
    lines = f.readlines()

# Track which lines need fixing (all content inside "for X in page:" loops)
fixed_lines = []
inside_page_loop = False
loop_indent = ""

for i, line in enumerate(lines):
    # Detect start of "for X in page:" loop
    if 'for individual in page:' in line or 'for business in page:' in line or 'for counterparty in page:' in line:
        inside_page_loop = True
        loop_indent = len(line) - len(line.lstrip())
        fixed_lines.append(line)
        continue
    
    # If inside page loop, add 4 spaces to indentation
    if inside_page_loop:
        current_indent = len(line) - len(line.lstrip())
        # Exit loop when we hit break outer loop or next major section
        if line.strip().startswith('# Break outer loop') or line.strip().startswith('# Write remaining') or (current_indent <= loop_indent and line.strip() and not line.strip().startswith('#')):
            inside_page_loop = False
            fixed_lines.append(line)
        else:
            # Add 4 spaces to current indentation for loop body
            if line.strip():  # Non-empty line
                fixed_lines.append('    ' + line)
            else:
                fixed_lines.append(line)  # Keep empty lines as-is
    else:
        fixed_lines.append(line)

# Write fixed version
with open('handler.py', 'w') as f:
    f.writelines(fixed_lines)

print("✅ Fixed indentation")
