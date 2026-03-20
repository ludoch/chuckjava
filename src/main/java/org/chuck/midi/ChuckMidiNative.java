package org.chuck.midi;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A cross-platform MIDI input implementation using JDK 25 FFM API (Panama).
 * Binds to RtMidi (.dll, .so, or .dylib) dynamically based on the OS.
 */
public class ChuckMidiNative {
    private final ChuckVM vm;
    private final ChuckEvent event;
    private final Arena arena;
    
    private final ConcurrentLinkedDeque<MidiMsg> queue;
    
    private MemorySegment rtmidiIn = MemorySegment.NULL;
    
    private MethodHandle createIn;
    private MethodHandle openPort;
    private MethodHandle getMessage;
    private MethodHandle closeIn;

    public ChuckMidiNative(ChuckVM vm, ChuckEvent event, ConcurrentLinkedDeque<MidiMsg> queue) {
        this.vm = vm;
        this.event = event;
        this.arena = Arena.ofShared();
        this.queue = queue;
        initBindings();
    }

    private void initBindings() {
        Linker linker = Linker.nativeLinker();
        String os = System.getProperty("os.name").toLowerCase();
        String libName = os.contains("win") ? "rtmidi.dll" : 
                         os.contains("mac") ? "librtmidi.dylib" : "librtmidi.so";
        
        try {
            SymbolLookup rtmidi = SymbolLookup.libraryLookup(libName, arena);
            
            createIn = lookup(rtmidi, "rtmidi_in_create_default", 
                FunctionDescriptor.of(ValueLayout.ADDRESS));

            openPort = lookup(rtmidi, "rtmidi_open_port",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            getMessage = lookup(rtmidi, "rtmidi_in_get_message",
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            closeIn = lookup(rtmidi, "rtmidi_in_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

        } catch (Exception e) {
            // Silently fail to allow IDE to run without native MIDI libraries present
        }
    }

    private MethodHandle lookup(SymbolLookup lookup, String name, FunctionDescriptor fd) {
        return lookup.find(name)
            .map(addr -> Linker.nativeLinker().downcallHandle(addr, fd))
            .orElseThrow(() -> new RuntimeException("Symbol not found: " + name));
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
                        MidiMsg msg = new MidiMsg();
                        msg.data1 = buffer.get(ValueLayout.JAVA_BYTE, 0) & 0xFF;
                        msg.data2 = size > 1 ? buffer.get(ValueLayout.JAVA_BYTE, 1) & 0xFF : 0;
                        msg.data3 = size > 2 ? buffer.get(ValueLayout.JAVA_BYTE, 2) & 0xFF : 0;
                        
                        queue.addLast(msg);
                        event.broadcast(vm);
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
