package org.chuck.audio;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.*;
import org.chuck.audio.util.Gain;
import org.chuck.audio.util.WvOut;
import org.chuck.core.ChuckVM;

/**
 * Handles Audio I/O for the ChuckVM.
 *
 * <p>Output: SourceDataLine (playback) in INT16/INT24/INT32/FLOAT32 format (Phase 1-A). Input:
 * TargetDataLine (microphone/ADC) — always INT16; opened gracefully, silent if unavailable.
 *
 * <p>Phase 1 features from the RtAudio analysis:
 *
 * <ul>
 *   <li>1-A {@link AudioSampleFormat} — configurable output bit-depth/encoding with INT16 fallback
 *   <li>1-B {@link DeviceInfo} — per-mixer capability probe (channels, sample-rates, formats)
 *   <li>1-C Preferred-rate auto-match — warns when requested SR is unavailable; records actual SR
 *   <li>1-D Latency reporting — {@link #getOutputLatencyMs()}, {@link #getInputLatencyMs()}
 *   <li>1-E Underrun / overflow counters — {@link #getUnderrunCount()}, {@link #getOverflowCount()}
 * </ul>
 */
public class ChuckAudio {
  private static final Logger logger = Logger.getLogger(ChuckAudio.class.getName());

  /** Standard sample rates probed during device enumeration (matches RtAudio's probe list). */
  private static final int[] STANDARD_RATES = {
    8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000
  };

  // ── Phase 1-B: DeviceInfo ────────────────────────────────────────────────

  /**
   * Snapshot of one audio mixer's capabilities, equivalent to RtAudio's {@code DeviceInfo} struct.
   */
  public record DeviceInfo(
      /** Human-readable mixer name. */
      String name,
      /** Maximum output channels (0 if mixer has no output). */
      int maxOutputChannels,
      /** Maximum input channels (0 if mixer has no input). */
      int maxInputChannels,
      /** Sample rates the mixer reports as supported. */
      List<Integer> supportedSampleRates,
      /**
       * Preferred sample rate — the lowest of {48 kHz, 44.1 kHz} the device supports, or the first
       * supported rate. Corresponds to {@code preferredSampleRate} in RtAudio.
       */
      int preferredSampleRate,
      /**
       * Output sample formats supported natively (always includes INT16 as fallback). Corresponds
       * to {@code nativeFormats} in RtAudio.
       */
      List<AudioSampleFormat> nativeOutputFormats,
      /**
       * Input sample formats supported natively (always includes INT16 as fallback). May be empty
       * if there is no input.
       */
      List<AudioSampleFormat> nativeInputFormats) {}

