# ChucK-Java UGen Reference

This document lists all built-in Unit Generators supported by ChucK-Java.

## ADSR
Class: `org.chuck.audio.util.Adsr`

### Parameters
- `state` (State)
- `currentLevel` (float)
- `attackTime` (float)
- `decayTime` (float)
- `sustainLevel` (float)
- `releaseTime` (float)
- `attackInc` (float)
- `decayInc` (float)
- `releaseInc` (float)

### Methods
- `keyOn()` -> void
- `keyOn(int)` -> void
- `keyOff()` -> void
- `keyOff(int)` -> void
- `sustainLevel()` -> double
- `attackTime()` -> double
- `decayTime()` -> double
- `releaseTime()` -> double
- `getAttackTime()` -> double
- `getDecayTime()` -> double
- `getSustainLevel()` -> double
- `getReleaseTime()` -> double
- `getCurrentLevel()` -> float
- `set(float, float, float, float)` -> void
- `set(double, double, double, double)` -> void
- `state()` -> int
- `getState()` -> int

## Adc
Description: Audio Device Controller (hardware input).

Class: `org.chuck.audio.util.Adc`

### Parameters
- `currentInput` (float[])

### Methods
- `getChannelLastOut(int)` -> float
- `setInputSample(int, float)` -> void
- `getInput(int)` -> float

## Adsr
Class: `org.chuck.audio.util.Adsr`

### Parameters
- `state` (State)
- `currentLevel` (float)
- `attackTime` (float)
- `decayTime` (float)
- `sustainLevel` (float)
- `releaseTime` (float)
- `attackInc` (float)
- `decayInc` (float)
- `releaseInc` (float)

### Methods
- `keyOn()` -> void
- `keyOn(int)` -> void
- `keyOff()` -> void
- `keyOff(int)` -> void
- `sustainLevel()` -> double
- `attackTime()` -> double
- `decayTime()` -> double
- `releaseTime()` -> double
- `getAttackTime()` -> double
- `getDecayTime()` -> double
- `getSustainLevel()` -> double
- `getReleaseTime()` -> double
- `getCurrentLevel()` -> float
- `set(float, float, float, float)` -> void
- `set(double, double, double, double)` -> void
- `state()` -> int
- `getState()` -> int

## AmbisonicDecoder
Category: Built-in Unit Generator

## AmbisonicEncoder
Category: Built-in Unit Generator

## AutoCorr
Class: `org.chuck.audio.analysis.AutoCorr`

### Parameters
- `size` (int)
- `buffer` (float[])
- `pos` (int)

### Methods
- `getSize()` -> int
- `setSize(int)` -> void

## BPF
Class: `org.chuck.audio.filter.BPF`

### Parameters
- `cutoff` (double)
- `q` (double)
- `b0` (double)
- `b1` (double)
- `b2` (double)
- `a1` (double)
- `a2` (double)
- `x1` (double)
- `x2` (double)
- `y1` (double)
- `y2` (double)

### Methods
- `Q()` -> double
- `Q(double)` -> double
- `freq()` -> double
- `freq(double)` -> double

## BRF
Class: `org.chuck.audio.filter.BRF`

### Parameters
- `cutoff` (double)
- `q` (double)
- `b0` (double)
- `b1` (double)
- `b2` (double)
- `a1` (double)
- `a2` (double)
- `x1` (double)
- `x2` (double)
- `y1` (double)
- `y2` (double)

### Methods
- `Q()` -> double
- `Q(double)` -> double
- `freq()` -> double
- `freq(double)` -> double

## BandedWG
Class: `org.chuck.audio.stk.BandedWG`

### Parameters
- `delays` (DelayL[])
- `filters` (OnePole[])
- `decayCoef` (double[])
- `envLevel` (double[])
- `noise` (Noise)
- `freq` (double)
- `bowing` (boolean)
- `bowVelocity` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## BeeThree
Class: `org.chuck.audio.stk.BeeThree`

### Parameters
- `op1` (SinOsc)
- `op2` (SinOsc)
- `op3` (SinOsc)
- `env` (Adsr)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## BiQuad
Class: `org.chuck.audio.filter.BiQuad`

### Parameters
- `prad` (double)
- `pfreq` (double)
- `eqzs` (boolean)
- `x1` (double)
- `x2` (double)
- `y1` (double)
- `y2` (double)
- `b0` (double)
- `b1` (double)
- `b2` (double)
- `a1` (double)
- `a2` (double)

### Methods
- `freq(double)` -> double
- `norm(double)` -> double
- `radius(double)` -> double
- `setPrad(double)` -> void
- `setPfreq(double)` -> void
- `setEqzs(double)` -> void
- `getPrad()` -> double
- `getPfreq()` -> double

## BiQuadStk
Class: `org.chuck.audio.filter.BiQuadStk`

### Parameters
- `b0` (double)
- `b1` (double)
- `b2` (double)
- `a1` (double)
- `a2` (double)
- `x1` (double)
- `x2` (double)
- `y1` (double)
- `y2` (double)

