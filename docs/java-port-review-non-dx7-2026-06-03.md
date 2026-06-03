# Java port review — non-DX7 synth path (2026-06-03)

Static review of the regular (subtractive / wavetable / sample) Deluge synth path in the Java
firmware engine vs the C++ firmware (`../DelugeFirmware`). Motivated by the DX7 bug class found
earlier (bit-field/offset/behavioral divergences hidden by self-only validation). **Review only —
no engine code changed.** Findings ranked by confidence/impact.

## Finding 1 — Envelope patch-source is not centered (bipolar) — HIGH
- **Firmware** `modulation/envelope.cpp`: `render()` and `noteOn()` return `(lastValue - 1073741824) << 1`
  ("Centre the range of the envelope around 0"); OFF returns `-2147483648`. The envelope is thus a
  **bipolar** patch source ([-2³¹, +2³¹] as the envelope goes 0→peak).
- **Java** `modulation/Envelope.java`: returns `lastValue` (unipolar [0, 2³¹)) and `0` for OFF — no
  centering. `FirmwareVoice` stores this uncentered value into `sourceValues[ENVELOPE_0..3]`
  (lines 319/334), and `Patcher.combineCablesLinear` applies `multiply_32x32_rshift32(srcVal, amount)`
  with **no re-centering**.
- **Impact:** envelope-as-**modulation-source** (e.g. the common env1→filter-cutoff, env→pitch) has
  ~half the depth and the wrong offset/polarity (unipolar 0→+A instead of firmware's bipolar −A→+A).
  The **volume VCA is unaffected** — `FirmwareVoice` uses `envelopes[0].lastValue` directly for the
  amplitude (separate path), so basic attack/decay loudness is correct.
- **Fix (needs care + a parity test):** store the centered value into the patch source, i.e.
  `sourceValues[ENVELOPE_0+i] = (envVal - 1073741824) << 1`, and return `-2147483648` for OFF — while
  keeping the direct `lastValue` VCA path. Verify env0→volume isn't double-applied (firmware routes
  volume through the patched centered source + a hardcoded cable; Java uses the direct VCA, so confirm
  there is no env0→LOCAL_VOLUME cable that would now double-count). Add a test: env1→cutoff sweep depth.

## Finding 2 — LFO S&H / Random-Walk overflow uses signed phase — MEDIUM
- **Firmware** `modulation/lfo.h`: `phase` is `uint32_t`; S&H/Random-Walk retrigger on unsigned wrap
  `(phase + phaseIncrement * numSamples < phase)`.
- **Java** `modulation/LFO.java:47,60`: `(long) phase + (long) phaseIncrement * numSamples > 0xFFFFFFFFL`.
  `phase` is a signed `int`; `(long) phase` **sign-extends** when the MSB is set, so for the entire
  upper half of every cycle the wrap/retrigger test is wrong → S&H and Random-Walk LFOs sample/step at
  the wrong times (or miss). SINE/SAW/TRIANGLE/SQUARE are unaffected (they don't use this test).
- **Fix:** treat phase/increment as unsigned, matching the firmware wrap:
  `long next = (phase & 0xFFFFFFFFL) + (phaseIncrement & 0xFFFFFFFFL) * numSamples; boolean wrapped = next > 0xFFFFFFFFL;`

## Finding 3 — 24-bit / 32-bit WAV samples load as silence — HIGH (feature gap)
- **Java** `firmware/storage/audio/AudioFileReader.java:49-52`: the PCM decode loop only handles
  `byteDepth == 2` (16-bit) and `== 1` (8-bit). For 24-bit (`byteDepth==3`) or 32-bit (`==4`) WAVs,
  neither branch runs → `data[i]` stays `0.0f` (silence) and the `ByteBuffer` is never advanced.
- **Impact:** any 24-bit sample (very common in sample libraries; the Deluge firmware supports them)
  plays as silence in sample-based synths/kits.
- **Fix:** add branches:
  - 24-bit: read 3 LE bytes, assemble + sign-extend, `/ 8388608.0f`.
  - 32-bit: detect int vs float (from `fmt` format tag 1 vs 3); int → `getInt()/2147483648.0f`, float → `getFloat()`.

## Areas checked and found correct (no bug)
- `util/Q31.java` multiply helpers (`mult`, `multiply_32x32_rshift32[_rounded]`, accumulate, saturate) —
  match firmware (`(int)((long)a*b >> 32)`, rounding adds `1<<31`).
- `dsp/oscillators/BasicWaves.java` — phase→table indexing uses logical `>>>`; interpolation widened to
  `long`; arithmetic `>>` only on the signed saw ramp. Correct.
- `AudioFileReader` 16-bit (`getShort()/32768`) and 8-bit (`(b & 0xFF)/128 - 1`) decode + sign — correct.
- Ladder filters — standard Q31 idioms (`>>>` on unsigned `logFreq`, `<<1` renormalize after rounded
  multiplies); no obvious sign/shift error (not bit-exact-verified without a hardware reference).
- (From the DX7 work) `scaleLevel/scaleCurve/scaleRate/scaleVelocity`, FM kernel + feedback, sin/exp2
  lookups, 32-entry algorithm table — all verified matching firmware.

## Suggested next steps
1. Fix #2 and #3 (mechanical, low-risk, match firmware exactly) + small unit tests.
2. Fix #1 with a dedicated env→cutoff parity test (highest behavioral impact; needs the double-count check).
3. Consider a hardware A/B for a subtractive preset with env→filter + an LFO, the way the DX7 A/B
   surfaced the pitch bugs — static review can't catch value-table or perceptual divergences.
