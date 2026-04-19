/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.2 - Absolute Debug)
   ------------------------------
*/

<<< "--- ENGINE STARTING (v3.2) ---" >>>;

// 1. Setup Drum Kit (8 tracks)
SndBuf kit[8];
Gain master => dac;
0.5 => master.gain;

// Load samples
"examples/data/kick.wav" => kit[0].read;
"examples/data/snare.wav" => kit[1].read;
"examples/data/hihat.wav" => kit[2].read;
"examples/data/hihat-open.wav" => kit[3].read;
"examples/book/digital-artists/audio/clap_01.wav" => kit[4].read;
"examples/book/digital-artists/audio/cowbell_01.wav" => kit[5].read;
"examples/book/digital-artists/audio/click_01.wav" => kit[6].read;
"examples/data/snare-hop.wav" => kit[7].read;

// Initialize
for(0 => int i; i < 8; i++) {
    kit[i] => master;
    kit[i].samples() => kit[i].pos;
}

// 3. Global variables
global int seq_current_step;

// Internal references
int data[];
float probs[];

// Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now;

0 => int step;
while(true) {
    step % 16 => seq_current_step;
    
    // FETCH GLOBALS
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;

    if (data == null) {
        if (step % 8 == 0) <<< "STATUS: data array is NULL" >>>;
    } else {
        // HEARTBEAT
        if (step % 16 == 0) {
            <<< "HEARTBEAT: Step 0. data[0] =", data[0], "probs[0] =", (probs != null ? probs[0] : -1.0) >>>;
        }

        // PROCESS ALL ROWS
        for(0 => int r; r < 8; r++) {
            data[r * 16 + (step % 16)] => int val;
            
            // LOG ANY NON-ZERO VALUE FOUND
            if (val != 0) {
                <<< "!!! FOUND ACTIVE CELL !!! Row:", r, "Step:", (step % 16), "Value:", val >>>;
                
                // Trigger Sample
                0 => kit[r].pos;
                
                // Verification Beep
                SinOsc s => dac; 440 => s.freq; 0.1 => s.gain; 5::ms => now; 0 => s.gain;
            }
        }
    }

    // DAC OUTPUT Level Check
    if (Math.abs(master.last()) > 0.001) {
        <<< ">>> DAC ACTIVE Level:", master.last() >>>;
    }
    
    T => now;
    step++;
}
