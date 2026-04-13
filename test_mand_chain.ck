Mandolin mand => JCRev r => Echo a => Echo b => Echo c => WvOut wave => blackhole;
"test_mand_chain.wav" => wave.wavWrite;

.95 => r.gain;
.05 => r.mix;
1000::ms => a.max => b.max => c.max;
750::ms => a.delay => b.delay => c.delay;
0.5 => a.mix => b.mix => c.mix;

1.0 => mand.noteOn;
2::second => now;
wave.closeFile();