### Methods
- `setB0(double)` -> void
- `setB1(double)` -> void
- `setB2(double)` -> void
- `setA1(double)` -> void
- `setA2(double)` -> void
- `setCoeffs(double, double, double, double, double)` -> void
- `clear()` -> void
- `gain()` -> double
- `gain(double)` -> double
- `Q()` -> double
- `Q(double)` -> void
- `freq()` -> double
- `freq(double)` -> void
- `set(double, double)` -> void

## Bitcrusher
Category: Built-in Unit Generator

## Blackhole
Class: `org.chuck.audio.util.Blackhole`

## Blit
Class: `org.chuck.audio.osc.Blit`

### Parameters
- `phase` (double)
- `rate` (double)
- `period` (double)
- `m` (int)
- `nHarmonics` (int)
- `frequency` (double)

### Methods
- `freq()` -> double
- `freq(double)` -> double
- `setFrequency(double)` -> void
- `setHarmonics(int)` -> void
- `harmonics(int)` -> int
- `harmonics()` -> int
- `reset()` -> void

## BlitSaw
Class: `org.chuck.audio.osc.BlitSaw`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## BlitSquare
Class: `org.chuck.audio.osc.BlitSquare`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## BlowBotl
Class: `org.chuck.audio.stk.BlowBotl`

### Parameters
- `tube` (DelayL)
- `filter` (OnePole)
- `noise` (Noise)
- `env` (Adsr)
- `noiseGain` (float)
- `endReflection` (float)
- `pressure` (float)
- `freq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## BlowHole
Class: `org.chuck.audio.stk.BlowHole`

### Parameters
- `bore` (DelayL)
- `tonehole` (DelayL)
- `filter` (OnePole)
- `noise` (Noise)
- `env` (Adsr)
- `pressure` (float)
- `noiseGain` (float)
- `toneCoeff` (float)
- `ventCoeff` (float)
- `freq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void
- `tonehole(double)` -> void
- `vent(double)` -> void

## Brass
Class: `org.chuck.audio.stk.Brass`

### Parameters
- `delayLine` (DelayL)
- `filter` (OnePole)
- `adsr` (Adsr)
- `lipFilter` (float)
- `pressure` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void
- `lip(float)` -> void

## Broadcaster
Description: Broadcasts audio over the network via HTTP. Supports raw WAV or compressed MP3.

Class: `org.chuck.audio.util.Broadcaster`

### Parameters
- `server` (HttpServer)
- `audioQueue` (ConcurrentLinkedQueue)
- `port` (int)
- `active` (boolean)
- `format` (String)
- `pcmBuffer` (byte[])
- `pcmIdx` (int)

### Methods
- `format()` -> String : Get the current output format.
- `format(String)` -> String : Set the output format ('wav' or 'mp3'). Requires ffmpeg installed for MP3.
- `start()` -> void
- `close()` -> void

## CNoise
Class: `org.chuck.audio.osc.CNoise`

### Parameters
- `mode` (int)
- `pinkRows` (long[])
- `pinkRunning` (long)
- `pinkIndex` (int)
- `brownAccum` (double)
- `xorState` (long)
- `fprob` (double)
- `rng` (Random)

### Methods
- `fprob(double)` -> double
- `fprob()` -> double
- `mode()` -> String
- `mode(String)` -> String

## Centroid
Class: `org.chuck.audio.analysis.Centroid`

## Chorus
Class: `org.chuck.audio.fx.Chorus`

### Parameters
- `delayLine` (DelayL)
- `lfo` (SinOsc)
- `modDepth` (float)
- `baseDelaySamples` (float)
- `mix` (float)

### Methods
- `setMix(float)` -> void
- `setModFreq(double)` -> void
- `setModDepth(float)` -> void

## Chroma
Class: `org.chuck.audio.analysis.Chroma`

### Methods
- `setSampleRate(float)` -> void

## Clarinet
Class: `org.chuck.audio.stk.Clarinet`

### Parameters
- `delayLine` (DelayL)
- `reedTable` (ReedTable)
- `filter` (OneZero)
- `envelope` (Envelope)
- `noise` (Noise)
- `vibrato` (SinOsc)
- `noiseGain` (float)
- `vibratoGain` (float)
- `outputGain` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## DCT
Class: `org.chuck.audio.analysis.DCT`

### Parameters
- `size` (int)
- `buffer` (float[])
- `pos` (int)

### Methods
- `getSize()` -> int
- `setSize(int)` -> void

## Delay
Class: `org.chuck.audio.fx.Delay`

### Parameters
- `buffer` (float[])
- `writePos` (int)
- `delaySamples` (int)

### Methods
- `setDelay(int)` -> void
- `setDelay(double)` -> void
- `getDelay()` -> double
- `init(double, double)` -> void
- `delay(double)` -> double
- `delay()` -> double

## DelayA
Class: `org.chuck.audio.fx.DelayA`

### Parameters
- `buffer` (float[])
- `writePos` (int)
- `outPoint` (int)
- `delay` (double)
- `alpha` (double)
- `coeff` (double)
- `apInput` (double)
- `apOutput` (double)
- `needNext` (boolean)
- `nextOut` (double)

### Methods
- `setDelay(double)` -> void
- `getDelay()` -> double
- `setDelaySec(double)` -> void
- `delay()` -> double

