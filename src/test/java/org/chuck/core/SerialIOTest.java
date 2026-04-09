package org.chuck.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SerialIOTest {
    @Test
    public void testSerialIOInstantiation() {
        SerialIO serial = new SerialIO();
        assertNotNull(serial);
        serial.close();
    }

    @Test
    public void testSerialIOList() {
        String[] ports = SerialIO.list();
        assertNotNull(ports);
        // Even if no ports are found, it should be an empty array or list of available ones
        System.out.println("Available ports: " + String.join(", ", ports));
    }
}
