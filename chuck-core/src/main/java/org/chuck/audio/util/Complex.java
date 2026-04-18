package org.chuck.audio.util;

/** Encapsulates a complex value (real and imaginary parts). */
public record Complex(float re, float im) {
  public float magnitude() {
    return (float) Math.sqrt(re * re + im * im);
  }

  public float phase() {
    return (float) Math.atan2(im, re);
  }
}
