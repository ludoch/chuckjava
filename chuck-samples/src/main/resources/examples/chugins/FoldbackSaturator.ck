// FoldbackSaturator — folds signal back past threshold, creating rich harmonics

SinOsc s => FoldbackSaturator fb => dac;
110 => s.freq;
0.9 => s.gain;
0.3 => fb.threshold;

// slowly lower threshold = more aggressive folding
while (true) {
    Math.sin(now / second * 0.5) * 0.25 + 0.35 => fb.threshold;
    1::ms => now;
}
