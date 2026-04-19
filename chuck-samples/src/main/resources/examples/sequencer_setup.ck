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
<<< "Engine: seq_pattern", (seq_pattern != null ? "LINKED" : "NULL") >>>;

// Internal references to work with
int data[];
float probs[];

if (seq_pattern != null) seq_pattern @=> data;
else new int[128] @=> data;

if (seq_probability != null) seq_probability @=> probs;
else new float[8] @=> probs;

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
            if (r < probs.cap()) probs[r] => p;

            if (Math.randomf() <= p) {
                0 => kit[r].pos; // TRIGGER
                <<< "TRIGGER: Row", r, "Step", (step % 16), "[DATA VAL:", val, "]" >>>;
            }
        }
    }
    
    T => now;
    step++;
}
