// procedural_reverb_test.ck
// Test file for ProceduralReverb UGen

SinOsc s => ProceduralReverb r => dac;
0.9 => r.decayFactor;
4410 => r.delayLength; // 0.1s at 44100Hz
440 => s.freq;
0.5 => s.gain;

1::second => now;
<<< "ProceduralReverb test completed successfully", "" >>>;
