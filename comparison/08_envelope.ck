Step s => Envelope e => WvOut2 w => blackhole;
me.arg(0) => w.wavFilename;
1.0 => s.next;
42 => Math.srandom;
for( int i; i < 4; i++ ) {
    e.keyOn(); 250::ms => now;
    e.keyOff(); 250::ms => now;
}
w.closeFile();
