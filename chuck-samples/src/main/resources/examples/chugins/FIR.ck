// FIR — general-purpose FIR filter
// Here: sinc-windowed lowpass, then modulated to highpass

Noise n => FIR f => dac;
0.3 => n.gain;

// design a 64-tap sinc lowpass (cutoff at SR/8)
64   => f.order;
4.0  => f.sinc;    // cutoff factor 4 → LP at SR/8

<<< "FIR LP, order:", f.order() >>>;
2::second => now;

// flip to highpass
f.hpHetero();
<<< "FIR HP" >>>;
2::second => now;
