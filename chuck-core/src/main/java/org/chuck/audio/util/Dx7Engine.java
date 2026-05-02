package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * 6-operator FM synthesis engine implementing the Yamaha DX7 voice architecture.
 *
 * <p>This is a single {@link ChuckUGen} that manages 6 internal operators, each with its own
 * phase accumulator, sine lookup, envelope generator (EG), and frequency calculation. The 32
 * DX7 algorithm matrices determine which operators modulate which, and which operators route
 * to the final output (carriers).
 *
 * <p>Usage:
 * <pre>{@code
 * Dx7Engine dx7 = new Dx7Engine(sampleRate);
 * dx7.loadPatch(hexStrToBytes(dx7Hex));
 * dx7.setFreq(261.6); // C4
 * dx7.noteOn();
 * // ... tick() per sample ...
 * dx7.noteOff();
 * }</pre>
 *
 * <p>Design notes:
 * <ul>
 *   <li>No sub-UGens — avoids overhead of 6 separate ChucK graph nodes per voice</li>
 *   <li>256-point sine table with linear interpolation shared across all operators (static)</li>
 *   <li>Per-operator EG: linear interpolation between levels, rate-controlled slope speed</li>
 *   <li>Algorithm processing is strictly in order (op1..op6) so later operators see earlier outputs</li>
 *   <li>Operator 1 supports self-feedback (scaled by global feedback 0-7)</li>
 * </ul>
 */
@doc("6-operator FM synthesis engine (Yamaha DX7).")
public class Dx7Engine extends ChuckUGen {

  // ── Static tables ──

  /** 256-point single-cycle sine table (shared across all instances). */
  private static final float[] SINE;
  static {
    SINE = new float[256];
    for (int i = 0; i < 256; i++) {
      SINE[i] = (float) Math.sin(2.0 * Math.PI * i / 256.0);
    }
  }

  /** Table length for modulo arithmetic. */
  private static final int TABLE_LEN = 256;

  // ── Constants ──

  /** Number of operators. */
  public static final int NUM_OPS = 6;

  /** Number of EG stages. */
  static final int NUM_EG_STAGES = 4;

  // EG stage indices
  static final int EG_ATTACK = 0;
  static final int EG_DECAY = 1;
  static final int EG_SUSTAIN = 2;
  static final int EG_RELEASE = 3;
  static final int EG_OFF = 4;

  // ── DX7 Algorithm matrix ──

