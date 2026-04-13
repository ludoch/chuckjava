// Range — rescale a signal from one range to another
// Here: maps a SinOsc [-1,1] output to [200, 2000] Hz to drive an LPF

SinOsc lfo => Range r => blackhole;
0.5 => lfo.freq;   // 0.5 Hz LFO

-1.0 => r.inMin;
 1.0 => r.inMax;
200.0  => r.outMin;
2000.0 => r.outMax;

Noise n => LPF lpf => dac;
0.3 => n.gain;

while (true) {
    r.last() => lpf.freq;
    1::ms => now;
}
