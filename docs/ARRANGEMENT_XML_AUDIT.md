# Arrangement XML model — audit vs C firmware (2026-06-25)

Triggered by real bugs in song serialization that shipped despite a "100% C-accurate" claim. This
audit re-checks the **arrangement-relevant** song XML, **field-by-field against the C writer AND
reader** (not against prior Java, not against memory). Each row cites the C `file:line`. Severity:
🔴 breaks load/playback · 🟡 wrong-but-tolerated · ⚪ cosmetic/view-state · ✅ correct.

C sources: `model/song/song.cpp` (`Song::writeToFile` ~1115-1320, reader ~1600-1660),
`model/output.cpp` (`Output::writeToFile`/read ~250-400), `model/clip/clip.cpp`
(`Clip::writeToFile`/read ~644-720), `src/definitions_cxx.hpp`.

## How the bugs happened (root cause of the misses)
The serializer was written to match *plausible-looking XML* and prior Java, not the C `writeToFile`.
A memory note even asserted (wrongly) that `clipInstances` "is not serialized." Three compounding
gaps let bugs through: (1) attributes were invented/renamed without checking the C reader actually
consumes them; (2) clip/arranger state was hardcoded (`isPlaying="1"`, `section="0"`); (3) the
per-synth engine RMS test held notes manually, masking a too-short clip note. **Lesson: verify
against both the C writer and the C reader; a green Java test that bypasses the data path proves
nothing about the file.**

## Bugs found & FIXED this pass
| # | Field | C truth | Was | Fix | Sev |
|---|---|---|---|---|---|
| 1 | `clipInstances` (Output) | serialized hex blob per Output (`output.cpp:259-291`) | claimed "Java invention", nearly removed | kept; format verified | 🔴 |
| 2 | `clipInstances` on `<instrumentClip>` | only on Output, never on a clip | **also** written on each clip (2× = 346) | removed from clip path | 🔴 |
| 3 | `clipCode` bit-31 | set when clip `section==255` (`output.cpp:280-285`) | plain index only | `clipCodeFor()` adds the flag; `0xFFFFFFFF` for no-clip | 🟡 |
| 4 | `isPlaying` | `= activeIfNoSolo` (`clip.cpp:659`) | hardcoded `"1"` on all → session cacophony | from `ClipModel.activeInSession` | 🔴 |
| 5 | `section` (clip) | written iff `!=255` (`clip.cpp:667`), clamp on read (`:715`) | hardcoded `"0"` | real `ClipModel.section`, read+write | 🟡 |
| 6 | `inArrangementView` | written when root UI = arranger (`song.cpp:1148`) | never written → booted to session | `ProjectModel.bootInArrangementView` | 🔴 |
| 7 | clip note length (all-synths) | — | 24-tick blip → slow synths silent | sustained note = full slot | 🔴 (test data) |
| 8 | `yScrollArrangementView` | `arrangementYScroll` (`song.cpp:1168`) | hardcoded `-7` | `0` | ⚪ |
| 9 | `rootNote` / arranger view-state | `rootNote`, `xZoom/xScrollArrangementView`, `arrangementAutoScrollOn` (`song.cpp:1166-1170`) | missing | now written | ⚪ |

## Remaining KNOWN divergences (honest — not yet addressed)
| Field | C truth | Our state | Sev | Note |
|---|---|---|---|---|
| `tempo`, `scale`, `key`, `swing` (root attrs) | **not written by C; reader ignores them** — tempo comes from `timePerTimerTick`, key from `rootNote`, scale from `modeNotes` | we write them | 🟡 harmless | They serve OUR round-trip parser. The C reader never reads them, so they can't affect hardware — but they are non-faithful extras. |
| `preview` (LED-image hex blob) | written (`song.cpp:1131-1143`) | not written (we write `previewNumPads` only) | ⚪ | Cosmetic thumbnail; hardware tolerates absence. |
| `arrangementOnlyTracks` / arrangement-only clips | array written when present (`song.cpp:1286-1297`) | **not serialized** (parser reads it, serializer never writes it) | 🟡 | Our songs place session clips via `clipInstances`, so N/A today, but a real gap for songs that use arrangement-only clips. |
| `currentTrackInstanceArrangementPos` | written when "inside" an instance (`song.cpp:1153`) | not written | ⚪ | `inArrangementView=1` is sufficient to boot in arranger. |
| `songGridScrollX/Y`, `sessionLayout`, `lastSelectedParam*`, `chordMem` | written (`song.cpp:1188-1320`) | not written | ⚪ | Community-firmware/session-grid + chord-mem state; defaults are fine. |
| `clipInstances` monotonic rule | per-Output instances must be ascending non-overlapping or are silently dropped (`output.cpp:384`) | our songs use 1 instance/Output → trivially satisfied; **no general guard** | 🟡 | If we ever place >1 instance per track unsorted, the reader will drop some. Should sort by `pos` when building. |

## Verified CORRECT (against C)
- `clipInstances` hex layout: `pos`(8) + `length`(8) + `clipCode`(8), `"0x"` prefix — matches `output.cpp` read (24 chars/instance) and write.
- `clipCode` = clip's save index; resolution order (sessionClips write order) matches our `getGlobalClipIndex` track→clip order.
- `<sections>`: all 24 slots, `id` + `numRepeats` (+ optional MIDI command) — `song.cpp:1253-1278`; `kMaxNumSections=24` (`definitions_cxx.hpp:459`).
- `section` clamp `min(section, 23)` on read — matches.
- `length`, `isPlaying`, `section`, `triplet`, `sequenceDirection` on `<instrumentClip>`.
- `timePerTimerTick`/`timerTickFraction` = 229/… for 120 BPM (matches hardware `Dx7A.xml`).

## Cannot verify without hardware
- **Tick-unit scaling.** Our positions are 96-PPQN-derived (`768` ticks/2-bar slot). `inputTickMagnitude=2`; the firmware applies its own magnitude. The structure is C-faithful, but whether 768 ticks renders as exactly 2 bars at the saved tempo can only be confirmed by hardware playback. (User report so far: spacing seemed right; the silence was the note-length bug, now fixed.)

## Coverage of C-reader attributes (our serializer)
Present: `timePerTimerTick`, `timerTickFraction`, `inputTickMagnitude`, `swingAmount`, `swingInterval`,
`xScroll`, `xZoom`, `yScrollSongView`, `yScrollArrangementView`, `xScrollArrangementView`,
`xZoomArrangementView`, `arrangementAutoScrollOn`, `rootNote`, `inArrangementView`, `activeModFunction`,
`affectEntire`, `modeNotes`. Absent (cosmetic): `preview`, `currentTrackInstanceArrangementPos`,
`songGridScrollX/Y`, `sessionLayout`.
