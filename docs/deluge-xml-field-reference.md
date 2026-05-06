# Deluge XML Field Reference

Every XML element and attribute parsed by `DelugeXmlParser.java`, organized by section.

---

## 1. Song-Level Elements

Parsed from `<song>` element in `parseSong()`.

### Global State

| XML Path | Attribute | Type | Setter | Notes |
|----------|-----------|------|--------|-------|
| `<song>` | `bpm` | float(hex) | `setBpm` | | |
| `<song>` | `swing` | float(hex) | `setSwing` | | |
| `<song>` | `timeSigNum` | int(hex) | `setTimeSigNum` | | |
| `<song>` | `timeSigDenom` | int(hex) | `setTimeSigDenom` | | |
| `<song>` | `transpose` | int(hex) | `setTranspose` | | |
| `<song>` | `humanize` | float(hex) | `setHumanize` | | |
| `<song>` | `key` | text | `setKey` | e.g. "0" (C) | |
| `<song>` | `scale` | text | `setScale` | e.g. "Major" | |
| `<presetScale>` | | text | n/a | not yet mapped | |

### Master FX

| XML Path | Attributes | Type | Setter | Notes |
|----------|------------|------|--------|-------|
| `<reverb>` | `roomSize`, `dampening`, `width`, `hpf`, `pan`, `model` | float(hex)/int | `setReverb*` | Nested `<compressor>` with attack/release/syncLevel |
| `<delay>` | `pingPong`, `analog`, `syncLevel`, `syncType` | int | `setDelay*` | |
| `<sidechain>` | `attack`, `release` | float(hex) | `setSidechain*` | |
| `<audioCompressor>` | `attack`, `release`, `threshold`, `ratio` | float(hex) | `setCompressor*` | |

### `<songParams>` Element

Child element with ~20 hex attributes:

| Attribute | Type | Setter |
|-----------|------|--------|
| `volume` | float(hex) | `setSongParamVolume` |
| `pan` | float(hex) | `setSongParamPan` |
| `lpfFrequency`, `lpfResonance` | float(hex) | `setSongParamLpf*` |
| `hpfFrequency`, `hpfResonance` | float(hex) | `setSongParamHpf*` |
| `reverbAmount` | float(hex) | `setSongParamReverbAmount` |
| `delayRate`, `delayFeedback` | float(hex) | `setSongParamDelay*` |
| `sidechainAttack`, `sidechainRelease` | float(hex) | `setSongParamSidechain*` |
| `compressorAttack`, `compressorRelease`, `compressorThreshold`, `compressorRatio` | float(hex) | `setSongParamCompressor*` |
| `modFXRate`, `modFXDepth`, `modFXFeedback` | float(hex) | `setSongParamModFX*` |
| `stutterRate` | float(hex) | `setSongParamStutterRate` |
| `sampleRateReduction` | float(hex) | `setSongParamSampleRateReduction` |
| `bitCrush` | float(hex) | `setSongParamBitCrush` |
| `eqBass`, `eqTreble`, `eqBassFrequency`, `eqTrebleFrequency` | float(hex) | `setSongParamEq*` |
| `modFXOffset` | float(hex) | `setSongParamModFXOffset` |

Nested children: `<delay>`, `<lpf>`, `<hpf>`, `<equalizer>` (child elements with hex attributes).

### Sections

`<sections>` → 12 `<section>` elements, each with:

| Child/Attr | Type | Model field |
|------------|------|-------------|
| name | text | SongSection id |
| colour (child) | text | (not mapped) |
| repeatCount | int | `setNumRepeats` |
| loopToSection | int | `setLoopToSection` |
| linkToSection | int | `setLinkToSection` |

Sections hold child `<pattern>` references (`patternId` attribute).

### Arranger Timeline

`<arrangerTimeline>` → `<arrangerClip>` elements → `ArrangerClip` model.

---

## 2. Synth Track (`<instrumentClip isKitClip="false">`)

Parsed via `populateSynth()`.

### Oscillators

| XML Path | Attribute/Child | Type | Setter |
|----------|-----------------|------|--------|
| `<osc1>` | `type` attr | text | `setOsc1Type` |
| `<osc1>` | `dx7patch` attr | text | `setDx7Patch` |
| `<osc2>` | `type` child | text | `setOsc2Type` |

