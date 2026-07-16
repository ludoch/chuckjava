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
- [x] **Consequence Undo/Redo System (`org.chuck.ide.model`)**: Bounded Command/Memento undo/redo stack (`UndoRedoStack`) with coalescing and global VM action tracking.

## Future Enhancements
- [ ] **Project Valhalla (Value Classes)**: Migrate `ChuckDuration` and `Complex` records to `value class` upon JDK 28/29 standardization to eliminate stack-frame boxing allocations.
- [ ] **Primitive Types in Patterns (JEP 530)**: Migrate `ChuckStack.pop()` dispatch to clean primitive switch patterns upon JDK 28+ finalization.
