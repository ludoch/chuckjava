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
    private OscMsg currentMsg;

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

    /** Begin building a new OSC message with the given address. */
    public OscOut start(String address) {
        currentMsg = new OscMsg();
        currentMsg.address = address;
        return this;
    }

    public OscOut add(String s) {
        if (currentMsg == null) currentMsg = new OscMsg();
        currentMsg.addString(s);
        return this;
    }

    public OscOut add(long i) {
        if (currentMsg == null) currentMsg = new OscMsg();
        currentMsg.addInt((int) i);
        return this;
    }

    public OscOut add(double d) {
        if (currentMsg == null) currentMsg = new OscMsg();
        currentMsg.addFloat((float) d);
        return this;
    }

    /** Send the message built with start()/add(). */
    public void send() {
        if (currentMsg != null) {
            send(currentMsg);
            currentMsg = null;
        }
    }

    /** Send an explicit OscMsg object. */
    public void send(OscMsg msg) {
        if (targetAddress == null) return;
        try {
            byte[] data = serialize(msg);
            DatagramPacket packet = new DatagramPacket(data, data.length, targetAddress, targetPort);
            socket.send(packet);
        } catch (IOException ignored) {}
    }

    /** Send an OscBundle object. */
    public void send(OscBundle bundle) {
        if (targetAddress == null) return;
        try {
            ByteBuffer buf = ByteBuffer.allocate(4096);
            serializeBundle(bundle, buf);
            byte[] result = new byte[buf.position()];
            buf.flip();
            buf.get(result);
            DatagramPacket packet = new DatagramPacket(result, result.length, targetAddress, targetPort);
            socket.send(packet);
        } catch (IOException ignored) {}
    }

    private void serializeBundle(OscBundle bundle, ByteBuffer buf) {
        writeOscString(buf, "#bundle");
        buf.putLong(1L); // Timetag: immediate (1)

        for (Object element : bundle.getElements()) {
            switch (element) {
                case OscMsg msg -> {
                    byte[] data = serialize(msg);
                    buf.putInt(data.length);
                    buf.put(data);
                }
                case OscBundle sub -> {
                    ByteBuffer subBuf = ByteBuffer.allocate(2048);
                    serializeBundle(sub, subBuf);
                    subBuf.flip();
                    buf.putInt(subBuf.remaining());
                    buf.put(subBuf);
                }
                case null -> {}
                default -> {}
            }
        }
    }

    private byte[] serialize(OscMsg msg) {
        ByteBuffer buf = ByteBuffer.allocate(2048);

        writeOscString(buf, msg.address);

        StringBuilder tags = new StringBuilder(",");
        for (Object arg : msg.getArgs()) {
            switch (arg) {
                case Integer _ -> tags.append("i");
                case Long _ -> tags.append("i");
                case Float _, Double _ -> tags.append("f");
                case String _ -> tags.append("s");
                case null -> {}
                default -> {}
            }
        }
        writeOscString(buf, tags.toString());

        for (Object arg : msg.getArgs()) {
            switch (arg) {
                case Integer i -> buf.putInt(i);
                case Long l -> buf.putInt(l.intValue());
                case Float f -> buf.putFloat(f);
                case Double d -> buf.putFloat(d.floatValue());
                case String s -> writeOscString(buf, s);
                case null -> {}
                default -> {}
            }
        }

        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    private void writeOscString(ByteBuffer buf, String s) {
        buf.put(s.getBytes());
        buf.put((byte) 0);
        int total = s.length() + 1;
        int pad = (4 - total % 4) % 4;
        for (int i = 0; i < pad; i++) buf.put((byte) 0);
    }

    public void close() {
        if (socket != null) socket.close();
    }
}
