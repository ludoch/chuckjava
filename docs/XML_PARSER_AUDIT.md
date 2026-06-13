# XML parser audit — our `DelugeXmlParser` vs the firmware's deserializer (2026-06-13)

Motivation: every fidelity bug found by the hardware-recording comparison this week traced to the
**XML → model bridge**, not the faithful DSP port. This audit compares our parser against the
firmware's own song/preset reader and records the systemic divergence + the per-param status.

## The core finding

**The firmware reads every sound parameter as a raw Q31 integer, verbatim.**
`Sound::readTagFromFileOrError` → `ParamSet::readParam` → `AutoParam::readFromFile`
(`auto_param.cpp:1886`) parses each `0x…` value straight into `params[p].currentValue`. There is
**no float conversion, no scaling, no curve** at read time, and the **same reader serves both**
preset `<defaultParams>` and song `<soundParams>`/`<kitParams>`. The patcher applies the curves
later, at render time, from that raw knob.

**Our parser does the opposite for most params:** `FieldBinding.hexFloat/hexHz` → a 0..1 model
float → the factory's `normToXxxKnob` → a knob. This double conversion is lossy and was the root
cause of the bugs below. Worst offender: `normToLinearParamKnob` floors a linear param's minimum
at `-2^29` instead of `INT_MIN`, so a song's *minimum* resonance/morph became a *moderate* value.

## Bugs this divergence caused (all fixed this week)

| Symptom (hardware comparison) | Root cause in the bridge | Fix |
|---|---|---|
| Real songs played instrument defaults (filter open, instant attack, no LFO/FM) | clip `<soundParams>` never read into the model — only preset `<defaultParams>` was bound | `parseClipSoundParamsStatics` (`fa5e86bf`) |
| Wrong pitches on loaded songs | sparse noteRow `y` (absolute note) ignored; row-index used | `ClipModel.rowYNote` (`8e47eba9`) |
| LFO vibrato far too slow | factory pre-curved the rate knob, patcher curved it again | raw knob stored (`fa5e86bf`) |
| Ladder LPF added 76% 2nd harmonic to a clean sine | min resonance floored at `-2^29` via float round-trip | raw-Q31 knob overlay (`7642b6c9`) |
| Instrument delay never sounded (HW 1.0s, ours ~65ms) | per-sound delay missing from the FX chain | per-sound delay port (`59a642ed`) |

## Per-param status (song `<soundParams>`, the 84 firmware param names)

Legend: **raw** = read as raw Q31 (firmware-faithful); **float** = lossy hex→float→knob round-trip;
**scalar** = handled by a dedicated engine field (own conversion); **n/a** = not a sound param.

### Patched params (go through the patcher / param array)
- **raw (correct):** lpfFrequency, lpfResonance, lpfMorph, hpfFrequency, hpfResonance, hpfMorph,
  volume, pan, oscAVolume, oscBVolume, noiseVolume, oscAPhaseWidth, oscBPhaseWidth,
  oscAWavetablePosition, oscBWavetablePosition, oscAPitch, oscBPitch, modulator1Pitch,
  modulator2Pitch — via `SOUNDPARAMS_RAW_PATCHED` (`7642b6c9` + 2026-06-13 extension).
- **raw via dedicated setters:** modulator1Volume/2Volume, modulator1Feedback/2Feedback,
  carrier1Feedback/2Feedback, waveFold, portamento, lfo1Rate (→GLOBAL_LFO_FREQ_1),
  lfo2Rate (→LOCAL_LFO_LOCAL_FREQ_1), env1..4 Attack/Decay/Release.
- **float — env SUSTAIN** (`env*Sustain`): still via `normToLinearParamKnob` (same min-floor class
  as resonance). Low risk (a level, not a tanh trigger) but should move to raw. **OPEN.**
- **not represented:** lfo3Rate/lfo4Rate (our sound has 2 LFOs), modulator pitch beyond 2.

### Unpatched / FX params (dedicated scalar fields — separate conversion, not the param array)
modFXRate, modFXDepth, modFXFeedback, modFXOffset, delayRate, delayFeedback, reverbAmount,
stutterRate, sampleRateReduction, bitcrushAmount, compressorShape, compressorThreshold, bass,
treble, bassFreq, trebleFreq. These use `setModFx*`/`setBitCrush`/etc. with bespoke scaling — NOT
yet audited value-by-value against the firmware's curves. **OPEN (medium priority).**

### Arp params
arpGate, arpRate, noteProbability, bassProbability, swapProbability, glideProbability,
reverseProbability, chordProbability, ratchetProbability, ratchetAmount, spreadVelocity,
spreadGate, spreadOctave, sequenceLength, chordPolyphony, rhythm → handled via `ArpModel` +
`configureArp` (value-scaling verified earlier). OK.

## Gaps that are NOT parser issues (genuine DSP calibration)

The hardware comparison shows two remaining differences that survive correct (raw) param input,
so they live in the DSP / curves, not the bridge:

1. **Ladder filter cutoff curve + resonance strength.** `TestFilterFidelity` (saw C3, center
   cutoff, moderate resonance) on hardware has a strong resonant peak ~3–4 octaves up (≈H12) and
   passes energy to ≈H14; ours rolls off by ≈H6 with **no resonant peak**. So our cutoff sits
   lower and our resonance is much weaker for the same raw knob. Suspects: `curveFrequency`
   (knob→Hz), and the ladder feedback/`processedResonance` scaling. The filter is a "faithful
   port", so compare `curveFrequency` + the resonance path against `lpladder.cpp` with identical
   raw inputs.
2. **Delay feedback level.** Timing is now correct (1.0s); HW echoes *grow* (near-unity feedback)
   where ours decay at the song's 0.25 knob — the feedback-amount→repeats/gain mapping.
3. **FM brightness/depth** spectral match (lower priority; modulator amount is now raw).

## Recommended next steps (by risk)

1. **DONE** — read all patched `<soundParams>` raw (this audit's implementation).
2. Move env **sustain** to raw (same bug class; easy).
3. Apply the **same raw reader to preset `<defaultParams>`** so presets and songs use one path
   (the firmware does). Higher risk: the preset fidelity tests are calibrated to the current float
   path — validate each against its reference WAV.
4. Audit the **unpatched FX scalar conversions** (modFX/delay/bitcrush/srr/eq/sidechain) against
   the firmware curves.
5. DSP calibration: ladder **cutoff curve + resonance**, then **delay feedback**, then **FM**.

## Method note

Measure rate/pitch/level at the **engine** (direct probes of `calculateBasePhaseIncrement`,
`computeFinalValueForParam`, global LFO source values) — fixed-bin Goertzel and zero-crossing
meters lie on vibrato'd or harmonically-rich tones (a filter's 2nd harmonic read as an octave
error; a swept LFO smeared across bins). Steady tones (filter/noise/tuning) measure cleanly.
