Moog m => WvOut2 w => blackhole;
"comparison/java/14_stk_moog.wav" => w.wavFilename;
42 => Math.srandom;
for( int i; i < 4; i++ ) {
    Std.mtof(60 + i) => m.freq;
    1.0 => m.noteOn;
    500::ms => now;
}
w.closeFile();