### Synth Mode

| XML Path | Child text | Values | Setter |
|----------|-----------|--------|--------|
| `<mode>` | text | `"fm"`, `"ringmod"`, else subtractive | `setSynthMode` (0/1/2) |

### Polyphony

| XML Path | Child text | Values | Setter |
|----------|-----------|--------|--------|
| `<polyphony>` | text | `"mono"`, `"legato"`, else poly | `setPolyphony` |

### Filter

| XML Path | Child text | Values | Setter |
|----------|-----------|--------|--------|
| `<lpfMode>` | text | `"12dB"`, `"24dB"`, `"SVF"` | `setFilterMode` |

### FM Modulators

| Child tag | Attr | Type | Setter |
|-----------|------|------|--------|
| `<modulator1>` | `transpose` | int | (used for fmRatio) |
| `<modulator1>` | `feedback` | float(hex) | `setModulator1Feedback` |
| `<modulator2>` | `amount` | float(hex) | `setModulator2Amount` |
| `<modulator2>` | `feedback` | float(hex) | `setModulator2Feedback` |
| `<carrier1>` | `feedback` | float(hex) | `setCarrier1Feedback` |
| `<carrier2>` | `feedback` | float(hex) | `setCarrier2Feedback` |

### Envelopes 0-3

`<envelope>` elements (direct children of `<sound>`):

| Attr | Type | Model field |
|------|------|-------------|
| `attack` | float(hex) | env.attack |
| `decay` | float(hex) | env.decay |
| `sustain` | float(hex) | env.sustain |
| `release` | float(hex) | env.release |

Also supports child-element format:
```xml
<envelope>
  <attack>0x...</attack>
  <decay>0x...</decay>
  ...
</envelope>
```

### LFOs 1-2

`<lfo1>`, `<lfo2>` child elements:

| Attr | Child | Type | Model field |
|------|-------|------|-------------|
| `type` | `<type>` | text | waveform (LfoType) |
| `rate` | `<rate>` | hex Hz | rateHz |
| `syncLevel` | `<syncLevel>` | int | syncLevel |
| (depth via attribute only) | | | depth (abs) |

### Arpeggiator

`<arpeggiator>` element (child-element format):

| Child tag | Type | Model field |
|-----------|------|-------------|
| `<mode>` | text | arp.mode |
| `<rate>` | float(hex) | arp.rate |
| `<octaves>` | int | arp.octaves |
| `<gate>` | float(hex) | arp.gate |
| `<syncLevel>` | int | arp.syncLevel |

### Compressor

`<compressor>` child element (attribute format):

| Attr | Type | Setter |
|------|------|--------|
| `attack` | float(hex, abs) | `setCompressorAttack` |
| `release` | float(hex, abs) | `setCompressorRelease` |
| `syncLevel` | int | `setCompressorSyncLevel` |

### Patch Cables

`<patchCables>` container with `<patchCable>` children inside **or** nested inside `<defaultParams>`:

| Child tag | Type | Model field |
|-----------|------|-------------|
| `<source>` | text | PatchCable.source |
| `<destination>` | text | PatchCable.destination |
| `<amount>` | hex float | PatchCable.amount (scaled: quadratic for PITCH) |

### Mod Knobs

`<modKnobs>` container with `<modKnob>` children. Each `<modKnob>` has:

| Child tag | Type | Model field |
|-----------|------|-------------|
| `<controlsParam>` | text | ModKnob.param |
| `<patchSource>` or `<patchAmountFromSource>` | text | ModKnob.patchSource |

### defaultParams Bindings (Synth)

Read from `<sound><defaultParams>` child elements using `FieldBinding` framework:

