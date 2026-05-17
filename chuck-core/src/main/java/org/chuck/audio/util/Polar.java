package org.chuck.audio.util;

/** Encapsulates a polar value (magnitude and phase). */
public record Polar(float mag, float phase) {
  public float mag() { return mag; }
  public float phase() { return phase; }
  
  public float re() {
    return (float) (mag * Math.cos(phase));
  }

  public float im() {
    return (float) (mag * Math.sin(phase));
  }
  
  public Polar plus(Polar other) { return fromComplex(toComplex().plus(other.toComplex())); }
  public Polar minus(Polar other) { return fromComplex(toComplex().minus(other.toComplex())); }
  public Polar times(float val) { return new Polar(mag * val, phase); }
  public Polar div(float val) { return new Polar(mag / val, phase); }

  public Complex toComplex() { return new Complex(re(), im()); }
  public static Polar fromComplex(Complex c) { return new Polar(c.mag(), c.phase()); }
}
