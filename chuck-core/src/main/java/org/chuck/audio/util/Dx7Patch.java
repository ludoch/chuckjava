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
 *   <li>6 operators × 21 bytes = 126 bytes
 *   <li>19 bytes global parameters (pitch EG 5 + algorithm 1 + feedback 1 + osc sync 1 + LFO 6 + pitch mod 1 + amp mod 1 + transpose 1)
 *   <li>NOT in standard DX7 SysEx: The Deluge firmware stores the 10-byte patch name at offset 145
 *       <em>within</em> the 156-byte blob (not as a separate field), followed by 1 byte checksum.
 *   <li>1 byte checksum at offset 155
 * </ul>
 *
 * <p>The transpose byte appears at offset 144, which means the global block is 19 bytes
 * (126-144 inclusive) and the name starts at 145.
 */
public record Dx7Patch(Operator[] operators, int algorithm, int feedback, int transpose, String name, byte[] raw) {

  /** Number of operators in a DX7 voice. */
  public static final int NUM_OPERATORS = 6;

  /** Number of EG stages (R1-R4, L1-L4). */
  public static final int NUM_EG_STAGES = 4;

  /** Offset where operator data starts. */
  private static final int OP_BYTE_OFFSET = 0;

  /** Bytes per operator. */
  private static final int OP_BYTES = 21;

  /** Offset of algorithm byte (0-31). */
  private static final int OFF_ALGORITHM = 134;

  /** Offset of feedback byte (0-7). */
  private static final int OFF_FEEDBACK = 135;

  /** Offset of transpose byte (semitones). */
  private static final int OFF_TRANSPOSE = 144;

  /** Offset of patch name (10 bytes). */
  private static final int OFF_NAME = 145;

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
      ops[i] = decodeOperator(raw, OP_BYTE_OFFSET + i * OP_BYTES);
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

  // ── Operator decoding ──

  private static Operator decodeOperator(byte[] raw, int off) {
    int[] egRate = new int[NUM_EG_STAGES];
    int[] egLevel = new int[NUM_EG_STAGES];
    for (int i = 0; i < NUM_EG_STAGES; i++) {
      egRate[i] = raw[off + i] & 0xFF;
      egLevel[i] = raw[off + i + 4] & 0xFF;
    }

    int detune = raw[off + 8] & 0xFF;         // 0-99 (DX7: 0=0, 1=±1, ... 7=±7, rest unused)
    int coarseFreq = raw[off + 9] & 0xFF;      // 0-99 → frequency ratio (0=0.5, 1=1, ..., 31=32)
    int fineFreq = raw[off + 10] & 0xFF;       // 0-99 → fine Hz offset (0=0, 1=1, ..., 99=99)
    int outputLevel = raw[off + 11] & 0xFF;    // 0-99
    int keyVelSens = raw[off + 12] & 0xFF;     // 0-99 (velocity sensitivity)
    int ampModSens = raw[off + 13] & 0xFF;     // 0-99 (amplitude modulation sensitivity)
    int oscMode = raw[off + 14] & 0xFF;        // 0=ratio, 1=fixed frequency
    int kcLeft = raw[off + 15] & 0xFF;         // key scaling left (breakpoint curve)
    int kcRight = raw[off + 16] & 0xFF;        // key scaling right

    return new Operator(egRate, egLevel, detune, coarseFreq, fineFreq,
        outputLevel, keyVelSens, ampModSens, oscMode, kcLeft, kcRight);
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
   * frequency controls, and output level. Operators can be configured as ratio-mode
   * (frequency = note × coarse) or fixed-mode (frequency = coarse × fine, independent of note).
   */
  public record Operator(
      int[] egRate,      // [4] EG rates (0-99)
      int[] egLevel,     // [4] EG levels (0-99)
      int detune,        // 0-99 (0=no detune)
      int coarseFreq,    // 0-99 (ratio: 0=0.5, 1=1, ≥1=n; fixed: n = freq in Hz × fine)
      int fineFreq,      // 0-99 (fine tuning, 0-99 Hz in fixed mode)
      int outputLevel,   // 0-99
      int keyVelSens,    // 0-99 (keyboard velocity sensitivity)
      int ampModSens,    // 0-99 (amplitude modulation sensitivity from LFO)
      int oscMode,       // 0=ratio (freq tracks note), 1=fixed (freq independent of note)
      int kcLeft,        // key scaling left breakpoint
      int kcRight        // key scaling right breakpoint
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
     * When oscMode=1, frequency = coarseFreq * fineFreq (if fineFreq>0) or coarseFreq.
     */
    public double fixedFrequency() {
      if (fineFreq > 0) return coarseFreq * (double) fineFreq;
      return coarseFreq;
    }
  }
}
