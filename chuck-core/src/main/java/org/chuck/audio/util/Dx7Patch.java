package org.chuck.audio.util;

/**
 * Decoded representation of a Yamaha DX7 voice patch (156 bytes).
 *
 * <p>The Deluge firmware stores DX7 patches as hex-encoded 156-byte strings in the
 * {@code dx7patch} attribute of {@code <osc1 type="dx7">}. This class parses the hex string
 * into structured operator and global parameter fields matching the real DX7 SysEx format.
 *
 * <p>Layout (156 bytes):
 * <ul>
 *   <li>6 operators × 21 bytes = 126 bytes</li>
 *   <li>19 bytes global parameters (pitch EG 5 + algorithm 1 + feedback 1 + osc sync 1 + LFO 6 + pitch mod 1 + amp mod 1 + transpose 1)</li>
 *   <li>10 bytes patch name at offset 145</li>
 *   <li>1 byte opSwitch/checksum at offset 155</li>
 * </ul>
 *
 * <p>Per-operator layout (21 bytes):
 * <pre>
 *   [0-3]  egRate      (0-99)  EG rates R1-R4
 *   [4-7]  egLevel     (0-99)  EG levels L1-L4
 *   [8]    breakPt     (0-127) Key scaling break point
 *   [9]    leftDepth   (0-99)  Key scaling left depth
 *   [10]   rightDepth  (0-99)  Key scaling right depth
 *   [11]   leftCurve   (0-3)   Key scaling left curve
 *   [12]   rightCurve  (0-3)   Key scaling right curve
 *   [13]   rateScale   (0-7)   Rate scaling
 *   [14]   ampModSens  (0-3)   Amp mod sensitivity
 *   [15]   velSens     (0-7)   Velocity sensitivity
 *   [16]   outLevel    (0-99)  Operator output level
 *   [17]   mode        (0-1)   Fixed freq(1) / ratio(0)
 *   [18]   coarse      (0-31)  Coarse frequency
 *   [19]   fine        (0-99)  Fine tuning
 *   [20]   detune      (0-14)  Detune (7=center)
 * </pre>
 */
public record Dx7Patch(Operator[] operators, int algorithm, int feedback, int transpose, String name, byte[] raw) {

  /** Number of operators in a DX7 voice. */
  public static final int NUM_OPERATORS = 6;

  /** Number of EG stages (R1-R4, L1-L4). */
  public static final int NUM_EG_STAGES = 4;

  /** Bytes per operator. */
  public static final int OP_BYTES = 21;

  /** Offset of algorithm byte (0-31). */
  public static final int OFF_ALGORITHM = 134;

  /** Offset of feedback byte (0-7). */
  public static final int OFF_FEEDBACK = 135;

  /** Offset of LFO speed (0-99). */
  public static final int OFF_LFO_SPEED = 137;

  /** Offset of LFO delay (0-99). */
  public static final int OFF_LFO_DELAY = 138;

  /** Offset of LFO pitch mod depth (0-99). */
  public static final int OFF_PMOD_DEPTH = 139;

  /** Offset of LFO amp mod depth (0-99). */
  public static final int OFF_AMOD_DEPTH = 140;

  /** Offset of LFO sync flag. */
  public static final int OFF_LFO_SYNC = 141;

  /** Offset of LFO waveform. */
  public static final int OFF_LFO_WAVEFORM = 142;

  /** Offset of pitch mod sensitivity (0-7). */
  public static final int OFF_PMOD_SENS = 143;

  /** Offset of transpose byte (semitones). */
  public static final int OFF_TRANSPOSE = 144;

  /** Offset of patch name (10 bytes). */
  public static final int OFF_NAME = 145;

  /** Offset of opSwitch bitmask (byte 155). Bit 0 = op1 on, bit 5 = op6 on. */
  public static final int OFF_OP_SWITCH = 155;

  /** Total patch size in bytes. */
  public static final int PATCH_SIZE = 156;

  /**
   * Parse a DX7 patch from its hex-encoded string representation.
   *
   * @param hex the 312-character hex string (156 bytes)
   * @return decoded patch
   * @throws IllegalArgumentException if the hex string is invalid or wrong length
   */
  public static Dx7Patch fromHex(String hex) {
    if (hex == null || hex.length() != PATCH_SIZE * 2) {
      throw new IllegalArgumentException(
          "DX7 patch hex must be " + (PATCH_SIZE * 2) + " chars, got "
              + (hex == null ? "null" : hex.length()));
    }
    byte[] raw = hexToBytes(hex);

    Operator[] ops = new Operator[NUM_OPERATORS];
    for (int i = 0; i < NUM_OPERATORS; i++) {
      ops[i] = decodeOperator(raw, i * OP_BYTES);
    }

    int algorithm = raw[OFF_ALGORITHM] & 0xFF;
    int feedback = raw[OFF_FEEDBACK] & 0xFF;
    int transpose = raw[OFF_TRANSPOSE] & 0xFF;

    String name = decodeName(raw, OFF_NAME);

    return new Dx7Patch(ops, algorithm, feedback, transpose, name, raw);
  }

