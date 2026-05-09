# Engine Read-Coverage Model

## Overview

The Deluge audio engine (`DelugeEngineDSL`) reads sequencer state from the `BridgeContract` via ChuckVM global objects (wrapping Java float[]/int[] arrays) and writes per-track runtime values as indexed globals (`G_XXX_<trackN>`). This document catalogs every data path: the bridge array, how the engine reads it, and whether it is tested.

## Data Path Types

### 1. Direct UGen binding (no per-track global)

The engine reads a bridge array and immediately applies the value to a UGen parameter. No per-track global is written.

| Bridge global | Engine location | Applied to | Test |
|---|---|---|---|
| `G_KIT_VOLUME` | KitShred line 766 | `kit[r].gain()` | E2E (indirect, via audio output) |
| `G_KIT_PAN` | KitShred line 767 | `pan[r].pan()` | E2E (indirect) |
| `G_DELAY_SEND` | KitShred line 764 | `dSend[r].gain()` | E2E |
| `G_REVERB_SEND` | KitShred line 765 | `rSend[r].gain()` | E2E |
| `G_TRACK_LEVEL` | KitShred line 808 | (step trigger gain) | E2E |
| `G_PAN` | SynthShred line 1192 | `pan[r].pan()` | E2E |

### 2. Per-track global writes (SynthData → SynthShred)

The SynthShred writes per-track globals for each active synth track (`synthBase..maxSynthBridgeRow`). Each global is keyed as `G_XXX + "_" + r`. Tested in `testSynthDataPerTrackGlobals`.

| Bridge global | Engine per-track global written | Test assertion |
|---|---|---|
| `G_MOD1_FB` → `mod1FbArr` | `G_MOD1_FB + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_NOISE_VOL` → `noiseVolArr` | `G_NOISE_VOL + "_" + r` | (indirect, same loop) |
| `G_OSC_MIX` → `oscMixArr` | `G_OSC_MIX + "_" + r` | (indirect) |
| `G_UNISON_NUM` → `unisonNumArr` | `G_UNISON_NUM + "_" + r` | (indirect) |
| `G_UNISON_DETUNE` → `unisonDetuneArr` | `G_UNISON_DETUNE + "_" + r` | (indirect) |
| `G_MOD_FX_TYPE` → `modFxTypeArr` | `G_MOD_FX_TYPE + "_" + r` | (indirect) |
| `G_MOD_FX_RATE` → `modFxRateArr` | `G_MOD_FX_RATE + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_MOD_FX_DEPTH` → `modFxDepthArr` | `G_MOD_FX_DEPTH + "_" + r` | (indirect) |
| `G_MOD_FX_FEEDBACK` → `modFxFeedbackArr` | `G_MOD_FX_FEEDBACK + "_" + r` | (indirect) |
| `G_PORTAMENTO` → `portamentoArr` | `G_PORTAMENTO + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_EQ_BASS` → `eqBassArr` | `G_EQ_BASS + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_EQ_TREBLE` → `eqTrebleArr` | `G_EQ_TREBLE + "_" + r` | (indirect) |
| `G_STUTTER_RATE` → `stutterRateArr` | `G_STUTTER_RATE + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_SAMPLE_RATE_RED` → `sampleRateRedArr` | `G_SAMPLE_RATE_RED + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_BITCRUSH` → `bitCrushArr` | `G_BITCRUSH + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_COMP_ATTACK` → `compAttackArr` | `G_COMP_ATTACK + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_COMP_RELEASE` → `compReleaseArr` | `G_COMP_RELEASE + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_MOD2_AMT` → `mod2AmtArr` | `G_MOD2_AMT + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_MOD2_FB` → `mod2FbArr` | `G_MOD2_FB + "_" + r` | `testSynthDataPerTrackGlobals` |
| `G_CARRIER2_FB` → `carrier2FbArr` | `G_CARRIER2_FB + "_" + r` | `testSynthDataPerTrackGlobals` |