## DelayL
Class: `org.chuck.audio.fx.DelayL`

### Parameters
- `buffer` (float[])
- `writePos` (int)
- `delaySamples` (double)

### Methods
- `setDelay(double)` -> void
- `getDelay()` -> double
- `init(double, double)` -> void
- `delay(double)` -> double
- `delay()` -> double

## DelayP
Class: `org.chuck.audio.fx.DelayP`

### Parameters
- `buffer` (float[])
- `writePos` (int)
- `maxSamples` (int)
- `delaySamples` (double)
- `shiftRate` (double)
- `readA` (double)
- `readB` (double)
- `xfadeLen` (int)
- `xfadePos` (int)

### Methods
- `max(double)` -> void
- `delay()` -> double
- `delay(double)` -> void
- `shift(double)` -> void
- `shift()` -> double

## Distortion
Description: Saturation effect suite: Overdrive, Fuzz, and Bitcrusher.

Class: `org.chuck.audio.fx.Distortion`

### Parameters
- `mode` (int)
- `drive` (float)
- `bits` (int)
- `downsample` (int)
- `downsampleCounter` (int)
- `lastDownsampledValue` (float)

### Methods
- `drive(float)` -> void : Set drive/gain factor.
- `downsample(int)` -> void : Set downsampling factor for Bitcrusher (1 = none).
- `mode(int)` -> void : Set distortion mode (0: Overdrive, 1: Fuzz, 2: Bitcrusher).
- `bits(int)` -> void : Set bit depth for Bitcrusher (1 to 16).

## Dyno
Class: `org.chuck.audio.fx.Dyno`

### Parameters
- `mode` (int)
- `thresh` (float)
- `ratio` (float)
- `slopeAbove` (float)
- `slopeBelow` (float)
- `attackTime` (float)
- `releaseTime` (float)
- `knee` (float)
- `externalGain` (float)
- `envelope` (float)

### Methods
- `attackTime(double)` -> void
- `attackTime()` -> double
- `releaseTime(double)` -> void
- `releaseTime()` -> double
- `thresh()` -> float
- `thresh(float)` -> void
- `ratio(float)` -> void
- `ratio()` -> float
- `slopeAbove(float)` -> void
- `slopeAbove()` -> float
- `slopeBelow()` -> float
- `slopeBelow(float)` -> void
- `compressor()` -> void
- `limiter()` -> void
- `expander()` -> void
- `gate()` -> void
- `duck()` -> void
- `mode(int)` -> void
- `mode()` -> int
- `set(float, float, double, double)` -> void

## Echo
Class: `org.chuck.audio.fx.Echo`

### Parameters
- `delayLine` (Delay)
- `mix` (float)
- `lastWet` (float)

### Methods
- `setDelay(int)` -> void
- `setMix(float)` -> void
- `setMax(double)` -> void

## Envelope
Class: `org.chuck.audio.util.Envelope`

### Parameters
- `target` (float)
- `value` (float)
- `rate` (float)

### Methods
- `keyOn()` -> void
- `keyOff()` -> void
- `setDuration(long)` -> void
- `setKeyOn(double)` -> void
- `setKeyOff(double)` -> void
- `getValue()` -> float
- `setValue(float)` -> void
- `setTarget(float)` -> void
- `setTime(float)` -> void

## ExpDelay
Category: Built-in Unit Generator

## ExpEnv
Category: Built-in Unit Generator

## FFT
Class: `org.chuck.audio.analysis.FFT`

### Parameters
- `size` (int)
- `windowType` (int)
- `ring` (float[])
- `writePos` (int)
- `win` (double[])
- `latestMags` (float[])
- `samplesSinceLastFFT` (int)

### Methods
- `setWindow(int)` -> void
- `getWindow()` -> int
- `getLatestMags()` -> float[]
- `getSize()` -> int
- `setSize(int)` -> void

## FIR
Category: Built-in Unit Generator

## FMVoices
Class: `org.chuck.audio.stk.FMVoices`

### Parameters
- `carrier` (SinOsc)
- `vibrato` (SinOsc)
- `mod1` (SinOsc)
- `mod2` (SinOsc)
- `env` (Adsr)
- `modEnv` (Adsr)
- `baseFreq` (double)
- `vibratoDepth` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void
- `setVibratoDepth(double)` -> void

## FeatureCollector
Class: `org.chuck.audio.analysis.FeatureCollector`

## FilterBasic
Class: `org.chuck.audio.filter.FilterBasic`

### Parameters
- `freq` (double)
- `Q` (double)

### Methods
- `Q()` -> double
- `Q(double)` -> void
- `freq()` -> double
- `freq(double)` -> void
- `set(double, double)` -> void

## FilterStk
Class: `org.chuck.audio.filter.FilterStk`

### Parameters
- `gain` (double)

### Methods
- `gain()` -> double
- `gain(double)` -> double
- `clear()` -> void
- `Q()` -> double
- `Q(double)` -> void
- `freq()` -> double
- `freq(double)` -> void
- `set(double, double)` -> void

## Flip
Class: `org.chuck.audio.analysis.Flip`

### Parameters
- `size` (int)
- `buffer` (float[])
- `pos` (int)