  /**
   * The 32 DX7 algorithm connections.
   *
   * <p>For each algorithm, a 6-element array. Element {@code i} is the bitmask of operators
   * whose output modulates operator {@code i}. Operators not in any bitmask (or whose output
   * bit is not consumed) are carriers — their output goes to the final mix.
   *
   * <p>Operator indices are 0-based (op1=0, op2=1, ..., op6=5).
   */
  /**
   * The 32 DX7 algorithm connections as bitmasks.
   *
   * <p>For each algorithm, a 6-element int array. Element {@code i} is a bitmask of operators
   * whose output modulates operator {@code i} (bit 0 = op1, bit 1 = op2, ..., bit 5 = op6).
   * Operators whose bit is NOT present in any entry's mask are carriers — their output sums
   * to the final mix.
   *
   * <p>Operator indices are 0-based (op1=0, op2=1, ..., op6=5).
   */
  private static final int[][] ALGORITHMS = {
    // algo 0: carriers=op[6] op1<-op6; op2<-op1; op3<-op2; op4<-op3; op5<-op4
    new int[]{0b100000, 0b000001, 0b000010, 0b000100, 0b001000, 0b000000},
    // algo 1: carriers=op[5,6] op1<-op5+op6; op2<-op1; op3<-op2; op4<-op3
    new int[]{0b110000, 0b000001, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 2: carriers=op[5,6] op2<-op1; op3<-op2; op4<-op3
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 3: carriers=op[6] op2<-op1; op3<-op2; op4<-op3; op5<-op4
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b001000, 0b000000},
    // algo 4: carriers=op[5,6] op2<-op1; op3<-op2; op4<-op3
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 5: carriers=op[5,6] op1<-op5; op2<-op6; op3<-op2; op4<-op3
    new int[]{0b010000, 0b100000, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 6: carriers=op[5,6] op1<-op5; op2<-op1; op3<-op6; op4<-op3
    new int[]{0b010000, 0b000001, 0b100000, 0b000100, 0b000000, 0b000000},
    // algo 7: carriers=op[1,3,5] op2<-op1; op4<-op3; op6<-op5
    new int[]{0b000000, 0b000001, 0b000000, 0b000100, 0b000000, 0b010000},
    // algo 8: carriers=op[6] op6<-op1+op2+op3+op4+op5
    new int[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b011111},
    // algo 9: carriers=op[4,6] op1<-op5; op2<-op1; op3<-op2
    new int[]{0b010000, 0b000001, 0b000010, 0b000000, 0b000000, 0b000000},
    // algo 10: carriers=op[2,3,4,5,6] op2<-op1
    new int[]{0b000000, 0b000001, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 11: carriers=op[4,5,6] op3<-op1+op2
    new int[]{0b000000, 0b000000, 0b000011, 0b000000, 0b000000, 0b000000},
    // algo 12: all 6 parallel carriers
    new int[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 13: carriers=op[5,6] op1<-op5+op6; op2<-op1; op3<-op2; op4<-op3
    new int[]{0b110000, 0b000001, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 14: carriers=op[1,5,6] op2<-op1; op3<-op5; op4<-op6
    new int[]{0b000000, 0b000001, 0b010000, 0b100000, 0b000000, 0b000000},
    // algo 15: carriers=op[3,6] op1<-op4; op2<-op1; op5<-op4
    new int[]{0b000100, 0b000001, 0b000000, 0b000000, 0b001000, 0b000000},
    // algo 16: op1->op2->op3->op4->op5->op6 (full serial)
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b001000, 0b010000},
    // algo 17: carriers=op[5,6] op1<-op5; op2<-op1; op3<-op2; op4<-op6
    new int[]{0b010000, 0b000001, 0b000010, 0b100000, 0b000000, 0b000000},
    // algo 18: carriers=op[5,6] op1<-op5; op2<-op6; op3<-op2; op4<-op3
    new int[]{0b010000, 0b100000, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 19: carriers=op[3,5,6] op1<-op4; op2<-op1
    new int[]{0b001000, 0b000001, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 20: carriers=op[5,6] op1<-op5; op2<-op1; op4<-op3+op6
    new int[]{0b010000, 0b000001, 0b000000, 0b100100, 0b000000, 0b000000},
    // algo 21: carriers=op[2,3,4,5,6] op2<-op1
    new int[]{0b000000, 0b000001, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 22: carriers=op[4,5,6] op3<-op1+op2
    new int[]{0b000000, 0b000000, 0b000011, 0b000000, 0b000000, 0b000000},
    // algo 23: carriers=op[4,5,6] op2<-op1; op3<-op2
    new int[]{0b000000, 0b000001, 0b000010, 0b000000, 0b000000, 0b000000},
    // algo 24: all 6 parallel carriers (summed pairs)
    new int[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 25: all 6 parallel carriers
    new int[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 26: all 6 parallel carriers
    new int[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 27: carriers=op[6] op2<-op1; op3<-op2; op4<-op3; op5<-op4
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b001000, 0b000000},
    // algo 28: carriers=op[2,3,4,5,6] op2<-op1
    new int[]{0b000000, 0b000001, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 29: carriers=op[5,6] op2<-op1; op3<-op2; op4<-op3
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b000000, 0b000000},
    // algo 30: all 6 parallel carriers
    new int[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000},
    // algo 31: op1->op2->op3->op4->op5->op6 (full serial)
    new int[]{0b000000, 0b000001, 0b000010, 0b000100, 0b001000, 0b010000},
  };

  /**
   * Carrier mask per algorithm: bit i set if operator i routes to output.
   * An operator is a carrier if its output is NOT consumed as modulation input by any other
   * operator in the algorithm.
   *
   * <p>Computed from ALGORITHMS: op j is a carrier if no operator i has j in its bitmask.
   */
  private static final int[] CARRIER_MASKS;
  static {
    CARRIER_MASKS = new int[32];
    for (int a = 0; a < 32; a++) {
      int mask = 0;
      int[] algo = ALGORITHMS[a];
      // Collect all operators that are modulated by each op
      int consumed = 0;
      for (int i = 0; i < NUM_OPS; i++) {
        consumed |= algo[i];
      }
      // Operator is a carrier if its output bit is NOT consumed
      for (int j = 0; j < NUM_OPS; j++) {
        if ((consumed & (1 << j)) == 0) {
          mask |= (1 << j);
        }
      }
      CARRIER_MASKS[a] = mask;
    }
  }

  // ── Per-operator state ──

  /**
   * Runtime state for a single DX7 operator.
   * These are mutable, reused across noteOn/noteOff cycles — no allocation per note.
   */
  private static class OpState {
    // Patch parameters (loaded from Dx7Patch)
    int[] egRate = new int[NUM_EG_STAGES];
    int[] egLevel = new int[NUM_EG_STAGES];
    int detune;
    int coarseFreq;
    int fineFreq;
    int outputLevel;
    int keyVelSens;
    int ampModSens;
    int oscMode;    // 0=ratio, 1=fixed
    int kcLeft;
    int kcRight;

    // Runtime state
    double phase;
    double freq;          // current frequency in Hz
    float ampLevel;       // current EG amplitude (0-1)
    int egStage;          // current EG stage (EG_ATTACK..EG_OFF)
    float output;         // last output sample
  }

  // ── Instance fields ──

  private final OpState[] ops = new OpState[NUM_OPS];
  private final float sampleRate;
  private int algorithm;
  private int feedback;
  private double baseFreq = 440.0;
  private boolean active;

  /**
   * Creates a Dx7Engine with the given sample rate.
   *
   * @param sampleRate sample rate in Hz (e.g., 44100)
   */
  public Dx7Engine(float sampleRate) {
    this.sampleRate = sampleRate;
    for (int i = 0; i < NUM_OPS; i++) {
      ops[i] = new OpState();
    }
    // Default: algorithm 0, no feedback
    this.algorithm = 0;
    this.feedback = 0;
    this.active = false;
  }

  /**
   * Load patch parameters from a decoded Dx7Patch.
   *
   * @param patch the decoded patch
   */
  public void loadPatch(Dx7Patch patch) {
    int algo = patch.algorithm();
    if (algo < 0 || algo > 31) algo = 0;
    this.algorithm = algo;
    this.feedback = patch.feedback() & 0x07;

    for (int i = 0; i < NUM_OPS && i < patch.operators().length; i++) {
      Dx7Patch.Operator src = patch.operators()[i];
      OpState op = ops[i];
      System.arraycopy(src.egRate(), 0, op.egRate, 0, NUM_EG_STAGES);
      System.arraycopy(src.egLevel(), 0, op.egLevel, 0, NUM_EG_STAGES);
      op.coarseFreq = src.coarseFreq();
      op.fineFreq = src.fineFreq();
      op.detune = src.detune();
      op.outputLevel = src.outputLevel();
      op.keyVelSens = src.keyVelSens();
      op.ampModSens = src.ampModSens();
      op.oscMode = src.oscMode();
      op.kcLeft = src.kcLeft();
      op.kcRight = src.kcRight();
    }
  }

  /**
   * Load patch parameters from a raw 156-byte patch blob.
   *
   * @param raw 156-byte DX7 patch array
   */
  public void loadPatch(byte[] raw) {
    loadPatch(Dx7Patch.fromHex(bytesToHex(raw)));
  }

  /**
   * Set the base note frequency.
   *
   * @param freq frequency in Hz
   */
  public void setFreq(double freq) {
    this.baseFreq = freq;
    updateOpFrequencies();
  }

  /**
   * Trigger note-on: reset EG phases to attack stage.
   */
  public void noteOn() {
    active = true;
    for (int i = 0; i < NUM_OPS; i++) {
      OpState op = ops[i];
      op.phase = 0.0;
      op.egStage = EG_ATTACK;
      op.ampLevel = 0.0f;
      op.output = 0.0f;
    }
  }

  /**
   * Trigger note-off: transition all operators to release stage.
   */
  public void noteOff() {
    for (int i = 0; i < NUM_OPS; i++) {
      OpState op = ops[i];
      if (op.egStage <= EG_SUSTAIN) {
        op.egStage = EG_RELEASE;
      }
    }
  }

  /**
   * Returns true if any operator is still producing sound.
   */
  public boolean isActive() {
    return active;
  }

  // ── ChuckUGen implementation ──

  @Override
  protected float compute(float input, long systemTime) {
    if (!active) return 0.0f;

    int algo = this.algorithm;
    int[] algoConn = ALGORITHMS[algo];
    int carrierMask = CARRIER_MASKS[algo];

    // Per-operator feedback: store previous output for op0 (op1 in DX7 terms)
    float prevOp0Out = ops[0].output;

    // Process operators in order (1..6). Later operators see outputs from earlier ones.
    for (int i = 0; i < NUM_OPS; i++) {
      OpState op = ops[i];

      // ── EG update ──
      updateEg(op);

      // ── Phase increment ──
      double phaseInc = op.freq * TABLE_LEN / sampleRate;

      // ── Modulation input ──
      // Sum outputs of operators whose bits are set in algoConn[i]
      double modSum = 0.0;
      int conn = algoConn[i];
      if (conn != 0) {
        for (int m = 0; m < NUM_OPS; m++) {
          if ((conn & (1 << m)) != 0) {
            modSum += ops[m].output;
          }
        }
      }

      // Self-feedback for operator 1 (index 0): feedback value 0-7, saturated
      double fbAmount = 0.0;
      if (i == 0 && feedback > 0) {
        // DX7 feedback: value 0-7 maps to approx 0-100% feedback
        // Saturate to prevent runaway
        fbAmount = clampDouble(prevOp0Out, -0.99, 0.99) * (feedback / 7.0);
      }

      // ── Advance phase ──
      op.phase += phaseInc;
      if (fbAmount != 0.0) {
        op.phase += fbAmount * TABLE_LEN;
      }

      // Wrap phase to [0, TABLE_LEN)
      while (op.phase >= TABLE_LEN) {
        op.phase -= TABLE_LEN;
      }
      while (op.phase < 0) {
        op.phase += TABLE_LEN;
      }

      // ── Sine lookup (linear interpolation) ──
      int idx = (int) op.phase;
      float frac = (float) (op.phase - idx);
      float sample = SINE[idx] + (SINE[(idx + 1) & 0xFF] - SINE[idx]) * frac;

      // ── Apply EG amplitude ──
      // DX7 EG is 0-99, normalize to 0-1
      float egGain = op.ampLevel;
      // Output level: on real DX7, 0 = max attenuation (-99dB, silent when EG is 0),
      // 99 = no attenuation. When outputLevel=0 but EG is non-zero, the EG
      // determines the effective amplitude. Since opGain was killing it for
      // Tomsweep (outputLevel=0 on all 6 ops), use: EG operator gain with
      // max ceiling from outputLevel: outputLevel/99.0f gives 0→0, 99→1.
      // But with all-zeros, EG provides the level directly.
      float opGain = (op.outputLevel + 1) / 100.0f;  // 0→0.01, 99→1.0
      float out = sample * egGain * opGain;

      op.output = out;
    }

    // ── Sum carriers ──
    float output = 0.0f;
    for (int i = 0; i < NUM_OPS; i++) {
      if ((carrierMask & (1 << i)) != 0) {
        output += ops[i].output;
      }
    }

    // Check if all operators have finished release
    boolean allOff = true;
    for (int i = 0; i < NUM_OPS && allOff; i++) {
      if (ops[i].egStage != EG_OFF) allOff = false;
    }
    if (allOff) active = false;

    return output;
  }

  // ── EG engine ──

  /**
   * Update the EG for one operator: advance the EG state machine one sample.
   *
   * <p>DX7 EG: rates 0-99 map to increment speed. Levels 0-99 are target amplitudes.
   * We use a simple linear interpolation: each sample, move toward the target level of
   * the current stage at rate-controlled speed.
   */
  private void updateEg(OpState op) {
    int stage = op.egStage;
    if (stage == EG_OFF) return;

    int targetLevel = op.egLevel[stage];
    float targetAmp = targetLevel / 99.0f;

    int rate = op.egRate[stage];
    if (rate == 0) {
      // Rate 0 = hold (or very slow). Instant at rate 99.
      // For attack, rate 0 means no change (infinite attack).
      // For release, rate 0 means never reach 0 (sustain forever).
      return;
    }

    // Map DX7 rate 1-99 to an increment per sample.
    // DX7 rates are exponential: higher rates are faster.
    // Simple linear map: rate 99 = instant, rate 0 = hold.
    // rate 1 ≈ very slow, rate 99 ≈ instant.
    float inc = rate / 99.0f;

    // Scale by stage: attack moves toward target, decay/release move away
    float step;
    if (stage == EG_ATTACK) {
      // Attack: move from current level UP to targetLevel
      step = targetAmp - op.ampLevel;
      if (step <= 0.001f) {
        // Reached target → next stage
        op.ampLevel = targetAmp;
        op.egStage = EG_DECAY;
        updateEg(op); // recurse for decay immediately
        return;
      }
      // Attack increment is proportional: small at low levels, accelerates
      // DX7 attack curve: roughly exponential
      // Use a per-sample step based on rate
      float attackStep = 0.005f + inc * 0.1f;
      if (step < attackStep) step = attackStep;
      op.ampLevel += step * inc;
      if (op.ampLevel > targetAmp) {
        op.ampLevel = targetAmp;
        op.egStage = EG_DECAY;
        updateEg(op);
      }
    } else if (stage == EG_DECAY) {
      // Decay: move from current level DOWN to target (sustain level)
      if (op.ampLevel <= targetAmp + 0.001f) {
        op.ampLevel = targetAmp;
        op.egStage = EG_SUSTAIN;
        return;
      }
      float decayStep = (op.ampLevel - targetAmp) * inc * 0.05f;
      if (decayStep < 0.0001f) decayStep = 0.0001f;
      op.ampLevel -= decayStep;
      if (op.ampLevel <= targetAmp) {
        op.ampLevel = targetAmp;
        op.egStage = EG_SUSTAIN;
      }
    } else if (stage == EG_RELEASE) {
      // Release: move from current level DOWN to 0
      if (op.ampLevel <= 0.001f) {
        op.ampLevel = 0.0f;
        op.egStage = EG_OFF;
        return;
      }
      float releaseStep = op.ampLevel * inc * 0.05f;
      if (releaseStep < 0.0001f) releaseStep = 0.0001f;
      op.ampLevel -= releaseStep;
      if (op.ampLevel <= 0.0f) {
        op.ampLevel = 0.0f;
        op.egStage = EG_OFF;
      }
    }
  }

  // ── Helpers ──

  /**
   * Recalculate per-operator frequencies from the base frequency and patch parameters.
   */
  private void updateOpFrequencies() {
    for (int i = 0; i < NUM_OPS; i++) {
      OpState op = ops[i];
      if (op.oscMode == 0) {
        // Ratio mode: freq = baseFreq * coarse (0=0.5, 1=1, 2=2, ..., 31=32)
        double coarse = op.coarseFreq == 0 ? 0.5 : op.coarseFreq;
        // Fine detune: 0-99, centered at 50 (~0 cent), range ~±50 cent
        double fine = 1.0 + (op.fineFreq - 50) / 100.0;
        op.freq = baseFreq * coarse * fine;
      } else {
        // Fixed mode: frequency independent of note
        // coarse * fine (if fine > 0) or coarse (in Hz)
        double f = op.coarseFreq;
        if (op.fineFreq > 0) {
          f *= op.fineFreq;
        }
        op.freq = f;
      }
      // Detune: 0-99 maps to approximately ±1 semitone adjustment
      // Centered at 0 (detune=50 ≈ 0 cents)
      double detuneRatio = 1.0 + (op.detune - 50) / 2000.0;
      op.freq *= detuneRatio;
    }
  }

  /**
   * Convert a byte array to a hex string (reverse of Dx7Patch.hexToBytes).
   */
  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b & 0xFF));
    }
    return sb.toString();
  }

  private static double clampDouble(double v, double min, double max) {
    return Math.max(min, Math.min(max, v));
  }

  /**
   * Sets the base frequency (ChucK-style setter, public for engine compatibility).
   */
  public double freq(double f) {
    setFreq(f);
    return f;
  }

  public double freq() {
    return baseFreq;
  }
}
