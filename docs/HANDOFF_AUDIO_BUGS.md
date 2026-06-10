# Handoff — audio bugs + master-FX work (2026-06-10)

Live status of the Mac-reported audio bugs and the master-FX work around them. Read with
`FIRMWARE2_DSP_PORT_STATUS.md` and the memories `fw2-master-delay-no-echo-bug`,
`deluge-remaining-approximations`.

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
| `b6304429` | **Bug 1 fixed** (see below) |

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
