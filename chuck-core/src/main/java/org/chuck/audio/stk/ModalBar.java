package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.stk.util.StkBiQuad;
import org.chuck.audio.stk.util.StkOnePole;
import org.chuck.core.ChuckVM;
import org.chuck.core.Std;

/**
 * ModalBar — struck resonant bar physical model. Models xylophones, vibraphones, marimbas, agogo
 * bells, and woodblocks using four parallel resonant modes.
 */
public class ModalBar extends ChuckUGen {
  private static final int MODES = 4;

  private static final double[][][] PRESETS = {
    // 0: Marimba
    {
      {1.0, 3.99, 10.65, -2443.0},
      {0.9996, 0.9994, 0.9994, 0.999},
      {0.04, 0.01, 0.01, 0.008},
      {0.429688, 0.445312, 0.093750}
    },
    // 1: Vibraphone
    {
      {1.0, 2.01, 3.9, 14.37},
      {0.99995, 0.99991, 0.99992, 0.9999},
      {0.025, 0.015, 0.015, 0.015},
      {0.390625, 0.570312, 0.078125}
    },
    // 2: Agogo
    {
      {1.0, 4.08, 6.669, -3725.0},
      {0.999, 0.999, 0.999, 0.999},
      {0.06, 0.05, 0.03, 0.02},
      {0.609375, 0.359375, 0.140625}
    },
    // 3: Wood1
    {
      {1.0, 2.777, 7.378, 15.377},
      {0.996, 0.994, 0.994, 0.99},
      {0.04, 0.01, 0.01, 0.008},
      {0.460938, 0.375000, 0.046875}
    },
    // 4: Reso
    {
      {1.0, 2.777, 7.378, 15.377},
      {0.99996, 0.99994, 0.99994, 0.9999},
      {0.02, 0.005, 0.005, 0.004},
      {0.453125, 0.250000, 0.101562}
    },
    // 5: Wood2
    {
      {1.0, 1.777, 2.378, 3.377},
      {0.996, 0.994, 0.994, 0.99},
      {0.04, 0.01, 0.01, 0.008},
      {0.312500, 0.445312, 0.109375}
    },
    // 6: Beats
    {
      {1.0, 1.004, 1.013, 2.377},
      {0.9999, 0.9999, 0.9999, 0.999},
      {0.02, 0.005, 0.005, 0.004},
      {0.398438, 0.296875, 0.070312}
    },
    // 7: Two Fixed
    {
      {1.0, 4.0, -1320.0, -3960.0},
      {0.9996, 0.999, 0.9994, 0.999},
      {0.04, 0.01, 0.01, 0.008},
      {0.453125, 0.453125, 0.070312}
    },
    // 8: Clump
    {
      {1.0, 1.217, 1.475, 1.729},
      {0.999, 0.999, 0.999, 0.999},
      {0.03, 0.03, 0.03, 0.03},
      {0.390625, 0.570312, 0.078125}
    }
  };

  private final StkBiQuad[] filters = new StkBiQuad[MODES];
  private final StkOnePole onepole = new StkOnePole();

  private final double[] ratios = new double[MODES];
  private final double[] gains = new double[MODES];
  private final double[] resons = new double[MODES];

  private double baseFreq = 440.0;
  private double stickHardness = 0.5;
  private double strikePosition = 0.56;
  private double directGain = 0.0;
  private double masterGain = 1.0;
  private double vibratoGain = 0.0;

  private double excitationDecay = 0.0;
  private double excitationAmp = 0.0;
  private double strikeForce = 0.0;
  private final float sampleRate;

  public ModalBar() {
    this(ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public ModalBar(float sampleRate) {
    this.sampleRate = sampleRate;

    for (int i = 0; i < MODES; i++) {
      filters[i] = new StkBiQuad(sampleRate);
    }

    setPreset(0); // Default Marimba
    setFreq(440.0);
  }

  public void setPreset(int preset) {
    int p = Math.abs(preset % 9);
    System.arraycopy(PRESETS[p][0], 0, ratios, 0, MODES);
    System.arraycopy(PRESETS[p][1], 0, resons, 0, MODES);
    System.arraycopy(PRESETS[p][2], 0, gains, 0, MODES);

    setStickHardness((float) PRESETS[p][3][0]);
    setStrikePosition((float) PRESETS[p][3][1]);
    directGain = PRESETS[p][3][2];

    if (p == 1) { // vibraphone
      vibratoGain = 0.2;
    } else {
      vibratoGain = 0.0;
    }
    updateFilters();
  }

  public void preset(int p) {
    setPreset(p);
  }

  public void setFreq(double f) {
    baseFreq = f;
    updateFilters();
  }

  public void freq(float f) {
    setFreq(f);
  }

  public void setStickHardness(float hardness) {
    stickHardness = Math.clamp(hardness, 0.0f, 1.0f);
    masterGain = 0.1 + (1.8 * stickHardness);
  }

  public void stickHardness(float hardness) {
    setStickHardness(hardness);
  }

  public void setStrikePosition(float position) {
    strikePosition = Math.clamp(position, 0.0f, 1.0f);

    double temp2 = strikePosition * Math.PI;
    double temp = Math.sin(temp2);
    gains[0] = 0.12 * temp;

    temp = Math.sin(0.05 + (3.9 * temp2));
    gains[1] = -0.03 * temp;

    temp = Math.sin(-0.05 + (11.0 * temp2));
    gains[2] = 0.11 * temp;
  }

  public void strikePosition(float position) {
    setStrikePosition(position);
  }

  public void strike(float force) {
    strikeForce = Math.max(0.0f, force);
    excitationDecay = 0.996 + stickHardness * 0.003;
    excitationAmp = strikeForce;

    double pole = 0.9 - stickHardness * 0.8;
    onepole.setPole(pole);
    onepole.clear();
  }

  public void noteOn(float velocity) {
    strike(velocity);
  }

  public void noteOff(float velocity) {
    for (int i = 0; i < MODES; i++) {
      resons[i] *= 0.8;
    }
    updateFilters();
  }

  public void controlChange(int number, float value) {
    float normalizedValue = value * (1.0f / 128.0f);
    if (number == 2) {
      setStickHardness(normalizedValue);
    } else if (number == 4) {
      setStrikePosition(normalizedValue);
    } else if (number == 16) {
      setPreset((int) value);
    } else if (number == 8) {
      directGain = normalizedValue;
    } else if (number == 1) {
      vibratoGain = normalizedValue * 0.3;
    }
  }

  private void updateFilters() {
    for (int i = 0; i < MODES; i++) {
      double modeFreq;
      if (ratios[i] < 0.0) {
        modeFreq = -ratios[i];
      } else {
        modeFreq = baseFreq * ratios[i];
      }

      // Limit frequencies below Nyquist
      if (modeFreq > sampleRate * 0.49) {
        modeFreq = sampleRate * 0.49;
      }
      filters[i].setResonance(modeFreq, resons[i], true);
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    double excitationSample = 0.0;

    if (excitationAmp > 0.0001) {
      double noise = Std.rand2f(-1.0, 1.0);
      excitationSample = onepole.tick(excitationAmp * noise);
      excitationAmp *= excitationDecay;
    }

    double totalExcitation = excitationSample + input;

    double wetSignal = 0.0;
    for (int i = 0; i < MODES; i++) {
      wetSignal += filters[i].tick(totalExcitation) * gains[i];
    }

    // Direct stick mix matches C++ masterGain * (wet - direct * excitation)
    double out = masterGain * (wetSignal - directGain * totalExcitation) * 11.5;

    lastOut = (float) (out * gain);
    return lastOut;
  }
}
