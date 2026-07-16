package org.chuck.audio.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concrete {@link AudioBackendStream} wrapping Windows WASAPI via Project Panama FFM.
 *
 * <p><b>Unverified beyond compilation</b> — this repo's sandbox has no Windows, so none of the COM
 * call sequence below has been exercised end-to-end. Written per the documented WASAPI shared-mode
 * rendering/capture pattern (the same shape used by countless WASAPI examples/tutorials): {@code
 * IMMDeviceEnumerator::GetDefaultAudioEndpoint} → {@code IMMDevice::Activate} (IAudioClient) →
 * {@code IAudioClient::Initialize}/{@code GetBufferSize}/{@code GetService} (IAudioRenderClient) →
 * {@code Start}, then per-cycle {@code GetCurrentPadding} + {@code IAudioRenderClient::GetBuffer}/
 * {@code ReleaseBuffer}. Deliberately uses this passive polling model (not WASAPI's event-driven
 * callback mode) — {@link #writeOutput}/{@link #readInput} do their COM calls synchronously on
 * whatever thread calls them (ChucK-Java's own engine thread), matching {@code AlsaBackendStream}'s
 * shape and avoiding a third distinct FFM-upcall pattern in this codebase.
 *
 * <p>Every COM call goes through {@link #comMethod}: COM objects are just a pointer to a vtable of
 * function pointers (`this` object layout: offset 0 holds the vtable pointer; the vtable itself is
 * an array of function pointers, `IUnknown`'s `QueryInterface`/`AddRef`/`Release` always occupying
 * slots 0-2, with each interface's own methods starting at slot 3).
 */
public class WASAPIBackendStream implements AudioBackendStream {
  private static final Logger logger = Logger.getLogger(WASAPIBackendStream.class.getName());

  private static final int CLSCTX_ALL = 0x17;
  private static final int EDATAFLOW_RENDER = 0;
  private static final int EDATAFLOW_CAPTURE = 1;
  private static final int EROLE_CONSOLE = 0;
  private static final int AUDCLNT_SHAREMODE_SHARED = 0;
  private static final short WAVE_FORMAT_IEEE_FLOAT = 3;
  private static final short WAVE_FORMAT_PCM = 1;

  private final AudioStreamConfig config;
  private int actualSampleRate;
  private int effectiveBufferSize;
  private int outputLatencySamples;
  private int inputLatencySamples;
  private final int numChannels;
  private final int numInChannels;
  private final AtomicLong underrunCount = new AtomicLong();
  private final AtomicLong overflowCount = new AtomicLong();
  private volatile boolean running = false;

  private final Linker linker;
  private final SymbolLookup ole32;
  private final SymbolLookup avrt;
  private Arena streamArena;
  private MemorySegment avrtHandle = MemorySegment.NULL;

  // Output (render) COM chain.
  private MemorySegment pEnumerator = MemorySegment.NULL;
  private MemorySegment pDevice = MemorySegment.NULL;
  private MemorySegment pAudioClient = MemorySegment.NULL;
  private MemorySegment pRenderClient = MemorySegment.NULL;
  private int renderBufferFrameCount;
  private MethodHandle renderGetCurrentPadding;
  private MethodHandle renderGetBuffer;
  private MethodHandle renderReleaseBuffer;
  private MethodHandle renderStart;
  private MethodHandle renderStop;

  // Input (capture) COM chain - optional, best-effort like ALSA/JACK's capture handling.
  private MemorySegment pCaptureDevice = MemorySegment.NULL;
  private MemorySegment pCaptureAudioClient = MemorySegment.NULL;
  private MemorySegment pCaptureClient = MemorySegment.NULL;
  private MethodHandle captureGetNextPacketSize;
  private MethodHandle captureGetBuffer;
  private MethodHandle captureReleaseBuffer;
  private MethodHandle captureStart;
  private MethodHandle captureStop;

  // Pre-allocated scratch for the hot writeOutput()/readInput() path - avoids per-call Arena churn.
  private MemorySegment scratchUint32;
  private MemorySegment scratchUint32b;
  private MemorySegment scratchPtr;
  private MemorySegment scratchPtr2;

  public WASAPIBackendStream(
      AudioStreamConfig config, Linker linker, SymbolLookup ole32, SymbolLookup avrt)
      throws Exception {
    this.config = config;
    this.linker = linker;
    this.ole32 = ole32;
    this.avrt = avrt;
    this.actualSampleRate = config.sampleRate();
    this.numChannels = Math.max(1, config.numOutputChannels());
    this.numInChannels = Math.max(0, config.numInputChannels());
    this.effectiveBufferSize =
        config.minimizeLatency() ? Math.max(64, config.bufferSize() / 4) : config.bufferSize();
    this.outputLatencySamples = this.effectiveBufferSize;

    initializeWASAPI();
  }

  // ── COM plumbing ─────────────────────────────────────────────────────────

  /**
   * Resolves and downcalls the {@code vtableIndex}-th method on a COM object. {@code obj} is the
   * `this` pointer passed as the (implicit) first argument by every caller of the returned handle.
   */
  private MethodHandle comMethod(MemorySegment obj, int vtableIndex, FunctionDescriptor desc) {
    MemorySegment vtable = obj.reinterpret(8).get(ValueLayout.ADDRESS, 0);
    MemorySegment fn =
        vtable
            .reinterpret((long) (vtableIndex + 1) * 8)
            .get(ValueLayout.ADDRESS, (long) vtableIndex * 8);
    return linker.downcallHandle(fn, desc);
  }

  private static MemorySegment guid(Arena arena, long data1, int data2, int data3, int... data4) {
    MemorySegment seg = arena.allocate(16);
    seg.set(ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), 0, (int) data1);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 4, (short) data2);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), 6, (short) data3);
    for (int i = 0; i < 8; i++) seg.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) data4[i]);
    return seg;
  }

  // Well-known Windows SDK GUIDs (CLSID_MMDeviceEnumerator, IID_IMMDeviceEnumerator,
  // IID_IAudioClient, IID_IAudioRenderClient, IID_IAudioCaptureClient).
  private static MemorySegment clsidMMDeviceEnumerator(Arena a) {
    return guid(a, 0xBCDE0395L, 0xE52F, 0x467C, 0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E);
  }

  private static MemorySegment iidIMMDeviceEnumerator(Arena a) {
    return guid(a, 0xA95664D2L, 0x9614, 0x4F35, 0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6);
  }

  private static MemorySegment iidIAudioClient(Arena a) {
    return guid(a, 0x1CB9AD4CL, 0xDBFA, 0x4C32, 0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2);
  }

  private static MemorySegment iidIAudioRenderClient(Arena a) {
    return guid(a, 0xF294ACFCL, 0x3146, 0x4483, 0xA7, 0xBF, 0xAD, 0xDC, 0xA7, 0xC2, 0x60, 0xE2);
  }

  private static MemorySegment iidIAudioCaptureClient(Arena a) {
    return guid(a, 0xC8ADBD64L, 0xE71E, 0x48A0, 0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17);
  }

  /**
   * WAVEFORMATEX: WORD wFormatTag; WORD nChannels; DWORD nSamplesPerSec; DWORD nAvgBytesPerSec;
   * WORD nBlockAlign; WORD wBitsPerSample; WORD cbSize; - 18 bytes.
   */
  private static MemorySegment waveFormatEx(
      Arena arena, short formatTag, int channels, int sampleRate, int bitsPerSample) {
    MemorySegment seg = arena.allocate(18);
    ByteOrder le = ByteOrder.LITTLE_ENDIAN;
    int blockAlign = channels * (bitsPerSample / 8);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(le), 0, formatTag);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(le), 2, (short) channels);
    seg.set(ValueLayout.JAVA_INT.withOrder(le), 4, sampleRate);
    seg.set(ValueLayout.JAVA_INT.withOrder(le), 8, sampleRate * blockAlign);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(le), 12, (short) blockAlign);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(le), 14, (short) bitsPerSample);
    seg.set(ValueLayout.JAVA_SHORT.withOrder(le), 16, (short) 0);
    return seg;
  }

  // ── Initialization ───────────────────────────────────────────────────────

  private void initializeWASAPI() throws Exception {
    streamArena = Arena.ofShared();
    try {
      MethodHandle coInit =
          linker.downcallHandle(
              ole32.find("CoInitializeEx").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      coInit.invoke(MemorySegment.NULL, 0);

      if (avrt != null && avrt.find("AvSetMmThreadCharacteristicsW").isPresent()) {
        MethodHandle setMm =
            linker.downcallHandle(
                avrt.find("AvSetMmThreadCharacteristicsW").get(),
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        MemorySegment proAudioTask = streamArena.allocateFrom("Pro Audio");
        MemorySegment taskIndexPtr = streamArena.allocate(ValueLayout.JAVA_INT);
        taskIndexPtr.set(ValueLayout.JAVA_INT, 0, 0);
        avrtHandle = (MemorySegment) setMm.invoke(proAudioTask, taskIndexPtr);
      }

      MethodHandle coCreateInstance =
          linker.downcallHandle(
              ole32.find("CoCreateInstance").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS));

      MemorySegment enumeratorOut = streamArena.allocate(ValueLayout.ADDRESS);
      int hr =
          (int)
              coCreateInstance.invoke(
                  clsidMMDeviceEnumerator(streamArena),
                  MemorySegment.NULL,
                  CLSCTX_ALL,
                  iidIMMDeviceEnumerator(streamArena),
                  enumeratorOut);
      if (hr != 0)
        throw new IllegalStateException(
            "CoCreateInstance(MMDeviceEnumerator) failed: HRESULT=" + hr);
      pEnumerator = enumeratorOut.get(ValueLayout.ADDRESS, 0);

      openRenderClient();
      if (numInChannels > 0) {
        try {
          openCaptureClient();
        } catch (Throwable t) {
          logger.log(
              Level.INFO, "[WASAPIBackendStream] Capture device unavailable: " + t.getMessage());
          pCaptureAudioClient = MemorySegment.NULL;
          pCaptureClient = MemorySegment.NULL;
        }
      }

      scratchUint32 = streamArena.allocate(ValueLayout.JAVA_INT);
      scratchUint32b = streamArena.allocate(ValueLayout.JAVA_INT);
      scratchPtr = streamArena.allocate(ValueLayout.ADDRESS);
      scratchPtr2 = streamArena.allocate(ValueLayout.ADDRESS);

      logger.log(
          Level.INFO,
          String.format(
              "[WASAPIBackendStream] Initialized WASAPI shared-mode stream. SR=%dHz, buffer=%d samples (%.2f ms), ch=%d, capture=%s",
              actualSampleRate,
              effectiveBufferSize,
              (effectiveBufferSize * 1000.0 / actualSampleRate),
              numChannels,
              pCaptureClient.equals(MemorySegment.NULL) ? "no" : "yes"));
    } catch (Throwable t) {
      if (streamArena != null) {
        streamArena.close();
        streamArena = null;
      }
      throw new Exception("WASAPI FFM initialization error: " + t.getMessage(), t);
    }
  }

  private void openRenderClient() throws Throwable {
    FunctionDescriptor getEndpointDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS);
    MemorySegment deviceOut = streamArena.allocate(ValueLayout.ADDRESS);
    int hr =
        (int)
            comMethod(pEnumerator, 3, getEndpointDesc)
                .invoke(pEnumerator, EDATAFLOW_RENDER, EROLE_CONSOLE, deviceOut);
    if (hr != 0)
      throw new IllegalStateException("GetDefaultAudioEndpoint(render) failed: HRESULT=" + hr);
    pDevice = deviceOut.get(ValueLayout.ADDRESS, 0);

    FunctionDescriptor activateDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);
    MemorySegment clientOut = streamArena.allocate(ValueLayout.ADDRESS);
    hr =
        (int)
            comMethod(pDevice, 3, activateDesc)
                .invoke(
                    pDevice,
                    iidIAudioClient(streamArena),
                    CLSCTX_ALL,
                    MemorySegment.NULL,
                    clientOut);
    if (hr != 0)
      throw new IllegalStateException("IMMDevice::Activate(IAudioClient) failed: HRESULT=" + hr);
    pAudioClient = clientOut.get(ValueLayout.ADDRESS, 0);

    MemorySegment wfx =
        waveFormatEx(streamArena, WAVE_FORMAT_IEEE_FLOAT, numChannels, actualSampleRate, 32);
    long hnsBufferDuration =
        (long)
            ((double) config.bufferSize()
                * Math.max(2, config.numBuffers())
                / actualSampleRate
                * 1e7);
    FunctionDescriptor initDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);
    hr =
        (int)
            comMethod(pAudioClient, 3, initDesc)
                .invoke(
                    pAudioClient,
                    AUDCLNT_SHAREMODE_SHARED,
                    0,
                    hnsBufferDuration,
                    0L,
                    wfx,
                    MemorySegment.NULL);
    if (hr != 0)
      throw new IllegalStateException("IAudioClient::Initialize(render) failed: HRESULT=" + hr);

    FunctionDescriptor getBufferSizeDesc =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    MemorySegment framesOut = streamArena.allocate(ValueLayout.JAVA_INT);
    hr = (int) comMethod(pAudioClient, 4, getBufferSizeDesc).invoke(pAudioClient, framesOut);
    if (hr != 0)
      throw new IllegalStateException("IAudioClient::GetBufferSize failed: HRESULT=" + hr);
    renderBufferFrameCount = framesOut.get(ValueLayout.JAVA_INT, 0);
    effectiveBufferSize = renderBufferFrameCount;
    outputLatencySamples = renderBufferFrameCount;

    FunctionDescriptor getServiceDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    MemorySegment renderClientOut = streamArena.allocate(ValueLayout.ADDRESS);
    hr =
        (int)
            comMethod(pAudioClient, 14, getServiceDesc)
                .invoke(pAudioClient, iidIAudioRenderClient(streamArena), renderClientOut);
    if (hr != 0)
      throw new IllegalStateException(
          "IAudioClient::GetService(IAudioRenderClient) failed: HRESULT=" + hr);
    pRenderClient = renderClientOut.get(ValueLayout.ADDRESS, 0);

    renderGetCurrentPadding =
        comMethod(
            pAudioClient,
            6,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    renderGetBuffer =
        comMethod(
            pRenderClient,
            3,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS));
    renderReleaseBuffer =
        comMethod(
            pRenderClient,
            4,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT));
    renderStart =
        comMethod(
            pAudioClient, 10, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    renderStop =
        comMethod(
            pAudioClient, 11, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
  }

  private void openCaptureClient() throws Throwable {
    FunctionDescriptor getEndpointDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS);
    MemorySegment deviceOut = streamArena.allocate(ValueLayout.ADDRESS);
    int hr =
        (int)
            comMethod(pEnumerator, 3, getEndpointDesc)
                .invoke(pEnumerator, EDATAFLOW_CAPTURE, EROLE_CONSOLE, deviceOut);
    if (hr != 0)
      throw new IllegalStateException("GetDefaultAudioEndpoint(capture) failed: HRESULT=" + hr);
    pCaptureDevice = deviceOut.get(ValueLayout.ADDRESS, 0);

    FunctionDescriptor activateDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);
    MemorySegment clientOut = streamArena.allocate(ValueLayout.ADDRESS);
    hr =
        (int)
            comMethod(pCaptureDevice, 3, activateDesc)
                .invoke(
                    pCaptureDevice,
                    iidIAudioClient(streamArena),
                    CLSCTX_ALL,
                    MemorySegment.NULL,
                    clientOut);
    if (hr != 0)
      throw new IllegalStateException(
          "IMMDevice::Activate(capture IAudioClient) failed: HRESULT=" + hr);
    pCaptureAudioClient = clientOut.get(ValueLayout.ADDRESS, 0);

    // Capture is always INT16, matching AudioBackendStream.readInput(short[]...)'s contract.
    MemorySegment wfx =
        waveFormatEx(streamArena, WAVE_FORMAT_PCM, numInChannels, actualSampleRate, 16);
    long hnsBufferDuration =
        (long)
            ((double) config.bufferSize()
                * Math.max(2, config.numBuffers())
                / actualSampleRate
                * 1e7);
    FunctionDescriptor initDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS);
    hr =
        (int)
            comMethod(pCaptureAudioClient, 3, initDesc)
                .invoke(
                    pCaptureAudioClient,
                    AUDCLNT_SHAREMODE_SHARED,
                    0,
                    hnsBufferDuration,
                    0L,
                    wfx,
                    MemorySegment.NULL);
    if (hr != 0)
      throw new IllegalStateException("IAudioClient::Initialize(capture) failed: HRESULT=" + hr);

    FunctionDescriptor getServiceDesc =
        FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    MemorySegment captureClientOut = streamArena.allocate(ValueLayout.ADDRESS);
    hr =
        (int)
            comMethod(pCaptureAudioClient, 14, getServiceDesc)
                .invoke(pCaptureAudioClient, iidIAudioCaptureClient(streamArena), captureClientOut);
    if (hr != 0)
      throw new IllegalStateException(
          "IAudioClient::GetService(IAudioCaptureClient) failed: HRESULT=" + hr);
    pCaptureClient = captureClientOut.get(ValueLayout.ADDRESS, 0);

    FunctionDescriptor getBufferSizeDesc =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    MemorySegment framesOut = streamArena.allocate(ValueLayout.JAVA_INT);
    comMethod(pCaptureAudioClient, 4, getBufferSizeDesc).invoke(pCaptureAudioClient, framesOut);
    inputLatencySamples = framesOut.get(ValueLayout.JAVA_INT, 0);

    captureGetNextPacketSize =
        comMethod(
            pCaptureClient,
            5,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    captureGetBuffer =
        comMethod(
            pCaptureClient,
            3,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    captureReleaseBuffer =
        comMethod(
            pCaptureClient,
            4,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    captureStart =
        comMethod(
            pCaptureAudioClient,
            10,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    captureStop =
        comMethod(
            pCaptureAudioClient,
            11,
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
  }

  // ── AudioBackendStream ───────────────────────────────────────────────────

  @Override
  public void start() {
    if (running) return;
    try {
      if (renderStart != null) {
        int hr = (int) renderStart.invoke(pAudioClient);
        if (hr != 0) {
          logger.log(
              Level.SEVERE,
              "[WASAPIBackendStream] IAudioClient::Start(render) failed: HRESULT=" + hr);
          return;
        }
      }
      if (captureStart != null
          && pCaptureAudioClient != null
          && !pCaptureAudioClient.equals(MemorySegment.NULL)) {
        captureStart.invoke(pCaptureAudioClient);
      }
      running = true;
    } catch (Throwable t) {
      logger.log(
          Level.SEVERE, "[WASAPIBackendStream] Exception starting WASAPI: " + t.getMessage(), t);
    }
  }

  @Override
  public void stop() {
    if (!running) return;
    running = false;
    try {
      if (renderStop != null) renderStop.invoke(pAudioClient);
    } catch (Throwable ignored) {
    }
    try {
      if (captureStop != null
          && pCaptureAudioClient != null
          && !pCaptureAudioClient.equals(MemorySegment.NULL)) {
        captureStop.invoke(pCaptureAudioClient);
      }
    } catch (Throwable ignored) {
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getActualSampleRate() {
    return actualSampleRate;
  }

  @Override
  public int getEffectiveBufferSize() {
    return effectiveBufferSize;
  }

  @Override
  public int getOutputLatencySamples() {
    return outputLatencySamples;
  }

  @Override
  public int getInputLatencySamples() {
    return inputLatencySamples;
  }

  @Override
  public long getUnderrunCount() {
    return underrunCount.get();
  }

  @Override
  public long getOverflowCount() {
    return overflowCount.get();
  }

  @Override
  public int readInput(short[] buffer, int offset, int length) {
    if (!running
        || pCaptureClient == null
        || pCaptureClient.equals(MemorySegment.NULL)
        || numInChannels <= 0) {
      java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
      return 0;
    }
    try {
      int hr = (int) captureGetNextPacketSize.invoke(pCaptureAudioClient, scratchUint32);
      if (hr != 0) {
        java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
        return 0;
      }
      int packetFrames = scratchUint32.get(ValueLayout.JAVA_INT, 0);
      int requestedFrames = length / numInChannels;
      if (packetFrames <= 0) {
        java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
        return 0;
      }

      hr =
          (int)
              captureGetBuffer.invoke(
                  pCaptureClient,
                  scratchPtr,
                  scratchUint32b,
                  scratchUint32,
                  MemorySegment.NULL,
                  MemorySegment.NULL);
      if (hr != 0) {
        java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
        return 0;
      }
      int framesAvailable = scratchUint32b.get(ValueLayout.JAVA_INT, 0);
      MemorySegment dataPtr = scratchPtr.get(ValueLayout.ADDRESS, 0);
      int frames = Math.min(framesAvailable, requestedFrames);
      if (frames > 0 && !dataPtr.equals(MemorySegment.NULL)) {
        MemorySegment shortView = dataPtr.reinterpret((long) framesAvailable * numInChannels * 2);
        int samples = frames * numInChannels;
        for (int i = 0; i < samples; i++) {
          buffer[offset + i] =
              shortView.getAtIndex(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), i);
        }
        if (samples < length) {
          java.util.Arrays.fill(buffer, offset + samples, offset + length, (short) 0);
        }
      } else {
        java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
      }
      captureReleaseBuffer.invoke(pCaptureClient, framesAvailable);
      if (frames < requestedFrames) overflowCount.incrementAndGet();
      return frames * numInChannels;
    } catch (Throwable t) {
      java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
      return 0;
    }
  }

  @Override
  public void writeOutput(float[] buffer, int offset, int length) {
    if (!running || pRenderClient == null || pRenderClient.equals(MemorySegment.NULL)) return;
    try {
      int frames = length / numChannels;
      int hr = (int) renderGetCurrentPadding.invoke(pAudioClient, scratchUint32);
      if (hr != 0) return;
      int padding = scratchUint32.get(ValueLayout.JAVA_INT, 0);
      int available = renderBufferFrameCount - padding;
      int framesToWrite = Math.min(frames, Math.max(0, available));
      if (framesToWrite <= 0) {
        underrunCount.incrementAndGet();
        return;
      }

      hr = (int) renderGetBuffer.invoke(pRenderClient, framesToWrite, scratchPtr);
      if (hr != 0) return;
      MemorySegment dataPtr = scratchPtr.get(ValueLayout.ADDRESS, 0);
      MemorySegment floatView =
          dataPtr.reinterpret(
              (long) framesToWrite * numChannels * ValueLayout.JAVA_FLOAT.byteSize());
      int samples = framesToWrite * numChannels;
      for (int i = 0; i < samples; i++) {
        floatView.setAtIndex(
            ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), i, buffer[offset + i]);
      }
      renderReleaseBuffer.invoke(pRenderClient, framesToWrite, 0);
      if (framesToWrite < frames) overflowCount.incrementAndGet();
    } catch (Throwable ignored) {
    }
  }

  @Override
  public void close() {
    stop();
    if (avrt != null
        && avrt.find("AvRevertMmThreadCharacteristics").isPresent()
        && !avrtHandle.equals(MemorySegment.NULL)) {
      try {
        MethodHandle revert =
            linker.downcallHandle(
                avrt.find("AvRevertMmThreadCharacteristics").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        revert.invoke(avrtHandle);
      } catch (Throwable ignored) {
      }
      avrtHandle = MemorySegment.NULL;
    }
    if (streamArena != null) {
      try {
        streamArena.close();
      } catch (Exception ignored) {
      }
      streamArena = null;
    }
  }
}
