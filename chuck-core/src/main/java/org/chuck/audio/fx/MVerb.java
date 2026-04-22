package org.chuck.audio.fx;

import java.util.Arrays;
import org.chuck.audio.util.StereoUGen;
import org.chuck.core.ChuckVM;
import org.chuck.core.doc;

@doc("MVerb: Studio quality, open-source reverb based on Dattorro's figure-of-eight structure.")
public class MVerb extends StereoUGen {

  // Constants
  public static final int DAMPINGFREQ = 0;
  public static final int DENSITY = 1;
  public static final int BANDWIDTHFREQ = 2;
  public static final int DECAY = 3;
  public static final int PREDELAY = 4;
  public static final int SIZE = 5;
  public static final int GAIN = 6;
  public static final int MIX = 7;
  public static final int EARLYMIX = 8;
  public static final int NUM_PARAMS = 9;

  // Helper Classes
  private static class Allpass {
    private final double[] buffer;
    private int index = 0;
    private int length = 0;
    private double feedback = 0.5;

    public Allpass(int maxLength) {
      this.buffer = new double[maxLength];
      this.length = maxLength - 1;
    }

    public double tick(double input) {
      double bufout = buffer[index];
      double temp = input * -feedback;
      double output = bufout + temp;
      buffer[index] = input + ((bufout + temp) * feedback);
      if (++index >= length) index = 0;
      return output;
    }

    public void setLength(int len) {
      if (len >= buffer.length) len = buffer.length - 1;
      if (len < 0) len = 0;
      this.length = len;
    }

    public void setFeedback(double fb) {
      this.feedback = fb;
    }

    public void clear() {
      Arrays.fill(buffer, 0);
      index = 0;
    }
  }

  private static class StaticAllpassFourTap {
    private final double[] buffer;
    private int index1 = 0;
    private int index2 = 0;
    private int index3 = 0;
    private int index4 = 0;
    private int length = 0;
    private double feedback = 0.5;

    public StaticAllpassFourTap(int maxLength) {
      this.buffer = new double[maxLength];
      this.length = maxLength - 1;
    }

    public double tick(double input) {
      double bufout = buffer[index1];
      double temp = input * -feedback;
      double output = bufout + temp;
      buffer[index1] = input + ((bufout + temp) * feedback);

      if (++index1 >= length) index1 = 0;
      if (++index2 >= length) index2 = 0;
      if (++index3 >= length) index3 = 0;
      if (++index4 >= length) index4 = 0;

      return output;
    }

    public void setIndex(int i1, int i2, int i3, int i4) {
      index1 = i1;
      index2 = i2;
      index3 = i3;
      index4 = i4;
    }

    public double getIndex(int idx) {
      return switch (idx) {
        case 0 -> buffer[index1];
        case 1 -> buffer[index2];
        case 2 -> buffer[index3];
        case 3 -> buffer[index4];
        default -> buffer[index1];
      };
    }

    public void setLength(int len) {
      if (len >= buffer.length) len = buffer.length - 1;
      if (len < 0) len = 0;
      this.length = len;
    }

    public void setFeedback(double fb) {
      this.feedback = fb;
    }

    public void clear() {
      Arrays.fill(buffer, 0);
      index1 = index2 = index3 = index4 = 0;
    }
  }

  private static class StaticDelayLine {
    private final double[] buffer;
    private int index = 0;
    private int length = 0;

    public StaticDelayLine(int maxLength) {
      this.buffer = new double[maxLength];
      this.length = maxLength - 1;
    }

    public double tick(double input) {
      double output = buffer[index];
      buffer[index++] = input;
      if (index >= length) index = 0;
      return output;
    }

    public void setLength(int len) {
      if (len >= buffer.length) len = buffer.length - 1;
      if (len < 0) len = 0;
      this.length = len;
    }

    public void clear() {
      Arrays.fill(buffer, 0);
      index = 0;
    }
  }

  private static class StaticDelayLineFourTap {
    private final double[] buffer;
    private int index1 = 0;
    private int index2 = 0;
    private int index3 = 0;
    private int index4 = 0;
    private int length = 0;