### Methods
- `getSize()` -> int
- `setSize(int)` -> void

## Flute
Class: `org.chuck.audio.stk.Flute`

### Parameters
- `delayLine` (DelayL)
- `filter` (OnePole)
- `adsr` (Adsr)
- `noise` (Noise)
- `jetDelay` (float)
- `noiseGain` (float)
- `endReflection` (float)
- `jetReflection` (float)
- `pressure` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## Flux
Class: `org.chuck.audio.analysis.Flux`

### Parameters
- `prevMagnitudes` (float[])

## FoldbackSaturator
Category: Built-in Unit Generator

## FreeVerb
Description: Lush Schroeder-Moorer algorithmic reverb.

Class: `org.chuck.audio.fx.FreeVerb`

### Parameters
- `combL` (CombFilter[])
- `combR` (CombFilter[])
- `allPassL` (AllPassFilter[])
- `allPassR` (AllPassFilter[])
- `roomSize` (float)
- `damp` (float)
- `mix` (float)

### Methods
- `roomSize(float)` -> void : Set room size (0.0 to 1.0).
- `damp(float)` -> void : Set damping factor (0.0 to 1.0).
- `mix(float)` -> void : Set dry/wet mix (0.0 to 1.0).

## FrencHrn
Class: `org.chuck.audio.stk.FrencHrn`

### Parameters
- `mod` (SinOsc)
- `car` (SinOsc)
- `sub` (SinOsc)
- `env` (Adsr)
- `freq` (double)
- `modIndex` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## FullRect
Class: `org.chuck.audio.util.FullRect`

## GVerb
Class: `org.chuck.audio.fx.GVerb`

### Parameters
- `delays` (Delay[])
- `mix` (float)
- `roomSize` (float)
- `damping` (float)

### Methods
- `roomSize(float)` -> void
- `damping(float)` -> void
- `mix(float)` -> void

## Gain
Description: Gain control UGen.

Class: `org.chuck.audio.util.Gain`

### Methods
- `setDb(float)` -> void
- `db()` -> float
- `db(float)` -> float

## GainDB
Class: `org.chuck.audio.util.GainDB`

### Parameters
- `db` (double)

### Methods
- `gain(double)` -> double
- `gain(float)` -> float
- `gain()` -> double
- `setDb(double)` -> void
- `db(float)` -> float
- `db()` -> float

## Granulator
Description: Real-time granular synthesis engine. Captures audio and generates randomized grains.

Class: `org.chuck.audio.util.Granulator`

### Parameters
- `buffer` (float[])
- `writePos` (int)
- `grainSizeMs` (float)
- `grainSizeJitterMs` (float)
- `positionJitterMs` (float)
- `pitchJitter` (float)
- `density` (float)
- `activeGrains` (List)
- `samplesUntilNextGrain` (double)
- `lastL` (float)
- `lastR` (float)

### Methods
- `getChannelLastOut(int)` -> float
- `pitchJitter(float)` -> void : Set randomization of pitch (0.0 to 1.0).
- `density(float)` -> void : Set grain density (grains per second).
- `grainSize(float)` -> void : Set grain size in milliseconds.
- `grainSizeJitter(float)` -> void : Set randomization of grain size in ms.
- `posJitter(float)` -> void : Set randomization of playback position in ms.

## Guitar
Description: Multi-string guitar physical model with bridge coupling.

Class: `org.chuck.audio.stk.Guitar`

### Parameters
- `strings` (Twang[])
- `excitation` (float[])
- `filePointer` (int)
- `stringState` (int)
- `pickFilter` (OnePole)
- `couplingFilter` (OnePole)
- `couplingGain` (float)
- `pluckGains` (float[])

### Methods
- `getChannelLastOut(int)` -> float
- `noteOn(int, double, double)` -> void : Pluck a string (0-5) with given frequency and amplitude.
- `noteOff(int, double)` -> void : Damp a string (0-5).
- `pickHardness(double)` -> void : Set the 'hardness' of the pick (0.0 to 1.0).
- `coupling(double)` -> void : Set bridge coupling gain.

## HPF
Class: `org.chuck.audio.filter.HPF`

### Parameters
- `cutoff` (double)
- `q` (double)
- `b0` (double)
- `b1` (double)
- `b2` (double)
- `a1` (double)
- `a2` (double)
- `x1` (double)
- `x2` (double)
- `y1` (double)
- `y2` (double)

### Methods
- `Q()` -> double
- `Q(double)` -> double
- `freq()` -> double
- `freq(double)` -> double

## HalfRect
Class: `org.chuck.audio.util.HalfRect`

## HevyMetl
Class: `org.chuck.audio.stk.HevyMetl`

