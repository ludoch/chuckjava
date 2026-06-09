# firmware2 sample-engine port — scoping plan

Companion to `FIRMWARE2_FAITHFUL_PORT.md` (the absolute rule) and `FIRMWARE2_PORT_ROADMAP.md`.
This plans the one remaining DSP-adjacent gap: **sample playback + time-stretch** in firmware2.
It exists because `TimeStretcher` and `VoiceSample` are the last blockers to deleting `firmware/`,
and they are **not** self-contained DSP — so they need an explicit design decision before coding.

## 1. The core finding (why this needs a plan, not just "translate the C")

The C `TimeStretcher` (1245 lines) and `VoiceSample`/`SampleLowLevelReader` (1251 lines) are built on
the Deluge's **cluster-based streaming engine**: audio is read from the SD card in fixed-size
`Cluster`s, lifetimes are managed by ref-count "reasons", and `SampleCache` memoises stretched/perc
data. That machinery models **SD-card hardware streaming** — it does not exist on desktop.

Evidence both desktop ports already side-stepped it:
- `firmware/` plays samples from an **in-RAM `float[] sample.data`** (`engine/VoiceSample.java`), with a
  simplified 112-line `TimeStretcher` — *not* the cluster engine.
- A strict line-for-line port of the C is therefore **impossible**: ~half of it manages cluster/cache
  lifetimes that have no desktop equivalent.

So the faithful unit here is the **time-stretch DSP algorithm**, backed by an **in-RAM sample reader**
that stands in for the cluster/low-level reader (the same adaptation `firmware/` already made). This is
consistent with the roadmap's stance that `storage/`, sample `model/`, and streaming are Java
application/infra code, while the **DSP** must be faithful.

> **Decision needed from the user:** approve "faithful DSP algorithm + adapted in-RAM I/O layer" as the
> port contract for this subsystem (it cannot be 100% line-for-line for the I/O parts). Everything in
> §3 Phase B is a faithful C port; Phase A is an adapter, explicitly not line-for-line.

## 2. What in `time_stretcher.cpp` is DSP vs. infrastructure

| C method | Lines | Class |
|---|---|---|
| `hopEnd` | 242–979 (~740) | **DSP core** — hop detection + moving-average difference search for the best crossfade point + crossfade setup. This is the heart. |
| `readFromBuffer` | 1051–1092 | **DSP** — crossfades the older/newer play heads into the osc buffer. |
| `getTotalDifferenceAbs` / `getTotalChange` | header inlines | **DSP** — the moving-average match metrics. |
| `getSamplePos` | 1227 | trivial DSP |
| `init` / `reInit` / `setupNewPlayHead` | ~250 | **mixed** — play-head setup; the position math is DSP, the cluster acquisition is infra. |
| `beenUnassigned`, `unassignAllReasonsForPerc*`, `rememberPercCacheCluster`, `updateClustersForPercLookahead`, `setupCrossfadeFromCache`, `reassessWhetherToBeFillingBuffer`, `allocateBuffer` | ~250 | **infra** — cluster/cache ref-count lifetime + SD prefetch. Adapt/stub against in-RAM data. |

Faithful-portable DSP core ≈ **~800 lines** (`hopEnd` + `readFromBuffer` + the match metrics + position
math). The remaining ~450 lines are streaming infra to adapt.

## 2b. Revised finding after reading `hopEnd` (entanglement is deeper than estimated)

Reading the C confirmed `hopEnd`/`readFromBuffer` are **not** a cleanly extractable ~800-line DSP block:
- `hopEnd` is interleaved *throughout* with `voiceSample->getPlayByteLowLevel`, `SampleLowLevelReader`,
  `guide->getSyncedNumSamplesIn`/`getBytePosToStartPlayback`/`getBytePosToEndOrLoopPlayback`,
  loop/pre-margin handling, and perc-cache cluster lookahead — even the position math depends on the guide.
- `readFromBuffer` reads `this->buffer`, which `setupCrossfadeFromCache` fills from a `SampleCache`.

So Phase B **cannot start with the DSP core**; it must start with the position/loop/reader math
(`SamplePlaybackGuide` + an in-RAM `SampleReader`). `TIME_STRETCH_ENABLE_BUFFER` and the perc `SampleCache`
are `0`/optional and stay out of scope, which removes a large slice.

**Done so far (faithful + tested):**
- `TimeStretcher` self-contained subset — `getTotalDifferenceAbs`, `getTotalChange`, `getSamplePos`, constants.
- Foundation — `Sample` (in-RAM), `SampleHolder` (`getEndPos`/`getDurationInSamples`), `SamplePlaybackGuide`
  (`setupPlaybackBounds` + byte-pos getters; transport-sync deferred to a seam).
