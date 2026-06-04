# Hardware A/B validation plan (2026-06-04 session)

Goal: confirm this session's faithfulness fixes against the real Deluge. For each patch below,
record the hardware output and render the Java output with the same patch + note, then compare
(listen + spectrum). The patches are chosen to each exercise specific fixes.

## How to render the Java side

```
mvn -pl deluge -am test-compile
CP="chuck-core/target/classes:deluge/target/classes:deluge/target/test-classes:<deps>"
java --enable-preview --add-modules=jdk.incubator.vector -cp "$CP" \
    org.chuck.deluge.reproduce.RenderPatchToWav "<patch.XML>" <midiNote> <out.wav> [seconds] [velocity]
```

`<deps>` = the dependency classpath (e.g. the value used by the existing `/tmp/dx7` harnesses, or
`mvn -pl deluge dependency:build-classpath`). The tool plays note-on, releases at 60% so the release
tail is captured, and writes 16-bit stereo WAV at 44.1 kHz.

## How to record the hardware side

Load the patch on the Deluge, play the single note for ~1.2 s then release; record the line out.
IMPORTANT: leave the gold (cutoff/mod) knobs at the patch's saved positions — physical knob moves
override the stored values and will not match the Java render (this bit us with 049 earlier).

## Patches (each targets specific fixes)

| Patch | Note | Velocity | Validates |
|---|---|---|---|
| `049 Basic FM` | C3 (60) | ~110 | **Native 2-op FM** engine (faithful voice.cpp port), modulator amount/ratio, the `envelope2 → modulator1Volume` cable driving FM brightness. Expect bright, broadband (energy through the high harmonics), not a dull near-sine. |
| `009 Hoover Bass` | C2 (36) | record both ~40 and ~110 | **Filter cutoff range** (open patches reach ~20 kHz now), **filter makeup gain** (no clipping/over-hot output), **velocity** sensitivity (the louder take should be clearly louder), **pan** (centred = balanced L/R). |
| `128_SYNTH_DUAL_MOD_C5` | C5 (72) | ~110 | **LFO rate** (firmware exp curve — the LFO modulation should move at the right speed) and **envelope rates** (attack/decay/release feel). |

## What changed this session (what you're listening for)

1. Native FM — was a dexed approximation; now faithful (049 should be bright).
2. `getExp` bug fix — corrected exp params (cutoff/LFO/pitch).
3. Filter cutoff range — open filters were capped ~6.3 kHz, now ~20 kHz (bright patches no longer muffled).
4. LFO rate — was an ad-hoc formula; now the firmware exp curve (correct speeds).
5. Envelope rates — per-stage firmware curves (attack vs decay/release differ).
6. Pan law — linear `shouldDoPanning` + fixed mis-centering (centred = balanced, full level).
7. Filter makeup gain — output was ~2.4× hot / clipping; now correct headroom.

## Known still-divergent (don't be surprised)

- Master compressor is disabled in Java (firmware applies a gentle song master compressor).
- `<volume>` is applied per-voice (`LOCAL_VOLUME`) vs the firmware's post-FX `GLOBAL_VOLUME_POST_FX`
  — same loudness for dry patches, slightly different wet/dry-vs-level for heavy-FX patches.
- Reverb/compressor algorithm fidelity not deeply verified (present and wired, but float ports).

## Comparison helpers

Spectrum/partials: the `/tmp/dx7/an.py` (windowed single-bin DFT of harmonics) and `spec2.py`
(top partials) scripts used this session. Compare peak partial, harmonic spread, and overall level
between the hardware and Java WAVs.
