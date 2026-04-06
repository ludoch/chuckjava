package org.chuck.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.chuck.audio.Adc;
import org.chuck.audio.Adsr;
import org.chuck.audio.BPF;
import org.chuck.audio.BRF;
import org.chuck.audio.BeeThree;
import org.chuck.audio.BiQuad;
import org.chuck.audio.Blackhole;
import org.chuck.audio.Blit;
import org.chuck.audio.BlitSaw;
import org.chuck.audio.BlitSquare;
import org.chuck.audio.Brass;
import org.chuck.audio.Centroid;
import org.chuck.audio.Chorus;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.Clarinet;
import org.chuck.audio.Delay;
import org.chuck.audio.DelayA;
import org.chuck.audio.DelayL;
import org.chuck.audio.Dyno;
import org.chuck.audio.Echo;
import org.chuck.audio.Envelope;
import org.chuck.audio.FFT;
import org.chuck.audio.FMVoices;
import org.chuck.audio.Flute;
import org.chuck.audio.Flux;
import org.chuck.audio.GVerb;
import org.chuck.audio.Gain;
import org.chuck.audio.GainDB;
import org.chuck.audio.HPF;
import org.chuck.audio.HevyMetl;
import org.chuck.audio.IFFT;
import org.chuck.audio.Identity2;
import org.chuck.audio.Impulse;
import org.chuck.audio.JCRev;
import org.chuck.audio.LiSa;
import org.chuck.audio.LiSa2;
import org.chuck.audio.LiSaN;
import org.chuck.audio.Lpf;
import org.chuck.audio.Mandolin;
import org.chuck.audio.Mix16;
import org.chuck.audio.Mix2;
import org.chuck.audio.Mix4;
import org.chuck.audio.Mix8;
import org.chuck.audio.MixN;
import org.chuck.audio.Modulate;
import org.chuck.audio.Moog;
import org.chuck.audio.NRev;
import org.chuck.audio.Noise;
import org.chuck.audio.OnePole;
import org.chuck.audio.OneZero;
import org.chuck.audio.PRCRev;
import org.chuck.audio.Pan16;
import org.chuck.audio.Pan2;
import org.chuck.audio.Pan4;
import org.chuck.audio.Pan8;
import org.chuck.audio.PanN;
import org.chuck.audio.PercFlut;
import org.chuck.audio.Phasor;
import org.chuck.audio.PitShift;
import org.chuck.audio.Plucked;
import org.chuck.audio.PoleZero;
import org.chuck.audio.PulseOsc;
import org.chuck.audio.RMS;
import org.chuck.audio.ResonZ;
import org.chuck.audio.Rhodey;
import org.chuck.audio.Rolloff;
import org.chuck.audio.SawOsc;
import org.chuck.audio.Saxofony;
import org.chuck.audio.Shakers;
import org.chuck.audio.SinOsc;
import org.chuck.audio.Sitar;
import org.chuck.audio.SndBuf;
import org.chuck.audio.SndBuf2;
import org.chuck.audio.SqrOsc;
import org.chuck.audio.Step;
import org.chuck.audio.StifKarp;
import org.chuck.audio.TriOsc;
import org.chuck.audio.TubeBell;
import org.chuck.audio.TwoPole;
import org.chuck.audio.TwoZero;
import org.chuck.audio.WaveLoop;
import org.chuck.audio.Wurley;
import org.chuck.audio.WvIn;
import org.chuck.audio.WvOut2;
import org.chuck.audio.WvOutUGen;
import org.chuck.audio.AutoCorr;
import org.chuck.audio.Chroma;
import org.chuck.audio.DCT;
import org.chuck.audio.FeatureCollector;
import org.chuck.audio.Flip;
import org.chuck.audio.IDCT;
import org.chuck.audio.UnFlip;
import org.chuck.audio.XCorr;
import org.chuck.audio.ZCR;

import org.chuck.audio.CNoise;
import org.chuck.audio.FullRect;
import org.chuck.audio.HalfRect;
import org.chuck.audio.SubNoise;
import org.chuck.audio.ZeroX;

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
        register("SubNoise", (sr, args) -> new SubNoise());
        register("CNoise", (sr, args) -> new CNoise());
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
        register("FullRect", (sr, args) -> new FullRect());
        register("HalfRect", (sr, args) -> new HalfRect());
        register("ZeroX", (sr, args) -> new ZeroX());

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
        register("DCT", (sr, args) -> new DCT());
        register("IDCT", (sr, args) -> new IDCT());
        register("AutoCorr", (sr, args) -> new AutoCorr());
        register("XCorr", (sr, args) -> new XCorr());
        register("Chroma", (sr, args) -> new Chroma(sr));
        register("Flip", (sr, args) -> new Flip());
        register("UnFlip", (sr, args) -> new UnFlip());
        register("FeatureCollector", (sr, args) -> new FeatureCollector());

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
