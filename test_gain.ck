SinOsc s => dac;
0.5 => s.gain;
440 => s.freq;
1::second => now;
