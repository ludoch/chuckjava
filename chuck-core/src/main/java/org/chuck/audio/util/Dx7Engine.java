package org.chuck.audio.util;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * 6-operator FM synthesis engine implementing the Yamaha DX7 voice architecture, ported from the
 * Deluge firmware's dexed/msfa implementation.
 *
 * <p>This is a single {@link ChuckUGen} that manages 6 internal operators, each with its own phase
 * accumulator, sine lookup, envelope generator (EG), and frequency calculation. The 32 DX7
 * algorithm matrices determine which operators modulate which, and which operators route to the
 * final output (carriers).
 *
 * <p>Key differences from the firmware's block-based renderer:
 *
 * <ul>
 *   <li>Per-sample {@link #compute(float, long)} instead of block rendering (n samples)
 *   <li>No gain interpolation across blocks — gain is computed per sample
 *   <li>Log-domain envelope with rate_scaling, proportional-attractive rising curve
 *   <li>Full pitch EG, LFO, key scaling, velocity scaling, opSwitch support
 *   <li>dB-additive output model via Exp2::lookup for log→linear conversion
 * </ul>
 *
 * <p>Math conventions (matching the firmware):
 *
 * <ul>
 *   <li>phase: Q32 (wraps naturally on 32-bit overflow)
 *   <li>level_: Q24 log-domain (2^24 = 6dB doubling)
 *   <li>Sin output: Q24 (±~1&lt;&lt;24)
 *   <li>gain: Q24 linear
 *   <li>logfreq: Q24 log2(frequency)
 *   <li>lfo_value: Q24, lfo_phase: Q32
 * </ul>
 */
@doc("6-operator FM synthesis engine (Dx7, dexed/msfa-based).")
public class Dx7Engine extends ChuckUGen {

  // ── Algorithm flag field extraction helpers ──
  private static int outbus(int flags) {
    return flags & 3;
  }

  private static int inbus(int flags) {
    return (flags >> 4) & 3;
  }

  private static boolean fbIn(int flags) {
    return (flags & Dx7EngineLookupTables.FB_IN) != 0;
  }

  private static boolean fbOut(int flags) {
    return (flags & Dx7EngineLookupTables.FB_OUT) != 0;
  }

  private static boolean outBusAdd(int flags) {
    return (flags & Dx7EngineLookupTables.OUT_BUS_ADD) != 0;
  }

  // ── Env: per-operator log-domain envelope ──

  /**
   * Log-domain envelope generator ported from dexed env.h/env.cpp.
   *
   * <p>Fields match the firmware Env class exactly: level_ (Q24 log-domain), targetlevel_, ix_
   * (segment index 0-4), inc_ (increment per sample), staticcount_ (ACCURATE_ENVELOPE), outlevel_
   * (microstep units), rate_scaling_, down_, rising_.
   */
  static class Env {
    int level_; // Q24 log-domain
    int targetlevel_;
    int ix_;
    int inc_;
    int staticcount_;
    int outlevel_;
    int rate_scaling_;
    boolean down_;
    boolean rising_;

    void init(byte[] patch, int opOff, int ol, int rateScaling) {
      outlevel_ = ol;
      rate_scaling_ = rateScaling;
      level_ = 0;
      down_ = true;
      advance(patch, opOff, 0, 0);
    }

    int getsample(byte[] patch, int opOff, int n, int extraRate) {
      if (staticcount_ > 0) {
        staticcount_ -= n;
        if (staticcount_ <= 0) {
          staticcount_ = 0;
          advance(patch, opOff, ix_ + 1, extraRate);
        }
      }

      if (ix_ < 3 || (ix_ < 4 && !down_)) {
        if (staticcount_ > 0) {
          // holding
        } else if (rising_) {
          final int jumptarget = 1716;
          if (level_ < (jumptarget << 16)) {
            level_ = jumptarget << 16;
          }
          // Proportional-attractive rising curve: level += ((17<<24) - level) >> 24 * inc * n
          level_ += (((17 << 24) - level_) >> 24) * inc_ * n;
          if (level_ >= targetlevel_) {
            level_ = targetlevel_;
            advance(patch, opOff, ix_ + 1, extraRate);
          }
        } else { // falling
          level_ -= inc_ * n;
          if (level_ <= targetlevel_) {
            level_ = targetlevel_;
            advance(patch, opOff, ix_ + 1, extraRate);
          }
        }
      }
      return level_;
    }

    void keydown(byte[] patch, int opOff, boolean d) {
      if (down_ != d) {
        down_ = d;
        advance(patch, opOff, d ? 0 : 3, 0);
      }
    }

    static int scaleoutlevel(int outlevel) {
      if (outlevel >= 20) return 28 + outlevel;
      return Dx7EngineLookupTables.levellut[outlevel];
    }

    void advance(byte[] patch, int opOff, int newix, int extraRate) {
      ix_ = newix;
      if (ix_ < 4) {
        // EnvParams layout: rates[0..3] at patch[opOff+0..3], levels[0..3] at patch[opOff+4..7]
        int rate = patch[opOff + ix_] & 0xFF; // egRate[ix_]
        int newlevel = patch[opOff + 4 + ix_] & 0xFF; // egLevel[ix_]
        int actuallevel = scaleoutlevel(newlevel) >> 1;
        actuallevel = (actuallevel << 6) + outlevel_ - 4256;
        if (actuallevel < 16) actuallevel = 16;
        targetlevel_ = actuallevel << 16;
        rising_ = targetlevel_ > level_;

        int qrate = (rate * 41) >> 6;
        qrate += rate_scaling_ + extraRate;
        if (qrate > 63) qrate = 63;

        // ACCURATE_ENVELOPE static count
        if (targetlevel_ == level_ || (ix_ == 0 && newlevel == 0)) {
          int staticrate = rate;
          staticrate += rate_scaling_ + extraRate;
          if (staticrate > 99) staticrate = 99;
          if (staticrate < 77) {
            staticcount_ = Dx7EngineLookupTables.statics[staticrate];
          } else {
            staticcount_ = 20 * (99 - staticrate);
          }
          if (staticrate < 77 && ix_ == 0 && newlevel == 0) {
            staticcount_ /= 20; // attack scaled faster
          }
          staticcount_ =
              (int) (((long) staticcount_ * (long) Dx7EngineLookupTables.sr_multiplier) >> 24);
        } else {
          staticcount_ = 0;
        }

        inc_ = (4 + (qrate & 3)) << (2 + (qrate >> 2));
        inc_ = (int) (((long) inc_ * (long) Dx7EngineLookupTables.sr_multiplier) >> 24);
      }
    }
  }

  // ── PitchEnv: pitch envelope generator ──

  /** Pitch envelope generator ported from dexed pitchenv.h/cpp. */
  static class PitchEnv {
    int level_; // Q24/octave
    int targetlevel_;
    boolean rising_;
    int ix_;
    int inc_;
    boolean down_;

    void set(byte[] patch, int pitchOff) {
      level_ = Dx7EngineLookupTables.pitchenv_tab[patch[pitchOff + 3] & 0xFF] << 19;
      down_ = true;
      advance(patch, pitchOff, 0);
    }

    int getsample(byte[] patch, int pitchOff, int n) {
      if (ix_ < 3 || (ix_ < 4 && !down_)) {
        if (rising_) {
          level_ += n * inc_;
          if (level_ >= targetlevel_) {
            level_ = targetlevel_;
            advance(patch, pitchOff, ix_ + 1);
          }
        } else {
          level_ -= n * inc_;
          if (level_ <= targetlevel_) {
            level_ = targetlevel_;
            advance(patch, pitchOff, ix_ + 1);
          }
        }
      }
      return level_;
    }

    void keydown(byte[] patch, int pitchOff, boolean d) {
      if (down_ != d) {
        down_ = d;
        advance(patch, pitchOff, d ? 0 : 3);
      }
    }

    boolean isDown() {
      return down_;
    }

    private void advance(byte[] patch, int pitchOff, int newix) {
      ix_ = newix;
      if (ix_ < 4) {
        int newlevel = patch[pitchOff + ix_] & 0xFF;
        targetlevel_ = Dx7EngineLookupTables.pitchenv_tab[newlevel] << 19;
        rising_ = targetlevel_ > level_;
        inc_ =
            Dx7EngineLookupTables.pitchenv_rate[patch[pitchOff + ix_] & 0xFF]
                * Dx7EngineLookupTables.pitchEnvUnit;
      }
    }
  }

  // ── Instance fields ──

  /** The 156-byte raw patch blob. */
  byte[] patch;

  /** Sample rate. */
  private final float sampleRate;

  // Per-voice state (matching DxVoice)

  /** Per-operator envelopes. */
  final Env[] env = new Env[6];

  /** Pitch envelope. */
  final PitchEnv pitchenv = new PitchEnv();

  /** Per-operator phase accumulators (Q32). */
  final int[] phase = new int[6];

  /** Per-operator last gain output (Q24). */
  final int[] gainOut = new int[6];

  /** Per-operator base pitch (Q24 log-frequency). */
  final int[] basePitch = new int[6];

  /** Feedback delay buffer (2 samples). */
  final int[] fbBuf = new int[2];

  /** LFO phase (Q32). */
  int lfoPhase;

  /** LFO value (Q24). */
  int lfoValue;

  /** LFO delay state. */
  int delayState;

  /** LFO delay increment. */
  int delayInc;

  /** LFO delay increment 2. */
  int delayInc2;

  /** Whether the note is currently sounding. */
  boolean active;

  /** Current MIDI note. */
  int midiNote;

  /** Current velocity (0-127). */
  int velocity;

  /**
   * Engine type override: -1=AUTO (firmware default), 0=MODERN (sinLookup/exp2Lookup, 32-bit),
   * 1=VINTAGE (mkiSin, ENV_BITDEPTH=14). When -1, auto-detection based on algorithm + feedback
   * applies.
   */
  private int forceVintage = -1;

  /** Random detune scale factor (from patch random_detune, defaults to 0). */
  int randomDetuneScale;

  /** Pitch bend or unison cents offset in Q24 log-frequency domain. */
  public int pitchBendOffset = 0;

  /** MIDI Modulation Wheel controller value (0-127, defaults to 127 for neutral compatibility). */
  public int modWheel = 127;

  /** MIDI Aftertouch (pressure) controller value (0-127). */
  public int aftertouch = 0;

  /** MIDI Breath Controller value (0-127). */
  public int breathController = 0;

  /** MIDI Foot Controller value (0-127). */
  public int footController = 0;

  /** Per-operator random detune values (int16, set during initNote). */
  final int[] detunePerVoice = new int[6];

  /** Static RNG state matching firmware CONG: jcong = 69069 * jcong + 1234567. */
  private static int rngState = 380116160;

  /**
   * Returns a pseudo-random 32-bit value matching the firmware's getNoise() CONG generator. Uses
   * the same LCG: jcong = 69069 * jcong + 1234567
   */
  public static int getNoise() {
    rngState = 69069 * rngState + 1234567;
    return rngState;
  }

  /** Whether we're in note-on state. */
  boolean noteOn;

  /** LFO delta per sample (Q32). */
  int lfoDelta;

  /** Thermal analog pitch drift state. */
  private int thermalDrift = 0;

  /**
   * Creates a Dx7Engine with the given sample rate. Dx7EngineLookupTables.init() must have been
   * called first.
   *
   * @param sampleRate sample rate in Hz
   */
  public Dx7Engine(float sampleRate) {
    this.sampleRate = sampleRate;
    Dx7EngineLookupTables.init(sampleRate);
    for (int i = 0; i < 6; i++) {
      env[i] = new Env();
      phase[i] = 0;
      gainOut[i] = 0;
    }
    active = false;
    noteOn = false;
    lfoPhase = 0;
    lfoValue = 1 << 23;
    delayState = 0;
    delayInc = 0;
    delayInc2 = 0;
  }

  /**
   * Load a DX7 patch from a 156-byte blob.
   *
   * @param patchData 156-byte raw patch array
   */
  public void loadPatch(byte[] patchData) {
    this.patch = patchData;
  }

  /** Load a DX7 patch from a Dx7Patch record. */
  public void loadPatch(Dx7Patch p) {
    this.patch = p.raw();
  }

  /**
   * Set the base note frequency (alternative to noteOn with MIDI note). Only used when not calling
   * noteOn(midiNote, velocity). This just stores the value; actual per-operator frequencies are set
   * via noteOn.
   */
  public void setFreq(double freq) {
    // In the dexed implementation, frequency is derived from MIDI note.
    // If someone calls setFreq() directly, we approximate by finding
    // the MIDI note closest to this frequency.
    if (freq > 0) {
      int note = (int) Math.round(69 + 12 * Math.log(freq / 440.0) / Math.log(2));
      if (note < 0) note = 0;
      if (note > 127) note = 127;
      midiNote = note;
    }
  }

  /**
   * Trigger note-on with MIDI note and velocity, using the currently loaded patch.
   *
   * @param midiNote MIDI note number (0-127)
   * @param velocity velocity (0-127)
   */
  public void noteOn(int midiNote, int velocity) {
    if (patch == null) return;
    noteOn = true;
    active = true;
    initNote(midiNote, velocity);
  }

  /** Legacy noteOn() with no args — uses previously set note/velocity or defaults. */
  public void noteOn() {
    if (patch == null) return;
    noteOn = true;
    active = true;
    if (midiNote == 0 && velocity == 0) {
      // Default: C4, velocity 100
      initNote(60, 100);
    } else {
      initNote(midiNote, velocity > 0 ? velocity : 100);
    }
  }

  /**
   * noteOn with velocity only, using the midiNote previously set by setFreq(). This is the primary
   * entry point for the DSL engine, which sets frequency via setFreq() then triggers with the
   * clip's actual velocity.
   *
   * @param velocity MIDI velocity (0-127)
   */
  public void noteOn(int velocity) {
    if (patch == null) return;
    noteOn = true;
    active = true;
    if (velocity <= 0) velocity = 100;
    initNote(midiNote > 0 ? midiNote : 60, velocity);
  }

  /** Trigger note-off — all operators enter release phase. */
  public void noteOff() {
    noteOn = false;
    for (int op = 0; op < 6; op++) {
      int opOff = op * 21;
      env[op].keydown(patch, opOff, false);
    }
    pitchenv.keydown(patch, 126, false);
  }

  public boolean isActive() {
    return active;
  }

  public boolean isVintage() {
    if (forceVintage == 1) return true;
    if (forceVintage == 0) return false;
    if (patch == null) return false;
    int algoIdx = (patch[134] & 0xFF);
    if (algoIdx > 31) algoIdx = 0;
    int feedback = patch[135] & 0xFF;
    int fbShift = feedback != 0 ? Dx7EngineLookupTables.FEEDBACK_BITDEPTH - feedback : 16;
    return fbShift < 16 && (algoIdx == 3 || algoIdx == 5);
  }

  public void setForceVintage(int val) {
    this.forceVintage = val;
  }

  public void setRandomDetuneScale(int val) {
    this.randomDetuneScale = val;
  }

  // ── Core initialization ──

  private void initNote(int midinote, int vel) {
    this.midiNote = midinote;
    this.velocity = vel;

    // Apply patch transpose (offset by 24)
    int transpose = patch[144] & 0xFF;
    int transposedNote = midinote + (transpose - 24);
    int logFreq = Dx7EngineLookupTables.dxNoteToFreq(transposedNote);

    for (int op = 0; op < 6; op++) {
      int off = op * 21;

      // Output level with key scaling + velocity
      int outlevel = patch[off + 16] & 0xFF;
      outlevel = Env.scaleoutlevel(outlevel);
      outlevel +=
          scaleLevel(
              midinote,
              patch[off + 8] & 0xFF,
              patch[off + 9] & 0xFF,
              patch[off + 10] & 0xFF,
              patch[off + 11] & 0xFF,
              patch[off + 12] & 0xFF);
      if (outlevel > 127) outlevel = 127;
      outlevel = outlevel << 5;
      outlevel += scaleVelocity(vel, patch[off + 15] & 0xFF);
      if (outlevel < 0) outlevel = 0;

      int rateScaling = scaleRate(midinote, patch[off + 13] & 0xFF);
      env[op].init(patch, off, outlevel, rateScaling);

      int mode = patch[off + 17] & 0xFF;
      int coarse = patch[off + 18] & 0xFF;
      int fine = patch[off + 19] & 0xFF;
      int detune = patch[off + 20] & 0xFF;

      detunePerVoice[op] = getNoise() >> 16;
      basePitch[op] = oscFreq(logFreq, mode, coarse, fine, detune, detunePerVoice[op]);
      phase[op] = 0;
      gainOut[op] = 0;
    }

    // Pitch envelope
    pitchenv.set(patch, 126);

    // Oscillator sync/unsync (matching firmware init(): patch[136] ? oscSync() : oscUnSync())
    if ((patch[136] & 0xFF) != 0) {
      // oscSync: all phases = 0 (already initialized above)
    } else {
      // oscUnSync: randomize phases
      for (int i = 0; i < 6; i++) {
        gainOut[i] = 0;
        phase[i] = getNoise();
      }
    }

    // LFO delay setup
    int a = 99 - (patch[138] & 0xFF); // LFO delay param
    if (a == 99) {
      delayInc = 0xFFFFFFFF;
      delayInc2 = 0xFFFFFFFF;
    } else {
      a = (16 + (a & 15)) << (1 + (a >> 4));
      delayInc = Dx7EngineLookupTables.lfo_unit * a;
      a &= 0xff80;
      if (a < 0x80) a = 0x80;
      delayInc2 = Dx7EngineLookupTables.lfo_unit * a;
    }
    delayState = 0;

    // Initialize LFO
    updateLfo();
    lfoPhase = patch[141] != 0 ? (1 << 31) - 1 : 0;
  }

  // ── LFO ──

  /** Update LFO delta based on patch rate parameter. Matches firmware DxPatch::updateLfo(). */
  void updateLfo() {
    if (patch == null) return;
    int rate = patch[137] & 0xFF; // LFO speed 0-99
    int sr = rate == 0 ? 1 : (165 * rate) >> 6;
    sr *= sr < 160 ? 11 : (11 + ((sr - 160) >> 4));
    lfoDelta = Dx7EngineLookupTables.lfo_unit * sr;
  }

  /** Advance LFO by n samples. Matches firmware DxPatch::computeLfo(n). */
  void computeLfo(int n) {
    if (patch == null) return;
    lfoPhase += n * lfoDelta;
    int waveform = patch[142] & 0xFF;
    if (waveform == 5) {
      // TODO: sample & hold
    }
    lfoValue = Dx7EngineLookupTables.lfoPhaseToValue(lfoPhase, waveform);
  }

  /** LFO delay computation per-sample. Matches firmware DxVoice::getdelay(n). */
  int getdelay(int n) {
    int delta = delayState < (1 << 31) ? delayInc : delayInc2;
    long d = ((long) delayState & 0xFFFFFFFFL) + (long) delta * n;
    if (d > 0xFFFFFFFFL) {
      return 1 << 24;
    }
    delayState = (int) d;
    if (d < (1L << 31)) {
      return 0;
    } else {
      return ((int) d >> 7) & ((1 << 24) - 1);
    }
  }

  // ── Computed helpers ──

  /** Compute operator frequency in Q24 log domain. Matches firmware DxVoice::osc_freq(). */
  private int oscFreq(
      int logFreqForDetune, int mode, int coarse, int fine, int detune, int randomDetune) {
    if (mode == 0) {
      // Ratio mode: start from the note's base log-frequency, then apply
      // coarse ratio, fine tune, and detune as additive offsets in the log domain.
      int logfreq = logFreqForDetune;

      // Detune ratio
      double detuneRatio = 0.0209 * Math.exp(-0.396 * ((double) logFreqForDetune / (1 << 24))) / 7;
      int randomScaled = (randomDetune * randomDetuneScale) >> 15;
      logfreq += (int) (detuneRatio * logFreqForDetune * (detune - 7 + randomScaled));

      logfreq += Dx7EngineLookupTables.coarsemul[coarse & 31];
      if (fine != 0) {
        logfreq += (int) Math.floor(24204406.323123 * Math.log(1 + 0.01 * fine) + 0.5);
      }
      return logfreq;
    } else {
      // Fixed mode
      int logfreq = (4458616 * ((coarse & 3) * 100 + fine)) >> 3;
      if (detune > 7) {
        logfreq += 13457 * (detune - 7);
      }
      return logfreq;
    }
  }

  /** Scale velocity to microstep units. Matches firmware ScaleVelocity(). */
  private int scaleVelocity(int vel, int sensitivity) {
    int clamped = Math.max(0, Math.min(127, vel));
    int velValue = Dx7EngineLookupTables.velocity_data[clamped >> 1] - 239;
    return ((sensitivity * velValue + 7) >> 3) << 4;
  }

  /** Scale rate based on MIDI note. Matches firmware ScaleRate(). */
  private int scaleRate(int midinote, int sensitivity) {
    int x = Math.min(31, Math.max(0, midinote / 3 - 7));
    return (sensitivity * x) >> 3;
  }

  /** Key scaling level calculation. Matches firmware ScaleLevel(). */
  private int scaleLevel(
      int midinote, int breakPt, int leftDepth, int rightDepth, int leftCurve, int rightCurve) {
    int offset = midinote - breakPt - 17;
    if (offset >= 0) {
      return scaleCurve((offset + 1) / 3, rightDepth, rightCurve);
    } else {
      return scaleCurve(-(offset - 1) / 3, leftDepth, leftCurve);
    }
  }

  /** Scale curve computation. Matches firmware ScaleCurve(). */
  private int scaleCurve(int group, int depth, int curve) {
    int scale;
    if (curve == 0 || curve == 3) {
      // linear
      scale = (group * depth * 329) >> 12;
    } else {
      // exponential
      int idx = Math.min(group, Dx7EngineLookupTables.exp_scale_data.length - 1);
      int rawExp = Dx7EngineLookupTables.exp_scale_data[idx];
      scale = (rawExp * depth * 329) >> 15;
    }
    if (curve < 2) {
      scale = -scale;
    }
    return scale;
  }

  // ── ChuckUGen implementation ──

  @Override
  protected float compute(float input, long systemTime) {
    return tick();
  }

  public float tick() {
    if (!active || patch == null) {
      return 0.0f;
    }

    int n = 1; // one sample per call

    // ── LFO ──
    computeLfo(n);
    int lfoDelay = getdelay(n);
    int lfoVal = lfoValue;

    // ── Pitch modulation scaled by active controllers ──
    int activePitchMod =
        Math.max(modWheel, Math.max(aftertouch, Math.max(breathController, footController)));
    int pitchmoddepth = (patch[139] & 0xFF) * 165 >> 6;
    pitchmoddepth = (pitchmoddepth * activePitchMod) >> 7;
    int pitchmodsens = Dx7EngineLookupTables.pitchmodsenstab[patch[143] & 7];
    long pmd = (long) pitchmoddepth * (long) lfoDelay; // Q32
    int senslfo = pitchmodsens * (lfoVal - (1 << 23));
    int pmod1 = (int) ((pmd * (long) senslfo) >> 39);
    if (pmod1 < 0) pmod1 = -pmod1;
    int pmod2 = 0;
    int pitchMod = Math.max(pmod1, pmod2);
    pitchMod = pitchenv.getsample(patch, 126, n) + (pitchMod * (senslfo < 0 ? -1 : 1));

    // ── Thermal analog pitch drift ──
    thermalDrift = (thermalDrift - (thermalDrift >> 12)) + (getNoise() >> 26);
    int driftMod = (thermalDrift * randomDetuneScale) >> 8;
    int pitchModTotal = pitchMod + driftMod;

    // ── Amp modulation scaled by active controllers ──
    // Firmware dx7note.cpp inverts the LFO value here (after pitch mod, before amp mod), so the
    // tremolo runs opposite-phase to the pitch vibrato. (Restored — was dropped in a refactor.)
    lfoVal = (1 << 24) - lfoVal;
    int activeAmpMod =
        Math.max(modWheel, Math.max(aftertouch, Math.max(breathController, footController)));
    int ampmoddepth = (patch[140] & 0xFF) * 165 >> 6;
    ampmoddepth = (ampmoddepth * activeAmpMod) >> 7;
    long amod1 = ((long) ampmoddepth * (long) lfoDelay) >> 8; // Q24
    amod1 = (amod1 * (long) lfoVal) >> 24;
    int amdMod = (int) amod1;

    // ── EG amp mod ──
    long amod3 = ((patch[144] & 0xFF) + 1L) << 17; // eg_mod from patch
    amdMod = Math.max((int) ((1 << 24) - amod3), amdMod);

    // Collect operator parameters: phase, freq, level_in, gain_out
    int[] opPhase = new int[6];
    int[] opFreq = new int[6];
    int[] opLevel = new int[6];
    int[] opGain = new int[6];

    for (int op = 0; op < 6; op++) {
      int off = op * 21;
      opPhase[op] = phase[op];
      opGain[op] = gainOut[op];

      boolean opSw = ((patch[155] >> op) & 1) != 0;

      if (!opSw) {
        // Advance envelope silently
        env[op].getsample(patch, off, n, 0);
        opLevel[op] = 0;
        opFreq[op] = 0;
      } else {
        int mode = patch[off + 17] & 0xFF;
        if (mode != 0) {
          // Fixed frequency mode
          opFreq[op] = Dx7EngineLookupTables.freqLutLookup(basePitch[op]);
        } else {
          // Ratio mode with pitch modulation
          opFreq[op] =
              Dx7EngineLookupTables.freqLutLookup(basePitch[op] + pitchModTotal + pitchBendOffset);
        }

        int level = env[op].getsample(patch, off, n, 0);

        // Amp mod sensitivity (matching firmware dx7note.cpp:365-374)
        int ampmodsens = Dx7EngineLookupTables.ampmodsenstab[patch[off + 14] & 3];
        if (ampmodsens != 0) {
          long sensamp = ((long) amdMod * (long) ampmodsens) >> 24;
          // pt = exp(sensamp / 262144 * 0.07 + 12.2)
          long pt = (long) Math.exp(((double) sensamp) / 262144.0 * 0.07 + 12.2);
          long ldiff = ((long) level * (pt << 4)) >> 28;
          level -= (int) ldiff;
          // level += (ampmodsens >> 16) * voice_ctrls->ampmod; // velmod=0 currently, no-op
        }

        // Velocity sensitivity modulation (firmware: level += patch[off+15] * voice_ctrls->velmod)
        level += (patch[off + 15] & 0xFF) * 0; // velmod=0 currently, formal no-op

        opLevel[op] = level;
      }
    }

    // ── Algorithm routing (bus-based, per-sample) ──
    int algoIdx = (patch[134] & 0xFF);
    if (algoIdx > 31) algoIdx = 0;

    // In per-sample mode: each operator renders one sample's worth
    // Bus accumulation: 0=main output, 1=bus1, 2=bus2
    int bus1 = 0;
    int bus2 = 0;
    // has_contents tracks which buses have active content (firmware fm_core.cpp line 92-93).
    // Main output (0) always starts with content. Buses 1/2 start empty.
    boolean[] has_contents = {true, false, false};

    int feedback = patch[135] & 0xFF;
    int fbShift = feedback != 0 ? Dx7EngineLookupTables.FEEDBACK_BITDEPTH - feedback : 16;

    int mainOutput = 0;

    boolean engineMkI = false;
    if (forceVintage == 1) {
      engineMkI = true;
    } else if (forceVintage == -1) {
      // Auto-detect: firmware activates EngineMkI for algorithms 3 and 5 with feedback
      if (fbShift < 16 && (algoIdx == 3 || algoIdx == 5)) {
        engineMkI = true;
      }
    }
    // forceVintage == 0: explicitly MODERN, engineMkI stays false

    for (int op = 0; op < 6; op++) {
      int flags = Dx7EngineLookupTables.ALGORITHMS[algoIdx * 6 + op];
      int ob = outbus(flags);
      int ib = inbus(flags);
      boolean add = outBusAdd(flags);
      boolean fbin = fbIn(flags);
      boolean fbout = fbOut(flags);

      int levelIn = opLevel[op];
      int freq = opFreq[op];

      // ── EngineMkI path for feedback algorithms 3/5 ──
      // The firmware's EngineMkI handles FB_IN|FB_OUT operators using mkiSin(),
      // which has a different sine function + ENV_BITDEPTH=14 envelope integration.
      // For algo 3 (3-op chain: op0→op1→op2) and algo 5 (2-op chain: op0→op1).
      if (engineMkI && op == 0 && (flags & 0xc0) == 0xc0) {
        if (algoIdx == 3) {
          int mkShift = Math.min(fbShift + 2, 16);
          int y =
              computeFb3(
                  opPhase[0],
                  opFreq[0],
                  opLevel[0],
                  opPhase[1],
                  opFreq[1],
                  opLevel[1],
                  opPhase[2],
                  opFreq[2],
                  opLevel[2],
                  fbBuf,
                  mkShift);
          mainOutput = y;
          has_contents[0] = true;
          // Advance phases for the three operators we're consuming
          opPhase[0] += opFreq[0];
          opPhase[1] += opFreq[1];
          opPhase[2] += opFreq[2];
          opGain[0] = Dx7EngineLookupTables.kGainLevelThresh + 1; // mark as active
          opGain[1] = Dx7EngineLookupTables.kGainLevelThresh + 1;
          opGain[2] = Dx7EngineLookupTables.kGainLevelThresh + 1;
          op += 2; // skip ops 1 and 2 — already processed
          continue;
        } else if (algoIdx == 5) {
          int mkShift = Math.min(fbShift + 2, 16);
          int y =
              computeFb2(
                  opPhase[0],
                  opFreq[0],
                  opLevel[0],
                  opPhase[1],
                  opFreq[1],
                  opLevel[1],
                  fbBuf,
                  mkShift);
          mainOutput = y;
          has_contents[0] = true;
          opPhase[0] += opFreq[0];
          opPhase[1] += opFreq[1];
          opGain[0] = Dx7EngineLookupTables.kGainLevelThresh + 1;
          opGain[1] = Dx7EngineLookupTables.kGainLevelThresh + 1;
          op++; // skip op 1
          continue;
        }
      }

      // Pre-compute gain to check if operator is inaudible
      int gain = Dx7EngineLookupTables.exp2Lookup(levelIn - (14 << 24));

      // Inaudible-operator optimization: skip sine computation if gain is below threshold.
      // Only check current gain (not prevGain) because on the first sample prevGain=0
      // while the EG may still be ramping up.
      // For bus management: non-add operators clear their target bus when silent.
      if (gain < Dx7EngineLookupTables.kGainLevelThresh) {
        opPhase[op] += freq * 1;
        if (!add) {
          if (ob == 1) {
            bus1 = 0;
            has_contents[1] = false;
          } else if (ob == 2) {
            bus2 = 0;
            has_contents[2] = false;
          }
        }
        // Keep feedback buffer updated even when silent
        if (fbout) {
          fbBuf[0] = fbBuf[1];
          fbBuf[1] = 0;
        }
        opGain[op] = gain;
        continue;
      }

      // Firmware line 92-93: if output bus has no content, force add=false
      // (first operator to write to an empty bus replaces, subsequent ones add)
      if (!has_contents[ob]) {
        add = false;
      }

      // ── Modulation input (bus or feedback) ──
      int mod = 0;
      if (fbin) {
        // FB_IN operator: add scaled feedback from fbBuf to phase
        // Matches firmware FmOpKernel::compute_fb(): scaled_fb = (y0 + y1) >> (fbShift + 1)
        mod = (fbBuf[0] + fbBuf[1]) >> (fbShift + 1);
      } else if (ib == 1 && has_contents[1]) {
        mod = bus1;
      } else if (ib == 2 && has_contents[2]) {
        mod = bus2;
      }

      // Sine lookup with modulation
      int y = Dx7EngineLookupTables.sinLookup(opPhase[op] + mod);

      // Gain: Exp2::lookup(levelIn - 14 * (1 << 24))
      int y1 = (int) (((long) y * (long) gain) >> 24);

      // Accumulate to output bus — handle all three outbus cases inline
      switch (ob) {
        case 0: // Main output (carrier)
          if (add) {
            mainOutput += y1;
          } else {
            mainOutput = y1;
          }
          break;
        case 1: // Bus 1
          if (add) {
            bus1 += y1;
          } else {
            bus1 = y1;
          }
          break;
        case 2: // Bus 2
          if (add) {
            bus2 += y1;
          } else {
            bus2 = y1;
          }
          break;
      }
      has_contents[ob] = true;

      // Store gain for active check
      opGain[op] = gain;

      // Update feedback buffer (only needed for FB_OUT ops not in the block loop above,
      // i.e. when the block path above already handled it, this won't fire again because
      // we used 'continue')
      if (fbout) {
        fbBuf[0] = fbBuf[1];
        fbBuf[1] = y1;
      }

      opPhase[op] += freq;
    }

    // ── Update state ──
    for (int op = 0; op < 6; op++) {
      phase[op] = opPhase[op];
      gainOut[op] = opGain[op];
    }

    // Check active status
    boolean anyActive = false;
    for (int op = 0; op < 6; op++) {
      if (gainOut[op] >= Dx7EngineLookupTables.kGainLevelThresh) {
        anyActive = true;
        break;
      }
    }
    if (!anyActive && !noteOn) {
      active = false;
    }

    // Convert Q24 to float (-1..1 range)
    float floatOut = mainOutput / 16777216.0f;
    if (engineMkI) {
      // Vintage 12-bit quantization
      float quantized = Math.round(floatOut * 2048.0f) / 2048.0f;
      // Resistor-ladder DAC step mismatch non-linearity (zero-crossing crossover distortion)
      if (quantized > 0.0f) {
        quantized += 0.0005f * (1.0f - quantized);
      } else if (quantized < 0.0f) {
        quantized -= 0.0005f * (1.0f + quantized);
      }
      return quantized;
    }
    return floatOut;
  }

  // ── EngineMkI rendering (for feedback algorithms 3 and 5) ──

  /**
   * Compute_fb2: 2-op feedback chain for algorithm 5 with feedback. Matches EngineMkI.cpp
   * compute_fb2().
   *
   * <p>Process sequence: op0 (with feedback) → op1 → output (main bus). Both operators output
   * directly to main (no bus accumulation).
   *
   * @return mkiSin output for the last operator in the chain
   */
  private static int computeFb2(
      int phase0,
      int freq0,
      int levelIn0,
      int phase1,
      int freq1,
      int levelIn1,
      int[] fbBuf,
      int fbShift) {

    int gain0 = fbBuf[1] != 0 ? fbBuf[1] : (Dx7EngineLookupTables.ENV_MAX - 1);
    int y0 = fbBuf[0];
    int y = fbBuf[1];

    // gain for op1
    int levelIn1_14 = levelIn1 >> (28 - Dx7EngineLookupTables.ENV_BITDEPTH);
    int gain1 =
        levelIn1_14 == 0
            ? (Dx7EngineLookupTables.ENV_MAX - 1)
            : (Dx7EngineLookupTables.ENV_MAX - levelIn1_14);

    // Per-sample (n=1): no dgain interpolation, gain = gain2 directly
    // op0: level_in used on the mkiSin call
    int levelIn0_14 = levelIn0 >> (28 - Dx7EngineLookupTables.ENV_BITDEPTH);
    int gain0_target = Dx7EngineLookupTables.ENV_MAX - levelIn0_14;

    // op 0 with feedback
    int scaled_fb = (y0 + y) >> (fbShift + 1);
    y0 = y;
    y = Dx7EngineLookupTables.mkiSin(phase0 + scaled_fb, gain0_target);
    phase0 += freq0;

    // op 1 modulated by op0
    y = Dx7EngineLookupTables.mkiSin(phase1 + y, gain1);
    phase1 += freq1;

    // Update feedback buffer
    fbBuf[0] = y0;
    fbBuf[1] = y;

    return y;
  }

  /**
   * Compute_fb3: 3-op feedback chain for algorithm 3 with feedback. Matches EngineMkI.cpp
   * compute_fb3().
   *
   * <p>Process sequence: op0 (with feedback) → op1 → op2 → output (main bus).
   *
   * @return mkiSin output for the last operator in the chain
   */
  private static int computeFb3(
      int phase0,
      int freq0,
      int levelIn0,
      int phase1,
      int freq1,
      int levelIn1,
      int phase2,
      int freq2,
      int levelIn2,
      int[] fbBuf,
      int fbShift) {

    int y0 = fbBuf[0];
    int y = fbBuf[1];

    // Envelope gain for each operator (per-sample: gain = gain2 directly)
    // gain = ENV_MAX - (level_in >> (28 - ENV_BITDEPTH))
    int gain1_val = envToGain(levelIn1);
    int gain2_val = envToGain(levelIn2);
    int gain0_target = envToGain(levelIn0);

    // op 0 with feedback
    int scaled_fb = (y0 + y) >> (fbShift + 1);
    y0 = y;
    y = Dx7EngineLookupTables.mkiSin(phase0 + scaled_fb, gain0_target);
    phase0 += freq0;

    // op 1 modulated by op0
    y = Dx7EngineLookupTables.mkiSin(phase1 + y, gain1_val);
    phase1 += freq1;

    // op 2 modulated by op1
    y = Dx7EngineLookupTables.mkiSin(phase2 + y, gain2_val);
    phase2 += freq2;

    // Update feedback buffer
    fbBuf[0] = y0;
    fbBuf[1] = y;

    return y;
  }

  /**
   * Convert Q24 log-domain envelope level to EngineMkI 14-bit gain. gain = ENV_MAX - (level_in >>
   * (28 - ENV_BITDEPTH)) If the result is 0, returns ENV_MAX - 1 (matching firmware gain_out == 0
   * check).
   */
  private static int envToGain(int levelIn) {
    int shifted = levelIn >> (28 - Dx7EngineLookupTables.ENV_BITDEPTH);
    int gain = Dx7EngineLookupTables.ENV_MAX - shifted;
    if (gain == 0) return Dx7EngineLookupTables.ENV_MAX - 1;
    return gain;
  }

  // ── Helper: set frequency (ChucK-style) ──

  public double freq(double f) {
    setFreq(f);
    return f;
  }
}
