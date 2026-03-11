package org.chuck.network;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Receives OSC messages over UDP.
 */
public class OscIn extends org.chuck.core.ChuckObject {
    private DatagramSocket socket;
    private boolean running = false;
    private final Map<String, OscEvent> addressToEvent = new ConcurrentHashMap<>();
    private final ChuckVM vm;

    public OscIn(ChuckVM vm) {
        super(new org.chuck.core.ChuckType("OscIn", org.chuck.core.ChuckType.OBJECT, 0, 0));
        this.vm = vm;
    }

    public void port(int port) {
        if (socket != null) socket.close();
        try {
            socket = new DatagramSocket(port);
            running = true;
            startListening();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open OSC port " + port, e);
        }
    }

    public OscEvent event(String address) {
        return addressToEvent.computeIfAbsent(address, k -> new OscEvent());
    }

    private void startListening() {
        Thread.ofVirtual().name("OSC-In-Listener").start(() -> {
            byte[] buffer = new byte[2048];
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    parseAndDispatch(packet.getData(), packet.getLength());
                } catch (IOException e) {
                    break;
                }
            }
        });
    }

    private void parseAndDispatch(byte[] data, int length) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
            String address = readString(buf);
            if (address == null || !address.startsWith("/")) return;

            String typeTag = readString(buf);
            if (typeTag == null || !typeTag.startsWith(",")) return;

            OscMsg msg = new OscMsg();
            msg.address = address;

            for (int i = 1; i < typeTag.length(); i++) {
                char t = typeTag.charAt(i);
                switch (t) {
                    case 'i' -> msg.addInt(buf.getInt());
                    case 'f' -> msg.addFloat(buf.getFloat());
                    case 's' -> msg.addString(readString(buf));
                }
            }

            OscEvent event = addressToEvent.get(address);
            if (event != null) {
                event.pushMsg(msg);
                event.broadcast(vm);
            }
        } catch (Exception ignored) {}
    }

    private String readString(ByteBuffer buf) {
        StringBuilder sb = new StringBuilder();
        while (buf.hasRemaining()) {
            byte b = buf.get();
            if (b == 0) break;
            sb.append((char) b);
        }
        // OSC strings are null-terminated and padded to 4 bytes
        int pad = 4 - (sb.length() + 1) % 4;
        if (pad < 4) {
            for (int i = 0; i < pad; i++) if (buf.hasRemaining()) buf.get();
        }
        return sb.toString();
    }

    public void close() {
        running = false;
        if (socket != null) socket.close();
    }

    public static class OscEvent extends ChuckEvent {
        private final java.util.Deque<OscMsg> messages = new java.util.ArrayDeque<>();

        public synchronized void pushMsg(OscMsg msg) {
            messages.addLast(msg);
            // This would trigger VM broadcast logic
        }

        public synchronized OscMsg nextMsg() {
            return messages.pollFirst();
        }
    }
}
