package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.filter.PoleZero;
import org.chuck.audio.osc.Noise;
import org.chuck.audio.osc.SinOsc;
import org.chuck.audio.stk.util.StkBiQuad;
import org.chuck.audio.util.Adsr;
import org.chuck.audio.util.JetTabl;
import org.chuck.core.doc;

/**
 * BlowBotl — STK blown-bottle ("pop bottle") physical model. Faithful port of the STK/ChucK
 * {@code BlowBotl}: a {@link StkBiQuad} Helmholtz resonator excited by breath pressure plus a
 * pressure-dependent noise term and the {@link JetTabl} non-linearity, output DC-blocked.
 */
@doc("Blown bottle physical model based on STK. Ported to Java 25.")
public class BlowBotl extends ChuckUGen {
  private static final double BOTTLE_RADIUS = 0.999;

  private final StkBiQuad resonator;
  private final JetTabl jetTable;
  private final PoleZero dcBlock;
  private final SinOsc vibrato;
  private final Noise noise;
  private final Adsr adsr;

  private final float sampleRate;
  private float noiseGain = 20.0f;
  private float vibratoGain = 0.0f;
  private float maxPressure = 0.0f;
  private float outputGain = 1.0f;

  public BlowBotl(float sampleRate) {
    this.sampleRate = sampleRate;
    this.jetTable = new JetTabl(false);
    this.dcBlock = new PoleZero(false);
    this.dcBlock.setBlockZero(0.99);
    this.vibrato = new SinOsc(sampleRate, false);
    this.vibrato.setFreq(5.925);
    this.resonator = new StkBiQuad(sampleRate);
    this.resonator.setResonance(500.0, BOTTLE_RADIUS, true);
    this.noise = new Noise(false);
    this.adsr = new Adsr(sampleRate, false);
    this.adsr.set(0.005f, 0.01f, 0.8f, 0.010f);
  }

  @doc("Set the bottle resonance frequency in Hz.")
  public void freq(double frequency) {
    setFreq(frequency);
  }

  public void setFreq(double frequency) {
    double f = frequency <= 0.0 ? 220.0 : frequency;
    resonator.setResonance(f, BOTTLE_RADIUS, true);
  }

  @doc("Start a note with given volume/velocity.")
  public void noteOn(float velocity) {
    // STK noteOn -> startBlowing(1.1 + amp*0.20, amp*0.02); outputGain = amp + 0.001.
    maxPressure = 1.1f + velocity * 0.20f;
    float rate = velocity * 0.02f;
    adsr.attackTime(rate > 0 ? 1.0 / rate : 1.0); // setAttackRate(rate)
    adsr.keyOn();
    outputGain = velocity + 0.001f;
  }

  @doc("Stop the note.")
  public void noteOff(float velocity) {
    float rate = velocity * 0.02f;
    adsr.releaseTime(rate > 0 ? 1.0 / rate : 1.0);
    adsr.keyOff();
  }

  @Override
  protected float compute(float input, long systemTime) {
    float breathPressure = maxPressure * adsr.tick(systemTime);
    breathPressure += vibratoGain * vibrato.tick(systemTime); // vibratoGain == 0 by default

    double pressureDiff = breathPressure - resonator.lastOut();

    double randPressure = noiseGain * noise.tick(systemTime);
    randPressure *= breathPressure;
    randPressure *= (1.0 + pressureDiff);

    resonator.tick(
        breathPressure + randPressure - (jetTable.tick((float) pressureDiff) * pressureDiff));
    lastOut = (float) (0.2 * outputGain * dcBlock.tick((float) pressureDiff, systemTime));
    return lastOut;
  }
}
