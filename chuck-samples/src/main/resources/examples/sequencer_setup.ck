/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.8 - FIXED)
   ------------------------------
*/

<<< "--- ENGINE STARTING (v3.8) ---" >>>;

// 1. Setup Drum Kit
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

// Initialize
for(0 => int i; i < 8; i++) {
    kit[i] => master;
    kit[i].samples() => kit[i].pos;
}

// Global synchronization
global int seq_current_step;

// Local references
int data[];
float probs[];

// Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now;

0 => int step;
while(true) {
    // 1. Refresh data from Java VM
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;

    if (data == null) {
        if (step % 8 == 0) <<< "WAITING FOR JAVA GRID DATA..." >>>;
    } else {
        // 2. Sync UI cursor
        step % 16 => seq_current_step;

        // 3. Process all 8 tracks
        for(0 => int r; r < 8; r++) {
            data[r * 16 + (step % 16)] => int val;
            
            if (val != 0) {
                // Apply probability
                1.0 => float p;
                if (probs != null && r < probs.cap()) probs[r] => p;

                if (Math.randomf() <= p) {
                    0 => kit[r].pos; // TRIGGER
                    <<< "TRIGGER: Row", r, "Step", (step % 16) >>>;
                }
            }
        }
    }

    // 4. DAC Output Monitor (print only if non-zero)
    master.last() => float out;
    if (Math.abs(out) > 0.001) {
        <<< "--- DAC LEVEL:", out, "---" >>>;
    }
    
    T => now;
    step++;
}
