Event e;
fun void hi( Event e, float f, string name ) {
    SinOsc s => WvOut2 w => blackhole; 
    name => w.wavFilename;
    f => s.freq;
    e => now;
    250::ms => now;
    w.closeFile();
}
spork ~ hi( e, 440, me.arg(0) + "_440.wav" );
spork ~ hi( e, 880, me.arg(0) + "_880.wav" );
100::ms => now;
e.broadcast();
500::ms => now;
