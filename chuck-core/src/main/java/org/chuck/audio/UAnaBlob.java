package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.util.Complex;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckType;

/** Encapsulates Unit Analyzer (UAna) results. */
public class UAnaBlob {
  private List<Complex> cvals = new ArrayList<>();
  private float[] fvals = new float[0];
  private float[] pvals = new float[0];
  private long when; // logical time of result

  public void setCvals(List<Complex> cvals) {
    this.cvals = cvals;
    int size = cvals.size();
    this.fvals = new float[size];
    this.pvals = new float[size];
    for (int i = 0; i < size; i++) {
      Complex c = cvals.get(i);
      fvals[i] = c.magnitude();
      pvals[i] = c.phase();
    }
  }

  public void setFvals(float[] fvals) {
    this.fvals = fvals;
  }

  public List<Complex> getCvals() {
    return cvals;
  }

  public float[] getFvals() {
    return fvals;
  }

  public float[] getPvals() {
    return pvals;
  }

  public void setWhen(long when) {
    this.when = when;
  }

  public long getWhen() {
    return when;
  }

  // ── ChucK API methods ─────────────────────────────────────────────────────

  /**
   * ChucK API: {@code blob.cvals()} — returns a {@code complex[]} ChuckArray of all complex bins.
   */
  public ChuckArray cvals() {
    ChuckArray arr = new ChuckArray("complex", cvals.size());
    for (int i = 0; i < cvals.size(); i++) {
      Object elem = arr.getObject(i);
      if (elem instanceof ChuckArray ca) {
        ca.setFloat(0, cvals.get(i).re());
        ca.setFloat(1, cvals.get(i).im());
      }
    }
    return arr;
  }

  /** ChucK API: {@code blob.fvals()} — returns a {@code float[]} ChuckArray of magnitudes. */
  public ChuckArray fvals() {
    ChuckArray arr = new ChuckArray(ChuckType.ARRAY, fvals.length);
    for (int i = 0; i < fvals.length; i++) arr.setFloat(i, fvals[i]);
    return arr;
  }

  /** ChucK API: {@code blob.cval(n)} — returns bin n as a {@code complex} ChuckArray. */
  public ChuckArray cval(long n) {
    int idx = (int) n;
    ChuckArray res = new ChuckArray("complex", new double[] {0.0, 0.0});
    if (idx >= 0 && idx < cvals.size()) {
      res.setFloat(0, cvals.get(idx).re());
      res.setFloat(1, cvals.get(idx).im());
    }
    return res;
  }

  /** ChucK API: {@code blob.fval(n)} — returns magnitude of bin n. */
  public double fval(long n) {
    int idx = (int) n;
    return (idx >= 0 && idx < fvals.length) ? fvals[idx] : 0.0;
  }

  /** ChucK API: {@code blob.when()} — logical time of this result. */
  public long when() {
    return when;
  }
}
