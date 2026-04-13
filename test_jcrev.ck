Impulse i => JCRev r => WvOut wave => blackhole;
"test_jcrev.wav" => wave.wavWrite;
1.0 => r.gain;
0.5 => r.mix;
1.0 => i.next;
1::second => now;
wave.closeFile();
