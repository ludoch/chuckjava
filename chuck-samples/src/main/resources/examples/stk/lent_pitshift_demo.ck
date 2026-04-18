// lent_pitshift_demo.ck
// Formant-preserving pitch shifter demo

// Create a rich source (SawOsc)
SawOsc s => LentPitShift pit => dac;
0.2 => s.gain;
110 => s.freq;

// Configure shifter
1.0 => pit.shift;

while(true) {
    // Sweep pitch shift from 0.5 (octave down) to 2.0 (octave up)
    for( 0 => int i; i < 100; i++ ) {
        0.5 + (i/100.0) * 1.5 => pit.shift;
        10::ms => now;
    }
    for( 100 => int i; i > 0; i-- ) {
        0.5 + (i/100.0) * 1.5 => pit.shift;
        10::ms => now;
    }
}
