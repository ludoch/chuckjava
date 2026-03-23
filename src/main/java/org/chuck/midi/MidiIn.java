package org.chuck.midi;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The ChucK MidiIn object.
 */
public class MidiIn extends ChuckEvent {
    private final ChuckMidiNative driver;
    private final ConcurrentLinkedDeque<MidiMsg> queue = new ConcurrentLinkedDeque<>();

    public MidiIn(ChuckVM vm) {
        this.driver = new ChuckMidiNative(vm, this, this.queue);
    }

    public void open(int port) {
        driver.open(port);
    }

    public boolean recv(MidiMsg msg) {
        MidiMsg m = queue.pollFirst();
        if (m != null) {
            msg.data1 = m.data1;
            msg.data2 = m.data2;
            msg.data3 = m.data3;
            return true;
        }
        return false;
    }

    public void close() {
        driver.close();
    }

    // Expose the event for 'min => now'
    public Object getEvent() {
        return this;
    }
}
