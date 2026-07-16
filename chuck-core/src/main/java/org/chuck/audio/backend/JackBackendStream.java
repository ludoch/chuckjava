package org.chuck.audio.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concrete {@link AudioBackendStream} wrapping JACK via Project Panama FFM.
 *
 * <p>Opens a JACK client (`jack_client_open`), registers stereo (or N-channel) output and input
 * ports (`jack_port_register`), sets the real-time processing callback (`jack_set_process_callback`
 * — via a genuine FFM upcall, mirroring the working pattern already proven in the sibling {@code
 * rtmidijava} library's {@code JackMidiIn}), auto-connects to the system's physical playback/
 * capture ports, and activates the graph (`jack_activate`).
 *
 * <p>JACK's process callback runs on a native-owned real-time thread, not a Java thread — data is
 * handed across via {@link SpscRingBuffer}: {@link #writeOutput}/{@link #readInput} (called from
 * {@code ChuckAudio}'s engine thread) are the producer/consumer on one side, the process callback
 * is the producer/consumer on the other. The callback never allocates or blocks.
 */
public class JackBackendStream implements AudioBackendStream {
  private static final Logger logger = Logger.getLogger(JackBackendStream.class.getName());

  private static final int JACK_PORT_IS_INPUT = 0x1;
  private static final int JACK_PORT_IS_OUTPUT = 0x2;
  private static final int JACK_PORT_IS_PHYSICAL = 0x4;

  private final AudioStreamConfig config;
  private int actualSampleRate;
  private int effectiveBufferSize;
  private int outputLatencySamples;
  private final AtomicLong underrunCount = new AtomicLong();
  private final AtomicLong overflowCount = new AtomicLong();
  private volatile boolean running = false;

  private final Linker linker;
  private final SymbolLookup libJack;
  private Arena streamArena;
  private MemorySegment clientPtr = MemorySegment.NULL;

  private MemorySegment[] outputPorts = new MemorySegment[0];
  private MemorySegment[] inputPorts = new MemorySegment[0];
  private final int numOutChannels;
  private final int numInChannels;

  private MemorySegment processStub;
  private MethodHandle jackPortGetBuffer;

  // Ring buffers connecting ChuckAudio's engine thread to JACK's real-time callback thread.
  private SpscRingBuffer ringOut;
  private SpscRingBuffer ringIn; // null if no capture requested

  // Pre-allocated scratch (no allocation inside the real-time callback or on the hot writeOutput/
  // readInput path beyond the one-time float[]-growth in readInput's short[]<->float[] conversion).
  private float[] outScratch;
  private float[] inScratch;
  private float[] captureConvertScratch;

  public JackBackendStream(AudioStreamConfig config, Linker linker, SymbolLookup libJack)
      throws Exception {
    this.config = config;
    this.linker = linker;
    this.libJack = libJack;
    this.numOutChannels = Math.max(0, config.numOutputChannels());
    this.numInChannels = Math.max(0, config.numInputChannels());
    // Placeholder values until initializeJack() queries what the server actually dictates -
    // JACK clients don't choose their own sample rate/buffer size, the server does.
    this.actualSampleRate = config.sampleRate();
    this.effectiveBufferSize = config.bufferSize();
    this.outputLatencySamples = this.effectiveBufferSize;

    initializeJack();
  }

  private void initializeJack() throws Exception {
    streamArena = Arena.ofShared();
    try {
      MethodHandle clientOpen =
          linker.downcallHandle(
              libJack.find("jack_client_open").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS));
      MemorySegment clientName = streamArena.allocateFrom("ChucK-Java");
      MemorySegment statusPtr = streamArena.allocate(ValueLayout.JAVA_INT);
      clientPtr = (MemorySegment) clientOpen.invoke(clientName, 0, statusPtr);
      if (clientPtr == null || clientPtr.equals(MemorySegment.NULL)) {
        throw new IllegalStateException(
            "jack_client_open failed with status: " + statusPtr.get(ValueLayout.JAVA_INT, 0));
      }

      // The server dictates sample rate and buffer size - query what we actually got rather than
      // trusting the requested config. Valid to call any time after jack_client_open() succeeds.
      MethodHandle getSampleRate =
          linker.downcallHandle(
              libJack.find("jack_get_sample_rate").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      MethodHandle getBufferSize =
          linker.downcallHandle(
              libJack.find("jack_get_buffer_size").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      actualSampleRate = (int) getSampleRate.invoke(clientPtr);
      effectiveBufferSize = (int) getBufferSize.invoke(clientPtr);
      outputLatencySamples = effectiveBufferSize;

      MethodHandle portReg =
          linker.downcallHandle(
              libJack.find("jack_port_register").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_LONG,
                  ValueLayout.JAVA_LONG));
      MemorySegment typeName = streamArena.allocateFrom("32 bit float mono audio");

      outputPorts = new MemorySegment[numOutChannels];
      for (int c = 0; c < numOutChannels; c++) {
        String portName = numOutChannels == 2 ? (c == 0 ? "out_left" : "out_right") : "out_" + c;
        outputPorts[c] =
            (MemorySegment)
                portReg.invoke(
                    clientPtr,
                    streamArena.allocateFrom(portName),
                    typeName,
                    (long) JACK_PORT_IS_OUTPUT,
                    0L);
      }

      inputPorts = new MemorySegment[numInChannels];
      for (int c = 0; c < numInChannels; c++) {
        String portName = numInChannels == 2 ? (c == 0 ? "in_left" : "in_right") : "in_" + c;
        inputPorts[c] =
            (MemorySegment)
                portReg.invoke(
                    clientPtr,
                    streamArena.allocateFrom(portName),
                    typeName,
                    (long) JACK_PORT_IS_INPUT,
                    0L);
      }

      jackPortGetBuffer =
          linker.downcallHandle(
              libJack.find("jack_port_get_buffer").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

      // Ring buffers sized generously (8 periods) to absorb any cadence jitter between
      // ChuckAudio's writeOutput()/readInput() cadence and JACK's actual callback cadence.
      int ringCapacityFrames = Math.max(effectiveBufferSize * 8, 2048);
      if (numOutChannels > 0) ringOut = new SpscRingBuffer(ringCapacityFrames, numOutChannels);
      if (numInChannels > 0) ringIn = new SpscRingBuffer(ringCapacityFrames, numInChannels);

      // Scratch sized to comfortably exceed any nframes JACK is likely to pass per callback -
      // process() defensively clamps against these lengths regardless.
      int maxScratchFrames = Math.max(effectiveBufferSize * 4, 4096);
      if (numOutChannels > 0) outScratch = new float[maxScratchFrames * numOutChannels];
      if (numInChannels > 0) inScratch = new float[maxScratchFrames * numInChannels];

      MethodHandle processHandle =
          MethodHandles.lookup()
              .findVirtual(
                  JackBackendStream.class,
                  "process",
                  MethodType.methodType(int.class, int.class, MemorySegment.class))
              .bindTo(this);
      processStub =
          linker.upcallStub(
              processHandle,
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
              streamArena);

      MethodHandle setProcessCallback =
          linker.downcallHandle(
              libJack.find("jack_set_process_callback").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS));
      int cbStatus = (int) setProcessCallback.invoke(clientPtr, processStub, MemorySegment.NULL);
      if (cbStatus != 0) {
        throw new IllegalStateException(
            "jack_set_process_callback failed with status: " + cbStatus);
      }

      logger.log(
          Level.INFO,
          String.format(
              "[JackBackendStream] Initialized JACK client successfully. SR=%dHz, buffer=%d samples (%.2f ms), out=%d ch, in=%d ch",
              actualSampleRate,
              effectiveBufferSize,
              (effectiveBufferSize * 1000.0 / actualSampleRate),
              numOutChannels,
              numInChannels));
    } catch (Throwable t) {
      if (streamArena != null) {
        streamArena.close();
        streamArena = null;
      }
      throw new Exception("JACK FFM initialization error: " + t.getMessage(), t);
    }
  }

  /**
   * The real-time process callback, invoked directly by JACK's own thread via the upcall stub. Must
   * never allocate, block, log, or let an exception escape into native code — any of those risks
   * glitching or crashing the whole JACK graph, not just this client.
   */
  private int process(int nframes, MemorySegment arg) {
    try {
      if (numOutChannels > 0 && ringOut != null && outScratch != null) {
        int maxFrames = outScratch.length / numOutChannels;
        int frames = Math.min(nframes, maxFrames);
        int got = ringOut.read(outScratch, 0, frames);
        if (got < frames) underrunCount.incrementAndGet();
        for (int c = 0; c < outputPorts.length; c++) {
          MemorySegment portBuf =
              (MemorySegment) jackPortGetBuffer.invokeExact(outputPorts[c], nframes);
          MemorySegment floatView =
              portBuf.reinterpret((long) nframes * ValueLayout.JAVA_FLOAT.byteSize());
          for (int i = 0; i < frames; i++) {
            floatView.setAtIndex(ValueLayout.JAVA_FLOAT, i, outScratch[i * numOutChannels + c]);
          }
          for (int i = frames; i < nframes; i++) {
            floatView.setAtIndex(ValueLayout.JAVA_FLOAT, i, 0f);
          }
        }
      }

      if (numInChannels > 0 && ringIn != null && inScratch != null) {
        int maxFrames = inScratch.length / numInChannels;
        int frames = Math.min(nframes, maxFrames);
        for (int c = 0; c < inputPorts.length; c++) {
          MemorySegment portBuf =
              (MemorySegment) jackPortGetBuffer.invokeExact(inputPorts[c], nframes);
          MemorySegment floatView =
              portBuf.reinterpret((long) nframes * ValueLayout.JAVA_FLOAT.byteSize());
          for (int i = 0; i < frames; i++) {
            inScratch[i * numInChannels + c] = floatView.getAtIndex(ValueLayout.JAVA_FLOAT, i);
          }
        }
        int written = ringIn.write(inScratch, 0, frames);
        if (written < frames) overflowCount.incrementAndGet();
      }
    } catch (Throwable t) {
      // Swallow - an exception must never escape into the native JACK callback frame.
    }
    return 0;
  }

  @Override
  public void start() {
    if (running || clientPtr.equals(MemorySegment.NULL)) return;
    try {
      MethodHandle activate =
          linker.downcallHandle(
              libJack.find("jack_activate").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      int status = (int) activate.invoke(clientPtr);
      if (status == 0) {
        running = true;
        autoConnectPhysicalPorts();
      } else {
        logger.log(Level.SEVERE, "[JackBackendStream] jack_activate failed with status: " + status);
      }
    } catch (Throwable t) {
      logger.log(
          Level.SEVERE,
          "[JackBackendStream] Exception activating JACK graph: " + t.getMessage(),
          t);
    }
  }

  /**
   * Connects our output/input ports to the system's physical playback/capture ports so selecting
   * JACK is actually audible without requiring the user to manually patch it in a patchbay -
   * without this, a JACK client produces sound into the void by default.
   */
  private void autoConnectPhysicalPorts() {
    try {
      connectAll(outputPorts, "system:playback_", JACK_PORT_IS_PHYSICAL | JACK_PORT_IS_INPUT, true);
      connectAll(inputPorts, "system:capture_", JACK_PORT_IS_PHYSICAL | JACK_PORT_IS_OUTPUT, false);
    } catch (Throwable t) {
      logger.log(
          Level.FINE,
          "[JackBackendStream] Auto-connect to physical ports failed: " + t.getMessage());
    }
  }

  private void connectAll(
      MemorySegment[] ourPorts, String fallbackPrefix, long flags, boolean ourPortIsSource)
      throws Throwable {
    if (ourPorts.length == 0) return;
    List<String> physicalNames = listPorts(null, flags);
    MethodHandle portName =
        linker.downcallHandle(
            libJack.find("jack_port_name").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    MethodHandle connect =
        linker.downcallHandle(
            libJack.find("jack_connect").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS));
    for (int c = 0; c < ourPorts.length; c++) {
      String physical = c < physicalNames.size() ? physicalNames.get(c) : fallbackPrefix + (c + 1);
      MemorySegment ourNameSeg = (MemorySegment) portName.invoke(ourPorts[c]);
      try (Arena a = Arena.ofConfined()) {
        MemorySegment physicalSeg = a.allocateFrom(physical);
        int rc =
            ourPortIsSource
                ? (int) connect.invoke(clientPtr, ourNameSeg, physicalSeg)
                : (int) connect.invoke(clientPtr, physicalSeg, ourNameSeg);
        if (rc != 0) {
          logger.log(
              Level.FINE, "[JackBackendStream] jack_connect to " + physical + " returned " + rc);
        }
      }
    }
  }

  private List<String> listPorts(String namePattern, long flags) throws Throwable {
    List<String> names = new ArrayList<>();
    MethodHandle getPorts =
        linker.downcallHandle(
            libJack.find("jack_get_ports").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG));
    MethodHandle free =
        linker.downcallHandle(
            libJack.find("jack_free").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    try (Arena a = Arena.ofConfined()) {
      MemorySegment namePatternSeg =
          namePattern == null ? MemorySegment.NULL : a.allocateFrom(namePattern);
      MemorySegment ports =
          (MemorySegment) getPorts.invoke(clientPtr, namePatternSeg, MemorySegment.NULL, flags);
      if (ports.equals(MemorySegment.NULL)) return names;
      int i = 0;
      while (true) {
        MemorySegment namePtr =
            ports
                .reinterpret((i + 1) * ValueLayout.ADDRESS.byteSize())
                .get(ValueLayout.ADDRESS, (long) i * ValueLayout.ADDRESS.byteSize());
        if (namePtr == null || namePtr.equals(MemorySegment.NULL)) break;
        names.add(namePtr.reinterpret(256).getString(0));
        i++;
      }
      free.invoke(ports);
    }
    return names;
  }

  @Override
  public void stop() {
    if (!running || clientPtr.equals(MemorySegment.NULL)) return;
    try {
      running = false;
      if (libJack.find("jack_deactivate").isPresent()) {
        MethodHandle deactivate =
            linker.downcallHandle(
                libJack.find("jack_deactivate").get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        deactivate.invoke(clientPtr);
      }
    } catch (Throwable t) {
      logger.log(Level.WARNING, "[JackBackendStream] Exception stopping JACK: " + t.getMessage());
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getActualSampleRate() {
    return actualSampleRate;
  }

  @Override
  public int getEffectiveBufferSize() {
    return effectiveBufferSize;
  }

  @Override
  public int getOutputLatencySamples() {
    return outputLatencySamples;
  }

  @Override
  public int getInputLatencySamples() {
    return effectiveBufferSize;
  }

  @Override
  public long getUnderrunCount() {
    return underrunCount.get();
  }

  @Override
  public long getOverflowCount() {
    return overflowCount.get();
  }

  @Override
  public int readInput(short[] buffer, int offset, int length) {
    if (!running || ringIn == null || numInChannels <= 0) {
      java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
      return 0;
    }
    int frames = length / numInChannels;
    if (captureConvertScratch == null || captureConvertScratch.length < frames * numInChannels) {
      captureConvertScratch = new float[frames * numInChannels];
    }
    int got = ringIn.read(captureConvertScratch, 0, frames);
    int samples = frames * numInChannels;
    for (int i = 0; i < samples; i++) {
      float clamped = Math.max(-1f, Math.min(1f, captureConvertScratch[i]));
      buffer[offset + i] = (short) (clamped * 32767f);
    }
    return got * numInChannels;
  }

  @Override
  public void writeOutput(float[] buffer, int offset, int length) {
    if (!running || ringOut == null || numOutChannels <= 0) return;
    int frames = length / numOutChannels;
    int written = ringOut.write(buffer, offset, frames);
    if (written < frames) overflowCount.incrementAndGet();
  }

  @Override
  public void close() {
    stop();
    if (clientPtr != null && !clientPtr.equals(MemorySegment.NULL)) {
      try {
        if (libJack.find("jack_client_close").isPresent()) {
          MethodHandle clientClose =
              linker.downcallHandle(
                  libJack.find("jack_client_close").get(),
                  FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
          clientClose.invoke(clientPtr);
        }
      } catch (Throwable ignored) {
      }
      clientPtr = MemorySegment.NULL;
    }
    if (streamArena != null) {
      try {
        streamArena.close();
      } catch (Exception ignored) {
      }
      streamArena = null;
    }
  }
}
