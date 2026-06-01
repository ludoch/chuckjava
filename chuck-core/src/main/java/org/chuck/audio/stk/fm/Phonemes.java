package org.chuck.audio.stk.fm;

/**
 * STK phoneme formant frequencies, extracted from ChucK's ugen_stk.cpp
 * (Phonemes::phonemeParameters[i][partial][0]). 32 phonemes x 3 formants. Used by FMVoices to
 * derive operator ratios for the selected vowel.
 */
public final class Phonemes {
  private Phonemes() {}

  /** formantFreq[index][partial], partial 0..2 — first element of each phoneme parameter triple. */
  public static final double[][] FORMANT_FREQ = {
    {273, 2086, 2754},
    {385, 2056, 2587},
    {515, 1805, 2526},
    {773, 1676, 2380},
    {770, 1153, 2450},
    {637, 895, 2556},
    {637, 895, 2556},
    {561, 1084, 2541},
    {515, 1031, 2572},
    {349, 918, 2350},
    {394, 1297, 1441},
    {462, 1200, 2500},
    {265, 1176, 2352},
    {204, 1570, 2481},
    {204, 1570, 2481},
    {204, 1570, 2481},
    {1000, 2800, 7425},
    {0, 2000, 5257},
    {100, 4000, 5500},
    {2693, 4000, 6123},
    {1000, 2800, 7425},
    {273, 2086, 2754},
    {349, 918, 2350},
    {770, 1153, 2450},
    {2000, 5257, 7171},
    {100, 4000, 5500},
    {2693, 4000, 6123},
    {2693, 4000, 6123},
    {2000, 5257, 7171},
    {100, 4000, 5500},
    {2693, 4000, 6123},
    {2693, 4000, 6123}
  };

  /** Phonemes::formantFrequency(index, partial) for partial 0..2. */
  public static double formantFrequency(int index, int partial) {
    if (index < 0 || index > 31 || partial < 0 || partial > 2) return 0.0;
    return FORMANT_FREQ[index][partial];
  }
}
