# ChucK-Java Csound Reference Guide

This guide provides technical reference for the ported Csound opcodes in ChucK-Java. Each entry follows a standardized template derived from the original Csound manual, adapted for ChucK's UGen architecture.

---

## Opcode Documentation Template

Every ported UGen should document the following:

1.  **Description:** A clear explanation of the mathematical/physical model.
2.  **ChucK Syntax:** How to connect and parameterize the UGen.
3.  **Parameters:** Detail each `.parameter` with its range and default value.
4.  **JDK 25 Optimization:** Notes on whether it uses Vector API or Virtual Threads.
5.  **Technical Note:** Specific implementation details (e.g., "Uses Zero-Delay Feedback").
6.  **Usage Example:** A concise code snippet.

---

## Ported Opcodes Reference

### `ReverbSC`
**Description:** 
A high-quality 8-delay line stereo feedback delay network reverb, originally by Sean Costello. It provides a dense, lush tail suitable for professional production.

**ChucK Syntax:**
```chuck
adc => ReverbSC rev => dac;
```

**Parameters:**
*   `.feedback` (float): Range [0, 1]. Controls the decay time. Default: `0.5`.
*   `.lpFreq` (float): Range [20, 20000]. Low-pass filter frequency in the feedback loop. Default: `10000`.

**JDK 25 Optimization:**
*   Uses the **Vector API** to process the 8 delay lines in parallel (SIMD), significantly reducing CPU overhead compared to the C original on modern processors.

**Usage Example:**
```chuck
adc => ReverbSC rev => dac;
0.8 => rev.feedback; // Long ambient tail
```

---

### `Gendy`
**Description:** 
Dynamic Stochastic Synthesis oscillator based on Iannis Xenakis' algorithm. It creates waveforms by randomly varying the time and amplitude of a set of breakpoints.

**ChucK Syntax:**
```chuck
Gendy gen => dac;
```

**Parameters:**
*   `.ampDist` (int): Amplitude distribution type (0=Linear, 1=Cauchy, etc.).
*   `.durDist` (int): Duration distribution type.
*   `.numPoints` (int): Number of breakpoints. Default: `12`.

**JDK 25 Optimization:**
*   Leverages **Project Loom** when running multiple `Gendy` instances in a dense cloud, allowing the stochastic modulation to run asynchronously if requested.

---

### `PVSAnal` (Phase Vocoder Analysis)
**Description:** 
Standard FFT-based spectral analysis. Converts time-domain signals into a streaming spectral format (PVS).

**ChucK Syntax:**
```chuck
adc => PVSAnal anal => ...
```

**Parameters:**
*   `.fftsize` (int): Power of two (256, 512, 1024, etc.).
*   `.overlap` (int): Hop size.
*   `.freeze` (int): 0 or 1. Stops the analysis update, "freezing" the current spectrum.

**JDK 25 Optimization:**
*   **Off-Heap Memory:** Large FFT buffers are allocated using the **FFM API** to minimize GC pressure.
*   **Vector API:** FFT kernels are vectorized for high-throughput analysis.

---

## Reusable Csound Documentation Patterns

When iterating on these docs, we should reuse the following "wisdom" from the original Csound manual:

1.  **Mathematical Clarity:** Csound docs always specify the exact formula for the transfer function. We should keep this in the `Technical Note` section.
2.  **Range Constraints:** Clearly mark parameters that can cause instability if exceeded (e.g., feedback > 1.0).
3.  **Historical Context:** If an algorithm is a classic (e.g., Moog, Chowning FM), mention its origin.

---

## Future Iterations
*   [ ] Add `Partikkel` parameter map (40+ params).
*   [ ] Add `ScannedSynth` mesh configuration guide.
*   [ ] Integrate interactive diagrams using Mermaid.js for spectral signal flow.
