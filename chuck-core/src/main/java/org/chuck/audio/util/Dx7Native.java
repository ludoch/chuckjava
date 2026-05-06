package org.chuck.audio.util;

/**
 * JNI bridge to the native Deluge DX7 DSP library (deluge_dsp_native.dll).
 * <p>
 * Each voice is identified by an opaque {@code long handle} returned from
 * {@link #nativeCreateVoice()}. The handle wraps a C++ {@code NativeVoice}
 * containing DxPatch + DxVoice + feedback buffer + block buffer.
 * <p>
 * Calling sequence:
 * <ol>
 *   <li>{@link #nativeInit()} once at startup</li>
 *   <li>{@link #nativeCreateVoice()} for each voice needed</li>
 *   <li>{@link #nativeLoadPatch(long, byte[])} with a 156-byte DX7 SysEx patch</li>
 *   <li>{@link #nativeNoteOn(long, int, int)} to trigger a note</li>
 *   <li>{@link #nativeTick(long)} or {@link #nativeTickBlock(long, float[], int)} to render</li>
 *   <li>{@link #nativeNoteOff(long)} to release</li>
 *   <li>{@link #nativeDestroyVoice(long)} when done</li>
 * </ol>
 */
public final class Dx7Native {

    static {
        // Try loading from java.library.path first (production deployment)
        String libName = "deluge_dsp_native";
        boolean loaded = false;
        try {
            System.loadLibrary(libName);
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            // Fallback: try project-relative paths (development).
            // Maven multi-module builds run tests from the module directory
            // (e.g. chuck-core/), so try both direct and parent-relative paths.
            String projectDir = System.getProperty("user.dir");
            String[] candidates = {
                projectDir + "/deluge/dx7native/lib/" + System.mapLibraryName(libName),
                projectDir + "/../deluge/dx7native/lib/" + System.mapLibraryName(libName)
            };
            UnsatisfiedLinkError lastError = e;
            for (String candidate : candidates) {
                try {
                    System.load(candidate);
                    loaded = true;
                    break;
                } catch (UnsatisfiedLinkError e2) {
                    lastError = e2;
                }
            }
            if (!loaded) {
                System.err.println("Dx7Native: cannot load " + libName +
                    " via library path or project paths.\n" +
                    "  Tried libraryPath: " + System.getProperty("java.library.path") + "\n" +
                    "  Tried paths: " + candidates[0] + "\n" +
                    "              " + candidates[1]);
                throw lastError;
            }
        }
        if (loaded) {
            System.out.println("Dx7Native: " + libName + " loaded successfully");
        }
    }

    private Dx7Native() { } // static-only utility

    // ---- lifecycle -------------------------------------------------------

    /** Initializes the global DxEngine singleton (LUTs, etc). Call once at startup. */
    public static native void nativeInit();

    /** Creates a NativeVoice + its DxPatch + DxVoice. Returns opaque handle. */
    public static native long nativeCreateVoice();

    /** Destroys a voice created by {@link #nativeCreateVoice()}. */
    public static native void nativeDestroyVoice(long handle);

    // ---- patch loading ---------------------------------------------------

    /**
     * Loads a 156-byte DX7 patch into the voice's patch data.
     * @param patchData 156-byte raw DX7 SysEx patch
     */
    public static native void nativeLoadPatch(long handle, byte[] patchData);

    // ---- note control ----------------------------------------------------

    /** Triggers note-on with the given MIDI note and velocity. */
    public static native void nativeNoteOn(long handle, int midiNote, int velocity);

    /** Releases the note (key-up). */
    public static native void nativeNoteOff(long handle);

    /**
     * Sets pitch bend offset.
     * @param pitchBend Q24 pitch offset added to base pitch during compute.
     */
    public static native void nativeSetPitchBend(long handle, int pitchBend);

    // ---- audio rendering -------------------------------------------------

    /**
     * Processes one sample and returns the output as a float in [-1..1].
     * Internally renders a block of 132 samples per call and buffers the rest.
     */
    public static native float nativeTick(long handle);

    /**
     * Renders {@code nSamples} into the provided float array (more efficient
     * than per-sample tick). The array must be at least {@code nSamples} long
     * and nSamples must be &le; 132.
     */
    public static native void nativeTickBlock(long handle, float[] outArray, int nSamples);

    /** Returns true if the voice is still producing audio (envelopes active). */
    public static native boolean nativeIsActive(long handle);
}
