BeeThree m => WvOut2 w => blackhole;
me.arg(0) => w.wavFilename;
[60, 64, 67, 72] @=> int notes[];
for( int i; i < 4; i++ ) {
    Std.mtof(notes[i]) => m.freq;
    1.0 => m.noteOn;
    250::ms => now;
    1.0 => m.noteOff;
    250::ms => now;
}
w.closeFile();