### Parameters
- `carrier1` (SinOsc)
- `carrier2` (SinOsc)
- `mod1` (SinOsc)
- `mod2` (SinOsc)
- `env1` (Adsr)
- `env2` (Adsr)
- `baseFreq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## HnkyTonk
Class: `org.chuck.audio.stk.HnkyTonk`

### Parameters
- `modA` (SinOsc)
- `carA` (SinOsc)
- `modB` (SinOsc)
- `carB` (SinOsc)
- `env` (Adsr)
- `freq` (double)
- `modIndex` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## IDCT
Class: `org.chuck.audio.analysis.IDCT`

## IFFT
Class: `org.chuck.audio.analysis.IFFT`

### Parameters
- `size` (int)
- `buffer` (float[])
- `readPos` (int)

### Methods
- `setSize(int)` -> void

## Identity2
Class: `org.chuck.audio.util.Identity2`

## Impulse
Class: `org.chuck.audio.util.Impulse`

### Parameters
- `nextValue` (float)

### Methods
- `setNext(float)` -> void

## JCRev
Class: `org.chuck.audio.fx.JCRev`

### Parameters
- `allpass` (AllPass[])
- `comb` (Comb[])
- `outLeft` (Delay)
- `outRight` (Delay)
- `mix` (float)

### Methods
- `setMix(float)` -> void

## JetTabl
Class: `org.chuck.audio.util.JetTabl`

## KasFilter
Category: Built-in Unit Generator

## KrstlChr
Class: `org.chuck.audio.stk.KrstlChr`

### Parameters
- `mods` (SinOsc[])
- `cars` (SinOsc[])
- `env` (Adsr)
- `freq` (double)
- `modIndex` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## LPF
Description: Low Pass Filter (1-pole).

Class: `org.chuck.audio.filter.Lpf`

### Parameters
- `cutoff` (float)
- `v0` (float)

### Methods
- `freq(double)` -> double
- `freq()` -> double
- `setCutoff(float)` -> void

## LentPitShift
Description: Formant-preserving pitch shifter (Lent algorithm). Best for monophonic signals.

Class: `org.chuck.audio.fx.LentPitShift`

### Parameters
- `inputLine` (float[])
- `outputLine` (float[])
- `window` (float[])
- `inputPtr` (int)
- `outputPtr` (int)
- `tMax` (int)
- `shift` (double)
- `period` (int)
- `samplesSinceLastAnalysis` (int)

### Methods
- `shift()` -> double
- `shift(double)` -> double : Set the pitch shift factor. 1.0 is no shift, 2.0 is an octave up.

## LiSa
Class: `org.chuck.audio.util.LiSa`

### Parameters
- `buffer` (float[])
- `recPos` (int)
- `isRecording` (boolean)
- `feedback` (float)
- `voices` (Voice[])
- `lastL` (float)
- `lastR` (float)

### Methods
- `getChannelLastOut(int)` -> float
- `feedback(float)` -> void
- `rate(float)` -> void
- `rate(int, float)` -> void
- `recPos(long)` -> void
- `play(int, int)` -> void
- `play(int)` -> void
- `voiceGain(int, float)` -> void
- `voicePan(int, float)` -> void
- `record(int)` -> void
- `duration(ChuckDuration)` -> void
- `duration(long)` -> void
- `pos(int, double)` -> void
- `pos(double)` -> void
- `loop(int, int)` -> void
- `loop(int)` -> void
- `bi(int)` -> void
- `bi(int, int)` -> void

## LiSa10
Category: Built-in Unit Generator

## LiSa16
Category: Built-in Unit Generator

## LiSa2
Class: `org.chuck.audio.util.LiSa2`

### Parameters
- `buffer` (float[][])
- `recPos` (int)
- `isRecording` (boolean)
- `voices` (Voice[])

### Methods
- `rate(int, float)` -> void
- `play(int, int)` -> void
- `play(int)` -> void
- `record(int)` -> void
- `duration(long)` -> void
- `pos(int, long)` -> void
- `loop(int, int)` -> void
- `loop(int)` -> void
- `bi(int)` -> void

## LiSa4
Category: Built-in Unit Generator

## LiSa6
Category: Built-in Unit Generator

## LiSa8
Category: Built-in Unit Generator

## Lpf
Description: Low Pass Filter (1-pole).

Class: `org.chuck.audio.filter.Lpf`

### Parameters
- `cutoff` (float)
- `v0` (float)

### Methods
- `freq(double)` -> double
- `freq()` -> double
- `setCutoff(float)` -> void

## MagicSine
Category: Built-in Unit Generator

## Mandolin
Class: `org.chuck.audio.stk.Mandolin`

### Methods
- `pluck(float)` -> void

## MidiPoly
Description: Automatic high-level voice manager for polyphonic MIDI performance. Manages a pool of UGens and maps incoming MIDI messages to active voices.

Class: `org.chuck.midi.MidiPoly`

### Methods
- `setInstrument(string)` -> string (e.g. "Rhodey", "Mandolin")
- `instrument()` -> string
- `voices(int)` -> int
- `voices()` -> int
- `onMessage(MidiMsg)` -> void

## Mesh2D
Class: `org.chuck.audio.stk.Mesh2D`

### Parameters
- `NX` (int)
- `NY` (int)
- `v` (float[][])
- `v_prev` (float[][])
- `v_next` (float[][])
- `xpickup` (float)
- `ypickup` (float)
- `xpitch` (float)
- `ypitch` (float)
- `decay` (float)

### Methods
- `nx(int)` -> void
- `nx(double)` -> void
- `ny(int)` -> void
- `ny(double)` -> void
- `decay(double)` -> void
- `noteOn(int)` -> void
- `noteOn(float)` -> void
- `noteOn(long)` -> void
- `noteOn(double)` -> void
- `pickupX(double)` -> void
- `pickupY(double)` -> void
- `noteOff(double)` -> void
- `clear()` -> void
- `x(double)` -> void
- `y(double)` -> void

## Mix16
Class: `org.chuck.audio.util.Mix16`

## Mix2
Class: `org.chuck.audio.util.Mix2`

## Mix4
Class: `org.chuck.audio.util.Mix4`

## Mix8
Class: `org.chuck.audio.util.Mix8`

## MixN
Class: `org.chuck.audio.util.MixN`

## ModalBar
Class: `org.chuck.audio.stk.ModalBar`

### Parameters
- `phase` (double[])
- `decayEn` (double[])
- `phaseInc` (double[])
- `decayCoef` (double[])
- `freq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## Modulate
Class: `org.chuck.audio.util.Modulate`

