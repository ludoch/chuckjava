# Deluge Parameter Mapping Reference Manual

This document acts as the definitive reference manual mapping the physical Yamaha/Deluge hardware parameter page system to our virtual Swing Workstation UI layout. It bridges physical front panel chassis markings with real-time model properties and high-fidelity DSP engine targets.

---

## 1. The Physical Deluge 8-Button × 2-Encoder System

On the physical Deluge, the column of **8 buttons** next to the key pad matrix acts as a **Page Selector** for the two gold encoder knobs (the **Left Gold Knob** and the **Right Gold Knob**). Toggling a button selects between its **Upper Mode** (green LED) and **Lower Mode** (orange/gold LED), routing the two encoders to control two distinct parameters at once:

```
[ AFFECT ENTIRE ] (Global override selector)
   ├── Button 1 ── [ VOLUME (Upper) ]  /  [ PAN (Lower) ]
   ├── Button 2 ── [ TRANSPOSE      ]  /  [ PORTAMENTO  ]
   ├── Button 3 ── [ LFO 1          ]  /  [ LFO 2       ]
   ├── Button 4 ── [ CUTOFF / FM    ]  /  [ HPF / LEVEL ]
   ├── Button 5 ── [ ENV 1          ]  /  [ ENV 2       ]
   ├── Button 6 ── [ MODRATE        ]  /  [ DEPTH       ]
   ├── Button 7 ── [ DELAY          ]  /  [ REVERB       ]
   └── Button 8 ── [ ARP RATE       ]  /  [ GATE        ]
```

---

## 2. Double-Knob Parameter Mapping Grid (16 Logical Pages)

Below is the complete mapping of what the **Left Gold Knob (Knob 1)** and **Right Gold Knob (Knob 2)** control under all 16 hardware select modes across Subtractive Synth, 6-Operator FM (DX7), and Drum Kit track types, alongside their **Swing UI Virtual Macro** column shortcut targets:

| Physical Button & State | Subtractive Synth Mode (Left / Right Knobs) | FM Synth Mode (Left / Right Knobs) | Drum Kit Mode (Left / Right Knobs) | Swing UI Virtual Macro (Col Index) |
| :--- | :--- | :--- | :--- | :--- |
| **VOLUME** *(Btn 1 Upper)* | **L:** Master Volume <br> **R:** Stereo Pan position | **L:** Master FM Volume <br> **R:** Stereo Pan position | **L:** Selected Drum Lane Vol <br> **R:** Selected Drum Lane Pan | Column 0: **`LEVEL`** |
| **PAN** *(Btn 1 Lower)* | **L:** Delay Send Level <br> **R:** Reverb Send Level | **L:** Delay Send Level <br> **R:** Reverb Send Level | **L:** Delay Send Level <br> **R:** Reverb Send Level | Column 1: **`PAN`** |
| **TRANSPOSE** *(Btn 2 Upper)* | **L:** Transpose (Semitones) <br> **R:** Fine Tune (Cents) | **L:** Transpose (Semitones) <br> **R:** Fine Tune (Cents) | **L:** Lane Drum Pitch (Semi) <br> **R:** Drum Sample Fine Tune | Column 2: **`PITCH`** |
| **PORTAMENTO** *(Btn 2 Lower)* | **L:** Portamento Glide Time <br> **R:** Legato Auto-Glide switch | **L:** Portamento Glide Time <br> **R:** Legato Auto-Glide switch | *Not Applicable* | *Handled inside custom popup editor* |
| **LFO1** *(Btn 3 Upper)* | **L:** LFO 1 Frequency Speed <br> **R:** LFO 1 Modulation Depth | **L:** Global LFO Speed (Hz) <br> **R:** Global LFO Pitch Depth | **L:** Selected Lane LFO 1 Speed <br> **R:** Selected Lane LFO 1 Depth | Column 7: **`LFO`** *(Controls speed)* |
| **LFO2** *(Btn 3 Lower)* | **L:** LFO 2 Frequency Speed <br> **R:** LFO 2 Modulation Depth | **L:** Global LFO Speed (Hz) <br> **R:** Global LFO Amp/Filter Depth | **L:** Selected Lane LFO 2 Speed <br> **R:** Selected Lane LFO 2 Depth | *Handled globally via LFO models* |
| **CUTOFF / FM** *(Btn 4 Upper)* | **L:** LPF Cutoff Frequency <br> **R:** LPF Resonance (Q) | **L:** Op Coarse Freq (Ratio) <br> **R:** Op Fine Frequency Ratio | **L:** Selected Lane LPF Cutoff <br> **R:** Selected Lane LPF Resonance | Column 3: **`FILTER`** |
| **RESONANCE / FM** *(Btn 4 Lower)*| **L:** HPF Cutoff Frequency <br> **R:** HPF Resonance (Q) | **L:** Op Output Level (0-99) <br> **R:** Op Detune Offset (-7/+7) | **L:** Selected Lane HPF Cutoff <br> **R:** Selected Lane HPF Resonance | Column 4: **`RESONANCE`** |
| **ENV1** *(Btn 5 Upper)* | **L:** Amp Attack *(Sus via Shift)* <br> **R:** Amp Decay *(Rel via Shift)* | **L:** Active Op EG Rate 1 / 2 <br> **R:** Active Op EG Rate 3 / 4 | **L:** Selected Lane Amp Attack <br> **R:** Selected Lane Amp Decay/Release| Column 5: **`OSC1`** *(Acts as FM envelope rates select)* |
| **ENV2** *(Btn 5 Lower)* | **L:** Filter Attack *(Sus via Shift)* <br> **R:** Filter Decay *(Rel via Shift)*| **L:** Active Op EG Level 1 / 2 <br> **R:** Active Op EG Level 3 / 4 | **L:** Selected Lane Filter Attack <br> **R:** Selected Lane Filter Decay/Rel | Column 6: **`OSC2`** *(Acts as FM envelope levels select)* |
| **MODRATE** *(Btn 6 Upper)* | **L:** Mod FX LFO Rate/Speed <br> **R:** Mod FX Modulation Depth | **L:** FM Algorithm (0-31) <br> **R:** FM Feedback Depth (0-7) | **L:** Selected Lane Mod LFO Rate <br> **R:** Selected Lane Mod LFO Depth | Column 8: **`MOD FX`** *(Controls depth)* |
| **DEPTH** *(Btn 6 Lower)* | **L:** Mod FX Feedback amount <br> **R:** Mod Type (Chorus/Flg/Phs) | **L:** Carrier Phase Sync <br> **R:** Active Operator Enabled State| **L:** Selected Lane Mod Feedback <br> **R:** Mod Type (Chorus/Flg/Phs) | *Handled inside Mod Config Popup* |
| **DELAY** *(Btn 7 Upper)* | **L:** Delay Sync Time/Rate <br> **R:** Delay Feedback level | **L:** Delay Sync Time/Rate <br> **R:** Delay Feedback level | **L:** Delay Sync Time/Rate <br> **R:** Delay Feedback level | Column 9: **`DELAY`** |
| **REVERB** *(Btn 7 Lower)* | **L:** Reverb Decay/Room Size <br> **R:** Reverb Highpass Damping | **L:** Reverb Decay/Room Size <br> **R:** Reverb Highpass Damping | **L:** Reverb Decay/Room Size <br> **R:** Reverb Highpass Damping | Column 10: **`REVERB`** |
| **ARP RATE** *(Btn 8 Upper)* | **L:** Arpeggiator Step Rate <br> **R:** Arpeggiator Direction Mode | *Not Applicable* | **L:** Arpeggiator Step Rate <br> **R:** Arpeggiator Direction Mode | Column 11: **`STUTTER`** *(Quick performance repeat)* |
| **GATE** *(Btn 8 Lower)* | **L:** Arpeggiator Gate Duration <br> **R:** Arpeggiator Octave Range | *Not Applicable* | **L:** Arpeggiator Gate Duration <br> **R:** Arpeggiator Octave Range | Column 13: **`GATE`** |

---

## 3. Virtual UI 16-Macro Column Mapping Matrix

To simplify computer operation with a standard mouse/keyboard interface, the Deluge Swing Workstation flattens these upper/lower hardware control pages into a **virtual 9th row (the Macro Sliders row)** consisting of **16 interactive button columns** (Index 0 to 15). 

Below is the complete, comprehensive mapping matrix for the 16 virtual macro columns in the Swing UI:

| Column Index | Software Macro Label | Upper/Lower Group | Model Property Target | High-Fidelity DSP/JNI Target | Synth Mode Behavior | FM Mode Behavior | Kit/Drum Mode Behavior |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- | :--- |
| **0** | **LEVEL** | Upper 1 | `track.getVolume()` | `LOCAL_VOLUME` in Q31 | Adjusts track master gain (0.0 to 1.5 multiplier). | Adjusts master FM voice loop master gain. | Adjusts overall summing drum kit voice gain. |
| **1** | **PAN** | Lower 1 | `track.getPan()` | `LOCAL_PAN` in Q31 | Adjusts stereo panning placement (-1.0 left to +1.0 right). | Adjusts stereo panning placement. | Adjusts master stereo panning placement. |
| **2** | **PITCH** | Upper 2 | `st.getTranspose()` | `LOCAL_PITCH_ADJUST` | Shifts note pitches (-24 to +24 semitones). | Shifts fundamental Carrier frequency. | Custom slider: adjusts selected drum lane sample pitch. |
| **3** | **FILTER** | Upper 4 | `st.getLpfFreq()` | `LOCAL_LPF_FREQUENCY` | Adjusts Lowpass Filter Cutoff frequency (20Hz to 20kHz). | Adjusts the active FM Operator's Coarse/Ratio Frequency (0 to 31 ratio). | Adjusts overall drum lane filter cutoff. |
| **4** | **RESONANCE** | Lower 4 | `st.getLpfRes()` | `LOCAL_LPF_RESONANCE` | Adjusts analog lowpass filter resonance Q (0.0 to 1.0). | Adjusts the active FM Operator's Output Volume Level (0 to 99). | Adjusts overall drum lane filter resonance. |
| **5** | **OSC1** | Upper 5 | `st.getOscMix()` | `LOCAL_OSC_A_VOLUME` | Adjusts relative volume mix of Oscillator A (0.0 to 1.0). | Controls FM Carrier feedback depth (0 to 7 level). | Custom: adjusts relative drum lane sample playback start offset. |
| **6** | **OSC2** | Lower 5 | `st.getNoiseVol()` | `LOCAL_NOISE_VOLUME` | Adjusts background white noise generator level (0.0 to 1.0). | Controls individual FM Modulator frequency ratios. | Custom: adjusts relative drum lane sample release envelope. |
| **7** | **LFO** | Upper 3 | `lfo0.rateHz()` | `LFO_SPEED` / `lfo_unit` | Adjusts primary LFO 1 speed frequency (0.0Hz to 20.0Hz). | Adjusts DX7 FM global Pitch/Modulation LFO Speed (0 to 99). | Adjusts drum pitch modulation LFO speed. |
| **8** | **MOD FX** | Upper 6 | `st.getModFxDepth()` | `modFXDepth` in Q31 | Adjusts mod Chorus/Flanger effect depth (0.0 to 1.0). | Adjusts carrier phase offset parameter. | Adjusts mod Chorus/Flanger depth. |
| **9** | **DELAY** | Upper 7 | `st.getDelaySend()` | `delaySend` in Q31 | Adjusts master Delay spatial send amount (0.0 to 1.0). | Adjusts master Delay send amount. | Adjusts individual drum lane delay send amount. |
| **10** | **REVERB** | Lower 7 | `st.getReverbSend()` | `reverbSend` in Q31 | Adjusts master Reverb spatial send amount (0.0 to 1.0). | Adjusts master Reverb send amount. | Adjusts individual drum lane reverb send amount. |
| **11** | **STUTTER** | - | `st.getStutterRate()` | `stutterRate` / `Stutterer` | Adjusts real-time step repeater rate (0.0 to 1.0). | Adjusts real-time step repeater rate. | Adjusts overall drum loop stutter gate. |
| **12** | **PROBABILITY** | - | `step.probability()` | `getStepProbability()` | Adjusts step-event trigger probability (0% to 100%). | Adjusts step-event trigger probability. | Adjusts individual drum step trigger probability. |
| **13** | **GATE** | Lower 8 | `step.gate()` | note duration ticks | Adjusts step note length duration gate multiplier (0.0 to 2.0). | Adjusts FM note duration gate. | Adjusts individual drum lane gate gate. |
| **14** | **VELOCITY** | - | `step.velocity()` | note velocity value | Adjusts note key strike velocity input (0.0 to 1.0). | Adjusts FM note envelope strike velocity. | Adjusts individual drum lane trigger velocity. |
| **15** | **SAMPLE** | - | sample file paths | `AudioFileReader` sample | Enters sample browse/swap menu for custom wav selection. | Enters custom DX7 SysEx patch selection list dialog! | Enters drum kit sample swap/load menu. |
