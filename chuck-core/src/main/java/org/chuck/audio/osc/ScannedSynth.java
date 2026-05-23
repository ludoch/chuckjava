package org.chuck.audio.osc;

import org.chuck.audio.ChuckUGen;
import org.chuck.core.doc;

/**
 * ScannedSynth: High-fidelity physical modeling Scanned Synthesis UGen. Ported from Csound's scans
 * / scanu scanned synthesis suite (Sean Costello / Istvan Varga).
 *
 * <p>Simulates a 1D mass-spring-damper physical string network slow-rate, and scans the dynamic
 * shapes at high audio rates to generate physically organic timbres.
 */
@doc("High-fidelity physical modeling Scanned Synthesis generator UGen.")
public class ScannedSynth extends ChuckUGen {

  private static final double DEFAULT_SRATE = 44100.0;

  // Gaussian pluck table
  private static final float[] DEFAULT_PLUCK_TABLE = {
    0.005f, 0.030f, 0.100f, 0.250f, 0.500f, 0.800f, 0.950f, 1.000f,
    0.950f, 0.800f, 0.500f, 0.250f, 0.100f, 0.030f, 0.005f, 0.000f
  };

  // Mass string physics arrays
  private final int len = 128; // Standard 128 masses string!
  private float[] x0 = new float[len]; // Future position
  private float[] x1 = new float[len]; // Current position
  private float[] x2 = new float[len]; // Previous position
  private float[] v = new float[len]; // Velocity

  // Mass, damping, centering, and connection arrays
  private float[] m = new float[len];
  private float[] c = new float[len];
  private float[] d = new float[len];
  private float[] f = new float[len * len]; // Spring connection weight matrix

  // Trajectory map
  private int[] trajectoryMap = new int[len];
  private int trajectoryLength = len;

  // Coefficients
  private double kStiffness = 0.1; // k_f
  private double kCentering = 0.01; // k_c
  private double kDamping = 0.05; // k_d
  private double kMass = 1.0; // k_m

  private double updateRate = 500.0; // Slow physics solver step rate (Hz)
  private double freq = 261.63; // Scan fundamental frequency pitch (Hz)
  private double amp = 0.5; // Output volume scaler

  private double slowStepAccumulator = 0.0;
  private double scanPhase = 0.0;
  private double sampleRate = 44100.0;

  public ScannedSynth() {
    super();
    this.sampleRate =
        org.chuck.core.ChuckVM.CURRENT_VM.isBound()
            ? org.chuck.core.ChuckVM.CURRENT_VM.get().getSampleRate()
            : DEFAULT_SRATE;

    // Initialize regular 1D clamped string physics profiles
    for (int i = 0; i < len; i++) {
      m[i] = 1.0f;
      c[i] = 0.1f;
      d[i] = 0.05f;
      trajectoryMap[i] = i; // simple sequential trajectory
    }

    // Connect neighbors with regular string spring stiffnesses
    for (int i = 0; i < len; i++) {
      if (i > 0) {
        f[i * len + (i - 1)] = 1.0f;
      }
      if (i < len - 1) {
        f[i * len + (i + 1)] = 1.0f;
      }
    }

    // Trigger initial plucks at center to populate starting positions
    pluck(0.5, 0.5);
  }

  @doc("Set output fundamental pitch frequency (Hz).")
  public void freq(float f) {
    this.freq = Math.max(1.0, f);
  }

  public float freq() {
    return (float) freq;
  }

  @doc("Set global spring stiffness coefficient (0.0 to 1.0). Controls sound brightness.")
  public void stiffness(float s) {
    this.kStiffness = Math.max(0.0, s);
  }

  public float stiffness() {
    return (float) kStiffness;
  }

  @doc("Set global tension centering force coefficient. Controls pitch decay rates.")
  public void centering(float c) {
    this.kCentering = Math.max(0.0, c);
  }

  public float centering() {
    return (float) kCentering;
  }

  @doc("Set global friction damping coefficient. Controls sound decay time.")
  public void damping(float d) {
    this.kDamping = Math.max(0.0, d);
  }

  public float damping() {
    return (float) kDamping;
  }