### Parameters
- `vibrato` (SinOsc)
- `vibratoRate` (float)
- `vibratoGain` (float)

### Methods
- `vibratoGain()` -> float
- `vibratoGain(float)` -> void
- `vibratoRate(float)` -> void
- `vibratoRate()` -> float

## Moog
Class: `org.chuck.audio.stk.Moog`

### Parameters
- `oscillators` (SinOsc[])
- `filter` (Lpf)
- `adsr` (Adsr)
- `filterQ` (float)
- `filterSweep` (float)
- `freq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void
- `filterQ(float)` -> void
- `filterSweep(float)` -> void

## NRev
Class: `org.chuck.audio.fx.NRev`

### Parameters
- `allpass` (AllPass[])
- `mix` (float)

### Methods
- `mix(float)` -> void
- `mix()` -> float

## Noise
Class: `org.chuck.audio.osc.Noise`

### Parameters
- `random` (Random)

## OnePole
Class: `org.chuck.audio.filter.OnePole`

### Parameters
- `b0` (float)
- `a1` (float)
- `lastOutput` (float)

### Methods
- `freq(double)` -> double
- `setPole(float)` -> void
- `setB0(float)` -> void
- `setA1(float)` -> void

## OneZero
Class: `org.chuck.audio.filter.OneZero`

### Parameters
- `b0` (float)
- `b1` (float)
- `lastInput` (float)

### Methods
- `setZero(float)` -> void
- `setB0(float)` -> void
- `setB1(float)` -> void

## Overdrive
Category: Built-in Unit Generator

## PRCRev
Class: `org.chuck.audio.fx.PRCRev`

### Parameters
- `allpass` (AllPass[])
- `comb` (Comb[])
- `mix` (float)

### Methods
- `mix(float)` -> void
- `mix()` -> float

## Pan16
Class: `org.chuck.audio.util.Pan16`

### Parameters
- `pan` (float)

### Methods
- `pan()` -> float
- `pan(float)` -> float

## Pan2
Class: `org.chuck.audio.util.Pan2`

### Parameters
- `left` (Gain)
- `right` (Gain)
- `pan` (float)
- `panType` (int)
- `gL` (float)
- `gR` (float)
- `multiBlockCache` (float[][])

### Methods
- `getChannelLastOut(int, long)` -> float
- `pan(float)` -> float
- `pan()` -> float
- `panType(int)` -> int
- `setPan(float)` -> void
- `getPan()` -> float
- `setPanType(int)` -> void

## Pan4
Class: `org.chuck.audio.util.Pan4`

### Parameters
- `pan` (float)

### Methods
- `pan()` -> float
- `pan(float)` -> float

## Pan8
Class: `org.chuck.audio.util.Pan8`

### Parameters
- `pan` (float)

### Methods
- `pan()` -> float
- `pan(float)` -> float

## PanN
Class: `org.chuck.audio.util.PanN`

### Parameters
- `pan` (float)

### Methods
- `pan()` -> float
- `pan(float)` -> float

## PercFlut
Class: `org.chuck.audio.stk.PercFlut`

### Parameters
- `carrier1` (SinOsc)
- `carrier2` (SinOsc)
- `mod1` (SinOsc)
- `mod2` (SinOsc)
- `env1` (Adsr)
- `env2` (Adsr)
- `baseFreq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## Perlin
Category: Built-in Unit Generator

## Phasor
Class: `org.chuck.audio.osc.Phasor`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## PitShift
Class: `org.chuck.audio.fx.PitShift`

### Parameters
- `delays` (DelayL[])
- `shift` (double)
- `phase` (double)
- `windowSize` (int)

### Methods
- `shift(double)` -> void
- `shift()` -> double

## Plucked
Class: `org.chuck.audio.stk.Plucked`

### Parameters
- `delayLine` (DelayL)
- `loopFilter` (OneZero)
- `excitation` (Impulse)
- `baseFreq` (float)

### Methods
- `setData(int, long)` -> void
- `setFreq(double)` -> void
- `noteOn(float)` -> void

## PoleZero
Class: `org.chuck.audio.filter.PoleZero`

### Parameters
- `b0` (double)
- `b1` (double)
- `a1` (double)
- `lastInput` (double)
- `lastOutput` (double)

