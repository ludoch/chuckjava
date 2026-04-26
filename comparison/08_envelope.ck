Step s => Envelope e => WvOut2 w => blackhole;
"comparison/java/08_envelope.wav" => w.wavFilename;
1.0 => s.next;
42 => Math.srandom;
for( int i; i < 4; i++ ) {
    e.keyOn(); 250::ms => now;
    e.keyOff(); 250::ms => now;
}
w.closeFile();
