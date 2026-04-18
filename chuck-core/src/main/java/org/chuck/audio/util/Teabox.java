package org.chuck.audio.util;

import org.chuck.audio.filter.FilterBasic;

/**
 * Teabox — Hardware sensor interface UGen. This is a stub implementation that always returns 0. In
 * ChucK, this UGen interacts with specific external hardware.
 */
public class Teabox extends FilterBasic {
  public Teabox(float sampleRate) {
    super(sampleRate);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Teabox returns sensor data; 0.0 if no hardware connected.
    return 0.0f;
  }
}
