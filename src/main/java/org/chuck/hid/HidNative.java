package org.chuck.hid;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Advanced HID discovery and polling using JDK 25 Foreign Function & Memory API.
 * Binds to Windows WinMM and User32 APIs.
 */
public class HidNative {
    private static final Linker linker = Linker.nativeLinker();
    private static final SymbolLookup user32 = SymbolLookup.libraryLookup("user32.dll", Arena.global());
    private static final SymbolLookup winmm = SymbolLookup.libraryLookup("winmm.dll", Arena.global());

    // --- User32: Device Discovery ---
    private static final FunctionDescriptor getRawInputDeviceListDesc = FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
    );
    private static final MethodHandle getRawInputDeviceList = user32.find("GetRawInputDeviceList")
        .map(addr -> linker.downcallHandle(addr, getRawInputDeviceListDesc)).orElse(null);

    // --- WinMM: Joystick Polling ---
    // JOYINFOEX structure size is 52 bytes
    private static final StructLayout JOYINFOEX = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("dwSize"),
        ValueLayout.JAVA_INT.withName("dwFlags"),
        ValueLayout.JAVA_INT.withName("dwXpos"),
        ValueLayout.JAVA_INT.withName("dwYpos"),
        ValueLayout.JAVA_INT.withName("dwZpos"),
        ValueLayout.JAVA_INT.withName("dwRpos"),
        ValueLayout.JAVA_INT.withName("dwUpos"),
        ValueLayout.JAVA_INT.withName("dwVpos"),
        ValueLayout.JAVA_INT.withName("dwButtons"),
        ValueLayout.JAVA_INT.withName("dwButtonNumber"),
        ValueLayout.JAVA_INT.withName("dwPOV"),
        ValueLayout.JAVA_INT.withName("dwReserved1"),
        ValueLayout.JAVA_INT.withName("dwReserved2")
    );

    private static final FunctionDescriptor joyGetPosExDesc = FunctionDescriptor.of(
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS
    );
    private static final MethodHandle joyGetPosEx = winmm.find("joyGetPosEx")
        .map(addr -> linker.downcallHandle(addr, joyGetPosExDesc)).orElse(null);

    private static final int JOY_RETURNALL = 0xFF;

    public static int countDevices() {
        if (getRawInputDeviceList == null) return 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment puiNumDevices = arena.allocate(ValueLayout.JAVA_INT);
            int result = (int) getRawInputDeviceList.invoke(MemorySegment.NULL, puiNumDevices, 16);
            return result == -1 ? 0 : puiNumDevices.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable t) { return 0; }
    }

    /**
     * Polls a joystick and returns a HidMsg if the state changed.
     */
    public static boolean pollJoystick(int id, HidMsg out, int[] lastButtons) {
        if (joyGetPosEx == null) return false;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment joyInfo = arena.allocate(JOYINFOEX);
            joyInfo.set(ValueLayout.JAVA_INT, 0, 52); // dwSize
            joyInfo.set(ValueLayout.JAVA_INT, 4, JOY_RETURNALL); // dwFlags

            int result = (int) joyGetPosEx.invoke(id, joyInfo);
            if (result != 0) return false;

            int buttons = joyInfo.get(ValueLayout.JAVA_INT, 32); // dwButtons
            int x = joyInfo.get(ValueLayout.JAVA_INT, 8);
            int y = joyInfo.get(ValueLayout.JAVA_INT, 12);

            // Simple event generation: Check if buttons changed
            if (buttons != lastButtons[0]) {
                out.type = (buttons > lastButtons[0]) ? 1 : 2; // Down or Up
                out.which = buttons ^ lastButtons[0]; // Bitmask of changed button
                lastButtons[0] = buttons;
                return true;
            }

            // Check if motion changed significantly
            float fx = (x - 32768) / 32768.0f;
            float fy = (y - 32768) / 32768.0f;
            if (Math.abs(fx - out.x) > 0.01 || Math.abs(fy - out.y) > 0.01) {
                out.type = 3; // MOUSE_MOTION (reused for joystick axes)
                out.x = fx;
                out.y = fy;
                return true;
            }

            return false;
        } catch (Throwable t) { return false; }
    }
}
