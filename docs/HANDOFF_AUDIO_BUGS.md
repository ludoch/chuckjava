# Handoff — audio bugs + master-FX work (2026-06-10)

Live status of the Mac-reported audio bugs and the master-FX work around them. Read with
`FIRMWARE2_DSP_PORT_STATUS.md` and the memories `fw2-master-delay-no-echo-bug`,
`deluge-remaining-approximations`.

## Slow-test regression scan (`mvn -pl deluge test -Pslow-tests`, 2026-06-10)

**412 run, 17 failures, 0 errors, 98 skipped. ALL 17 failures are `PhysicalHardwareFidelityTest`**
(synth waveform parity vs the real-Deluge WAVs). Every other slow test passes (sample engine,
time-stretch, master FX, E2E, MIDI, model, project, UI, ADSR, etc.). Failing cases: testDrySawtoothParity
(+REC07), testPwmSquareParity, testFilteredLPFParity, testHooverBassRecordingParity, testResonantLpfSawParity,
testSynthDualModRecordingParity, testDetunedSawParity, testPureNoiseParity, testFmSimpleParity,
testNoiseLpfModParity, testFilterModSawParity, testLfoAutoPanSawParity, testTriangleSawParity,
testBasicFmRecordingParity, testDelayTrailSawParity, testFmFeedbackParity.

