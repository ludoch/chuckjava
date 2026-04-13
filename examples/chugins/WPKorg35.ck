// WPKorg35 — virtual analog Korg35 lowpass filter (Pirkle AN-5)
// Resonance [0..~2): >1.9 self-oscillates

SawOsc s => WPKorg35 f => dac;
110 => s.freq;
0.5 => s.gain;

1.5 => f.resonance;

// sweep cutoff upward
while (true) {
    Math.sin(now / second * 0.3) * 1500 + 2000 => f.cutoff;
    1::ms => now;
}
