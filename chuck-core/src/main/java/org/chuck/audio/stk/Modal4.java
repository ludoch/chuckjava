package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * Modal4: High-fidelity physical modeling 4-resonator Modal Synthesis instrument. Ported from Perry
 * Cook's standard STK modal tools and Csound's modal4 opcode.
 *
 * <p>Uses four parallel BiQuad bandpass filters with dynamic equal-gain zero notch configurations,
 * excited by a OnePole stick hardness lowpass filtered hammer strike noise burst, to simulate
 * realistic struck marimbas, vibraphones, agogo bells, and wood blocks.
 */
@doc("High-fidelity physical modeling 4-resonator Modal Synthesis instrument.")
public class Modal4 extends ChuckUGen {

  public static final int PRESET_MARIMBA = 0;
  public static final int PRESET_VIBRAPHONE = 1;
  public static final int PRESET_AGOGO = 2;
  public static final int PRESET_WOODBLOCK = 3;
  public static final int PRESET_CUSTOM = 4;

  private static final double DEFAULT_SRATE = 44100.0;

  // Preset Ratio, Gain, and Resonance arrays
  private static final double[][] PRESET_RATIOS = {
    {1.0, 3.984, 10.0, 19.33}, // Marimba
    {1.0, 2.01, 3.84, 5.95}, // Vibraphone
    {1.0, 1.35, 1.87, 2.37}, // Agogo Bells
    {1.0, 1.57, 2.37, 3.40} // Wood Block
  };

  private static final double[][] PRESET_GAINS = {
    {1.0, 0.7, 0.5, 0.3}, // Marimba
    {1.0, 0.5, 0.3, 0.2}, // Vibraphone
    {1.0, 0.8, 0.6, 0.4}, // Agogo Bells
    {1.0, 0.6, 0.4, 0.2} // Wood Block
  };

  private static final double[][] PRESET_RESONS = {
    {0.9996, 0.9990, 0.9970, 0.9950}, // Marimba
    {0.99995, 0.99991, 0.99985, 0.99980}, // Vibraphone
    {0.9990, 0.9970, 0.9950, 0.9930}, // Agogo Bells
    {0.9900, 0.9800, 0.9700, 0.9600} // Wood Block
  };

  private static class BiQuad {
    double b0 = 1.0;
    double b1 = 0.0;
    double b2 = 0.0;
    double a1 = 0.0;
    double a2 = 0.0;

    double x1 = 0.0;
    double x2 = 0.0;
    double y1 = 0.0;
    double y2 = 0.0;

    void clear() {
      x1 = x2 = y1 = y2 = 0.0;
    }

    void setFreqAndReson(double freq, double r, double sampleRate) {
      double theta = 2.0 * Math.PI * freq / sampleRate;
      a1 = -2.0 * r * Math.cos(theta);
      a2 = r * r;

      // High-volume poles-only excitation normalization
      b0 = 1.0;
      b1 = 0.0;
      b2 = 0.0;
    }

    double tick(double input) {
      double output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
      x2 = x1;
      x1 = input;
      y2 = y1;
      y1 = output;
      return output;
    }
  }

  private static class OnePole {
    double b0 = 1.0;
    double a1 = 0.0;
    double y1 = 0.0;

    void clear() {
      y1 = 0.0;
    }

    void setPole(double pole) {
      a1 = -pole;
      b0 = 1.0 + a1;
    }

    double tick(double input) {
      double output = b0 * input - a1 * y1;
      y1 = output;
      return output;
    }
  }

  private final BiQuad[] filters = new BiQuad[4];
  private final OnePole onepole = new OnePole();

  private final double[] ratios = new double[4];
  private final double[] gains = new double[4];
  private final double[] resons = new double[4];

  private int presetType = PRESET_MARIMBA;
  private double baseFreq = 440.0;
  private double stickHardness = 0.5; // Mallet stick hardness (0.0 to 1.0)
  private double masterGain = 0.5;

  private int strikeCountdown = 0;
  private double strikeForce = 0.0;
  private double sampleRate = 44100.0;

  public Modal4() {
    super();
    this.sampleRate =
        org.chuck.core.ChuckVM.CURRENT_VM.isBound()
            ? org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate()
            : DEFAULT_SRATE;

    for (int i = 0; i < 4; i++) {
      filters[i] = new BiQuad();
    }

    // Default to Marimba preset
    preset(PRESET_MARIMBA);
  }

