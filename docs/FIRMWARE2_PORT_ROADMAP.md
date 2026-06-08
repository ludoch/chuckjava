# firmware2 port roadmap — next subsystems

Companion to `FIRMWARE2_FAITHFUL_PORT.md` (the rule + protocol + what's done). This file enumerates
the **remaining work**, grounded in the 9 failing tests (`mvn -pl deluge test` → 19/275), each mapped to
its C source. Every item under bucket **A** is a faithful line-for-line port per the absolute rule; open
the cited C file first.

## How the failures cluster

| Failing test | Symptom | Bucket | C source |
|---|---|---|---|
| `SidechainRoutingParityTest.sidechainPatchCableDucksGlobalVolume` | SIDECHAIN source goes negative but summed output not ducked (65% drop missing) | **A1** | `processing/sound/sound.cpp` (global-param render), `modulation/sidechain/sidechain.cpp` |
| `ArpParityTest` | arp produces no notes (`heard=[]`) | **A2** | `modulation/arpeggiator.cpp` (1989), `arpeggiator_rhythms.h` |
| `LiveAutomationMpeTest` | MPE expression not applied | **A3** | `voice.cpp` expression sources |
| `Firmware2IntegrationTest` | `fw2Voices` not empty when flag off | **B1** | bridge (`FirmwareSound`) |
| `AudioIntegrityTest` | not silent after release (output `3435008`) | **B2** | voice culling (`voice.cpp` cull) + bridge |
| `DelugeE2ETest` | song renders near-silent (`peak≈2.4e-7`) | **B3** | bridge / song render |
| `FirmwareSoundTest` | `expected 1 but 0` | **B** | bridge |
| `FirmwarePatchCableTest.envelopeToCutoffSweepsFilterOverTime` | env2→cutoff sweeps ~1.8% (needs 10%) | **C1** | calibration — `voice.cpp` env shape; needs hardware A/B |
| `FirmwareGoldenSignatureTest` | dx7 brightness 0.095 vs golden 0.561 | **C2** | calibration — needs hardware A/B |
| `DigitalAudioFidelityTest` | steady-state level low | **C** | calibration / level re-baseline |

Note: `FirmwarePatchCableTest.velocityToCutoffBrightensWithVelocity` **passes** — the per-voice patcher
(LOCAL params) is faithful. The env→cutoff failure is sweep *depth* (calibration), not the patch path.

## Bucket A — faithful C subsystem ports (do these; follow the rule)

### A1. Sidechain ducking of global volume  ✅ DONE (commit d172db0c)
- Root cause was **not** a missing port: the `Sound::render` global-param duck path already works
  (legacy proves it). The fw2 bridge copied `paramKnobs` (factory-only) into the fw2 patched-param
  set, so a **directly-constructed** FirmwareSound (tests setting only `paramNeutralValues`) got
  zero knobs → the fw2 voice ignored SUSTAIN/volume and decayed → nothing to duck. Fix: a
  `paramKnobsPopulated` flag (set by the factory); the bridge falls back to `paramNeutralValues`
  when unset — byte-identical for factory sounds. Also made `GranularProcessor` grain selection
  deterministic (was `Math.random()`) and re-baselined the now-stable granular audibility bar.
- **Key insight for bucket B**: any directly-constructed FirmwareSound test on the fw2 default now
  honors `paramNeutralValues`. Tests still failing that build sounds directly may benefit from / need
  awareness of this. The legacy↔fw2 param-domain split (paramNeutralValues = curve-outputs for env
  RATES; raw bipolar knobs otherwise) is the recurring bridge subtlety.

### (original A1 notes) Sidechain ducking of global volume
- The `SIDECHAIN` patch source already computes correctly (goes negative on a kick). The gap: a cable
  `SIDECHAIN → GLOBAL_VOLUME_POST_REVERB_SEND` must duck the **summed Sound output**, which the C does in
  `Sound::render` via the global source values + global-param patching — not in the per-voice `Voice`.
- Port: `processing/sound/sound.cpp` global-source/patching path that applies `GLOBAL_VOLUME_POST_REVERB_SEND`
  (and the post-fx-volume params) to the mixed buffer; the follower itself is `modulation/sidechain/sidechain.cpp`
  (214) — small, self-contained Q31 envelope follower (attack/release LUTs). `dsp/compressor/rms_feedback.cpp`
  (167) is the separate *audio compressor*, not this path.
- Closes `SidechainRoutingParityTest` (65% drop).

### A2. Arpeggiator
- Port `modulation/arpeggiator.cpp` (1989) + `arpeggiator.h` + `arpeggiator_rhythms.h`. The arp holds note
  state and emits noteOn/noteOff on a clock; today `heard=[]` means those events never reach firmware2 voices.
- Mirror the C `Arpeggiator::render`/`switchAnyNoteOn`/gate logic; then route emitted notes through the same
  path `FirmwareSound` uses to trigger fw2 voices.
- Closes `ArpParityTest`.

### A3. MPE / expression sources
- Port the `voice.cpp` expression handling: the per-voice expression source values (X/bend, Y/timbre,
  aftertouch/pressure) feeding `sourceValues[PatchSource.*]`, plus the polyphonic-expression application.
- Closes `LiveAutomationMpeTest`.

### A4. Full patcher fidelity (`patch_cable_set.cpp`, 1203) — lower priority
- firmware2 `Patcher` is faithful for static per-block patching (velocity→cutoff passes). The C
  `PatchCableSet`/`performPatching(sourcesChanged, …)` adds the `sourcesChanged` bitmask, ordered
  destinations, `sourcesPatchedToAnything`, range cables, and automation smoothing. Port for full
  automation fidelity once A1–A3 land. No currently-failing test isolates this alone.

## Bucket B — firmware2 ↔ FirmwareSound bridge fixes (integration, not C-file ports)

- **B1** `Firmware2IntegrationTest`: ✅ DONE (commit 5c4d794c). The flag-off path was already correct
  (legacy voices populated, fw2Voices empty); the test relied on a stale "default OFF" assumption and
  now opts out of fw2 explicitly. (`useFirmware2` defaults on.)
- **B2** `AudioIntegrityTest`: after release, the voice must be **culled** so the engine emits true silence
  (faithful to `voice.cpp` end-of-release cull → voice removed from the active list), and the bridge must
  drop it from `fw2Voices`.
- **B3** `DelugeE2ETest` / `FirmwareSoundTest`: song-level render path produces near-silence — trace the
  bridge from clip playback → fw2 voice trigger → master buffer.

## Bucket C — hardware-calibration re-baseline (BLOCKED on device A/B)

Do **not** blind-tune these — they encode the faithful (quieter, 2^29-unity) engine's real levels and need a
hardware capture to set honest thresholds:
- **C1** env2→cutoff sweep depth (current ~1.8%); the test itself flags the bipolar-vs-unipolar envelope
  source question.
- **C2** `FirmwareGoldenSignatureTest` golden signatures (dx7 brightness 0.561, fm peak, lfo tremolo, env decay).
- `DigitalAudioFidelityTest` steady-state level.

## Order of attack
A1 ✅ → B1 ✅ → **B2 (voice cull / silent-after-release) ← next** → B3 (E2E silence) → A2 (arp) →
A3 (MPE) → A4 (patcher) → C (with hardware).

## Status: 17 failures / 275 (was 19 at the start of this pass, 32 at the start of the arc).
Remaining: AudioIntegrityTest (silent-after-release, kit gating), DelugeE2ETest (song silence),
DigitalAudioFidelityTest (kit/steady-state level), FirmwareGoldenSignatureTest ×6 (golden re-baseline),
FirmwarePatchCableTest (env2→cutoff depth), FirmwareSoundTest (mono/poly), LiveAutomationMpeTest ×3 (MPE),
ArpParityTest (arp). Buckets: B2/B3 (bridge), A2/A3 (ports), C (hardware-calibration).
