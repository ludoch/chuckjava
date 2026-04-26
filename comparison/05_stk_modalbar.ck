ModalBar m => WvOut2 w => blackhole;
me.arg(0) => w.wavFilename;
42 => Math.srandom;
for( int i; i < 8; i++ ) {
    440 * (i+1) => m.freq;
    1.0 => m.strike;
    250::ms => now;
}
w.closeFile();
