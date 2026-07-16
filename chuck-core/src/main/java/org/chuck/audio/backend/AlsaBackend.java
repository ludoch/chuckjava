package org.chuck.audio.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.audio.AudioSampleFormat;
import org.chuck.audio.ChuckAudio.DeviceInfo;

/**
 * Direct ALSA PCM backend via Foreign Function &amp; Memory downcalls to {@code libasound.so.2}
 * (Linux only) — bypasses PulseAudio/PipeWire's extra buffering hop that the default {@link
 * JavaSoundBackend} rides on top of.
 */
public class AlsaBackend implements AudioBackend {
  private static final Logger logger = Logger.getLogger(AlsaBackend.class.getName());

  /** Standard sample rates probed during device enumeration (matches RtAudio's probe list). */
  private static final int[] STANDARD_RATES = {
    8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000
  };

  @Override
  public String name() {
    return "ALSA";
  }

  @Override
  public boolean isAvailable() {
    return AlsaNative.AVAILABLE;
  }

  @Override
  public List<DeviceInfo> getOutputDeviceInfo() {
    return enumerate(AlsaNative.SND_PCM_STREAM_PLAYBACK);
  }

  @Override
  public List<DeviceInfo> getInputDeviceInfo() {
    return enumerate(AlsaNative.SND_PCM_STREAM_CAPTURE);
  }

  @Override
  public AudioBackendStream openStream(AudioStreamConfig config) throws Exception {
    if (!isAvailable()) {
      throw new IllegalStateException("ALSA (libasound.so.2) is not available on this platform");
    }
    NegotiatedParams playback =
        openWithFallback(
            config.outputDeviceName(),
            AlsaNative.SND_PCM_STREAM_PLAYBACK,
            config.numOutputChannels(),
            config.sampleFormat(),
            config.sampleRate(),
            config.bufferSize(),
            config.numBuffers(),
            /* nonblock= */ false);

    NegotiatedParams capture = null;
    if (config.numInputChannels() > 0) {
      try {
        capture =
            openWithFallback(
                config.inputDeviceName(),
                AlsaNative.SND_PCM_STREAM_CAPTURE,
                config.numInputChannels(),
                AudioSampleFormat.INT16, // capture is always INT16, matching AudioBackendStream's
                // readInput(short[]...) contract
                playback.actualRate(),
                config.bufferSize(),
                config.numBuffers(),
                /* nonblock= */ true);
      } catch (Exception e) {
        logger.log(Level.INFO, "[AlsaBackend] Capture device unavailable: " + e.getMessage());
        capture = null;
      }
    }

    int outputLatencySamples = playback.bufferFrames();
    int inputLatencySamples = capture != null ? capture.bufferFrames() : 0;

    return new AlsaBackendStream(
        config,
        playback.pcm(),
        capture != null ? capture.pcm() : null,
        playback.actualRate(),
        playback.periodFrames(),
        outputLatencySamples,
        inputLatencySamples);
  }

  /**
   * When a specific device name was requested, opens exactly that (fails loudly if it doesn't work
   * — an explicit request shouldn't silently redirect elsewhere). When none was requested, tries
   * {@code "default"} first, then falls through the actually-enumerated devices in order - some
   * environments (observed: a PipeWire-managed {@code "default"} PCM under a container) return
   * {@code EPERM} from {@code hw_params_any} on {@code "default"} while a concrete {@code
   * plughw:}/{@code sysdefault:} device opens fine, so a single hardcoded {@code "default"} attempt
   * isn't reliable enough as the sole no-device-requested behavior.
   */
  private NegotiatedParams openWithFallback(
      String requestedName,
      int stream,
      int channels,
      AudioSampleFormat fmt,
      int rate,
      int periodFrames,
      int periods,
      boolean nonblock)
      throws Exception {
    boolean explicit = requestedName != null && !requestedName.isEmpty();
    List<String> candidates = new ArrayList<>();
    if (explicit) {
      candidates.add(requestedName);
    } else {
      candidates.add("default");
      for (DeviceInfo info : enumerate(stream)) {
        if (!candidates.contains(info.name())) candidates.add(info.name());
      }
    }

    Exception last = null;
    for (String name : candidates) {
      try {
        return openAndConfigure(name, stream, channels, fmt, rate, periodFrames, periods, nonblock);
      } catch (Exception e) {
        last = e;
        logger.log(
            Level.FINE, "[AlsaBackend] PCM open failed for '" + name + "': " + e.getMessage());
      }
    }
    String direction = stream == AlsaNative.SND_PCM_STREAM_PLAYBACK ? "output" : "input";
    if (last != null) throw last;
    throw new java.io.IOException("No ALSA " + direction + " device available");
  }

