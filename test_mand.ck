Mandolin m => WvOut wave => blackhole;
"test_mand.wav" => wave.wavWrite;
1.0 => m.noteOn;
1::second => now;
wave.closeFile();