    public StaticDelayLineFourTap(int maxLength) {
      this.buffer = new double[maxLength];
      this.length = maxLength - 1;
    }

    public double tick(double input) {
      double output = buffer[index1];
      buffer[index1++] = input;
      if (index1 >= length) index1 = 0;
      if (++index2 >= length) index2 = 0;
      if (++index3 >= length) index3 = 0;
      if (++index4 >= length) index4 = 0;
      return output;
    }

    public void setIndex(int i1, int i2, int i3, int i4) {
      index1 = i1;
      index2 = i2;
      index3 = i3;
      index4 = i4;
    }

    public double getIndex(int idx) {
      return switch (idx) {
        case 0 -> buffer[index1];
        case 1 -> buffer[index2];
        case 2 -> buffer[index3];
        case 3 -> buffer[index4];
        default -> buffer[index1];
      };
    }

    public void setLength(int len) {
      if (len >= buffer.length) len = buffer.length - 1;
      if (len < 0) len = 0;
      this.length = len;
    }

    public void clear() {
      Arrays.fill(buffer, 0);
      index1 = index2 = index3 = index4 = 0;
    }
  }

  private static class StaticDelayLineEightTap {
    private final double[] buffer;
    private int index1 = 0;
    private int index2 = 0;
    private int index3 = 0;
    private int index4 = 0;
    private int index5 = 0;
    private int index6 = 0;
    private int index7 = 0;
    private int index8 = 0;
    private int length = 0;

    public StaticDelayLineEightTap(int maxLength) {
      this.buffer = new double[maxLength];
      this.length = maxLength - 1;
    }

    public double tick(double input) {
      double output = buffer[index1];
      buffer[index1++] = input;
      if (index1 >= length) index1 = 0;
      if (++index2 >= length) index2 = 0;
      if (++index3 >= length) index3 = 0;
      if (++index4 >= length) index4 = 0;
      if (++index5 >= length) index5 = 0;
      if (++index6 >= length) index6 = 0;
      if (++index7 >= length) index7 = 0;
      if (++index8 >= length) index8 = 0;
      return output;
    }

    public void setIndex(int i1, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
      index1 = i1;
      index2 = i2;
      index3 = i3;
      index4 = i4;
      index5 = i5;
      index6 = i6;
      index7 = i7;
      index8 = i8;
    }

    public double getIndex(int idx) {
      return switch (idx) {
        case 0 -> buffer[index1];
        case 1 -> buffer[index2];
        case 2 -> buffer[index3];
        case 3 -> buffer[index4];
        case 4 -> buffer[index5];
        case 5 -> buffer[index6];
        case 6 -> buffer[index7];
        case 7 -> buffer[index8];
        default -> buffer[index1];
      };
    }

    public void setLength(int len) {
      if (len >= buffer.length) len = buffer.length - 1;
      if (len < 0) len = 0;
      this.length = len;
    }

    public void clear() {
      Arrays.fill(buffer, 0);
      index1 = index2 = index3 = index4 = index5 = index6 = index7 = index8 = 0;
    }
  }

  private static class StateVariable {
    public enum FilterType {
      LOWPASS,
      HIGHPASS,
      BANDPASS,
      NOTCH
    }

    private double sampleRate;
    private double frequency;
    private double q;
    private double f;

    private double low = 0;
    private double high = 0;
    private double band = 0;
    private double notch = 0;

    private FilterType type = FilterType.LOWPASS;
    private final int overSampleCount;

    public StateVariable(int overSampleCount) {
      this.overSampleCount = overSampleCount;
      setSampleRate(44100.0);
      setFrequency(1000.0);
      setResonance(0);
      setType(FilterType.LOWPASS);
      reset();
    }

    public double tick(double input) {
      for (int i = 0; i < overSampleCount; i++) {
        low += f * band + 1e-25;
        high = input - low - q * band;
        band += f * high;
        notch = low + high;
      }
      return switch (type) {
        case LOWPASS -> low;
        case HIGHPASS -> high;
        case BANDPASS -> band;
        case NOTCH -> notch;
      };
    }

