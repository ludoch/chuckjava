package org.chuck.core;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * SerialIO: Real serial communication using jSerialComm.
 */
public class SerialIO extends ChuckEvent implements AutoCloseable {
    private SerialPort port;
    private final LinkedBlockingQueue<Integer> byteQueue = new LinkedBlockingQueue<>();
    private ChuckVM attachedVM;

    public SerialIO() {
        super();
    }

    public static String[] list() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] names = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            names[i] = ports[i].getSystemPortName() + " (" + ports[i].getDescriptivePortName() + ")";
        }
        return names;
    }

    public int open(int portIndex, int baud, int binary) {
        close();
        SerialPort[] ports = SerialPort.getCommPorts();
        if (portIndex < 0 || portIndex >= ports.length) return 0;
        
        port = ports[portIndex];
        port.setBaudRate(baud);
        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        
        if (port.openPort()) {
            attachedVM = ChuckVM.CURRENT_VM.get();
            port.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }
                @Override
                public void serialEvent(SerialPortEvent event) {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) return;
                    byte[] newData = new byte[port.bytesAvailable()];
                    int numRead = port.readBytes(newData, newData.length);
                    for (int i = 0; i < numRead; i++) {
                        byteQueue.offer(newData[i] & 0xFF);
                    }
                    if (attachedVM != null) {
                        broadcast(attachedVM);
                    }
                }
            });
            return 1;
        }
        return 0;
    }

    public void write(int value) {
        if (port != null && port.isOpen()) {
            byte[] buf = { (byte)(value & 0xFF) };
            port.writeBytes(buf, 1);
        }
    }

    public int recv() {
        Integer val = byteQueue.poll();
        return val != null ? val : -1;
    }

    /** For ChucK-style recv(int & val) or similar. 
     *  In Java we might just return the value or use a holder.
     */
    public int onByte() {
        return recv() != -1 ? 1 : 0;
    }

    @Override
    public void close() {
        if (port != null) {
            port.removeDataListener();
            port.closePort();
            port = null;
        }
        byteQueue.clear();
    }
}
