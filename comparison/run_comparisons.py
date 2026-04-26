import subprocess
import os
import wave
import math
import struct
import sys

# ChucK v1.5 Parity Test Suite
CK_FILES = [
    "01_filter_lpf.ck", "02_filter_resonz.ck", "03_stk_mandolin.ck",
    "04_stk_stifkarp.ck", "05_stk_modalbar.ck", "06_event_broadcast.ck",
    "07_shred_spork.ck", "08_envelope.ck", "09_filter_onepole.ck",
    "10_filter_twopole.ck", "11_filter_onezero.ck", "12_filter_twozero.ck",
    "13_stk_beethree.ck", "14_stk_moog.ck", "15_sndbuf.ck"
]

JAVA_JAR = "../chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar"

def run_command(cmd, label):
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            print(f"  [ERROR] {label}: {result.stderr}")
        return result
    except subprocess.TimeoutExpired:
        print(f"  [TIMEOUT] {label}")
        return None

def compare_wavs(wav1, wav2):
    if not os.path.exists(wav1) or not os.path.exists(wav2):
        return None, "File missing"
    
    try:
        with wave.open(wav1, 'rb') as w1, wave.open(wav2, 'rb') as w2:
            if w1.getnchannels() != w2.getnchannels() or w1.getsampwidth() != w2.getsampwidth():
                return None, "Format mismatch"
            
            n_frames = min(w1.getnframes(), w2.getnframes())
            if n_frames == 0: return 0.0, "Empty"
            
            frames1 = w1.readframes(n_frames)
            frames2 = w2.readframes(n_frames)
            
            count = n_frames * w1.getnchannels()
            data1 = struct.unpack(f"<{count}h", frames1)
            data2 = struct.unpack(f"<{count}h", frames2)
            
            # Phase alignment search (compensate for starting transients/delays)
            best_rms = 1.0
            search_range = 100
            for shift in range(-search_range, search_range, 1):
                sd = 0; sc = 0
                for i in range(max(0, -shift), min(count, count - shift)):
                    d = data1[i] - data2[i+shift]
                    sd += d*d; sc += 1
                if sc > 0:
                    rms = math.sqrt(sd/sc)/32768.0
                    if rms < best_rms: best_rms = rms
            
            return best_rms, f"Frames: {n_frames}"
    except Exception as e:
        return None, str(e)

if __name__ == "__main__":
    if not os.path.exists(JAVA_JAR):
        print(f"Error: Java JAR not found at {JAVA_JAR}. Run 'mvn clean install' first.")
        sys.exit(1)

    os.makedirs("native", exist_ok=True)
    os.makedirs("java", exist_ok=True)

    print("Step 1: Running comparisons (v1.5 Parity)...")
    for ck in CK_FILES:
        base = ck.replace(".ck", "")
        print(f"[{base}] ...", end="", flush=True)
        native_wav = f"native/{base}.wav"
        java_wav = f"java/{base}.wav"
        
        # Native Run
        run_command(["chuck", "--silent", f"{ck}:{native_wav}", "--halt"], "native")
        
        # Java Run
        run_command(["java", "--add-modules", "jdk.incubator.vector", "-jar", JAVA_JAR, "--silent", f"{ck}:{java_wav}", "--halt"], "java")
        print(" done.")

    print("\nStep 2: Results")
    print(f"{'Test Case':<25} | {'Best RMS Diff':<15} | {'Status'}")
    print("-" * 60)
    
    total_passed = 0
    for ck in CK_FILES:
        base = ck.replace(".ck", "")
        r, note = compare_wavs(f"native/{base}.wav", f"java/{base}.wav")
        
        status = "FAIL"
        if r is not None:
            if r == 0: status = "BIT-EXACT"
            elif r < 1e-6: status = "NEAR-EXACT"
            elif r < 0.05: status = "HIGH PARITY"
            elif r < 0.2: status = "ACCEPTABLE"
            else: status = "DIVERGED"
            
            if r < 0.2: total_passed += 1
        
        val_str = f"{r:<15.6e}" if r is not None else "FAILED"
        print(f"{base:<25} | {val_str} | {status}")

    print(f"\nFinal Score: {total_passed}/{len(CK_FILES)} cases within tolerance.")
