// KasFilter — under-sampling resonant lowpass with cosine crossfading
// Unique aliased character; resonance via negative feedback

SawOsc s => KasFilter f => dac;
55 => s.freq;
0.6 => s.gain;

0.7  => f.resonance;  // [0..0.95]
0.3  => f.accent;     // [0..1] waveshaping on crossfade

// sweep cutoff
while (true) {
    Math.sin(now / second * 0.25) * 600 + 800 => f.freq;
    1::ms => now;
}
