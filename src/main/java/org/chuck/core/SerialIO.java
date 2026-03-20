package org.chuck.core;

/**
 * SerialIO: Stub for serial communication.
 */
public class SerialIO extends ChuckObject {
    public SerialIO() {
        super(ChuckType.OBJECT);
    }

    public static String[] list() {
        return new String[] { "COM1 (Stub)", "COM2 (Stub)" };
    }

    public int open(int port, int baud, int binary) {
        System.out.println("[SerialIO] Opened stub port " + port + " at " + baud + " baud.");
        return 1;
    }

    public void onByte() {}
    public void onInt() {}

    public void write(int value) {
        System.out.println("[SerialIO] Write: " + value);
    }

    public void close() {
        System.out.println("[SerialIO] Closed stub port.");
    }
}
