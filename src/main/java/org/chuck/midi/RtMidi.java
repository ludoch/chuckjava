package org.chuck.midi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Low-level FFM (Panama) bindings to the RtMidi C API (rtmidi_c.h). Provides a unified way for
 * MidiIn and MidiOut to use native drivers.
 */
public class RtMidi {
  private static final Logger logger = Logger.getLogger(RtMidi.class.getName());
  private static final Arena globalArena = Arena.ofShared();
  private static boolean available = false;

  // --- Core Function Handles ---
  public static MethodHandle get_port_count;
  public static MethodHandle get_port_name;

  // In
  public static MethodHandle in_create_default;
  public static MethodHandle in_free;
  public static MethodHandle in_set_callback;
  public static MethodHandle in_ignore_types;

  // Out
  public static MethodHandle out_create_default;
  public static MethodHandle out_free;
  public static MethodHandle out_send_message;

  // Common
  public static MethodHandle open_port;
  public static MethodHandle open_virtual_port;
  public static MethodHandle close_port;

  static {
    init();
  }

  private static void init() {
    String os = System.getProperty("os.name").toLowerCase();
    String libName =
        os.contains("win") ? "rtmidi.dll" : os.contains("mac") ? "librtmidi.dylib" : "librtmidi.so";

    try {
      SymbolLookup rtmidi = SymbolLookup.libraryLookup(libName, globalArena);
      Linker linker = Linker.nativeLinker();

      // Port Management
      get_port_count =
          lookup(
              rtmidi,
              linker,
              "rtmidi_get_port_count",
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      get_port_name =
          lookup(
              rtmidi,
              linker,
              "rtmidi_get_port_name",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS));

      // Midi In
      in_create_default =
          lookup(
              rtmidi,
              linker,
              "rtmidi_in_create_default",
              FunctionDescriptor.of(ValueLayout.ADDRESS));
      in_free =
          lookup(rtmidi, linker, "rtmidi_in_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
      in_set_callback =
          lookup(
              rtmidi,
              linker,
              "rtmidi_in_set_callback",
              FunctionDescriptor.ofVoid(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
      in_ignore_types =
          lookup(
              rtmidi,
              linker,
              "rtmidi_in_ignore_types",
              FunctionDescriptor.ofVoid(
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_BOOLEAN,
                  ValueLayout.JAVA_BOOLEAN,
                  ValueLayout.JAVA_BOOLEAN));

      // Midi Out
      out_create_default =
          lookup(
              rtmidi,
              linker,
              "rtmidi_out_create_default",
              FunctionDescriptor.of(ValueLayout.ADDRESS));
      out_free =
          lookup(rtmidi, linker, "rtmidi_out_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
      out_send_message =
          lookup(
              rtmidi,
              linker,
              "rtmidi_out_send_message",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT));

      // Common Port Ops
      open_port =
          lookup(
              rtmidi,
              linker,
              "rtmidi_open_port",
              FunctionDescriptor.ofVoid(
                  ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      open_virtual_port =
          lookup(
              rtmidi,
              linker,
              "rtmidi_open_virtual_port",
              FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
      close_port =
          lookup(
              rtmidi, linker, "rtmidi_close_port", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

      available = true;
      logger.info("RtMidi native library loaded successfully: " + libName);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "RtMidi native library not found (" + libName + "). Falling back to JavaSound or stubs.");
    }
  }

  private static MethodHandle lookup(
      SymbolLookup lookup, Linker linker, String name, FunctionDescriptor fd) {
    return lookup
        .find(name)
        .map(addr -> linker.downcallHandle(addr, fd))
        .orElseThrow(() -> new RuntimeException("RtMidi symbol not found: " + name));
  }

  public static boolean isAvailable() {
    return available;
  }
}
