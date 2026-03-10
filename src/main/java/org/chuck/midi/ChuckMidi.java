package org.chuck.midi;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;

import javax.sound.midi.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Real MIDI input using javax.sound.midi (built into JDK — no native library needed).
 *
 * Usage from ChucK code:
 *   MidiIn min;
 *   MidiMsg msg;
 *   min.open(0);          // open port 0
 *   while (true) {
 *       min => now;        // wait for next message
 *       min.recv(msg);     // copy bytes into msg
 *       msg.data1 => ...;
 *   }
 */
public class ChuckMidi {
    private final ChuckVM vm;
    private final ChuckEvent midiEvent;

    // Raw bytes of the most recently received message
    public volatile int data1 = 0;
    public volatile int data2 = 0;
    public volatile int data3 = 0;

    // Queue so recv() can drain multiple messages that arrived between waits
    private final ArrayBlockingQueue<int[]> messageQueue = new ArrayBlockingQueue<>(256);

    private MidiDevice device;
    private Transmitter transmitter;

    public ChuckMidi(ChuckVM vm, ChuckEvent event) {
        this.vm = vm;
        this.midiEvent = event;
    }

    // ── Device enumeration ─────────────────────────────────────────────────────

    /** Returns a human-readable list of all available MIDI input devices. */
    public static List<String> listInputDevices() {
        List<String> names = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() != 0) {   // input-capable
                    names.add(info.getName() + " — " + info.getDescription());
                }
            } catch (MidiUnavailableException ignored) {}
        }
        return names;
    }

    /** Returns the number of available MIDI input devices. */
    public static int countInputDevices() {
        return listInputDevices().size();
    }

    // ── Open / close ───────────────────────────────────────────────────────────

    /**
     * Opens the MIDI input device at the given port index (0-based among input devices).
     * Returns true on success.
     */
    public boolean open(int portIndex) {
        close();
        List<MidiDevice.Info> inputs = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice dev = MidiSystem.getMidiDevice(info);
                if (dev.getMaxTransmitters() != 0) inputs.add(info);
            } catch (MidiUnavailableException ignored) {}
        }
        if (portIndex < 0 || portIndex >= inputs.size()) return false;
        try {
            device = MidiSystem.getMidiDevice(inputs.get(portIndex));
            device.open();
            transmitter = device.getTransmitter();
            transmitter.setReceiver(new Receiver() {
                @Override public void send(MidiMessage message, long timeStamp) {
                    byte[] raw = message.getMessage();
                    int b1 = raw.length > 0 ? raw[0] & 0xFF : 0;
                    int b2 = raw.length > 1 ? raw[1] & 0xFF : 0;
                    int b3 = raw.length > 2 ? raw[2] & 0xFF : 0;
                    data1 = b1; data2 = b2; data3 = b3;
                    messageQueue.offer(new int[]{b1, b2, b3});
                    midiEvent.broadcast(vm);   // wake any shreds waiting on min => now
                }
                @Override public void close() {}
            });
            return true;
        } catch (MidiUnavailableException e) {
            return false;
        }
    }

    public void close() {
        if (transmitter != null) { try { transmitter.close(); } catch (Exception ignored) {} transmitter = null; }
        if (device != null)      { try { device.close();      } catch (Exception ignored) {} device = null; }
    }

    // ── Message retrieval ──────────────────────────────────────────────────────

    /**
     * Copies the next queued message into msg. Returns true if a message was available.
     * Intended to be called right after a shred wakes from "min => now".
     */
    public boolean recv(MidiMsg msg) {
        int[] m = messageQueue.poll();
        if (m == null) return false;
        msg.data1 = m[0]; msg.data2 = m[1]; msg.data3 = m[2];
        return true;
    }

    public ChuckEvent getMidiEvent() { return midiEvent; }
}
