// mverb_test.ck
// Test file for MVerb UGen

SinOsc s => MVerb m => dac;
0.5 => m.mix; // Set wet mix
440 => s.freq;
0.5 => s.gain;

1::second => now;
<<< "MVerb test completed successfully", "" >>>;
