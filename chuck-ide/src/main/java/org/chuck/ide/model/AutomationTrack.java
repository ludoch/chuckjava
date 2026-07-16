package org.chuck.ide.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A time-stamped breakpoint automation track for a single UGen parameter or global variable.
 * Supports real-time recording, loop playback, and smooth linear/exponential interpolation.
 */
public class AutomationTrack {
  public record Breakpoint(double timeSamples, double value) {}

  private final List<Breakpoint> points = new ArrayList<>();
  private double loopDurationSamples = 44100.0 * 2.0; // Default 2 seconds at 44.1 kHz
  private boolean isLooping = true;

  public synchronized void addPoint(double timeSamples, double value) {
    // Clamp time inside loop duration if looping
    double rawT =
        isLooping && loopDurationSamples > 0 ? (timeSamples % loopDurationSamples) : timeSamples;
    final double t = rawT < 0 ? 0 : rawT;

    // Remove existing point if within close epsilon (5 ms)
    double eps = 44100.0 * 0.005;
    points.removeIf(p -> Math.abs(p.timeSamples - t) < eps);
    points.add(new Breakpoint(t, value));
    points.sort(Comparator.comparingDouble(Breakpoint::timeSamples));
  }

  public synchronized void clear() {
    points.clear();
  }

  public synchronized List<Breakpoint> getPoints() {
    return new ArrayList<>(points);
  }

  public synchronized double getLoopDurationSamples() {
    return loopDurationSamples;
  }

  public synchronized void setLoopDurationSamples(double dur) {
    this.loopDurationSamples = Math.max(100.0, dur);
  }

  public synchronized boolean isLooping() {
    return isLooping;
  }

  public synchronized void setLooping(boolean looping) {
    this.isLooping = looping;
  }

  /** Evaluates the automation curve at the given absolute VM sample time. */
  public synchronized double evaluate(double absTimeSamples, double defaultValue) {
    if (points.isEmpty()) return defaultValue;
    if (points.size() == 1) return points.get(0).value;

    double t =
        isLooping && loopDurationSamples > 0
            ? (absTimeSamples % loopDurationSamples)
            : absTimeSamples;
    if (t < 0) t = 0;

    // Before first breakpoint
    Breakpoint first = points.get(0);
    if (t <= first.timeSamples) {
      if (isLooping && points.size() > 1) {
        Breakpoint last = points.get(points.size() - 1);
        double dt = (first.timeSamples + loopDurationSamples) - last.timeSamples;
        if (dt > 0) {
          double alpha = (t + loopDurationSamples - last.timeSamples) / dt;
          return last.value + alpha * (first.value - last.value);
        }
      }
      return first.value;
    }

    // After last breakpoint
    Breakpoint last = points.get(points.size() - 1);
    if (t >= last.timeSamples) {
      if (isLooping && points.size() > 1) {
        double dt = (first.timeSamples + loopDurationSamples) - last.timeSamples;
        if (dt > 0) {
          double alpha = (t - last.timeSamples) / dt;
          return last.value + alpha * (first.value - last.value);
        }
      }
      return last.value;
    }

    // Binary search / linear interpolation between breakpoints
    for (int i = 0; i < points.size() - 1; i++) {
      Breakpoint p1 = points.get(i);
      Breakpoint p2 = points.get(i + 1);
      if (t >= p1.timeSamples && t <= p2.timeSamples) {
        double dt = p2.timeSamples - p1.timeSamples;
        if (dt <= 0) return p2.value;
        double alpha = (t - p1.timeSamples) / dt;
        return p1.value + alpha * (p2.value - p1.value);
      }
    }
    return defaultValue;
  }

  /** Generates a standard Sine LFO curve across the loop duration. */
  public synchronized void generateSineLFO(double minVal, double maxVal, double cycles) {
    clear();
    int steps = 64;
    for (int i = 0; i <= steps; i++) {
      double t = (i / (double) steps) * loopDurationSamples;
      double angle = (i / (double) steps) * cycles * 2.0 * Math.PI;
      double norm = (Math.sin(angle) + 1.0) * 0.5;
      addPoint(t, minVal + norm * (maxVal - minVal));
    }
  }

  /** Generates a Triangle LFO curve across the loop duration. */
  public synchronized void generateTriangleLFO(double minVal, double maxVal, double cycles) {
    clear();
    int steps = 64;
    for (int i = 0; i <= steps; i++) {
      double t = (i / (double) steps) * loopDurationSamples;
      double phase = ((i / (double) steps) * cycles) % 1.0;
      double norm = phase < 0.5 ? (phase * 2.0) : (2.0 - phase * 2.0);
      addPoint(t, minVal + norm * (maxVal - minVal));
    }
  }

  /** Generates a Ramp Up curve across the loop duration. */
  public synchronized void generateRampUp(double minVal, double maxVal) {
    clear();
    addPoint(0, minVal);
    addPoint(loopDurationSamples * 0.99, maxVal);
  }

  /** Generates a Ramp Down curve across the loop duration. */
  public synchronized void generateRampDown(double minVal, double maxVal) {
    clear();
    addPoint(0, maxVal);
    addPoint(loopDurationSamples * 0.99, minVal);
  }

  /** Generates Random Sample & Hold step breakpoints across the loop duration. */
  public synchronized void generateRandomSH(double minVal, double maxVal, int steps) {
    clear();
    for (int i = 0; i < steps; i++) {
      double t = (i / (double) steps) * loopDurationSamples;
      double val = minVal + Math.random() * (maxVal - minVal);
      addPoint(t, val);
    }
  }
}