### 3. Per-track global writes (KitData → KitShred)

The KitShred writes per-track globals for ALL tracks (`BridgeContract.TRACKS`) regardless of `kit.length` (which only counts rows with loaded samples). Tested in `testKitDataPerTrackGlobals`.

| Bridge global | Engine per-track global written | Test assertion |
|---|---|---|
| `G_KIT_HPF_FREQ` → `kitHpfFreqArr` | `G_KIT_HPF_FREQ + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_HPF_RES` → `kitHpfResArr` | `G_KIT_HPF_RES + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_NOISE_VOL` → `kitNoiseVolArr` | `G_KIT_NOISE_VOL + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_EQ_BASS` → `kitEqBassArr` | `G_KIT_EQ_BASS + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_EQ_TREBLE` → `kitEqTrebleArr` | `G_KIT_EQ_TREBLE + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_SIDECHAIN` → `kitSidechainArr` | `G_KIT_SIDECHAIN + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_MOD_FX_TYPE` → `kitModFxTypeArr` | `G_KIT_MOD_FX_TYPE + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_STUTTER_RATE` → `kitStutterRateArr` | `G_KIT_STUTTER_RATE + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_SAMPLE_RATE_RED` → `kitSampleRateRedArr` | `G_KIT_SAMPLE_RATE_RED + "_" + r` | `testKitDataPerTrackGlobals` |
| `G_KIT_BITCRUSH` → `kitBitCrushArr` | `G_KIT_BITCRUSH + "_" + r` | `testKitDataPerTrackGlobals` |

### 4. Sound parameters (per-track arrays, not engine-written)

These bridge arrays hold per-sound data but are read by the UI/FX bus directly (not written as per-track globals by the engine):

| Bridge global | Purpose |
|---|---|
| `G_KIT_COMP_ATTACK` | Read by KitShred for FX bus, not written as per-track global |
| `G_KIT_COMP_RELEASE` | Same |
| `G_KIT_DELAY_RATE` | Sound-level delay rate |
| `G_KIT_DELAY_FB` | Sound-level delay feedback |

## ChuckArray Backing Model

- `float[]`-backed `ChuckArray`: `getFloat(index)` returns `backingFloat[index]` directly (zero-copy).
- `int[]`-backed `ChuckArray`: `getFloat(index)` casts `(double) backingInt[index]`.
- The backing arrays are the same objects that `BridgeContract` exposes via `getXxxRaw()`, so in-process mutations are immediately visible to the engine.

## Test Patterns

### Integration per-track tests (`DelugeEngineTest`)

1. G_RELOAD trigger (forces engine re-init for SynthShred discovery of synth tracks)
2. Set distinct values on each array element for each track
3. Start playback (G_PLAY=1) with a step active
4. Advance 44100*3 samples (~3 seconds)
5. Assert per-track globals via `vm.getGlobalFloat(G_XXX + "_" + track)`

### Unit array tests

- `testChuckArrayIntBackedGetFloat` — verify int[]→getFloat() returns correct double values, bounds, and negative indexing
- `testChuckArrayFloatBackedGetFloat` — verify float[]→getFloat() returns correct values, bounds, and negative indexing

## Notable Design Points

- **kit.length ≠ BridgeContract.TRACKS**: The KitShred's `kit[]` array is sized to match the count of kit-type tracks that have sample strings (not all TRACKS rows). The per-track global write loop uses `BridgeContract.TRACKS` to cover all tracks regardless of sample loading.
- **SynthShred iteration is slot-based**: Only tracks within `synthBase..maxSynthBridgeRow` (where trackType=1) are processed.
- **Pan is NOT a per-track global**: `G_PAN` is read directly from the bridge array and applied to the UGen. It is not stored as `G_PAN_<r>`.
- **kitVolume/kitPan**: Applied directly to UGens (not stored as per-track globals), consistent with the real Deluge firmware behavior.
