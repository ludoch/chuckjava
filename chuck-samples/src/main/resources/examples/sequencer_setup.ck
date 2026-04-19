/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.8 - Works on Mac)
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

// 3. Global synchronization
global int seq_current_step;

// Local placeholders (Size 128, will be replaced by Java)
new int[128] @=> int data[];
new float[8] @=> float probs[];
for(0 => int i; i < 8; i++) 1.0 => probs[i];

// Timing logic (120 BPM)
125::ms => dur T;
T - (now % T) => now;

0 => int step;
while(true) {
    // 1. Refresh data from Java VM
    Machine.getGlobalObject("seq_pattern") $ int[] @=> int[] javaData;
    Machine.getGlobalObject("seq_probability") $ float[] @=> float[] javaProbs;
    
    // 2. Only use Java data if it is valid and correct size
    if (javaData != null && javaData.cap() == 128) {
        javaData @=> data;
    }
    if (javaProbs != null && javaProbs.cap() == 8) {
        javaProbs @=> probs;
    }

    // 3. Update UI cursor
    step % 16 => seq_current_step;
    
    // 4. Diagnostic Heartbeat
    if (step % 16 == 0) {
        <<< "HEARTBEAT: Step 0. data.cap() =", data.cap(), "data[0] =", data[0] >>>;
    }

    // 5. Process all 8 tracks
    for(0 => int r; r < 8; r++) {
        data[r * 16 + (step % 16)] => int val;
        
        if (val != 0) {
            probs[r] => float p;
            if (Math.randomf() <= p) {
                0 => kit[r].pos; // TRIGGER
                <<< "TRIGGER: Row", r, "Step", (step % 16) >>>;
            }
        }
    }

    // 6. Audio Monitor
    master.last() => float out;
    if (Math.abs(out) > 0.001) <<< "DAC Level:", out >>>;

    // 7. Advance time
    T => now;
    step++;
}
