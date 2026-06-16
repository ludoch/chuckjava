# Plan: collapse `FirmwareSound` shadow state → single `fw2Sound` source of truth

Status: **planned** (the "perfect final fix"). Stopgap guards already landed (see end).

## Background — what the bridge is, and why it's fragile

`org.chuck.deluge.firmware` ("the bridge") was the **first** faithful Deluge port — built to
replace the old ChucK/float-approximation `org.chuck.deluge.engine`. It was **not** built for the
ChucK DSL. Later, `org.chuck.deluge.firmware2` arrived as a stricter line-by-line C port and
**superseded `firmware/`'s DSP**, leaving `firmware/` as a half-finished migration:

- `FirmwareSound.renderInternal` → `fw2Sound.renderInternal` (DSP delegates to firmware2)
- `FirmwareAudioEngine` master FX bus uses the firmware2 `Compressor`/`Delay`/`Freeverb`

`FirmwareSound` keeps **~68 parallel/shadow fields** and hand-copies them into `fw2Sound` via
`syncParamsToFw2()`. That manual seam — a value that must be copied, to the right place, with no
compiler check — produced **all three Bridge bugs** found in the 2026-06 audit:

- `lfoWaveforms` synced into `lfoConfig[].waveType` but the global render read a *different* array
- `lfo1`/`lfo2` patch-source mapped to the wrong (local) `PatchSource`
- `voicePriority` — a `fw2Sound` scalar the bridge simply **never wrote** (stuck at default)

Plus a silent landmine: `fc.source = c.from.ordinal()` assumes `firmware.PatchSource` and
`firmware2.PatchSource` stay in identical order forever.

## What is genuinely needed vs vestigial

- **Needed** (no firmware2 equivalent): the sequencer/song model (`Song`/`Clip`/`NoteRow`/
  `Arrangement`/`PlaybackHandler`), the song-level mixer + master FX bus (`FirmwareAudioEngine`),
  UI-support (`firmware/gui` OLED + `firmware/hid`, used by ~6 Swing panels), and `BridgeContract`.
- **Vestigial / fragile**: `FirmwareSound`'s shadow fields + `syncParamsToFw2`, and the duplicate
  `PatchSource`/`PatchCable`/`Destination` classes.

## Goal

Make `fw2Sound` the **single source of truth**: `FirmwareSound` becomes a thin façade with no shadow
state and no `syncParamsToFw2`, eliminating the entire "forgot/mis-synced a field" bug class.

## Field taxonomy (the ~68 fields)

- **A — pure 1:1 copies** (~45): `oscTypes`, `lpfMode`/`hpfMode`, `filterRoute`, unison params,
  `oscillatorSync`, `clippingAmount`, retrigger phases, `portamentoKnob`, `polyphonic`,
  `maxPolyphony`, `voicePriority`, `sidechainSend`, `modFXType`, sample settings, … → mechanical.
- **B — derived/transformed** (~12): FM `fmRatio→modulatorTranspose+cents`,
  `delaySyncLevel→delayUserRate`, modFX params read from `patchedParamValues`,
  `samples[]→fw2SampleCache`, `lfoWaveforms→lfoConfig.waveType`,
  `paramNeutralValues/paramKnobs→patchedParamValues`, patch cables → fw2 cable set. → keep the
  logic, but run it at **write-time** against `fw2Sound`.
- **C — bridge-only** (~11): transport seams (`transportTimePerTick`), `fw2SampleCache`,
  `paramKnobsPopulated`, cache keys. → stay on `FirmwareSound`.

## Strategy: keep `FirmwareSound`'s public API stable, swap its backing store

The 6 UI files read/write `FirmwareSound` public fields. So convert each bucket-A field to a
getter/setter pair **backed by `fw2Sound`** (UI call sites change field-access → accessor, but
values/semantics are identical). Bucket-B logic moves into those setters (or a small `recompute()`
for cross-field derivations). When every field is direct, `syncParamsToFw2()` has nothing left.

## Phasing (each phase compiles + full suite green before the next)

0. **Freeze behavior:** golden test snapshotting `fw2Sound`'s full post-build state for a
   representative synth + kit — the behavior-preservation oracle for the whole refactor.
1. **Bucket A, in clusters** (osc / filter / unison / poly / retrigger / sidechain / modFX-type /
   samples): per cluster, back the field with `fw2Sound`, delete its `syncParamsToFw2` line, update
   the few UI readers, run tests.
2. **Bucket B derivations:** move FM-ratio, delay-sync, modFX-extract, sample-cache, LFO-waveform,
   param-knob, and cable-set logic to write directly into `fw2Sound`.
3. **Delete `syncParamsToFw2`** + its ~10 call sites; replace the per-render-block portion with a
   tiny explicit `updateTransport(numSamples, bpm)`.
4. **(Separate branch) unify the duplicate enums:** fold
   `firmware.modulation.patch.PatchSource`/`PatchCable`/`Destination` into the firmware2 ones; the
   lockstep guard then becomes unnecessary.

## Blast radius & risk

- **Files (~8):** `FirmwareSound` (internals), `FirmwareFactory` (writes via accessors), 5 UI files
  (field→accessor), `PureFirmwareEngine` (transport push).
- **Risk:** medium, mitigated by the Phase-0 golden snapshot + existing fidelity / propagation /
  lockstep / completeness tests, and by small green-at-each-step clusters.
- **Out of scope:** sequencer, `FirmwareAudioEngine` master bus, `firmware/gui`+`hid`,
  `BridgeContract` — all load-bearing, untouched.
- **Effort:** ~a focused day; **own branch, not `main`.**

## Verification

- Phase-0 golden `fw2Sound`-state snapshot identical before/after each phase.
- `mvn -pl deluge test` + `-Pslow-tests` green throughout (incl. `PhysicalHardwareFidelityTest`,
  `VoicePriorityPropagationTest`, `PatchSourceLockstepTest`, `Fw2SyncCompletenessTest`,
  `LfoResyncTest`).
- Manual smoke: load a song, live-edit synth params, confirm sound unchanged.

## Stopgap guards already in place (do not regress)

Until the refactor lands, two cheap guards convert the worst failure modes into build failures:

- `PatchSourceLockstepTest` — fails if `firmware.PatchSource` / `firmware2.PatchSource` drift in name
  or ordinal (the `c.from.ordinal()` landmine).
- `Fw2SyncCompletenessTest` — census: every scalar/enum field of `firmware2.Sound` must be classified
  `BRIDGE_SYNCED` or `RUNTIME_OR_DERIVED`; a **new** field fails the build until classified (the
  `voicePriority` bug class) — plus live model→`fw2Sound` propagation checks for key fields.

When the façade refactor removes the shadow state, `Fw2SyncCompletenessTest`'s census still serves as
documentation of which `fw2Sound` fields are model-driven; `PatchSourceLockstepTest` retires with the
duplicate enums in Phase 4.
