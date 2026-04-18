// ambisonics_demo.ck
// Demonstrate First-Order Ambisonics (B-format)

SinOsc s => AmbisonicEncoder enc;
enc.chan(0) => AmbisonicDecoder dec => dac; // Connect encoded W,X,Y,Z to decoder
enc.chan(1) => dec;
enc.chan(2) => dec;
enc.chan(3) => dec;

0.5 => s.gain;
440 => s.freq;

// Default decoder is Stereo (layout=0)
0 => dec.layout;

while(true) {
    for(0 => float a; a < 360; a + 2 => a) {
        a => enc.azimuth;
        Math.sin(a * 0.1) * 45.0 => enc.elevation;
        10::ms => now;
    }
}
