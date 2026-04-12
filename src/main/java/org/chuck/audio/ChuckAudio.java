package org.chuck.audio;

import java.io.IOException;
import java.lang.foreign.*;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.*;
import org.chuck.audio.util.Gain;
import org.chuck.audio.util.WvOut;
import org.chuck.core.ChuckVM;

/**
 * Handles Audio I/O for the ChuckVM. Output: SourceDataLine (playback). Input: TargetDataLine
 * (microphone/ADC) — opened gracefully; silent if unavailable.
 */
public class ChuckAudio {
  private static final Logger logger = Logger.getLogger(ChuckAudio.class.getName());
  private final ChuckVM vm;
  private final int bufferSize;
  private int numChannels;
  private int numInputChannels;
  private final float sampleRate;

  private SourceDataLine outputLine;
  private TargetDataLine inputLine; // null if no mic available
  private boolean running = false;
  // Zipper-noise prevention: UI sets targetGain; audio thread smooths toward it each sample.
  // Alpha ≈ 1 − exp(−1 / (44100 × 0.02)) gives ~20 ms ramp; good for any typical sample rate.
  private volatile float targetGain = 0.8f;
  private float smoothedGain = 0.8f;
  private float gainSmoothAlpha = 0.00113f; // recomputed in initJavaSound if SR differs
  private Gain masterGainUGen;
  private int verbose = 1;

  // Drift tracking (Wall Clock)
  private long lastBufferTimeNanos = 0;
  private final java.util.concurrent.atomic.AtomicLong totalDriftNanos =
      new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong driftCount =
      new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong maxDriftNanos =
      new java.util.concurrent.atomic.AtomicLong(0);

  // Optional recorder
  private WvOut recorder;

  /** Name of the preferred output mixer (empty = system default). */
  private String outputDeviceName = "";

  /** Name of the preferred input mixer (empty = system default). */
  private String inputDeviceName = "";

  public void setOutputDeviceName(String name) {
    this.outputDeviceName = name == null ? "" : name;
  }

  public void setInputDeviceName(String name) {
    this.inputDeviceName = name == null ? "" : name;
  }

