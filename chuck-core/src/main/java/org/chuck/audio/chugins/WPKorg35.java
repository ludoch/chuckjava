package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * WPKorg35 — virtual analog Korg35 low-pass filter.
 *
 * <p>Direct Java port of Will Pirkle's VA Korg35 filter (Application Note AN-5). Uses 2 one-pole
 * LPF stages plus 1 HPF stage with trapezoidal integration, modeling the Korg MS-10/MS-20 filter.
 *
 * <p>Port of chugins/WPKorg35 by Owen Vallis.
 *
 * @see <a href="http://www.willpirkle.com/Downloads/AN-5Korg35_V3.pdf">Pirkle AN-5</a>
 */
public class WPKorg35 extends ChuckUGen {
  private final float sampleRate;

  // One-pole state variables
  private double z1_lpf1, z1_lpf2, z1_hpf;

  // Alpha (feedforward) coefficient, shared by all three filters
  private double alpha;

  // Beta feedback coefficients
  private double beta_lpf2, beta_hpf;

  // Alpha0: global scaling factor
  private double alpha0;

  // Parameters
  private double cutoff = 1000.0;
  private double resonance = 0.0; // K, [0..2)
  private double saturation = 1.0;
  private boolean nonlinear = false;

  public WPKorg35(float sampleRate) {
    this.sampleRate = sampleRate;
    updateFilter();
  }

  private void updateFilter() {
    double wd = 2.0 * Math.PI * cutoff;
    double T = 1.0 / sampleRate;
    double wa = (2.0 / T) * Math.tan(wd * T / 2.0);
    double g = wa * T / 2.0;
    double G = g / (1.0 + g);

    alpha = G;

    beta_lpf2 = (resonance - resonance * G) / (1.0 + g);
    beta_hpf = -1.0 / (1.0 + g);

    alpha0 = 1.0 / (1.0 - resonance * G + resonance * G * G);
  }

  /** One-pole LPF tick: returns LP output. */
  private double tickLPF(double xn, double[] z1ref) {
    double vn = (xn - z1ref[0]) * alpha;
    double out = vn + z1ref[0];
    z1ref[0] = vn + out;
    return out;
  }

  /** One-pole HPF tick: returns HP output (xn - LP output). */
  private double tickHPF(double xn, double[] z1ref) {
    double vn = (xn - z1ref[0]) * alpha;
    double lp = vn + z1ref[0];
    z1ref[0] = vn + lp;
    return xn - lp;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double xn = input;

    // Feedback outputs
    double fb_lpf2 = z1_lpf2 * beta_lpf2;
    double fb_hpf = z1_hpf * beta_hpf;

    // Process through LPF1
    double[] z1a = {z1_lpf1};
    double y1 = tickLPF(xn, z1a);
    z1_lpf1 = z1a[0];

    // Form feedback sum S35
    double S35 = fb_hpf + fb_lpf2;

    // Calculate u with global scale
    double u = alpha0 * (y1 + S35);

    // Optional nonlinear processing
    if (nonlinear) {
      u = Math.tanh(saturation * u);
    }

    // Feed u through LPF2
    double[] z1b = {z1_lpf2};
    double lpf2Out = tickLPF(u, z1b);
    z1_lpf2 = z1b[0];

    // Feed K*lpf2Out through HPF (for feedback path only)
    double[] z1c = {z1_hpf};
    tickHPF(resonance * lpf2Out, z1c);
    z1_hpf = z1c[0];

    return (float) lpf2Out;
  }

  public double cutoff(double hz) {
    this.cutoff = Math.max(1.0, Math.min(sampleRate / 2.0 - 1.0, hz));
    updateFilter();
    return this.cutoff;
  }

  public double cutoff() {
    return cutoff;
  }

  public double resonance(double k) {
    this.resonance = Math.max(0.0, Math.min(1.99, k));
    updateFilter();
    return this.resonance;
  }

  public double resonance() {
    return resonance;
  }

  public double saturation(double s) {
    this.saturation = s;
    return s;
  }

  public double saturation() {
    return saturation;
  }

  public int nonlinear(int enabled) {
    this.nonlinear = enabled != 0;
    return enabled;
  }

  public int nonlinear() {
    return nonlinear ? 1 : 0;
  }
}
