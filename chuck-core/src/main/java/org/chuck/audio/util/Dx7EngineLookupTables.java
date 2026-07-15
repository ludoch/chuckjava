package org.chuck.audio.util;

/**
 * Static lookup tables ported from the dexed/msfa implementation.
 *
 * <p>All tables are initialized once via {@link #init(double)} and then shared across all Dx7Engine
 * instances. Math conventions match the firmware:
 *
 * <ul>
 *   <li>phase: Q32 (wraps on 32-bit overflow)
 *   <li>logfreq: Q24 log2(frequency)
 *   <li>level_: Q24 log-domain (2^24 = 6dB doubling)
 *   <li>Sin output: Q24 (±~1&lt;&lt;24)
 *   <li>gain: Q24 linear
 *   <li>lfo_value: Q24
 *   <li>pitch_env: Q24/octave
 * </ul>
 */
public final class Dx7EngineLookupTables {

  // ── Table constants (matching engine.h) ──

  public static final int EXP2_LG_N_SAMPLES = 10;
  public static final int EXP2_N_SAMPLES = 1 << EXP2_LG_N_SAMPLES;
  static final int[] exp2tab = new int[EXP2_N_SAMPLES << 1];

  public static final int TANH_LG_N_SAMPLES = 10;
  public static final int TANH_N_SAMPLES = 1 << TANH_LG_N_SAMPLES;
  static final int[] tanhtab = new int[TANH_N_SAMPLES << 1];

  // SIN_DELTA: twice as much RAM but less computation
  public static final int SIN_LG_N_SAMPLES = 10;
  public static final int SIN_N_SAMPLES = 1 << SIN_LG_N_SAMPLES;
  static final int[] sintab = new int[SIN_N_SAMPLES << 1];

  public static final int FREQ_LG_N_SAMPLES = 10;
  public static final int FREQ_N_SAMPLES = 1 << FREQ_LG_N_SAMPLES;
  static final int[] freq_lut = new int[FREQ_N_SAMPLES + 1];

  // ── Static DX7 tables ──

  /** Coarse frequency multipliers (32 entries). */
  static final int[] coarsemul = {
    -16777216, 0, 16777216, 26591258, 33554432, 38955489, 43368474, 47099600, 50331648, 53182516,
    55732705, 58039632, 60145690, 62083076, 63876816, 65546747, 67108864, 68576247, 69959732,
    71268397, 72509921, 73690858, 74816848, 75892776, 76922906, 77910978, 78860292, 79773775,
    80654032, 81503396, 82323963, 83117622
  };

  /** Amp mod sensitivity table (4 entries). */
  static final int[] ampmodsenstab = {0, 4342338, 7171437, 16777216};

  /** Pitch mod sensitivity table (8 entries). */
  static final int[] pitchmodsenstab = {0, 10, 20, 33, 55, 92, 153, 255};

  /** Velocity data LUT (64 entries). */
  static final int[] velocity_data = {
    0, 70, 86, 97, 106, 114, 121, 126, 132, 138, 142, 148, 152, 156, 160, 163,
    166, 170, 173, 174, 178, 181, 184, 186, 189, 190, 194, 196, 198, 200, 202, 205,
    206, 209, 211, 214, 216, 218, 220, 222, 224, 225, 227, 229, 230, 232, 233, 235,
    237, 238, 240, 241, 242, 243, 244, 246, 246, 248, 249, 250, 251, 252, 253, 254
  };

  /** Exponential scale data (33 entries). */
  static final int[] exp_scale_data = {
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 14, 16, 19, 23, 27, 33, 39, 47, 56, 66, 80, 94, 110, 126, 142,
    158, 174, 190, 206, 222, 238, 250
  };

  /** Envelope level LUT (20 entries, for outlevel 0-19). */
  static final int[] levellut = {
    0, 5, 9, 13, 17, 20, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 42, 43, 45, 46
  };