    public void reset() {
      low = high = band = notch = 0;
    }

    public void setSampleRate(double sr) {
      this.sampleRate = sr * overSampleCount;
      updateCoefficient();
    }

    public void setFrequency(double freq) {
      this.frequency = freq;
      updateCoefficient();
    }

    public void setResonance(double res) {
      this.q = 2 - 2 * res;
    }

    public void setType(FilterType t) {
      this.type = t;
    }

    private void updateCoefficient() {
      f = 2.0 * Math.sin(Math.PI * frequency / sampleRate);
    }
  }

  // MVerb instance variables
  private final Allpass[] allpass = new Allpass[4];
  private final StaticAllpassFourTap[] allpassFourTap = new StaticAllpassFourTap[4];
  private final StateVariable[] bandwidthFilter = new StateVariable[2];
  private final StateVariable[] damping = new StateVariable[2];
  private final StaticDelayLine predelay;
  private final StaticDelayLineFourTap[] staticDelayLine = new StaticDelayLineFourTap[4];
  private final StaticDelayLineEightTap[] earlyReflectionsDelayLine =
      new StaticDelayLineEightTap[2];

  private double sampleRate = 44100.0;
  private double dampingFreq = 0.9;
  private double density1 = 0.5;
  private double density2 = 0.5;
  private double bandwidthFreq = 0.9;
  private double preDelayTime = 0.0;
  private double decay = 0.5;
  private double gain = 1.0;
  private double mix = 1.0;
  private double earlyMix = 1.0;
  private double size = 1.0;

  private double mixSmooth = 0.0;
  private double earlyLateSmooth = 0.0;
  private double bandwidthSmooth = 0.0;
  private double dampingSmooth = 0.0;
  private double predelaySmooth = 0.0;
  private double sizeSmooth = 0.0;
  private double densitySmooth = 0.0;
  private double decaySmooth = 0.0;

  private double previousLeftTank = 0.0;
  private double previousRightTank = 0.0;

  private int controlRate = 0;
  private int controlRateCounter = 0;

  public MVerb() {
    super();
    // Initialize arrays
    for (int i = 0; i < 4; i++) {
      allpass[i] = new Allpass(96000);
      allpassFourTap[i] = new StaticAllpassFourTap(96000);
      staticDelayLine[i] = new StaticDelayLineFourTap(96000);
    }
    for (int i = 0; i < 2; i++) {
      bandwidthFilter[i] = new StateVariable(4);
      damping[i] = new StateVariable(4);
      earlyReflectionsDelayLine[i] = new StaticDelayLineEightTap(96000);
    }
    predelay = new StaticDelayLine(96000);

    // Default values
    sampleRate = ChuckVM.CURRENT_VM.get().getSampleRate();
    dampingFreq = 0.9;
    bandwidthFreq = 0.9;
    decay = 0.5;
    gain = 1.0;
    mix = 1.0;
    size = 1.0;
    earlyMix = 1.0;
    previousLeftTank = 0.0;
    previousRightTank = 0.0;
    preDelayTime = 100.0 * (sampleRate / 1000.0);
    mixSmooth =
        earlyLateSmooth =
            bandwidthSmooth = dampingSmooth = predelaySmooth = sizeSmooth = decaySmooth = densitySmooth = 0.0;
    controlRate = (int) (sampleRate / 1000.0);
    controlRateCounter = 0;

    reset();
  }

