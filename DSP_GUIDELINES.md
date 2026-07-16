# Digital Signal Processing (DSP) Architecture Guidelines

This document serves as a reference for the core DSP architectural decisions in the `chuck-core` engine, specifically regarding numerical precision, floating-point math, and common audio programming pitfalls.

## The Hybrid Precision Architecture

In `chuck-core`, we employ a **hybrid precision architecture**:

1. **The Transport Layer (32-bit `float`)**: 
   All audio buffers (`float[]`), I/O, and the `ChuckUGen.tick()` pipeline use 32-bit single-precision floats. This ensures that the JDK 27 Vector API (SIMD) can process the maximum number of samples per CPU instruction (typically 8 floats per 256-bit vector register), keeping the engine blazing fast and memory bandwidth low.
   
2. **The "Danger Zone" Internal State (64-bit `double`)**: 
   Inside specific UGens that use feedback, recursion, or accumulation, the *internal private variables* and delay lines must use 64-bit double-precision math.

### Why `double` is Mandatory for Internal State

If a recursive algorithm uses 32-bit `float` for its internal state, two severe audio bugs will inevitably occur:

#### 1. Denormal (Subnormal) CPU Spikes
When an audio tail (like an exponential envelope or a reverb) decays toward absolute zero, the numbers become microscopic (e.g., `1.0 × 10^-38`). At this scale, the CPU switches into a special, incredibly slow hardware mode to calculate these "denormal" numbers. In older algorithms, this would instantly max out the CPU and crash the audio thread. 
* **The Fix**: Calculating the decay in 64-bit `double` provides enough precision to cleanly cross the denormal threshold and round down to a true `0.0` without triggering the CPU penalty.

#### 2. IIR Filter Limit Cycles (Quantization Noise)
When an Infinite Impulse Response (IIR) filter (like a Biquad, ShelfEQ, or SVFilter) has a very low cutoff frequency relative to the sample rate, the math requires extremely high precision. With only 24 bits of mantissa in a `float`, the filter cannot accurately represent the tiny fractional changes between samples. Instead of smoothly decaying to silence, the rounding errors compound in a loop, creating a self-sustaining, continuous, high-pitched whining noise (a limit cycle).
* **The Fix**: Upgrading the filter's delay memory (`ic1eq`, `lx1`, `ly1`, etc.) to 64-bit `double` completely eradicates the limit cycle, allowing the filter to mathematically rest at absolute zero.

#### 3. Phase Accumulator Drift
In oscillators (`SineWave`, `Phasor`, `MorphingWavetable`), a tiny `phaseIncrement` is added to a running `phase` total thousands of times a second. If `phase` is a 32-bit `float`, as the total grows larger, it loses the precision required to accurately add the tiny increment. This causes the oscillator's pitch to physically drift flat over time.
* **The Fix**: The `phase` variable must always be a 64-bit `double`.

---

## Industry "Gold Standard" References

If you want to dive deeper into the mathematics and theory behind these audio programming phenomena, these are the foundational texts and resources used by the DSP industry:

### 1. "The Art of VA Filter Design" by Vadim Zavalishin (Native Instruments)
The modern bible for writing analog-sounding filters (Zero-Delay Feedback / ZDF). He extensively details why 64-bit `double` is required to prevent limit cycles when modeling analog circuitry at high sample rates.
* **Link**: [The Art of VA Filter Design (Free PDF)](https://www.native-instruments.com/fileadmin/ni_media/downloads/pdf/VAFilterDesign_2.1.2b.pdf)

### 2. Julius O. Smith's DSP Books (CCRMA at Stanford)
Julius O. Smith (a pioneer at Stanford's CCRMA, where ChucK was originally created) publishes his legendary DSP textbooks for free online. His books cover the rigorous math behind delay lines, comb filters, and quantization noise.
* **Link**: [Physical Audio Signal Processing](https://ccrma.stanford.edu/~jos/pasp/)
* **Link**: [Introduction to Digital Filters](https://ccrma.stanford.edu/~jos/filters/)

### 3. EarLevel Engineering (Nigel Redmon)
Nigel Redmon’s blog is phenomenal for explaining complex DSP concepts (like Biquad coefficients, envelope curves, and wavetables) in plain, readable English without burying you in calculus.
* **Link**: [EarLevel Engineering - Biquads](https://www.earlevel.com/main/category/digital-audio/biquads/)

### 4. The Musicdsp.org Archives
A legendary, decades-old mailing list and code repository where plugin developers share C++ snippets and discuss edge-case bugs. Searching "denormal" here will reveal 20 years of programmers fighting the exact issues outlined above.
* **Link**: [Musicdsp.org Source Code Archive](https://www.musicdsp.org/en/latest/)

---

*Note: In embedded hardware (like embedded DSP hardware or Eurorack modules), the processors often lack 64-bit FPUs. Hardware engineers are forced to use 32-bit floats and must write hacky "dithering" code (injecting microscopic white noise into the signal) to manually disrupt the limit cycles and denormals. Because desktop CPUs process 64-bit math with zero performance penalty, we bypass those hacks entirely and simply use `double`.*
