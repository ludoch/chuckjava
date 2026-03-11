package org.chuck.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Sends OSC messages over UDP.
 */
public class OscOut extends org.chuck.core.ChuckObject {
    private DatagramSocket socket;
    private InetAddress targetAddress;
    private int targetPort;

    public OscOut() {
        super(new org.chuck.core.ChuckType("OscOut", org.chuck.core.ChuckType.OBJECT, 0, 0));
        try {
            socket = new DatagramSocket();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create OscOut socket", e);
        }
    }

    public void dest(String host, int port) {
        try {
            targetAddress = InetAddress.getByName(host);
            targetPort = port;
        } catch (IOException e) {
            throw new RuntimeException("Invalid host: " + host, e);
        }
    }

    public void send(OscMsg msg) {
        if (targetAddress == null) return;
        try {
            byte[] data = serialize(msg);
            DatagramPacket packet = new DatagramPacket(data, data.length, targetAddress, targetPort);
            socket.send(packet);
        } catch (IOException ignored) {}
    }

    private byte[] serialize(OscMsg msg) {
        // Simple OSC serialization: [address][tags][args]
        ByteBuffer buf = ByteBuffer.allocate(2048);
        
        writeString(buf, msg.address);
        
        StringBuilder tags = new StringBuilder(",");
        for (Object arg : msg.getArgs()) {
            if (arg instanceof Integer) tags.append("i");
            else if (arg instanceof Float || arg instanceof Double) tags.append("f");
            else if (arg instanceof String) tags.append("s");
        }
        writeString(buf, tags.toString());

        for (Object arg : msg.getArgs()) {
            if (arg instanceof Integer i) buf.putInt(i);
            else if (arg instanceof Float f) buf.putFloat(f);
            else if (arg instanceof Double d) buf.putFloat(d.floatValue());
            else if (arg instanceof String s) writeString(buf, s);
        }

        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    private void writeString(ByteBuffer buf, String s) {
        buf.put(s.getBytes());
        buf.put((byte) 0);
        int pad = 4 - (s.length() + 1) % 4;
        if (pad < 4) {
            for (int i = 0; i < pad; i++) buf.put((byte) 0);
        }
    }

    public void close() {
        if (socket != null) socket.close();
    }
}
