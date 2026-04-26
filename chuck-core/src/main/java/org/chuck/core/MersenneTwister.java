package org.chuck.core;

/**
 * A Java implementation of the MT19937 Mersenne Twister RNG.
 * Used for mathematical parity with native ChucK (1.5.0.1+).
 */
public class MersenneTwister {
  private static final int N = 624;
  private static final int M = 397;
  private static final int MATRIX_A = 0x9908b0df;
  private static final int UPPER_MASK = 0x80000000;
  private static final int LOWER_MASK = 0x7fffffff;

  private int[] mt = new int[N];
  private int mti = N + 1;

  public MersenneTwister() {
    seed((int) System.currentTimeMillis());
  }

  public MersenneTwister(int s) {
    seed(s);
  }

  public void seed(int s) {
    mt[0] = s;
    for (mti = 1; mti < N; mti++) {
      mt[mti] = (1812433253 * (mt[mti - 1] ^ (mt[mti - 1] >>> 30)) + mti);
    }
  }

  public int nextInt() {
    int y;
    int[] mag01 = {0, MATRIX_A};

    if (mti >= N) {
      int kk;
      if (mti == N + 1) seed(5489);

      for (kk = 0; kk < N - M; kk++) {
        y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
        mt[kk] = mt[kk + M] ^ (y >>> 1) ^ mag01[y & 0x1];
      }
      for (; kk < N - 1; kk++) {
        y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
        mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ mag01[y & 0x1];
      }
      y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
      mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ mag01[y & 0x1];

      mti = 0;
    }

    y = mt[mti++];

    y ^= (y >>> 11);
    y ^= (y << 7) & 0x9d2c5680;
    y ^= (y << 15) & 0xefc60000;
    y ^= (y >>> 18);

    return y;
  }

  /** ChucK's ck_random_f() returns uniform double in [0, 1]. */
  public double nextDouble() {
    // std::uniform_real_distribution uses enough bits to fill a double.
    // ChucK uses 32-bit random internally for both int and float.
    long val = nextInt() & 0xFFFFFFFFL;
    return val / 4294967296.0;
  }

  private double nextNextGaussian;
  private boolean haveNextNextGaussian = false;

  public double nextGaussian() {
    if (haveNextNextGaussian) {
      haveNextNextGaussian = false;
      return nextNextGaussian;
    } else {
      double v1, v2, s;
      do {
        v1 = 2 * nextDouble() - 1; // between -1.0 and 1.0
        v2 = 2 * nextDouble() - 1; // between -1.0 and 1.0
        s = v1 * v1 + v2 * v2;
      } while (s >= 1 || s == 0);
      double multiplier = Math.sqrt(-2 * Math.log(s) / s);
      nextNextGaussian = v2 * multiplier;
      haveNextNextGaussian = true;
      return v1 * multiplier;
    }
  }
}
