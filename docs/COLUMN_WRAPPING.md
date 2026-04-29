# Column Wrapping

When grid mode columns exceed clip length, extra columns mirror back to the
beginning of the clip instead of representing independent steps.

## Problem

Switching from 8×16 to 24×16 with a 16-step clip shows columns 16–23 as empty
cells. The user expects a wider viewport onto the same 16 steps — toggling
column 16 should toggle column 0.

## Solution

In `buildVoiceRow()`, compute an `activeCol` that aliases the visual column to
the clip's actual step range:

```
activeCol = colId % clipLength   (when clipLength < stepCount, CLIP mode)
activeCol = colId                (otherwise)
```

Clip length is read from `ClipModel.getStepCount()`, falling back to
`bridge.getTrackLength()`, then `stepCount`.

`activeCol` replaces `colId`/`c` in every CLIP-mode step operation:

- **Rendering**: `bridge.getStep(engineRow, activeCol)`
- **Toggle**: `bridge.setStep(engineRow, activeCol, !state)`
- **Persist**: `cModel.setStep(modelRow, activeCol, data)`

Mute/SOLO columns are unaffected — they use `colId` directly.

## Example

24×16 grid mode, 16-step clip, 8×16 viewport:

| Visual column | activeCol | Engine step |
|---|---|---|
| 0             | 0         | step 0      |
| 7             | 7         | step 7      |
| 15            | 15        | step 15     |
| 16            | 0         | step 0      |
| 23            | 7         | step 7      |

Toggling column 16 toggles step 0 (same engine step as column 0).
