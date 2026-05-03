# Pure Java Deluge Sequencer Feasibility Study

This document evaluates the feasibility of implementing a "Pure Java" Deluge sequencer mode, bypassing the ChucK engine entirely for sequence logic while retaining our existing Java UI.

## 1. Architectural Comparison: ChucK vs. Pure Java

| Feature | Current ChucK Architecture | Proposed Pure Java Mode |
| :--- | :--- | :--- |
| **Logic Language** | ChucK DSL (`DelugeEngineDSL.java`) | Pure Java 25 |
| **Timing Model** | Strongly-timed `now => time` | High-priority Java Event Loop / `ScheduledExecutorService` |
| **Data Sync** | `BridgeContract` (Global Variables) | Direct Object Reference / Shared Memory |
| **DSP Engine** | ChucK VM | Ported Csound UGens / Manual Java Audio Thread |
| **Flexibility** | High (easy to spork new logic) | Moderate (requires rigid scheduling architecture) |
| **Performance** | JNI/VM Overhead | Zero VM overhead, direct SIMD (Vector API) |

---

## 2. Key Porting Targets from DelugeFirmware

Analysis of the `../DelugeFirmware/src` repository identifies the following critical logic for porting:

*   **`PlaybackHandler` (`playback/playback_handler.cpp`):** 
    *   *The "Heart":* Manages the master clock, swing, transport state (play/stop), and external MIDI clock sync.
    *   *Java Port:* A dedicated `SequencerClock` class using `nanoTime()` and high-resolution sleeps.
*   **`Clip` and `InstrumentClip` (`model/clip/`):**
    *   *Data Structure:* Handles the mapping of notes to time positions, including iteration dependence and probability.
    *   *Java Port:* Enhance `ClipModel.java` to handle its own playback cursor logic.
*   **`NoteRow` and `Note` (`model/note/`):**
    *   *Storage:* The efficient storage and lookup of note events (velocity, length, probability).
    *   *Java Port:* Optimize `StepData.java` for O(1) or O(log N) lookup during real-time playback.

---

## 3. Pure Java Implementation Strategy

To implement this as a pure Java option, we should follow this architectural plan:

### 1. The High-Resolution Scheduler
We cannot rely on standard `Thread.sleep()` for audio-accurate timing.
*   **Approach:** Use a dedicated Virtual Thread (Project Loom) pinned to a carrier thread, or a high-priority system thread.
*   **Mechanism:** A "Look-ahead" scheduler that buffers events 5-10ms in advance and sends them to the audio buffer.

### 2. Audio Thread Integration
Since we won't use the ChucK VM, we need a native-style audio callback in Java.
*   **Technology:** Use **FFM API** to bind directly to `RtAudio` or `PortAudio` (as seen in `rtmidijava`'s approach).
*   **Processing:** Implement a `JavaAudioEngine` that calls `tick()` on a graph of ported Csound UGens entirely in Java.

### 3. Shared Object Model
*   **Eliminate Bridge:** The `BridgeContract` is a bottleneck. In Pure Java mode, the UI (`SwingGridPanel`) and the Sequencer share the same `ProjectModel` instance.
*   **Thread Safety:** Use `AtomicReference` or `Concurrent` collections for UI-to-Engine parameter updates (e.g., Cutoff, Resonance).

---

## 4. Feasibility Conclusion

**Feasibility: High.** 

With **JDK 25**, the previous barriers to a pure Java audio engine (GC jitter, lack of SIMD, poor timing) are largely resolved by **Project Loom**, the **Vector API**, and **FFM API**.

### Why do this?
1.  **Lower Latency:** Eliminate the ChucK VM's interpretation layer and JNI bridge.
2.  **Developer Experience:** Allow Java developers to debug the entire sequencer using standard IDE debuggers without a VM boundary.
3.  **Portability:** A pure Java engine is easier to package as a standalone desktop app or even a WASM-based web app.

### Recommendation
Implement this as an **"Experimental Pure-Java Mode"** selectable in the `PreferencesTab`.
*   Keep the UI exactly as is.
*   Abstract the `SequencerEngine` interface so it can point to either `DelugeEngineDSL` (ChucK) or `NativeJavaSequencer`.
