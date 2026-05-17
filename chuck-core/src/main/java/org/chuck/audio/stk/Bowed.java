package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OnePole;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.util.BowTable;

/** A bowed string physical model. */
public class Bowed extends ChuckUGen {
  private final DelayL neckDelay;
  private final DelayL bridgeDelay;
  private final BowTable bowTable;
  private final OnePole filter;

  @SuppressWarnings("unused")
  private float bowPressure = 0.0f;

  private float bowVelocity = 0.0f;
  private float vibratoFreq = 6.125f;
  private float vibratoGain = 0.0f;

  @SuppressWarnings("unused")
  private double freq = 220.0;

  private final float sampleRate;

  private double phase = 0.0;

  public Bowed(float sampleRate) {
    this.sampleRate = sampleRate;
    int maxDelay = (int) (sampleRate / 20.0); // Down to 20Hz
    neckDelay = new DelayL(maxDelay);
    bridgeDelay = new DelayL(maxDelay);
    bowTable = new BowTable();
    filter = new OnePole();
    filter.setPole(0.95f);

    setFreq(220.0);
  }

  public double setFreq(double f) {
    this.freq = f;
    double totalDelay = sampleRate / f;
    neckDelay.setDelay(totalDelay * 0.75); // Nut to bow
    bridgeDelay.setDelay(totalDelay * 0.25); // Bow to bridge
    return f;
  }

  public double freq(double f) {
      setFreq(f);
      return f;
  }

  public double bowPressure(double p) {
    this.bowPressure = (float) p;
    return p;
  }

  public double bowVelocity(double v) {
    this.bowVelocity = (float) v;
    return v;
  }

  public double vibratoFreq(double f) {
    this.vibratoFreq = (float) f;
    return f;
  }

  public double vibratoGain(double g) {
    this.vibratoGain = (float) g;
    return g;
  }
  
  public double startBowing(double v) {
      noteOn((float)v);
      return v;
  }
  
  public double stopBowing(double v) {
      noteOff((float)v);
      return v;
  }

  public void noteOn(float velocity) {
    bowVelocity = 0.05f + (velocity * 0.2f);
    bowPressure = 0.1f + (velocity * 0.1f);
  }

  public void noteOff(float velocity) {
    bowVelocity = 0.0f;
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Simple vibrato
    @SuppressWarnings("unused")
    double vibrato = Math.sin(phase) * vibratoGain;
    phase += 2.0 * Math.PI * vibratoFreq / sampleRate;
    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;

    // This is a simplified waveguide bow model
    float bridgeReflection = -bridgeDelay.tick(0.0f, systemTime); // Use tick(0) to pull last out
    float neckReflection = -filter.tick(neckDelay.tick(0.0f, systemTime), systemTime);

    float bowDiff = bowVelocity - (bridgeReflection + neckReflection);
    float newVel = bowDiff * bowTable.lookup(bowDiff);

    bridgeDelay.tick(neckReflection + newVel, systemTime);
    neckDelay.tick(bridgeReflection + newVel, systemTime);

    lastOut = bridgeReflection;
    return lastOut;
  }
}
