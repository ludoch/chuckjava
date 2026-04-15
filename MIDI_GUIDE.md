# ChucK-Java MIDI Guide for Musicians

ChucK-Java features a professional-grade MIDI subsystem based on **RtMidi**, providing low-latency access to native hardware drivers and high-level abstractions for musical performance.

---

## 1. Setting Up Native Drivers

By default, ChucK-Java uses the standard JavaSound MIDI system. For professional use, it is highly recommended to enable the **Native RtMidi** backend.

### Benefits of Native MIDI:
- **Low Latency:** Bypasses the JVM's polling jitter for much tighter timing.
- **Native Port Sharing:** Open the same port multiple times across different shreds without "Device Busy" conflicts.
- **ASIO/WinMM (Windows):** Direct access to Windows MIDI drivers.
- **CoreMIDI (macOS):** Full support for macOS MIDI services and virtual ports.
- **ALSA/JACK (Linux):** Native Linux audio/MIDI integration.
- **Virtual Ports:** Create ports that other apps (Ableton, Logic) can see and send to.

### How to Enable:
1.  Open the IDE.
2.  Go to **Preferences -> MIDI Settings**.
3.  Check the **Native MIDI Status**. If it shows a 🔴 red dot, you may need to provide the path to your `rtmidi.dll` (Windows), `.so` (Linux), or `.dylib` (macOS).
4.  Once enabled (🟢 green dot), ChucK will automatically prefer native drivers.

---

## 2. Opening Devices

In ChucK-Java, you can open devices by **index** or by **name**. Opening by name is recommended for portable scripts.

```chuck
MidiIn min;

// Open the first available device
min.open(0);

// Open by name substring (e.g., if you have an "Arturia KeyStep")
if( min.open("KeyStep") ) {
    <<< "Successfully opened KeyStep!", "" >>>;
}
```

---

## 3. Automatic Polyphony with `MidiPoly` & `MidiMpe`

Writing polyphonic logic manually (voice arrays, note-tracking) is complex. ChucK-Java provides `MidiPoly` to handle this for you.

```chuck
MidiIn min;
MidiPoly poly => dac;

// Connect MidiIn directly to MidiPoly for automatic voice management!
min => poly;

// Choose any STK instrument (Rhodey, Wurley, Mandolin, Sitar, etc.)
poly.setInstrument("Rhodey");
poly.voices(12); // Support up to 12 simultaneous notes

// Optional: Custom microtonal tuning
// float myScale[128]; ... poly.tuning(myScale);

// The voices are triggered automatically. Just wait!
1::week => now;
```

### MPE (MIDI Polyphonic Expression)
If you have an MPE controller (Roli Seaboard, LinnStrument), use `MidiMpe` instead of `MidiPoly`. It handles per-note pitch bend and channel pressure automatically:

```chuck
MidiIn min;
MidiMpe mpe => dac;
min => mpe;

mpe.setInstrument("Moog");
mpe.bendRange(48); // Set pitch bend range (default 48 semitones)

1::week => now;
```

---

## 4. IDE Performance Tools

### MIDI Keyboard Monitor
Located at the bottom of the IDE, the real-time keyboard visualizes:
- **Active Notes:** Highlights keys in orange when you play.
- **Pitch Bend:** A vertical cyan bar on the left shows bend depth.
- **CC Activity:** The label shows the last moved Control Change (knob/fader).

### MIDI Learn (The "L" Button)
Bind physical knobs to your code without writing a single line of MIDI parsing:
1.  Declare a `global float` or `global int` in your script.
2.  Open the **Control** tab on the left panel.
3.  Click the **"L"** button next to your variable (it turns yellow).
4.  Move a knob on your keyboard. It's now mapped! (Button turns green).
5.  Right-click the "L" button to unmap.

---

## 5. Advanced Techniques

### Sample-Accurate Timing
Every `MidiMsg` includes a `msg.when` field containing the precision timestamp from the hardware. 

### MIDI Clock (Transport Sync)
Sync ChucK to Ableton, drum machines, or hardware sequencers using MIDI Real-Time messages (24ppq). Note: ensure your `MidiIn` does not filter out Time messages.

```chuck
MidiIn min; min.open(0);
min.ignoreTypes(1, 0, 1); // Allow Time messages (middle argument = 0)

MidiClock clock;

// Feed the clock in a background shred
spork ~ feedClock();
fun void feedClock() {
    MidiMsg msg;
    while(min => now) {
        while(min.recv(msg)) clock.update(msg);
    }
}

// Main sequence loop
while(clock.onBeat() => now) {
    <<< "Beat! BPM:", clock.bpm() >>>;
    // Trigger your drums, sequences, etc.
}
```

### MIDI File Recording & Sequencing
Use `MidiFileOut` to save your generative performances, now with full multi-track and tempo map support.

```chuck
MidiFileOut mfo;
mfo.open("my_composition.mid");

// Setup track and tempo
mfo.setBpm(120.0);
mfo.addMarker("Intro");
int track1 = mfo.addTrack("Synth Lead");

// Inside your loop:
mfo.write(track1, msg);

// Optional: High-res 14-bit sweep
mfo.nrpn(track1, 0, 100, 8192, 1.5); // (track, channel, param, value, timestamp)

// At the end:
mfo.close();
```

To easily play back `.mid` files, use the new `MidiPlayer` sequencer:

```chuck
MidiFileIn file;
MidiPlayer player;
MidiPoly poly => dac;

// Load file and connect to instrument
file.open("my_composition.mid");
file => player => poly;

// Auto-play!
player.play();
```

### Filtering Data
If your controller sends too much data (like Active Sensing), you can filter it out to save CPU:
```chuck
min.ignoreTypes(1, 1, 1); // Ignore Sysex, Timing, and Sensing
```

---

## 6. Pro-Tip: Virtual Ports (macOS/Linux)
You can make ChucK-Java appear as a MIDI device to other apps:
```chuck
MidiIn min;
min.openVirtual("From ChucK"); // Now Ableton can "see" ChucK!
```