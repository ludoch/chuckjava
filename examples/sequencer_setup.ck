/* 
   CHUCK GRID SEQUENCER PRO SETUP
   ------------------------------
   This script connects the IDE's 8-track visual grid to a real 
   drum kit. It handles sample loading, visual sync, and 
   per-track probability for randomness.
*/

// 1. Setup Drum Kit (8 tracks)
SndBuf kit[8];
Gain master => dac;
0.6 => master.gain;

// Load high-quality samples from the data folders
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

// 2. Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now; // Sync to global time

0 => int step;
while(true) {
    // A. Move the green cursor in the IDE
    Machine.setGlobalInt("seq_current_step", step % 16);
    
    // B. Read grid data and probabilities from the IDE
    // Cast the shared objects to the correct array types
    if (Machine.getGlobalObject("seq_pattern") $ int[] @=> int data[]) {
        if (Machine.getGlobalObject("seq_probability") $ float[] @=> float probs[]) {
            
            for(0 => int r; r < 8; r++) {
                // Check if grid pad is ON using standard [index] notation
                if (data[r * 16 + (step % 16)] > 0) {
                    
                    // Apply probability (0.0 to 1.0)
                    1.0 => float p;
                    if (r < probs.cap()) probs[r] => p;
                    
                    if (Math.randomf() <= p) {
                        0 => kit[r].pos; // TRIGGER
                    }
                }
            }
        }
    }
    
    T => now;
    step++;
}
