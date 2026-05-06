package org.chuck.audio.util;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * JDK 25 Foreign Function &amp; Memory (FFM) API bindings for {@code deluge_dsp_native.dll}.
 *
 * <p>Replaces the JNI-based {@link Dx7Native} class. Calls the C API functions
 * exported by the DLL ({@code dx7_init, dx7_create_voice, ...}) directly via
 * {@link Linker#downcallHandle}, eliminating the JNI glue layer.
 *
 * <p>All voice handles are opaque {@code long} values (actually pointers) returned
 * from {@link #dx7CreateVoice()} and passed to the other methods.
 */
public final class FfmDx7Engine {

    private static final Linker linker = Linker.nativeLinker();
    private static final SymbolLookup lib;

    // ---- downcall handles ----------------------------------------------------

    private static final MethodHandle dx7Init;
    private static final MethodHandle dx7CreateVoice;
    private static final MethodHandle dx7DestroyVoice;
    private static final MethodHandle dx7LoadPatch;
    private static final MethodHandle dx7NoteOn;
    private static final MethodHandle dx7NoteOff;
    private static final MethodHandle dx7SetPitchBend;
    private static final MethodHandle dx7Tick;
    private static final MethodHandle dx7TickBlock;
    private static final MethodHandle dx7IsActive;

    static {
        // Load the DLL
        SymbolLookup lookup = loadLibrary();
        lib = lookup;

        FunctionDescriptor voidDesc = FunctionDescriptor.ofVoid();
        FunctionDescriptor longDesc = FunctionDescriptor.of(ValueLayout.JAVA_LONG);
        FunctionDescriptor voidWithLongDesc = FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG);

        dx7Init = findAndBind("dx7_init", voidDesc);
        dx7CreateVoice = findAndBind("dx7_create_voice", longDesc);
        dx7DestroyVoice = findAndBind("dx7_destroy_voice", voidWithLongDesc);

        // dx7_load_patch(long, pointer, int32)
        dx7LoadPatch = findAndBind("dx7_load_patch",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT
            ));

        // dx7_note_on(long, int32, int32)
        dx7NoteOn = findAndBind("dx7_note_on",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT
            ));

        // dx7_note_off(long)
        dx7NoteOff = findAndBind("dx7_note_off", voidWithLongDesc);

        // dx7_set_pitch_bend(long, int32)
        dx7SetPitchBend = findAndBind("dx7_set_pitch_bend",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT
            ));

        // dx7_tick(long) -> float
        dx7Tick = findAndBind("dx7_tick",
            FunctionDescriptor.of(
                ValueLayout.JAVA_FLOAT,
                ValueLayout.JAVA_LONG
            ));

        // dx7_tick_block(long, pointer, int32) -> void
        dx7TickBlock = findAndBind("dx7_tick_block",
            FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT
            ));

        // dx7_is_active(long) -> boolean
        dx7IsActive = findAndBind("dx7_is_active",
            FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.JAVA_LONG
            ));
    }

    private FfmDx7Engine() { }

    // ---- library loading ----------------------------------------------------

    private static SymbolLookup loadLibrary() {
        // Try java.library.path first
        try {
            return SymbolLookup.libraryLookup("deluge_dsp_native", Arena.global());
        } catch (Exception ignored) {
            // fall through
        }

        // Fallback: try project-relative paths (development).
        // Multi-module Maven builds run tests from the module directory (chuck-core/),
        // so try both direct and parent-relative paths.
        String projectDir = System.getProperty("user.dir");
        String libName = System.mapLibraryName("deluge_dsp_native");
        String[] candidates = {
            projectDir + "/deluge/dx7native/lib/" + libName,
            projectDir + "/../deluge/dx7native/lib/" + libName
        };

        Exception lastError = null;
        for (String candidate : candidates) {
            try {
                return SymbolLookup.libraryLookup(candidate, Arena.global());
            } catch (Exception e) {
                lastError = e;
            }
        }

        var msg = "FfmDx7Engine: cannot load deluge_dsp_native.\n" +
            "  Tried libraryPath: " + System.getProperty("java.library.path") + "\n" +
            "  Tried paths: " + String.join("\n              ", candidates);
        if (lastError != null) {
            throw new UnsatisfiedLinkError(msg + "\n  Last error: " + lastError.getMessage());
        }
        throw new UnsatisfiedLinkError(msg);
    }

    // ---- helper: find symbol + bind downcall ---------------------------------

    private static MethodHandle findAndBind(String name, FunctionDescriptor desc) {
        SymbolLookup lookup = lib;
        // The DLL is C++, so the symbol may be mangled in the export table.
        // If the exact name fails, try again — our C API uses extern "C" so
        // the symbol should match exactly on Windows/MSVC.
        MemorySegment symbol = lookup.find(name).orElseThrow(() ->
            new UnsatisfiedLinkError("FfmDx7Engine: symbol '" + name + "' not found in deluge_dsp_native"));
        return linker.downcallHandle(symbol, desc);
    }

    // ---- public API ----------------------------------------------------------

    /** Initializes the global DxEngine singleton. Call once at startup. */
    public static void init() {
        try {
            dx7Init.invokeExact();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Creates a native voice. Returns opaque handle (0 on failure). */
    public static long createVoice() {
        try {
            return (long) dx7CreateVoice.invokeExact();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Destroys a voice created by {@link #createVoice()}. */
    public static void destroyVoice(long handle) {
        try {
            dx7DestroyVoice.invokeExact(handle);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Loads a 156-byte DX7 patch into the voice's patch data.
     * @param handle    opaque voice handle
     * @param patchData 156-byte raw DX7 SysEx patch
     */
    public static void loadPatch(long handle, byte[] patchData) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocateFrom(ValueLayout.JAVA_BYTE, patchData);
            dx7LoadPatch.invokeExact(handle, seg, patchData.length);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Triggers note-on with the given MIDI note and velocity. */
    public static void noteOn(long handle, int midiNote, int velocity) {
        try {
            dx7NoteOn.invokeExact(handle, midiNote, velocity);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Releases the note (key-up). */
    public static void noteOff(long handle) {
        try {
            dx7NoteOff.invokeExact(handle);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Sets pitch bend offset (Q24). */
    public static void setPitchBend(long handle, int pitchBend) {
        try {
            dx7SetPitchBend.invokeExact(handle, pitchBend);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Processes one sample and returns the output as a float in [-1..1]. */
    public static float tick(long handle) {
        try {
            return (float) dx7Tick.invokeExact(handle);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Renders nSamples into the provided float array (more efficient
     * than per-sample tick).
     */
    public static void tickBlock(long handle, float[] outArray, int nSamples) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate((long) outArray.length * ValueLayout.JAVA_FLOAT.byteSize());
            dx7TickBlock.invokeExact(handle, seg, nSamples);
            // Copy results back
            MemorySegment.copy(seg, ValueLayout.JAVA_FLOAT, 0,
                               MemorySegment.ofArray(outArray), ValueLayout.JAVA_FLOAT, 0,
                               nSamples);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** Returns true if the voice is still producing audio (envelopes active). */
    public static boolean isActive(long handle) {
        try {
            return (boolean) dx7IsActive.invokeExact(handle);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
