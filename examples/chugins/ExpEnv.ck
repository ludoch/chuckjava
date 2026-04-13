// ExpEnv — single time-constant exponential decay
// Ideal for modal synthesis: one ExpEnv per resonant mode

SinOsc s => ExpEnv e => dac;
440 => s.freq;
0.7 => s.gain;

// T60 = time in samples for 60 dB decay
0.5 * second => e.T60;

// fire a burst every beat
while (true) {
    e.keyOn();
    500::ms => now;
}
