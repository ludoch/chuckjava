// Test Gen9 (Oscillate mode)
Gen9 g9 => blackhole;
[1.0, 1.0, 0.0, 2.0, 0.5, 0.0] => g9.coeffs;
440 => g9.freq; // MUST SET FREQ
100::ms => now;
<<< "Gen9 last:", g9.last() >>>;

// Test Gen17 (Lookup mode)
// Map SinOsc -1..1 to 0..1 for lookup
SinOsc s => Gain g => Gen17 g17 => blackhole;
0.5 => g.gain; 0.5 => g.offset;
440 => s.freq;
0.5 => s.gain;
[1.0, 0.0, 0.0] => g17.coeffs; // T1(x) = x
100::ms => now;
<<< "Gen17 last:", g17.last() >>>;

if (Math.abs(g9.last()) > 0.001 && Math.abs(g17.last()) > 0.001) {
    <<< "success", "" >>>;
} else {
    <<< "FAILURE: outputs are too small", "" >>>;
}
