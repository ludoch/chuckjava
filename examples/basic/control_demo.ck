// control_demo.ck
// Demonstrate the dynamic Control Surface panel

global float freq;
global float vol;

// Initial values
440.0 => freq;
0.5 => vol;

SinOsc s => dac;

while(true) {
    freq => s.freq;
    vol => s.gain;
    10::ms => now;
}
