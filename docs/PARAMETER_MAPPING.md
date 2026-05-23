# Deluge Parameter Mapping Reference Manual

This document acts as the definitive reference manual mapping the physical Yamaha/Deluge hardware parameter page system to our virtual Swing Workstation UI layout. It bridges physical front panel chassis markings with real-time model properties and high-fidelity DSP engine targets.

---

## 1. Physical Hardware Parameter Select Grid

On the physical Deluge body, the column of **8 buttons** vertically to the right of the key pad matrix, directly below the two gold encoders, functions as a **Page Selector** for the two primary physical gold encoder knobs. Each button can be toggled between an **Upper Mode** (green LED indicator) and a **Lower Mode** (orange/gold LED indicator) to access **16 distinct parameter slots**:

```
[ AFFECT ENTIRE ] (Global override selector)
   ├── Button 1 ── [ VOLUME (Upper) ]  /  [ PAN (Lower) ]
   ├── Button 2 ── [ TRANSPOSE      ]  /  [ PORTAMENTO  ]
   ├── Button 3 ── [ LFO 1          ]  /  [ LFO 2       ]
   ├── Button 4 ── [ CUTOFF / FM    ]  /  [ RESONANCE   ]
   ├── Button 5 ── [ ENV 1          ]  /  [ ENV 2       ]
   ├── Button 6 ── [ MODRATE        ]  /  [ DEPTH       ]
   ├── Button 7 ── [ DELAY          ]  /  [ REVERB       ]
   └── Button 8 ── [ ARP RATE       ]  /  [ GATE        ]
```

### Context-Dependent DSP Engine Mappings:
The physical parameter target shifts dynamically based on the active **Track Instrument Type**:

| Physical Selector | Subtractive Synth Target | FM Synth Target (6-Op DX7) | Drum Kit Target |
| :--- | :--- | :--- | :--- |
| **VOLUME** | Master Track Volume | Master Voice FM Volume | Master Kit Volume |
| **PAN** | Stereo Pan Position | Stereo Pan Position | Stereo Pan Position |
| **TRANSPOSE** | Pitch semitone tune | Pitch semitone tune | Row drum-sample pitch |
| **PORTAMENTO** | Glide transition speed | Operator detune offset | Not applicable |
| **LFO 1** | Primary LFO modulation | Pitch LFO speed/depth | Drum pitch LFO |
| **LFO 2** | Secondary LFO modulation | Amplitude LFO speed/depth | Drum filter LFO |
| **CUTOFF / FM** | Analog Lowpass Cutoff | Operator Frequency Coarse/Ratio | Drum Lane Filter Cutoff |
| **RESONANCE** | Filter Resonance (Q) | Operator Level / Feedback depth | Drum Lane Filter Q |
| **ENV 1** | Amp ADSR Envelope | Operator EG Rates 1-4 | Drum Amp Envelope |
| **ENV 2** | Filter ADSR Envelope | Operator EG Levels 1-4 | Drum Filter Envelope |
| **MODRATE** | Mod Chorus/Flanger LFO rate | Carrier detune phase | Mod Chorus rate |
| **DEPTH** | Mod Chorus/Flanger Depth | FM Operator modulation index | Mod Chorus depth |
| **DELAY** | Delay Time/Feedback amount | Delay Time/Feedback amount | Delay Time/Feedback amount |
| **REVERB** | Reverb Send Level / size | Reverb Send Level / size | Reverb Send Level / size |
| **ARP RATE** | Arpeggiator Rate step-division| Not applicable | Arpeggiator Rate |
| **GATE** | Arp gate length / swing | Not applicable | Arp gate length / swing |

---

## 2. Virtual UI 16-Macro Column Mapping Matrix

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

---

## 3. How Software Handles Modifiers & Double Toggles
*   **The Shift Multiplier:** Holding the **`Shift`** key while dragging a virtual macro slider button immediately slows down the adjustment resolution by a factor of **10x** for high-precision fine-tuning (e.g. tuning sub-bass synth cents or exact envelope milliseconds!).
*   **Context-Coherence resets:** Toggling step sequence buttons on the UI grid instantly resets active `rawNoteEvents` for that row, seamlessly falling back from parsed high-resolution sub-tick events back to standard grid alignments.
