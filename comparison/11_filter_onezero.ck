SinOsc s => OneZero f => WvOut2 w => blackhole;
me.arg(0) => w.wavFilename;
0.5 => f.zero;
for( int i; i < 8; i++ ) {
    100 + i * 200 => s.freq;
    250::ms => now;
}
w.closeFile();
