SinOsc s => TwoZero f => WvOut2 w => blackhole;
me.arg(0) => w.wavFilename;
for( int i; i < 8; i++ ) {
    i * 500 => f.freq;
    250::ms => now;
}
w.closeFile();
