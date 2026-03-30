package org.chuck.audio;

import java.util.Random;

/**
 * Colored noise generator.
 *
 * Modes (set via mode("name")):
 *   "white" — uniform white noise (default)
 *   "pink"  — pink noise via Voss-McCartney multi-rate algorithm (-3 dB/oct)
 *   "brown" — brown/red noise (integrated white, -6 dB/oct)
 *   "flip"  — bit-flip noise (single random bit flipped per sample)
 *   "xor"   — XOR noise (random bits XOR'd with accumulator)
 *
 * All modes output in approximately [-1, 1].
 */
public class CNoise extends ChuckUGen {

    private static final int MODE_WHITE = 0;
    private static final int MODE_PINK  = 1;
    private static final int MODE_BROWN = 2;
    private static final int MODE_FLIP  = 3;
    private static final int MODE_XOR   = 4;

    private int mode = MODE_WHITE;

    // Pink noise state (Voss-McCartney)
    private static final int PINK_DEPTH = 16;
    private final long[] pinkRows   = new long[PINK_DEPTH];
    private long  pinkRunning = 0;
    private int   pinkIndex   = 0;

    // Brown / XOR / flip state
    private double brownAccum  = 0.0;
    private long   xorState    = 0L;

    // Flip probability for XOR/flip modes (0..1 maps to probability per bit)
    private double fprob = 0.5 / 32.0;

    private final Random rng = new Random();

    public CNoise() {
        // Initialize pink rows with random values
        for (int i = 0; i < PINK_DEPTH; i++) {
            pinkRows[i] = rng.nextLong();
            pinkRunning += pinkRows[i];
        }
    }

    /** Set noise mode by name: "white", "pink", "brown", "flip", "xor". */
    public String mode(String name) {
        switch (name.toLowerCase()) {
            case "white" -> mode = MODE_WHITE;
            case "pink"  -> mode = MODE_PINK;
            case "brown" -> mode = MODE_BROWN;
            case "flip"  -> mode = MODE_FLIP;
            case "xor"   -> mode = MODE_XOR;
        }
        return name;
    }
    public String mode() {
        return switch (mode) {
            case MODE_PINK  -> "pink";
            case MODE_BROWN -> "brown";
            case MODE_FLIP  -> "flip";
            case MODE_XOR   -> "xor";
            default         -> "white";
        };
    }

    /** Flip probability for xor/flip modes [0,1]. */
    public double fprob(double p) { fprob = p; return p; }
    public double fprob()         { return fprob; }

    @Override
    protected float compute(float input, long systemTime) {
        return switch (mode) {
            case MODE_PINK  -> (float) pink();
            case MODE_BROWN -> (float) brown();
            case MODE_FLIP  -> (float) flip();
            case MODE_XOR   -> (float) xor();
            default         -> (float) white();
        };
    }

    // ── White noise ──────────────────────────────────────────────────────────
    private double white() {
        return rng.nextDouble() * 2.0 - 1.0;
    }

    // ── Pink noise (Voss-McCartney) ─────────────────────────────────────────
    // Each of the PINK_DEPTH "rows" is updated at a different rate (every 2^k samples).
    // The sum of all rows plus a white value gives the pink noise output.
    private double pink() {
        // Find the lowest-set bit of the counter to determine which row to update
        int updateRow = Integer.numberOfTrailingZeros(++pinkIndex);
        if (updateRow >= PINK_DEPTH) updateRow = PINK_DEPTH - 1;

        long oldVal = pinkRows[updateRow];
        long newVal = rng.nextLong();
        pinkRows[updateRow] = newVal;
        pinkRunning += (newVal - oldVal);

        double pink = pinkRunning + rng.nextLong();
        // Normalize to [-1, 1]: running sum is sum of PINK_DEPTH+1 longs
        return pink / ((double) Long.MAX_VALUE * (PINK_DEPTH + 1));
    }

    // ── Brown noise (integrated white) ─────────────────────────────────────
    private double brown() {
        brownAccum += white() * 0.02;
        if (brownAccum >  1.0) brownAccum =  1.0;
        if (brownAccum < -1.0) brownAccum = -1.0;
        return brownAccum;
    }

    // ── Bit-flip noise ───────────────────────────────────────────────────────
    private double flip() {
        // Flip one random bit of a 32-bit integer accumulator
        int bit = rng.nextInt(32);
        xorState ^= (1L << bit);
        return (xorState & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL * 2.0 - 1.0;
    }

    // ── XOR noise ────────────────────────────────────────────────────────────
    private double xor() {
        // XOR a mask where each bit is set with probability fprob
        long mask = 0;
        for (int i = 0; i < 32; i++) {
            if (rng.nextDouble() < fprob)
                mask |= (1L << i);
        }
        xorState ^= mask;
        return (xorState & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL * 2.0 - 1.0;
    }
}
