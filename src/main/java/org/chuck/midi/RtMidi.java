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
  public static MethodHandle in_create;
  public static MethodHandle in_free;
  public static MethodHandle in_set_callback;
  public static MethodHandle in_ignore_types;

  // Out
  public static MethodHandle out_create_default;
  public static MethodHandle out_create;
  public static MethodHandle out_free;
  public static MethodHandle out_send_message;

  // Common
  public static MethodHandle open_port;
  public static MethodHandle open_virtual_port;
  public static MethodHandle close_port;
  public static MethodHandle set_error_callback;
  public static MethodHandle get_compiled_api;
  public static MethodHandle api_name;
  public static MethodHandle api_display_name;

  /** Error types matching rtmidi_c.h */
  public enum ErrorType {
    WARNING(0),
    DEBUG_WARNING(1),
    UNSPECIFIED(2),
    NO_DEVICES_FOUND(3),
    INVALID_PARAMETER(4),
    MEMORY_ERROR(5),
    INVALID_PORT(6),
    DEVICE_ERROR(7),
    DRIVER_ERROR(8),
    SYSTEM_ERROR(9),
    THREAD_ERROR(10);

    public final int id;

    ErrorType(int id) {
      this.id = id;
    }

    public static ErrorType fromId(int id) {
      for (ErrorType t : values()) if (t.id == id) return t;
      return UNSPECIFIED;
    }
  }

  /**
   * Callback Descriptor for errors: void error_callback(RtMidiError::Type type, const char*
   * errorText, void* userData)
   */
  public static final FunctionDescriptor ERROR_CALLBACK_DESC =
      FunctionDescriptor.ofVoid(
          ValueLayout.JAVA_INT, // type
          ValueLayout.ADDRESS, // error text
          ValueLayout.ADDRESS // user data
          );

  /** Native API identifiers matching rtmidi_c.h */
  public enum Api {
    UNSPECIFIED(0),
    MACOSX_CORE(1),
    LINUX_ALSA(2),
    UNIX_JACK(3),
    WINDOWS_MM(4),
    RTMIDI_DUMMY(5),
    WEB_MIDI(6),
    WINDOWS_UWP(7),
    ANDROID_AMIDI(8);

    public final int id;

    Api(int id) {
      this.id = id;
    }

    public static Api fromId(int id) {
      for (Api a : values()) if (a.id == id) return a;
      return UNSPECIFIED;
    }
  }

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
      in_create =
          lookup(
              rtmidi,
              linker,
              "rtmidi_in_create",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT));
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
      out_create =
          lookup(
              rtmidi,
              linker,
              "rtmidi_out_create",
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
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
      set_error_callback =
          lookup(
              rtmidi,
              linker,
              "rtmidi_set_error_callback",
              FunctionDescriptor.ofVoid(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

      get_compiled_api =
          lookup(
              rtmidi,
              linker,
              "rtmidi_get_compiled_api",
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      api_name =
          lookup(
              rtmidi,
              linker,
              "rtmidi_api_name",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
      api_display_name =
          lookup(
              rtmidi,
              linker,
              "rtmidi_api_display_name",
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

      available = true;
      logger.info("RtMidi native library loaded successfully: " + libName);
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "RtMidi native library not found (" + libName + "). Falling back to JavaSound or stubs.");
    }
  }

  public static java.util.List<Api> getCompiledApis() {
    if (!available) return java.util.Collections.emptyList();
    try (Arena arena = Arena.ofConfined()) {
      int count = (int) get_compiled_api.invoke(MemorySegment.NULL, 0);
      MemorySegment buf = arena.allocate(ValueLayout.JAVA_INT, count);
      get_compiled_api.invoke(buf, count);
      java.util.List<Api> apis = new java.util.ArrayList<>();
      for (int i = 0; i < count; i++) {
        apis.add(Api.fromId(buf.get(ValueLayout.JAVA_INT, i * 4)));
      }
      return apis;
    } catch (Throwable t) {
      return java.util.Collections.emptyList();
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

  /** Helper to get a port name from a native RtMidi device pointer. */
  public static String getPortName(MemorySegment devicePtr, int portIndex) {
    if (!available || devicePtr.equals(MemorySegment.NULL)) return "Unknown";

    try (Arena arena = Arena.ofConfined()) {
      // First call with NULL buffer to get size
      MemorySegment sizePtr = arena.allocate(ValueLayout.JAVA_INT, 1024);
      MemorySegment buf = arena.allocate(1024);

      // rtmidi_get_port_name(device, index, buf, sizePtr)
      int result = (int) get_port_name.invoke(devicePtr, portIndex, buf, sizePtr);
      if (result > 0) {
        return buf.getString(0);
      }
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Error getting port name", t);
    }
    return "Port " + portIndex;
  }
}
