package org.chuck.midi;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/**
 * The ChucK MidiIn object.
 */
public class MidiIn extends ChuckObject {
    private final ChuckMidiNative driver;

    public MidiIn(ChuckVM vm) {
        super(ChuckType.OBJECT); // In a real VM this would be type MIDI_IN
        this.driver = new ChuckMidiNative(vm);
    }

    public void open(int port) {
        driver.open(port);
    }

    public void close() {
        driver.close();
    }

    // Expose the event for 'min => now'
    public Object getEvent() {
        return driver.getMidiEvent();
    }
}