  public void reset() {
    controlRateCounter = 0;
    for (int i = 0; i < 2; i++) {
      bandwidthFilter[i].setSampleRate(sampleRate);
      bandwidthFilter[i].reset();
      damping[i].setSampleRate(sampleRate);
      damping[i].reset();
    }
    predelay.clear();
    predelay.setLength((int) preDelayTime);

    for (int i = 0; i < 4; i++) {
      allpass[i].clear();
      allpassFourTap[i].clear();
      staticDelayLine[i].clear();
    }
    for (int i = 0; i < 2; i++) {
      earlyReflectionsDelayLine[i].clear();
    }

    allpass[0].setLength((int) (0.0048 * sampleRate));
    allpass[1].setLength((int) (0.0036 * sampleRate));
    allpass[2].setLength((int) (0.0127 * sampleRate));
    allpass[3].setLength((int) (0.0093 * sampleRate));

    allpass[0].setFeedback(0.75);
    allpass[1].setFeedback(0.75);
    allpass[2].setFeedback(0.625);
    allpass[3].setFeedback(0.625);

    updateSize();
  }

  private void updateSize() {
    allpassFourTap[0].setLength((int) (0.020 * sampleRate * size));
    allpassFourTap[1].setLength((int) (0.060 * sampleRate * size));
    allpassFourTap[2].setLength((int) (0.030 * sampleRate * size));
    allpassFourTap[3].setLength((int) (0.089 * sampleRate * size));

    allpassFourTap[0].setFeedback(density1);
    allpassFourTap[1].setFeedback(density2);
    allpassFourTap[2].setFeedback(density1);
    allpassFourTap[3].setFeedback(density2);

    allpassFourTap[0].setIndex(0, 0, 0, 0);
    allpassFourTap[1].setIndex(
        0, (int) (0.006 * sampleRate * size), (int) (0.041 * sampleRate * size), 0);
    allpassFourTap[2].setIndex(0, 0, 0, 0);
    allpassFourTap[3].setIndex(
        0, (int) (0.031 * sampleRate * size), (int) (0.011 * sampleRate * size), 0);

    staticDelayLine[0].setLength((int) (0.15 * sampleRate * size));
    staticDelayLine[1].setLength((int) (0.12 * sampleRate * size));
    staticDelayLine[2].setLength((int) (0.14 * sampleRate * size));
    staticDelayLine[3].setLength((int) (0.11 * sampleRate * size));

    staticDelayLine[0].setIndex(
        0,
        (int) (0.067 * sampleRate * size),
        (int) (0.011 * sampleRate * size),
        (int) (0.121 * sampleRate * size));
    staticDelayLine[1].setIndex(0, (int) (0.036 * sampleRate * size), (int) (0.089 * sampleRate * size), 0);
    staticDelayLine[2].setIndex(0, (int) (0.0089 * sampleRate * size), (int) (0.099 * sampleRate * size), 0);
    staticDelayLine[3].setIndex(0, (int) (0.067 * sampleRate * size), (int) (0.0041 * sampleRate * size), 0);

    earlyReflectionsDelayLine[0].setLength((int) (0.089 * sampleRate));
    earlyReflectionsDelayLine[0].setIndex(
        0,
        (int) (0.0199 * sampleRate),
        (int) (0.0219 * sampleRate),
        (int) (0.0354 * sampleRate),
        (int) (0.0389 * sampleRate),
        (int) (0.0414 * sampleRate),
        (int) (0.0692 * sampleRate),
        0);

    earlyReflectionsDelayLine[1].setLength((int) (0.069 * sampleRate));
    earlyReflectionsDelayLine[1].setIndex(
        0,
        (int) (0.0099 * sampleRate),
        (int) (0.011 * sampleRate),
        (int) (0.0182 * sampleRate),
        (int) (0.0189 * sampleRate),
        (int) (0.0213 * sampleRate),
        (int) (0.0431 * sampleRate),
        0);
  }

  public void setParameter(int index, double value) {
    switch (index) {
      case DAMPINGFREQ -> dampingFreq = 1.0 - value;
      case DENSITY -> density1 = value;
      case BANDWIDTHFREQ -> bandwidthFreq = value;
      case PREDELAY -> preDelayTime = value;
      case SIZE -> {
        size = 0.95 * value + 0.05;
        updateSize();
      }
      case DECAY -> decay = value;
      case GAIN -> gain = value;
      case MIX -> mix = value;
      case EARLYMIX -> earlyMix = value;
    }
  }

