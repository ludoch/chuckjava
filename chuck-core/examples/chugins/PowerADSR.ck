// PowerADSR — ADSR with power-shaped stages
// Convex attack (curve < 1) = fast rise, slow approach to peak
// Concave attack (curve > 1) = slow start, explosive final push

SinOsc s => PowerADSR env => dac;
440 => s.freq;
0.8 => s.gain;

0.1 * second => env.attackTime;
0.1 * second => env.decayTime;
0.6          => env.sustainLevel;
0.4 * second => env.releaseTime;
0.3          => env.attackCurve;   // convex: fast rise
2.0          => env.releaseCurve;  // concave: slow release tail

while (true) {
    env.keyOn();
    600::ms => now;
    env.keyOff();
    600::ms => now;
}
