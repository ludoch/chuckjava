Mandolin m => dac;
440 => m.freq;
while (true) {
    Math.random() * 500 + 200 => m.freq;
    1.0 => m.noteOn;
    200::ms => now;
}
