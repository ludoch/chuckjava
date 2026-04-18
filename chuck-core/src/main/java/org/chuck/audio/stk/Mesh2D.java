package org.chuck.audio.stk;

import org.chuck.audio.ChuckUGen;

/**
 * Mesh2D: Two-dimensional rectilinear waveguide mesh class. Ported from STK (The Synthesis ToolKit
 * in C++).
 *
 * <p>This class implements a rectilinear, digital waveguide mesh structure. For details, see Van
 * Duyne and Smith, "Physical Modeling with the 2-D Digital Waveguide Mesh", Proceedings of the 1993
 * International Computer Music Conference.
 */
public class Mesh2D extends ChuckUGen {
  private int NX = 12;
  private int NY = 12;
  private float[][] v;
  private float[][] v_prev;
  private float[][] v_next;

  private float xpickup = 0.5f;
  private float ypickup = 0.5f;
  private float xpitch = 0.5f;
  private float ypitch = 0.5f;

  private float decay = 0.999f;

  public Mesh2D() {
    super();
    initMesh(NX, NY);
  }

  public Mesh2D(int nx, int ny) {
    super();
    initMesh(nx, ny);
  }

  private void initMesh(int nx, int ny) {
    if (nx < 3) nx = 3;
    if (ny < 3) ny = 3;
    this.NX = nx;
    this.NY = ny;
    v = new float[NX][NY];
    v_prev = new float[NX][NY];
    v_next = new float[NX][NY];
    clear();
  }

  public void nx(int n) {
    initMesh(n, NY);
  }

  public void nx(double n) {
    nx((int) n);
  }

  public void ny(int n) {
    initMesh(NX, n);
  }

  public void ny(double n) {
    ny((int) n);
  }

  public void x(double f) {
    xpitch = (float) f;
  }

  public void y(double f) {
    ypitch = (float) f;
  }

  public void pickupX(double f) {
    xpickup = (float) f;
  }

  public void pickupY(double f) {
    ypickup = (float) f;
  }

  public void decay(double f) {
    decay = (float) f;
  }

  public void noteOn(double velocity) {
    int ix = (int) (xpitch * (NX - 1));
    int iy = (int) (ypitch * (NY - 1));
    if (ix <= 0) ix = 1;
    if (ix >= NX - 1) ix = NX - 2;
    if (iy <= 0) iy = 1;
    if (iy >= NY - 1) iy = NY - 2;
    v[ix][iy] += (float) velocity;
  }

  public void noteOn(float velocity) {
    noteOn((double) velocity);
  }

  public void noteOn(int velocity) {
    noteOn((double) velocity);
  }

  public void noteOn(long velocity) {
    noteOn((double) velocity);
  }

  public void noteOff(double velocity) {
    // Just let it decay
  }

  public void clear() {
    for (int i = 0; i < NX; i++) {
      for (int j = 0; j < NY; j++) {
        v[i][j] = 0.0f;
        v_prev[i][j] = 0.0f;
        v_next[i][j] = 0.0f;
      }
    }
  }

  @Override
  protected float compute(float input, long systemTime) {
    // Rectilinear waveguide mesh update
    for (int i = 1; i < NX - 1; i++) {
      float[] vi = v[i];
      float[] vi_prev = v_prev[i];
      float[] vi_next = v_next[i];
      float[] vi_plus = v[i + 1];
      float[] vi_minus = v[i - 1];
      for (int j = 1; j < NY - 1; j++) {
        vi_next[j] =
            (0.5f * (vi_plus[j] + vi_minus[j] + vi[j + 1] + vi[j - 1]) - vi_prev[j]) * decay;
      }
    }

    // Swap buffers
    float[][] temp = v_prev;
    v_prev = v;
    v = v_next;
    v_next = temp;

    // Pickup output
    int ix = (int) (xpickup * (NX - 1));
    int iy = (int) (ypickup * (NY - 1));
    if (ix <= 0) ix = 1;
    if (ix >= NX - 1) ix = NX - 2;
    if (iy <= 0) iy = 1;
    if (iy >= NY - 1) iy = NY - 2;

    lastOut = v[ix][iy];
    return lastOut;
  }
}
