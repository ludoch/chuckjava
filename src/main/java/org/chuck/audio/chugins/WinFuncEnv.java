package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * WinFuncEnv — envelope with interchangeable window function shapes.
 *
 * <p>Attack phase uses the rising half of the selected window; release uses the falling half.
 * Available shapes: Hann (default), Blackman, BlackmanHarris, BlackmanNuttall, Nuttall,
 * Exponential, HannPoisson, Parzen, Poisson, Tukey, Welch, Sigmoid.
 *
 * <p>Port of chugins/WinFuncEnv.
 */
public class WinFuncEnv extends ChuckUGen {
  // Window type enum
  private enum WinType {
    HANN,
    BLACKMAN,
    BLACKMAN_DERIVATIVE,
    EXPONENTIAL,
    HANN_POISSON,
    PARZEN,
    POISSON,
    TUKEY,
    SIGMOID,
    WELCH
  }

  private WinType winType = WinType.HANN;
  private final double[] winParams = new double[4]; // a0,a1,a2,a3 or single param

  private boolean keyOn = false;
  private boolean keyOff = false;
  private int n = 0;
  private double attack = 0;
  private double release = 0;
  private double currentLevel = 0;
  private double keyOnLevel = 0;
  private double keyOffLevel = 0;

  // Pre-computed per-stage
  private double twoPiVal, fourPiVal, sixPiVal, N_inv;

  @Override
  protected float compute(float input, long systemTime) {
    if (keyOn) {
      if (n < attack) {
        double w = attackWindow(n, (int) attack);
        currentLevel = w * (1.0 - keyOnLevel) + keyOnLevel;
        n++;
        return (float) (input * currentLevel);
      } else {
        return input;
      }
    }
    if (keyOff) {
      if (n < release) {
        double w = releaseWindow(n, (int) release);
        currentLevel = w * keyOffLevel;
        n++;
        return (float) (input * currentLevel);
      } else {
        return 0.0f;
      }
    }
    return input;
  }

  private double attackWindow(int n, int attack) {
    if (n == 0) {
      N_inv = 1.0 / (attack * 2.0);
      twoPiVal = 2.0 * Math.PI * N_inv;
      fourPiVal = 4.0 * Math.PI * N_inv;
      sixPiVal = 6.0 * Math.PI * N_inv;
    }
    return switch (winType) {
      case HANN -> n == 0 ? 0.0 : 0.5 * (1.0 - Math.cos(twoPiVal * n));
      case BLACKMAN ->
          winParams[0]
              - winParams[1] * Math.cos(twoPiVal * n)
              + winParams[2] * Math.cos(fourPiVal * n);
      case BLACKMAN_DERIVATIVE ->
          winParams[0]
              - winParams[1] * Math.cos(twoPiVal * n)
              + winParams[2] * Math.cos(fourPiVal * n)
              - winParams[3] * Math.cos(sixPiVal * n);
      case EXPONENTIAL -> {
        double t_inv = 1.0 / (attack * winParams[0]);
        yield Math.exp(-Math.abs(n - attack) * t_inv);
      }
      case HANN_POISSON -> {
        double ni = 1.0 / attack;
        int nr = attack - n;
        yield 0.5 * (1.0 + Math.cos(Math.PI * nr * ni)) * Math.exp(-winParams[0] * nr * ni);
      }
      case PARZEN -> {
        double ni = 1.0 / attack;
        int nr = attack - n - 1;
        if (nr < attack * 0.5) yield 1.0 - 6.0 * Math.pow(nr * ni, 2) * (1.0 - nr * ni);
        else yield 2.0 * Math.pow(1.0 - nr * ni, 3);
      }
      case POISSON -> {
        double ni = 1.0 / attack;
        int nr = attack - n;
        yield Math.exp(-winParams[0] * nr * ni);
      }
      case TUKEY -> {
        if (n < winParams[0] * attack)
          yield 0.5 * (1.0 + Math.cos(Math.PI * ((2.0 * n) / (winParams[0] * attack * 2.0) - 1.0)));
        else yield 1.0;
      }
      case SIGMOID -> {
        double k = winParams[0];
        double ni = 1.0 / attack;
        yield 1.0 / (1.0 + Math.exp(-k * (n * ni - 0.5)));
      }
      case WELCH -> {
        double ni = 1.0 / attack;
        double t = n * ni - 0.5;
        yield 1.0 - 4.0 * t * t;
      }
    };
  }

