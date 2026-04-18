// WvOut2 test
SinOsc l => WvOut2 w => dac;
SawOsc r => w;

440 => l.freq;
880 => r.freq;
.2 => l.gain;
.2 => r.gain;

"test_stereo.wav" => w.wavWrite;
1::second => now;
w.closeFile();
<<< "Stereo file recorded: test_stereo.wav" >>>;