  /** Envelope static counts for ACCURATE_ENVELOPE (77 entries at 44.1kHz). */
  static final int[] statics = {
    1764000, 1764000, 1411200, 1411200, 1190700, 1014300, 992250, 882000, 705600, 705600,
    584325, 507150, 502740, 441000, 418950, 352800, 308700, 286650, 253575, 220500,
    220500, 176400, 145530, 145530, 125685, 110250, 110250, 88200, 88200, 74970,
    61740, 61740, 55125, 48510, 44100, 37485, 31311, 30870, 27562, 27562,
    22050, 18522, 17640, 15435, 14112, 13230, 11025, 9261, 9261, 7717,
    6615, 6615, 5512, 5512, 4410, 3969, 3969, 3439, 2866, 2690,
    2249, 1984, 1896, 1808, 1411, 1367, 1234, 1146, 926, 837,
    837, 705, 573, 573, 529, 441, 441
  };

  /** Pitch envelope rate table (101 entries). */
  static final int[] pitchenv_rate = {
    1, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10,
    10, 11, 11, 12, 12, 13, 13, 14, 14, 15, 16, 16, 17, 18, 18, 19, 20,
    21, 22, 23, 24, 25, 26, 27, 28, 30, 31, 33, 34, 36, 37, 38, 39, 41,
    42, 44, 46, 47, 49, 51, 53, 54, 56, 58, 60, 62, 64, 66, 68, 70, 72,
    74, 76, 79, 82, 85, 88, 91, 94, 98, 102, 106, 110, 115, 120, 125, 130, 135,
    141, 147, 153, 159, 165, 171, 178, 185, 193, 202, 211, 232, 243, 254, 255
  };

  /** Pitch envelope table (99 entries, signed Q24/octave). */
  static final int[] pitchenv_tab = {
    -128, -116, -104, -95, -85, -76, -68, -61, -56, -52, -49, -46, -43, -41, -39, -37, -35,
    -33, -32, -31, -30, -29, -28, -27, -26, -25, -24, -23, -22, -21, -20, -19, -18, -17,
    -16, -15, -14, -13, -12, -11, -10, -9, -8, -7, -6, -5, -4, -3, -2, -1, 0,
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
    18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34,
    35, 38, 40, 43, 46, 49, 53, 58, 65, 73, 82, 92, 103, 115, 127
  };

  /**
   * LFO unit constant. Matches firmware: (int32_t)(25190424 / 44100.0 + 0.5) This is 571.5 in fixed
   * point.
   */
  static final int lfo_unit = (int) (25190424 / (float) 44100 + 0.5f);

  // ── FmCore algorithm array (32 algorithms × 6 operators) ──
  // Each byte encodes outbus(1:0), OUT_BUS_ADD(2), inbus(5:4), FB_IN(6), FB_OUT(7)

  /** Index into algorithm flags: FB_OUT flag. */
  public static final int FB_OUT = 1 << 7;

  /** Index into algorithm flags: FB_IN flag. */
  public static final int FB_IN = 1 << 6;

  /** Index into algorithm flags: IN_BUS_TWO. */
  public static final int IN_BUS_TWO = 1 << 5;

  /** Index into algorithm flags: IN_BUS_ONE. */
  public static final int IN_BUS_ONE = 1 << 4;

  /** Index into algorithm flags: OUT_BUS_ADD. */
  public static final int OUT_BUS_ADD = 1 << 2;

  /** Index into algorithm flags: OUT_BUS_TWO. */
  public static final int OUT_BUS_TWO = 1 << 1;

  /** Index into algorithm flags: OUT_BUS_ONE. */
  public static final int OUT_BUS_ONE = 1 << 0;

