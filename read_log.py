import sys

with open('compile_log.txt', 'r', encoding='utf-16le', errors='ignore') as f:
    lines = f.readlines()

print("ALL ERRORS:")
for line in lines:
    if 'ERROR' in line or 'Exception' in line:
        print(line.strip())

print("\nLAST 30 LINES:")
for line in lines[-30:]:
    print(line.strip())
