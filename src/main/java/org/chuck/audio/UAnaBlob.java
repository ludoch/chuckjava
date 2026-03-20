package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates Unit Analyzer (UAna) results.
 */
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
}
