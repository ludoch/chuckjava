# Deluge faithful-port — continuation handoff (2026-06-05)

Self-contained handoff so any session (incl. Gemini, which has no access to the prior
chat or Claude's memory files) can continue. **The goal is a bit-faithful Java port of the
Deluge firmware DSP — match the real hardware, not "close enough".** The firmware C++ source
is the ground truth.

---

## 0. Start here: continue from the current worktree, not from a clean checkout

- **Do not discard the local WIP.** This handoff is for resuming from the current dirty worktree,
  not for starting fresh from `main`.
- **Do not `reset --hard`, `checkout --`, or rebuild the branch from scratch.** There is validated
  in-progress work in the tree, including merge-resolution content that compiles and passes tests
  even though Git still reports `UU` for two files in the index.
- **Treat `docs/PORT_HANDOFF.md` as the map for the current worktree state.** The historical
  sections below describe what is already merged on `main`; the new sections 2.1–2.3 describe what
  is only present locally right now.

## 1. Ground rules

- **Firmware source = ground truth.** Reference C++ lives at `~/a/DelugeFirmware/src/deluge/`.
  Port the exact integer/fixed-point math + lookup tables; do NOT substitute float approximations
  (`Math.pow/sin/exp/...`) for the firmware's fixed-point tables.
- **Verify every change with a pinning unit test** that asserts hand-traced firmware values
  (see `FirmwareParamCurvesTest`, `FirmwareEnvRateTest` for the pattern). Hardware A/B only
  *confirms* your reading of the firmware; it does not define correctness. We currently have NO
  hardware access (until ~Sunday), so lean on source + pinning tests.
- **Supported engine = the PURE firmware engine** (`org.chuck.deluge.firmware.*`). The legacy
  `DelugeEngineDSL` ("--hifi") is UNSUPPORTED — do not use it in tests.
- **Commit/push discipline:** for this WIP, continue from the current worktree first; do not throw
  away local changes just to recreate them on a fresh branch. Once the current worktree is in the
  desired state, keep the deluge suite green and commit only the intended files. Historical note:
  earlier fixes were being merged onto `main` one by one. Do not treat the older Claude-specific
  trailer from prior sessions as a standing requirement for new commits. Exclude noise:
  `comparison/*.wav`, `.claude/settings.local.json`, `jdk25/`.

## 2. Build / test / run

```
# build + test the deluge module (uses JDK 25 + preview + vector incubator via the pom)
mvn -pl chuck-core,deluge -am test            # full suite (deluge ~229 tests)
mvn -pl deluge test -Dtest=FirmwareEnvRateTest # one test class

# NOTE: chuck-core's ChuckMachineApiTest#testMachineEval is a FLAKY timing test under parallel
# load (passes when run alone). A single failure there is not your change.

# Render a patch to WAV (A/B harness):
mvn -pl deluge -am test-compile
DEPS=$(mvn -q -pl deluge dependency:build-classpath -Dmdep.outputFile=/dev/stdout | tail -1)
CP="chuck-core/target/classes:deluge/target/classes:deluge/target/test-classes:$DEPS"
java --enable-preview --add-modules=jdk.incubator.vector -cp "$CP" \
    org.chuck.deluge.reproduce.RenderPatchToWav "<patch.XML>" <midiNote> <out.wav> [seconds] [velocity]
```

Hardware patches live at `/home/ludo/ludocard/SYNTHS/*.XML` (196 presets) — **this is the mounted SD
card, machine-local; a fresh session only has it if `ludocard` is mounted.** The three A/B patches:
`049 Basic FM.XML` (FM, C3), `009 Hoover Bass.XML` (filter/velocity/pan, C2),
`128_SYNTH_DUAL_MOD_C5.XML` (LFO+env, C5). Hardware recording from the prior session:
`~/a/REC00009.WAV` (049 Basic FM @ C3). **Because the SD patches are machine-local, the P1
golden-WAV suite must use PROGRAMMATICALLY-built sounds (self-contained), not these XML files;
the XML patches are only for the hardware A/B (P5).**

## 2.1 Current repo state (2026-06-05, HEAD `67008446`)

- **Current content is green:** on this worktree, both `mvn -pl deluge spotless:apply test` and
  `mvn clean package` completed successfully.
- **There is broader local DSP WIP besides the four files summarized below.** Preserve the rest of
  the modified deluge files unless you have a specific reason and user approval to revert them.
- **Git index caveat:** `deluge/src/main/java/org/chuck/deluge/firmware/engine/FirmwareFactory.java`
  and `deluge/src/main/java/org/chuck/deluge/firmware/engine/FirmwareVoice.java` still show as `UU`
  in `git status` because the merge resolution content has not been staged yet. The files themselves
  no longer contain conflict markers and do compile/test as-is.
- **Uncommitted test-fixture updates in this worktree:**
  - `FirmwareSynthVoiceTest` now uses full track volume (`setVolume(1.0f)`) so its audibility
    assertions match the current firmware-style volume curve path.
  - `RingModParityTest` now builds its fixture through `FirmwareFactory` with both oscillators,
    osc2 pitch, and track volume explicitly configured, so the parity check follows the real
    model→factory→engine path instead of constructing a partially-configured `FirmwareSound`
    directly.

## 2.2 In-flight implementation choices already validated on this worktree

If you re-hit the unresolved merge in another session, keep these choices unless you have new
firmware evidence:

- **Keep the factory's firmware-style volume mapping** in
  `FirmwareFactory.mapModelToSound(...)`: `LOCAL_VOLUME`, `LOCAL_OSC_A_VOLUME`,
  `LOCAL_OSC_B_VOLUME`, and `LOCAL_NOISE_VOLUME` should continue to use
  `normToBipolarParamVolume(...)`, not the older direct `float * 2147483647.0` mapping. The direct
  mapping was what made the low-volume characterization tests fail on this worktree.
- **Keep cutoff stored as the recovered knob, not a pre-combined exp value:** use
  `cutoffKnobFromHz(...)` for LPF/HPF neutral values. Do not resurrect the abandoned
  `cutoffComboFromHz(...)` branch unless you re-thread the entire exp-combine path and pin it with
  tests.
- **Keep the current `FirmwareVoice` final-gain ordering** (render voice → filter →
  apply `env0 * LOCAL_VOLUME` for non-FM). The alternate conflict side had extra pre-filter gain
  code and a duplicate local `filterGain` assignment; the current ordering is the one that compiled
  cleanly and passed `mvn clean package` on this worktree.

## 2.3 Best next implementation target

Start **P1 Golden-WAV regression suite** from the existing self-contained helpers instead of inventing
new fixtures from scratch:

- Reuse the programmatic model-building pattern in
  `deluge/src/test/java/org/chuck/deluge/firmware/engine/FirmwareSynthVoiceTest.java`.
- Reuse the factory-driven ring-mod fixture shape in
  `deluge/src/test/java/org/chuck/deluge/firmware/engine/RingModParityTest.java`.
- Put the new golden/signature tests under `deluge/src/test/java/org/chuck/deluge/firmware/engine/`
  or the existing reproduce area, but keep them **programmatic-first** so CI does not depend on the
  mounted SD card.
- Prefer stable signatures (RMS / peak / selected harmonic bins) over raw byte checksums; the
  current tests already show the right pattern for computing RMS, mean, brightness, and
  zero-crossing/frequency-style metrics.

## 3. Render pipeline (how a patch becomes audio)

`DelugeXmlParser.parseSynth(File) -> SynthTrackModel` → `ProjectModel.addTrack` →
`FirmwareFactory.createSong(project) -> Song` → `(InstrumentClip) song.clips.get(0)).sound` is a
`FirmwareSound` → `sound.triggerNote(note,vel)` / `sound.releaseNote(note,-1)` +
`sound.renderOutput(StereoSample[128], 128, reverbBufferOrNull)` per block.
Per-voice DSP is in `FirmwareVoice.render`; sound-level FX in `FirmwareSound.renderInternal`;
master mix/reverb in `FirmwareAudioEngine`.

**Gotcha:** the factory installs `AutoParam`s that override `paramNeutralValues`. To force a param
in a test you must also set `sound.paramManager.getAutomatedParam(paramId).currentValue`.

---

## 4. DONE before the 2026-06-05 session (all on `main`, HEAD f350e07a) — do NOT redo

| commit | fix |
|---|---|
| 879b8dab | **Native 2-op FM**: replaced the dexed `FmCore` approximation with a faithful port of `voice.cpp` `renderSineWaveWithFeedback`/`renderFMWithFeedback`/`renderFMWithFeedbackAdd` on `SineOsc.doFMNew` (2 modulators + feedback + mod1→mod0; carriers FM'd by the modulator buffer). Modulator amount via the Deluge volume parabola from the raw knob. **Also fixed a cable-routing bug**: `destStr.contains("VOLUME")` sent `modulator1/2Volume` cables to master `LOCAL_VOLUME`. |
| 3ca21c0d | **Param-curve foundation** (`ParamCurves` = `getParamNeutralValue`/`getParamRange`; `FirmwareUtils.getFinalParameterValueVolume/Linear/Hybrid/Exp`). **Fixed a real `getExp` bug**: `increaseMagnitudeAndSaturate` used `>= 0` (so magnitude 0 hit `1<<31` overflow → force-saturate) + unsigned `>>>`; firmware uses `> 0` + arithmetic `>>`. |
| 5f34c9ce | **Filter cutoff range**: removed non-firmware caps (`BasicFilterComponent` moveability `min(1073741823)`, the `min(67108864)` freq clamps). Open cutoff 6.3 kHz → 19.9 kHz. |
| 3b47c06a | **LFO rate**: dropped the ad-hoc `200+pow(2,...)*500`; the unsynced LFO phase increment is the exp-curved rate param directly (`getExp(121739, combineExp(knob))`). Preserved raw rate knob through the parser. |
| 0b021092 | **Envelope rates**: per-stage firmware curves — attack `getExp(4096,-combo)`, decay/release `neutral*lookupReleaseRate` (ported `releaseRateTable64`). Raw env knobs preserved; programmatic time-in-seconds falls back to `190.2/time`. |
| de64741d | **Pan law**: linear `shouldDoPanning` (centre = full both channels) + fixed mis-centering (`LOCAL_PAN` is now bipolar, centre 0). Was constant-power cos/sin on a mis-encoded param (centred sound rendered hard-right & −3 dB). |
| 70b69ca1 | **Filter makeup gain**: `FirmwareVoice` discarded `filterSet.setConfig(...)`'s return (`filterGain`). Capturing+applying it fixes ~2.4× hot output / clipping. |
| f350e07a | A/B harness `RenderPatchToWav` + `docs/HARDWARE_AB_PLAN.md`. |

## 5. VERIFIED FAITHFUL — do NOT investigate (already checked vs firmware)

- **Pitch** (`pow(2,n/12)` is exact ET; firmware `noteFrequencyTable` differs by ~0.007 cent).
- **Oscillators** (`renderCrude*` for `tableNumber < 6` matches the firmware at normal CPU load
  `tableNumber < cpuDireness+6`).
- **`fastPythag`** — dead code, unused.
- **Reverb** — wired & functional (factory sets `reverbSendAmount`; `FirmwareAudioEngine.masterReverb.process` mixes back).
- **Velocity** — works for cabled patches (009 Hoover Bass vel30→120 RMS ratio 2.29).
- **Master/patch volume** — `<volume>` is applied (as `LOCAL_VOLUME`); patches differ in level.

---

## 6. PENDING WORK (prioritised)

### P1 — Golden-WAV regression suite (hardware-free, do FIRST)
Render representative patches to committed reference signatures and assert in CI. Locks in the 7
fixes, fixes the documented test-gap (no golden WAVs), and **becomes the exact hardware-A/B baseline**.
Recommended approach (robust, no SD-card dependency): build the sounds **programmatically** (like
`FirmwareSynthVoiceTest`) and assert on a stable signature (peak, RMS, and a few single-bin DFT
harmonic magnitudes — NOT a raw byte checksum, which is brittle across JITs). Cover:
- saw + LPF (sweep a couple cutoffs) — exercises filter range + makeup gain
- native FM (set `synthMode=FM`, `fmModulatorAmountBase[0]`, `fmRatio1`) — FM engine
- LFO→volume tremolo at a known rate — LFO rate
- envelope attack/decay/release shape — env rates
- ring-mod, and a DX7 patch — coverage
Also add an XML-driven render of `049 Basic FM` @ C3 asserting it is bright (energy in high harmonics).

### P2 — Concrete-bug audit vs firmware source (mostly low-risk)
The "ignored return / wrong shift / wrong constant" pattern found 3 real bugs this session
(`getExp`, `filterGain`, cable routing). Keep scanning, comparing Java ⇄ firmware line-by-line:
- `dsp/fx/ModFXProcessor`, `SrrBitcrushProcessor`, `EqProcessor`, `dsp/reverb/*`, `Stutterer`,
  `SideChain`, `GranularProcessor` — check shifts/constants and that return values aren't discarded.
- Note `srrBitcrush.process`/`modFX.processModFX` modify a `postFXVolume` int[] that is then
  **discarded** (never multiplied into the buffer). Minor (only the bitcrush/modFX makeup), but
  verify against the firmware whether it should be applied.

### P3 — Master compressor (LEVEL-SHIFTING — ideally needs hardware A/B)
`FirmwareAudioEngine` line ~68 has `// masterCompressor.renderVolNeutral(masterBuffer, Q31.ONE);`
**commented out**; the firmware applies a song master compressor (`audio_engine.cpp:899`
`globalEffectable.compressor.render(buf, masterVolAdjL>>1, ...)`). Enabling it changes ALL output
dynamics and the `RMSFeedbackCompressor` port is unverified (heavy float exp/log/sqrt). Verify the
port vs `dsp/compressor/*` in the firmware, pin with tests, then enable. Re-baseline P1 goldens.
**Risk: defer until hardware A/B unless the port can be confidently verified from source.**

### P4 — #8 Patcher + volume-scale (BIG, STRUCTURAL, LEVEL-SHIFTING)
The firmware uses a **2^29-unity** volume scale (max "4.0" at 2^31) and threads `GLOBAL_VOLUME_POST_FX`
(post-FX master, the patch `<volume>`) through the output. The Java uses **2^31-unity** per-voice
volume and routes `<volume>` to `LOCAL_VOLUME` (per-voice) instead of `GLOBAL_VOLUME_POST_FX`
(post-master); `postFXVolume` is hardcoded `Q31.ONE` in both `FirmwareSound.renderInternal` and
`GlobalEffectable.renderOutput`. The approximate `Patcher` also doesn't fold the base knob and uses a
linear approx instead of the volume parabola. A **back-compute approach was tried and FAILED for
volume** (the engine's 2^31-unity scale can't round-trip the firmware's 2^29-unity curve, max ~0.25).
The faithful fix needs **raw-knob plumbing + the firmware output scale through every consumer** (osc
amplitude, filter, master) — broad, re-baselines all E2E peaks. Treat as a deliberate multi-commit
effort; ideally with hardware A/B. NOT a functional gap (patch volume already scales output via
`LOCAL_VOLUME`); it's a staging/scale faithfulness issue.

### P5 — Hardware A/B (when access returns)
Follow `docs/HARDWARE_AB_PLAN.md`: record 049 Basic FM (C3), 009 Hoover Bass (C2, two velocities),
128_SYNTH_DUAL_MOD_C5 (C5); render the Java side with `RenderPatchToWav`; compare spectra/level.
**Leave the gold/cutoff knobs at the patch's saved positions** — physical knob moves override stored
values and won't match (this bit us with 049: its stored LPF cutoff is genuinely low).

---

## 7. KEY GOTCHAS / domain notes

- **Volume scale divergence (root of P3/P4):** firmware volume params output 2^29 = unity (headroom to
  2^31 = "4.0"); Java oscillators treat 2^31 = unity. This is why back-compute fails for volume and why
  enabling master volume/compressor blindly would clip.
- **`getFinalParameterValueVolume`** (parabola) vs **`Linear`** vs **`Hybrid`** (pan) vs **`Exp`**
  (cutoff/LFO/pitch) — pick the curve by param index (`FIRST_LOCAL_NON_VOLUME=7`,
  `FIRST_LOCAL_HYBRID=19`, `FIRST_LOCAL_EXP=24`). Ported in `FirmwareUtils`.
- **Filter:** the per-voice ladder/SVF (`FirmwareVoice.filterSet`) returns a makeup `filterGain` from
  `setConfig` that MUST be applied (now is). `ONE_Q16 = 134217728` (misnamed; it's 2^27).
- **DIAG debug spam:** `FirmwareVoice.noteOff`/`FirmwareSound.releaseNote` print `[DIAG ...]` to
  stdout on every note. Harmless but noisy — a good cleanup task (gate behind a static flag or remove).
- **DX7** path (`sound.isDx7()`, `Dx7Engine`) is separate from native FM; verified working earlier.

## 8. File map

- Per-voice DSP: `firmware/engine/FirmwareVoice.java` (osc render, FM `renderFm`, env/LFO, pan, filter).
- Sound/FX chain + global LFO: `firmware/engine/FirmwareSound.java`.
- Master mix/reverb/delay/compressor: `firmware/engine/FirmwareAudioEngine.java`,
  `firmware/engine/GlobalEffectable.java`.
- Param curves: `firmware/modulation/params/ParamCurves.java`, `firmware/util/FirmwareUtils.java`,
  `firmware/util/Q31.java`, `firmware/util/LookupTables.java`.
- Patcher (approximate): `firmware/modulation/patch/Patcher.java`.
- Filters: `firmware/dsp/filter/{FirmwareFilter,LpLadderFilter,HpLadderFilter,SVFilter,BasicFilterComponent,FilterSet}.java`.
- Factory (model→engine): `firmware/engine/FirmwareFactory.java`. Parser: `xml/DelugeXmlParser.java`,
  `xml/DelugeHexMapper.java` (`hexToQ31` preserves raw knobs). Model: `model/SynthTrackModel.java`.
- A/B harness: `deluge/src/test/.../reproduce/RenderPatchToWav.java`. Plan: `docs/HARDWARE_AB_PLAN.md`.
- Feature map: `docs/FIRMWARE_FEATURES_MAPPING.md`.

## 9. Methodology checklist for each fix

1. Find the Java code; find the firmware equivalent in `~/a/DelugeFirmware/src/deluge/`.
2. Port the exact integer math + tables. No float shortcuts for what the firmware does in fixed point.
3. Add a unit test pinning hand-traced firmware values (compute by hand from the C++).
4. `mvn -pl deluge test` green. Re-baseline any characterization tests whose values legitimately shift
   (explain why in the commit).
5. Branch off `main`, commit (Co-Authored-By line), and hand back for merge/push.