  /** Convert a hex string to a byte array. */
  public static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
          + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }

  /**
   * Returns the opSwitch bitmask (byte 155). Bit 0 = operator 1 active, bit 5 = operator 6 active.
   */
  public int opSwitch() {
    return raw[OFF_OP_SWITCH] & 0xFF;
  }

  /**
   * Returns true if the specified operator is active.
   *
   * @param op operator index (0-5)
   */
  public boolean isOpActive(int op) {
    return ((raw[OFF_OP_SWITCH] >> op) & 1) != 0;
  }

  // ── Operator decoding ──

  private static Operator decodeOperator(byte[] raw, int off) {
    int[] egRate = new int[NUM_EG_STAGES];
    int[] egLevel = new int[NUM_EG_STAGES];
    for (int i = 0; i < NUM_EG_STAGES; i++) {
      egRate[i] = raw[off + i] & 0xFF;
      egLevel[i] = raw[off + i + 4] & 0xFF;
    }

    // Corrected field mapping matching the firmware layout:
    // Offsets match the DX7 SysEx format: key scaling at 8-12, rateScale at 13,
    // ampModSens at 14, velSens at 15, outLevel at 16, mode at 17, coarse at 18,
    // fine at 19, detune at 20.
    int breakPt = raw[off + 8] & 0xFF;        // 0-127
    int leftDepth = raw[off + 9] & 0xFF;       // 0-99
    int rightDepth = raw[off + 10] & 0xFF;     // 0-99
    int leftCurve = raw[off + 11] & 0xFF;      // 0-3
    int rightCurve = raw[off + 12] & 0xFF;     // 0-3
    int rateScale = raw[off + 13] & 0xFF;      // 0-7
    int ampModSens = raw[off + 14] & 0xFF;     // 0-3
    int velSens = raw[off + 15] & 0xFF;        // 0-7
    int outLevel = raw[off + 16] & 0xFF;       // 0-99
    int mode = raw[off + 17] & 0xFF;           // 0=ratio, 1=fixed
    int coarse = raw[off + 18] & 0xFF;         // 0-31
    int fine = raw[off + 19] & 0xFF;           // 0-99
    int detune = raw[off + 20] & 0xFF;         // 0-14

    return new Operator(egRate, egLevel, breakPt, leftDepth, rightDepth,
        leftCurve, rightCurve, rateScale, ampModSens, velSens,
        outLevel, mode, coarse, fine, detune);
  }

  private static String decodeName(byte[] raw, int off) {
    StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      int b = raw[off + i] & 0xFF;
      if (b == 0) break;
      sb.append((char) (b >= 32 && b < 127 ? b : ' '));
    }
    return sb.toString().trim();
  }

  // ── Value objects ──

  /**
   * A single DX7 operator (voice element).
   *
   * <p>Each operator is a sine-wave oscillator with its own envelope generator (EG),
   * frequency controls, and output level. The field ordering matches the DX7 SysEx
   * layout (21 bytes per operator).
   */
  public record Operator(
      int[] egRate,       // [4] EG rates (0-99)
      int[] egLevel,      // [4] EG levels (0-99)
      int breakPt,        // 0-127 Key scaling break point
      int leftDepth,      // 0-99 Key scaling left depth
      int rightDepth,     // 0-99 Key scaling right depth
      int leftCurve,      // 0-3 Key scaling left curve
      int rightCurve,     // 0-3 Key scaling right curve
      int rateScale,      // 0-7 Rate scaling
      int ampModSens,     // 0-3 Amp mod sensitivity
      int velSens,        // 0-7 Velocity sensitivity
      int outputLevel,    // 0-99
      int mode,           // 0=ratio, 1=fixed
      int coarseFreq,     // 0-31 (coarse frequency)
      int fineFreq,       // 0-99 (fine tuning)
      int detune          // 0-14 (7=center)
  ) {
    /**
     * Returns the effective frequency ratio for ratio-mode operators.
     * DX7 convention: 0=0.5, 1=1, 2=2, ..., 31=32
     */
    public double frequencyRatio() {
      if (coarseFreq == 0) return 0.5;
      return coarseFreq;
    }

    /**
     * Returns the fixed frequency in Hz (for fixed-mode operators).
     * When mode=1, frequency = coarseFreq * fineFreq (if fineFreq>0) or coarseFreq.
     */
    public double fixedFrequency() {
      if (fineFreq > 0) return coarseFreq * (double) fineFreq;
      return coarseFreq;
    }
  }
}