These are `@Tag("slow")` (excluded from the default build) so they've been red for a while — **not a
fresh regression from this session** (this session touched master-FX/driver/kit/Patcher-neutral, not the
oscillator core). Triage of the dry saw: FW2 plays the **correct pitch** (~545 Hz ≈ C5) at **roughly the
correct level** (FW2 RMS 0.017 at the test's 2.5% volume × 40 ≈ 0.68 ≈ HW 0.56), but the **waveform shape
differs** — the HW reference is far brighter / more HF (zero-cross rate ~10.7 kHz vs FW2's clean ~545 Hz),
so cross-correlation is ~0. This is the **oscillator timbre approximation**, not silence or wrong notes.
See `deluge-remaining-approximations` (crude oscillators). Fixing it = make the fw2 saw/square/etc. match
the Deluge's band-limited oscillator harmonics; validate by getting these 17 correlations ≥ 0.90.

## ⚠️ TOP PRIORITY (added after user reported "synth sounds terrible, nothing works for synth")

**1. Synth output does NOT match the real-Deluge reference WAVs — shape correlation ~0.**
`PhysicalHardwareFidelityTest` (run with `mvn -pl deluge test -Pslow-tests -Dtest=PhysicalHardwareFidelityTest`)
renders the actual XML patches and cross-correlates against 37 real Deluge recordings in
`deluge/src/test/resources/fidelity/*.wav` (the ORACLE the user remembered). **Every correlation is
~0** (Dry Saw 0.08, Sine 0.19, Detuned Saw -0.25, Eight-Voice Unison 0.03, …; assertion wants ≥0.90).
These tests are `@Tag("slow")` → **excluded from the default build**, so they've been silently failing.
This is almost certainly "nothing works for synth." Either the synth DSP shape is wrong, or the test
methodology is broken (alignment/window/pitch). **Investigate first:**
   - Quick triage: render `098_DRY_SAW_C5.XML`, check the compared window `sw[88200 .. 88200+4410]` —
     is it sustained saw audio at the right pitch (note 72 = C5 ≈ 523 Hz)? If near-silent → the note
     isn't sounding there (envelope/trigger-timing bug). If audible but wrong frequency → pitch bug. If
     right pitch but low correlation → waveform-shape/aliasing bug (crude oscillator — see
     `deluge-remaining-approximations`). The large `bestLagOffset` values (±200–340) hint at pitch/phase
     drift across the window.
   - The test forces `LOCAL_OSC_A_VOLUME = LOCAL_VOLUME = 53687091` (2.5%) and uses `s.l >> 15`, so it is
     shape-only (level-independent) and does NOT exercise the 48×/12× driver gain.

**2. "Terrible synth" distortion — FIXED (`d7eb5e48`).** The driver did `(s.l * 48) >> 16` as int×int;
`s.l * 48` overflows int32 for |s.l| > ~0.02 Q31 (every audible synth note) → wraparound garbage →
harsh distortion. Fixed: long arithmetic + clamp; gain 48×→12×. Kit drums (~0.003 Q31) didn't overflow,
so they were merely quiet (see #3).

**3. Kit drums far too quiet (~-49 dB).** A drum sample (~0.8 peak in the file) renders at only ~0.003
Q31. The per-source sample amplitude path (`LOCAL_OSC_A_VOLUME >> 4` × `overallOscAmplitude`) is
crushing sample playback. The parallel agent attempted fixes (commits `13b44f13`, `9f4a104d`,
`7e1fcc77`, `b5b48138` OSC_B/NOISE_VOL=MIN_VALUE, `923e0834` factory param map) but the user still
reports kit too low. Check the sample-playback amplitude in `Voice` (around lines 540–560, the
`sampAmp`/`ampArr` path) and `VoiceSample.render` against the C `voice.cpp` sample branch — a sample
should play near its file level, not 16×-attenuated like an oscillator source.

**Note on the monitor-gain band-aid:** a single fixed driver gain CANNOT serve both -49 dB drums and
-6 dB loud content. The real fix is engine-level amplitude correctness (#1, #3), validated against the
fidelity WAVs. Don't keep cranking the driver gain — it just trades "too quiet" for "clips/distorts."

---

## Done this session (all merged to main + pushed)

| Commit | What |
|---|---|
| `173b7ae7` | Patcher curve-neutral = `getParamNeutralValue(p)` (not the knob) — fixed fw2 synth-voice silence |
| `f3caa3f2` | Master FX bus migrated to firmware2 (Reverb/Delay/Compressor on `int[][]`) |
| `1abb6adc` | Build hotfix: Voice `paramFinalValues` seed (a pulled commit referenced a non-existent field) |
| `345ceac1` | Delay resampling secondary-write: faithful `advance(callback)` (clearAndMoveOn + swap-counter decrement) |
| `8e68afe3` | **Delay no-echo P1 fixed**: `DelayBuffer.K_MAX_SAMPLE_VALUE` was Q31-max, must be `1<<24` (128× off) |
| `d715a71e` | **Reverb silence fixed**: master engine + DSL UGen now call `setPanLevels` (C audio_engine.cpp:836) |
| `367a78f0` | Reverb-send made faithful (volume curve + `cableToLinearParamShortcut >>2`) |
| `62ca8e90` | E2E reverb-send routing test |
| `b6304429` | **Bug 1 fixed** (kit cells all-same-sound; see below) |
| (parallel agent) | `b5b48138` OSC_B/NOISE_VOL=MIN_VALUE, `13b44f13`/`9f4a104d`/`7e1fcc77` kit sample-amp attempts, `64d4298a`/`923e0834` factory param map, `2bc4529e`/`7857b2f4` driver monitor gain 16×→48× |
| `d7eb5e48` | **Fixed "terrible synth"**: driver gain int32 overflow (long math + clamp), 48×→12× |

Master-FX scorecard: **Delay** (was broken→fixed), **Reverb** (was broken→fixed), **Compressor** (verified OK)
— all three had lost their unit tests in the parity-oracle deletion (`6fa73408`); now covered by
`MasterFxRegressionTest` + `ReverbSendRoutingTest`. Suite: **332 passing, 0 failures**.

## Bug 1 — 808 kit cells all play the same sound — FIXED (`b6304429`)

Root cause: the Kit Sound Editor (`SwingKitConfigDialog`) loaded samples via
`BridgeContract.setSamplePath`, which only **stores the path** in a per-track array. Its only consumers
(`g_sample_<idx>` / `G_LOAD_TRIGGER`) live in the **legacy `DelugeEngineDSL`**, not the pure engine the
Swing UI runs. So the live `FirmwareKit` drums never received their samples → all fell back to the
default (sample-less) oscillator → identical sound.

Fix: `SwingDelugeApp.applyKitDrumSampleLive(kit, drumIdx, path)` loads the WAV (`AudioFileReader`) and
applies it to the live `FirmwareKit` drum (`oscType=SAMPLE` + `samples[0]` + `fw2SampleCache[0]`),
mirroring `FirmwareFactory.createKitClip`. The dialog calls it on file-pick. Test: `KitDrumSampleTest`.

## Bug 2 — very low volume even at max — CLOSED (faithful headroom, monitor gain added)

**Final conclusion: the fw2 gain chain is faithful to the C end-to-end. Verified:**

- `overallOscAmplitude = lshiftAndSaturate<2>(multiply_32x32_rshift32(LOCAL_VOLUME, (env0>>1)+1<<30))`
  — matches voice.cpp:984.
- Subtractive no-filter `sourceAmplitude = LOCAL_OSC_A_VOLUME >> 4` — matches voice.cpp:1048.
- Post-filter gain `multiply_32x32_rshift32_rounded(sample, overallOscAmplitude) << 1` — matches voice.cpp:1518 (verified `<< 1`, not `<< 2` as a stale grep once suggested).
- `volumeNeutralValueForUnison = 134217728 / sqrt(numUnison)` matches sound.cpp:3010 (both `134217728` for `numUnison=1`).
- Velocity IS wired (`sourceValues[VELOCITY]` set in `Voice.noteOn`).
- Master chain: fw2 matches C.

A single max-volume synth note peaks at ~0.03 Q31 (~-30 dBFS) — the Deluge's intentional headroom.

**Remedy: monitor gain at `JavaAudioDriver`** (2×, clearly labelled NOT part of the faithful port).
Applies before the 16-bit clamp so it can reach full scale for mixed content. Engine soft-limits to
~0.5 Q31, so 2× → ±1.0, well within 16-bit range for most material.

### How to reproduce / diagnose (throwaway test pattern)
Create `FirmwareAudioEngine` + `FirmwareSound`, `oscTypes[0]=SAW`,
`paramNeutralValues[LOCAL_OSC_A_VOLUME]=Q31.ONE`, `paramNeutralValues[LOCAL_VOLUME]=Q31.ONE`,
`triggerNote(60,127)`, render ~60 blocks, read `engine.masterBuffer[i].l`. Read the live voice via
`sound.fw2Sound.voices.get(0).paramFinalValues[...]` and `.overallOscAmplitudeLastTime`. (Delete the
throwaway test before committing.)

## Other open follow-ups (lower priority)
- Status doc `FIRMWARE2_DSP_PORT_STATUS.md` test count is stale (says 316; actual 332).
- FFT (`FftConfigManager`) is a sanctioned approximation with a weak test — see
  `deluge-remaining-approximations` item 11.
- Deleting `firmware/dsp/*` is done; the legacy `DelugeEngineDSL` (--hifi) still exists and still owns the
  `g_sample_`/`G_LOAD_TRIGGER` kit-load path (now bypassed for the pure engine by `b6304429`).