- `SampleReader` — in-RAM `readSamplesResampled` (forward/reverse resampled read, option (b) full precision
  via `SincInterpolator.interpolateWide`). No cluster boundaries / loop / cache yet.

**Then:** pitched-sample milestone — `readSamplesNative`, `getWhichKernel`, `VoiceSample` (native +
resampled dispatch), and loop/one-shot end. The fw2 sampler now plays pitched samples end-to-end.

**Time-stretch DSP — now complete + tested** (`TimeStretcher`):
- `computeHopParameters` (beam-width/crossfade params from the ratio),
- `Sample.getAveragesForCrossfade` (the similarity metric),
- `getTotalDifferenceAbs`/`getTotalChange` (comparators), `getSamplePos`, constants,
- `searchForCrossfadeOffset` — the bidirectional sliding-window crossfade-point search (hopEnd 604-862),
  re-derivation-verified over forward/reverse resampled cases.

**Remaining = assembly, not new DSP:** wire the pieces into a hopEnd-equivalent render — two `SampleReader`
play-heads (older + newer), `setupNewPlayHead` (reposition the new head reader to `newHeadBytePos +
bestOffset`, `additionalOscPos`), and the crossfade mix (`crossfadeProgress`/`crossfadeIncrement`)
between the heads in the VoiceSample render loop. Plus the guide transport-sync seam
(`getSyncedNumSamplesIn`). `TIME_STRETCH_ENABLE_BUFFER`/`SampleCache` stay out of scope (= 0/optional).

## 3. Proposed phases (revised)

**Phase A — in-RAM sample-access adapter (infra, NOT line-for-line; ~1–2 days)**
- `Sample` (fw2): wraps the loaded PCM as an int array + metadata (channels, rate, loop points,
  audioDataStart/length). Mirror only the fields `TimeStretcher`/`VoiceSample` read.
- `SampleReader` (fw2): replaces `SampleLowLevelReader` + `Cluster` reads with direct indexed reads of
  the in-RAM array, exposing the same surface `hopEnd`/`readFromBuffer` call (read N samples at a
  byte/sample pos, with interpolation via the already-ported `SincInterpolator`).
- Stub the cluster "reasons"/cache lifetime methods to no-ops (document each as desktop-N/A).

**Phase B — faithful TimeStretcher DSP port (line-for-line; ~3–5 days)**
- ✅ Done: `getTotalDifferenceAbs`, `getTotalChange`, `getSamplePos`, constants (the self-contained subset).
- Next: port `SamplePlaybackGuide` position/loop/sync math + a `SampleLowLevelReader`-equivalent over the
  Phase-A `SampleReader` FIRST (hopEnd depends on them throughout — see §2b), then `hopEnd` +
  `readFromBuffer` verbatim, citing file:line per the rule.
- Reuse the ported `SincInterpolator` for sub-sample reads (the C uses the same interpolator).
- Keep `SampleCache` (perc/stretch memoisation) **out of scope** — `TIME_STRETCH_ENABLE_BUFFER`/perc cache
  are `0`/optional; recompute instead. Add later only if a perf need appears.

**Phase C — VoiceSample integration + verification (~2–3 days)**
- Wire a fw2 `VoiceSample` that drives the TimeStretcher and feeds `Voice`.
- Verification (no firmware/ oracle — firmware/'s TimeStretcher is the 112-line simplification, non-
  faithful): golden-WAV tests — render a known sample at several time-stretch ratios + pitch, compare to
  a committed golden, and assert the crossfade-point search picks the C's choice on a deterministic input
  (re-derive `getTotalDifferenceAbs`/`hopEnd` selection in the test, the contract-test pattern used for
  `combineHitStrengths`/`toPositive`/Sinc).

## 4. Out of scope (and why)
- **Cluster/SampleCache/SD streaming** — SD-card hardware; desktop uses RAM (§1).
- **FFT / vocoder** — the FFT is the third-party **NE10 ARM NEON** library, not a Deluge C function;
  separate, larger effort. See `firmware2-port-boundary` memory.
- **SampleRecorder** (1564 lines) — recording, not playback.

## 5. Risk / sizing
- Phase B is the only strictly-faithful part and the riskiest (the hop/crossfade-search is intricate
  fixed-point). ~800 lines, mechanical but exacting.
- Total ≈ 6–10 focused days. Biggest unknown: how much of `init`/`setupNewPlayHead`'s position math
  entangles with cluster boundaries (may force more of Phase A than estimated).
