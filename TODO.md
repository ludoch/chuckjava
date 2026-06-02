# ChucK-Java Deluge TODO

## Phase 1: Object Model & Documentation [DONE]
- [x] Define Unified Object Model (Song -> Track -> Clip -> Synth/Kit).
- [x] Map Object Model to XML structures.
- [x] Document Desktop-optimized UI Interactions & Actions.
- [x] Consolidate Design into `UNIFIED_DESIGN.md`.

## Phase 2: UI Overhaul (Desktop Optimized) [DONE]
- [x] Implement Sidebar Project Manager (Project Tree + SD Emulator via `SwingProjectSidebarPanel.java`).
- [x] Integrate Piano Roll Keyboard (`PianoRollComponent.java`) as persistent panel.
- [x] Implement Persistent Velocity/Automation Lane below the Matrix Grid (`SwingVelocityLanePanel.java`).
- [x] Update Matrix Grid to support custom sizing (up to 192 steps) and multi-cell step selections.
- [x] Implement Keyboard Shortcuts (Space, Page Up/Down, Line Up/Down, Delete, Backspace, Escape, etc.).
- [x] Add Right-Click Context Menu and custom parameter configurations popup to Grid Cells.

## Phase 3: Advanced Visual Editors [DONE]
- [x] **OSC & FM Matrix**: Integrated the widescreen 12-tab `SwingSynthConfigDialog` featuring custom FM operator grids, interactive DX7 algorithms selection, and visual node controls!
- [x] **Multisampling Keyzones Editor**: Developed a dedicated multisample keyzones editor with custom split bounds, sample pitch root keys, and visual keyboard mappings!
- [x] **Automation Editor**: Completed the main grid Step Automation Editor with pixel-perfect step headers, dynamic horizontal JScrollbar footer zoom tools, and unclipped widgets!
- [x] **Random Patch / Kit Generator**: Implemented the complete **Delugeator Randomizer Suite** JDialog with responsive HSL probability dials and custom generator profiles!
- [x] **Audio Loop Slicer & Kit Splitter**: Integrated the dynamic visual JDialog featuring automatic transient peaks detection and multi-slice voice layout configurations!
- [x] **Advanced Wavetable Index Scan Editor**: Developed the widescreen 3D perspective waterfall **Wavetable Index Scan Laboratory** JDialog with zero-latency JNI position hot-swaps!

## Phase 4: Audio Engine Deepening [DONE]
- [x] **True Sidechain Routing**: Implemented multi-bus sidechain target ducking, unipolar envelope mappings, and linear decay release curves!
- [x] **4 Envelopes & 4 LFOs per voice**: Fully cabled tab sub-editors with unipolar/bipolar modulation matrix patch cables!
- [x] **SVFilter Morphing**: Integrated LPF/HPF cutoff/resonance morph sliders cabled straight to the JNI multi-mode filters!
- [x] **Reverb & Delay Sends**: Fully wired spatial space reverb and tempo-synced stereo delay sends to core engine channels!
- [x] **Pedal-Style Continuous Looper**: Developed a multi-layer looper deck with timing-detector Auto-BPM, overdub loop stackers, and real-time foot-pedal layer undo/redo triggers!

## Future Ideas / Community Inspired [IN PROGRESS]
- [x] **Triplet Column Grid Divisions View (SwingGridPanel & ChucK Sequencer)**: Add a `[3]` grid toolbar toggle button to switch step grids columns from 16 to 12 subdivisions (triplets) dynamically.
- [x] **Arranger Live Capture Suite**: Add a **`[🔴 Capture Live Arranger]`** record mode that registers live song/clip actions directly onto arrangement timeline slots in real-time.
- [x] **Complex Note Entry & Horizontal Auto-Scrolling**: Support drag-to-tie notes entries extending gate durations up to 192 steps in StepData with real-time horizontal auto-scroll matching!

---
*Last updated: June 2, 2026*
