package org.chuck.audio.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concrete {@link AudioBackendStream} wrapping macOS CoreAudio / AudioUnit via Project Panama FFM.
 *
 * <p><b>Unverified beyond compilation</b> — written per the documented Core Audio API (Apple's
 * {@code AudioUnitProperties.h}/{@code AUComponent.h}), but this repo's sandbox has no macOS, so
 * the actual native call sequence below has never been exercised end-to-end. The sibling {@code
 * JackBackendStream} uses the same FFM-upcall render-callback shape and was verified against a real
 * {@code jackd} server; that mechanism (the upcall itself) is proven, only the CoreAudio- specific
 * property IDs/struct layouts here are new and unverified.
 *
 * <p>Negotiates an AudioComponentInstance (DefaultOutputUnit / HAL), sets the
 * AudioStreamBasicDescription to packed interleaved 32-bit float at the requested sample rate and
 * channel count, and registers a render callback that drains an {@link SpscRingBuffer} fed by
 * {@link #writeOutput}. Output only for this pass — real microphone capture needs a structurally
 * different unit ({@code kAudioUnitSubType_HALOutput} with {@code
 * kAudioOutputUnitProperty_EnableIO} on both scopes, not the simpler DefaultOutputUnit this opens)
 * and is left as silence, matching this interface's existing honest-stub convention elsewhere.
 */
public class CoreAudioBackendStream implements AudioBackendStream {
  private static final Logger logger = Logger.getLogger(CoreAudioBackendStream.class.getName());

  // AudioUnitPropertyID / AudioUnitScope constants (AUComponent.h / AudioUnitProperties.h).
  private static final int K_AUDIO_UNIT_PROPERTY_STREAM_FORMAT = 8;
  private static final int K_AUDIO_UNIT_PROPERTY_SET_RENDER_CALLBACK = 23;
  private static final int K_AUDIO_UNIT_SCOPE_GLOBAL = 0;
  private static final int K_AUDIO_UNIT_SCOPE_INPUT = 1;

  // kAudioFormatLinearPCM = 'lpcm'
  private static final int K_AUDIO_FORMAT_LINEAR_PCM = 0x6C70636D;
  // kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked
  private static final int K_LINEAR_PCM_FLAGS_FLOAT_PACKED = 0x1 | 0x8;

  // AudioStreamBasicDescription: Float64 mSampleRate; UInt32 mFormatID, mFormatFlags,
  // mBytesPerPacket, mFramesPerPacket, mBytesPerFrame, mChannelsPerFrame, mBitsPerChannel,
  // mReserved; - 40 bytes total, no implicit padding (Float64 first keeps 8-byte alignment).
  private static final StructLayout AUDIO_STREAM_BASIC_DESCRIPTION =
      MemoryLayout.structLayout(
          ValueLayout.JAVA_DOUBLE.withName("mSampleRate"),
          ValueLayout.JAVA_INT.withName("mFormatID"),
          ValueLayout.JAVA_INT.withName("mFormatFlags"),
          ValueLayout.JAVA_INT.withName("mBytesPerPacket"),
          ValueLayout.JAVA_INT.withName("mFramesPerPacket"),
          ValueLayout.JAVA_INT.withName("mBytesPerFrame"),
          ValueLayout.JAVA_INT.withName("mChannelsPerFrame"),
          ValueLayout.JAVA_INT.withName("mBitsPerChannel"),
          ValueLayout.JAVA_INT.withName("mReserved"));

  // AURenderCallbackStruct: AURenderCallback inputProc; void *inputProcRefCon; - 2 pointers.
  private static final StructLayout AURENDER_CALLBACK_STRUCT =
      MemoryLayout.structLayout(
          ValueLayout.ADDRESS.withName("inputProc"),
          ValueLayout.ADDRESS.withName("inputProcRefCon"));

  private final AudioStreamConfig config;
  private final int actualSampleRate;
  private final int effectiveBufferSize;
  private final int outputLatencySamples;
  private final int numChannels;
  private final AtomicLong underrunCount = new AtomicLong();
  private final AtomicLong overflowCount = new AtomicLong();
  private volatile boolean running = false;

  private final Linker linker;
  private final SymbolLookup audioToolbox;
  private MemorySegment audioUnitInstance = MemorySegment.NULL;
  private Arena streamArena;
  private MemorySegment renderStub;

  // Ring buffer connecting ChuckAudio's engine thread (producer, via writeOutput()) to CoreAudio's
  // own real-time render-callback thread (consumer, via the upcall below). CoreAudio decides
  // inNumberFrames per callback dynamically (unlike JACK's fixed period), which is exactly why a
  // ring buffer - not a rigid 1:1 buffer mapping - is the right connector here.
  private SpscRingBuffer ringOut;
  private float[] outScratch; // pre-allocated, no allocation inside the render callback

  // AudioComponentDescription layout (5 ints = 20 bytes)
  private static final StructLayout AUDIO_COMPONENT_DESC =
      MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("componentType"),
          ValueLayout.JAVA_INT.withName("componentSubType"),
          ValueLayout.JAVA_INT.withName("componentManufacturer"),
          ValueLayout.JAVA_INT.withName("componentFlags"),
          ValueLayout.JAVA_INT.withName("componentFlagsMask"));

  public CoreAudioBackendStream(AudioStreamConfig config, Linker linker, SymbolLookup audioToolbox)
      throws Exception {
    this.config = config;
    this.linker = linker;
    this.audioToolbox = audioToolbox;
    this.actualSampleRate = config.sampleRate();
    this.numChannels = Math.max(1, config.numOutputChannels());
    // For CoreAudio, we negotiate a very low buffer size when minimizeLatency is requested
    this.effectiveBufferSize =
        config.minimizeLatency() ? Math.max(64, config.bufferSize() / 4) : config.bufferSize();
    this.outputLatencySamples = this.effectiveBufferSize; // direct hardware buffer callback slice

    initializeNativeAudioUnit();
  }

  private void initializeNativeAudioUnit() throws Exception {
    streamArena = Arena.ofShared();
    try {
      // kAudioUnitType_Output = 'auou' (0x61756F75)
      // kAudioUnitSubType_DefaultOutput = 'def ' (0x64656620)
      // kAudioUnitManufacturer_Apple = 'appl' (0x6170706C)
      MemorySegment descSeg = streamArena.allocate(AUDIO_COMPONENT_DESC);
      descSeg.set(ValueLayout.JAVA_INT, 0, 0x61756F75);
      descSeg.set(ValueLayout.JAVA_INT, 4, 0x64656620);
      descSeg.set(ValueLayout.JAVA_INT, 8, 0x6170706C);
      descSeg.set(ValueLayout.JAVA_INT, 12, 0);
      descSeg.set(ValueLayout.JAVA_INT, 16, 0);

      MethodHandle findNext =
          linker.downcallHandle(
              audioToolbox.find("AudioComponentFindNext").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
      MemorySegment comp = (MemorySegment) findNext.invoke(MemorySegment.NULL, descSeg);
      if (comp == null || comp.equals(MemorySegment.NULL)) {
        throw new IllegalStateException("Could not find DefaultOutputUnit AudioComponent.");
      }

      MethodHandle instNew =
          linker.downcallHandle(
              audioToolbox.find("AudioComponentInstanceNew").orElseThrow(),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
      MemorySegment outInstancePtr = streamArena.allocate(ValueLayout.ADDRESS);
      int status = (int) instNew.invoke(comp, outInstancePtr);
      if (status != 0) {
        throw new IllegalStateException(
            "AudioComponentInstanceNew failed with OSStatus: " + status);
      }
      audioUnitInstance = outInstancePtr.get(ValueLayout.ADDRESS, 0);

      // Set the stream format BEFORE initializing - without this, the unit doesn't know what
      // format to expect from the render callback and nothing plays.
      setStreamFormat();

      // Register the render callback BEFORE initializing, same ordering CoreAudio examples use.
      registerRenderCallback();

      MethodHandle auInit =
          linker.downcallHandle(
              audioToolbox.find("AudioUnitInitialize").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      status = (int) auInit.invoke(audioUnitInstance);
      if (status != 0) {
        throw new IllegalStateException("AudioUnitInitialize failed with OSStatus: " + status);
      }

      int ringCapacityFrames = Math.max(effectiveBufferSize * 8, 2048);
      ringOut = new SpscRingBuffer(ringCapacityFrames, numChannels);
      int maxScratchFrames = Math.max(effectiveBufferSize * 4, 4096);
      outScratch = new float[maxScratchFrames * numChannels];

      logger.log(
          Level.INFO,
          String.format(
              "[CoreAudioBackendStream] Initialized DefaultOutputUnit successfully. SR=%dHz, Latency=%d samples (%.2f ms), ch=%d",
              actualSampleRate,
              outputLatencySamples,
              (outputLatencySamples * 1000.0 / actualSampleRate),
              numChannels));
    } catch (Throwable t) {
      if (streamArena != null) {
        streamArena.close();
        streamArena = null;
      }
      throw new Exception("CoreAudio FFM initialization error: " + t.getMessage(), t);
    }
  }

  private void setStreamFormat() throws Throwable {
    MemorySegment asbd = streamArena.allocate(AUDIO_STREAM_BASIC_DESCRIPTION);
    int bytesPerFrame = 4 * numChannels; // packed interleaved Float32
    asbd.set(ValueLayout.JAVA_DOUBLE, 0, (double) actualSampleRate);
    asbd.set(ValueLayout.JAVA_INT, 8, K_AUDIO_FORMAT_LINEAR_PCM);
    asbd.set(ValueLayout.JAVA_INT, 12, K_LINEAR_PCM_FLAGS_FLOAT_PACKED);
    asbd.set(ValueLayout.JAVA_INT, 16, bytesPerFrame); // mBytesPerPacket
    asbd.set(ValueLayout.JAVA_INT, 20, 1); // mFramesPerPacket (always 1 for PCM)
    asbd.set(ValueLayout.JAVA_INT, 24, bytesPerFrame); // mBytesPerFrame
    asbd.set(ValueLayout.JAVA_INT, 28, numChannels); // mChannelsPerFrame
    asbd.set(ValueLayout.JAVA_INT, 32, 32); // mBitsPerChannel
    asbd.set(ValueLayout.JAVA_INT, 36, 0); // mReserved

    MethodHandle setProperty =
        linker.downcallHandle(
            audioToolbox.find("AudioUnitSetProperty").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT));
    int status =
        (int)
            setProperty.invoke(
                audioUnitInstance,
                K_AUDIO_UNIT_PROPERTY_STREAM_FORMAT,
                K_AUDIO_UNIT_SCOPE_INPUT,
                0,
                asbd,
                (int) AUDIO_STREAM_BASIC_DESCRIPTION.byteSize());
    if (status != 0) {
      throw new IllegalStateException("AudioUnitSetProperty(StreamFormat) failed: " + status);
    }
  }

  private void registerRenderCallback() throws Throwable {
    MethodHandle renderHandle =
        MethodHandles.lookup()
            .findVirtual(
                CoreAudioBackendStream.class,
                "renderCallback",
                MethodType.methodType(
                    int.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    MemorySegment.class,
                    int.class,
                    int.class,
                    MemorySegment.class))
            .bindTo(this);
    renderStub =
        linker.upcallStub(
            renderHandle,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // inRefCon
                ValueLayout.ADDRESS, // ioActionFlags
                ValueLayout.ADDRESS, // inTimeStamp
                ValueLayout.JAVA_INT, // inBusNumber
                ValueLayout.JAVA_INT, // inNumberFrames
                ValueLayout.ADDRESS // ioData
                ),
            streamArena);

    MemorySegment cbStruct = streamArena.allocate(AURENDER_CALLBACK_STRUCT);
    cbStruct.set(ValueLayout.ADDRESS, 0, renderStub);
    cbStruct.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);

    MethodHandle setProperty =
        linker.downcallHandle(
            audioToolbox.find("AudioUnitSetProperty").orElseThrow(),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT));
    int status =
        (int)
            setProperty.invoke(
                audioUnitInstance,
                K_AUDIO_UNIT_PROPERTY_SET_RENDER_CALLBACK,
                K_AUDIO_UNIT_SCOPE_GLOBAL,
                0,
                cbStruct,
                (int) AURENDER_CALLBACK_STRUCT.byteSize());
    if (status != 0) {
      throw new IllegalStateException("AudioUnitSetProperty(SetRenderCallback) failed: " + status);
    }
  }

  /**
   * The render callback, invoked directly by CoreAudio's own real-time thread via the upcall stub.
   * Must never allocate, block, log, or let an exception escape into native code. Requests a single
   * interleaved buffer (mNumberBuffers=1) via the packed-interleaved stream format set above, so
   * this only ever touches {@code ioData}'s first (and only) {@code AudioBuffer}: {@code
   * mNumberChannels} at byte offset 8, {@code mDataByteSize} at offset 12, {@code mData} pointer at
   * offset 16 (AudioBufferList header {@code mNumberBuffers} at 0 + 4 bytes padding to align the
   * first AudioBuffer's leading pointer field).
   */
  private int renderCallback(
      MemorySegment inRefCon,
      MemorySegment ioActionFlags,
      MemorySegment inTimeStamp,
      int inBusNumber,
      int inNumberFrames,
      MemorySegment ioData) {
    try {
      if (ringOut != null && outScratch != null && !ioData.equals(MemorySegment.NULL)) {
        int maxFrames = outScratch.length / numChannels;
        int frames = Math.min(inNumberFrames, maxFrames);
        int got = ringOut.read(outScratch, 0, frames);
        if (got < frames) underrunCount.incrementAndGet();

        MemorySegment dataPtr = ioData.reinterpret(24).get(ValueLayout.ADDRESS, 16);
        long totalSamples = (long) inNumberFrames * numChannels;
        MemorySegment floatView =
            dataPtr.reinterpret(totalSamples * ValueLayout.JAVA_FLOAT.byteSize());
        int writeSamples = frames * numChannels;
        for (int i = 0; i < writeSamples; i++) {
          floatView.setAtIndex(ValueLayout.JAVA_FLOAT, i, outScratch[i]);
        }
        for (long i = writeSamples; i < totalSamples; i++) {
          floatView.setAtIndex(ValueLayout.JAVA_FLOAT, i, 0f);
        }
      }
    } catch (Throwable t) {
      // Swallow - an exception must never escape into the native CoreAudio callback frame.
    }
    return 0; // noErr
  }

  @Override
  public void start() {
    if (running || audioUnitInstance.equals(MemorySegment.NULL)) return;
    try {
      MethodHandle auStart =
          linker.downcallHandle(
              audioToolbox.find("AudioOutputUnitStart").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      int status = (int) auStart.invoke(audioUnitInstance);
      if (status == 0) {
        running = true;
      } else {
        logger.log(Level.SEVERE, "[CoreAudioBackendStream] AudioOutputUnitStart failed: " + status);
      }
    } catch (Throwable t) {
      logger.log(
          Level.SEVERE,
          "[CoreAudioBackendStream] Exception starting CoreAudio: " + t.getMessage(),
          t);
    }
  }

  @Override
  public void stop() {
    if (!running || audioUnitInstance.equals(MemorySegment.NULL)) return;
    try {
      running = false;
      MethodHandle auStop =
          linker.downcallHandle(
              audioToolbox.find("AudioOutputUnitStop").orElseThrow(),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      auStop.invoke(audioUnitInstance);
    } catch (Throwable t) {
      logger.log(
          Level.WARNING,
          "[CoreAudioBackendStream] Exception stopping CoreAudio: " + t.getMessage());
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
    return 0; // capture not implemented for CoreAudio in this pass, see class javadoc
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
    // Real microphone capture needs a separate HAL input unit - see class javadoc. Silence,
    // matching this interface's existing honest-stub convention rather than guessing.
    java.util.Arrays.fill(buffer, offset, offset + length, (short) 0);
    return 0;
  }

  @Override
  public void writeOutput(float[] buffer, int offset, int length) {
    if (!running || ringOut == null) return;
    int frames = length / numChannels;
    int written = ringOut.write(buffer, offset, frames);
    if (written < frames) overflowCount.incrementAndGet();
  }

  @Override
  public void close() {
    stop();
    if (audioUnitInstance != null && !audioUnitInstance.equals(MemorySegment.NULL)) {
      try {
        if (audioToolbox.find("AudioComponentInstanceDispose").isPresent()) {
          MethodHandle dispose =
              linker.downcallHandle(
                  audioToolbox.find("AudioComponentInstanceDispose").get(),
                  FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
          dispose.invoke(audioUnitInstance);
        }
      } catch (Throwable ignored) {
      }
      audioUnitInstance = MemorySegment.NULL;
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
