/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.7 - The Final Fix)
   ------------------------------
*/

<<< "--- ENGINE STARTING (v3.7) ---" >>>;

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

// 3. Global synchronization
global int seq_current_step;
int data[];
float probs[];

// Helper for logging
fun int isVerbose() { return Machine.loglevel() >= 2; }

// Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now;

0 => int step;
while(true) {
    // 1. Refresh references from Java (Crucial: do this inside the loop)
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;

    // 2. Safety Check: skip if Java hasn't provided data yet
    if (data == null || data.cap() < 128) {
        if (step % 8 == 0) <<< "WAITING FOR JAVA GRID..." >>>;
        100::ms => now;
        continue;
    }

    // 3. Update UI cursor
    step % 16 => seq_current_step;
    
    // HEARTBEAT (Log Level 2+)
    if (step % 16 == 0 && isVerbose()) <<< "HEARTBEAT: Step 0, Pattern[0] =", data[0] >>>;

    // 4. Process Triggers
    for(0 => int r; r < 8; r++) {
        data[r * 16 + (step % 16)] => int val;
        
        if (val != 0) {
            // Apply probability
            1.0 => float p;
            if (probs != null && r < probs.cap()) probs[r] => p;

            if (p >= 1.0 || Math.randomf() <= p) {
                0 => kit[r].pos; // TRIGGER
                if (isVerbose()) <<< "TRIGGER: Row", r, "Step", (step % 16) >>>;
            }
        }
    }

    // 5. Advance time
    T => now;
    step++;
}
