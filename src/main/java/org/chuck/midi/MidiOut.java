package org.chuck.midi;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;
import javax.sound.midi.*;

/**
 * MidiOut: Support for sending MIDI messages using javax.sound.midi.
 */
public class MidiOut extends ChuckObject {
    private Receiver receiver;
    private MidiDevice device;

    public MidiOut() {
        super(ChuckType.OBJECT);
    }

    public int open(int port) {
        try {
            MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
            if (port < 0 || port >= infos.length) return 0;
            
            device = MidiSystem.getMidiDevice(infos[port]);
            if (!device.isOpen()) device.open();
            receiver = device.getReceiver();
            return 1;
        } catch (MidiUnavailableException e) {
            System.err.println("MidiOut: Error opening port " + port + ": " + e.getMessage());
            return 0;
        }
    }

    public void send(MidiMsg msg) {
        if (receiver == null) return;
        try {
            ShortMessage sm = new ShortMessage();
            sm.setMessage(msg.data1, msg.data2, msg.data3);
            receiver.send(sm, -1);
        } catch (InvalidMidiDataException e) {
            System.err.println("MidiOut: Invalid MIDI data: " + e.getMessage());
        }
    }

    public void close() {
        if (receiver != null) receiver.close();
        if (device != null && device.isOpen()) device.close();
        receiver = null;
        device = null;
    }
}
