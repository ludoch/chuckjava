# Audio Tracks — user guide

Audio tracks let you play and record **audio clips** (WAV samples) on the timeline, alongside
synth/kit/MIDI tracks. They run through the same per-track FX chain (filter, EQ, mod-FX, delay,
reverb send, pan, volume) and the master bus as everything else.

> Status: playback, pitch/time-stretch, transport + arrangement sync, musical-length looping, and
> recording-into-a-track are implemented. The one not-yet-wired piece is *transport-synced loop
> capture* (auto-start/stop recording on the bar) — see [Limitations](#limitations).

## 1. Play an existing audio clip

1. Add an **Audio track** to your song.
2. Point its clip at a WAV file (import, or set the clip's file path).
3. Hit **Play**. The clip streams only while the song is playing and **restarts from the start each
   time you press play** (it's silent when stopped).

By default the clip **loops at its musical length** — i.e. in time with the song tempo, not at the
raw end of the WAV. A 2-bar clip loops every 2 bars; change the song BPM and the loop length tracks
it.

## 2. Pitch and time-stretch

Set the track's **play rate** (and the clip's *pitch/speed independent* flag):

| Mode | Flag | Effect |
|---|---|---|
| **Coupled** (default) | pitch/speed independent = off | Rate changes **pitch and speed together** (like a tape/varispeed — resampled). 1.5× = faster + higher. |
| **Independent** | pitch/speed independent = on | Rate changes **speed only**; pitch is preserved (time-stretch). 0.75× = slower, same pitch. |

(Reverse playback is not currently supported — see Limitations.)

## 3. Arrangement placement

In the arrangement timeline, drop the audio track's clip at one or more positions. Each placement
plays **only while the playhead is inside its bar bounds**, and the clip **restarts at the start of
each placement**. Multiple placements of the same track at different positions all play. The gap
between placements is silent (only the FX tail — reverb/delay — rings out naturally after a clip
ends).

## 4. Record into a track (sampling)

Use the **Threshold Record** dialog (Tools menu):

1. Open Threshold Record. Pick your **target track** from the list — it now includes:
   - **Kit** tracks → records into the selected **drum slot**.
   - **Synth** tracks → records into **oscillator 1** (as a SAMPLE osc, playable pitched).
   - **Audio** tracks → records into the track's **audio clip**.
2. Set the input threshold and **Arm**. Recording starts when the input crosses the threshold and
   the captured WAV is saved to `SAMPLES/RECORDED/`.
3. On finish the engine rebuilds, so the recording is **immediately audible** in its slot/clip.

### Overdub (audio tracks)
If an audio track's clip is in **overdub mode** and already has audio, a new recording is **mixed
over** the existing clip (saturating sum) instead of replacing it — layer takes on top of each other.

## Limitations / not yet wired

- **Transport-synced loop capture:** recording is *threshold-triggered*, not bar-synced. The math to
  capture exactly N bars at tempo exists (`AudioRecordingUtil.syncedCaptureFrames`) but auto
  start/stop on the loop point isn't wired yet (it needs a live input device, so it isn't covered by
  the headless tests).
- **Reverse playback:** no model field for it yet.
- **Time-stretch looping:** independent-mode (time-stretch) clips currently play one-shot per
  trigger; coupled-mode clips loop at musical length.
- **CV/Gate:** out of scope on desktop (no analog jacks).

## For developers
Engine internals, phase breakdown, and the C-firmware mapping are in
[`AUDIO_TRACK_PORT_PLAN.md`](AUDIO_TRACK_PORT_PLAN.md). Key classes: `firmware2.AudioOutput`
(streaming + gating), `FirmwareFactory.createAudioSound` (wiring from the model),
`engine.AudioInputCaptureLine` (recording), `engine.AudioRecordingUtil` (overdub/synced math).
Tests: `AudioOutputPlaybackTest`, `AudioRecordingUtilTest`.