  /** The 32 DX7 algorithms — flat array: [algo0_op0, algo0_op1, ..., algo31_op5]. */
  public static final int[] ALGORITHMS = {
    0xc1, 0x11, 0x11, 0x14, 0x01, 0x14, // 0
    0x01, 0x11, 0x11, 0x14, 0xc1, 0x14, // 1
    0xc1, 0x11, 0x14, 0x01, 0x11, 0x14, // 2
    0xc1, 0x11, 0x94, 0x01, 0x11, 0x14, // 3
    0xc1, 0x14, 0x01, 0x14, 0x01, 0x14, // 4
    0xc1, 0x94, 0x01, 0x14, 0x01, 0x14, // 5
    0xc1, 0x11, 0x05, 0x14, 0x01, 0x14, // 6
    0x01, 0x11, 0xc5, 0x14, 0x01, 0x14, // 7
    0x01, 0x11, 0x05, 0x14, 0xc1, 0x14, // 8
    0x01, 0x05, 0x14, 0xc1, 0x11, 0x14, // 9
    0xc1, 0x05, 0x14, 0x01, 0x11, 0x14, // 10
    0x01, 0x05, 0x05, 0x14, 0xc1, 0x14, // 11
    0xc1, 0x05, 0x05, 0x14, 0x01, 0x14, // 12
    0xc1, 0x05, 0x11, 0x14, 0x01, 0x14, // 13
    0x01, 0x05, 0x11, 0x14, 0xc1, 0x14, // 14
    0xc1, 0x11, 0x02, 0x25, 0x05, 0x14, // 15
    0x01, 0x11, 0x02, 0x25, 0xc5, 0x14, // 16
    0x01, 0x11, 0x11, 0xc5, 0x05, 0x14, // 17
    0xc1, 0x14, 0x14, 0x01, 0x11, 0x14, // 18
    0x01, 0x05, 0x14, 0xc1, 0x14, 0x14, // 19
    0x01, 0x14, 0x14, 0xc1, 0x14, 0x14, // 20
    0xc1, 0x14, 0x14, 0x14, 0x01, 0x14, // 21
    0xc1, 0x14, 0x14, 0x01, 0x14, 0x04, // 22
    0xc1, 0x14, 0x14, 0x14, 0x04, 0x04, // 23
    0xc1, 0x14, 0x14, 0x04, 0x04, 0x04, // 24
    0xc1, 0x05, 0x14, 0x01, 0x14, 0x04, // 25
    0x01, 0x05, 0x14, 0xc1, 0x14, 0x04, // 26
    0x04, 0xc1, 0x11, 0x14, 0x01, 0x14, // 27
    0xc1, 0x14, 0x01, 0x14, 0x04, 0x04, // 28
    0x04, 0xc1, 0x11, 0x14, 0x04, 0x04, // 29
    0xc1, 0x14, 0x04, 0x04, 0x04, 0x04, // 30
    0xc4, 0x04, 0x04, 0x04, 0x04, 0x04 // 31
  };

  // ── EngineMkI constants (EngineMkI.cpp) ──

  /** NEGATIVE_BIT for EngineMkI sinLog tables. */
  public static final int NEGATIVE_BIT = 0x8000;

  /** ENV_BITDEPTH for EngineMkI envelope integration (14-bit). */
  public static final int ENV_BITDEPTH = 14;

  /** ENV_MAX = 1 << ENV_BITDEPTH = 16384. */
  public static final int ENV_MAX = 1 << ENV_BITDEPTH;

  /** Table size for sinLog (10-bit). */
  public static final int SINLOG_BITDEPTH = 10;

  /** Table size for sinLog (1024 entries). */
  public static final int SINLOG_TABLESIZE = 1 << SINLOG_BITDEPTH;

  /** Table size for sinExp (10-bit). */
  public static final int SINEXP_TABLESIZE = 1024;

  /** EngineMkI sine log table (1024 uint16 entries). */
  public static final int[] sinLogTable = new int[SINLOG_TABLESIZE];

  /** EngineMkI sine exp table (1024 uint16 entries). */
  public static final int[] sinExpTable = new int[SINEXP_TABLESIZE];

  /**
   * Gain threshold for EngineMkI (ENV_MAX - 100 = 16284). FmCore's kGainLevelThresh = 1120 is used
   * for FmCore rendering.
   */
  public static final int kLevelThresh = ENV_MAX - 100;

  /**
   * Gain threshold below which an operator is considered inaudible. Matches dexed kGainLevelThresh
   * = 1120.
   */
  public static final int kGainLevelThresh = 1120;

  /** Feedback bitdepth constant matching the firmware. */
  public static final int FEEDBACK_BITDEPTH = 8;

  // ── Sample-rate-dependent fields ──