| Child tag | Converter | Setter | Notes |
|-----------|-----------|--------|-------|
| `lpfFrequency` | hexToHz | `setLpfFreq` | |
| `lpfResonance` | hexToFloat(abs) | `setLpfRes` | |
| `hpfFrequency` | hexToHz | `setHpfFreq` | |
| `hpfResonance` | hexToFloat(abs) | `setHpfRes` | |
| `modulator1Amount` | hexToFloat(abs) | `setFmAmount` | |
| `modulator1Feedback` | hexToFloat(abs) | `setModulator1Feedback` | |
| `modulator2Amount` | hexToFloat(abs) | `setModulator2Amount` | |
| `modulator2Feedback` | hexToFloat(abs) | `setModulator2Feedback` | |
| `carrier1Feedback` | hexToFloat(abs) | `setCarrier1Feedback` | |
| `carrier2Feedback` | hexToFloat(abs) | `setCarrier2Feedback` | |
| `oscAVolume` | hexToFloat(abs) | `setOscMix` | |
| `noiseVolume` | hexToFloat(abs) | `setNoiseVol` | |
| `volume` | hexToFloat(abs) | `setVolume` | |
| `pan` | hexToFloat(abs) | `setPan` | **sign loss from abs** |
| `portamento` | hexToFloat(abs) | `setPortamento` | |
| `modFXRate` | hexToFloat(abs) | `setModFxRate` | |
| `modFXDepth` | hexToFloat(abs) | `setModFxDepth` | |
| `modFXFeedback` | hexToFloat(abs) | `setModFxFeedback` | |
| `reverbAmount` | hexToFloat(abs) | `setReverbSend` | |
| `delayRate` | hexToFloat(abs) | `setDelaySend` | |
| `stutterRate` | hexToFloat(abs) | `setStutterRate` | |
| `sampleRateReduction` | hexToFloat(abs) | `setSampleRateReduction` | |
| `bitCrush` | hexToFloat(abs) | `setBitCrush` | |

### Direct Bindings (Synth)

| Child tag | Value | Setter |
|-----------|-------|--------|
| `<modFXType>` | text(upper) | `setModFxType` |
| `<polyphonic>` | text(upper) | `setPolyphony` |
| `<mode>` | text | `setSynthMode` |
| `<lpfMode>` | text(upper) | `setFilterMode` |

---

## 3. Kit Track (`<instrumentClip isKitClip="true">`)

Parsed via `parseKitElement()` → `parseKitSound()`.

### Per-Sound Fields

Read from `<sound>` child elements of `<presetSlot>`:

#### Root Attributes

| Attr | Type | Setter |
|------|------|--------|
| `polyphonic` | int(0/1) | `setPolyphonic` |
| `voicePriority` | int | `setVoicePriority` |
| `clippingAmount` | float(hex, abs) | `setClippingAmount` |
| `sideChainSend` | float(hex, abs) | `setSidechainSend` |
| `modFXType` | text | `setModFxType` |
| `lpfMode` | text | parsed to `FilterMode` enum |
| `hpfMode` | text | `setHpfMode` |

#### Basic Sound Attributes

| XML Path | Format | Model field |
|----------|--------|-------------|
| `<name>` | child text | name |
| `<osc1><fileName>` | child text | samplePath |
| `<osc1><reversed>` | child int | reverse |
| `<zone><startMilliseconds>` | child float | startMs |
| `<zone><endMilliseconds>` | child float | endMs |
| `<muteGroup>` | child int | muteGroup |

#### Osc2 Type

`<osc2 type="...">` attribute → `setOsc2Type`.

#### Unison

`<unison>` element:

| Attr | Type | Setter |
|------|------|--------|
| `num` | int | `setUnisonNum` |
| `detune` | hex float | `setUnisonDetune` |

#### LFOs

`<lfo1>`, `<lfo2>` elements:

| Attr | Child | Type | Setter |
|------|-------|------|--------|
| `type` | `<type>` | text | LfoType parsed via `parseLfoType()` |
| `rate` | `<rate>` | hex Hz | rateHz |
| `syncLevel` | `<syncLevel>` | int | syncLevel |
| (depth via attribute only) | | float(hex, abs) | depth |

`parseLfoType()` handles: SINE, TRIANGLE, SQUARE, SAW, S_AND_H (s&h, s&amp;h), RANDOM_WALK (randomWalk, randomwalk), DECAY, RAMP, ORGANIC, PINK, NOISE, WARBLER, EVEN, ODD, BROWN.

#### Delay

`<delay>` element (attr format):

| Attr | Type | Setter |
|------|------|--------|
| `rate` | float(hex) | `setDelayRate` |
| `feedback` | float(hex) | `setDelayFeedback` |

