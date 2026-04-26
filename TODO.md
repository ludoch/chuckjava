# ChucK-Java Deluge TODO

## Phase 1: Object Model & Documentation [DONE]
- [x] Define Unified Object Model (Song -> Track -> Clip -> Synth/Kit).
- [x] Map Object Model to XML structures.
- [x] Document Desktop-optimized UI Interactions & Actions.
- [x] Consolidate Design into `UNIFIED_DESIGN.md`.

## Phase 2: UI Overhaul (Desktop Optimized)
- [ ] Implement Sidebar Project Manager (Project Tree + SD Emulator).
- [ ] Integrate 88-key Piano Keyboard (`PianoKeyboard.java`) as persistent panel.
- [ ] Implement Persistent Velocity Lane below the Matrix Grid.
- [ ] Update Matrix Grid to support Dynamic sizing (8x16, 16x16) and Marquee selection.
- [ ] Implement Keyboard Shortcuts (Space, Ctrl+S/N/C/V, Alt+Drag, etc.).
- [ ] Add Right-Click Context Menu to Grid Cells.

## Phase 3: Advanced Visual Editors
- [ ] Implement OSC & FM Matrix (Node Editor Pop-up).
- [ ] Implement Multisampling Editor (Waveform + Keyboard Map).
- [ ] Implement Automation Graph Editor (Bezier curves over Grid).
- [ ] Implement Random Patch / Kit Generation (inspired by Deluge_Random_Patch).

## Phase 4: Audio Engine Deepening
- [ ] Implement true Sidechain routing (Source -> Target).
- [ ] Add 4 Envelopes and 4 LFOs per Synth voice (following `UNIFIED_DESIGN.md`).
- [ ] Wire SVFilter morphing to the UI.
- [ ] Integrate MVerb and ProceduralReverb as options in the Deluge Synth engine UI.

## Future Ideas / Community Inspired
- [ ] Implement Actions to generate things (e.g. Random Patch/Kit) inspired by [Deluge_Random_Patch](https://github.com/adwuard/Deluge_Random_Patch).
- [ ] Explore Deluge Community Firmware features for advanced synthesis.
- [ ] Support complex Note Entry (spanning multiple cells) with horizontal auto-scrolling.

---
*Last updated: April 21, 2026*