  private static boolean initialized;
  private static double sampleRate;
  static int sr_multiplier; // Q24 sample rate multiplier for Env
  static int pitchEnvUnit; // unit_ for PitchEnv

  // ── Initialization ──

  /**
   * Initialize all lookup tables. Must be called once before using any Dx7Engine. Safe to call
   * multiple times with the same sample rate — reinitializes on rate change.
   *
   * @param sr sample rate in Hz (e.g., 44100)
   */
  public static synchronized void init(double sr) {
    if (initialized && sr == sampleRate) return;
    sampleRate = sr;
    sr_multiplier = (int) ((44100.0 / sr) * (1 << 24) + 0.5);
    pitchEnvUnit = (int) ((1 << 24) / (21.3 * sr) + 0.5);
    exp2Init();
    tanhInit();
    sinInit();
    freqLutInit(sr);
    engineMkIInit();
    initialized = true;
  }

  /** Returns true if tables have been initialized. */
  public static boolean isInitialized() {
    return initialized;
  }

  // ── Sin lookup ──

  /**
   * Sine lookup with delta interpolation (SIN_DELTA).
   *
   * @param phase Q32 phase
   * @return Q24 sine value (±~1<<24)
   */
  public static int sinLookup(int phase) {
    final int SHIFT = 24 - SIN_LG_N_SAMPLES;
    int lowbits = phase & ((1 << SHIFT) - 1);
    int phaseInt = (phase >> (SHIFT - 1)) & ((SIN_N_SAMPLES - 1) << 1);
    int dy = sintab[phaseInt];
    int y0 = sintab[phaseInt + 1];
    return y0 + (int) (((long) dy * (long) lowbits) >> SHIFT);
  }

  // ── Exp2 lookup ──

  /**
   * Exponential (2^x) lookup. Q24 in, Q24 out.
   *
   * @param x Q24 log-domain value
   * @return Q24 linear value
   */
  public static int exp2Lookup(int x) {
    final int SHIFT = 24 - EXP2_LG_N_SAMPLES;
    int lowbits = x & ((1 << SHIFT) - 1);
    int xInt = (x >> (SHIFT - 1)) & ((EXP2_N_SAMPLES - 1) << 1);
    int dy = exp2tab[xInt];
    int y0 = exp2tab[xInt + 1];
    int y = y0 + (int) (((long) dy * (long) lowbits) >> SHIFT);
    return y >> (6 - (x >> 24));
  }

  // ── Freqlut lookup ──

  /**
   * Frequency lookup: log-frequency to phase increment.
   *
   * @param logfreq Q24 log2(frequency)
   * @return Q32 phase increment per sample
   */
  public static int freqLutLookup(int logfreq) {
    final int SAMPLE_SHIFT = 24 - FREQ_LG_N_SAMPLES;
    final int MAX_LOGFREQ_INT = 20;
    int ix = (logfreq & 0xffffff) >> SAMPLE_SHIFT;
    int y0 = freq_lut[ix];
    int y1 = freq_lut[ix + 1];
    int lowbits = logfreq & ((1 << SAMPLE_SHIFT) - 1);
    int y = y0 + (int) (((long) (y1 - y0) * (long) lowbits) >> SAMPLE_SHIFT);
    int hibits = logfreq >> 24;
    return y >> (MAX_LOGFREQ_INT - hibits);
  }

  // ── Tanh lookup ──

  /**
   * Hyperbolic tangent lookup. Q24 in, Q24 out.
   *
   * @param x Q24 input
   * @return Q24 tanh(x)
   */
  public static int tanhLookup(int x) {
    int signum = x >> 31;
    x ^= signum;
    if (x >= (4 << 24)) {
      if (x >= (17 << 23)) {
        return signum ^ (1 << 24);
      }
      int sx = (int) (((long) -48408812 * (long) x) >> 24);
      return signum ^ ((1 << 24) - 2 * exp2Lookup(sx));
    } else {
      final int SHIFT = 26 - TANH_LG_N_SAMPLES;
      int lowbits = x & ((1 << SHIFT) - 1);
      int xInt = (x >> (SHIFT - 1)) & ((TANH_N_SAMPLES - 1) << 1);
      int dy = tanhtab[xInt];
      int y0 = tanhtab[xInt + 1];
      int y = y0 + (int) (((long) dy * (long) lowbits) >> SHIFT);
      return y ^ signum;
    }
  }

