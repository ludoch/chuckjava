// granular_demo.ck
// Real-time granular synthesis textures

SinOsc s => Granulator g => dac;
0.5 => s.gain;
440 => s.freq;

// Configure granulator
100 => g.grainSize;      // 100ms grains
50 => g.grainSizeJitter;  // +/- 50ms random variation
200 => g.posJitter;      // read from up to 200ms in the past
0.1 => g.pitchJitter;    // subtle pitch variation
20 => g.density;         // 20 grains per second

while(true) {
    // Sweep the source frequency
    Math.random2f(200, 800) => s.freq;
    1::second => now;
}