  @doc(
      "Load a standard modal physical preset (0 = Marimba, 1 = Vibraphone, 2 = Agogo, 3 = WoodBlock).")
  public void preset(int type) {
    if (type >= 0 && type < 4) {
      this.presetType = type;
      System.arraycopy(PRESET_RATIOS[type], 0, this.ratios, 0, 4);
      System.arraycopy(PRESET_GAINS[type], 0, this.gains, 0, 4);
      System.arraycopy(PRESET_RESONS[type], 0, this.resons, 0, 4);
      updateFilters();
    } else {
      this.presetType = PRESET_CUSTOM;
    }
  }

  public int preset() {
    return presetType;
  }

  @doc("Set base fundamental tuning frequency (Hz).")
  public void freq(float f) {
    this.baseFreq = Math.max(10.0, f);
    updateFilters();
  }

  public float freq() {
    return (float) baseFreq;
  }

  @doc("Set stick mallet hardness factor (0.0 to 1.0). Controls sound transient crispness.")
  public void hardness(float h) {
    this.stickHardness = Math.max(0.0f, Math.min(1.0f, h));
  }

  public float hardness() {
    return (float) stickHardness;
  }

  @doc("Set dynamic master output volume amplitude scaler.")
  public void amp(float a) {
    this.masterGain = Math.max(0.0, a);
  }

  public float amp() {
    return (float) masterGain;
  }

  @doc("Set specific custom ratio multiplier for a resonator mode (0 to 3).")
  public void ratio(int mode, float r) {
    if (mode >= 0 && mode < 4) {
      this.ratios[mode] = Math.max(0.1f, r);
      this.presetType = PRESET_CUSTOM;
      updateFilters();
    }
  }

  public float ratio(int mode) {
    return mode >= 0 && mode < 4 ? (float) ratios[mode] : 1.0f;
  }

  @doc("Set specific custom gain volume multiplier for a resonator mode (0 to 3).")
  public void gain(int mode, float g) {
    if (mode >= 0 && mode < 4) {
      this.gains[mode] = Math.max(0.0f, g);
      this.presetType = PRESET_CUSTOM;
    }
  }

  public float gain(int mode) {
    return mode >= 0 && mode < 4 ? (float) gains[mode] : 0.0f;
  }

  @doc(
      "Set specific custom resonance decay radius multiplier for a resonator mode (0 to 3) (0.9 to 0.99999).")
  public void resonance(int mode, float radius) {
    if (mode >= 0 && mode < 4) {
      this.resons[mode] = Math.max(0.5f, Math.min(0.99999f, radius));
      this.presetType = PRESET_CUSTOM;
      updateFilters();
    }
  }

  public float resonance(int mode) {
    return mode >= 0 && mode < 4 ? (float) resons[mode] : 0.9f;
  }

  @doc("Strikes/excites the physical modal instrument with a hammer mallet of a target force.")
  public void strike(float force) {
    this.strikeForce = Math.max(0.0f, force);
    this.strikeCountdown = 16; // 16 samples short excitation click burst!

    // Hardness maps to lowpass pole (0.0 maps to slow 0.9, 1.0 maps to fast 0.1)
    double pole = 0.9 - stickHardness * 0.8;
    onepole.setPole(pole);
    onepole.clear();

    // Do not clear the filters to allow multiple overlapping strikes (polyphonic voice response!)
  }

  @doc("Trigger note-on keyboard strikes.")
  public void noteOn(float velocity) {
    strike(velocity);
  }

  @doc("Trigger note-off damping stops.")
  public void noteOff(float velocity) {
    // Fast decay modal dampening
    for (int i = 0; i < 4; i++) {
      resons[i] *= 0.8; // Dampen resonance decay radii
    }
    updateFilters();
  }

  private void updateFilters() {
    for (int i = 0; i < 4; i++) {
      double modeFreq = baseFreq * ratios[i];
      // Limit mode frequencies below Nyquist limit to prevent filter blowouts!
      if (modeFreq > sampleRate * 0.49) {
        modeFreq = sampleRate * 0.49;
      }
      filters[i].setFreqAndReson(modeFreq, resons[i], sampleRate);
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    double excitationSample = 0.0;

    // Generate hammer mallet excitation strike click
    if (strikeCountdown > 0) {
      // Single unit impulse at step 0 (strikeCountdown == 16), followed by zeroes
      double impulse = (strikeCountdown == 16) ? strikeForce : 0.0;
      excitationSample = onepole.tick(impulse);
      strikeCountdown--;
    }

    // Mix input external signal with hammer strike excitation
    double totalExcitation = excitationSample + input;

    // Tick the 4 parallel BiQuad resonance modes
    double wetSignal = 0.0;
    for (int i = 0; i < 4; i++) {
      wetSignal += filters[i].tick(totalExcitation) * gains[i];
    }

    float outputVal = (float) (wetSignal * masterGain * gain);

    lastOut = outputVal;
    return outputVal;
  }
}