#### Compressor

`<audioCompressor>` element (attr format):

| Attr | Type | Setter |
|------|------|--------|
| `attack` | float(hex) | `setCompressorAttack` |
| `release` | float(hex) | `setCompressorRelease` |
| `syncLevel` | int | `setCompressorSyncLevel` |

#### Arpeggiator

`<arpeggiator>` element:

| Attr | Type | Setter |
|------|------|--------|
| `gate` | float(hex) | `setArpeggiatorGate` |

#### ModFX

| XML Path | Format | Setter |
|----------|--------|--------|
| `<modulationFXType>` or root `modFXType` attr | text | `setModFxType` |

#### Patch Cables

`<patchCables>` container with `<patchCable>` children, searched:
1. Direct child of `<sound>` (only direct children, not nested)
2. Inside `<defaultParams>` (via `parseKitCablesFromContainer`)

Each `<patchCable>` uses `<source>`, `<destination>`, `<amount>` child elements.

#### Mod Knobs

`<modKnobs>` container with `<modKnob>` children. Each `<modKnob>`:

| Child element | Type | Model field |
|---------------|------|-------------|
| `<controlsParam>` | text | ModKnob.param |
| `<patchSource>` or `<patchAmountFromSource>` | text | ModKnob.patchSource |

#### Envelopes 1-4 (inside defaultParams)

`<defaultParams>` → `<envelope1>` through `<envelope4>`:

| Child | Type | Model field |
|-------|------|-------------|
| `<attack>` | hex float | env.attack |
| `<decay>` | hex float | env.decay |
| `<sustain>` | hex float | env.sustain |
| `<release>` | hex float | env.release |

#### EQ (inside defaultParams)

`<defaultParams>` → `<equalizer>`:

| Child | Type | Setter |
|-------|------|--------|
| `<bass>` | hex float (abs) | `setEqBass` |
| `<treble>` | hex float (abs) | `setEqTreble` (**sign loss**) |

#### LFO Rates (inside defaultParams)

| Child | Type | Setter |
|-------|------|--------|
| `<lfo1Rate>` | hex float (normalized to 0-1) | `setLfo1RateHz` |
| `<lfo2Rate>` | hex float (normalized to 0-1) | `setLfo2RateHz` |

#### Default Params (kit sound)

| Child | Type | Setter |
|-------|------|--------|
| `<volume>` | hex float (abs) | `setVolume` |
| `<pan>` | hex float (abs) | `setPan` (**sign loss**) |
| `<oscAVolume>` | hex float (abs) | `setOscAVolume` |
| `<oscBVolume>` | hex float (abs) | `setOscBVolume` |
| `<noiseVolume>` | hex float (abs) | `setNoiseVolume` |
| `<arpeggiatorGate>` | hex float (abs) | `setArpeggiatorGate` |
| `<portamento>` | hex float (abs) | `setPortamento` |
| `<stutterRate>` | hex float (abs) | `setStutterRate` |
| `<sampleRateReduction>` | hex float (abs) | `setSampleRateReduction` |
| `<bitCrush>` | hex float (abs) | `setBitCrush` |
| `<fmAmount>` | hex float (abs) | `setFmAmount` |
| `<reverbAmount>` | hex float (abs) | `setReverbAmount` |

---

## 4. Per-NoteRow `<soundParams>` Overrides

Inside `<instrumentClip>`, each `<noteRow>` may have a `<soundParams>` child element.
Parsed into `ClipModel.rowSoundParams` (Map<Integer, Map<String, Float>>).

Attributes parsed (all hex float):

`arpeggiatorGate`, `portamento`, `compressorShape`, `oscAVolume`, `oscBVolume`, `noiseVolume`, `volume`, `pan`, `lpfFrequency`, `lpfResonance`, `hpfFrequency`, `hpfResonance`, `lfo1Rate`, `lfo2Rate`, `modulator1Amount`, `modulator1Feedback`, `modulator2Amount`, `modulator2Feedback`, `carrier1Feedback`, `carrier2Feedback`, `modFXRate`, `modFXDepth`, `modFXOffset`, `modFXFeedback`, `delayRate`, `delayFeedback`, `reverbAmount`, `stutterRate`, `sampleRateReduction`, `bitCrush`.

