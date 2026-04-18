// MagicSine — efficient sine via the magic circle algorithm
// Sounds identical to SinOsc but uses 4 mults+2 adds per sample instead of a table lookup

MagicSine s => dac;
0.4 => s.gain;

[60, 62, 64, 65, 67, 69, 71, 72] @=> int scale[];
for (int i; i < scale.size(); i++) {
    Std.mtof(scale[i]) => s.freq;
    200::ms => now;
}
