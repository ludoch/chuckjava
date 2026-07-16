package org.chuck.audio.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hand-rolled Foreign Function &amp; Memory bindings to {@code libasound.so.2} (ALSA), covering the
 * PCM I/O API and the device-hint enumeration API. Follows the same pattern as {@link
 * org.chuck.hid.HidNative}: dynamically resolve a system-provided shared library, bind individual
 * symbols defensively, and expose an {@link #AVAILABLE} flag so callers on non-Linux platforms (or
 * a Linux box without alsa-lib) can degrade gracefully instead of crashing at class-load time.
 *
 * <p>{@code snd_pcm_hw_params_t} is treated as an opaque blob: allocated via {@code
 * snd_pcm_hw_params_sizeof()} and never read/written field-by-field from Java, matching how the
 * sibling {@code rtmidijava} library handles ALSA's other opaque structs.
 */
final class AlsaNative {
  private static final Logger logger = Logger.getLogger(AlsaNative.class.getName());
  private static final Linker LINKER = Linker.nativeLinker();

  // ── PCM stream direction (snd_pcm_stream_t) ─────────────────────────────
  static final int SND_PCM_STREAM_PLAYBACK = 0;
  static final int SND_PCM_STREAM_CAPTURE = 1;

  // ── snd_pcm_open mode flags ──────────────────────────────────────────────
  static final int SND_PCM_NONBLOCK = 0x00000001;

  // ── snd_pcm_access_t ─────────────────────────────────────────────────────
  static final int SND_PCM_ACCESS_RW_INTERLEAVED = 3;

  // ── snd_pcm_format_t (subset actually used) ──────────────────────────────
  static final int SND_PCM_FORMAT_S16_LE = 2;
  static final int SND_PCM_FORMAT_S32_LE = 10;
  static final int SND_PCM_FORMAT_FLOAT_LE = 14;
  static final int SND_PCM_FORMAT_S24_3LE = 32; // 3-byte packed, NOT the 4-byte S24_LE=6

  /** True if libasound.so.2 was found and every symbol below was bound successfully. */
  static final boolean AVAILABLE;

  private static SymbolLookup alsa;

  static MethodHandle snd_pcm_open;
  static MethodHandle snd_pcm_close;
  static MethodHandle snd_pcm_prepare;
  static MethodHandle snd_pcm_drop;
  static MethodHandle snd_pcm_drain;
  static MethodHandle snd_pcm_nonblock;

  static MethodHandle snd_pcm_hw_params_sizeof;
  static MethodHandle snd_pcm_hw_params_any;
  static MethodHandle snd_pcm_hw_params_set_access;
  static MethodHandle snd_pcm_hw_params_set_format;
  static MethodHandle snd_pcm_hw_params_set_channels;
  static MethodHandle snd_pcm_hw_params_set_rate_near;
  static MethodHandle snd_pcm_hw_params_set_period_size_near;
  static MethodHandle snd_pcm_hw_params_set_periods_near;
  static MethodHandle snd_pcm_hw_params;
  static MethodHandle snd_pcm_hw_params_get_rate;
  static MethodHandle snd_pcm_hw_params_get_period_size;
  static MethodHandle snd_pcm_hw_params_get_buffer_size;
  static MethodHandle snd_pcm_hw_params_get_channels_min;
  static MethodHandle snd_pcm_hw_params_get_channels_max;
  static MethodHandle snd_pcm_hw_params_test_rate;
  static MethodHandle snd_pcm_hw_params_test_format;

  static MethodHandle snd_pcm_writei;
  static MethodHandle snd_pcm_readi;
  static MethodHandle snd_pcm_avail_update;
  static MethodHandle snd_pcm_recover;
  static MethodHandle snd_strerror;

  static MethodHandle snd_device_name_hint;
  static MethodHandle snd_device_name_get_hint;
  static MethodHandle snd_device_name_free_hint;
  static MethodHandle libc_free;
  static MethodHandle libc_strlen;

  static {
    boolean ok;
    try {
      alsa = SymbolLookup.libraryLookup("libasound.so.2", Arena.global());
      bindAll();
      ok = true;
    } catch (Throwable t) {
      logger.log(Level.FINE, "[AlsaNative] libasound.so.2 not available: " + t);
      ok = false;
    }
    AVAILABLE = ok;
  }

  private AlsaNative() {}

  private static MethodHandle downcall(String name, FunctionDescriptor desc) {
    return alsa.find(name).map(addr -> LINKER.downcallHandle(addr, desc)).orElse(null);
  }

