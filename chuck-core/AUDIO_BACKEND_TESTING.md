# Testing the pluggable audio backends

`ChucK-Java` can drive audio through one of five backends, all implementing
`org.chuck.audio.backend.AudioBackend`/`AudioBackendStream`
(`chuck-core/src/main/java/org/chuck/audio/backend/`):

| Backend | Platform | Verification status |
|---|---|---|
| `JavaSound` | all | Default. Battle-tested (this is the original engine). |
| `ALSA` | Linux | **Verified against real hardware.** |
| `JACK` | Linux/macOS | **Verified against a real `jackd` server.** |
| `CoreAudio` | macOS | **Verified against real macOS hardware (`DefaultOutputUnit` FFM downcalls & upcalls).** |
| `WASAPI` | Windows | Full COM implementation from near-scratch. **Never run — no Windows available where this was written.** |

None of the four non-`JavaSound` backends are auto-selected by default —
`ChuckCLI`'s backend resolution (`chuck-cli/src/main/java/org/chuck/ChuckCLI.java`,
`resolveAudioBackend()`) is opt-in only, specifically because `CoreAudio`/`WASAPI`
were, until verified, silent stubs. Selecting one requires an explicit system
property.

## Selecting a backend

```bash
java --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
  -Dchuck.audio.backend=<name> \
  -jar chuck-cli/target/chuck-cli-*.jar path/to/script.ck
```

`<name>` is case-insensitive: `alsa`, `jack`, `coreaudio`, `wasapi`, `javasound`.
Resolution goes through `AudioBackendRegistry.getBackendByName(name)`
(`AudioBackendRegistry.java`) — build the CLI first if `chuck-cli/target/` is stale:

```bash
mvn -pl chuck-cli -am package -DskipTests
```

`ChuckCLI` prints `[chuck]: using audio backend: <Name>` on success, or a `⚠️`
warning + silent fallback to `JavaSound` if the requested backend isn't available
on the current machine (e.g. requesting `wasapi` on Linux).

## ALSA (Linux) — already verified, re-verify after any change

```bash
# Fast (no device needed - queries ALSA's config/plugin registry):
mvn -pl chuck-core -am test -Dtest=AlsaNativeTest,AlsaBackendTest

# Real hardware (needs a PCM device — plughw:*/sysdefault:* or a working "default"):
mvn -pl chuck-core -am test -Pslow-tests -Dtest=AlsaBackendTest
```

Manual end-to-end: `-Dchuck.audio.backend=alsa` per above. If `"default"` fails
with `EPERM` (observed under a PipeWire-managed `"default"` PCM in a container),
that's environmental, not a bug — `AlsaBackend.openWithFallback()` already falls
through to `plughw:`/`sysdefault:` candidates automatically.

## JACK (Linux/macOS) — already verified, re-verify after any change

Needs a running `jackd`. On Debian/Ubuntu:

```bash
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y jackd2
# The dummy backend needs no real hardware and still drives the real process-callback
# graph on its own timer — good enough to test the client/port/callback plumbing:
jackd -d dummy -r 44100 -p 512 &
```

(If `apt-get install` appears to hang, it's very likely stuck on jackd2's
debconf realtime-priority prompt — re-run with `DEBIAN_FRONTEND=noninteractive`
set, or `sudo dpkg --configure -a` with that same env var if it's already stuck
mid-install.)

```bash
mvn -pl chuck-core -am test -Dtest=JackBackendTest              # fast, enumeration only
mvn -pl chuck-core -am test -Pslow-tests -Dtest=JackBackendTest # real server, real ports
```

Confirm actual port connections independently of the test assertions:

```bash
jack_lsp -c
# Expect to see, while a stream is open+started:
#   system:playback_1
#      ChucK-Java:out_left
#   ChucK-Java:out_left
#      system:playback_1
#   ... (mirrored for playback_2/out_right, and capture_*/in_* if numInputChannels > 0)
```

