# ChucK-Java MIDI Guide for Musicians

ChucK-Java features a professional-grade MIDI subsystem based on **RtMidiJava**, providing zero-setup, low-latency access to native hardware drivers and high-level abstractions for musical performance.

---

## 1. Professional Native Drivers

Unlike many Java applications, ChucK-Java uses a pure Java FFM (Foreign Function & Memory) implementation of RtMidi. This means **native MIDI support is always active** on Windows, macOS, and Linux without installing any extra libraries.

### Benefits of Native MIDI:
- **Zero Setup:** No DLLs or .so files to download. It just works.
- **Low Latency:** Bypasses the standard JVM polling jitter for much tighter musical timing.
- **Zero-GC Hot Path:** The MIDI engine does not allocate memory on the heap during playback, preventing "stutters" caused by Java Garbage Collection.
- **Native Port Sharing:** Open the same hardware port multiple times across different shreds without "Device Busy" conflicts.
- **Virtual Ports:** Create ports on macOS and Linux that other apps (Ableton, Logic) can see and send to.

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
- **Hardware Input:** Highlights keys in **Orange** when you play.
- **Code Output:** Highlights keys in **Light Blue** when your ChucK code plays a note (via `MidiOut` or `MidiPoly`).

### MIDI Monitor Tab
A dedicated **MIDI** tab in the left panel provides advanced diagnostics:
- **Log Tab:** Detailed history of incoming (In) and outgoing (Out) messages with precision timestamps.
- **CC Grid Tab:** Real-time visual feedback for all 128 Control Change values.
- **Mappings Tab (Map Manager):** Manage your MIDI Learn connections.

### Status Bar Tools
- **MIDI Menu (Patchbay):** View ports, toggle monitoring, and setup **Thru routes**.
- **SYNC Button:** Turn ChucK into the master clock for your studio (24ppq).
- **REC Button:** Instantly capture incoming MIDI to a `.mid` file in `recordings/`.
- **! (Panic) Button:** Essential for silencing stuck notes.

### MIDI Learn (The "L" Button)
Bind physical knobs to your code without writing MIDI parsing logic:
1.  Declare a `global float` or `global int` in your script.
2.  Open the **Control** tab on the left panel.
3.  Click the **"L"** button next to your variable.
4.  Move a knob on your keyboard. It's now mapped!

---

## 5. Advanced Techniques

### Sample-Accurate Timing
Every `MidiMsg` includes a `msg.when` field containing the precision timestamp from the hardware driver.

### MIDI Clock (Transport Sync)
```chuck
MidiIn min; min.open(0);
min.ignoreTypes(1, 0, 1); // Allow Time messages

MidiClock clock;
spork ~ feedClock();

fun void feedClock() {
    MidiMsg msg;
    while(min => now) {
        while(min.recv(msg)) clock.update(msg);
    }
}

while(clock.onBeat() => now) {
    <<< "Beat! BPM:", clock.bpm() >>>;
}
```

### Filtering Data
If your controller sends too much data (like Active Sensing), you can filter it out:
```chuck
min.ignoreTypes(1, 1, 1); // Ignore Sysex, Timing, and Sensing
```

---

## 6. Virtual Ports (macOS/Linux)
You can make ChucK-Java appear as a MIDI device to other apps:
```chuck
MidiIn min;
min.openVirtual("From ChucK"); // Now Ableton can "see" ChucK!
```
