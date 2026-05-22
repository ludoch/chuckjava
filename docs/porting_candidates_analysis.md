# ChucK-Java: Deluge Firmware Porting Candidates Analysis

This document details the top remaining high-impact DSP and synthesizer systems in the original C++ Deluge Firmware (`../DelugeFirmware`) that are currently unported or only partially implemented in our high-fidelity Java engine.

---

## 🎹 Candidate Systems Overview

We have categorized the top 4 candidates by their musical impact, complexity, and architectural fit:

| System Name | C++ Target Location | Java Target Location | Complexity | Musical Impact | Key Benefits |
|-------------|---------------------|----------------------|------------|----------------|--------------|
| **1. Full Stereo Master Compressor** | `dsp/compressor/rms_feedback.cpp` | `dsp/effects/MasterCompressor.java` | 🟡 Medium | 🔴 High | Professional master dynamics, parallel blend compression, track sidechaining. |
| **2. Extended Moog & SVF Filter Modes** | `dsp/filter/lpladder.cpp`, `svf.cpp` | `dsp/filter/LpLadderFilter.java`, `SVFilter.java` | 🟡 Medium | 🔴 High | 2-pole/4-pole Moog models, highpass/notch SVF drive saturation, morphing. |
| **3. Modulation Matrix Source Expansion** | `model/voice/voice.cpp`, `patch.cpp` | `engine/FirmwareVoice.java`, `modulation/` | 🟢 Low | 🟡 Medium | Support for Envelopes 3-4, Local LFO 2, Global LFO 2, and physical X/Y axes. |
| **4. Cubic Mod-FX Resonance Curves** | `dsp/fx/chorus.cpp`, `phaser.cpp` | `dsp/fx/Chorus.java`, `Phaser.java` | 🟢 Low | 🟢 Low | Stable, non-linear feedback loops with 32-bit cubic curves for Chorus/Phaser. |

---

### 1. 🎛️ Full Stereo Master Compressor (`RMSFeedbackCompressor`)
The original Deluge firmware features a premium stereo master compressor utility in the DSP pipeline:
- **RMS Level Detection**: Calculates real-time root-mean-square energy profiles over multiple sliding windows instead of simple naive peak tracking.
- **Feedback & Feedforward Topologies**: Can be configured to route dynamic gain corrections backward through the level detector loop for smooth, vintage-style feedback compression.
- **Parallel Compression (Blend)**: A dry/wet blend parameter allows parallel master compression directly inside the master channel.
- **HPF Sidechain Filter**: Filters out low-end bass frequencies from entering the sidechain detector, preventing heavy kick drum elements from causing master level "pumping".

### 2. 🎚️ Extended Moog & SVF Filter Modes
Our current filter implementations default to generic, standard lowpass structures. The original C++ firmware contains a rich family of non-linear filter types:
- **Moog 2-Pole & 4-Pole Ladder Filters**: Models the physical response and phase shifts of discrete transistor ladders, including low-end bass drop compensation when resonance is boosted.
- **State-Variable Highpass & Notch Saturation**: Offers a morphable notch mode and highpass shape with independent resonance-compensated saturation loops, allowing aggressive self-oscillating tones.
- **LPF Filter Drive**: Exposes a dedicated saturation/drive parameter inside the recursive feedback loop.

### 3. 🔌 Modulation Matrix Source Expansion
While our current patch bay support basic sources like envelopes 1-2 and LFO 1, the Deluge firmware maps a much wider list:
- **Envelopes 3-4**: Allows complex triple-stage parameter ramps (e.g. env 3 driving noise envelope while env 4 drives FM feedback).
- **Secondary Local & Global LFOs**: Exposes a second local LFO (per-voice) and global master LFOs 1-2 to trigger slow spatial sweeps.
- **X/Y Controller Axes**: Maps structural controllers (such as performance pads or key parameters) directly to internal modulation destinations.

### 4. 🌀 Cubic Mod-FX Resonance Curves
The flanger, phaser, and chorus delay pipelines inside the Deluge C++ code utilize a custom mathematical feedback shaper:
- **32-Bit Cubic Feedback Limits**: Employs non-linear feedback loops ($y = 1.5x - 0.5x^3$) to naturally soft-clip high-frequency feedback peaks.
- **Fidelity**: Prevents feedback loops from exploding into harsh digital square waves, retaining round, highly organic feedback peaks that resemble bucket-brigade delay (BBD) hardware.

---

## 🎯 Recommended Next Steps

1. **Option 1: The Master Compressor** is a standalone, self-contained system that would immediately wrap our master output stage in a professional, punchy dynamic package.
2. **Option 2: The Extended Filter Modes** is highly creative and provides instant hands-on sound design options when building track presets.

*Prepared by Antigravity*
