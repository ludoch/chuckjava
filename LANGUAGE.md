# ChucK-Java Language & API Reference

Complete reference for the ChucK-Java implementation — operators, types, built-ins, UGens, IDE, and CLI.

---

## Table of Contents

1. [Types](#types)
2. [Operators](#operators)
3. [Control Flow](#control-flow)
4. [Functions & Classes](#functions--classes)
5. [Built-in Globals & Constants](#built-in-globals--constants)
6. [Time & Duration](#time--duration)
7. [Built-in Classes](#built-in-classes)
8. [String Methods](#string-methods)
9. [Arrays](#arrays)
10. [Unit Generators (UGens)](#unit-generators-ugens)
11. [Unit Analyzers (UAna)](#unit-analyzers-uana)
12. [File I/O](#file-io)
13. [Events & Synchronization](#events--synchronization)
14. [OSC Networking](#osc-networking)
15. [MIDI](#midi)
16. [HID](#hid)
17. [CLI Flags](#cli-flags)
18. [IDE Keyboard Shortcuts](#ide-keyboard-shortcuts)

---

## Types

### Primitive Types

| Type | Description | Default |
|------|-------------|---------|
| `int` | 64-bit signed integer | `0` |
| `float` | 64-bit double-precision float | `0.0` |
| `dur` | Duration (in samples internally) | `0` |
| `time` | Absolute time point (in samples) | `0` |
| `void` | No value (function return only) | — |

### Object Types

| Type | Description |
|------|-------------|
| `string` | Mutable text string |
| `vec2` | 2D vector (`x`, `y`) |
| `vec3` | 3D vector (`x`, `y`, `z`) |
| `vec4` | 4D vector (`x`, `y`, `z`, `w`) |
| `complex` | Complex number (`re`, `im`); literal: `#(re, im)` |
| `polar` | Polar form (`mag`, `phase`); literal: `%(mag, phase)` |
| `Object` | Base object type |
| `UGen` | Base unit generator |
| `Event` | Synchronization event |

### Type Declarations

```chuck
int x;                  // declare int, default 0
float f;                // declare float, default 0.0
string s;               // declare string, default ""
int arr[10];            // declare int array of size 10
float mat[4][4];        // declare 2D float array
SinOsc s;               // declare UGen instance
auto x = 5;             // type inferred from right-hand side
```

### Type Casting

```chuck
(float) myInt           // cast int to float
(int) myFloat           // truncate float to int
(string) myInt          // convert int to string
```

---

## Operators

### Chuck Operators

| Operator | Syntax | Description |
|----------|--------|-------------|
| `=>` | `val => dest` | Chuck: assign value / connect UGen |
| `@=>` | `val @=> ref` | Reference-assign object |
| `!=>` | `src !=> dest` | Unchuck: disconnect UGen |
| `<=>` | `a <=> b` | Swap values/connections |
| `=^` | `uana =^ blob` | Upchuck: trigger analysis |
| `<=` | `chout <= val` | Stream write (IO) |

### Compound Chuck (Assign + Operate)

| Operator | Equivalent |
|----------|-----------|
| `v +=> x` | `x + v => x` |
| `v -=> x` | `x - v => x` |
| `v *=> x` | `x * v => x` |
| `v /=> x` | `x / v => x` |
| `v %=> x` | `x % v => x` |
| `v &=> x` | `x & v => x` |
| `\|=> x` | `x \| v => x` |

### Arithmetic

| Operator | Description |
|----------|-------------|
| `+` | Addition |
| `-` | Subtraction / unary negation |
| `*` | Multiplication |
| `/` | Division |
| `%` | Modulo |

### Comparison

| Operator | Description |
|----------|-------------|
| `==` | Equal |
| `!=` | Not equal |
| `<` | Less than |
| `>` | Greater than |
| `<=` | Less than or equal |
| `>=` | Greater than or equal |

### Logical & Bitwise

| Operator | Description |
|----------|-------------|
| `&&` | Logical AND |
| `\|\|` | Logical OR |
| `!` | Logical NOT (unary) |
| `&` | Bitwise AND |
| `\|` | Bitwise OR |

### Increment / Decrement

```chuck
i++;    // post-increment
i--;    // post-decrement
++i;    // pre-increment
--i;    // pre-decrement
```

### Array Operators

```chuck
arr << val;          // append value to array
arr << a << b << c;  // chained append
arr[i]               // index access
```

### Spork

```chuck
spork ~ myFunc();        // run function as new shred
spork ~ myFunc() @=> Shred sh;  // capture shred reference
```

### Print

```chuck
<<< val >>>;               // print any value with type annotation
<<< a, b, c >>>;           // print multiple values space-separated
<<< "text", val >>>;       // mix strings and values
```

---

## Control Flow

### If / Else

```chuck
if (condition) { ... }
if (condition) { ... } else { ... }
if (condition) { ... } else if (other) { ... } else { ... }
```

### While / Until

```chuck
while (condition) { ... }
until (condition) { ... }       // loop UNTIL condition is true (opposite of while)
```

### Do-While / Do-Until

```chuck
do { ... } while (condition);
do { ... } until (condition);
```

### For

```chuck
for (int i = 0; i < 10; i++) { ... }
for (int i = 0; i < arr.size(); i++) { ... }
```

### Foreach (For-Each)

```chuck
for (int x : arr) { ... }           // iterate over array elements
for (auto x : arr) { ... }          // type-inferred iteration
```

### Repeat

```chuck
repeat (10) { ... }                 // execute body N times
```

### Break / Continue / Return

```chuck
break;              // exit innermost loop
continue;           // skip to next loop iteration
return;             // return from function (void)
return val;         // return value from function
```

---

## Functions & Classes

### Function Definition

```chuck
fun returnType name(type param, ...) {
    ...
    return val;
}

// Static function (class-level)
fun static int add(int a, int b) { return a + b; }
```

### Function Overloading

Multiple definitions with different parameter types are allowed:
```chuck
fun void foo(int x) { <<< "int", x >>>; }
fun void foo(float x) { <<< "float", x >>>; }
```

### Class Definition

```chuck
class Foo {
    // Fields
    int x;
    float y;
    5 => int n;         // field with literal initializer

    // Constructor
    fun Foo() { ... }

    // Destructor
    fun ~Foo() { ... }

    // Method
    fun void doSomething() { ... }

    // Static method
    fun static int helper(int a) { return a * 2; }
}

Foo f;                  // instantiate (calls default constructor)
Foo f2 = new Foo;       // explicit instantiation
42 => f.x;              // set field via chuck
f.doSomething();        // call method
```

### Inheritance

```chuck
class Bar extends Foo {
    fun void doSomething() {   // override
        super.doSomething();   // call parent method
        <<< "Bar" >>>;
    }
}
```

### Operator Overloading

```chuck
class Vec2 {
    float x, y;

    fun Vec2 operator+(Vec2 other) {
        Vec2 result;
        x + other.x => result.x;
        y + other.y => result.y;
        return result;
    }

    fun int operator==(Vec2 other) {
        return (x == other.x && y == other.y);
    }

    fun int operator!() { return (x == 0.0 && y == 0.0); }
    fun Vec2 operator++() { x + 1 => x; return this; }
}
```

Overloadable operators: `+`, `-`, `*`, `/`, `%`, `&`, `|`, `<`, `>`, `<=`, `>=`, `==`, `!=`, `&&`, `||`, `!`, `++`, `--`

### Public Classes

```chuck
public class MyClass { ... }    // visible across files loaded by Machine
```

---

## Built-in Globals & Constants

### Special Variables

| Name | Type | Description |
|------|------|-------------|
| `now` | `time` | Current logical time (samples) |
| `me` | `Shred` | Reference to the current shred |
| `dac` | `UGen` | Digital-to-Analog output |
| `adc` | `UGen` | Analog-to-Digital input |
| `blackhole` | `UGen` | Output sink (discards audio, still ticks) |
| `chout` | `IO` | Standard output stream |
| `cherr` | `IO` | Standard error stream |
| `maybe` | `int` | Randomly 0 or 1 each access |
| `null` | (any) | Null object reference |
| `true` | `int` | 1 |
| `false` | `int` | 0 |

### Duration Constants

| Name | Value |
|------|-------|
| `samp` | 1 sample |
| `ms` | milliseconds in samples (sampleRate / 1000) |
| `second` | seconds in samples (sampleRate) |
| `minute` | 60 * second |
| `hour` | 60 * minute |

### Math Constants

| Name | Value |
|------|-------|
| `pi` | π (3.14159...) |
| `e` | Euler's number (2.71828...) |
| `Math.PI` | π |
| `Math.TWO_PI` | 2π |
| `Math.HALF_PI` | π/2 |
| `Math.E` | e |
| `Math.SQRT2` | √2 |
| `Math.INFINITY` | +∞ |

---

## Time & Duration

```chuck
// Duration literals
1::samp                     // 1 sample
500::ms                     // 500 milliseconds
2::second                   // 2 seconds
1.5::second                 // 1.5 seconds
1::minute                   // 1 minute

// Advance time
1::second => now;           // pause this shred for 1 second
now + 1000 => now;          // advance by 1000 samples

// Wait for event
myEvent => now;             // pause until event is signaled

// Time arithmetic
time t = now + 2::second;   // future time point
dur d = 1::second + 500::ms;
```

---

## Built-in Classes

### Math

```chuck
Math.sin(x)         // sine
Math.cos(x)         // cosine
Math.tan(x)         // tangent
Math.asin(x)
Math.acos(x)
Math.atan(x)
Math.atan2(y, x)
Math.sinh(x)
Math.cosh(x)
Math.tanh(x)
Math.sqrt(x)
Math.pow(base, exp)
Math.abs(x)
Math.floor(x)
Math.ceil(x)
Math.round(x)
Math.trunc(x)
Math.log(x)         // natural log
Math.log2(x)
Math.log10(x)
Math.exp(x)
Math.isinf(x)       // returns 1 if infinite
Math.isnan(x)       // returns 1 if NaN
Math.equal(a, b)    // floating-point equality
Math.dbtolin(db)    // dB to linear amplitude
Math.dbtopow(db)    // dB to power
Math.lintodb(lin)   // linear to dB
Math.powtodb(pow)   // power to dB
Math.dbtorms(db)    // dB to RMS
Math.rmstodb(rms)   // RMS to dB
Math.mtof(midi)     // MIDI note to frequency (Hz)
Math.ftom(freq)     // frequency to MIDI note
Math.random()       // random float 0.0 to 1.0
Math.random2(lo, hi)    // random int in range [lo, hi]
Math.randomf()      // random float 0.0 to 1.0
Math.random2f(lo, hi)   // random float in range [lo, hi]
Math.srandom(seed)  // seed the RNG
```

### Std

```chuck
Std.ftom(freq)          // frequency to MIDI note (float)
Std.mtof(midi)          // MIDI note to frequency (alias for Math.mtof)
Std.abs(x)              // absolute value
Std.rand()              // alias for Math.random
Std.rand2(lo, hi)       // alias for Math.random2
Std.fabs(x)             // float absolute value
```

### Machine

```chuck
Machine.add("path/to/file.ck")          // compile and spork file; returns shred ID
Machine.add("file.ck:arg0:arg1")        // pass arguments to file
Machine.remove(shredId)                 // stop a shred by ID
Machine.eval("<<< 42 >>>;")             // compile and run source string
```

### Shred (`me`)

```chuck
me.id()             // int: this shred's ID
me.exit()           // terminate this shred
me.args()           // int: number of arguments passed to this shred
me.numArgs()        // alias for args()
me.arg(i)           // string: get argument at index i
me.dir()            // string: directory of this shred's source file
me.sourceDir()      // alias for dir()
```

### IO / chout / cherr

```chuck
chout <= "hello" <= IO.newline();       // print with newline
chout <= val <= " " <= other <= IO.nl();
cherr <= "error: " <= msg <= IO.newline();

IO.newline()        // newline string constant
IO.nl()             // alias for newline()
IO.tab()            // tab character
IO.INT8             // binary format constant
IO.INT16
IO.INT32
IO.FLOAT32
IO.FLOAT64
```

---

## String Methods

```chuck
string s = "Hello, world!";

s.length()                      // int: number of characters
s.charAt(i)                     // int: character code at index i
s.setCharAt(i, c)               // set character at index i
s.substring(start)              // string: from start to end
s.substring(start, len)         // string: len characters from start
s.insert(i, val)                // insert string at position
s.erase(start, len)             // delete len characters from start
s.replace(start, val)           // replace from start to end with val
s.replace(start, len, val)      // replace range with val
s.find(val)                     // int: first index of val, or -1
s.find(val, start)              // int: search starting at start
s.rfind(val)                    // int: last index of val
s.rfind(val, start)             // int: search backward from start
s.upper()                       // string: uppercase copy
s.lower()                       // string: lowercase copy
s.trim()                        // string: whitespace-trimmed copy
s.ltrim()                       // string: leading whitespace-trimmed copy
s.rtrim()                       // string: trailing whitespace-trimmed copy
s.set(val)                      // set string content from any value
```

String concatenation via `+`:
```chuck
"Hello" + ", " + "world!" => string result;
```

---

## Arrays

```chuck
// Declaration
int arr[0];                         // empty dynamic array
int arr[10];                        // 10 elements, all 0
float mat[3][3];                    // 2D array
string words[0];                    // empty string array

// Access
arr[0] => int first;
42 => arr[5];

// Append
arr << 1;
arr << 1 << 2 << 3;                 // chained append

// Size
arr.size()                          // int: number of elements
arr.cap()                           // int: allocated capacity

// Erase
arr.erase(i)                        // remove element at index i

// Pop
arr.popBack()                       // remove last element
arr.popFront()                      // remove first element

// Sort
arr.sort()                          // sort in place (numeric arrays)

// Iteration
for (int x : arr) { <<< x >>>; }
for (auto x : arr) { <<< x >>>; }

// Array literals (comma-separated in [])
[1, 2, 3, 4] @=> int nums[];
```

---

## Unit Generators (UGens)

### Connecting UGens

```chuck
SinOsc s => dac;                    // connect oscillator to output
SinOsc s => Gain g => dac;         // chain through gain
s !=> dac;                          // disconnect
440 => s.freq;                      // set parameter via chuck
s.gain(0.5);                        // set gain (method call)
```

All UGens support:
```chuck
ugen.gain(val)          // float: output gain multiplier
ugen.gain()             // float: get current gain
ugen.last()             // float: last computed sample
ugen.op(int)            // set operation mode (0=pass, 1=sum, 2=diff, 3=product)
ugen.buffered(bool)     // enable/disable buffering
```

---

### Oscillators

All oscillators inherit from `Osc`:
```chuck
freq => osc.freq         // float: frequency in Hz
osc.freq()               // get frequency
phase => osc.phase       // float: phase 0.0–1.0
osc.phase()              // get phase
width => osc.width       // float: pulse width (SqrOsc, PulseOsc)
sync => osc.sync         // int: 0=freq mode, 1=phase mode, 2=FM input mode
```

| UGen | Description |
|------|-------------|
| `SinOsc` | Sine wave |
| `SawOsc` | Sawtooth wave |
| `TriOsc` | Triangle wave |
| `SqrOsc` | Square wave |
| `PulseOsc` | Variable-width pulse |
| `Phasor` | Linear ramp 0→1 (phase signal) |

---

### Envelope / Amplitude

**Adsr / ADSR**
```chuck
ADSR e => dac;
e.set(attackSec, decaySec, sustainLevel, releaseSec);
e.keyOn();              // begin attack
e.keyOn(1);
e.keyOff();             // begin release
e.keyOff(1);
e.state()               // int: 0=ATTACK 1=DECAY 2=SUSTAIN 3=RELEASE 4=DONE
e.attackTime()          // float: current attack time (samples)
e.decayTime()
e.sustainLevel()
e.releaseTime()
```

**Envelope**
```chuck
Envelope e;
e.target(val)           // float: target value
e.time(seconds)         // float: time to reach target
e.duration(samples)     // int: duration in samples
e.value(val)            // float: set immediately
e.keyOn()               // ramp to 1
e.keyOff()              // ramp to 0
e.value()               // float: current value
```

---

### Filters

**LPF / HPF / BPF / BRF** (Butterworth filters)
```chuck
LPF f => dac;
440 => f.freq;
1.0 => f.Q;
f.freq()
f.Q()
```

**ResonZ**
```chuck
ResonZ r;
r.set(freq, Q);
freq => r.freq;
Q => r.Q;
```

**OnePole / OneZero / TwoPole / TwoZero / PoleZero**
```chuck
OnePole p;
0.9 => p.pole;
```

**BiQuad**
```chuck
BiQuad b;
b.pfreq(freq)       // pole frequency
b.prad(radius)      // pole radius
b.eqzs(1)          // set equal zeros (notch)
b.b0(val)           // direct coefficient access
b.b1(val)
b.b2(val)
b.a1(val)
b.a2(val)
```

---

### Effects

**Gain**
```chuck
Gain g;
0.5 => g.gain;
g.db(val)           // set gain in dB
```

**Pan2** (stereo panning)
```chuck
Pan2 p => dac;
0.0 => p.pan;       // float: -1.0 (left) to 1.0 (right)
p.pan()
```

**Echo**
```chuck
Echo e;
500 => e.delay;     // int: delay in samples
0.5 => e.mix;       // float: dry/wet
```

**Delay / DelayA / DelayL**
```chuck
Delay d;
1000 => d.delay;    // samples
d.delay()
d.max(val)          // max delay buffer size
```

**Chorus**
```chuck
Chorus c;
1.0 => c.modFreq;   // float: LFO frequency
0.5 => c.modDepth;  // float: depth
0.5 => c.mix;
```

**JCRev / NRev / PRCRev** (reverbs)
```chuck
JCRev r;
0.3 => r.mix;       // float: dry/wet
```

**PitShift** (pitch shifter)
```chuck
PitShift p;
2.0 => p.shift;     // float: pitch ratio (1.0 = no change, 2.0 = octave up)
0.5 => p.mix;
```

**Dyno** (dynamics processor)
```chuck
Dyno d;
d.compress()        // configure as compressor
d.limit()           // configure as limiter
d.gate()            // configure as noise gate
d.expand()          // configure as expander
0.5 => d.thresh;
10::ms => d.attackTime;
100::ms => d.releaseTime;
0.5 => d.ratio;
```

---

### Synthesis & Sampling

**Noise**
```chuck
Noise n => dac;
// No parameters — white noise output
```

**Impulse**
```chuck
Impulse imp;
1.0 => imp.next;    // float: amplitude of next impulse
```

**Step**
```chuck
Step s;
0.5 => s.next;      // float: constant output value
```

**SndBuf** (audio file / sample playback)
```chuck
SndBuf buf => dac;
"path/to/file.wav" => buf.read;
buf.samples()           // int: buffer length in samples
buf.length()            // alias for samples()
buf.pos()               // int: current read position
1 => buf.loop;          // int: 0=no loop, 1=loop
2.0 => buf.rate;        // float: playback rate multiplier (1.0 = normal)
0 => buf.pos;           // seek to beginning
buf.db(val)             // set gain in dB
```

**ChuGen** (Custom Unit Generator)
Extend this class to define your own sample-rate logic in ChucK:
```chuck
public class MyFilter extends ChuGen {
    fun float tick(float in) {
        // custom DSP logic here
        return in * 0.5;
    }
}
```
`ChuGen` provides a synchronous `tick()` that runs inside the audio thread for sample-accurate custom synthesis.

**LiSa** (Live Sampling — granular / loop / multi-voice)
```chuck
LiSa lisa;
88200 => lisa.duration;         // int: record buffer size in samples
1 => lisa.record;               // start recording
0 => lisa.record;               // stop recording
1 => lisa.play;                 // play voice 0
1 => lisa.loop;                 // loop voice 0
1 => lisa.bi;                   // bidirectional playback
lisa.playPos()                  // float: current play position (samples)

// Multi-voice (v = voice index 0..N)
1 => lisa.play(v, 1);
1000 => lisa.pos(v, 1000);      // set voice v position
1.5 => lisa.rate(v, 1.5);       // set voice v playback rate
1 => lisa.loop(v, 1);           // loop voice v
```

**WvOut** (write audio to WAV file)
```chuck
WvOut w;
w.wavFilename("output.wav");
dac => w;                       // connect DAC to capture output
// (auto-records while connected)
```

---

### Physical Model Instruments

All instruments support basic note triggering:
```chuck
Inst i => dac;
440.0 => i.freq;        // set frequency (Hz) before noteOn
0.8 => i.noteOn;        // float: velocity (0.0–1.0), triggers note
0.0 => i.noteOff;       // float: release velocity

i.freq(440.0)           // method-style
i.noteOn(0.8)
i.noteOff(0.0)
```

| UGen | Description |
|------|-------------|
| `Clarinet` | Clarinet physical model (STK) |
| `Flute` | Flute physical model (STK) |
| `Bowed` | Bowed string (STK) |
| `Brass` | Brass instrument (STK) |
| `Saxofony` | Saxophone model (STK) |
| `Mandolin` | Mandolin / plucked string (STK) |
| `Plucked` | Karplus-Strong plucked string |
| `StifKarp` | Stiff string model (STK) |
| `Moog` | Moog synthesizer model (STK) |
| `Sitar` | Sitar model (STK) |
| `Rhodey` | Rhodes electric piano (FM) |
| `Wurley` | Wurlitzer electric piano (FM) |
| `TubeBell` | Tubular bell (FM, STK) |
| `HevyMetl` | Heavy metal (FM, STK) |
| `PercFlut` | Percussive flute (FM, STK) |
| `FMVoices` | FM voices (STK) |
| `BeeThree` | Hammond organ (FM, STK) |
| `Shakers` | Stochastic percussion (STK) — maracas, cabasa, etc. |
| `BandedWG` | Banded waveguide (STK) |
| `ModalBar` | Modal bar percussion (STK) |
| `BlowBotl` | Bottle blow model (STK) |
| `BlowHole` | Blown hole instrument (STK) |

---

### Utilities

**Blackhole** — pull UGens without outputting audio (useful for running analyzers)
```chuck
SinOsc s => FFT fft =^ UAnaBlob blob;
fft => blackhole;           // tick the FFT without DAC output
```

**Mix2** — stereo mixer
```chuck
Mix2 m => dac;
srcL => m.left;
srcR => m.right;
```

**SubNoise** — sub-sample noise
**HalfRect** — half-wave rectifier
**FullRect** — full-wave rectifier
**ZeroX** — zero-crossing detector
**Modulate** — modulator
**WaveLoop** — wavetable loop oscillator
**WvIn** — read audio from file (input)

---

## Unit Analyzers (UAna)

### Connecting Analyzers

```chuck
SinOsc s => FFT fft =^ UAnaBlob blob;
fft => blackhole;

// Upchuck: trigger analysis
fft =^ blob;
blob.fvals()        // float[]: magnitude spectrum
blob.cvals()        // complex[]: complex spectrum
blob.pvals()        // float[]: phase spectrum
```

### FFT

```chuck
FFT fft;
1024 => fft.size;               // int: FFT size (power of 2)
Windowing.hann(512) => fft.window; // set window
fft.size()                      // get size
```

Window types via `Windowing`:
```chuck
Windowing.hann(N)
Windowing.hamming(N)
Windowing.blackman(N)
Windowing.blackmanHarris(N)
Windowing.rectangle(N)
```

### IFFT (Inverse FFT)

```chuck
IFFT ifft => dac;
// Receives spectrum via =^ and produces audio
```

### RMS (Root Mean Square)

```chuck
SinOsc s => RMS rms =^ UAnaBlob blob;
rms => blackhole;
rms =^ blob;
blob.fvals()[0]     // float: RMS power value
```

### Centroid (Spectral Centroid)

```chuck
SinOsc s => FFT fft =^ Centroid c =^ UAnaBlob blob;
c =^ blob;
blob.fvals()[0]     // float: spectral centroid frequency
```

---

## File I/O

```chuck
FileIO f;
f.open("data.txt", FileIO.READ);    // open for reading
f.open("out.txt", FileIO.WRITE);    // open for writing
f.open("data.bin", FileIO.READ | FileIO.BINARY);

f.good()            // int: 1 if file is open and valid
f.eof()             // int: 1 if at end of file
f.more()            // int: 1 if more data to read (opposite of eof)

// Reading text
f.readLine()        // string: read a line
f.readString()      // string: read whitespace-delimited token
f.readToken()       // alias for readString()
f.readInt(mode)     // int: read integer (mode = FileIO.INT32, etc.)
f.readFloat()       // float: read float

// Writing text
f << "hello" << IO.newline();
f.write("text")
f.writeInt(42)
f.writeFloat(3.14)

// Binary
f.readInt(FileIO.INT16)     // read 16-bit int
f.readInt(FileIO.INT32)     // read 32-bit int
f.readFloat(FileIO.FLOAT32) // read 32-bit float
f.readFloat(FileIO.FLOAT64) // read 64-bit double

// Seeking
f.seek(pos)         // int: seek to byte position
f.tell()            // int: current position

f.close()           // close file

// Mode constants
FileIO.READ         // read mode
FileIO.WRITE        // write mode
FileIO.APPEND       // append mode
FileIO.BINARY       // binary mode
FileIO.INT8, INT16, INT32, INT64
FileIO.FLOAT32, FLOAT64
```

---

## Events & Synchronization

```chuck
// Define and use an event
Event e;
e => now;               // wait for event (blocks shred)

// Signal from another shred
e.signal()              // wake one waiting shred
e.broadcast()           // wake all waiting shreds
e.waiting()             // int: number of shreds waiting

// Custom event classes
class MyEvent extends Event {
    int value;
}

MyEvent me;
42 => me.value;
me.signal();
```

---

## OSC Networking

```chuck
// Sending OSC
OscOut xmit;
xmit.dest("localhost", 9000);   // host, port
xmit.start("/myAddress");       // begin message
xmit.add(1.0);                  // add float arg
xmit.add(42);                   // add int arg
xmit.add("hello");              // add string arg
xmit.send();                    // transmit

// Receiving OSC
OscIn oin;
9000 => oin.port;               // listen on port
OscMsg msg;
oin.addAddress("/myAddress");
oin.addAddress("/test/*");      // wildcard matching

oin => now;                     // wait for message
while (oin.recv(msg)) {
    msg.address             // string: OSC address
    msg.getInt(0)           // int: first int argument
    msg.getFloat(0)         // float: first float argument
    msg.getString(0)        // string: first string argument
}
```

---

## MIDI

```chuck
MidiIn min;
MidiMsg msg;

min.open(0);            // open MIDI device at index 0

// Poll for messages (min is an Event)
while (min => now) {
    while (min.recv(msg)) {
        msg.data1           // int: status byte (e.g., 0x90 = note on)
        msg.data2           // int: note number
        msg.data3           // int: velocity
    }
}

min.close();
```

---

## HID

```chuck
Hid hid;
HidMsg msg;

hid.openKeyboard(0);    // open keyboard device
hid.openMouse(0);       // open mouse device
hid.openJoystick(0);    // open joystick device

hid => now;             // wait for HID event
while (hid.recv(msg)) {
    msg.isButtonDown()  // int: button pressed
    msg.isButtonUp()    // int: button released
    msg.which           // int: button/key index
    msg.deltax          // int: mouse X delta
    msg.deltay          // int: mouse Y delta
    msg.axisPosition    // float: joystick axis position
}
```

---

## CLI Flags

Run via `mvn exec:java -Dexec.args="[flags] [files]"` or `./run.sh [flags] [files]`.

| Flag | Description |
|------|-------------|
| `--halt` / `-h` | Exit once all shreds finish (default in headless mode) |
| `--loop` / `-l` | Keep running after shreds finish; starts OSC Machine Server on port 8888 |
| `--silent` / `-s` | Disable audio output (headless, no sound device opened) |
| `--verbose:N` | Log level (0=quiet, 1=normal, 2=RMS monitoring) |
| `--syntax` | Syntax-check files only, do not execute |
| `--dump` | Print compiled bytecode (virtual instructions) to console |
| `--srate:N` | Set sample rate (default: 44100) |
| `--bufsize:N` | Set audio buffer size in samples (default: 512) |
| `--chan:N` | Set number of output channels (default: 2) |
| `--antlr` | Use ANTLR4-based parser instead of the default handwritten parser |
| `--version` | Print version and exit |
| `--help` | Print usage summary |

### On-the-Fly Commands (requires `--loop` running instance)

| Flag | Description |
|------|-------------|
| `+` / `--add file.ck` | Compile and spork a new shred |
| `-` / `--remove N` | Remove shred by ID |
| `=` / `--replace N file.ck` | Replace shred N with new file |
| `^` / `--status` | Print VM status (active shreds, logical time) |

### Examples

```bash
# Run a file (stops when done)
mvn exec:java -Dexec.args="examples/basic/foo.ck"

# Run in silent headless mode
mvn exec:java -Dexec.args="--silent examples/basic/foo.ck"

# Start a loop server
mvn exec:java -Dexec.args="--loop"

# Add a file to running server (in another terminal)
mvn exec:java -Dexec.args="+ examples/basic/bar.ck"

# Syntax-check only
mvn exec:java -Dexec.args="--syntax myScript.ck"

# Dump bytecode
mvn exec:java -Dexec.args="--dump myScript.ck"

# Open IDE with files
./run.sh examples/basic/bar.ck examples/basic/chirp.ck
```

---

## IDE Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+N` | New tab |
| `Ctrl+O` | Open file into new tab |
| `Ctrl+S` | Save current tab |
| `Ctrl+W` | Close current tab |
| `Ctrl+Enter` | Spork (Add Shred) — compile and run current tab |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |
| `Ctrl+X` | Cut |
| `Ctrl+C` | Copy |
| `Ctrl+V` | Paste |

### IDE Controls

| Control | Description |
|---------|-------------|
| **Add Shred** | Compile and run the current editor tab in the VM |
| **Remove Shred** | Stop a selected shred from the active shred list |
| **Replace Shred** | Swap selected shred with current editor content |
| **Stop All** | Kill all running shreds |
| **Master Gain** | Slider: overall output volume (0.0–1.0) |
| **VM Time** | Display: logical time elapsed (samples) |
| **Spectrum** | Real-time FFT frequency display (lime green) |
| **Oscilloscope** | Real-time waveform display (cyan) |
| **Console Clear** | Clear the output log panel |

### IDE Panels

- **File Browser** (left) — navigate and double-click to open `.ck` files
- **Editor** (center) — syntax-highlighted `CodeArea` with line numbers and error-line highlighting
- **Shred List** (right) — active shreds by ID and name
- **Console** (bottom) — print output, errors, and VM messages
