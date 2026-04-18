package org.chuck.audio.osc;

import java.util.Random;
import org.chuck.audio.ChuckUGen;

/** A white noise generator UGen. */
public class Noise extends ChuckUGen {
  private final Random random = new Random();

  public Noise() {
    this(true);
  }

  public Noise(boolean autoRegister) {
    super(autoRegister);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Generate a random float between -1.0 and 1.0
    return input + (random.nextFloat() * 2.0f - 1.0f);
  }
}
