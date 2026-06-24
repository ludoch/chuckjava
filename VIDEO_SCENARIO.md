# Deluge Sequencer Bootcamp Video Scenario (WIP)

> [!IMPORTANT]
> **Status: Work in Progress (WIP)**
> This document details the exact script, timeline, and automation sequence used to record the Deluge Sequencer demonstration video. It is currently under active development and requires further refinement of voiceover transitions, additional multi-track orchestration, and final video rendering parameters.

---

## 🎬 Video Concept & Goals

The goal of this video is to demonstrate the high-fidelity integration of the ChucK-Java DSP audio engine with the isomorphic Deluge Swing UI workstation. It walks the user through basic note entry, vertical transposing, note length modification, track length duplication, grid zooming, and generative probability step programming.

---

## ⏱️ Timeline & Script

Below is the exact timeline and narration script executed programmatically by [SwingScenarioRecorder.java](file:///Users/ludo/a/chuckjava/deluge/src/test/java/org/deluge/ui/SwingScenarioRecorder.java).

| Section | Timestamp | Narration Script | Visual Action / Animation |
| :--- | :--- | :--- | :--- |
| **1. Intro** | `0:00 - 0:11` | "Welcome to the Deluge Sequencer Boot Camp! Today we will learn note entry, transposing, note lengths, and probability step conditions on the isomorphic grid." | UI boots in Diatonic C Major. The cursor is stationary, letting the user orient themselves. |
| **2. Orientation** | `0:11 - 0:23` | "First, look at the grid. It is divided horizontally in columns of four: step 1, 5, 9, and 13. This represents standard sixteenth note divisions." | The cursor animates smoothly across the top pads of columns 0, 4, 8, and 12 to highlight beat subdivisions. |
| **3. Note Entry** | `0:23 - 0:34` | "To insert a note, click on any blank pad. Let's enter a standard four-on-the-floor beat by placing notes at columns 0, 4, 8, and 12." | Grid scrolls to center C4. The cursor moves and clicks at **Row 5 Column 1** (C4), then **Column 5** (C4), **Column 9** (C4), and **Column 13** (C4). The play button is pressed, and the synth audio starts playing. |
| **4. Note Transpose** | `0:34 - 0:42` | "To transpose a note, hold the pad and drag or scroll it vertically. Let's move our second note from C4 up to E4." | The cursor moves to the second note (Column 5), clicks it to remove it, and clicks the E4 pad above it. The audio pitch shifts instantly. |
| **5. Note Length** | `0:42 - 0:50` | "Adjust note length by holding the start pad and clicking a pad further right. This stretches the note's gate visually." | The cursor presses down on the third note (Column 9), drags to Column 11, and releases. The note is stretched to 3 steps, sustaining the synthesizer gate. |
| **6. Duplicate** | `0:50 - 1:00` | "To double the pattern length and clone all active notes, hold Shift and press down the scroll encoder. Watch the grid expand from 16 to 32 steps." | The grid length is programmatically doubled from 16 to 32 steps, copying the notes to the second page. |
| **7. Zooming** | `1:00 - 1:10` | "Turn the scroll encoder to zoom the grid resolution. Zooming out displays eighth notes; zooming in displays thirty-second notes for ultra-fine programming." | The grid resolution is zoomed out and then in to demonstrate the flexible resolution of the sequencer. |
| **8. Probability** | `1:10 - 1:22` | "Finally, let's create generative variations. Hold a step pad and turn the encoder to set a 70% probability condition, creating organic, evolving melodies." | The cursor moves to the fourth note (Column 13) and applies a **70% probability condition**, causing the note to trigger randomly on playback. |
| **9. Outro & Jam** | `1:22 - End` | "Let's listen to our generative, high-fidelity synthesis pattern play out!" | The user enjoys the generative, evolving melody playing back on the digital synthesizer engine for 10 seconds before the script stops playback and terminates. |

---

## 🛠️ How to Compile & Run

The video pipeline consists of a Java automation runner and a Python video post-processing mixer.

### Step 1: Run the Scenario Recorder
Execute the Java scenario recorder to capture the UI frame-by-frame and render the high-fidelity master audio:
```bash
mvn -pl deluge test -Dtest=SwingScenarioRecorder
```
*This will output PNG frames to `target/recorder/frames/`, the master audio to `target/recorder/audio_master.wav`, and the timeline data to `target/recorder/narration_timeline.json`.*

### Step 2: Assemble the Final Video
The compiler uses macOS's native `say` utility to synthesize high-quality text-to-speech narration, then uses `ffmpeg` to mix and compile the final HD video:
```bash
python3 deluge/src/test/python/CompileVideo.py
```
*The compiled video will be saved to `target/Swing_Sequencer_Bootcamp.mp4`.*

---

## 📝 Remaining Enhancements (WIP Agenda)
- [ ] **Voiceover Quality:** Integrate a higher-quality AI speech synthesis engine (e.g. ElevenLabs) or local Neural TTS.
- [ ] **Multi-Track Orchestration:** Add a secondary drum/kit track to play along with the synth line for a richer demonstration.
- [ ] **Visual Overlays:** Render neon overlays showing the active keys being played on the isomorphic keyboard columns.
- [ ] **Transition Timing:** Fine-tune frame capture rates and audio latency to eliminate micro-stuttering.