Plus child elements: `<envelope1>`–`<envelope4>`, `<equalizer>` (bass/treble/frequency), `<patchCables>`.

---

## 5. Hex Encoding Notes

All float values in Deluge XML are stored as hex strings representing a 32-bit integer:

| Hex Format | Meaning | Conversion |
|------------|---------|------------|
| `0x00000000` | 0.0 | `(int)value / Integer.MAX_VALUE` |
| `0x40000000` | 0.5 | |
| `0x7FFFFFFF` | 1.0 | |
| `0x80000000` | -1.0 | (hex unsigned → sign-extended) |
| `0xE6666660` | -0.2 | |

Frequency values (lpfFrequency, hpfFrequency) use exponential mapping:
```
freq = 20 × 1000^((norm+1)/2)  where norm = hexToFloat
```

**Known limitation:** `readHexFloat` applies `Math.abs()` (unipolar), so negative values like `pan=-0.2` or `eqTreble=-0.2` lose their sign on re-parse. This matches the firmware behavior where most parameters are unipolar, but pan and EQ treble are technically bipolar.

---

## 6. Audio Track (`<audioTrack>`)

Parsed inside `<instruments>` alongside `<sound>` and `<kit>`. Audio clips appear in `<sessionClips>` as `<audioClip>` children matched to tracks by `trackName`.

### Audio Track Model Fields

| Field | Type | Default | XML Source | Setter |
|-------|------|---------|------------|--------|
| `looping` | boolean | false | `<audioTrack looping="...">` | `setLooping` |
| `playRate` | float | 1.0 | `<audioTrack playRate="...">` | `setPlayRate` |
| `recording` | boolean | false | `<audioTrack recording="...">` | `setRecording` |
| `playing` | boolean | false | `<audioTrack playing="...">` | `setPlaying` |

### Audio Clip Fields

Each `<audioClip>` inside `<sessionClips>` is matched to its parent `<audioTrack>` by `trackName` attribute:

| Attribute | Type | Default | Setter | Notes |
|-----------|------|---------|--------|-------|
| `trackName` | text | null | `setTrackName` | Links clip to parent audioTrack |
| `filePath` | text | null | `setFilePath` | Relative path to WAV file |
| `startSamplePos` | int | 0 | `setStartSamplePos` | Sample playback start (0=beginning) |
| `endSamplePos` | int | 0 | `setEndSamplePos` | Sample playback end (0=end of file) |
| `attack` | float | 0.0 | `setAttack` | Envelope attack |
| `priority` | int | 1 | `setPriority` | Voice priority |
| `pitchSpeedIndependent` | boolean | false | `setPitchSpeedIndependent` | |
| `overdubsShouldCloneAudioTrack` | boolean | false | `setOverdubsShouldCloneAudioTrack` | |
| `isPlaying` | boolean | false | `setPlaying` | |
| `isSoloing` | boolean | false | `setSoloing` | |
| `isArmedForRecording` | boolean | false | `setArmedForRecording` | |
| `length` | int | 768 | `setLength` | Tick length |
| `colourOffset` | int | 0 | `setColourOffset` | |
| `section` | int | 0 | `setSection` | Session section |
| `beingEdited` | boolean | false | `setBeingEdited` | |

### Per-Clip Params (`<params>` child element)

Each `<audioClip>` may have a `<params>` child element with hex-encoded float attributes:

