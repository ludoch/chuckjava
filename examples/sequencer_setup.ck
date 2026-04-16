/* 
   PRO SEQUENCER SETUP
   -------------------
   This script demonstrates how to read the IDE's Grid Sequencer 
   using ChucK code. Use this as a template for your own kits.
*/

// 1. Connection logic
// We use a bank of SndBufs for maximum "punch"
SndBuf kick => dac; "examples/data/kick.wav" => kick.read;
SndBuf snare => dac; "examples/data/snare.wav" => snare.read;
SndBuf hh => dac; "examples/data/hihat.wav" => hh.read;
SndBuf hho => dac; "examples/data/hihat-open.wav" => hho.read;

// Array of our buffers for easy indexing
[kick, snare, hh, hho] @=> SndBuf kit[];

// Mute them initially
for(0=>int i; i<kit.cap(); i++) kit[i].samples() => kit[i].pos;

// 2. Timing
125::ms => dur T; // 120 BPM 16th notes
T - (now % T) => now; // Align to global time

0 => int step;
while(true) {
    // A. Visual Sync: Move the green dot in the IDE
    Machine.setGlobalInt("seq_current_step", step % 16);
    
    // B. Pattern Logic: Read the grid from the IDE
    if (Machine.getGlobalObject("seq_pattern") $ ChuckArray != null) {
        Machine.getGlobalObject("seq_pattern") $ ChuckArray @=> ChuckArray data;
        
        // Loop through rows (we have 4 samples connected, but grid has 8 rows)
        for(0 => int r; r < kit.cap(); r++) {
            // Check if the pad is active at this step
            if (data.getInt(r * 16 + (step % 16)) > 0) {
                0 => kit[r].pos; // PLAY!
            }
        }
    }
    
    T => now;
    step++;
}
