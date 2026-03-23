package org.chuck.host;

import org.chuck.audio.ChuckAudio;
import org.chuck.core.ChuckVM;
import java.util.function.Consumer;

/**
 * ChuckHost provides a high-level, unified API for embedding ChucK-Java 
 * into other Java applications. It orchestrates the VM, compiler, and 
 * optional audio engines.
 */
public class ChuckHost {
    private final ChuckVM vm;
    private ChuckAudio audio;
    private final int sampleRate;

    /**
     * Creates a new ChucK Host with the specified sampling rate.
     */
    public ChuckHost(int sampleRate) {
        this.sampleRate = sampleRate;
        this.vm = new ChuckVM(sampleRate);
    }

    /**
     * Initializes the default JavaSound audio engine.
     * @param bufferSize Samples per audio buffer (e.g. 512)
     * @param numChannels Number of output channels (e.g. 2)
     * @return This host instance for chaining
     */
    public ChuckHost withAudio(int bufferSize, int numChannels) {
        if (audio != null) audio.stop();
        this.audio = new ChuckAudio(vm, bufferSize, numChannels, sampleRate);
        this.audio.start();
        return this;
    }

    /**
     * Compiles and executes ChucK code from a source string.
     * @param code The ChucK source code
     * @return The ID of the newly created shred
     */
    public int run(String code) {
        return vm.run(code, "embedded-script");
    }

    /**
     * Compiles and executes ChucK code from a file path.
     * @param path Path to the .ck file
     * @return The ID of the newly created shred
     */
    public int add(String path) {
        return vm.add(path);
    }

    /**
     * Sporks a Java-based shred from a Runnable.
     */
    public int spork(Runnable task) {
        return vm.spork(task);
    }

    /**
     * Removes a running shred by its ID.
     */
    public void remove(int shredId) {
        vm.removeShred(shredId);
    }

    /**
     * Removes all running shreds and resets the VM state.
     */
    public void clear() {
        vm.clear();
    }

    /**
     * Advances the VM time manually. 
     * Use this if you are NOT using 'withAudio' and want to drive 
     * the engine from your own callback.
     * @param samples Number of samples to advance
     */
    public void advance(int samples) {
        vm.advanceTime(samples);
    }

    /**
     * Returns the last computed sample for a DAC channel.
     * Useful for custom audio callback patterns.
     */
    public float getLastOut(int channel) {
        return vm.getChannelLastOut(channel);
    }

    /**
     * Sets a global integer variable.
     */
    public void setGlobalInt(String name, long value) {
        vm.setGlobalInt(name, value);
    }

    /**
     * Gets a global integer variable.
     */
    public long getGlobalInt(String name) {
        return vm.getGlobalInt(name);
    }

    /**
     * Sets a global object (e.g. for interop).
     */
    public void setGlobalObject(String name, Object obj) {
        vm.setGlobalObject(name, obj);
    }

    /**
     * Gets a global object.
     */
    public Object getGlobalObject(String name) {
        return vm.getGlobalObject(name);
    }

    /**
     * Registers a listener for ChucK's print statements (<<< ... >>>).
     */
    public void onPrint(Consumer<String> listener) {
        vm.addPrintListener(listener::accept);
    }

    /**
     * Sets the master volume gain for the audio output (if using withAudio).
     * @param gain 0.0 to 1.0
     */
    public void setMasterGain(float gain) {
        if (audio != null) {
            audio.setMasterGain(gain);
        }
    }

    /**
     * Stops the audio engine and shuts down the host.
     */
    public void stop() {
        if (audio != null) {
            audio.stop();
        }
        vm.clear();
    }

    /**
     * Returns the underlying ChuckVM instance for advanced usage.
     */
    public ChuckVM getVM() {
        return vm;
    }
}