  private static void bindAll() {
    var INT = ValueLayout.JAVA_INT;
    var LONG = ValueLayout.JAVA_LONG;
    var ADDR = ValueLayout.ADDRESS;

    snd_pcm_open = downcall("snd_pcm_open", FunctionDescriptor.of(INT, ADDR, ADDR, INT, INT));
    snd_pcm_close = downcall("snd_pcm_close", FunctionDescriptor.of(INT, ADDR));
    snd_pcm_prepare = downcall("snd_pcm_prepare", FunctionDescriptor.of(INT, ADDR));
    snd_pcm_drop = downcall("snd_pcm_drop", FunctionDescriptor.of(INT, ADDR));
    snd_pcm_drain = downcall("snd_pcm_drain", FunctionDescriptor.of(INT, ADDR));
    snd_pcm_nonblock = downcall("snd_pcm_nonblock", FunctionDescriptor.of(INT, ADDR, INT));

    snd_pcm_hw_params_sizeof = downcall("snd_pcm_hw_params_sizeof", FunctionDescriptor.of(LONG));
    snd_pcm_hw_params_any =
        downcall("snd_pcm_hw_params_any", FunctionDescriptor.of(INT, ADDR, ADDR));
    snd_pcm_hw_params_set_access =
        downcall("snd_pcm_hw_params_set_access", FunctionDescriptor.of(INT, ADDR, ADDR, INT));
    snd_pcm_hw_params_set_format =
        downcall("snd_pcm_hw_params_set_format", FunctionDescriptor.of(INT, ADDR, ADDR, INT));
    snd_pcm_hw_params_set_channels =
        downcall("snd_pcm_hw_params_set_channels", FunctionDescriptor.of(INT, ADDR, ADDR, INT));
    snd_pcm_hw_params_set_rate_near =
        downcall(
            "snd_pcm_hw_params_set_rate_near", FunctionDescriptor.of(INT, ADDR, ADDR, ADDR, ADDR));
    snd_pcm_hw_params_set_period_size_near =
        downcall(
            "snd_pcm_hw_params_set_period_size_near",
            FunctionDescriptor.of(INT, ADDR, ADDR, ADDR, ADDR));
    snd_pcm_hw_params_set_periods_near =
        downcall(
            "snd_pcm_hw_params_set_periods_near",
            FunctionDescriptor.of(INT, ADDR, ADDR, ADDR, ADDR));
    snd_pcm_hw_params = downcall("snd_pcm_hw_params", FunctionDescriptor.of(INT, ADDR, ADDR));
    snd_pcm_hw_params_get_rate =
        downcall("snd_pcm_hw_params_get_rate", FunctionDescriptor.of(INT, ADDR, ADDR, ADDR));
    snd_pcm_hw_params_get_period_size =
        downcall("snd_pcm_hw_params_get_period_size", FunctionDescriptor.of(INT, ADDR, ADDR, ADDR));
    snd_pcm_hw_params_get_buffer_size =
        downcall("snd_pcm_hw_params_get_buffer_size", FunctionDescriptor.of(INT, ADDR, ADDR));
    snd_pcm_hw_params_get_channels_min =
        downcall("snd_pcm_hw_params_get_channels_min", FunctionDescriptor.of(INT, ADDR, ADDR));
    snd_pcm_hw_params_get_channels_max =
        downcall("snd_pcm_hw_params_get_channels_max", FunctionDescriptor.of(INT, ADDR, ADDR));
    snd_pcm_hw_params_test_rate =
        downcall("snd_pcm_hw_params_test_rate", FunctionDescriptor.of(INT, ADDR, ADDR, INT, INT));
    snd_pcm_hw_params_test_format =
        downcall("snd_pcm_hw_params_test_format", FunctionDescriptor.of(INT, ADDR, ADDR, INT));

    snd_pcm_writei = downcall("snd_pcm_writei", FunctionDescriptor.of(LONG, ADDR, ADDR, LONG));
    snd_pcm_readi = downcall("snd_pcm_readi", FunctionDescriptor.of(LONG, ADDR, ADDR, LONG));
    snd_pcm_avail_update = downcall("snd_pcm_avail_update", FunctionDescriptor.of(LONG, ADDR));
    snd_pcm_recover = downcall("snd_pcm_recover", FunctionDescriptor.of(INT, ADDR, INT, INT));
    snd_strerror = downcall("snd_strerror", FunctionDescriptor.of(ADDR, INT));

    snd_device_name_hint =
        downcall("snd_device_name_hint", FunctionDescriptor.of(INT, INT, ADDR, ADDR));
    snd_device_name_get_hint =
        downcall("snd_device_name_get_hint", FunctionDescriptor.of(ADDR, ADDR, ADDR));
    snd_device_name_free_hint =
        downcall("snd_device_name_free_hint", FunctionDescriptor.of(INT, ADDR));

    SymbolLookup libc = LINKER.defaultLookup();
    libc_free =
        libc.find("free")
            .map(addr -> LINKER.downcallHandle(addr, FunctionDescriptor.ofVoid(ADDR)))
            .orElse(null);
    libc_strlen =
        libc.find("strlen")
            .map(addr -> LINKER.downcallHandle(addr, FunctionDescriptor.of(LONG, ADDR)))
            .orElse(null);
  }

  /** Reads a NUL-terminated C string at {@code addr}, or {@code null} if addr is NULL. */
  static String readCString(MemorySegment addr) {
    if (addr == null || addr.address() == 0) return null;
    try {
      long len = (long) libc_strlen.invokeExact(addr);
      return addr.reinterpret(len + 1).getString(0);
    } catch (Throwable t) {
      return null;
    }
  }

  /** Best-effort human-readable errno string for logging. */
  static String strerror(int errnum) {
    try {
      MemorySegment s = (MemorySegment) snd_strerror.invokeExact(errnum);
      String msg = readCString(s);
      return msg != null ? msg : ("errno " + errnum);
    } catch (Throwable t) {
      return "errno " + errnum;
    }
  }

  /**
   * Maps a cross-backend {@link org.chuck.audio.AudioSampleFormat} to its ALSA {@code
   * snd_pcm_format_t} equivalent. Kept local to the ALSA backend rather than added to the shared
   * enum: ALSA has two incompatible "24-bit" layouts (3-byte-packed {@code S24_3LE} vs. the
   * 4-byte-container {@code S24_LE}), and {@code INT24} here always means the former, matching
   * {@link org.chuck.audio.AudioSampleFormat#INT24}'s {@code bytesPerSample=3}.
   */
  static int toAlsaFormat(org.chuck.audio.AudioSampleFormat fmt) {
    return switch (fmt) {
      case INT16 -> SND_PCM_FORMAT_S16_LE;
      case INT24 -> SND_PCM_FORMAT_S24_3LE;
      case INT32 -> SND_PCM_FORMAT_S32_LE;
      case FLOAT32 -> SND_PCM_FORMAT_FLOAT_LE;
    };
  }
}