| Attribute | Type | Default | Setter |
|-----------|------|---------|--------|
| `volume` | float(hex) | 1.0 | `setVolume` |
| `pan` | float(hex) | 0.0 | `setPan` (**sign loss from abs**) |
| `reverbAmount` | float(hex) | 0.0 | `setReverbAmount` |
| `sidechainShape` | float(hex) | 0.0 | `setSidechainShape` |
| `modFXRate` | float(hex) | 0.0 | `setModFXRate` |
| `modFXDepth` | float(hex) | 0.0 | `setModFXDepth` |
| `modFXOffset` | float(hex) | 0.0 | `setModFXOffset` |
| `modFXFeedback` | float(hex) | 0.0 | `setModFXFeedback` |
| `stutterRate` | float(hex) | 0.0 | `setStutterRate` |
| `sampleRateReduction` | float(hex) | 0.0 | `setSampleRateReduction` |
| `bitCrush` | float(hex) | 0.0 | `setBitCrush` |
| `delayRate` | float(hex) | 0.0 | `setDelayRate` |
| `delayFeedback` | float(hex) | 0.0 | `setDelayFeedback` |
| `lpfFrequency` | float(hex) | 20000.0 | `setLpfFrequency` |
| `lpfResonance` | float(hex) | 0.0 | `setLpfResonance` |
| `hpfFrequency` | float(hex) | 20.0 | `setHpfFrequency` |
| `hpfResonance` | float(hex) | 0.0 | `setHpfResonance` |
| `eqBass` | float(hex) | 0.0 | `setEqBass` |
| `eqTreble` | float(hex) | 0.0 | `setEqTreble` (**sign loss**) |
| `eqBassFrequency` | float(hex) | 0.0 | `setEqBassFrequency` |
| `eqTrebleFrequency` | float(hex) | 0.0 | `setEqTrebleFrequency` |

### Audio File Resolution

In `SequencerMain.pushProjectToBridge()`, the `filePath` is resolved via `resolveSamplePath()` using the same logic as kit samples (searches `SAMPLES/` and XML-local directories). The `startSamplePos`/`endSamplePos` are normalized to 0..1 range by `BridgeContract.computeAudioClipRange()`, which reads the WAV file header to determine total sample count.

---

## 7. Coverage Gaps

Tags present in XML song files that the parser does not currently handle:

| XML Tag | Files Affected | Description | Priority |
|---------|---------------|-------------|----------|
| `modeNote` / `modeNotes` | 31/31 (all songs) | Scale note mask — defines which scale degrees are active | **Medium** |
| `selectedDrumIndex` | 13 files | Kit UI state — which drum pad is selected in the editor | Low (UI only) |
| `soundSources` | 13 files | Container for kit `<sound>` elements (newer format) | **Medium** (alternative to `<presetSlot>`) |
| `kitParams` | 14 files | Per-kit track params (volume, pan, FX, EQ, delay) | **Medium** |
| `bendRange` | 2 files | MPE bend range semitones | Low |
| `bendRangeMPE` | 2 files | MPE bend range for MPE channels | Low |
| `columnControls` / `leftCol` / `rightCol` | 6 files | UI column layout for kit grid editor | Low (UI only) |
| `depthControlledBy` | 2 files | Modulation depth source routing | Low |
| `sampleRange` / `sampleRanges` | 1 file | Alternative zone format for sample ranges | Low |
| `songCompressor` | 1 file | Song-level compressor (variant of `<audioCompressor>`) | **Low** (rare) |
| `osc2` | 30 files | Second oscillator type/params parsed for synth, NOT for kit sounds | **Medium** (kit sounds use osc2 for dual-layer) |
| `modulator2` | 10 files | FM modulator 2 — parsed for synth directly, but not inside kit `defaultParams` | Low for kit |
| `unison` (kit inner) | 13 files | Unison settings parsed for kit sounds via `parseKitSound()`, but only when `<unison>` is direct child | Low |

### Functional Impact Summary

1. **modeNotes** (31/31 files): The scale mode notes define which notes are "in key" for the song grid. Without parsing, the grid display can't show which pads should be lit. Affects all songs.
2. **soundSources** (13 files): Some kit files use `<soundSources>` instead of/alongside `<presetSlot>`. The parser only reads `<presetSlot>` for kit sounds. Affected songs may have unread drum sounds.
3. **kitParams** (14 files): Kit-level params (volume, pan, EQ, delay, FX) are not applied. These are per-track mix settings, not per-sound, so they affect the overall kit mix.
4. **osc2 for kit sounds** (30 files): Kit sounds with dual-layer samples (osc1 + osc2) don't have the second layer's zone/sample data parsed. Affects most kit-based songs.
5. **songCompressor** (1 file, SONG006667): A variant song compressor element that differs from `<audioCompressor>`.

### Likely-to-Never-Parse (UI State)

These are UI state elements that don't affect playback:
- `selectedDrumIndex`
- `columnControls`, `leftCol`, `rightCol`
- `colour`, `beingEdited`, `colourOffset`
