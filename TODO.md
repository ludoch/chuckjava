# ChucK-Java TODO & Roadmap

## Completed Milestones [DONE]
- [x] **Core Virtual Machine**: Multi-shred concurrency, exact sample-timing (`=> now`), sporking, event broadcast, and object lifecycle (`ChuckVM`, `ChuckShred`).
- [x] **Java DSL Generator (`ChuckToDSLConverter.java`)**: 100% batch translation and `javac` compilation pass rate across all 636 `.ck` example files.
- [x] **Full UGen & UAna Library**: 100+ standard unit generators, STK synthesizers (`FMVoices`, `BeeThree`, `Wurley`, `Rhodey`, `TubeBell`, `HevyMetl`, `PercFlut`, `Mandolin`, `Moog`), filters, and audio analysis tools.
- [x] **Interactive JavaFX IDE (`ChuckIDE.java`)**: Live coding editor with syntax highlighting, visualizers (FFT, Scope), MIDI monitor, and VM control surface.
- [x] **Pluggable Audio Backend Architecture (`org.chuck.audio.backend`)**: Driver abstractions for low-latency FFM backends (`AudioBackend`, `AudioBackendStream`, `JavaSoundBackend`).
- [x] **Consequence Undo/Redo System (`org.chuck.ide.model`)**: Bounded Command/Memento undo/redo stack (`UndoRedoStack`) with coalescing and global VM action tracking.

## Future Enhancements
- [ ] **Phase 3 Native FFM Audio Drivers**: Optional low-latency WASAPI Exclusive Mode (Windows), JACK (Linux/macOS), and ALSA (Linux) drivers using Foreign Function & Memory API (`AudioBackend` interface).
- [ ] **Project Valhalla (Value Classes)**: Migrate `ChuckDuration` and `Complex` records to `value class` upon JDK 28/29 standardization to eliminate stack-frame boxing allocations.
- [ ] **Primitive Types in Patterns (JEP 530)**: Migrate `ChuckStack.pop()` dispatch to clean primitive switch patterns upon JDK 28+ finalization.
