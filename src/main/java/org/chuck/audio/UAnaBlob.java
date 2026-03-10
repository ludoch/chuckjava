package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates Unit Analyzer (UAna) results.
 */
public class UAnaBlob {
    private List<Complex> cvals = new ArrayList<>();
    private float[] fvals = new float[0];
    private long when; // logical time of result

    public void setCvals(List<Complex> cvals) {
        this.cvals = cvals;
        this.fvals = new float[cvals.size()];
        for (int i = 0; i < cvals.size(); i++) {
            fvals[i] = cvals.get(i).magnitude();
        }
    }

    public List<Complex> getCvals() {
        return cvals;
    }

    public float[] getFvals() {
        return fvals;
    }

    public void setWhen(long when) {
        this.when = when;
    }

    public long getWhen() {
        return when;
    }
}
