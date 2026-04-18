// WinFuncEnv — envelope with window-function-shaped attack/release

SinOsc s => WinFuncEnv env => dac;
440 => s.freq;
0.8 => s.gain;

// set to Hann window (default), 200ms attack, 600ms release
0.2 * second => env.attack;
0.6 * second => env.release;

while (true) {
    env.keyOn();
    800::ms => now;
    env.keyOff();
    800::ms => now;
}
