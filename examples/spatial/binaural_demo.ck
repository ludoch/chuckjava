// binaural_demo.ck
// Demonstrate 3D spatial audio over headphones

SinOsc s => Spatial3D sp => dac;
0.5 => s.gain;
440 => s.freq;

// Rotate sound in a circle
while(true) {
    for(0 => float a; a < 360; a + 1 => a) {
        a => sp.azimuth;
        // distance oscillates between 1 and 5
        1.0 + Math.sin(a * 0.05) * 2.0 => sp.distance;
        10::ms => now;
    }
}