  // ── Helper: DX7 note → log-frequency ──

  /** Convert a MIDI note number to Q24 log-frequency. Matches firmware dxNoteToFreq(). */
  public static int dxNoteToFreq(int note) {
    final int base = 50857777; // (1<<24) * (log(440)/log(2) - 69/12)
    final int step = (1 << 24) / 12;
    return base + step * note;
  }

  // ── LFO phase-to-value ──

  /**
   * Convert LFO phase to a modulation value based on waveform. Matches firmware lfoPhaseToValue().
   */
  public static int lfoPhaseToValue(int phase, int waveform) {
    int wf = waveform;
    if (wf == 0) wf = 4; // triangle fallback
    switch (wf) {
      case 0:
        { // triangle
          int x = phase >> 7;
          x ^= -(phase >> 31);
          x &= (1 << 24) - 1;
          return x;
        }
      case 1: // sawtooth down
        return (~phase ^ (1 << 31)) >> 8;
      case 2: // sawtooth up
        return (phase ^ (1 << 31)) >> 8;
      case 3: // square
        return ((~phase) >> 7) & (1 << 24);
      case 4: // sine
        return (1 << 23) + (sinLookup(phase >> 8) >> 1);
      default:
        return 1 << 23;
    }
  }

  // ── Private table initializers ──

  private static void exp2Init() {
    double inc = Math.pow(2, 1.0 / EXP2_N_SAMPLES);
    double y = 1 << 30;
    for (int i = 0; i < EXP2_N_SAMPLES; i++) {
      exp2tab[(i << 1) + 1] = (int) Math.floor(y + 0.5);
      y *= inc;
    }
    for (int i = 0; i < EXP2_N_SAMPLES - 1; i++) {
      exp2tab[i << 1] = exp2tab[(i << 1) + 3] - exp2tab[(i << 1) + 1];
    }
    exp2tab[(EXP2_N_SAMPLES << 1) - 2] = (int) ((1L << 31) - exp2tab[(EXP2_N_SAMPLES << 1) - 1]);
  }

  private static double dtanh(double y) {
    return 1 - y * y;
  }

  private static void tanhInit() {
    double step = 4.0 / TANH_N_SAMPLES;
    double y = 0;
    for (int i = 0; i < TANH_N_SAMPLES; i++) {
      tanhtab[(i << 1) + 1] = (int) ((1 << 24) * y + 0.5);
      double k1 = dtanh(y);
      double k2 = dtanh(y + 0.5 * step * k1);
      double k3 = dtanh(y + 0.5 * step * k2);
      double k4 = dtanh(y + step * k3);
      double dy = (step / 6) * (k1 + k4 + 2 * (k2 + k3));
      y += dy;
    }
    for (int i = 0; i < TANH_N_SAMPLES - 1; i++) {
      tanhtab[i << 1] = tanhtab[(i << 1) + 3] - tanhtab[(i << 1) + 1];
    }
    int lasty = (int) ((1 << 24) * y + 0.5);
    tanhtab[(TANH_N_SAMPLES << 1) - 2] = lasty - tanhtab[(TANH_N_SAMPLES << 1) - 1];
  }

  private static void sinInit() {
    double dphase = 2 * Math.PI / SIN_N_SAMPLES;
    int c = (int) Math.floor(Math.cos(dphase) * (1 << 30) + 0.5);
    int s = (int) Math.floor(Math.sin(dphase) * (1 << 30) + 0.5);
    int u = 1 << 30;
    int v = 0;
    for (int i = 0; i < SIN_N_SAMPLES / 2; i++) {
      sintab[(i << 1) + 1] = (v + 32) >> 6;
      sintab[((i + SIN_N_SAMPLES / 2) << 1) + 1] = -((v + 32) >> 6);
      int t = (int) (((long) u * (long) s + (long) v * (long) c + (1 << 29)) >> 30);
      u = (int) (((long) u * (long) c - (long) v * (long) s + (1 << 29)) >> 30);
      v = t;
    }
    for (int i = 0; i < SIN_N_SAMPLES - 1; i++) {
      sintab[i << 1] = sintab[(i << 1) + 3] - sintab[(i << 1) + 1];
    }
    sintab[(SIN_N_SAMPLES << 1) - 2] = -sintab[(SIN_N_SAMPLES << 1) - 1];
  }