### Methods
- `setB0(double)` -> void
- `setB1(double)` -> void
- `setA1(double)` -> void
- `setAllpass(double)` -> void
- `setBlockZero(double)` -> void

## PowerADSR
Category: Built-in Unit Generator

## PulseOsc
Class: `org.chuck.audio.osc.PulseOsc`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## RMS
Class: `org.chuck.audio.analysis.RMS`

### Parameters
- `ring` (float[])
- `writePos` (int)
- `size` (int)

### Methods
- `setSize(int)` -> void

## Range
Category: Built-in Unit Generator

## ResonZ
Class: `org.chuck.audio.filter.ResonZ`

### Parameters
- `freq` (float)
- `Q` (float)
- `a0` (float)
- `b1` (float)
- `b2` (float)
- `y1` (float)
- `y2` (float)

### Methods
- `setFreq(float)` -> void
- `setQ(float)` -> void
- `set(float, float)` -> void

## Rhodey
Class: `org.chuck.audio.stk.Rhodey`

### Parameters
- `carrier` (SinOsc)
- `modulator` (SinOsc)
- `carrierEnv` (Adsr)
- `modulatorEnv` (Adsr)
- `baseFreq` (double)
- `modIndex` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## Rolloff
Class: `org.chuck.audio.analysis.Rolloff`

### Parameters
- `percent` (float)

### Methods
- `percent(float)` -> void
- `percent()` -> float

## SawOsc
Class: `org.chuck.audio.osc.SawOsc`

### Methods
- `setFrequency(float)` -> void
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## Saxofony
Class: `org.chuck.audio.stk.Saxofony`

### Parameters
- `delayLine` (DelayL)
- `reedTable` (ReedTable)
- `filter` (OneZero)
- `adsr` (Adsr)
- `pressure` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## Shakers
Class: `org.chuck.audio.stk.Shakers`

### Parameters
- `random` (Random)
- `type` (int)
- `energy` (float)
- `shakeLevel` (float)
- `numObjects` (int)
- `systemDecay` (float)
- `filter` (ResonZ)

### Methods
- `noteOn(float)` -> void
- `energy(float)` -> void
- `objects(int)` -> void
- `preset(int)` -> void

## SinOsc
Description: A sine wave oscillator.

Class: `org.chuck.audio.osc.SinOsc`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## Sitar
Class: `org.chuck.audio.stk.Sitar`

### Parameters
- `delayLine` (DelayL)
- `filter` (OnePole)
- `noise` (Noise)
- `loopGain` (float)
- `ampmult` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void

## SndBuf
Class: `org.chuck.audio.util.SndBuf`

### Parameters
- `samples` (float[])
- `pos` (double)
- `rate` (double)
- `loop` (boolean)

### Methods
- `samples()` -> long
- `setLoop(boolean)` -> void
- `setRead(String)` -> void
- `setSamples(float[])` -> void
- `setRate(double)` -> void
- `setPos(double)` -> void
- `valueAt(long)` -> float
- `ready()` -> int
- `length()` -> long
- `set(String)` -> void
- `isDone()` -> boolean
- `pos()` -> long
- `db()` -> float
- `db(float)` -> float

## SndBuf2
Class: `org.chuck.audio.util.SndBuf2`

### Parameters
- `samples` (float[][])
- `pos` (double)
- `rate` (double)
- `loop` (boolean)

### Methods
- `rate(double)` -> void
- `setRead(String)` -> void
- `pos(double)` -> void
- `loop(int)` -> void
- `read(String)` -> void

## Spatial3D
Description: Binaural 3D panner for headphones. Uses ITD and ILD models.

Class: `org.chuck.audio.util.Spatial3D`

### Parameters
- `azimuth` (float)
- `elevation` (float)
- `distance` (float)
- `delayL` (Delay)
- `delayR` (Delay)
- `shadowL` (OnePole)
- `shadowR` (OnePole)

### Methods
- `azimuth(float)` -> void : Set azimuth in degrees (-180 to 180). 0 is front, 90 is right.
- `elevation(float)` -> void : Set elevation in degrees (-90 to 90).
- `distance(float)` -> void : Set distance (normalized). 1.0 is default.

## SqrOsc
Class: `org.chuck.audio.osc.SqrOsc`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## Step
Class: `org.chuck.audio.util.Step`

### Parameters
- `nextValue` (float)

### Methods
- `setNext(float)` -> void

## StifKarp
Class: `org.chuck.audio.stk.StifKarp`

### Parameters
- `delayLine` (DelayL)
- `filter` (OnePole)
- `pickupPos` (float)
- `lastInput` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void
- `pickupPos(float)` -> void

## SubNoise
Class: `org.chuck.audio.osc.SubNoise`

### Parameters
- `lastValue` (float)
- `rate` (int)
- `count` (int)

### Methods
- `rate()` -> int
- `rate(int)` -> void

## Teabox
Class: `org.chuck.audio.util.Teabox`

### Methods
- `Q()` -> double
- `Q(double)` -> void
- `freq()` -> double
- `freq(double)` -> void
- `set(double, double)` -> void

## TriOsc
Class: `org.chuck.audio.osc.TriOsc`

