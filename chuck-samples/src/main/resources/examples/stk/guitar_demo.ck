// guitar_demo.ck
// Advanced multi-string guitar physical model demo

Guitar g => dac;
0.5 => g.gain;

// Set pick hardness (0.0 to 1.0)
0.8 => g.pickHardness;

// Frequencies for a basic chord (E major)
[ 82.41, 123.47, 164.81, 207.65, 246.94, 329.63 ] @=> float e_major[];

// Play the chord string by string
for( 0 => int i; i < 6; i++ ) {
    g.noteOn(i, e_major[i], 0.8);
    50::ms => now; // Arpeggiate
}

2::second => now;

// Strumming loop
while(true) {
    // Strum up
    for( 0 => int i; i < 6; i++ ) {
        g.noteOn(i, e_major[i], 0.5 + Math.random2f(0, 0.3));
        20::ms => now;
    }
    
    1::second => now;
    
    // Strum down
    for( 5 => int i; i >= 0; i-- ) {
        g.noteOn(i, e_major[i], 0.5 + Math.random2f(0, 0.3));
        25::ms => now;
    }
    
    1::second => now;
}
