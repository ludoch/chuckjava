fun void play( float f, string name ) {
    SinOsc s => WvOut2 w => blackhole;
    name => w.wavFilename;
    f => s.freq;
    100::ms => now;
    w.closeFile();
}
for( int i; i < 5; i++ ) {
    spork ~ play( 440 * (i+1), me.arg(0) + "_" + i + ".wav" );
    50::ms => now;
}
500::ms => now;
