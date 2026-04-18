// brass_demo.ck
// Improved STK Brass model demo

Brass b => dac;
0.5 => b.gain;

// Vowel-like brass melody
[ 110.0, 164.8, 220.0, 329.6 ] @=> float notes[];

while(true) {
    for( 0 => int i; i < notes.size(); i++ ) {
        notes[i] => b.freq;
        
        // Random lip tension shift
        notes[i] * Math.random2f(0.9, 1.1) => b.lip;
        
        1.0 => b.noteOn;
        0.4::second => now;
        
        1.0 => b.noteOff;
        0.1::second => now;
    }
}
