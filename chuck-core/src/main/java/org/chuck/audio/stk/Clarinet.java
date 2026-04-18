package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.OneZero;
import org.chuck.audio.fx.DelayL;
import org.chuck.audio.osc.Noise;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.util.Envelope;
import org.chuck.audio.util.ReedTable;
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

  public Clarinet(float lowestFrequency, float sampleRate) {
    this.sampleRate = sampleRate;
    // Length for half-wavelength (stopped pipe)
    int length = (int) (0.5 * sampleRate / lowestFrequency + 1);
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
    // Stopped pipe: fundamental is 4 * length, but feedback loop is 2 * length.
    // We use 0.5 factor to match STK tuning.
    double delay = (0.5 * sampleRate / frequency) - 1.0;
    delayLine.setDelay(delay);
  }

  public void setFreq(double frequency) {
    freq(frequency);
  }

  @doc("Start a note with given volume/velocity.")
  public void noteOn(float velocity) {
    envelope.setRate(0.005f); // STK default attack rate
    envelope.setTarget(0.55f + (velocity * 0.30f)); // STK default mapping
    envelope.keyOn();
  }

  @doc("Stop the note.")
  public void noteOff(float velocity) {
    envelope.setRate(0.01f); // STK default release rate
    envelope.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    envelope.tick(systemTime);
    float breathPressure = envelope.getValue();

    // Add noise and vibrato to breath
    breathPressure += breathPressure * noiseGain * noise.tick(systemTime);
    breathPressure += breathPressure * vibratoGain * vibrato.tick(systemTime);

    // Calculate pressure difference across reed
    // STK uses -1.0 reflection at bell, plus low-pass filter
    float boreOutput = -delayLine.getLastOut();
    float filteredBore = filter.tick(boreOutput, systemTime);
    float pressureDiff = filteredBore - breathPressure;

    // Use reed table to calculate new bore input
    float out =
        delayLine.tick(breathPressure + pressureDiff * reedTable.tick(pressureDiff), systemTime);

    lastOut = out * outputGain;
    return lastOut;
  }
}
