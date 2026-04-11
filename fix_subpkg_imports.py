import os

core_classes = ['ChuckAudio', 'ChuckUGen', 'ChuGen', 'Chugraph', 'UAna', 'UAnaBlob', 'VectorAudio']
subpkgs = ['osc', 'filter', 'fx', 'stk', 'analysis', 'util']

base_dir = 'src/main/java/org/chuck/audio'

for subpkg in subpkgs:
    pkg_dir = os.path.join(base_dir, subpkg)
    if not os.path.exists(pkg_dir):
        continue
        
    for filename in os.listdir(pkg_dir):
        if filename.endswith('.java'):
            file_path = os.path.join(pkg_dir, filename)
            with open(file_path, 'r') as f:
                lines = f.readlines()
            
            content = "".join(lines)
            new_lines = []
            package_line_index = -1
            
            for i, line in enumerate(lines):
                if line.startswith('package '):
                    package_line_index = i
                    break
            
            needed_imports = []
            for core_class in core_classes:
                # Simple check if the core class is used in the file
                # We look for the class name as a word
                import re
                if re.search(r'\b' + core_class + r'\b', content):
                    # Check if it's already imported (unlikely but safe)
                    if f'import org.chuck.audio.{core_class};' not in content:
                        needed_imports.append(f'import org.chuck.audio.{core_class};')
            
            if needed_imports:
                # Insert imports after package declaration
                new_lines = lines[:package_line_index+1]
                new_lines.append('\n')
                for imp in needed_imports:
                    new_lines.append(imp + '\n')
                new_lines.extend(lines[package_line_index+1:])
                
                with open(file_path, 'w') as f:
                    f.writelines(new_lines)
                print(f"Added core imports to {file_path}")