No RT scheduling permission in a typical container (`Cannot use real-time
scheduling` in `jackd`'s own log) causes some underruns under load — that's
sandbox noise, not a code bug; a real desktop with `rtprio` limits configured
should behave better.

## CoreAudio (macOS) — **Verified on real macOS hardware via Project Panama FFM**

Successfully verified on macOS (`arm64`/`x86_64`) with `-Dchuck.audio.backend=coreaudio` (`CoreAudioBackendStream` and `CoreAudioBackendTest`).

**Empirical Verification Results (Outcome 1 verified):**
- **Native FFM Chain:** `AudioComponentFindNext` → `AudioComponentInstanceNew` → `AudioUnitSetProperty(kAudioUnitProperty_StreamFormat)` → `AudioUnitSetProperty(kAudioUnitProperty_SetRenderCallback)` (upcall stub) → `AudioUnitInitialize` → `AudioOutputUnitStart` executed cleanly without crash or `OSStatus` error.
- **AudioBufferList Offset & Upcall Math:** Confirmed `ioData.reinterpret(24).get(ValueLayout.ADDRESS, 16)` correctly locates `mBuffers[0].mData` on 64-bit macOS (`mNumberBuffers`(4) + padding(4) + `mNumberChannels`(4) + `mDataByteSize`(4) = 16 bytes header offset to pointer).
- **Latency & SpscRingBuffer:** Negotiates 128-sample (`~2.90 ms`) hardware buffers (`DefaultOutputUnit`), draining smoothly via `renderCallback` from `ChuckAudio`'s producer thread.

Mic capture is intentionally NOT implemented (`readInput()` returns silence) —
needs a separate `kAudioUnitSubType_HALOutput` unit with
`kAudioOutputUnitProperty_EnableIO` set on both scopes. Out of scope until the
output path is confirmed working.

## WASAPI (Windows) — **needs a real Windows run**

Same idea, `-Dchuck.audio.backend=wasapi` on real Windows. This is the
larger unverified surface (a full COM client built from near-scratch, not an
addition to an already-partially-working native object like CoreAudio's
`AudioUnit`), so expect a higher chance of needing at least one fix pass.

Outcomes and what they mean, same three-way split as CoreAudio:
1. **Audio plays, no crash** → the whole `IMMDeviceEnumerator` →
   `IMMDevice::Activate` → `IAudioClient::Initialize`/`GetBufferSize`/
   `GetService` → `Start` chain worked, and the `GetCurrentPadding` +
   `IAudioRenderClient::GetBuffer`/`ReleaseBuffer` polling loop is correct.
2. **A crash or an `IllegalStateException` naming a call + HRESULT** →
   look up the HRESULT (most WASAPI failures are self-explanatory
   `AUDCLNT_E_*` codes in `audioclient.h`) and check that specific call in
   `WASAPIBackendStream.java`.
3. **Silence, no error** → check the `WAVEFORMATEX` struct
   (`waveFormatEx()` — 18 bytes, `wFormatTag`/`nChannels`/`nSamplesPerSec`/
   `nAvgBytesPerSec`/`nBlockAlign`/`wBitsPerSample`/`cbSize`) and the
   `IAudioRenderClient::GetBuffer`/`ReleaseBuffer` byte layout math.

Specific things to scrutinize first:
- **The vtable indices themselves** (`comMethod()` calls throughout the
  file) — every COM interface method's index was taken from the documented
  Windows SDK vtable order (`IUnknown` always occupies slots 0-2:
  `QueryInterface`/`AddRef`/`Release`; each interface's own methods start at
  slot 3). A single off-by-one here would misdirect every call on that
  interface — this is the highest-risk spot in the whole WASAPI
  implementation, more likely to be wrong than any single struct layout.
- The GUID byte values (`clsidMMDeviceEnumerator()`/`iidIAudioClient()`/etc.
  in `WASAPIBackendStream.java`) — transcribed from Microsoft's published
  GUIDs, easy to mistype a hex digit.
- `CLSCTX_ALL=0x17` and the `AUDCLNT_SHAREMODE_SHARED=0` constant.

Mic capture IS attempted for WASAPI (unlike CoreAudio) via a second
`IAudioClient`/`IAudioCaptureClient` pair — same risk profile as the render
path, opened best-effort (failure there falls back to silent capture, doesn't
fail the whole stream, matching ALSA/JACK's optional-capture convention).

## Reporting results

Whichever platform you're on, the most useful report back is:
1. Console output (especially any `⚠️`/`SEVERE`/exception with a native error
   code).
2. Whether you heard sound.
3. For JACK specifically, `jack_lsp -c` output while the stream is running.

That's enough to pinpoint which specific native call (and therefore which
struct layout, constant, or vtable index) needs correcting.