  public double getParameter(int index) {
    return switch (index) {
      case DAMPINGFREQ -> dampingFreq * 100.0;
      case DENSITY -> density1 * 100.0;
      case BANDWIDTHFREQ -> bandwidthFreq * 100.0;
      case PREDELAY -> preDelayTime * 100.0;
      case SIZE -> ((0.95 * size) + 0.05) * 100.0;
      case DECAY -> decay * 100.0;
      case GAIN -> gain * 100.0;
      case MIX -> mix * 100.0;
      case EARLYMIX -> earlyMix * 100.0;
      default -> 0.0;
    };
  }

  public void dampingFreq(float value) { setParameter(DAMPINGFREQ, value); }
  public float dampingFreq() { return (float) getParameter(DAMPINGFREQ); }

  public void density(float value) { setParameter(DENSITY, value); }
  public float density() { return (float) getParameter(DENSITY); }

  public void bandwidthFreq(float value) { setParameter(BANDWIDTHFREQ, value); }
  public float bandwidthFreq() { return (float) getParameter(BANDWIDTHFREQ); }

  public void predelay(float value) { setParameter(PREDELAY, value); }
  public float predelay() { return (float) getParameter(PREDELAY); }

  public void size(float value) { setParameter(SIZE, value); }
  public float size() { return (float) getParameter(SIZE); }

  public void decay(float value) { setParameter(DECAY, value); }
  public float decay() { return (float) getParameter(DECAY); }



  public void mix(float value) { setParameter(MIX, value); }
  public float mix() { return (float) getParameter(MIX); }

  public void earlyMix(float value) { setParameter(EARLYMIX, value); }
  public float earlyMix() { return (float) getParameter(EARLYMIX); }

