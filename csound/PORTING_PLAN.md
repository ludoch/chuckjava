# Csound to ChucK-Java Porting Plan

This document outlines the strategic roadmap for porting high-value algorithms and features from the Csound C repository to the ChucK-Java and Deluge-Java ecosystem.

## 1. Architectural Strategy

We will leverage **JDK 25** advanced features to ensure the Java port is as efficient as (or more efficient than) the original C implementation, particularly focusing on parallelism and SIMD.

### JDK 25 Integration Points
*   **Project Loom (Virtual Threads):** 
    *   *Where:* Used in **Granular Synthesis (`partikkel`)** and **Asynchronous Disk I/O (`diskin2`)**.
    *   *Why:* Each grain in a high-density cloud can be managed by a virtual thread if it involves complex parameter modulation, or more likely, we use virtual threads to handle the orchestration of grain pools without blocking the main audio thread.
*   **Vector API (`jdk.incubator.vector`):** 
    *   *Where:* **FFT/IFFT kernels**, **Spectral Processing (PVS)**, and **Multi-tap Delay lines**.
    *   *Why:* To achieve C-like performance for bin-by-bin multiplications in the spectral domain and parallel filter coefficient calculations.
*   **Foreign Function & Memory API (FFM):**
    *   *Where:* Large **Spectral Buffers** and **Delay Lines**.
    *   *Why:* To allocate memory outside the JVM heap, avoiding GC pauses for multi-gigabyte sample buffers or long delay lines used in physical modeling.

---

## 2. Architectural Strategy: The "Pure Java" Native Path

A critical decision for the ChucK-Java port is the move away from traditional JNI (Java Native Interface) in favor of the **JDK 25 Foreign Function & Memory (FFM) API**.

### Native Interop Strategy
As demonstrated in our sibling project `rtmidijava`, we can achieve high-performance interaction with native system libraries (like `winmm.dll`, `libasound.so`, or `CoreMIDI`) without writing a single line of C/C++ wrapper code.

