# Fidelity Gap Analysis — Java engine vs real Deluge hardware

**Purpose.** This is the working reference for closing the audio-fidelity gap between our
`org.deluge.firmware2` engine and a real Synthstrom Deluge (c1.2.0). Fidelity is the project's
make-or-break goal. This doc is meant to be picked up by any agent: it states how we measure
fidelity, the current score, exactly which synth families fail, the likely C subsystems
responsible, and the workflow to make + verify progress.

Last measured: commit `296716b4` (2026-06-26).

---

## 1. Ground truth: the reference recordings

Two arranger songs, each playing ~94 ludocard synth presets one-by-one (one sustained C4 per
synth, ~4 s each), resampled on real hardware:

| recording | format | duration | content |
|---|---|---|---|
| `ALLSYN_1/output_000.wav` | 24-bit stereo 44.1 kHz | ~377 s | playable synths 0–93 |
| `ALLSYN_2/output_000.wav` | 24-bit stereo 44.1 kHz | ~379 s | playable synths 94–187 (incl. 16 multisamples) |

- **Not in git** (95 MB each — over GitHub's practical limit). They live on the dev machine at
  `~/ALL_SYNTHS_SONG/ALLSYN_{1,2}/output_000.wav`. Keep a copy; they are irreplaceable ground truth.
- The matching songs are generated, not hand-made: `AllSynthsFidelityTest#generateAllSynthsSong`
  with `-Dsynth.dir=~/ludocard/SYNTHS -Dsynth.offset=N -Dsynth.max=M`, written to
  `~/ludocard/SONGS/ALLSYN_{1,2}.XML`. Each synth's clip holds one C4; the synths are sorted by
  filename and missing-sample presets are skipped (see [[all-synths-arranger-hardware]] / the memory).
- **Why two songs of 94, not one of 188:** the Deluge can't hold ~188 instruments — RAM exhaustion
  makes synths progressively silent past ~120 and stalls the playhead entirely at 188. ≤~94 plays
  cleanly. This is a hardware limit, not a bug.

## 2. How we measure: `FidelityScorecardTest`

Renders each synth through our engine (single C4, 3 s), then compares a **normalized
log-magnitude spectrum** (48 log bins 50 Hz–15 kHz, mean-subtracted) against the matching slice of
the hardware recording, using **cosine similarity** (1.0 = identical timbre). The metric is
level- and alignment-tolerant (per-synth it picks the loudest 2 s window on both sides).

Run it (self-skips if the recordings aren't present):
```
mvn -pl deluge test -Dgpg.skip=true -Dtest=FidelityScorecardTest
```
It prints a per-synth table + a summary (mean/median/distribution). Re-run after any change to
track progress and catch regressions.

## 3. Current score (TRUSTWORTHY baseline — gapped recordings + onset alignment)

- **169 measurable synths: median 0.72, mean 0.68.**
- **≥0.90: 12 (7%) · ≥0.80: 59 (35%) · <0.60: 43.**
- 19 not measurable (our engine renders them silent — see §5).

This is the first baseline we **trust** at the per-synth level. The songs now place a **2 s silence
gap between synths** (AllSynthsFidelityTest, `-Dsynth.gap`), so every attack is an unambiguous
onset; FidelityScorecardTest fits a global uniform grid (period+offset by cross-correlation of an
energy-rise function) + a ±0.3 s snap and confirms tight onset spacing (**5.7–6.4 s** around the
6 s nominal — i.e. each synth is correctly located). vs the gapless recordings this lifted ≥0.80
from 43 → 59 synths by *fixing mismeasurements*, not the engine.

History (for context): earlier numbers were unreliable. Gapless concatenated recordings made
per-synth alignment ambiguous — equal-slice / greedy-tracking / global-grid gave medians 0.68–0.70
and individual synths swung wildly (FM Bells 1: −0.31 ↔ 0.00) because the cosine overfit by
window-shopping neighbours. The transpose-stripped recordings before that gave a false 0.72. The
gap fix removes this whole class of error — **re-record only when serialization changes.**

This is **mediocre overall** but **highly non-uniform**: the failure is concentrated in specific
synthesis subsystems, while the subtractive core is faithful. With alignment now reliable, the low
scorers are CONFIRMED-real gaps (not artifacts): FM bells/modulation (≈0 / negative cosine) and
oscillator hard-sync (Saw Sync 0.09) remain the worst — see §4.

## 4. The gaps, ranked by impact (with evidence + likely cause)

### 4.1 FM synthesis — WORST (negative/near-zero cosine)
Our 2-op FM timbre is wrong (often anti-correlated — energy in the wrong harmonics).
- `056 FM Bell Modulation` **−0.14**, `093 FM Distorted Bells` **0.06**, `068 FM Bells 1` **0.13**,
  `084 FM Narrow Band` **0.22**, `069 FM Bells 2` **0.31**, FM pads 0.6–0.8.
- Likely C subsystems: `model/voice/voice.cpp` FM path (`renderFmPath`/`renderCarrier*`), modulator
  phase-increment + feedback, `dsp/` operator math. Check modulator→carrier index scaling,
  feedback, and the modulator transpose/cents (we just reworked source transpose — verify FM
  modulator transpose path too). Compare our `Voice.renderFmPath` against the C operator-by-operator.

### 4.2 Oscillator hard sync — major
Sync patches are badly off.
- `046 Saw Sync` **0.04**, `045 Square Sync` **0.28**, `098 Saturated Sync` **0.33**.
- Likely cause: `processing/render_wave.h` `renderOscSync` / `oscillator.cpp` sync branch. Our
  `Oscillator.renderWaveSync` (the half-sine crossfade at the reset) may not match the C's reset
  handling. The band-limited PWM port (renderPulseWave) was recently fixed; the **sync** path is the
  next oscillator item.

### 4.3 Resonant / distorted filter — moderate
- `015 Resonant Filter Bass` **0.24**, `120 High Harsh Pad` **0.23**, `124 Filter Modulation Pad`
  **0.30**, `059 Distorted Lead Guitar` **−0.01**.
- Likely cause: filter resonance curve / self-oscillation, and the wavefolder/clipping
  (`mod_controllable_audio` saturation, `LOCAL_FOLD`). Check `FirmwareFilter` resonance + drive vs
  the C `dsp/filter/*`.

### 4.4 Reverb / delay / modFX (pads) — moderate, partly a metric confound
Reverb-heavy pads score 0.4–0.7. Our render **is** wet (master reverb is applied), so this is our
reverb/FX **algorithm differing** from the C, not missing FX.
- `137 Epic Saw Modulation Pad` 0.44, `141 Ringmod Pad` 0.48, `144 Sweep Chords` 0.22,
  `133 80s Strings` 0.62.
- Likely cause: `dsp/reverb/*` (our `firmware2/Reverb.java`), `dsp/delay/*` (`Delay.java`), modFX.
  Calibrate the reverb against the C; note that reverb-tail differences depress these scores beyond
  the true dry-tone error.

## 5. Real bugs: synths our engine renders SILENT

These produce no sound in-engine but DO sound on hardware. Highest priority — they're 0 fidelity:
- **`107 FM LPG Percussion`** — non-sample FM patch, genuinely silent in our engine. Real bug.
- (Investigate any other non-multisample entries flagged `n/a` by the scorecard; some may be
  slow-attack/arp false-positives — confirm with a longer render before treating as a bug.)
- **~14 multisamples** (`SawFifthFilter`, `SolidBass*`, `Vibraphone`, `Hang Drum`, `Sitar`,
  `Soft Sax`, `Trompet`, …) are silent **only because the scorecard's in-engine render doesn't load
  their sample files** — they sound on hardware. To make them measurable we must load multisample
  samples in the engine test path. Not a synthesis bug; a test-harness/sample-loading gap.

## 6. What is already faithful — DO NOT REGRESS

Simple subtractive patches match hardware well; protect these when changing shared code:
- `117 Belledy` 0.97, `114 Sootheerio` 0.96, `006 Vaporwave Bass` 0.96, `080 House` 0.93,
  `165 Acid Arp` 0.93, `036 Analog Ambient Square` 0.93, `054 Ghostly Sines` 0.92,
  `163 Crisp Pop Arp` 0.92. Most bass/lead/arp patches sit 0.85–0.93.

Takeaway: **oscillator (saw/square/sine/analog) + ladder filter + ADSR envelope are faithful.** The
gap is in FM, oscillator sync, resonance/distortion, and FX.

## 7. Metric caveats (so the number isn't misread)

- **Spectral-only:** ignores amplitude-envelope/time evolution. A pad with the right spectrum but a
  wrong attack/swell still scores high; a right tone with wrong dynamics is not penalized. Consider
  adding an envelope/onset metric later.
- **Reverb-tail differences** depress FX-heavy scores below the true synthesis error.
- **Per-synth alignment** uses loudest-window matching — robust but not sample-exact.
- **One held C4** — doesn't exercise keytracking/velocity layers across the range.
- These mean §3's 0.72 **understates** core synthesis fidelity and **over-attributes** error to FX.

## 8. Workflow to make progress (for any agent)

1. Pick the highest-impact family from §4 (start with FM or sync — biggest, clearest gaps).
2. Open the exact C subsystem (cited above) under `~/a/DelugeFirmware/src/deluge/`. Read it; mirror
   its structure. This is a faithful port — translate the C, cite file:line (see
   `docs/FIRMWARE2_FAITHFUL_PORT.md`).
3. Fix in `org.deluge.firmware2`. Build/format: `mvn -pl deluge compile` /
   `mvn -pl deluge spotless:apply`.
4. **Re-run `FidelityScorecardTest`.** Confirm the targeted family's scores rose AND the §6
   faithful set did not regress. The scorecard is the objective gate — don't claim a fidelity fix
   without it moving the number (and beware spectral-blind confounds; cross-check with a direct
   spectral probe like SquarePwmRenderTest's Goertzel approach when in doubt).
5. Update §3/§4 here with the new numbers.

**Honesty rule (hard-won):** RMS and autocorrelation have repeatedly given false readings on this
project (RMS is duty/pitch-invariant; autocorrelation mis-locks on harmonic-rich tones; a phantom
osc-B SINE once masqueraded as an osc-pitch bug). Always reset the noise seed
(`Functions.resetNoiseSeed()`) and verify with a spectral metric. Never report a fidelity
improvement the scorecard doesn't confirm.
