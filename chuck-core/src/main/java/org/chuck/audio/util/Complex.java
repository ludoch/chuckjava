package org.chuck.audio.util;

/** Encapsulates a complex value (real and imaginary parts). */
public record Complex(float re, float im) {
  public float re() { return re; }
  public float im() { return im; }
  public float mag() { return magnitude(); }

  public float magnitude() {
    return (float) Math.sqrt(re * re + im * im);
  }

  public float phase() {
    return (float) Math.atan2(im, re);
  }

  public Complex plus(Complex other) { return new Complex(re + other.re, im + other.im); }
  public Complex minus(Complex other) { return new Complex(re - other.re, im - other.im); }
  public Complex times(float val) { return new Complex(re * val, im * val); }
  public Complex div(float val) { return new Complex(re / val, im / val); }
  public Complex times(Complex other) {
      return new Complex(re * other.re - im * other.im, re * other.im + im * other.re);
  }
  
  public static Complex fromPolar(float mag, float phase) {
      return new Complex((float)(mag * Math.cos(phase)), (float)(mag * Math.sin(phase)));
  }
}
