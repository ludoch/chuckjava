# Deluge UI redesign — action map + hybrid custom-control plan

**Status: partially implemented.** The foundation (shared animation clock, rotary encoder with
sticky filter, transient param readout) and the display swap (old 4-char LED removed, OLED primary)
have landed with tests. Phases 3–6 remain. See [Current state](#current-state) for the exact split.

> Scope note: this is a **UI/UX** plan only. It speaks the engine through the existing
> `BridgeContract` + model and makes **no engine changes** (the faithful `firmware2` port is
> untouched). UI is visual and this is a Wayland session (screenshots return black), so control
> *logic* is unit-tested but "does it look right" needs a human pass.

## Why

The Swing app emulates a Synthstrom Deluge, but its action surface is mouse/keyboard-shaped and only
loosely mirrors the hardware. The Deluge's whole interaction language is *turn / press /
press-while-turning* on **6 encoders**, a modal **SHIFT** combo layer, and **2 gold mod-knobs** — the
app approximated these with scrollbars, modal dialogs, and a shift-held overlay, and used a legacy
4-char "retro LED" readout instead of the faithful OLED. This plan (a) catalogs every instrument-clip
action vs its Swing status, and (b) designs modern self-drawn controls for the non-grid surface while
keeping a faithful pad grid + OLED.

Status legend: ✅ faithful · 🔁 adapted (works, non-hardware gesture) · ⚠ partial/loose · ❌ missing.
C refs: `~/a/DelugeFirmware/src/deluge/` (icv = instrument_clip_view.cpp, icm = instrument_clip_minder.cpp).
Swing: `deluge/src/main/java/org/deluge/ui/`.

---

## Part A — action map (synth / instrument-clip context)

### A1. Transport & global buttons
| HW action | C ref | Swing now | Proposed |
|---|---|---|---|
| PLAY start/stop | buttons.cpp:151 | ✅ PLAY btn | keep |
| RECORD toggle | buttons.cpp:219 | 🔁 REC = live-record | keep; SHIFT+REC = resample |
| SHIFT modal layer | buttons.cpp:176 | ⚠ shift-held overlay | real modal SHIFT state (Part B) |
| BACK undo / SHIFT+BACK redo | view.cpp:381 | 🔁 Ctrl+Z/Y | BACK button honoring SHIFT |
| TAP_TEMPO / SHIFT+TAP metronome | view.cpp:143 | ⚠ TAP only | add SHIFT+TAP metronome |
| RECORD+PLAY stop rec at loop end | buttons.cpp:154 | ❌ | low priority |

### A2. View-switch buttons
| HW action | C ref | Swing now | Proposed |
|---|---|---|---|
| SESSION (hold→song/arr) | icv.cpp:261 | ✅ SONG/ARR tabs | keep |
| CLIP→AUTOMATION | icv.cpp:290 | ✅ CLIP/AUTO toggle | keep |
| KEYBOARD | icv.cpp:305 | ✅ KEYPLAY tab | keep |
| SYNTH/KIT/MIDI/CV change; SHIFT+SYNTH=new; MOD7+SYNTH=FM | icv.cpp:512, icm.cpp:543 | 🔁 type convert in inspector | type buttons + SHIFT/new |

### A3. Utility buttons
| HW action | C ref | Swing now | Proposed |
|---|---|---|---|
| SCALE_MODE; SHIFT+SCALE cycles; AUDITION+SCALE sets root | icv.cpp:855 | ⚠ scale stored, no live control | SCALE button + root-set gesture |
| CROSS_SCREEN_EDIT wrap-edit | icv.cpp:316 | 🔁 cross-screen note dragging | keep, label wrap-edit |
| LEARN; X_ENC+LEARN copy / SHIFT paste | view.cpp:182, icv.cpp:701 | ⚠ MIDI-learn dialog tab | add note copy/paste on selection |
| SAVE/LOAD hold sub-menus | icm.cpp:384 | 🔁 menu/Ctrl-S, hot-swap presets | surface preset load on type buttons |
| SYNC_SCALING / FILL | view.cpp:330 | ❌ | low priority |
| AFFECT_ENTIRE (kit: all drums) | icm.cpp:484 | ✅ (kit-only, all drums) | keep |

### A4. Encoders — the biggest gap (no hardware encoder model originally)
| Encoder | turn | press | press+turn / shift | C ref | Swing now |
|---|---|---|---|---|---|
| **X_ENC** (horiz) | scroll timeline; SHIFT=clip length; +Y=shift notes | nudge notes | NOTES+turn=velocity; +LEARN=copy | icv.cpp:6346 | ⚠ scrollbar |
| **Y_ENC** (vert) | scroll rows; transpose; SHIFT=octave | repeat/euclidean | +X=transpose screen | icv.cpp:6073 | ⚠ scrollbar |
| **SELECT_ENC** | menu/list nav | enter Sound Editor; SHIFT=settings | — | icv.cpp:827 | ❌ (dialogs) |
| **TEMPO_ENC** | BPM; SHIFT=fine | show tempo; SHIFT=clear automation | +TAP=swing | buttons.cpp:252 | ⚠ BPM slider |
| **MOD_0 / MOD_1** (gold) | automate assigned param; sticky | delete automation; +LEARN copy/paste | — | view.cpp:796 | ❌ (no gold knobs) |
| hold-pad + MOD turn | per-step param-lock automation | — | — | view.cpp:796 | 🔁 shift-click "arm" + dialog |

Sticky behaviour (ignore back-wiggle within ~0.5s, encoders.cpp:244-292) lives in the knob widget.

### A5. Pad & sidebar gestures
| HW action | C ref | Swing now | Proposed |
|---|---|---|---|
| edit pad press/hold = note; release = off | icv.cpp:2239 | ✅ step toggle / KEYPLAY | keep |
| hold pad + X_ENC velocity; +Y_ENC repeat | icv.cpp:6346 | 🔁 alt-click props dialog | hold-pad + knob |
| hold pad + 2nd pad = note length | icv.cpp:2315 | 🔁 drag-to-tie | keep |
| audition col (x=17) play row | icv.cpp:4968 | 🔁 KEYPLAY / row trigger | keep |
| mute col (x=16) toggle mute | icv.cpp:4048 | 🔁 shift-click / mute col | keep |
| AUDITION+SCALE set root | padAction:1942 | ❌ | with SCALE button |

### A6. Display text
| HW behaviour | C ref | Swing now | Proposed |
|---|---|---|---|
| param turn → transient name+value readout | oled.cpp:723 | ✅ `DelugeParamReadout` (LED dropped) | done |
| scrolling names | seven_segment.h:55 | ⚠ | OLED scroll + readout marquee |
| popup flash count / blink (110/60ms) | numeric_layer_basic_text.cpp:31 | ⚠ in readout | model in readout/OLED |
| tempo/scale/key popups | view.cpp | ⚠ partial | route through OLED |

### A7. Pad colours & animation
| HW behaviour | C ref | Swing now | Proposed |
|---|---|---|---|
| velocity → brightness | note_row.cpp:1964 | ✅ intensity blend | keep |
| root vs in-scale vs out | isomorphic.cpp:122 | ⚠ KEYPLAY only | extend to clip rows |
| note tails/blur | rgb.h:84 | ⚠ tie line only | add tail rendering |
| playhead/record cursor blink (60/110ms) | pad_leds.cpp:89 | ⚠ static ring | blink clock (`UiAnimator`) |
| launch-queued / mute blink | session_view.cpp:2666 | 🔁 static amber | add blink |
| scroll/explode/collapse anims | pad_leds.cpp:108 | ❌ | optional polish |

---

## Part B — hybrid custom-control design (OLED-only text)

**Principles:** one shared animation clock; every control self-drawn via `paintComponent`
(Graphics2D, AA) like `DelugePadButton`; controls speak the engine through `BridgeContract` + model
(no engine changes); no legacy LED.

Components (`ui/controls/`):
1. **`DelugeEncoderKnob`** — rotary knob; drag = turn, click = press, Alt/right-drag = press+turn;
   implements the sticky back-wiggle filter. ✅ built + tested.
2. **`DelugeModKnobBar`** — the two gold mod-knobs + 8 mod-button param row; turn automates the
   assigned param (macro/shift-param plumbing in `SwingGridPanel`); press deletes automation. ❌ TODO.
3. **`DelugeScrollEncoders`** — X/Y encoder pair driving `scrollOffsetX`/`scrollOffset` + SHIFT zoom
   (GridMode) + transpose, replacing raw scrollbars. ❌ TODO (a `DelugeEncoderStrip` exists as the
   transport/encoder strip; the scroll-binding piece is the gap).
4. **`DelugeParamReadout`** — modern transient readout (replaces LED): value + name, flash/blink,
   marquee. ✅ built + tested.
5. **OLED panel** — keep/extend `SwingOledPanel` as the primary text surface. ✅ exists.
6. **`NeonSlider` / `RotaryComboBox` / `SegmentedToggle`** — modern self-drawn replacements for the
   synth/kit dialog `JSlider`/`JComboBox`/checkboxes. `SegmentedToggle` ✅ built; the other two ❌ TODO.
7. **`UiAnimator`** — single Swing Timer (~30fps) broadcasting a tick so blink/fade/scroll are
   phase-aligned. ✅ built + tested.

**Layout (hybrid):** top = faithful 8×16 `DelugePadButton` grid + mute/audition columns + OLED; a
slim transport/encoder strip (PLAY/REC/SHIFT/BACK/TAP, X/Y/SELECT/TEMPO knobs, param readout);
bottom = a modern always-visible synth param panel from the new knobs/sliders/combos.

---

## Part C — phased roadmap (with status)

1. **Foundation** — `UiAnimator` + `DelugeEncoderKnob` (sticky) + tests. ✅ **DONE.**
2. **Display swap** — `DelugeParamReadout`, route readouts to it + OLED, delete `RetroLedDisplay`.
   ✅ **DONE** (LED removed).
3. **Encoders live** — `DelugeScrollEncoders` (X/Y → scroll/zoom/transpose) and `DelugeModKnobBar`
   (gold knobs → macro/shift-param automation, press = delete). ⏳ **TODO** — the natural next slice;
   the encoder/sticky/animator foundation it needs is in place.
4. **Pad fidelity** — blink clock for playhead/record/launch-queue; note tails; clip-row scale
   colouring. ⏳ **TODO.**
5. **Modern param panel** — `NeonSlider`/`RotaryComboBox`/`SegmentedToggle`; surface synth params in a
   bottom panel; keep dialogs as "advanced". ⏳ **TODO** (`SegmentedToggle` done).
6. **Combo/utility actions** — SCALE button + root-set, note copy/paste, type buttons, metronome —
   close remaining ❌ rows. ⏳ **TODO.**

## <a name="current-state"></a>Current state (verified)

| Built & wired (with tests) | Still missing |
|---|---|
| `ui/controls/`: `UiAnimator`, `DelugeEncoderKnob`, `StickyTurnFilter`, `DelugeParamReadout`, `DelugeEncoderStrip`, `SegmentedToggle` (+ `model/ModKnob`); legacy `RetroLedDisplay` **deleted** | `DelugeModKnobBar`, `DelugeScrollEncoders`, `NeonSlider`, `RotaryComboBox` |

Tests present: `DelugeEncoderKnobTest`, `DelugeEncoderStripTest`, `DelugeParamReadoutTest`,
`StickyTurnFilterTest`, `UiAnimatorTest`.

## Verification
- Per-component unit tests (encoder deltas/sticky; readout flash timing; scale colouring) via
  `mvn -pl deluge test`.
- `mvn -pl deluge compile` + spotless.
- Manual (human, since Wayland screenshots are black): `run_deluge.sh` — gold-knob turn automates a
  param (OLED/readout updates), X/Y knobs scroll+zoom, playhead blinks.

## Critical files
- New (controls): `ui/controls/{DelugeEncoderKnob,DelugeEncoderStrip,DelugeParamReadout,SegmentedToggle,UiAnimator,StickyTurnFilter}.java`
  (done); `{DelugeModKnobBar,DelugeScrollEncoders,NeonSlider,RotaryComboBox}.java` (TODO).
- Edit: `ui/SwingTopBarPanel.java` (transport/encoder strip), `ui/SwingGridPanel.java`
  (scroll/zoom/automation via encoders; blink/tails), `ui/SwingDelugeApp.java` (animator wiring,
  readout call sites), `ui/SwingSynthConfigDialog.java` / `SwingKitConfigDialog.java` (new controls),
  `ui/SwingOledPanel.java` (primary text surface).
- Reuse: `ui/DelugePadButton.java`, `ui/DarkSliderUI.java`, `ui/DarkComboBoxRenderer.java`,
  `BridgeContract`, the macro/`activeShiftParam` plumbing in `SwingGridPanel`.
