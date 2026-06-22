# Audio Track (`AudioOutput`) port — scope & plan

## Why
Audio tracks are the one major Deluge instrument type still missing from the engine. The model
(`org.deluge.model.AudioTrackModel` + its `AudioClip`) exists, but `FirmwareFactory.createAudioSound()`
is a **stub** (returns an empty `FirmwareSound`), so audio clips produce no sound and the timeline
audio-clip workflow (play / loop / time-stretch / pitch / record / overdub) doesn't function.

This is also the foundation for **live looping** — recording the input into an audio clip and looping
it — which is built on the same `AudioOutput` streaming path.

## Good news: the DSP is already ported — this is integration, not new DSP
The C audio path (`model/clip/audio_clip.cpp` `AudioClip::render`, `model/audio_output.cpp`) drives a
`VoiceSample` over a `SampleHolder` with a `SamplePlaybackGuide` and a `TimeStretcher`. Every one of
those is already a faithful firmware2 class:

| C piece | firmware2 (already ported) |
|---|---|
| `VoiceSample` (resampled/stretched playback) | `org.deluge.firmware2.VoiceSample` |
| sample read + sinc/linear interp | `SampleReader`, `SincInterpolator` |
| time-stretch / independent pitch | `TimeStretcher`, `LivePitchShifter` |
| audio file + start/end/loop/reverse | `Sample`, `SampleHolder`, `SamplePlaybackGuide` |
| per-track FX (filter/EQ/modFX/delay/reverb-send/pan/volume) | `GlobalEffectable` (audio tracks extend it on hardware) |
| input capture for recording | `AudioInputCaptureLine` (already records → WAV, used by the threshold sampler) |

So the work is wiring these together behind a new `AudioOutput`, not writing DSP.

## Design

### New class: `org.deluge.firmware2.AudioOutput extends GlobalEffectable`
Mirrors the C `AudioOutput`/`AudioClip` pairing. Holds:
- a `SampleHolder` (the clip's audio file + start/end/loop points, reverse flag),
- a `VoiceSample` (the playback head) + `SamplePlaybackGuide`,
- playback params: `phaseIncrement` (pitch), `timeStretchRatio` (independent of pitch),
  loop on/off.

`renderInternal(int[] trackBuffer, numSamples, reverbBuffer)` (the `GlobalEffectable` hook the engine
already calls per track per block): if a clip is active, advance the `VoiceSample` and write its
output into `trackBuffer`; `GlobalEffectable` then applies the track FX chain + sums to master — so
audio tracks get filters/EQ/mod-FX/delay/reverb-send/pan/volume **for free**, exactly like synth/kit
tracks.

### Wire-up: `FirmwareFactory.createAudioSound`
Replace the stub: build an `AudioOutput` from `AudioTrackModel` — resolve the `AudioClip` file path
to a `Sample`, set start/end/loop/reverse, pitch (`playRate`/transpose), time-stretch flag, and the
track's volume/pan/FX params (same param plumbing kits/synths use).

### Transport / timeline
- **Session/clip view:** an audio clip launches like an instrument clip; on launch, start the
  `VoiceSample` at the clip start, looping over the clip length, synced to the transport.
- **Arrangement view:** clip *instances* are placed at absolute bars; the engine starts/stops the
  `AudioOutput` as the playhead enters/leaves each instance (map transport tick → sample frame via
  BPM + sample rate, the same clocking `FidelitySongSmokeTest` uses).

## Phasing (ship incrementally, test each)
1. **Playback of a loaded clip — ✅ DONE.** `AudioOutput extends GlobalEffectable` streams a `Sample`
   (start→end, loop) at unity pitch via `VoiceSample`, summed + FX'd by `GlobalEffectable`;
   `createAudioSound` builds it from `AudioTrackModel`'s first audio clip. Verified non-silent by
   `AudioOutputPlaybackTest` (RMS ~0.017, no clipping). Loudness is governed by the downstream
   post-FX volume + master compressor; wiring the per-track volume param is a small later refinement.
2. **Pitch + time-stretch + reverse** — feed `playRate`/transpose to `phaseIncrement` and the
   `TimeStretcher` ratio (reuse `VoiceSample`'s native-vs-resampled selection). Verify pitched clip
   renders and matches expected duration.
3. **Transport sync + arrangement placement** — start/stop on playhead crossing clip-instance
   bounds; loop at clip length in session view.
4. **Live recording / overdub** — reuse `AudioInputCaptureLine` to record input into the clip's file
   at loop start (the capture→WAV path already works for the threshold sampler), then it streams via
   phase 1. Overdub = mix new input with the existing clip.

## Risks / notes
- `AudioOutput` is a `GlobalEffectable` subclass that does NOT render synth voices — keep
  `renderInternal` purely sample-streaming so the silence early-out and FX path behave.
- Looping/boundary handling: `SampleReader` currently notes "loop/jump-back deferred to later
  increments" — phase 1 can do one-shot + simple loop; gapless/crossfaded loop may need the C
  `audio_clip.cpp` boundary logic ported.
- Faithfulness: `AudioOutput`/`AudioClip` are firmware2 → port the C structure, cite file:line, keep
  fixed-point math exact (the reused DSP already is).

## Effort
Phase 1 (audible playback): small–moderate (a new `AudioOutput` + un-stub `createAudioSound` + one
test) since all DSP is reused. Phases 2–4 are incremental. CV remains out of scope (no analog jacks).
