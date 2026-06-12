# Upstream bug report (ready to file on SynthstromAudible/DelugeFirmware)

Found 2026-06-12 while running our line-for-line Java port of the firmware DSP. Verified against
the local checkout at commit `22314a61`. Re-check line numbers against current `community` HEAD
before filing. Our port carries the fix with a documented-deviation comment
(`firmware2/LivePitchShifter.java`, commit `64f00093`).

---

**Title:** Out-of-bounds stack read in `LivePitchShifter::hopEnd` —
`readPos[kNumMovingAverages + 1]` indexes one past the array

**Affected file:** `src/deluge/processing/live/live_pitch_shifter.cpp`, function
`LivePitchShifter::hopEnd` (declaration at line 632, OOB read at line 661; verified at commit
`22314a61`).

**Description**

`hopEnd` declares a stack array sized `kNumMovingAverages + 1` and later reads index
`kNumMovingAverages + 1`, which is one element past the end:

```cpp
// line 632 — kNumMovingAverages = 3 (definitions_cxx.hpp:786), so this is int32_t readPos[4],
// valid indices 0..3:
int32_t readPos[TimeStretch::Crossfade::kNumMovingAverages + 1];

readPos[0] = averagesStartPosNewHead;                       // line 634
...
for (int32_t i = 0; i < TimeStretch::Crossfade::kNumMovingAverages; i++) {
    ...
    readPos[i + 1] = ...;                                   // fills readPos[1..3]
}
...
else {
    // line 661 — reads readPos[4]: OUT OF BOUNDS (the array has indices 0..3)
    searchSizeBoundary =
        (uint32_t)(numRawSamplesProcessedLatest - readPos[TimeStretch::Crossfade::kNumMovingAverages + 1])
        & (kInputRawBufferSize - 1);
}
```

The read is reached whenever the rightward search direction is taken (`searchDirection == 1`),
i.e. routinely during live pitch-shifting.

**Impact**

This is undefined behaviour, but in practice a read-only overread of adjacent stack memory (most
likely `newHeadRunningTotals[0]`, declared immediately after — exact layout is
compiler/optimization dependent). No crash or corruption on hardware; the consequence is that
`searchSizeBoundary` for the rightward crossfade-position search is computed from a garbage
value. The subsequent `& (kInputRawBufferSize - 1)` keeps it in range, so the result is an
*arbitrary* search boundary rather than the intended one — the hop-splice search may scan a
wrong-sized window, which can pick a suboptimal crossover point (subtle audio-quality effect,
not a safety issue on the device). A UBSan/ASan host build would flag it immediately.

**Suggested fix**

The semantically intended value appears to be the **end of the moving-averages window**, which is
the last *filled* element:

```cpp
searchSizeBoundary =
    (uint32_t)(numRawSamplesProcessedLatest - readPos[TimeStretch::Crossfade::kNumMovingAverages])
    & (kInputRawBufferSize - 1);
```

(`readPos[3]` = `readPos[0] + 3 * lengthPerMovingAverage`, wrapped — i.e. the first sample past
the averages window, consistent with the boundary's purpose of limiting the search to data that
has actually been received.)

**How it was found**

We maintain a line-for-line Java port of the firmware DSP. Java's bounds checking turned this
exact read into an `ArrayIndexOutOfBoundsException: Index 4 out of bounds for length 4` at the
transcribed line, during live input pitch-shifting (any non-unity note on an
`inLeft`/`inRight`/`inStereo` oscillator source). The C silently reads whatever sits next to the
array on the stack.

**Reproducer hint:** any sustained pitch-shifted live input reaches `hopEnd` with
`searchDirection == 1`.