  private static void freqLutInit(double sr) {
    double y = (1L << (24 + 20)) / sr;
    double inc = Math.pow(2, 1.0 / FREQ_N_SAMPLES);
    for (int i = 0; i < FREQ_N_SAMPLES + 1; i++) {
      freq_lut[i] = (int) Math.floor(y + 0.5);
      y *= inc;
    }
  }

  /** Initialize EngineMkI sinLog and sinExp tables. Matches EngineMkI.cpp constructor. */
  private static void engineMkIInit() {
    for (int i = 0; i < SINLOG_TABLESIZE; i++) {
      double x1 = Math.sin(((0.5 + i) / SINLOG_TABLESIZE) * Math.PI / 2.0);
      sinLogTable[i] = (int) Math.round(-1024 * (Math.log(x1) / Math.log(2)));
      // clamps: firmware round() outputs uint16, ensure range 0-0xFFFF
      if (sinLogTable[i] < 0) sinLogTable[i] = 0;
      if (sinLogTable[i] > 0xFFFF) sinLogTable[i] = 0xFFFF;
    }
    for (int i = 0; i < SINEXP_TABLESIZE; i++) {
      double x1 = (Math.pow(2, (double) i / SINEXP_TABLESIZE) - 1) * 4096;
      sinExpTable[i] = (int) Math.round(x1);
      if (sinExpTable[i] < 0) sinExpTable[i] = 0;
      if (sinExpTable[i] > 0xFFFF) sinExpTable[i] = 0xFFFF;
    }
  }

  /**
   * Fixed-point division: div_n(x, inv_n) = (int32_t)(((int64_t)x * (int64_t)inv_n) >> 30) where
   * inv_n = (1 << 30) / n. For per-sample rendering (n=1): inv_n = 1<<30, result = x.
   */
  public static int div_n(int x, int inv_n) {
    return (int) (((long) x * (long) inv_n) >> 30);
  }

  /** EngineMkI sinLog lookup. Matches EngineMkI.cpp sinLog(). */
  public static int mkiSinLog(int phi) {
    final int SINLOG_TABLEFILTER = SINLOG_TABLESIZE - 1;
    int index = phi & SINLOG_TABLEFILTER;
    int quadrant = phi & (SINLOG_TABLESIZE * 3);
    if (quadrant == 0) {
      return sinLogTable[index];
    } else if (quadrant == SINLOG_TABLESIZE) {
      return sinLogTable[index ^ SINLOG_TABLEFILTER];
    } else if (quadrant == SINLOG_TABLESIZE * 2) {
      return sinLogTable[index] | NEGATIVE_BIT;
    } else {
      return sinLogTable[index ^ SINLOG_TABLEFILTER] | NEGATIVE_BIT;
    }
  }

  /**
   * EngineMkI sine computation. Matches EngineMkI.cpp mkiSin().
   *
   * @param phase Q32 phase
   * @param env uint16 envelope value (ENV_BITDEPTH=14 bit)
   * @return Q27 sine value (left-shifted by 13 from a 14-bit result)
   */
  public static int mkiSin(int phase, int env) {
    int expVal = mkiSinLog(phase >> (22 - SINLOG_BITDEPTH)) + env;
    boolean isSigned = (expVal & NEGATIVE_BIT) != 0;
    expVal &= ~NEGATIVE_BIT;

    final int SINEXP_FILTER = 0x3FF;
    int result = 4096 + sinExpTable[(expVal & SINEXP_FILTER) ^ SINEXP_FILTER];
    result >>= (expVal >> 10);

    if (isSigned) return (-result - 1) << 13;
    else return result << 13;
  }

  private Dx7EngineLookupTables() {}
}
