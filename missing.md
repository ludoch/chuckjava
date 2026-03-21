# ChucK-Java: Missing Features & Gaps

This document tracks the features from the original [Stanford ChucK](https://chuck.stanford.edu/) implementation that are currently missing, incomplete, or divergent in this JDK 25 Java port.

## 1. Core Language & VM Architecture

| Feature | Status | Description |
|:---|:---|:---|
| **Shred References** | Done | `spork ~` now returns a `Shred` object reference. Supports `.id()`, `.exit()`, and `.done()`. |
| **Event Conjunction** | Done | `e1 && e2 => now` is implemented. Shred waits until all events trigger since the wait began. |
| **Event Disjunction** | Done | `e1 || e2 => now` is implemented. Shred waits until any event triggers. |
| **Chugins System** | Done | Dynamic plugin architecture implemented via `ChuginLoader`. Loads `.jar` files from the `chugins/` directory. |
| **Machine Control** | Done | `Machine.add()`, `Machine.remove()`, `Machine.replace()`, `Machine.status()`, `Machine.eval()`, and `Machine.clear()` are fully implemented. |
| **me.dir(n)** | Done | Multi-level directory access implemented in `ChuckShred` and `ChuckEmitter`. |
| **Duration Arithmetic** | Done | Fixed `dur * float`, `dur / float`, and `dur / dur` operations to work correctly with sub-sample precision (double). |
| **Vectorized Audio** | Done | SIMD-accelerated mixing and oscillators using Java Vector API. |
| **Off-heap Audio** | Done | Off-heap DAC buffers via Project Panama (FFM) to eliminate GC jitter. |

## 2. Unit Generators (UGens)

| UGen | Status | Description |
|:---|:---|:---|
| **Dyno** | Done | Fully implemented with Envelope Follower and Gain Computer supporting Compressor, Limiter, Expander, and Gate modes. |
| **Chugraph** | Done | Implemented as a base class with `inlet` and `outlet` support. |
| **ChuGen** | Done | Implemented with synchronous `tick()` logic execution within the audio thread. |
| **WvIn / WaveLoop** | Done | Fully implemented with support for loading and looping WAV/AIFF files. |
| **WvOut** | Done | Implemented as `WvOut` UGen for recording audio to WAV files from within ChucK. |
| **NRev / PRCRev** | Done | Classic STK reverbs implemented. |
| **GVerb** | Done | High-quality studio reverb implemented using a multi-delay network. |
| **PitShift** | Done | Functional time-domain pitch shifter implemented using dual cross-fading delay lines. |
| **Gen9 / Gen17** | Done | Implemented sum-of-sinusoids (`Gen9`) and Chebyshev polynomials (`Gen17`) for table synthesis and wavefolding. |
| **Mix2** | Done | 2-channel stereo mixer implemented. |
| **HalfRect / FullRect** | Done | Signal rectification UGens implemented. |
| **ZeroX** | Done | Zero-crossing detector implemented. |
| **SubNoise** | Done | Sub-sampled noise generator implemented. |
| **Modulate** | Done | Vibrato/Tremolo modulation UGen implemented. |
| **WarpTable / CurveTable** | Done | Advanced lookup table UGens implemented. |

## 3. I/O & Networking

| Feature | Status | Description |
|:---|:---|:---|
| **FileIO** | Done | Fully implemented ASCII and Binary modes, including support for the `<=` streaming operator. |
| **MidiOut** | Done | Implemented with support for sending MIDI messages using `javax.sound.midi`. |
| **MidiIn** | Done | Implemented with `RtMidi` binding via FFM API. Full support for `recv(MidiMsg)` and `Event` waiting (`min => now`). |
| **SerialIO** | Done | Functional stub implemented for debugging. |
| **OscBundle** | Done | Implemented support for grouping and sending OSC messages. |

## 4. Standard Libraries

| Library | Status | Description |
|:---|:---|:---|
| **Std Methods** | Done | Implemented `atoi`, `atof`, `rand2`, `rand2f`, `fabs`, `srand`, `systemTime`, `getenv`, `setenv`, and `range`. |
| **RegEx** | Done | Pattern matching using `java.util.regex`. |
| **Reflect** | Done | Shred and object introspection implemented. |
| **UAna (Flux/Rolloff)** | Done | Spectral feature extractors implemented. |

## 5. IDE & UI

| Feature | Status | Description |
|:---|:---|:---|
| **Visualizer Config** | Done | Added UI controls to adjust FFT size and oscilloscope window size. |
| **Project View** | Done | Added file system management (new file, delete, refresh) and project directory support. |
