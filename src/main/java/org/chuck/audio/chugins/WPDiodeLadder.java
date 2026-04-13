package org.chuck.audio.chugins;

import org.chuck.audio.ChuckUGen;

/**
 * WPDiodeLadder — virtual analog diode ladder low-pass filter.
 *
 * <p>Direct Java port of Will Pirkle's VA Diode Ladder filter (Application Note AN-6). Uses 4
 * cascaded one-pole trapezoidal integrators with feedback correction to model the Moog-style diode
 * ladder topology.
 *
 * <p>Port of chugins/WPDiodeLadder by Owen Vallis.
 *
 * @see <a href="http://www.willpirkle.com/Downloads/AN-6DiodeLadderFilter.pdf">Pirkle AN-6</a>
 */
public class WPDiodeLadder extends ChuckUGen {
  private final float sampleRate;

  // One-pole trapezoidal integrator state
  private double z1_1, z1_2, z1_3, z1_4;

  // Filter coefficients (recomputed on cutoff/resonance change)
  private double alpha_1, alpha_2, alpha_3, alpha_4;
  private double beta_1, beta_2, beta_3, beta_4;
  private double gamma_1, gamma_2, gamma_3;
  private double delta_1, delta_2, delta_3;
  private double epsilon_1, epsilon_2, epsilon_3;
  private double a0_1, a0_2, a0_3;
  private double feedback_1, feedback_2, feedback_3;

  // Stage gain scalars (SG)
  private double GAMMA;
  private double SG1, SG2, SG3, SG4;

  // Parameters
  private double cutoff = 1000.0;
  private double resonance = 0.0; // K, [0..17]
  private double saturation = 1.0;
  private boolean nonlinear = false;
  private boolean nlpNorm = true; // true = normalized tanh

  public WPDiodeLadder(float sampleRate) {
    this.sampleRate = sampleRate;
    // a0 constants set in constructor per Pirkle
    a0_1 = 1.0;
    a0_2 = 0.5;
    a0_3 = 0.5;
    // LPF4 has a0=0.5 implicit, gamma=1, delta=0, epsilon=0, feedback=0
    updateFilter();
  }

  private double getFeedbackOutput(double z1, double beta, double feedback, double delta) {
    return beta * (z1 + feedback * delta);
  }

  private double doOnePole(
      double xn,
      double alpha,
      double gamma,
      double delta,
      double epsilon,
      double a0,
      double feedback,
      double[] z1ref,
      int idx) {
    double x_in =
        xn * gamma
            + feedback
            + epsilon * getFeedbackOutput(z1ref[idx], /* beta */ 1.0, feedback, delta);
    double vn = (a0 * x_in - z1ref[idx]) * alpha;
    double out = vn + z1ref[idx];
    z1ref[idx] = vn + out;
    return out;
  }

  private void updateFilter() {
    double wd = 2.0 * Math.PI * cutoff;
    double T = 1.0 / sampleRate;
    double wa = (2.0 / T) * Math.tan(wd * T / 2.0);
    double g = wa * T / 2.0;

    double G4 = 0.5 * g / (1.0 + g);
    double G3 = 0.5 * g / (1.0 + g - 0.5 * g * G4);
    double G2 = 0.5 * g / (1.0 + g - 0.5 * g * G3);
    double G1 = g / (1.0 + g - g * G2);

    GAMMA = G4 * G3 * G2 * G1;

    SG1 = G4 * G3 * G2;
    SG2 = G4 * G3;
    SG3 = G4;
    SG4 = 1.0;

    double alpha = g / (1.0 + g);
    alpha_1 = alpha_2 = alpha_3 = alpha_4 = alpha;

    beta_1 = 1.0 / (1.0 + g - g * G2);
    beta_2 = 1.0 / (1.0 + g - 0.5 * g * G3);
    beta_3 = 1.0 / (1.0 + g - 0.5 * g * G4);
    beta_4 = 1.0 / (1.0 + g);

    gamma_1 = 1.0 + G1 * G2;
    gamma_2 = 1.0 + G2 * G3;
    gamma_3 = 1.0 + G3 * G4;

    delta_1 = g;
    delta_2 = 0.5 * g;
    delta_3 = 0.5 * g;

    epsilon_1 = G2;
    epsilon_2 = G3;
    epsilon_3 = G4;
  }

  @Override
  protected float compute(float input, long systemTime) {
    double xn = input;

    // Feedback outputs from each stage's z1 state
    double fb4 = beta_4 * z1_4; // LPF4: delta=0, feedback=0
    double fb3 = beta_3 * (z1_3 + feedback_3 * delta_3); // LPF3
    double fb2 = beta_2 * (z1_2 + feedback_2 * delta_2); // LPF2
    double fb1 = beta_1 * (z1_1 + feedback_1 * delta_1); // LPF1

    // Set inter-stage feedbacks
    feedback_3 = fb4;
    feedback_2 = fb3;
    feedback_1 = fb2;

    double SIGMA = SG1 * fb1 + SG2 * fb2 + SG3 * fb3 + SG4 * fb4;

    // Optional nonlinear processing at input
    if (nonlinear) {
      if (nlpNorm) {
        xn = (1.0 / Math.tanh(saturation)) * Math.tanh(saturation * xn);
      } else {
        xn = Math.tanh(saturation * xn);
      }
    }

    double un = (xn - resonance * SIGMA) / (1.0 + resonance * GAMMA);

    // LPF1
    {
      double x_in = un * gamma_1 + feedback_1 + epsilon_1 * fb1;
      double vn = (a0_1 * x_in - z1_1) * alpha_1;
      double out = vn + z1_1;
      z1_1 = vn + out;
      un = out;
    }
    // LPF2
    {
      double x_in = un * gamma_2 + feedback_2 + epsilon_2 * fb2;
      double vn = (a0_2 * x_in - z1_2) * alpha_2;
      double out = vn + z1_2;
      z1_2 = vn + out;
      un = out;
    }
    // LPF3
    {
      double x_in = un * gamma_3 + feedback_3 + epsilon_3 * fb3;
      double vn = (a0_3 * x_in - z1_3) * alpha_3;
      double out = vn + z1_3;
      z1_3 = vn + out;
      un = out;
    }
    // LPF4 (gamma=1, delta=0, epsilon=0, feedback=0, a0=0.5)
    {
      double x_in = un; // gamma=1, no feedback terms
      double vn = (0.5 * x_in - z1_4) * alpha_4;
      double out = vn + z1_4;
      z1_4 = vn + out;
      un = out;
    }

    return (float) un;
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
    this.resonance = Math.max(0.0, Math.min(17.0, k));
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

  public int nlp_type(int norm) {
    this.nlpNorm = norm != 0;
    return norm;
  }

  public int nlp_type() {
    return nlpNorm ? 1 : 0;
  }
}
