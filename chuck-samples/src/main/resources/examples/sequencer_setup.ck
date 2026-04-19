/* 
   CHUCK GRID SEQUENCER PRO SETUP
   ------------------------------
   This script connects the standalone visual grid to a real 
   drum kit. It handles sample loading, visual sync, and 
   per-track probability for randomness.
*/

// 1. Setup Drum Kit (8 tracks)
SndBuf kit[8];
Gain master => dac;
0.6 => master.gain;

// Test Oscillator
SinOsc testBlip => dac;
0 => testBlip.gain;

// Load samples
"examples/data/kick.wav" => kit[0].read;
"examples/data/snare.wav" => kit[1].read;
"examples/data/hihat.wav" => kit[2].read;
"examples/data/hihat-open.wav" => kit[3].read;
"examples/book/digital-artists/audio/clap_01.wav" => kit[4].read;
"examples/book/digital-artists/audio/cowbell_01.wav" => kit[5].read;
"examples/book/digital-artists/audio/click_01.wav" => kit[6].read;
"examples/data/snare-hop.wav" => kit[7].read;

// Initialize: connect all to master and set to end (silent)
for(0 => int i; i < 8; i++) {
    kit[i] => master;
    kit[i].samples() => kit[i].pos;
}

// 3. Global variables for Java integration
global int seq_pattern[];
global float seq_probability[];
global int seq_current_step;

<<< "--- ENGINE STARTING ---" >>>;

// Internal references
int data[];
float probs[];

// LINKING: Prefer direct 'global' variables if they are set (should be)
if (seq_pattern != null) {
    seq_pattern @=> data;
    <<< "LINKED seq_pattern from global keyword" >>>;
} else {
    // Fallback: Try Machine API if global keyword is failing
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    if (data != null) <<< "LINKED seq_pattern from Machine API" >>>;
}

if (seq_probability != null) {
    seq_probability @=> probs;
    <<< "LINKED seq_probability from global keyword" >>>;
} else {
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;
    if (probs != null) <<< "LINKED seq_probability from Machine API" >>>;
}

// FINAL SAFETY: If everything is null, initialize local
if (data == null) {
    <<< "WARNING: Initializing local data array (128)" >>>;
    new int[128] @=> data;
}
if (probs == null) {
    <<< "WARNING: Initializing local probs array (8)" >>>;
    new float[8] @=> probs;
    for(0 => int i; i < 8; i++) 1.0 => probs[i];
}

// RMS Monitor: Print master output level every 250ms
spork ~ rmsMonitor();

fun void rmsMonitor() {
    while(true) {
        master.last() => float val;
        if (Math.abs(val) > 0.001) {
            <<< "--- DAC OUTPUT DETECTED: Level =", val, "---" >>>;
        }
        250::ms => now;
    }
}

// 2. Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now; // Sync to global time

0 => int step;
while(true) {
    // A. Update the global cursor position
    step % 16 => seq_current_step;
    
    // B. Read grid data and probabilities
    for(0 => int r; r < 8; r++) {
        // Check if grid pad is ON
        data[r * 16 + (step % 16)] => int val;
        if (val > 0) {
            // Apply probability (0.0 to 1.0)
            1.0 => float p;
            if (probs != null && r < probs.cap()) probs[r] => p;

            if (Math.randomf() <= p) {
                0 => kit[r].pos; // TRIGGER
                <<< "TRIGGER: Row", r, "Step", (step % 16), "[DATA VAL:", val, "]" >>>;
                
                // Audio path verification: Play a short high beep
                880 + (r * 110) => testBlip.freq;
                0.1 => testBlip.gain;
                10::ms => now;
                0 => testBlip.gain;
                (T - 10::ms) => now;
                continue;
            }
        }
    }
    
    T => now;
    step++;
}
