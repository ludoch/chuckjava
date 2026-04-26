SinOsc s => TwoPole f => WvOut2 w => blackhole;
"comparison/java/10_filter_twopole.wav" => w.wavFilename;
0.9 => f.radius;
for( int i; i < 8; i++ ) {
    i * 500 => f.freq;
    250::ms => now;
}
w.closeFile();
