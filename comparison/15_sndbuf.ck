SndBuf b => WvOut2 w => blackhole;
"comparison/java/15_sndbuf.wav" => w.wavFilename;
"special:dope" => b.read;
for( int i; i < 4; i++ ) {
    0 => b.pos;
    0.5 + i * 0.5 => b.rate;
    500::ms => now;
}
w.closeFile();
