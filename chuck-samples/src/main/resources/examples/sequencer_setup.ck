/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.6 - Hardened Timing)
   ------------------------------
*/

<<< "--- ENGINE STARTING (v3.6) ---" >>>;

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

// LINKING PHASE: Wait until Java has provided the objects
while(data == null) {
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    if (data == null) 100::ms => now;
}
while(probs == null) {
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;
    if (probs == null) 100::ms => now;
}

<<< "--- JAVA LINK ESTABLISHED ---" >>>;

// 2. Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now;

0 => int step;
while(true) {
    // A. Update cursor
    step % 16 => seq_current_step;
    
    // B. Periodic Heartbeat
    if (step % 16 == 0) <<< "HEARTBEAT: Step 0, Pattern[0] =", data[0] >>>;

    // C. Scan all 8 tracks for the current step
    for(0 => int r; r < 8; r++) {
        data[r * 16 + (step % 16)] => int val;
        
        if (val != 0) {
            // Check probability
            1.0 => float p;
            if (r < probs.cap()) probs[r] => p;

            if (p >= 1.0 || Math.randomf() <= p) {
                0 => kit[r].pos; // TRIGGER
                <<< "TRIGGER: Row", r, "Step", (step % 16) >>>;
                
                // Tiny yield to ensure audio seek is processed
                1::samp => now;
            }
        }
    }

    // D. Audio Monitor
    master.last() => float out;
    if (Math.abs(out) > 0.001) <<< "DAC Level:", out >>>;

    // E. Wait for next step
    T => now;
    step++;
}
