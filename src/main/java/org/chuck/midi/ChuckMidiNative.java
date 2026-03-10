package org.chuck.midi;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * A production-ready MIDI input implementation using JDK 25 FFM API (Panama).
 * This binds directly to the RtMidi C library symbols.
 */
public class ChuckMidiNative {
    private final ChuckVM vm;
    private final ChuckEvent midiEvent;
    private final Arena arena;
    
    // Native Handles
    private MemorySegment rtmidiIn = MemorySegment.NULL;
    
    // Method Handles for RtMidi C API
    private MethodHandle createIn;
    private MethodHandle openPort;
    private MethodHandle getMessage;
    private MethodHandle closeIn;

    public ChuckMidiNative(ChuckVM vm) {
        this.vm = vm;
        this.midiEvent = new ChuckEvent();
        this.arena = Arena.ofShared();
        initBindings();
    }

    private void initBindings() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup stdlib = linker.defaultLookup();
        
        try {
            createIn = lookup(stdlib, "rtmidi_in_create_default", 
                FunctionDescriptor.of(ValueLayout.ADDRESS));

            openPort = lookup(stdlib, "rtmidi_open_port",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            getMessage = lookup(stdlib, "rtmidi_in_get_message",
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            closeIn = lookup(stdlib, "rtmidi_in_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        } catch (Exception e) {
            // Log but don't fail; allows running in non-native environments
        }
    }

    private MethodHandle lookup(SymbolLookup lookup, String name, FunctionDescriptor fd) {
        return lookup.find(name)
            .map(addr -> Linker.nativeLinker().downcallHandle(addr, fd))
            .orElseThrow(() -> new RuntimeException("Symbol not found: " + name));
    }

    public ChuckEvent getMidiEvent() {
        return midiEvent;
    }

    public void open(int portNumber) {
        if (createIn == null) return;
        try {
            rtmidiIn = (MemorySegment) createIn.invoke();
            MemorySegment portName = arena.allocateFrom("ChucK-Java Port");
            openPort.invoke(rtmidiIn, portNumber, portName);
            startPolling();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to open MIDI port", t);
        }
    }

    private void startPolling() {
        Thread.ofVirtual().name("MIDI-Poller").start(() -> {
            MemorySegment buffer = arena.allocate(1024);
            MemorySegment sizePtr = arena.allocate(ValueLayout.JAVA_LONG, 1024L);
            
            while (!rtmidiIn.equals(MemorySegment.NULL)) {
                try {
                    double stamp = (double) getMessage.invoke(rtmidiIn, buffer, sizePtr);
                    long size = sizePtr.get(ValueLayout.JAVA_LONG, 0);
                    
                    if (size > 0) {
                        int b1 = buffer.get(ValueLayout.JAVA_BYTE, 0) & 0xFF;
                        int b2 = size > 1 ? buffer.get(ValueLayout.JAVA_BYTE, 1) & 0xFF : 0;
                        int b3 = size > 2 ? buffer.get(ValueLayout.JAVA_BYTE, 2) & 0xFF : 0;
                        
                        vm.setGlobalInt("midi_b1", b1);
                        vm.setGlobalInt("midi_b2", b2);
                        midiEvent.broadcast(vm);
                    }
                    Thread.sleep(1);
                } catch (Throwable t) {
                    break;
                }
            }
        });
    }

    public void close() {
        if (!rtmidiIn.equals(MemorySegment.NULL)) {
            try {
                closeIn.invoke(rtmidiIn);
                rtmidiIn = MemorySegment.NULL;
            } catch (Throwable t) {}
        }
        arena.close();
    }
}