  /** Returns capability info for every mixer that exposes a {@link SourceDataLine}. */
  public static List<DeviceInfo> getOutputDeviceInfo() {
    List<DeviceInfo> result = new ArrayList<>();
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        Mixer m = AudioSystem.getMixer(info);
        if (!m.isLineSupported(new DataLine.Info(SourceDataLine.class, null))) continue;
        DeviceInfo di = probeDevice(m, info.getName());
        if (di.maxOutputChannels() > 0) result.add(di);
      } catch (Exception ignored) {
      }
    }
    return result;
  }

  /** Returns capability info for every mixer that exposes a {@link TargetDataLine}. */
  public static List<DeviceInfo> getInputDeviceInfo() {
    List<DeviceInfo> result = new ArrayList<>();
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        Mixer m = AudioSystem.getMixer(info);
        if (!m.isLineSupported(new DataLine.Info(TargetDataLine.class, null))) continue;
        DeviceInfo di = probeDevice(m, info.getName());
        if (di.maxInputChannels() > 0) result.add(di);
      } catch (Exception ignored) {
      }
    }
    return result;
  }

  private static DeviceInfo probeDevice(Mixer m, String name) {
    // Probe output channels
    int maxOutCh = 0;
    for (int ch = 8; ch >= 1; ch--) {
      AudioFormat f = new AudioFormat(44100, 16, ch, true, false);
      if (m.isLineSupported(new DataLine.Info(SourceDataLine.class, f))) {
        maxOutCh = ch;
        break;
      }
    }
    // Probe input channels
    int maxInCh = 0;
    for (int ch = 8; ch >= 1; ch--) {
      AudioFormat f = new AudioFormat(44100, 16, ch, true, false);
      if (m.isLineSupported(new DataLine.Info(TargetDataLine.class, f))) {
        maxInCh = ch;
        break;
      }
    }

    int probeCh = Math.max(1, Math.min(2, maxOutCh > 0 ? maxOutCh : maxInCh));

    // Probe supported sample rates
    List<Integer> rates = new ArrayList<>();
    for (int r : STANDARD_RATES) {
      AudioFormat f = new AudioFormat(r, 16, probeCh, true, false);
      boolean outOk = maxOutCh > 0 && m.isLineSupported(new DataLine.Info(SourceDataLine.class, f));
      boolean inOk = maxInCh > 0 && m.isLineSupported(new DataLine.Info(TargetDataLine.class, f));
      if (outOk || inOk) rates.add(r);
    }

    // Preferred rate: lowest of {48000, 44100} that is supported, else first supported
    int preferredRate = rates.isEmpty() ? 44100 : rates.get(0);
    for (int r : new int[] {48000, 44100}) {
      if (rates.contains(r)) {
        preferredRate = r;
        break;
      }
    }

    // Probe output formats at preferred rate
    Set<AudioSampleFormat> outFmts = new LinkedHashSet<>();
    outFmts.add(AudioSampleFormat.INT16); // always include as fallback
    if (maxOutCh > 0) {
      for (AudioSampleFormat sf : AudioSampleFormat.values()) {
        AudioFormat jf = sf.toJavaAudioFormat(preferredRate, probeCh);
        if (m.isLineSupported(new DataLine.Info(SourceDataLine.class, jf))) outFmts.add(sf);
      }
    }

    // Probe input formats at preferred rate
    Set<AudioSampleFormat> inFmts = new LinkedHashSet<>();
    if (maxInCh > 0) {
      inFmts.add(AudioSampleFormat.INT16);
      for (AudioSampleFormat sf : AudioSampleFormat.values()) {
        AudioFormat jf =
            sf.toJavaAudioFormat(preferredRate, Math.max(1, Math.min(probeCh, maxInCh)));
        if (m.isLineSupported(new DataLine.Info(TargetDataLine.class, jf))) inFmts.add(sf);
      }
    }

    return new DeviceInfo(
        name,
        maxOutCh,
        maxInCh,
        List.copyOf(rates),
        preferredRate,
        List.copyOf(outFmts),
        List.copyOf(inFmts));
  }

  // ── Legacy name-only device lists (kept for IDE backward compat) ─────────

  /** Returns all mixer names that support SourceDataLine (playback). */
  public static List<String> getOutputDeviceNames() {
    List<String> names = new ArrayList<>();
    names.add(""); // system default
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        Mixer m = AudioSystem.getMixer(info);
        if (m.isLineSupported(new DataLine.Info(SourceDataLine.class, null)))
          names.add(info.getName());
      } catch (Exception ignored) {
      }
    }
    return names;
  }

  /** Returns all mixer names that support TargetDataLine (capture). */
  public static List<String> getInputDeviceNames() {
    List<String> names = new ArrayList<>();
    names.add(""); // system default
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        Mixer m = AudioSystem.getMixer(info);
        if (m.isLineSupported(new DataLine.Info(TargetDataLine.class, null)))
          names.add(info.getName());
      } catch (Exception ignored) {
      }
    }
    return names;
  }

  // ── Instance fields ───────────────────────────────────────────────────────

  private final ChuckVM vm;
  private final int bufferSize;
  private int numChannels;
  private int numInputChannels;
  private final float sampleRate;

  // Phase 1-A: sample format
  private AudioSampleFormat sampleFormat = AudioSampleFormat.INT16;
  private AudioSampleFormat actualFormat = AudioSampleFormat.INT16; // what was actually opened

  // Phase 1-C: actual sample rate (may differ if preferred-rate fallback kicks in)
  private float actualSampleRate;

  // Phase 1-D: latency
  private int outputLatencySamples = 0;
  private int inputLatencySamples = 0;

  // Phase 1-E: underrun / overflow counters
  private final AtomicLong underrunCount = new AtomicLong(0);
  private final AtomicLong overflowCount = new AtomicLong(0);
  private long underrunLogCount = 0; // throttle logging to 1 per 100 events
  private long overflowLogCount = 0;

  // Phase 2: buffer count, minimize-latency, effective buffer size
  private int numBuffers = 2; // double-buffering by default (matches RtAudio default)
  private boolean minimizeLatency = false;
  private int effectiveBufferSize; // set in initJavaSound; may differ from bufferSize when
  // minimizeLatency=true

  // Phase 3: real-time scheduling (RTAUDIO_SCHEDULE_REALTIME)
  private boolean scheduleRealtime = false;

  private SourceDataLine outputLine;
  private TargetDataLine inputLine; // null if no mic available
  private boolean running = false;

  // Zipper-noise prevention: UI sets targetGain; audio thread smooths toward it each sample.
  private volatile float targetGain = 0.8f;
  private float smoothedGain = 0.8f;
  private float gainSmoothAlpha = 0.00113f; // recomputed in initJavaSound

  private Gain masterGainUGen;
  private int verbose = 1;

  // Drift / timing
  private long lastBufferTimeNanos = 0;
  private final AtomicLong totalDriftNanos = new AtomicLong(0);
  private final AtomicLong driftCount = new AtomicLong(0);
  private final AtomicLong maxDriftNanos = new AtomicLong(0);
  private volatile double cpuLoad = 0.0;

  // Optional recorder
  private WvOut recorder;

  /** Name of the preferred output mixer (empty = system default). */
  private String outputDeviceName = "";

  /** Name of the preferred input mixer (empty = system default). */
  private String inputDeviceName = "";

  // ── Construction ──────────────────────────────────────────────────────────

  public ChuckAudio(ChuckVM vm, int bufferSize, int numChannels, float sampleRate) {
    this.vm = vm;
    this.bufferSize = bufferSize;
    this.effectiveBufferSize = bufferSize; // updated in initJavaSound
    this.numChannels = numChannels;
    this.numInputChannels = numChannels;
    this.sampleRate = sampleRate;
    this.actualSampleRate = sampleRate;
    initJavaSound();
  }

  // ── Configuration setters ─────────────────────────────────────────────────

  public void setOutputDeviceName(String name) {
    this.outputDeviceName = name == null ? "" : name;
  }

  public void setInputDeviceName(String name) {
    this.inputDeviceName = name == null ? "" : name;
  }

  /**
   * Request a specific output sample format (Phase 1-A). Must be called before the engine starts.
   * If the format is unsupported by the driver, initJavaSound falls back to INT16.
   */
  public void setSampleFormat(AudioSampleFormat fmt) {
    this.sampleFormat = fmt == null ? AudioSampleFormat.INT16 : fmt;
  }

  public AudioSampleFormat getActualFormat() {
    return actualFormat;
  }

  /**
   * Set the number of internal driver buffers (Phase 2). Default 2 (double-buffering). Higher
   * values increase latency but reduce the risk of dropouts under CPU load. Must be called before
   * the engine starts. Corresponds to {@code StreamOptions.numberOfBuffers} in RtAudio.
   */
  public void setNumBuffers(int n) {
    this.numBuffers = Math.max(2, n);
  }

  public int getNumBuffers() {
    return numBuffers;
  }

  /**
   * When true, {@link #initJavaSound} opens the SourceDataLine with the driver's minimum allowed
   * buffer size rather than {@code bufferSize * numBuffers} bytes. Corresponds to {@code
   * RTAUDIO_MINIMIZE_LATENCY} in RtAudio. Must be called before the engine starts.
   */
  public void setMinimizeLatency(boolean minimize) {
    this.minimizeLatency = minimize;
  }

  /**
   * Returns the buffer size actually in use (samples). May be smaller than the constructor argument
   * when {@link #setMinimizeLatency(boolean)} is true.
   */
  public int getEffectiveBufferSize() {
    return effectiveBufferSize;
  }

  /**
   * Request real-time thread priority for the audio engine thread (Phase 3). On any platform this
   * raises the Java thread priority to {@link Thread#MAX_PRIORITY}. On Windows it additionally
   * calls {@code SetThreadPriority(THREAD_PRIORITY_TIME_CRITICAL)} via FFM; on Linux/Mac it calls
   * {@code pthread_setschedparam(SCHED_RR, priority=99)} via FFM. Must be called before {@link
   * #start()}.
   *
   * <p>Corresponds to {@code RTAUDIO_SCHEDULE_REALTIME} in RtAudio.
   */
  public void setScheduleRealtime(boolean realtime) {
    this.scheduleRealtime = realtime;
  }

  public boolean isScheduleRealtime() {
    return scheduleRealtime;
  }

  public void setMasterGainUGen(Gain ugen) {
    this.masterGainUGen = ugen;
  }

  public void setVerbose(int verbose) {
    this.verbose = verbose;
  }

  public void setMasterGain(float gain) {
    this.targetGain = gain;
  }

  // ── Phase 1-C: actual sample rate ────────────────────────────────────────

  /** Returns the sample rate actually opened (may equal the requested rate). */
  public float getActualSampleRate() {
    return actualSampleRate;
  }

  // ── Phase 1-D: latency ───────────────────────────────────────────────────

  /** Output latency in samples (computed from the SourceDataLine buffer size after open). */
  public int getOutputLatencySamples() {
    return outputLatencySamples;
  }

  /** Output latency in milliseconds. */
  public double getOutputLatencyMs() {
    return outputLatencySamples * 1000.0 / actualSampleRate;
  }

  /** Input latency in samples, or 0 if no input line is open. */
  public int getInputLatencySamples() {
    return inputLatencySamples;
  }

  /** Input latency in milliseconds. */
  public double getInputLatencyMs() {
    return inputLatencySamples * 1000.0 / actualSampleRate;
  }

  /** Combined round-trip latency (output + input) in milliseconds. */
  public double getTotalLatencyMs() {
    return getOutputLatencyMs() + getInputLatencyMs();
  }

  // ── Phase 1-E: underrun / overflow ───────────────────────────────────────

  /**
   * Number of output underruns detected since start (late write intervals exceeding one buffer
   * period). Corresponds to {@code RTAUDIO_OUTPUT_UNDERFLOW} status flag.
   */
  public long getUnderrunCount() {
    return underrunCount.get();
  }

  /**
   * Number of input overflows detected since start (ADC buffer had more than 2× the needed data
   * queued before read). Corresponds to {@code RTAUDIO_INPUT_OVERFLOW} status flag.
   */
  public long getOverflowCount() {
    return overflowCount.get();
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  /** Opens a SourceDataLine on the named mixer, falling back to system default. */
  private SourceDataLine openOutputLine(AudioFormat fmt) throws LineUnavailableException {
    if (!outputDeviceName.isEmpty()) {
      for (Mixer.Info info : AudioSystem.getMixerInfo()) {
        if (info.getName().equals(outputDeviceName)) {
          try {
            Mixer m = AudioSystem.getMixer(info);
            DataLine.Info dl = new DataLine.Info(SourceDataLine.class, fmt);
            SourceDataLine line = (SourceDataLine) m.getLine(dl);
            logger.log(Level.INFO, "[Audio] Output device: " + info.getName());
            return line;
          } catch (Exception ignored) {
            logger.log(
                Level.WARNING,
                "[Audio] Could not open output device '"
                    + outputDeviceName
                    + "', falling back to default");
          }
        }
      }
    }
    return (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
  }

  /** Opens a TargetDataLine on the named mixer, falling back to system default. */
  private TargetDataLine openInputLine(AudioFormat fmt) throws LineUnavailableException {
    if (!inputDeviceName.isEmpty()) {
      for (Mixer.Info info : AudioSystem.getMixerInfo()) {
        if (info.getName().equals(inputDeviceName)) {
          try {
            Mixer m = AudioSystem.getMixer(info);
            DataLine.Info dl = new DataLine.Info(TargetDataLine.class, fmt);
            TargetDataLine line = (TargetDataLine) m.getLine(dl);
            logger.log(Level.INFO, "[Audio] Input device: " + info.getName());
            return line;
          } catch (Exception ignored) {
            logger.log(
                Level.WARNING,
                "[Audio] Could not open input device '"
                    + inputDeviceName
                    + "', falling back to default");
          }
        }
      }
    }
    return (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
  }

  /**
   * Try to open a SourceDataLine with {@code fmt}. Falls back to INT16 on failure. Updates {@link
   * #actualFormat} and {@link #actualSampleRate} to reflect what was actually opened.
   */
  private SourceDataLine openOutputWithFallback(
      AudioSampleFormat targetFmt, float rate, int channels) {
    // Phase 1-C: try preferred rate, then common alternatives if that fails
    int[] rateOrder = rateOrder(rate);
    for (int r : rateOrder) {
      for (AudioSampleFormat sf : new AudioSampleFormat[] {targetFmt, AudioSampleFormat.INT16}) {
        AudioFormat jf = sf.toJavaAudioFormat(r, channels);
        try {
          SourceDataLine line = openOutputLine(jf);
          if (minimizeLatency) {
            // Let the driver choose the smallest buffer it can handle
            line.open(jf);
          } else {
            line.open(jf, bufferSize * channels * sf.bytesPerSample * numBuffers);
          }
          actualFormat = sf;
          actualSampleRate = r;
          if (r != (int) rate) {
            logger.log(
                Level.WARNING,
                String.format("[Audio] Requested %.0f Hz not available; opened at %d Hz", rate, r));
          }
          if (sf != targetFmt) {
            logger.log(
                Level.WARNING,
                "[Audio] Requested format " + targetFmt + " not available; fell back to " + sf);
          }
          logger.log(
              Level.INFO,
              String.format("[Audio] Output opened: %s, %d Hz, %d ch", sf, r, channels));
          return line;
        } catch (LineUnavailableException | IllegalArgumentException ignored) {
          // try next combination
        }
      }
    }
    return null; // nothing worked
  }

  /** Rate priority order: requested first, then 48 kHz, 44.1 kHz, then the rest. */
  private int[] rateOrder(float requested) {
    List<Integer> order = new ArrayList<>();
    int req = (int) requested;
    order.add(req);
    for (int r : new int[] {48000, 44100, 22050, 96000}) {
      if (r != req) order.add(r);
    }
    return order.stream().mapToInt(Integer::intValue).toArray();
  }

  private void initJavaSound() {
    gainSmoothAlpha = (float) (1.0 - Math.exp(-1.0 / (sampleRate * 0.02)));

    // ── Output ───────────────────────────────────────────────────────────────
    outputLine = openOutputWithFallback(sampleFormat, sampleRate, numChannels);
    if (outputLine == null) {
      logger.log(Level.SEVERE, "[Audio] Could not open any output line.");
      return;
    }

    // Phase 1-D: compute output latency from actual buffer size
    int outBufBytes = outputLine.getBufferSize();
    outputLatencySamples = outBufBytes / (numChannels * actualFormat.bytesPerSample);

    // Phase 2: derive effective buffer size for the audio loop
    // When minimizeLatency=true the driver chose its minimum; use half the total buffer
    // (one period) as our write chunk size, clamped to at least 32 samples.
    if (minimizeLatency) {
      effectiveBufferSize = Math.max(32, outputLatencySamples / numBuffers);
    } else {
      effectiveBufferSize = bufferSize;
    }
    logger.log(
        Level.INFO,
        String.format(
            "[Audio] Output latency: %d samples (%.1f ms), effectiveBufferSize=%d, numBuffers=%d",
            outputLatencySamples, getOutputLatencyMs(), effectiveBufferSize, numBuffers));

    // ── Input (microphone) — optional ────────────────────────────────────────
    // Input is always INT16 for simplicity; no format negotiation needed on the input path.
    AudioFormat inFmt16 = AudioSampleFormat.INT16.toJavaAudioFormat(actualSampleRate, numChannels);
    AudioFormat inFmt16mono = AudioSampleFormat.INT16.toJavaAudioFormat(actualSampleRate, 1);
    try {
      if (AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, inFmt16))) {
        inputLine = openInputLine(inFmt16);
        inputLine.open(inFmt16, effectiveBufferSize * numChannels * 2 * numBuffers);
        numInputChannels = numChannels;
        logger.log(Level.INFO, "[Audio] Microphone input line opened: " + inFmt16);
      } else if (AudioSystem.isLineSupported(
          new DataLine.Info(TargetDataLine.class, inFmt16mono))) {
        inputLine = openInputLine(inFmt16mono);
        inputLine.open(inFmt16mono, effectiveBufferSize * 1 * 2 * numBuffers);
        numInputChannels = 1;
        logger.log(Level.INFO, "[Audio] Microphone input line opened (MONO): " + inFmt16mono);
      }
      if (inputLine != null) {
        // Phase 1-D: compute input latency
        inputLatencySamples = inputLine.getBufferSize() / (numInputChannels * 2);
        logger.log(
            Level.INFO,
            String.format(
                "[Audio] Input latency: %d samples (%.1f ms)",
                inputLatencySamples, getInputLatencyMs()));
      }
    } catch (LineUnavailableException | SecurityException e) {
      logger.log(Level.INFO, "[Audio] Microphone access failed: " + e.getMessage());
      inputLine = null;
    }
  }

  // ── Audio engine thread ───────────────────────────────────────────────────

  public void start() {
    if (running || outputLine == null) return;
    running = true;
    outputLine.start();
    if (inputLine != null) inputLine.start();

    final AudioSampleFormat fmt = actualFormat; // capture for lambda
    final int bps = fmt.bytesPerSample;
    final int effBuf = effectiveBufferSize; // Phase 2: may differ from bufferSize

    Thread audioThread =
        Thread.ofPlatform()
            .name("ChucK-Audio-Engine")
            .unstarted(
                () -> {
                  // Phase 3: raise OS-level priority once the thread is running
                  if (scheduleRealtime) {
                    applyRealtimePriority(Thread.currentThread());
                  }
                  try (Arena arena = Arena.ofShared()) {
                    int bytesPerBuffer = effBuf * numChannels * bps;
                    int inBytesPerBuffer = effBuf * numInputChannels * 2; // input always INT16
                    MemorySegment outSeg = arena.allocate(bytesPerBuffer);
                    byte[] outBuf = new byte[bytesPerBuffer];
                    byte[] inBuf = inputLine != null ? new byte[inBytesPerBuffer] : null;

                    // Phase 2 (non-interleaved block path): pre-allocated DAC output buffers.
                    // Used when no ADC input is needed — avoids per-sample loop overhead.
                    final boolean useBlockPath = (inputLine == null);
                    float[][] dacBuffers = useBlockPath ? new float[numChannels][effBuf] : null;

                    long expectedBufferNanos = (long) (effBuf * 1_000_000_000.0 / actualSampleRate);
                    lastBufferTimeNanos = System.nanoTime();

                    while (running) {
                      long startTime = System.nanoTime();
                      boolean isIdle = (vm.getActiveShredCount() == 0);

                      long elapsed = startTime - lastBufferTimeNanos;
                      long drift = elapsed - expectedBufferNanos;
                      if (drift > 0) {
                        totalDriftNanos.addAndGet(drift);
                        driftCount.incrementAndGet();
                        if (drift > maxDriftNanos.get()) maxDriftNanos.set(drift);

                        // Phase 1-E: underrun detection — late by more than one buffer period
                        if (drift > expectedBufferNanos) {
                          long u = underrunCount.incrementAndGet();
                          if (u - underrunLogCount >= 100) {
                            underrunLogCount = u;
                            logger.log(
                                Level.WARNING,
                                String.format(
                                    "[Audio] Output underrun #%d (drift %.1f ms)", u, drift / 1e6));
                          }
                        }
                      }
                      lastBufferTimeNanos = startTime;

                      // ── Capture ──────────────────────────────────────────────
                      if (inputLine != null && inBuf != null) {
                        int available = inputLine.available();
                        // Phase 1-E: overflow detection — more than 2× buffer queued
                        if (available > inBytesPerBuffer * 2) {
                          long o = overflowCount.incrementAndGet();
                          if (o - overflowLogCount >= 100) {
                            overflowLogCount = o;
                            logger.log(
                                Level.WARNING,
                                String.format(
                                    "[Audio] Input overflow #%d (%d bytes queued)", o, available));
                          }
                        }
                        if (available >= inBytesPerBuffer) {
                          inputLine.read(inBuf, 0, inBytesPerBuffer);
                        }
                      }

                      double sumSq = 0;
                      float[] bufPeak = new float[numChannels];

                      // ── Idle Optimization ──
                      if (isIdle && smoothedGain < 0.0001f) {
                        // Truly idle: output silence and sleep to save CPU
                        for (int i = 0; i < effBuf; i++) {
                          for (int c = 0; c < numChannels; c++) {
                            writeSample(outSeg, fmt, i, c, numChannels, 0.0f);
                          }
                        }
                        try {
                          Thread.sleep(5);
                        } catch (InterruptedException ignored) {}
                      } else {
                        // Not idle OR fading out
                        float effectiveTargetGain = isIdle ? 0.0f : targetGain;

                        if (useBlockPath) {
                          vm.advanceTime(dacBuffers, 0, effBuf);
                          float gainStart = smoothedGain;
                          for (int step = 0; step < effBuf; step++) {
                            smoothedGain += gainSmoothAlpha * (effectiveTargetGain - smoothedGain);
                          }
                          float gainEnd = smoothedGain;
                          for (int i = 0; i < effBuf; i++) {
                            float g = gainStart + (gainEnd - gainStart) * i / effBuf;
                            for (int c = 0; c < numChannels; c++) {
                              float sample = dacBuffers[c][i] * g;
                              sumSq += (double) sample * sample;
                              float abs = Math.abs(sample);
                              if (abs > bufPeak[c]) bufPeak[c] = abs;
                              writeSample(outSeg, fmt, i, c, numChannels, sample);
                            }
                          }
                        } else {
                          for (int i = 0; i < effBuf; i++) {
                            // Feed ADC (always INT16 from input)
                            if (inBuf != null) {
                              for (int c = 0; c < numChannels; c++) {
                                int inputChan = Math.min(c, numInputChannels - 1);
                                int idx = (i * numInputChannels + inputChan) * 2;
                                short pcm = (short) ((inBuf[idx + 1] << 8) | (inBuf[idx] & 0xFF));
                                vm.adc.setInputSample(c, pcm / 32768.0f);
                              }
                            }
                            vm.advanceTime(1);
                            smoothedGain += gainSmoothAlpha * (effectiveTargetGain - smoothedGain);
                            for (int c = 0; c < numChannels; c++) {
                              float sample = vm.getDacChannel(c).getLastOut() * smoothedGain;
                              sumSq += (double) sample * sample;
                              float abs = Math.abs(sample);
                              if (abs > bufPeak[c]) bufPeak[c] = abs;
                              writeSample(outSeg, fmt, i, c, numChannels, sample);
                            }
                          }
                        }
                      }

                      if (verbose > 1
                          || (verbose > 0 && vm.getCurrentTime() % (actualSampleRate * 2) == 0)) {
                        double rms = Math.sqrt(sumSq / ((double) effBuf * numChannels));
                        if (rms > 1e-9) {
                          vm.print(String.format("[Audio] Engine RMS: %.6f\n", rms));
                        }
                      }

                      // Publish peak levels for VU meters (~10 ms decay per buffer)
                      for (int c = 0; c < numChannels && c < peakOut.length; c++) {
                        peakOut[c] = Math.max(bufPeak[c], peakOut[c] * 0.97f);
                      }

                      // Transfer off-heap → byte array → JavaSound
                      MemorySegment.copy(
                          outSeg, ValueLayout.JAVA_BYTE, 0, outBuf, 0, bytesPerBuffer);
                      outputLine.write(outBuf, 0, outBuf.length);

                      long endTime = System.nanoTime();
                      cpuLoad = (double) (endTime - startTime) / expectedBufferNanos;
                    }
                  } catch (Throwable t) {
                    logger.log(Level.SEVERE, "CRITICAL: Audio Engine Thread Crashed!", t);
                    vm.print("Audio Engine Error: " + t.getMessage());
                  }
                });
    if (scheduleRealtime) {
      audioThread.setPriority(Thread.MAX_PRIORITY);
    }
    audioThread.start();
  }

  /**
   * Phase 3: raise OS scheduler priority for the audio thread. Uses FFM on supported platforms;
   * falls back gracefully if the OS call fails (e.g., insufficient privileges).
   */
  private static void applyRealtimePriority(Thread thread) {
    String os = System.getProperty("os.name", "").toLowerCase();
    try {
      if (os.contains("win")) {
        // Windows: SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_TIME_CRITICAL=15)
        try (Arena a = Arena.ofConfined()) {
          SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", a);
          MethodHandle getThread =
              Linker.nativeLinker()
                  .downcallHandle(
                      kernel32.findOrThrow("GetCurrentThread"),
                      FunctionDescriptor.of(ValueLayout.ADDRESS));
          MethodHandle setPrio =
              Linker.nativeLinker()
                  .downcallHandle(
                      kernel32.findOrThrow("SetThreadPriority"),
                      FunctionDescriptor.of(
                          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
          MemorySegment hThread = (MemorySegment) getThread.invokeExact();
          setPrio.invokeExact(hThread, 15); // THREAD_PRIORITY_TIME_CRITICAL
          logger.info("[Audio] Real-time thread priority applied (Windows TIME_CRITICAL).");
        }
      } else if (os.contains("linux") || os.contains("mac")) {
        // POSIX: pthread_setschedparam(pthread_self(), SCHED_RR=2, {sched_priority=99})
        // Requires CAP_SYS_NICE on Linux or running as root on macOS — fails silently otherwise.
        try (Arena a = Arena.ofConfined()) {
          SymbolLookup libpthread =
              os.contains("mac")
                  ? SymbolLookup.libraryLookup("libpthread.dylib", a)
                  : SymbolLookup.libraryLookup("libpthread.so.0", a);
          MethodHandle self =
              Linker.nativeLinker()
                  .downcallHandle(
                      libpthread.findOrThrow("pthread_self"),
                      FunctionDescriptor.of(ValueLayout.ADDRESS));
          MethodHandle setschedparam =
              Linker.nativeLinker()
                  .downcallHandle(
                      libpthread.findOrThrow("pthread_setschedparam"),
                      FunctionDescriptor.of(
                          ValueLayout.JAVA_INT,
                          ValueLayout.ADDRESS,
                          ValueLayout.JAVA_INT,
                          ValueLayout.ADDRESS));
          MemorySegment tid = (MemorySegment) self.invokeExact();
          // sched_param struct: just one int (sched_priority)
          MemorySegment param = a.allocate(4);
          param.set(ValueLayout.JAVA_INT, 0, 99); // max priority for SCHED_RR
          int SCHED_RR = 2;
          int rc = (int) setschedparam.invokeExact(tid, SCHED_RR, param);
          if (rc == 0) {
            logger.info("[Audio] Real-time thread priority applied (POSIX SCHED_RR pri=99).");
          } else {
            logger.warning(
                "[Audio] pthread_setschedparam returned " + rc + " (need CAP_SYS_NICE).");
          }
        }
      }
    } catch (Throwable ex) {
      logger.warning("[Audio] Could not set real-time priority: " + ex.getMessage());
    }
  }

  // ── Sample-write helper (format-dispatched) ──────────────────────────────

  /**
   * Writes one sample into the off-heap MemorySegment in the correct format. Frame index {@code i}
   * and channel {@code c} are used to compute the byte offset; {@code numCh} is the stride.
   */
  private static void writeSample(
      MemorySegment seg, AudioSampleFormat fmt, int i, int c, int numCh, float sample) {
    long base = (long) (i * numCh + c);
    float clamp = Math.max(-1f, Math.min(1f, sample));
    switch (fmt) {
      case INT16 ->
          seg.set(
              ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN),
              base * 2,
              (short) (clamp * 32767f));
      case INT24 -> {
        int s24 = (int) (clamp * 8388607f);
        long off = base * 3;
        seg.set(ValueLayout.JAVA_BYTE, off, (byte) (s24 & 0xFF));
        seg.set(ValueLayout.JAVA_BYTE, off + 1, (byte) ((s24 >> 8) & 0xFF));
        seg.set(ValueLayout.JAVA_BYTE, off + 2, (byte) ((s24 >> 16) & 0xFF));
      }
      case INT32 ->
          seg.set(
              ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN),
              base * 4,
              (int) (clamp * (float) Integer.MAX_VALUE));
      case FLOAT32 ->
          // No clamping — float output can carry headroom beyond ±1
          seg.set(ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), base * 4, sample);
    }
  }

  // ── Recorder ─────────────────────────────────────────────────────────────

  public void startRecording(String filename) throws IOException {
    if (recorder == null) {
      recorder = new WvOut(sampleRate, numChannels);
    }
    recorder.open(filename);
  }

  public void stopRecording() throws IOException {
    if (recorder != null) {
      recorder.close();
    }
  }

  public boolean isRecording() {
    return recorder != null && recorder.isRecording();
  }

  // ── Performance metrics ───────────────────────────────────────────────────

  public double getAverageDriftMs() {
    long count = driftCount.get();
    return count == 0 ? 0.0 : (totalDriftNanos.get() / (double) count) / 1_000_000.0;
  }

  public double getCpuLoad() {
    return cpuLoad;
  }

  public double getMaxDriftMs() {
    return maxDriftNanos.get() / 1_000_000.0;
  }

  /** Peak amplitude [0,1] per output channel — updated every buffer for VU meters. */
  private final float[] peakOut = new float[8];

  public float getPeakOut(int channel) {
    return channel < peakOut.length ? peakOut[channel] : 0f;
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  public void stop() {
    running = false;
    try {
      stopRecording();
    } catch (IOException e) {
      // ignore on shutdown
    }
    if (inputLine != null) {
      inputLine.stop();
      inputLine.close();
    }
    if (outputLine != null) {
      outputLine.stop();
      outputLine.close();
    }
  }
}
