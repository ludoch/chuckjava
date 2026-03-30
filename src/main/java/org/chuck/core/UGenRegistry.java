package org.chuck.core;

import org.chuck.audio.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Centralized registry for all built-in Unit Generators.
 * Eliminates the need for manual Set/Switch duplication in the compiler.
 */
public class UGenRegistry {
    
    @FunctionalInterface
    public interface UGenFactory {
        ChuckUGen create(float sampleRate, Object[] args);
    }

    private static final Map<String, UGenFactory> REGISTRY = new HashMap<>();

    static {
        // --- Oscillators ---
        register("SinOsc", (sr, args) -> new SinOsc(sr));
        register("SawOsc", (sr, args) -> new SawOsc(sr));
        register("TriOsc", (sr, args) -> new TriOsc(sr));
        register("SqrOsc", (sr, args) -> new SqrOsc(sr));
        register("PulseOsc", (sr, args) -> new PulseOsc(sr));
        register("Phasor", (sr, args) -> new Phasor(sr));
        register("Noise", (sr, args) -> new Noise());
        register("Impulse", (sr, args) -> new Impulse());
        register("Step", (sr, args) -> new Step());
        
        // --- Band-limited ---
        register("Blit", (sr, args) -> new Blit(sr));
        register("BlitSaw", (sr, args) -> new BlitSaw(sr));
        register("BlitSquare", (sr, args) -> new BlitSquare(sr));

        // --- Filters ---
        register("LPF", (sr, args) -> new Lpf(sr));
        register("Lpf", (sr, args) -> new Lpf(sr));
        register("HPF", (sr, args) -> new HPF(sr));
        register("BPF", (sr, args) -> new BPF(sr));
        register("BRF", (sr, args) -> new BRF(sr));
        register("ResonZ", (sr, args) -> new ResonZ(sr));
        register("BiQuad", (sr, args) -> new BiQuad(sr));
        register("OnePole", (sr, args) -> new OnePole());
        register("OneZero", (sr, args) -> new OneZero());
        register("TwoPole", (sr, args) -> new TwoPole(sr));
        register("TwoZero", (sr, args) -> new TwoZero(sr));
        register("PoleZero", (sr, args) -> new PoleZero());

        // --- Effects ---
        register("Echo", (sr, args) -> new Echo((int)(sr * 2)));
        register("Delay", (sr, args) -> new Delay((int)(sr * 2), sr));
        register("DelayL", (sr, args) -> new DelayL((int)(sr * 2), sr));
        register("DelayA", (sr, args) -> new DelayA((int)(sr * 2), sr));
        register("Chorus", (sr, args) -> new Chorus(sr));
        register("JCRev", (sr, args) -> new JCRev(sr));
        register("NRev", (sr, args) -> new NRev(sr));
        register("PRCRev", (sr, args) -> new PRCRev(sr));
        register("GVerb", (sr, args) -> new GVerb(sr));
        register("PitShift", (sr, args) -> new PitShift());
        register("Dyno", (sr, args) -> new Dyno(sr));
        register("Modulate", (sr, args) -> new Modulate(sr));

        // --- Panning & Mixing ---
        register("Pan2", (sr, args) -> new Pan2());
        register("Pan4", (sr, args) -> new Pan4());
        register("Pan8", (sr, args) -> new Pan8());
        register("Pan16", (sr, args) -> new Pan16());
        register("PanN", (sr, args) -> args.length > 0 && args[0] instanceof Number n ? new PanN(n.intValue()) : new PanN(2));
        register("Mix2", (sr, args) -> new Mix2());
        register("Mix4", (sr, args) -> new Mix4());
        register("Mix8", (sr, args) -> new Mix8());
        register("Mix16", (sr, args) -> new Mix16());
        register("MixN", (sr, args) -> args.length > 0 && args[0] instanceof Number n ? new MixN(n.intValue()) : new MixN(2));
        register("Identity2", (sr, args) -> new Identity2());

        // --- Sampling & I/O ---
        register("SndBuf", (sr, args) -> new SndBuf(sr));
        register("SndBuf2", (sr, args) -> new SndBuf2(sr));
        register("WvIn", (sr, args) -> new WvIn(sr));
        register("WvOut", (sr, args) -> new WvOutUGen(sr));
        register("WvOut2", (sr, args) -> new WvOut2(sr));
        register("WaveLoop", (sr, args) -> new WaveLoop(sr));
        register("LiSa", (sr, args) -> new LiSa(sr));
        register("LiSa2", (sr, args) -> new LiSa2(sr));
        register("LiSa4", (sr, args) -> new LiSaN(4, sr));
        register("LiSa8", (sr, args) -> new LiSaN(8, sr));
        register("LiSa16", (sr, args) -> new LiSaN(16, sr));

        // --- STK Instruments ---
        register("Clarinet", (sr, args) -> new Clarinet(10.0f, sr));
        register("Mandolin", (sr, args) -> new Mandolin(10.0f, sr));
        register("Plucked", (sr, args) -> new Plucked(10.0f, sr));
        register("Rhodey", (sr, args) -> new Rhodey(sr));
        register("Wurley", (sr, args) -> new Wurley(sr));
        register("TubeBell", (sr, args) -> new TubeBell(sr));
        register("BeeThree", (sr, args) -> new BeeThree(sr));
        register("FMVoices", (sr, args) -> new FMVoices(sr));
        register("HevyMetl", (sr, args) -> new HevyMetl(sr));
        register("PercFlut", (sr, args) -> new PercFlut(sr));
        register("Moog", (sr, args) -> new Moog(sr));
        register("Saxofony", (sr, args) -> new Saxofony(sr));
        register("Flute", (sr, args) -> new Flute(sr));
        register("Brass", (sr, args) -> new Brass(sr));
        register("Sitar", (sr, args) -> new Sitar(sr));
        register("StifKarp", (sr, args) -> new StifKarp(sr));
        register("Shakers", (sr, args) -> new Shakers(sr));

        // --- Analysis (UAna) ---
        register("FFT", (sr, args) -> new FFT());
        register("IFFT", (sr, args) -> new IFFT());
        register("RMS", (sr, args) -> new RMS());
        register("Flux", (sr, args) -> new Flux());
        register("Rolloff", (sr, args) -> new Rolloff());
        register("Centroid", (sr, args) -> new Centroid());
        register("ZCR", (sr, args) -> new ZCR());
        
        // --- Tools ---
        register("Gain", (sr, args) -> new Gain());
        register("GainDB", (sr, args) -> args.length > 0 && args[0] instanceof Number n ? new GainDB(n.doubleValue()) : new GainDB());
        register("Blackhole", (sr, args) -> new Blackhole());
        register("Adc", (sr, args) -> new Adc());
        register("Envelope", (sr, args) -> new Envelope(sr));
        register("ADSR", (sr, args) -> new Adsr(sr));
        register("Adsr", (sr, args) -> new Adsr(sr));
    }

    private static void register(String name, UGenFactory factory) {
        REGISTRY.put(name, factory);
    }

    public static boolean isRegistered(String name) {
        return REGISTRY.containsKey(name);
    }

    public static Set<String> getRegisteredNames() {
        return REGISTRY.keySet();
    }

    public static ChuckUGen instantiate(String name, float sampleRate, Object[] args) {
        UGenFactory factory = REGISTRY.get(name);
        if (factory != null) {
            return factory.create(sampleRate, args);
        }
        return null;
    }
}