  @doc("Set global mass coefficient. Scales inertia response times.")
  public void mass(float m) {
    this.kMass = Math.max(0.001, m);
  }

  public float mass() {
    return (float) kMass;
  }

  @doc("Set physics solver update frequency rate (Hz) (typically 100 to 1000 Hz).")
  public void updateRate(float rate) {
    this.updateRate = Math.max(1.0, rate);
  }

  public float updateRate() {
    return (float) updateRate;
  }

  @doc("Set output volume amplitude.")
  public void amp(float a) {
    this.amp = Math.max(0.0, a);
  }

  public float amp() {
    return (float) amp;
  }

  @doc("Set dynamic trajectory map indices for custom scan routes (spirals, zigzags).")
  public void trajectory(int[] map) {
    if (map == null || map.length == 0) return;
    int length = Math.min(len, map.length);
    this.trajectoryMap = new int[length];
    for (int i = 0; i < length; i++) {
      this.trajectoryMap[i] = Math.max(0, Math.min(len - 1, map[i]));
    }
    this.trajectoryLength = length;
    this.scanPhase = 0.0;
  }

  @doc(
      "Excites the dynamic physical string with a hammer pluck at position (0.0 to 1.0) and force.")
  public void pluck(double position, double force) {
    double pos = Math.max(0.0, Math.min(1.0, position));
    int center = (int) (len * pos);
    int halfSize = DEFAULT_PLUCK_TABLE.length / 2;
    for (int i = 0; i < DEFAULT_PLUCK_TABLE.length; i++) {
      int targetIdx = center - halfSize + i;
      if (targetIdx >= 0 && targetIdx < len) {
        x1[targetIdx] += (float) (force * DEFAULT_PLUCK_TABLE[i]);
        x2[targetIdx] +=
            (float) (force * DEFAULT_PLUCK_TABLE[i]); // Align state to prevent step velocities
      }
    }
  }

  private void solveOdeStep() {
    double dt = 1.0;
    for (int i = 0; i < len; i++) {
      double a = 0.0;

      // Sum neighbors spring forces
      for (int j = 0; j < len; j++) {
        float weight = f[i * len + j];
        if (weight != 0.0f) {
          a += (x1[j] - x1[i]) * weight * kStiffness;
        }
      }

      // Add centering spring force (clamped tension)
      a += -x1[i] * c[i] * kCentering;

      // Add velocity-damping force
      a += -(x1[i] - x2[i]) * d[i] * kDamping;

      // Divide by mass inertia
      a /= m[i] * kMass;

      // Euler integration
      v[i] += a * dt;
      x0[i] += v[i] * dt;
    }

    // Shift memory time order
    float[] tmp = x2;
    x2 = x1;
    x1 = x0;
    x0 = tmp;

    // Keep memory shapes synchronized
    System.arraycopy(x1, 0, x0, 0, len);
  }

  @Override
  protected float compute(float input, long systemTime) {
    // 1. Advance slow physical simulation
    double stepSize = updateRate / sampleRate;
    slowStepAccumulator += stepSize;
    while (slowStepAccumulator >= 1.0) {
      solveOdeStep();
      slowStepAccumulator -= 1.0;
    }

    double t = slowStepAccumulator;

    // 2. Scan positions at fundamental frequency pitch (wrap around trajectory mapping)
    double scanInc = freq / sampleRate * trajectoryLength;
    scanPhase += scanInc;
    while (scanPhase >= trajectoryLength) {
      scanPhase -= trajectoryLength;
    }
    while (scanPhase < 0) {
      scanPhase += trajectoryLength;
    }

    // 3. Linear time/space interpolation between steps
    int p1 = (int) scanPhase;
    int p2 = (p1 + 1) % trajectoryLength;
    double frac = scanPhase - p1;

    // Interpolate space values across current and previous physics states
    double y1 = x2[trajectoryMap[p1]] + (x1[trajectoryMap[p1]] - x2[trajectoryMap[p1]]) * t;
    double y2 = x2[trajectoryMap[p2]] + (x1[trajectoryMap[p2]] - x2[trajectoryMap[p2]]) * t;

    double outSample = amp * (y1 + frac * (y2 - y1));

    return (float) (input + outSample);
  }
}
