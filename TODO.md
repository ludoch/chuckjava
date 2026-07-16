# ChucK-Java TODO & Roadmap

## Completed Milestones [DONE]
- [x] **Core Virtual Machine**: Multi-shred concurrency, exact sample-timing (`=> now`), sporking, event broadcast, and object lifecycle (`ChuckVM`, `ChuckShred`).
- [x] **Java DSL Generator (`ChuckToDSLConverter.java`)**: 100% batch translation and `javac` compilation pass rate across all 636 `.ck` example files.
- [x] **Full UGen & UAna Library**: 100+ standard unit generators, STK synthesizers (`FMVoices`, `BeeThree`, `Wurley`, `Rhodey`, `TubeBell`, `HevyMetl`, `PercFlut`, `Mandolin`, `Moog`), filters, and audio analysis tools.
- [x] **Exact `special:*` Audio Resources**: Zero missing resource errors via bundled `special_dope.wav` and algorithmic `tryLoadSpecial` waveform generation across `SndBuf` and `SndBuf2` (`15_sndbuf.ck` high parity).
- [x] **Interactive JavaFX IDE (`ChuckIDE.java`)**: Live coding editor with syntax highlighting, professional visualizers, and VM control surface.
- [x] **miniAudicle Workstation Parity (`org.chuck.ide`)**:
  - Positive-slope zero-crossing trigger locked CRT Oscilloscope with multi-pass phosphor glow (`VisualizerPanel.java`).
  - Logarithmic frequency spectrum analyzer ($20\text{ Hz}$–$20\text{ kHz}$) with exponential decay smoothing (`VisualizerPanel.java`).
  - Detachable Virtual Console (`VirtualConsolePanel.java`) with real-time regex filtering, auto-scroll toggle, and `↗ Detach` floating window.
  - Live Shred Property Inspector (`ShredInspectorDialog.java`) with real-time instruction table, memory stack pointer, and direct `[Kill Shred]` control.
  - Live DAC audio recorder (`WvOut`) to `.wav` files via `[● Record WAV]`.
- [x] **Pluggable Audio Backend Architecture (`org.chuck.audio.backend`)**: Driver abstractions and concrete FFM implementations (`WASAPIBackend`, `CoreAudioBackend`, `JackBackend`, `JavaSoundBackend`) with auto-negotiation (`AudioBackendRegistry`).
- [x] **Phase 3 Native FFM Audio Drivers**: Low-latency WASAPI Exclusive/Shared Mode (Windows via `Ole32`/`Avrt`), CoreAudio (`AudioToolbox`), and JACK (`libjack`) backends via Foreign Function & Memory API (Project Panama), reducing round-trip audio latency from `~20ms` down to `<5ms`.
- [x] **Phase 4 Interactive MIDI CC Learn & Parameter Automation (`org.chuck.ide`)**:
  - Two-way MIDI CC Learn (`[L]`) auto-mapping physical controller knobs/faders to live UGen/global variables (`ControlSurface.java`).
  - Real-time parameter breakpoint recording (`[● Rec]`), loop playback (`[▶ Play]`), and curve interpolation engine (`AutomationTrack.java`).
  - Interactive breakpoint curve canvas with instant LFO presets (`Sine LFO`, `Triangle LFO`, `Ramp Up/Down`, `Random S&H`) (`AutomationCanvas.java`).
  - Custom Min/Max Range Scaling (`[Set Range]`) transforming raw `0..127` MIDI CC values to physical units ($20\text{ Hz}$–$20\text{ kHz}$).
- [x] **Phase 6 Multi-Channel Surround Bus & Auxiliary Stem Routing (`org.chuck.audio`)**:
  - Dynamic $N$-Channel matrix audio routing (`dac.chan(0..N-1)`) supporting Stereo (2), Quadraphonic (4), 5.1 (6), and 7.1 Surround (8) across `ChuckVM` and FFM audio backends.
  - Multi-track Stem Export (`WvOut.openMultiTrack`) in both `chuck-ide` (`[● Record Stems]`) and CLI (`--stems:<base>`), exporting individual mono/stereo WAV files per channel alongside $N$-channel interleaved master exports.
- [x] **Consequence Undo/Redo System (`org.chuck.ide.model`)**: Bounded Command/Memento undo/redo stack (`UndoRedoStack`) with coalescing and global VM action tracking.

## Future Enhancements
- [ ] **Project Valhalla (Value Classes)**: Migrate `ChuckDuration` and `Complex` records to `value class` upon JDK 28/29 standardization to eliminate stack-frame boxing allocations.
- [ ] **Primitive Types in Patterns (JEP 530)**: Migrate `ChuckStack.pop()` dispatch to clean primitive switch patterns upon JDK 28+ finalization.