  /** Returns all mixer names that support SourceDataLine (playback). */
  public static java.util.List<String> getOutputDeviceNames() {
    java.util.List<String> names = new java.util.ArrayList<>();
    names.add(""); // system default
    for (javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        javax.sound.sampled.Mixer m = AudioSystem.getMixer(info);
        DataLine.Info dl = new DataLine.Info(SourceDataLine.class, null);
        if (m.isLineSupported(dl)) names.add(info.getName());
      } catch (Exception ignored) {
      }
    }
    return names;
  }

  /** Returns all mixer names that support TargetDataLine (capture). */
  public static java.util.List<String> getInputDeviceNames() {
    java.util.List<String> names = new java.util.ArrayList<>();
    names.add(""); // system default
    for (javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        javax.sound.sampled.Mixer m = AudioSystem.getMixer(info);
        DataLine.Info dl = new DataLine.Info(TargetDataLine.class, null);
        if (m.isLineSupported(dl)) names.add(info.getName());
      } catch (Exception ignored) {
      }
    }
    return names;
  }

  public ChuckAudio(ChuckVM vm, int bufferSize, int numChannels, float sampleRate) {
    this.vm = vm;
    this.bufferSize = bufferSize;
    this.numChannels = numChannels;
    this.numInputChannels = numChannels; // Default to same as output
    this.sampleRate = sampleRate;
    initJavaSound();
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

  /** Opens a SourceDataLine on the named mixer, falling back to system default. */
  private SourceDataLine openOutputLine(AudioFormat fmt) throws LineUnavailableException {
    if (!outputDeviceName.isEmpty()) {
      for (javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
        if (info.getName().equals(outputDeviceName)) {
          try {
            javax.sound.sampled.Mixer m = AudioSystem.getMixer(info);
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
      for (javax.sound.sampled.Mixer.Info info : AudioSystem.getMixerInfo()) {
        if (info.getName().equals(inputDeviceName)) {
          try {
            javax.sound.sampled.Mixer m = AudioSystem.getMixer(info);
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

  private void initJavaSound() {
    // Recompute alpha for actual sample rate: 1 − exp(−1/(sr × rampSeconds))
    gainSmoothAlpha = (float) (1.0 - Math.exp(-1.0 / (sampleRate * 0.02)));
    AudioFormat format = new AudioFormat(sampleRate, 16, numChannels, true, false);
    // Output
    try {
      outputLine = openOutputLine(format);
      outputLine.open(format, bufferSize * numChannels * 4);
    } catch (LineUnavailableException e) {
      logger.log(Level.SEVERE, "Audio output unavailable: " + e.getMessage());
    }
    // Input (microphone) — optional
    try {
      if (!AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, format))) {
        // Try mono if stereo not supported
        AudioFormat monoFormat = new AudioFormat(sampleRate, 16, 1, true, false);
        if (AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, monoFormat))) {
          inputLine = openInputLine(monoFormat);
          inputLine.open(monoFormat, bufferSize * 4);
          numInputChannels = 1;
          logger.log(Level.INFO, "[Audio] Microphone input line opened (MONO): " + monoFormat);
        }
      } else {
        inputLine = openInputLine(format);
        inputLine.open(format, bufferSize * numChannels * 4);
        logger.log(Level.INFO, "[Audio] Microphone input line opened: " + format);
      }
    } catch (LineUnavailableException | SecurityException e) {
      logger.log(Level.INFO, "[Audio] Microphone access failed: " + e.getMessage());
      inputLine = null;
    }
  }

  public void start() {
    if (running || outputLine == null) return;
    running = true;
    outputLine.start();
    if (inputLine != null) inputLine.start();

    Thread.ofPlatform()
        .name("ChucK-Audio-Engine")
        .start(
            () -> {
              try (Arena arena = Arena.ofShared()) {
                int bytesPerBuffer = bufferSize * numChannels * 2; // 16-bit PCM
                MemorySegment outSeg = arena.allocate(bytesPerBuffer);
                byte[] outBuf = new byte[bytesPerBuffer];
                byte[] inBuf = inputLine != null ? new byte[bytesPerBuffer] : null;

                long expectedBufferNanos = (long) (bufferSize * 1_000_000_000.0 / sampleRate);
                lastBufferTimeNanos = System.nanoTime();

                while (running) {
                  long startTime = System.nanoTime();
                  long elapsedSinceLast = startTime - lastBufferTimeNanos;
                  long drift = elapsedSinceLast - expectedBufferNanos;
                  if (drift > 0) {
                    totalDriftNanos.addAndGet(drift);
                    driftCount.incrementAndGet();
                    if (drift > maxDriftNanos.get()) maxDriftNanos.set(drift);
                  }
                  lastBufferTimeNanos = startTime;

                  // ── Capture: read only if available to avoid blocking output
                  if (inputLine != null && inBuf != null) {
                    int available = inputLine.available();
                    int bytesNeeded = bufferSize * numInputChannels * 2;
                    if (available >= bytesNeeded) {
                      inputLine.read(inBuf, 0, bytesNeeded);
                    }
                  }

                  double sumSq = 0;
                  // Reset per-buffer peak accumulators
                  float[] bufPeak = new float[numChannels];

                  // ── Per-sample processing ─────────────────────────────────────
                  for (int i = 0; i < bufferSize; i++) {
                    // Feed ADC
                    if (inBuf != null) {
                      for (int c = 0; c < numChannels; c++) {
                        int inputChan = Math.min(c, numInputChannels - 1);
                        int idx = (i * numInputChannels + inputChan) * 2;
                        short pcm = (short) ((inBuf[idx + 1] << 8) | (inBuf[idx] & 0xFF));
                        vm.adc.setInputSample(c, pcm / 32768.0f);
                      }
                    }

                    // Advance time by 1 sample
                    vm.advanceTime(1);

                    // Smooth gain toward target to prevent zipper noise on rapid slider changes.
                    smoothedGain += gainSmoothAlpha * (targetGain - smoothedGain);

                    // Interleave Left/Right for stereo output
                    for (int c = 0; c < numChannels; c++) {
                      float sample = vm.getDacChannel(c).getLastOut() * smoothedGain;

                      sumSq += (double) sample * sample;
                      float abs = Math.abs(sample);
                      if (abs > bufPeak[c]) bufPeak[c] = abs;
                      short s16 = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767f);

                      // Write directly to off-heap MemorySegment
                      long offset = (long) (i * numChannels + c) * 2;
                      outSeg.set(
                          ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), offset, s16);
                    }
                  }

                  if (verbose > 1 || (verbose > 0 && vm.getCurrentTime() % (sampleRate * 2) == 0)) {
                    double rms = Math.sqrt(sumSq / (bufferSize * numChannels));
                    if (rms > 1e-9) {
                      vm.print(String.format("[Audio] Engine RMS: %.6f\n", rms));
                    }
                  }

                  // Publish peak levels for VU meters (with ~10ms decay per buffer)
                  for (int c = 0; c < numChannels && c < peakOut.length; c++) {
                    peakOut[c] = Math.max(bufPeak[c], peakOut[c] * 0.97f);
                  }

                  // Transfer from off-heap to byte array for JavaSound write
                  MemorySegment.copy(outSeg, ValueLayout.JAVA_BYTE, 0, outBuf, 0, bytesPerBuffer);
                  outputLine.write(outBuf, 0, outBuf.length);
                }
              } catch (Throwable t) {
                logger.log(Level.SEVERE, "CRITICAL: Audio Engine Thread Crashed!", t);
                vm.print("Audio Engine Error: " + t.getMessage());
              }
            });
  }

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

  public double getAverageDriftMs() {
    long count = driftCount.get();
    return count == 0 ? 0.0 : (totalDriftNanos.get() / (double) count) / 1_000_000.0;
  }

  public double getMaxDriftMs() {
    return maxDriftNanos.get() / 1_000_000.0;
  }

  /** Peak amplitude [0,1] for each output channel — updated every buffer for the VU meters. */
  private final float[] peakOut = new float[8];

  public float getPeakOut(int channel) {
    return channel < peakOut.length ? peakOut[channel] : 0f;
  }

  public void stop() {
    running = false;
    try {
      stopRecording();
    } catch (IOException e) {
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
