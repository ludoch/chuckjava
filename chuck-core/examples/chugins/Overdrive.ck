// Overdrive — power-law soft clipping distortion
// drive > 1 compresses (soft clip); drive < 1 expands

SinOsc s => Overdrive od => dac;
440 => s.freq;
0.8 => s.gain;

// sweep drive from clean to heavily saturated
0.0 => float t;
while (t < 3.0) {
    1.0 + t * 3.0 => od.drive;  // 1 to 10
    5::ms => now;
    5.0 / 1000.0 +=> t;
}
