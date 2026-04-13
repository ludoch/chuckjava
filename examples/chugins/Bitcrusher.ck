// Bitcrusher — bit-depth reduction and downsampling decimation

SinOsc s => Bitcrusher bc => dac;
220 => s.freq;
0.6 => s.gain;

// reduce bits progressively: 32 → 8 → 4 → 2
[32, 16, 8, 4, 2] @=> int bits[];
for (int i; i < bits.size(); i++) {
    bits[i] => bc.bits;
    <<< "bits:", bc.bits() >>>;
    400::ms => now;
}

// also demonstrate downsampling
32 => bc.bits;
for (int ds; ds < 20; ds++) {
    ds + 1 => bc.downsample;
    <<< "downsample:", bc.downsample() >>>;
    200::ms => now;
}
