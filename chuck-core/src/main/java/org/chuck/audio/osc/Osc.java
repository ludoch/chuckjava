package org.chuck.audio.osc;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckType;

/** Base class for simple oscillator unit generators. Matches native ChucK quirks exactly. */
public abstract class Osc extends ChuckUGen {
  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  protected double freq = 220.0;

  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  protected double phase = 0.0;

  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  protected double width = 0.5;

  @SuppressWarnings("unused") // Used via introspection in ChucK scripts
  protected int sync = 0; // 0: sync freq, 1: sync phase, 2: FM

  protected final float sampleRate;
  protected double num = 0.0; // phase increment

  public Osc() {
    this(org.chuck.core.ChuckDSL.sampleRate());
  }

  public Osc(float sampleRate) {
    this(sampleRate, true);
  }

  public Osc(float sampleRate, boolean autoRegister) {
    super(new ChuckType("Osc", ChuckType.OBJECT, 8, 0), autoRegister);
    this.sampleRate = sampleRate;
    setFreq(220.0);
  }

  public void setFreq(double f) {
    this.freq = f;
    this.num = f / sampleRate;
    // Native quirk from osc_ctrl_freq (ugen_osc.cpp):
    if (this.num >= 1.0) this.num -= Math.floor(this.num);
  }

  public double freq(double f) {
    setFreq(f);
    return f;
  }

  public double freq() {
    return freq;
  }

  public double getFreq() {
    return freq;
  }

  public void setPhase(double p) {
    this.phase = p;
    // Replicate native quirk (typo) in osc_ctrl_phase:
    if (this.phase >= 1.0 || this.phase < 0.0) {
      this.phase -= Math.floor(this.num); // Yes, num, not phase!
    }
  }

  public double phase(double p) {
    setPhase(p);
    return this.phase;
  }

  public double phase() {
    return phase;
  }

  public double getPhase() {
    return phase;
  }

  public void setWidth(double width) {
    this.width = width;
  }

  public double width(double w) {
    this.width = w;
    return w;
  }

  public double width() {
    return width;
  }

  public double getWidth() {
    return width;
  }

  public void setSync(int sync) {
    this.sync = sync;
  }

  public int sync(int s) {
    this.sync = s;
    return s;
  }

  public int sync() {
    return sync;
  }

  public int getSync() {
    return sync;
  }

  public void init(double f) {
    setFreq(f);
  }

  public float last() {
    return lastOut;
  }

  @Override
  protected void triggerDataHook(int index, long value) {
    super.triggerDataHook(index, value);
    switch (index) {
      case 0 -> setFreq(getDataAsDouble(0));
      case 2 -> this.width = getDataAsDouble(2);
      case 3 -> setPhase(getDataAsDouble(3));
      default -> {}
    }
  }

  @Override
  public void setData(int index, long value) {
    super.setDataInternal(index, value);
    triggerDataHook(index, value);
  }

  @Override
  protected float compute(float in, long systemTime) {
    boolean inc_phase = true;
    double d_num = this.num;

    if (getNumSources() > 0) {
      switch (sync) {
        case 0 -> { // sync freq
          d_num = in / sampleRate;
          // Replicate native bug in sinosc_tick:
          if (d_num >= 1.0) d_num -= Math.floor(d_num);
          else if (d_num <= -1.0) d_num += Math.floor(d_num);
        }
        case 1 -> { // sync phase
          phase = in;
          inc_phase = false;
        }
        case 2 -> { // FM
          d_num = (freq + in) / sampleRate;
          // Replicate native bug in sinosc_tick:
          if (d_num >= 1.0) d_num -= Math.floor(d_num);
          else if (d_num <= -1.0) d_num += Math.floor(d_num);
        }
      }
    }

    // Native ChucK samples BEFORE increment
    float out = (float) computeOsc(phase);

    if (inc_phase) {
      phase += d_num;
      // Normal wrapping in tick
      if (phase >= 1.0 || phase < 0.0) phase -= Math.floor(phase);
    }

    return out;
  }

  /** Re-added for BlitSaw/BlitSquare compatibility. */
  protected static jdk.incubator.vector.FloatVector vPolyBlep(
      jdk.incubator.vector.FloatVector vT, jdk.incubator.vector.FloatVector vDt) {
    var species = vT.species();
    var vZero = jdk.incubator.vector.FloatVector.zero(species);
    var vOne = jdk.incubator.vector.FloatVector.broadcast(species, 1.0f);
    var mask1 = vT.compare(jdk.incubator.vector.VectorOperators.LT, vDt);
    var vT1_1 = vT.div(vDt.add(1e-9f));
    var vRes1 = vT1_1.add(vT1_1).sub(vT1_1.mul(vT1_1)).sub(vOne);
    var mask2 = vT.compare(jdk.incubator.vector.VectorOperators.GT, vOne.sub(vDt));
    var vT1_2 = vT.sub(vOne).div(vDt.add(1e-9f));
    var vRes2 = vT1_2.mul(vT1_2).add(vT1_2).add(vT1_2).add(vOne);
    return vZero.blend(vRes1, mask1).blend(vRes2, mask2);
  }

  protected abstract double computeOsc(double phase);

  protected static double polyBlep(double t, double dt) {
    if (dt <= 0.0) return 0.0;
    if (t < dt) {
      double t1 = t / dt;
      return t1 + t1 - t1 * t1 - 1.0;
    } else if (t > 1.0 - dt) {
      double t1 = (t - 1.0) / dt;
      return t1 * t1 + t1 + t1 + 1.0;
    }
    return 0.0;
  }
}