  private double releaseWindow(int n, int release) {
    if (n == 0) {
      N_inv = 1.0 / (release * 2.0);
      twoPiVal = 2.0 * Math.PI * N_inv;
      fourPiVal = 4.0 * Math.PI * N_inv;
      sixPiVal = 6.0 * Math.PI * N_inv;
    }
    int nr = n + release;
    return switch (winType) {
      case HANN -> 0.5 * (1.0 - Math.cos(twoPiVal * nr));
      case BLACKMAN ->
          winParams[0]
              - winParams[1] * Math.cos(twoPiVal * nr)
              + winParams[2] * Math.cos(fourPiVal * nr);
      case BLACKMAN_DERIVATIVE ->
          winParams[0]
              - winParams[1] * Math.cos(twoPiVal * nr)
              + winParams[2] * Math.cos(fourPiVal * nr)
              - winParams[3] * Math.cos(sixPiVal * nr);
      case EXPONENTIAL -> {
        double t_inv = 1.0 / (release * winParams[0]);
        yield n == 0 ? 1.0 : Math.exp(-Math.abs(n) * t_inv);
      }
      case HANN_POISSON -> {
        double ni = 1.0 / release;
        yield 0.5 * (1.0 + Math.cos(Math.PI * n * ni)) * Math.exp(-winParams[0] * n * ni);
      }
      case PARZEN -> {
        double ni = 1.0 / release;
        if (n < release * 0.5) yield 1.0 - 6.0 * Math.pow(n * ni, 2) * (1.0 - n * ni);
        else yield 2.0 * Math.pow(1.0 - n * ni, 3);
      }
      case POISSON -> {
        double ni = 1.0 / release;
        yield Math.exp(-winParams[0] * n * ni);
      }
      case TUKEY -> {
        if (n < winParams[0] * release)
          yield 0.5
              * (1.0 + Math.cos(Math.PI * ((2.0 * n) / (winParams[0] * release * 2.0) - 1.0)));
        else yield 1.0;
      }
      case SIGMOID -> {
        double k = winParams[0];
        double ni = 1.0 / release;
        yield 1.0 - 1.0 / (1.0 + Math.exp(-k * (n * ni - 0.5)));
      }
      case WELCH -> {
        double ni = 1.0 / release;
        double t = n * ni - 0.5;
        yield Math.max(0.0, 1.0 - 4.0 * t * t);
      }
    };
  }

  public int keyOn() {
    keyOnLevel = currentLevel;
    n = 0;
    keyOn = true;
    keyOff = false;
    return 1;
  }

  public int keyOn(int ignored) {
    return keyOn();
  }

  public int keyOff() {
    keyOffLevel = currentLevel;
    n = 0;
    keyOn = false;
    keyOff = true;
    return 1;
  }

  public int keyOff(int ignored) {
    return keyOff();
  }

  public double attack(double samps) {
    keyOnLevel = currentLevel;
    n = 0;
    attack = samps;
    return samps;
  }

  public double attack() {
    return attack;
  }

  public double release(double samps) {
    keyOffLevel = currentLevel;
    n = 0;
    release = samps;
    return samps;
  }

  public double release() {
    return release;
  }

  // --- Window shape setters ---

  public void setHann() {
    winType = WinType.HANN;
  }

  public void setBlackman() {
    setBlackman(0.16);
  }

  public void setBlackman(double a) {
    winType = WinType.BLACKMAN;
    winParams[0] = (1.0 - a) / 2.0;
    winParams[1] = 0.5;
    winParams[2] = a / 2.0;
  }

  public void setBlackmanHarris() {
    setBlackmanDerivative(0.35875, 0.48829, 0.14128, 0.01168);
  }

  public void setBlackmanNuttall() {
    setBlackmanDerivative(0.3635819, 0.4891775, 0.1365995, 0.0106411);
  }

  public void setNuttall() {
    setBlackmanDerivative(0.355768, 0.487396, 0.144232, 0.012604);
  }

  public void setBlackmanDerivative(double a0, double a1, double a2, double a3) {
    winType = WinType.BLACKMAN_DERIVATIVE;
    winParams[0] = a0;
    winParams[1] = a1;
    winParams[2] = a2;
    winParams[3] = a3;
  }

  public void setExponential() {
    winType = WinType.EXPONENTIAL;
    winParams[0] = 8.69 / 60.0;
  }

  public void setExponential(double a) {
    winType = WinType.EXPONENTIAL;
    winParams[0] = a;
  }

  public void setHannPoisson() {
    winType = WinType.HANN_POISSON;
    winParams[0] = 0.5;
  }

  public void setHannPoisson(double a) {
    winType = WinType.HANN_POISSON;
    winParams[0] = a;
  }

  public void setParzen() {
    winType = WinType.PARZEN;
  }

  public void setPoisson() {
    winType = WinType.POISSON;
    winParams[0] = 6.0;
  }

  public void setPoisson(double a) {
    winType = WinType.POISSON;
    winParams[0] = a;
  }

  public void setTukey() {
    winType = WinType.TUKEY;
    winParams[0] = 0.5;
  }

  public void setTukey(double a) {
    winType = WinType.TUKEY;
    winParams[0] = a;
  }

  public void setSigmoid() {
    winType = WinType.SIGMOID;
    winParams[0] = 2.0;
  }

  public void setSigmoid(double k) {
    winType = WinType.SIGMOID;
    winParams[0] = k;
  }

  public void setWelch() {
    winType = WinType.WELCH;
  }
}
