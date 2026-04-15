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
3.  Check the **Native MIDI Status**. If it shows a 🔴 red dot, you need to provide the `rtmidi` library for your system.

#### **Where to find the library?**

-   **macOS (Homebrew):**
    1.  Run: `brew install rtmidi`
    2.  The library (`librtmidi.dylib`) is installed in:
        *   **Apple Silicon:** `/opt/homebrew/lib/`
        *   **Intel Macs:** `/usr/local/lib/`
    3.  **Tip:** Use `brew ls --verbose rtmidi | grep dylib` to find the exact location if you're unsure.

-   **Windows (rtmidi.dll):**
    *   **The "Borrow" Method (Easiest):** Search your computer for `rtmidi.dll`. Many music apps include it. Look in the installation folders of **Bespoke Synth**, **VCV Rack**, or **SuperCollider**.
    *   **Build from Source (Recommended):** If you have Visual Studio and CMake installed, you can build it in seconds:
        1.  `git clone https://github.com/thestk/rtmidi.git`
        2.  `mkdir build && cd build`
        3.  `cmake .. -DRTMIDI_BUILD_SHARED_LIBS=ON`
        4.  `cmake --build . --config Release`
        5.  The `rtmidi.dll` will be in the `Release` folder.
    *   **Package Managers:**
        - **vcpkg:** `vcpkg install rtmidi:x64-windows`. The DLL will be in `vcpkg/installed/x64-windows/bin/`.
        - **MSYS2:** `pacman -S mingw-w64-x86_64-rtmidi`. The DLL will be in `/mingw64/bin/`.
    *   **Manual Copy:** Once you find the DLL, copy it to the ChucK-Java project root or any folder in your system PATH. Alternatively, point the IDE to its folder in Preferences.

-   **Linux:**
    1.  Run: `sudo apt-get install librtmidi-dev` (Debian/Ubuntu) or `sudo dnf install rtmidi-devel` (Fedora).
    2.  The library (`librtmidi.so`) is usually in `/usr/lib/x86_64-linux-gnu/` or `/usr/local/lib/`.

4.  Once you have the file, click the **...** button in MIDI Preferences and select the **folder** containing the file.
5.  Click **Refresh Devices**. Once enabled (🟢 green dot), ChucK will automatically prefer native drivers.

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
- **Hardware Input:** Highlights keys in **Orange** when you play.
- **Code Output:** Highlights keys in **Light Blue** when your ChucK code plays a note (via `MidiOut` or `MidiPoly`), giving you visual feedback on generative melodies.
- **Pitch Bend:** A vertical cyan bar on the left shows bend depth.
- **CC Activity:** A status label showing the last received MIDI message.

### MIDI Monitor Tab
A dedicated **MIDI** tab in the left panel provides advanced diagnostics:
- **Log Tab:** Detailed history of incoming (In) and outgoing (Out) messages with precision timestamps.
- **CC Grid Tab:** Real-time visual feedback for all 128 Control Change values.
- **Mappings Tab (Map Manager):** A central dashboard listing every active MIDI Learn connection, making it easy to manage your hardware-to-software setup.

### Status Bar Tools
- **MIDI Menu (Patchbay):** View available ports, toggle global monitoring, and setup **Thru routes** to connect inputs directly to outputs.
- **SYNC Button:** Turn ChucK into the master clock for your studio. Sends 24ppq MIDI Clock to selected outputs based on the BPM field.
- **REC Button:** Instantly capture all incoming MIDI data into a timestamped `.mid` file in the `recordings/` directory. No code required!
- **! (Panic) Button:** Sends "All Notes Off" and "Reset All Controllers" to all active MIDI outputs. Essential for silencing stuck notes.
- **Activity Indicator:** A small circular "LED" flashes **Green** for incoming MIDI and **Orange** for outgoing MIDI.

### MIDI Learn (The "L" Button)
Bind physical knobs to your code without writing a single line of MIDI parsing:
1.  Declare a `global float` or `global int` in your script.
2.  Open the **Control** tab on the left panel.
3.  Click the **"L"** button next to your variable (it turns yellow).
4.  Move a knob on your keyboard. It's now mapped! (Button turns green, and the **CC Number** is displayed).
5.  Right-click the "L" button to unmap.

**Mappings are automatically saved** to your preferences and will be restored the next time you use the same variable names in a script.

---

## 5. Advanced Techniques

### Velocity Curves for Expressive Play
When using `MidiPoly`, you can customize how the instrument responds to your touch using the `curve()` method:
```chuck
MidiPoly poly => dac;
poly.setInstrument("Rhodey");

// Set exponential response (requires harder hits for full volume)
poly.curve(2.0);

// Set logarithmic response (sensitive to light touches)
poly.curve(0.5);
```

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