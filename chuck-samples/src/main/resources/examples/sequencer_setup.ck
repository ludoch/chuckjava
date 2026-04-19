/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.3 - Log Level Control)
   ------------------------------
*/

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

// Helper to check verbosity
fun int verbose() { return Machine.loglevel() >= 2; }
fun int debug()   { return Machine.loglevel() >= 3; }

if (verbose()) <<< "--- ENGINE STARTING (v3.3) ---" >>>;

// Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now;

0 => int step;
while(true) {
    step % 16 => seq_current_step;
    
    // Refresh data from Java VM
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;

    if (data == null) {
        if (step % 8 == 0 && verbose()) <<< "STATUS: Waiting for Java Grid Data..." >>>;
    } else {
        // HEARTBEAT (Log Level 2+)
        if (step % 16 == 0 && verbose()) {
            <<< "HEARTBEAT: Step 0. data[0] =", data[0] >>>;
        }

        // Process all tracks
        for(0 => int r; r < 8; r++) {
            data[r * 16 + (step % 16)] => int val;
            
            if (val != 0) {
                // Apply probability check
                1.0 => float p;
                if (probs != null && r < probs.cap()) probs[r] => p;

                if (Math.randomf() <= p) {
                    0 => kit[r].pos;
                    if (verbose()) <<< "TRIGGER: Row", r, "Step", (step % 16) >>>;
                    
                    // Diagnostic Beep (Log Level 3+ only)
                    if (debug()) {
                        SinOsc s => dac; 440 + (r*100) => s.freq; 0.05 => s.gain; 5::ms => now; 0 => s.gain;
                    }
                }
            }
        }
    }

    // Audio Emission Monitor (Log Level 2+)
    if (verbose() && Math.abs(master.last()) > 0.001) {
        <<< ">>> DAC ACTIVE Level:", master.last() >>>;
    }
    
    T => now;
    step++;
}
