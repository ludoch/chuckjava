// ExpDelay — echo with exponentially spaced taps decaying in amplitude
// Creates a dense reverb-like tail from a sparse set of delay taps

Impulse imp => ExpDelay ed => dac;

0.8  => ed.mix;
12   => ed.reps;
1.5  => ed.durcurve;   // exponential spacing of taps
2.0  => ed.ampcurve;   // exponential amplitude decay
second => ed.delay;    // max tap at 1 second

// fire an impulse every 2 seconds
while (true) {
    1.0 => imp.next;
    2::second => now;
}
