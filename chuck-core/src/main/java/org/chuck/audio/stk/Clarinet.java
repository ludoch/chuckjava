package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OneZero;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.osc.Noise;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Envelope;
import org.chuck.audio.util.ReedTable;
import org.chuck.core.ChuckVM;
import org.chuck.core.doc;

/**
 * Clarinet: A multi-mode clarinet physical model. Based on the Synthesis ToolKit (STK) C++
 * implementation.
 */
@doc("Clarinet physical model based on STK. Ported to Java 25.")
public class Clarinet extends ChuckUGen {
  private final DelayL delayLine;
  private final ReedTable reedTable;
  private final OneZero filter;
  private final Envelope envelope;
  private final Noise noise;
  private final SinOsc vibrato;

  private float noiseGain = 0.2f;
  private float vibratoGain = 0.1f;
  private float outputGain = 1.0f;
  private final float sampleRate;
  private final int length;

  public Clarinet() {
    this(50.0f, ChuckVM.CURRENT_VM.get().getSampleRate());
  }

  public Clarinet(float lowestFrequency, float sampleRate) {
    this.sampleRate = sampleRate;
    // Full bore length (STK: sampleRate/lowestFrequency + 1); the loop delay is a half-wavelength.
    this.length = (int) (sampleRate / lowestFrequency + 1);
    this.delayLine = new DelayL(length, sampleRate, false);
    this.reedTable = new ReedTable(false);
    this.reedTable.setOffset(0.7f); // STK default
    this.reedTable.setSlope(-0.3f); // STK default

    this.filter = new OneZero(false);
    this.envelope = new Envelope(sampleRate, false);
    this.noise = new Noise(false);
    this.vibrato = new SinOsc(sampleRate, false);
    this.vibrato.setFreq(5.735);

    filter.setB0(0.5f);
    filter.setB1(0.5f);
  }

  @doc("Set the clarinet frequency in Hz.")
  public void freq(double frequency) {
    double freakency = frequency <= 0.0 ? 220.0 : frequency;
    // STK: half-wavelength delay minus the OneZero filter's group delay (1.5 samples).
    double delay = (sampleRate / freakency) * 0.5 - 1.5;
    if (delay <= 0.0) delay = 0.3;
    else if (delay > length) delay = length;
    delayLine.setDelay(delay);
  }

  public void setFreq(double frequency) {
    freq(frequency);
  }

  @doc("Start a note with given volume/velocity.")
  public void noteOn(float velocity) {
    // STK Clarinet::noteOn -> startBlowing(0.55 + amp*0.30, amp*0.005); outputGain = amp + 0.001.
    // NOTE: do NOT call envelope.keyOn() — it would overwrite the breath target with 1.0.
    envelope.setRate(velocity * 0.005f);
    envelope.setTarget(0.55f + (velocity * 0.30f));
    outputGain = velocity + 0.001f;
  }

  @doc("Stop the note.")
  public void noteOff(float velocity) {
    // STK Clarinet::noteOff -> stopBlowing(amp*0.01): ramp breath target to 0.
    envelope.setRate(velocity * 0.01f);
    envelope.setTarget(0.0f);
  }

  @Override
  protected float compute(float input, long systemTime) {
    envelope.tick(systemTime);
    float breathPressure = envelope.getValue();

    // Add noise and vibrato to breath
    breathPressure += breathPressure * noiseGain * noise.tick(systemTime);
    breathPressure += breathPressure * vibratoGain * vibrato.tick(systemTime);

    // Commuted loss filtering: STK reflects with -0.95 * OneZero(delayLine.lastOut()).
    float filteredBore = filter.tick(delayLine.getLastOut(), systemTime);
    float pressureDiff = -0.95f * filteredBore - breathPressure;

    // Use reed table to calculate new bore input
    float out =
        delayLine.tick(breathPressure + pressureDiff * reedTable.tick(pressureDiff), systemTime);

    lastOut = out * outputGain;
    return lastOut;
  }
}