### Methods
- `setData(int, long)` -> void
- `freq()` -> double
- `freq(double)` -> double : Set the frequency in Hz.
- `setFreq(double)` -> void
- `setSync(int)` -> void
- `setPhase(double)` -> void
- `getFreq()` -> double
- `getPhase()` -> double
- `setWidth(double)` -> void
- `getWidth()` -> double
- `getSync()` -> int
- `last()` -> float
- `init(double)` -> void
- `sync()` -> int
- `sync(int)` -> int
- `width()` -> double
- `width(double)` -> double
- `phase(double)` -> double
- `phase()` -> double

## TubeBell
Class: `org.chuck.audio.stk.TubeBell`

### Parameters
- `carrier1` (SinOsc)
- `carrier2` (SinOsc)
- `mod1` (SinOsc)
- `mod2` (SinOsc)
- `env1` (Adsr)
- `env2` (Adsr)
- `baseFreq` (double)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## Twang
Description: Enhanced plucked-string physical model with pluck position control.

Class: `org.chuck.audio.stk.Twang`

### Parameters
- `delayLine` (DelayL)
- `combDelay` (DelayL)
- `loopFilter` (OneZero)
- `freq` (double)
- `loopGain` (double)
- `pluckPosition` (double)

### Methods
- `freq(double)` -> double : Set string frequency in Hz.
- `freq()` -> double
- `loopGain()` -> double
- `loopGain(double)` -> double : Set loop gain (0.0 to 1.0). Controls sustain.
- `pluckPos()` -> double
- `pluckPos(double)` -> double : Set pluck position (0.0 to 1.0). 0.5 is center.

## TwoPole
Class: `org.chuck.audio.filter.TwoPole`

### Parameters
- `b0` (double)
- `a1` (double)
- `a2` (double)
- `out1` (double)
- `out2` (double)
- `resFreq` (double)
- `resRad` (double)
- `resNorm` (boolean)

### Methods
- `freq()` -> double
- `freq(double)` -> double
- `norm(double)` -> double
- `radius(double)` -> double
- `radius()` -> double
- `setA2(double)` -> void
- `setResonance(double, double, boolean)` -> void
- `setResonance(double, double)` -> void

## TwoZero
Class: `org.chuck.audio.filter.TwoZero`

### Parameters
- `b0` (double)
- `b1` (double)
- `b2` (double)
- `in1` (double)
- `in2` (double)
- `notchFreq` (double)
- `notchRad` (double)

### Methods
- `freq(double)` -> double
- `freq()` -> double
- `radius()` -> double
- `radius(double)` -> double
- `setB2(double)` -> void
- `setNotch(double, double)` -> void

## UnFlip
Class: `org.chuck.audio.analysis.UnFlip`

### Parameters
- `playback` (float[])
- `playPos` (int)

## VoicForm
Class: `org.chuck.audio.stk.VoicForm`

### Parameters
- `filters` (BPF[])
- `env` (Adsr)
- `buzz` (SinOsc)
- `freq` (double)
- `phonemeIdx` (int)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void
- `phoneme(int)` -> void

## WPDiodeLadder
Category: Built-in Unit Generator

## WPKorg35
Category: Built-in Unit Generator

## WaveLoop
Class: `org.chuck.audio.osc.WaveLoop`

## WinFuncEnv
Category: Built-in Unit Generator

## Wurley
Class: `org.chuck.audio.stk.Wurley`

### Parameters
- `carrier` (SinOsc)
- `modulator` (SinOsc)
- `carrierEnv` (Adsr)
- `modulatorEnv` (Adsr)
- `baseFreq` (double)
- `modIndex` (float)

### Methods
- `setFreq(double)` -> void
- `noteOn(float)` -> void
- `noteOff(float)` -> void

## WvIn
Class: `org.chuck.audio.util.WvIn`

### Methods
- `path(String)` -> String

## WvOut
Class: `org.chuck.audio.util.WvOut`

### Parameters
- `fos` (FileOutputStream)
- `totalSamples` (long)
- `numChannels` (int)

### Methods
- `isRecording()` -> boolean
- `record(float, float)` -> void
- `close()` -> void
- `open(String)` -> void

## WvOut2
Class: `org.chuck.audio.util.WvOut2`

### Parameters
- `fos` (FileOutputStream)
- `totalSamples` (long)

### Methods
- `closeFile()` -> void
- `wavWrite(String)` -> void

## XCorr
Class: `org.chuck.audio.analysis.XCorr`

### Parameters
- `size` (int)
- `bufA` (float[])
- `bufB` (float[])
- `posA` (int)
- `posB` (int)
- `sourceCount` (int)

### Methods
- `getSize()` -> int
- `setSize(int)` -> void

## ZCR
Class: `org.chuck.audio.analysis.ZCR`

### Parameters
- `frameSize` (int)
- `buffer` (float[])
- `bufIdx` (int)
- `result` (float)
- `lastSample` (float)

### Methods
- `setFrameSize(int)` -> void
- `getFrameSize()` -> int
- `getZCR()` -> float
- `addSample(float)` -> float
- `last()` -> float

## ZeroX
Class: `org.chuck.audio.analysis.ZeroX`

### Parameters
- `lastInput` (float)

