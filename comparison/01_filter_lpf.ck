Noise n => LPF f => WvOut2 w => blackhole;
"comparison/java/01_filter_lpf.wav" => w.wavFilename;
42 => Math.srandom;
now => time start;
while( now < start + 2::second ) {
    100 + Math.fabs(Math.sin(now/second)) * 5000 => f.freq;
    2 => f.Q;
    5::ms => now;
}
w.closeFile();
