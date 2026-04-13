// distortion_demo.ck
// Saturation suite demo

SinOsc s => Distortion d => dac;
0.5 => s.gain;
220 => s.freq;

while(true) {
    // 1. Overdrive (Soft clipping)
    0 => d.mode;
    10.0 => d.drive;
    <<< "Mode: Overdrive" >>>;
    2::second => now;
    
    // 2. Fuzz (Hard clipping)
    1 => d.mode;
    5.0 => d.drive;
    <<< "Mode: Fuzz" >>>;
    2::second => now;
    
    // 3. Bitcrusher
    2 => d.mode;
    1 => d.drive;
    4 => d.bits;        // 4-bit depth
    8 => d.downsample;  // massive downsampling
    <<< "Mode: Bitcrusher" >>>;
    2::second => now;
}
