StifKarp m => WvOut2 w => blackhole;
"comparison/java/04_stk_stifkarp.wav" => w.wavFilename;
42 => Math.srandom;
[ 61, 63, 65, 66, 68 ] @=> int notes[];
for( int i; i < 8; i++ ) {
    Std.mtof(notes[i % notes.size()]) => m.freq;
    0.8 => m.pluck;
    250::ms => now;
}
w.closeFile();
