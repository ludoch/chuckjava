// Perlin — coherent noise oscillator using Ken Perlin's gradient noise
// Produces smooth, band-limited noise with an organic texture

Perlin p => LPF lpf => dac;
0.4 => p.gain;
800 => lpf.freq;

// slowly modulate Perlin frequency for evolving timbre
while (true) {
    Math.sin(now / second * 0.1) * 80 + 120 => p.freq;
    5::ms => now;
}
