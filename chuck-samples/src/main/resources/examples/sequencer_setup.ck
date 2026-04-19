/* 
   CHUCK GRID SEQUENCER PRO SETUP (v3.5 - Persistent Diagnostics)
   ------------------------------
*/

<<< "--- ENGINE STARTING (v3.5) ---" >>>;

// 1. Setup Drum Kit (8 tracks)
SndBuf kit[8];
Gain master => dac;
0.7 => master.gain;

// Load samples
"examples/data/kick.wav" => kit[0].read;
"examples/data/snare.wav" => kit[1].read;
"examples/data/hihat.wav" => kit[2].read;
"examples/data/hihat-open.wav" => kit[3].read;
"examples/book/digital-artists/audio/clap_01.wav" => kit[4].read;
"examples/book/digital-artists/audio/cowbell_01.wav" => kit[5].read;
"examples/book/digital-artists/audio/click_01.wav" => kit[6].read;
"examples/data/snare-hop.wav" => kit[7].read;

// Initialize: set to end of buffer
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
    // 1. Update cursor
    step % 16 => seq_current_step;
    
    // 2. Fetch Java Data
    Machine.getGlobalObject("seq_pattern") $ int[] @=> data;
    Machine.getGlobalObject("seq_probability") $ float[] @=> probs;

    if (data != null) {
        // HEARTBEAT
        if (step % 16 == 0) <<< "HEARTBEAT: Step 0, Pattern[0] =", data[0] >>>;

        // 3. Process Triggers
        for(0 => int r; r < 8; r++) {
            data[r * 16 + (step % 16)] => int val;
            
            if (val != 0) {
                0 => kit[r].pos; // TRIGGER DRUM
                <<< "TRIGGER: Row", r, "Step", (step % 16) >>>;
                
                // Temporary verification oscillator (subtle)
                SinOsc s => dac; 
                440 + (r * 100) => s.freq; 
                0.05 => s.gain; 
                2::ms => now; 
                0 => s.gain;
            }
        }
    } else {
        if (step % 8 == 0) <<< "WAITING FOR JAVA DATA..." >>>;
    }

    // 4. DAC Output Monitor (Always on)
    master.last() => float out;
    if (Math.abs(out) > 0.001) {
        <<< ">>> DAC OUTPUT Level:", out, ">>>" >>>;
    }
    
    T => now;
    step++;
}