  @Override
  protected void computeStereo(float left, float right, long systemTime) {
    double l = left;
    double r = right;

    double oneOverSampleFrames = 1.0; // Assuming sample-by-sample processing
    double MixDelta = (mix - mixSmooth) * oneOverSampleFrames;
    double EarlyLateDelta = (earlyMix - earlyLateSmooth) * oneOverSampleFrames;
    double BandwidthDelta = (((bandwidthFreq * 18400.0) + 100.0) - bandwidthSmooth) * oneOverSampleFrames;
    double DampingDelta = (((dampingFreq * 18400.0) + 100.0) - dampingSmooth) * oneOverSampleFrames;
    double PredelayDelta = ((preDelayTime * 200.0 * (sampleRate / 1000.0)) - predelaySmooth) * oneOverSampleFrames;
    double SizeDelta = (size - sizeSmooth) * oneOverSampleFrames;
    double DecayDelta = (((0.7995 * decay) + 0.005) - decaySmooth) * oneOverSampleFrames;
    double DensityDelta = (((0.7995 * density1) + 0.005) - densitySmooth) * oneOverSampleFrames;

    mixSmooth += MixDelta;
    earlyLateSmooth += EarlyLateDelta;
    bandwidthSmooth += BandwidthDelta;
    dampingSmooth += DampingDelta;
    predelaySmooth += PredelayDelta;
    sizeSmooth += SizeDelta;
    decaySmooth += DecayDelta;
    densitySmooth += DensityDelta;

    if (controlRateCounter >= controlRate) {
      controlRateCounter = 0;
      bandwidthFilter[0].setFrequency(bandwidthSmooth);
      bandwidthFilter[1].setFrequency(bandwidthSmooth);
      damping[0].setFrequency(dampingSmooth);
      damping[1].setFrequency(dampingSmooth);
    }
    controlRateCounter++;

    predelay.setLength((int) predelaySmooth);
    density2 = decaySmooth + 0.15;
    if (density2 > 0.5) density2 = 0.5;
    if (density2 < 0.25) density2 = 0.25;

    allpassFourTap[1].setFeedback(density2);
    allpassFourTap[3].setFeedback(density2);
    allpassFourTap[0].setFeedback(density1);
    allpassFourTap[2].setFeedback(density1);

    double bandwidthLeft = bandwidthFilter[0].tick(l);
    double bandwidthRight = bandwidthFilter[1].tick(r);

    double earlyReflectionsL =
        earlyReflectionsDelayLine[0].tick(bandwidthLeft * 0.5 + bandwidthRight * 0.3)
            + earlyReflectionsDelayLine[0].getIndex(2) * 0.6
            + earlyReflectionsDelayLine[0].getIndex(3) * 0.4
            + earlyReflectionsDelayLine[0].getIndex(4) * 0.3
            + earlyReflectionsDelayLine[0].getIndex(5) * 0.3
            + earlyReflectionsDelayLine[0].getIndex(6) * 0.1
            + earlyReflectionsDelayLine[0].getIndex(7) * 0.1
            + (bandwidthLeft * 0.4 + bandwidthRight * 0.2) * 0.5;

    double earlyReflectionsR =
        earlyReflectionsDelayLine[1].tick(bandwidthLeft * 0.3 + bandwidthRight * 0.5)
            + earlyReflectionsDelayLine[1].getIndex(2) * 0.6
            + earlyReflectionsDelayLine[1].getIndex(3) * 0.4
            + earlyReflectionsDelayLine[1].getIndex(4) * 0.3
            + earlyReflectionsDelayLine[1].getIndex(5) * 0.3
            + earlyReflectionsDelayLine[1].getIndex(6) * 0.1
            + earlyReflectionsDelayLine[1].getIndex(7) * 0.1
            + (bandwidthLeft * 0.2 + bandwidthRight * 0.4) * 0.5;

    double predelayMonoInput = predelay.tick((bandwidthRight + bandwidthLeft) * 0.5);
    double smearedInput = predelayMonoInput;
    for (int j = 0; j < 4; j++) smearedInput = allpass[j].tick(smearedInput);

    double leftTank = allpassFourTap[0].tick(smearedInput + previousRightTank);
    leftTank = staticDelayLine[0].tick(leftTank);
    leftTank = damping[0].tick(leftTank);
    leftTank = allpassFourTap[1].tick(leftTank);
    leftTank = staticDelayLine[1].tick(leftTank);

    double rightTank = allpassFourTap[2].tick(smearedInput + previousLeftTank);
    rightTank = staticDelayLine[2].tick(rightTank);
    rightTank = damping[1].tick(rightTank);
    rightTank = allpassFourTap[3].tick(rightTank);
    rightTank = staticDelayLine[3].tick(rightTank);

    previousLeftTank = leftTank * decaySmooth;
    previousRightTank = rightTank * decaySmooth;

    double accumulatorL =
        (0.6 * staticDelayLine[2].getIndex(1))
            + (0.6 * staticDelayLine[2].getIndex(2))
            - (0.6 * allpassFourTap[3].getIndex(1))
            + (0.6 * staticDelayLine[3].getIndex(1))
            - (0.6 * staticDelayLine[0].getIndex(1))
            - (0.6 * allpassFourTap[1].getIndex(1))
            - (0.6 * staticDelayLine[1].getIndex(1));

    double accumulatorR =
        (0.6 * staticDelayLine[0].getIndex(2))
            + (0.6 * staticDelayLine[0].getIndex(3))
            - (0.6 * allpassFourTap[1].getIndex(2))
            + (0.6 * staticDelayLine[1].getIndex(2))
            - (0.6 * staticDelayLine[2].getIndex(3))
            - (0.6 * allpassFourTap[3].getIndex(2))
            - (0.6 * staticDelayLine[3].getIndex(2));

    accumulatorL = ((accumulatorL * earlyMix) + ((1 - earlyMix) * earlyReflectionsL));
    accumulatorR = ((accumulatorR * earlyMix) + ((1 - earlyMix) * earlyReflectionsR));

    l = (l + mixSmooth * (accumulatorL - l)) * gain;
    r = (r + mixSmooth * (accumulatorR - r)) * gain;

    lastOutChannels[0] = (float) l;
    lastOutChannels[1] = (float) r;
  }

  @Override
  protected void computeStereo(float input, long systemTime) {
    computeStereo(input, input, systemTime);
  }
}
