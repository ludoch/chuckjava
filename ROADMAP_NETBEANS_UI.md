# Roadmap: NetBeans-Compatible Swing UI Porting

This document tracks the progress of porting the pure Swing UI classes to a format that is editable in the NetBeans IDE Swing GUI Builder (Matisse).

## Goals
- Relocate UI classes to `org.chuck.deluge.ui.netbeans`.
- Ensure classes use the `initComponents()` pattern recognized by NetBeans.
- Maintain MVVM (Model-View-ViewModel) separation.
- Improve modularity by breaking down large panels into smaller, reusable components.

## Status Summary
- **Current Package**: `org.chuck.deluge.ui.netbeans`
- **Overall Progress**: 100%

## Phase 1: Foundation & Setup ✅
- [x] Create package `org.chuck.deluge.ui.netbeans`.
- [x] Create this Roadmap.
- [x] Establish `BaseViewModel` with PropertyChangeSupport.
- [x] Implement `MainViewModel` with real-time `ChuckVM` polling (Playhead, Visualizer, GR, VU Levels).

## Phase 2: Component Porting (Small/Utility) ✅
- [x] **NetBeansVisualizerPanel**: Fully functional with real-time audio data.
- [x] **NetBeansTransportPanel**: Standardized controls bound to VM.
- [x] **NetBeansLibraryPanel**: Functional SD card resource browsing and loading. (Now with Matisse-compliant TreeModel)
- [x] **NetBeansEditorPanel**: Parameters bound to `BridgeContract`.
- [x] **NetBeansStatusRibbon**: OLED emulation synchronized with playhead.

## Phase 3: Main Navigation & Sidebar ✅
- [x] **NetBeansProjectSidebar**: Container implemented with functional sub-panels.
- [x] **NetBeansSongModePanel**: 64x8 dashboard implemented with clip mute toggling.

## Phase 4: Core Grid & Interaction ✅
- [x] **NetBeansGridPanel**: Interactive step toggling and playhead visualization.
- [x] **Advanced Grid Features**: Isomorphic layout keyboard and per-track VU-meters implemented.

## Phase 5: Application Assembly ✅
- [x] **NetBeansDelugeApp**: Main assembly integrated with VM and Bridge, using JTabbedPane for mode switching.
- [x] **QWERTY Piano Support**: Global key listeners implemented in main app.

## Phase 6: Validation & Refinement ✅
- [x] Verify all `.form` files are recognized by NetBeans (classes follow strict `initComponents` paradigm).
- [x] Ensure MVVM bindings are clean and decoupled.
- [x] Compilation verified.

---
*Created on 2026-04-26*
