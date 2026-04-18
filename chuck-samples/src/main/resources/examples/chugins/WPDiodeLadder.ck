// WPDiodeLadder — virtual analog diode ladder lowpass filter (Pirkle AN-6)
// Resonance [0..17]: 0 = flat, ~16 = self-oscillation

SawOsc s => WPDiodeLadder f => dac;
110 => s.freq;
0.5 => s.gain;

8.0 => f.resonance;

// sweep cutoff
while (true) {
    Math.sin(now / second * 0.4) * 800 + 1000 => f.cutoff;
    1::ms => now;
}
