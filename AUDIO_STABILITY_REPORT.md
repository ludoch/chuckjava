# ChucK-Java Audio Stability Report (April 2026)

This document records the investigation, root cause analysis, and architectural fixes implemented to address high-frequency oscillations and noise in the ChucK-Java engine.

## 1. Incident Summary
**Symptoms**: 
- Starting the sequencer triggered an immediate "click" followed by a continuous high-frequency sound.
- Noise occurred even when no cells were selected (idle state).
- DAC logs showed alternating samples peaking at $\pm2.8$ (well beyond the $1.0$ full-scale range).

## 2. Root Cause Analysis
The issue was caused by three overlapping DSP failure modes:

### A. Denormal Limit Cycles
Internal IIR filters (`SVFilter`, `ShelfEQ`, `HPF`) were processing tiny residual values (denormals). In recursive feedback loops, these values can prevent the filter state from ever decaying to zero, causing permanent "ringing" or oscillation at the Nyquist frequency.

### B. Undamped Feedback Loops
The `Echo` UGen used in the global FX bus defaulted to a feedback coefficient (`gain`) of $1.0$. Any impulse (like a start-up gain jump) was captured in an undamped loop, effectively creating a permanent digital oscillator.

### C. Safety Chain Failure
While a `Dyno` limiter was present in the `engine.ck` script, it defaulted to a compression ratio of $1.0$. This meant it performed no gain reduction, allowing the $\pm2.8$ internal oscillations to reach the DAC and the speakers.

## 3. Diagnostic Infrastructure
To identify these issues, a **Source Attribution System** was added:
- **UGen Naming**: `ChuckUGen` now supports a `setName()` method accessible from ChucK scripts.
- **Dac Attribution**: `DacChannel` was updated to log the name and value of any individual source contributing more than $0.01$ to the final sum when `DEBUG_AUDIO` is active.
- **UI Toggle**: A **🐞 DEBUG** button was added to the JavaFX UI to toggle this tracing without restarting the engine.

## 4. Implemented Guardrails
The following architectural changes were made to prevent recurrence:

| Component | Fix | Purpose |
| :--- | :--- | :--- |
| **Global UGen** | **Anti-Denormal Flush** | The `tick()` loop now forces any value $< 10^{-15}$ to exactly $0.0$. |
| **IIR Filters** | **State Clamping** | `SVFilter` and `ShelfEQ` now hard-clamp internal integrators to $\pm2.0$. |
| **IIR Filters** | **Explicit `reset()`** | Added a method to clear internal buffers/integrators on engine start. |
| **Envelopes** | **`IDLE` Force-Mute** | `DelugeAdsr` now strictly returns $0.0$ when in the `IDLE` state. |
| **Dynamics** | **Limiter Defaults** | `Dyno` now defaults to a $10:1$ ratio when in `LIMITER` mode. |

## 5. Development Guidelines
1. **Always Name Master Branches**: Use `master.setName("NAME")` in scripts to ensure logs are actionable.
2. **Safety Chain Mandatory**: Every master output should end with `=> HPF hpf => Dyno limiter => dac;`.
3. **Impulse Awareness**: Assume any gain change will create an impulse. Filters must be stable enough to damp this impulse immediately.
