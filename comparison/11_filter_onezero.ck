SinOsc s => OneZero f => WvOut2 w => blackhole;
"comparison/java/11_filter_onezero.wav" => w.wavFilename;
0.5 => f.zero;
for( int i; i < 8; i++ ) {
    100 + i * 200 => s.freq;
    250::ms => now;
}
w.closeFile();
