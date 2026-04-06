import os
import subprocess

test_dir = "src/test/01-Basic"
files = sorted([f for f in os.listdir(test_dir) if f.endswith(".ck")])

passed = 0
failed = []
skipped = 0

for f in files:
    prefix = f.split("-")[0].split(".")[0]
    try:
        num = int(prefix)
    except ValueError:
        continue
    
    if num > 250:
        skipped += 1
        continue
    
    cmd = ["mvn", "test", "-Dtest=ChucKIntegrationTest", f"-DspecificTestFile=01-Basic/{f}"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    if result.returncode == 0:
        passed += 1
        print(f".", end="", flush=True)
    else:
        failed.append(f)
        print(f"F", end="", flush=True)

print(f"\nResults for 01-Basic (up to 250):")
print(f"Passed: {passed}")
print(f"Failed: {len(failed)}")
print(f"Skipped (above 250): {skipped}")

if failed:
    print("\nFailures:")
    for f in failed:
        print(f"  - {f}")