*   **JNI vs. FFM:** We will **NOT** use JNI or the latest JNI support in JDK 25 for new porting efforts. JNI adds significant complexity to the build system and introduces "brittle" boundaries.
*   **The FFM Advantage:**
    *   **In-Java Declarations:** We define native struct layouts (like `MIDIHDR` or Csound's `PVSDAT`) and function signatures (using `Linker` and `SymbolLookup`) directly in Java code.
    *   **Performance:** FFM is designed to be as fast as JNI while providing better safety and off-heap memory management via `Arena`.
    *   **Zero-Overhead Bindings:** We can bind to Csound's internal C functions or system audio APIs directly from `chuck-core`.

---

## 3. Feature Mapping & Implementation Sites

| Feature Class | Csound Source | ChucK-Java Implementation Site | Target Use Case |
| :--- | :--- | :--- | :--- |
| **Spectral (PVS)** | `Opcodes/pvs*.c` | `chuck-core/src/main/java/org/chuck/core/spectral/` | Advanced sound design, time-stretching |
| **Reverb SC** | `Opcodes/reverbsc.c` | `chuck-core/src/main/java/org/chuck/ugens/ReverbSC.java` | Global master effect for Deluge & ChucK |
| **Partikkel** | `Opcodes/partikkel.c` | `chuck-core/src/main/java/org/chuck/ugens/Partikkel.java` | Professional granular synthesis |
| **Scanned Synth** | `Opcodes/scansyn.c` | `chuck-core/src/main/java/org/chuck/ugens/ScannedSynth.java` | Physical modeling synthesis |
| **Moog Ladder** | `Opcodes/newfils.c` | `chuck-core/src/main/java/org/chuck/ugens/MoogLadder.java` | VA Synth engine in Deluge |
| **Gendy** | `Opcodes/gendy.c` | `chuck-core/src/main/java/org/chuck/ugens/Gendy.java` | Algorithmic/Noise synthesis |

---

## 3. User Experience (UX) & UI Integration

Ported features should not only sound good but be easy to control and visualize.

### ChucK IDE Enhancements (`chuck-ide`)
*   **Spectral Monitor Panel:** A new panel in the IDE to visualize the "PVS Bus" content (magnitudes/phases) in real-time. This helps users understand what `pvsblur` or `pvscale` is actually doing to the spectrum.
*   **UGen Browser Integration:** Add a "Csound Heritage" category to the `UGenBrowser` with auto-generated documentation for the ported opcodes.
*   **Live Parameter Control:** Integration with `ControlSurface.java` to allow mapping external MIDI controllers to `ReverbSC` or `Partikkel` parameters with zero configuration.

### Deluge Java Emulator Enhancements (`deluge`)
*   **Advanced FX Tab:** In `SwingMasterFxPanel.java`, add a toggle to switch between "Classic Deluge Reverb" and "Csound ReverbSC".
*   **Synth Engine Visualizer:** Enhance `SwingVisualizerPanel.java` to show the excitation state of a `ScannedSynth` oscillator.
*   **New Modal Dialogs:** Create a `PartikkelConfigDialog.java` to handle the 40+ parameters of the `partikkel` opcode, which are too many for a standard sidebar.

---

## 4. Extensive Testing Strategy (Maven Native)

To ensure the Java port is faithful to the C original and stable under load, we use a pure Java testing architecture integrated directly into the Maven lifecycle. **No Python scripts are used for validation.**

### Tier 1: Bit-Accuracy Validation (`mvn verify`)
*   **The `.csd` Oracle:** For every ported UGen, we maintain a corresponding `.csd` file in `src/test/resources/csound/`.
*   **Java Comparison Engine:** We use a specialized JUnit 5 extension `CsoundBitAccuracyExtension` that:
    1.  Spawns a native `csound` process (via `ProcessBuilder`) to generate a reference `.wav`.
    2.  Executes the ChucK-Java UGen within a headless `ChuckVM` instance (as seen in `TutorialsTest.java`).
    3.  Performs an in-memory sample-by-sample comparison.
*   **Automated Metrics:**
    *   *RMS Error:* Measured in Java using `double[]` arrays.
    *   *Tolerance:* Error must be < -90dB for deterministic filters.

### Tier 2: Performance & Vectorization (`mvn test -Pbenchmark`)
*   **JMH Suite:** Using the Maven JMH plugin to track the efficiency of `jdk.incubator.vector` code paths.
*   **Stress Testing:** A "Shred Bomb" test where 1000+ instances of `Partikkel` are spawned using Java's **Virtual Threads**, ensuring the scheduler handles high-concurrency audio without jitter.

### Tier 3: Memory & Lifecycle (`mvn test -Paudit`)
*   **FFM Leak Detection:** Utilizing a custom `MemoryArenaTracker` to ensure all `Arena` allocations from FFM are correctly closed.
*   **GC Pressure Analysis:** Integrated with Maven to fail the build if GC pauses exceed 1ms during a 30-second stress test (using JFR).

---

## 5. Integration with Maven Lifecycle

| Goal | Maven Command | Purpose |
| :--- | :--- | :--- |
| **Unit Tests** | `mvn test` | Rapid validation of logic and mathematical transforms. |
| **Integration** | `mvn verify` | Bit-accuracy checks against native Csound binaries. |
| **Formatting** | `mvn spotless:apply` | Enforce code style consistency before porting. |
| **Native Image** | `mvn -Pnative package` | Verify GraalVM compatibility for the ported opcodes. |

---

## 5. New User Guide: "Getting Started with Csound Features"

*Welcome! You've just opened ChucK-Java. Here is how you use the new Csound power.*

### Step 1: Your First High-Quality Reverb
Instead of the standard `NRev`, use `ReverbSC`. It's lusher and more professional.
```chuck
adc => ReverbSC rev => dac;
0.85 => rev.feedback; // Long tail
5000 => rev.lpFreq;   // Dampen high frequencies
```

### Step 2: Exploring the Spectral World
To freeze a sound in time:
```chuck
// 1. Analyze input
adc => PVSAnal anal => PVSynth synth => dac;
// 2. Set freeze to 1
1 => anal.freeze; 
// The sound currently in the buffer will loop infinitely without pitch change!
```

### Step 3: Troubleshooting
*   **"I don't see the Spectral Panel":** Go to `View -> Panels -> Spectral Monitor`.
*   **"Audio is crackling":** Check the `Status Bar`. If CPU is high, try increasing the `Vector API` buffer size in `Preferences`.

---

## 6. Detailed Porting Roadmap

### Phase 1: The "Gold Standards"
*   **ReverbSC:** Port the Costello reverb. High-impact UI: `SwingMasterFxPanel`.
*   **MoogLadder:** Implement the zero-delay feedback version.

### Phase 2: Spectral Foundation
*   Implement `PVSBuffer` and `PVSBus` in the ChucK core.
*   Port `pvsanal`, `pvsynth`.

### Phase 3: Complex Synthesis & UX
*   **Partikkel:** Implement the `PartikkelConfigDialog` in the Deluge UI.
*   **Scanned Synthesis:** Implement the Visualizer update.
