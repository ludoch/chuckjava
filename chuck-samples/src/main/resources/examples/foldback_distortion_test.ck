// foldback_distortion_test.ck
// Test file for Foldback mode in Distortion UGen

SinOsc s => Distortion d => dac;
3 => d.mode; // Foldback mode
2.0 => d.drive;
0.5 => d.threshold;
440 => s.freq;
0.5 => s.gain;

1::second => now;
<<< "Foldback distortion test completed successfully", "" >>>;