  /** Holds an open+configured PCM handle plus the values ALSA actually negotiated. */
  private record NegotiatedParams(
      MemorySegment pcm, int actualRate, int periodFrames, int bufferFrames) {}

  /**
   * Opens {@code pcmName} and negotiates hw_params via the full {@code snd_pcm_hw_params_*} API
   * (not the {@code snd_pcm_set_params} convenience wrapper), so period size and period count map
   * directly onto {@link AudioStreamConfig#bufferSize()}/{@link AudioStreamConfig#numBuffers()} —
   * explicit control over both is the reason to use ALSA directly instead of JavaSound/PulseAudio
   * in the first place. Leaves the PCM in the {@code SND_PCM_STATE_PREPARED} state, ready for
   * {@code snd_pcm_writei}/{@code readi}.
   */
  private static NegotiatedParams openAndConfigure(
      String pcmName,
      int stream,
      int channels,
      AudioSampleFormat fmt,
      int desiredRate,
      int desiredPeriodFrames,
      int desiredPeriods,
      boolean nonblock)
      throws Exception {
    MemorySegment pcm = null;
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pcmPtr = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment nameSeg = arena.allocateFrom(pcmName);
      int mode = nonblock ? AlsaNative.SND_PCM_NONBLOCK : 0;
      int rc = (int) AlsaNative.snd_pcm_open.invokeExact(pcmPtr, nameSeg, stream, mode);
      if (rc != 0) {
        throw new java.io.IOException(
            "snd_pcm_open(" + pcmName + ") failed: " + AlsaNative.strerror(rc));
      }
      pcm = pcmPtr.get(ValueLayout.ADDRESS, 0);

      long paramsSize = (long) AlsaNative.snd_pcm_hw_params_sizeof.invokeExact();
      MemorySegment params = arena.allocate(paramsSize);
      requireZero(AlsaNative.snd_pcm_hw_params_any, pcm, params, "hw_params_any");
      requireZero(
          AlsaNative.snd_pcm_hw_params_set_access,
          pcm,
          params,
          AlsaNative.SND_PCM_ACCESS_RW_INTERLEAVED,
          "set_access");
      requireZero(
          AlsaNative.snd_pcm_hw_params_set_format,
          pcm,
          params,
          AlsaNative.toAlsaFormat(fmt),
          "set_format");
      requireZero(AlsaNative.snd_pcm_hw_params_set_channels, pcm, params, channels, "set_channels");

      MemorySegment rateBuf = arena.allocate(ValueLayout.JAVA_INT);
      rateBuf.set(ValueLayout.JAVA_INT, 0, desiredRate);
      MemorySegment dirBuf = arena.allocate(ValueLayout.JAVA_INT);
      int rateRc =
          (int)
              AlsaNative.snd_pcm_hw_params_set_rate_near.invokeExact(pcm, params, rateBuf, dirBuf);
      if (rateRc != 0) {
        throw new java.io.IOException("set_rate_near failed: " + AlsaNative.strerror(rateRc));
      }

      MemorySegment periodBuf = arena.allocate(ValueLayout.JAVA_LONG);
      periodBuf.set(ValueLayout.JAVA_LONG, 0, (long) desiredPeriodFrames);
      dirBuf.set(ValueLayout.JAVA_INT, 0, 0);
      int periodRc =
          (int)
              AlsaNative.snd_pcm_hw_params_set_period_size_near.invokeExact(
                  pcm, params, periodBuf, dirBuf);
      if (periodRc != 0) {
        throw new java.io.IOException(
            "set_period_size_near failed: " + AlsaNative.strerror(periodRc));
      }

      MemorySegment periodsBuf = arena.allocate(ValueLayout.JAVA_INT);
      periodsBuf.set(ValueLayout.JAVA_INT, 0, Math.max(2, desiredPeriods));
      dirBuf.set(ValueLayout.JAVA_INT, 0, 0);
      int periodsRc =
          (int)
              AlsaNative.snd_pcm_hw_params_set_periods_near.invokeExact(
                  pcm, params, periodsBuf, dirBuf);
      if (periodsRc != 0) {
        throw new java.io.IOException("set_periods_near failed: " + AlsaNative.strerror(periodsRc));
      }

      int commitRc = (int) AlsaNative.snd_pcm_hw_params.invokeExact(pcm, params);
      if (commitRc != 0) {
        throw new java.io.IOException(
            "snd_pcm_hw_params commit failed: " + AlsaNative.strerror(commitRc));
      }

      MemorySegment rateOut = arena.allocate(ValueLayout.JAVA_INT);
      dirBuf.set(ValueLayout.JAVA_INT, 0, 0);
      int getRateRc =
          (int) AlsaNative.snd_pcm_hw_params_get_rate.invokeExact(params, rateOut, dirBuf);
      int actualRate = getRateRc == 0 ? rateOut.get(ValueLayout.JAVA_INT, 0) : desiredRate;

      MemorySegment periodOut = arena.allocate(ValueLayout.JAVA_LONG);
      dirBuf.set(ValueLayout.JAVA_INT, 0, 0);
      int getPeriodRc =
          (int) AlsaNative.snd_pcm_hw_params_get_period_size.invokeExact(params, periodOut, dirBuf);
      int actualPeriodFrames =
          getPeriodRc == 0 ? (int) periodOut.get(ValueLayout.JAVA_LONG, 0) : desiredPeriodFrames;

      MemorySegment bufOut = arena.allocate(ValueLayout.JAVA_LONG);
      int getBufRc = (int) AlsaNative.snd_pcm_hw_params_get_buffer_size.invokeExact(params, bufOut);
      int actualBufferFrames =
          getBufRc == 0
              ? (int) bufOut.get(ValueLayout.JAVA_LONG, 0)
              : actualPeriodFrames * Math.max(2, desiredPeriods);

      int prepRc = (int) AlsaNative.snd_pcm_prepare.invokeExact(pcm);
      if (prepRc != 0) {
        throw new java.io.IOException("snd_pcm_prepare failed: " + AlsaNative.strerror(prepRc));
      }

      return new NegotiatedParams(pcm, actualRate, actualPeriodFrames, actualBufferFrames);
    } catch (Throwable t) {
      if (pcm != null) {
        try {
          int closeRc = (int) AlsaNative.snd_pcm_close.invokeExact(pcm);
          if (closeRc != 0) {
            logger.log(
                Level.FINE, "[AlsaBackend] cleanup snd_pcm_close: " + AlsaNative.strerror(closeRc));
          }
        } catch (Throwable ignored) {
          // best-effort cleanup only
        }
      }
      if (t instanceof Exception ex) throw ex;
      if (t instanceof Error err) throw err;
      throw new java.io.IOException("ALSA negotiation failed for " + pcmName, t);
    }
  }

  private static void requireZero(
      java.lang.invoke.MethodHandle setter, MemorySegment pcm, MemorySegment params, String what)
      throws Throwable {
    int rc = (int) setter.invokeExact(pcm, params);
    if (rc != 0) throw new java.io.IOException(what + " failed: " + AlsaNative.strerror(rc));
  }

  private static void requireZero(
      java.lang.invoke.MethodHandle setter,
      MemorySegment pcm,
      MemorySegment params,
      int value,
      String what)
      throws Throwable {
    int rc = (int) setter.invokeExact(pcm, params, value);
    if (rc != 0) throw new java.io.IOException(what + " failed: " + AlsaNative.strerror(rc));
  }

  // ── Device enumeration ───────────────────────────────────────────────────

  private List<DeviceInfo> enumerate(int stream) {
    List<DeviceInfo> result = new ArrayList<>();
    if (!isAvailable()) return result;
    for (String pcmName : hintNames(stream)) {
      try {
        DeviceInfo info = probe(pcmName, stream);
        if (info != null) result.add(info);
      } catch (Throwable ignored) {
        // Some hint names (e.g. a plughw for a card that just disappeared) may fail to open;
        // skip rather than fail the whole enumeration.
      }
    }
    return result;
  }

  /**
   * Queries ALSA's config/plugin registry for candidate PCM names — works even with no {@code
   * /dev/snd/*} node present (unlike {@code snd_ctl_*} card iteration), which matters for CI.
   * Filtered to the {@code default}/{@code plughw:*}/{@code sysdefault:*} families: these do
   * software format/rate conversion as needed, unlike raw {@code hw:*} which requires an exact
   * hardware match.
   */
  private List<String> hintNames(int stream) {
    // LinkedHashSet: ALSA's hint list can list the same logical device more than once.
    Set<String> names = new LinkedHashSet<>();
    if (!AlsaNative.AVAILABLE) return List.of();

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hintsPtr = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment iface = arena.allocateFrom("pcm");
      int rc = (int) AlsaNative.snd_device_name_hint.invokeExact(-1, iface, hintsPtr);
      if (rc != 0) return List.of();

      MemorySegment hints = hintsPtr.get(ValueLayout.ADDRESS, 0);
      MemorySegment idName = arena.allocateFrom("NAME");
      MemorySegment idIoid = arena.allocateFrom("IOID");

      for (long i = 0; ; i++) {
        MemorySegment hint =
            hints
                .reinterpret((i + 1) * ValueLayout.ADDRESS.byteSize())
                .get(ValueLayout.ADDRESS, i * ValueLayout.ADDRESS.byteSize());
        if (hint == null || hint.address() == 0) break;

        String name = getHintField(hint, idName);
        String ioid = getHintField(hint, idIoid);

        boolean directionOk =
            ioid == null
                || (stream == AlsaNative.SND_PCM_STREAM_PLAYBACK
                    ? "Output".equalsIgnoreCase(ioid)
                    : "Input".equalsIgnoreCase(ioid));
        boolean nameOk =
            name != null
                && ("default".equals(name)
                    || name.startsWith("plughw:")
                    || name.startsWith("sysdefault:"));

        if (directionOk && nameOk) names.add(name);
      }
      int freeRc = (int) AlsaNative.snd_device_name_free_hint.invokeExact(hints);
      if (freeRc != 0) {
        logger.log(
            Level.FINE, "[AlsaBackend] snd_device_name_free_hint: " + AlsaNative.strerror(freeRc));
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[AlsaBackend] device hint enumeration failed: " + t);
      return List.of();
    }
    return new ArrayList<>(names);
  }

  /** Reads one ALSA device-hint field (e.g. "NAME"/"IOID"), freeing the strdup'd result. */
  private String getHintField(MemorySegment hint, MemorySegment idSeg) throws Throwable {
    MemorySegment valuePtr =
        (MemorySegment) AlsaNative.snd_device_name_get_hint.invokeExact(hint, idSeg);
    if (valuePtr == null || valuePtr.address() == 0) return null;
    String value = AlsaNative.readCString(valuePtr);
    AlsaNative.libc_free.invokeExact(valuePtr);
    return value;
  }

  /** Transiently opens {@code pcmName} to probe its channel/rate/format capabilities. */
  private DeviceInfo probe(String pcmName, int stream) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pcmPtr = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment nameSeg = arena.allocateFrom(pcmName);
      int rc =
          (int)
              AlsaNative.snd_pcm_open.invokeExact(
                  pcmPtr, nameSeg, stream, AlsaNative.SND_PCM_NONBLOCK);
      if (rc != 0) return null;
      MemorySegment pcm = pcmPtr.get(ValueLayout.ADDRESS, 0);
      try {
        long paramsSize = (long) AlsaNative.snd_pcm_hw_params_sizeof.invokeExact();
        MemorySegment params = arena.allocate(paramsSize);
        rc = (int) AlsaNative.snd_pcm_hw_params_any.invokeExact(pcm, params);
        if (rc != 0) return null;

        int maxChannels = readUint(arena, params, AlsaNative.snd_pcm_hw_params_get_channels_max, 2);
        if (maxChannels <= 0 || maxChannels > 256) maxChannels = 2;

        List<Integer> rates = new ArrayList<>();
        for (int rate : STANDARD_RATES) {
          int r = (int) AlsaNative.snd_pcm_hw_params_test_rate.invokeExact(pcm, params, rate, 0);
          if (r == 0) rates.add(rate);
        }
        int preferred = 44100;
        if (!rates.isEmpty()) {
          preferred = rates.contains(48000) ? 48000 : rates.contains(44100) ? 44100 : rates.get(0);
        }

        List<AudioSampleFormat> formats = new ArrayList<>();
        for (AudioSampleFormat fmt : AudioSampleFormat.values()) {
          int alsaFmt = AlsaNative.toAlsaFormat(fmt);
          int r = (int) AlsaNative.snd_pcm_hw_params_test_format.invokeExact(pcm, params, alsaFmt);
          if (r == 0) formats.add(fmt);
        }
        if (formats.isEmpty()) formats.add(AudioSampleFormat.INT16);

        boolean isPlayback = stream == AlsaNative.SND_PCM_STREAM_PLAYBACK;
        return new DeviceInfo(
            pcmName,
            isPlayback ? maxChannels : 0,
            isPlayback ? 0 : maxChannels,
            rates,
            preferred,
            isPlayback ? formats : List.of(),
            isPlayback ? List.of() : formats);
      } finally {
        int closeRc = (int) AlsaNative.snd_pcm_close.invokeExact(pcm);
        if (closeRc != 0) {
          logger.log(Level.FINE, "[AlsaBackend] snd_pcm_close: " + AlsaNative.strerror(closeRc));
        }
      }
    } catch (Throwable t) {
      logger.log(Level.FINE, "[AlsaBackend] probe(" + pcmName + ") failed: " + t);
      return null;
    }
  }

  private int readUint(
      Arena arena, MemorySegment params, java.lang.invoke.MethodHandle getter, int fallback)
      throws Throwable {
    MemorySegment out = arena.allocate(ValueLayout.JAVA_INT);
    int rc = (int) getter.invokeExact(params, out);
    return rc == 0 ? out.get(ValueLayout.JAVA_INT, 0) : fallback;
  }
}
