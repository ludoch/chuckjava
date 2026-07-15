package org.chuck.audio.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.*;
import org.chuck.audio.AudioSampleFormat;
import org.chuck.audio.ChuckAudio.DeviceInfo;

/** Default JavaSound implementation of {@link AudioBackend}. */
public class JavaSoundBackend implements AudioBackend {
  private static final Logger logger = Logger.getLogger(JavaSoundBackend.class.getName());

  private static final int[] STANDARD_RATES = {
    8000, 11025, 16000, 22050, 32000, 44100, 48000, 88200, 96000, 176400, 192000
  };

  @Override
  public String name() {
    return "JavaSound";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public List<DeviceInfo> getOutputDeviceInfo() {
    List<DeviceInfo> result = new ArrayList<>();
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        Mixer m = AudioSystem.getMixer(info);
        if (!m.isLineSupported(new DataLine.Info(SourceDataLine.class, null))) continue;
        DeviceInfo di = probeDevice(m, info.getName());
        if (di.maxOutputChannels() > 0) result.add(di);
      } catch (Exception ignored) {
      }
    }
    return result;
  }

  @Override
  public List<DeviceInfo> getInputDeviceInfo() {
    List<DeviceInfo> result = new ArrayList<>();
    for (Mixer.Info info : AudioSystem.getMixerInfo()) {
      try {
        Mixer m = AudioSystem.getMixer(info);
        if (!m.isLineSupported(new DataLine.Info(TargetDataLine.class, null))) continue;
        DeviceInfo di = probeDevice(m, info.getName());
        if (di.maxInputChannels() > 0) result.add(di);
      } catch (Exception ignored) {
      }
    }
    return result;
  }

  @Override
  public AudioBackendStream openStream(AudioStreamConfig config) throws Exception {
    AudioSampleFormat fmt = config.sampleFormat();
    int sr = config.sampleRate();
    int ch = config.numOutputChannels();
    SourceDataLine outLine =
        openOutputLine(fmt.toJavaAudioFormat(sr, ch), config.outputDeviceName());
    if (outLine == null) {
      // Fallback to INT16 if FLOAT32/INT24 failed
      outLine =
          openOutputLine(
              AudioSampleFormat.INT16.toJavaAudioFormat(sr, ch), config.outputDeviceName());
      if (outLine == null) {
        throw new LineUnavailableException(
            "Could not open any output data line for device: " + config.outputDeviceName());
      }
    }
    int actualSr = (int) outLine.getFormat().getSampleRate();
    int outBufBytes = outLine.getBufferSize();
    int outLatencySamples = outBufBytes / (ch * fmt.bytesPerSample);
    int effBuf =
        config.minimizeLatency()
            ? Math.max(32, outLatencySamples / config.numBuffers())
            : config.bufferSize();

    TargetDataLine inLine = null;
    int inLatencySamples = 0;
    if (config.numInputChannels() > 0) {
      AudioFormat inFmt =
          AudioSampleFormat.INT16.toJavaAudioFormat(actualSr, config.numInputChannels());
      try {
        inLine = openInputLine(inFmt, config.inputDeviceName());
        if (inLine != null) {
          inLine.open(inFmt, effBuf * config.numInputChannels() * 2 * config.numBuffers());
          inLatencySamples = inLine.getBufferSize() / (config.numInputChannels() * 2);
        }
      } catch (Exception e) {
        logger.log(Level.INFO, "[JavaSoundBackend] Microphone access failed: " + e.getMessage());
        inLine = null;
      }
    }

    return new JavaSoundBackendStream(
        config, outLine, inLine, actualSr, effBuf, outLatencySamples, inLatencySamples);
  }

  private SourceDataLine openOutputLine(AudioFormat fmt, String deviceName) {
    try {
      if (deviceName != null && !deviceName.isEmpty()) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
          if (info.getName().equals(deviceName)) {
            Mixer m = AudioSystem.getMixer(info);
            DataLine.Info dl = new DataLine.Info(SourceDataLine.class, fmt);
            if (m.isLineSupported(dl)) {
              SourceDataLine line = (SourceDataLine) m.getLine(dl);
              line.open(fmt);
              return line;
            }
          }
        }
      }
      DataLine.Info dl = new DataLine.Info(SourceDataLine.class, fmt);
      if (AudioSystem.isLineSupported(dl)) {
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(dl);
        line.open(fmt);
        return line;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private TargetDataLine openInputLine(AudioFormat fmt, String deviceName) {
    try {
      if (deviceName != null && !deviceName.isEmpty()) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
          if (info.getName().equals(deviceName)) {
            Mixer m = AudioSystem.getMixer(info);
            DataLine.Info dl = new DataLine.Info(TargetDataLine.class, fmt);
            if (m.isLineSupported(dl)) {
              return (TargetDataLine) m.getLine(dl);
            }
          }
        }
      }
      DataLine.Info dl = new DataLine.Info(TargetDataLine.class, fmt);
      if (AudioSystem.isLineSupported(dl)) {
        return (TargetDataLine) AudioSystem.getLine(dl);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private DeviceInfo probeDevice(Mixer m, String name) {
    int maxOut = 0;
    int maxIn = 0;
    List<Integer> rates = new ArrayList<>();
    List<AudioSampleFormat> outFmts = new ArrayList<>();
    List<AudioSampleFormat> inFmts = new ArrayList<>();

    for (Line.Info li : m.getSourceLineInfo()) {
      if (li instanceof DataLine.Info dli
          && SourceDataLine.class.isAssignableFrom(dli.getLineClass())) {
        for (AudioFormat af : dli.getFormats()) {
          int ch = af.getChannels();
          if (ch != AudioSystem.NOT_SPECIFIED && ch > maxOut) maxOut = ch;
        }
      }
    }
    for (Line.Info li : m.getTargetLineInfo()) {
      if (li instanceof DataLine.Info dli
          && TargetDataLine.class.isAssignableFrom(dli.getLineClass())) {
        for (AudioFormat af : dli.getFormats()) {
          int ch = af.getChannels();
          if (ch != AudioSystem.NOT_SPECIFIED && ch > maxIn) maxIn = ch;
        }
      }
    }
    if (maxOut == 0 && maxIn == 0) {
      maxOut = 2;
      maxIn = 2;
    }
    for (int rate : STANDARD_RATES) {
      boolean supported = false;
      if (maxOut > 0) {
        AudioFormat testFmt = new AudioFormat(rate, 16, Math.min(2, maxOut), true, false);
        if (m.isLineSupported(new DataLine.Info(SourceDataLine.class, testFmt))) supported = true;
      }
      if (!supported && maxIn > 0) {
        AudioFormat testFmt = new AudioFormat(rate, 16, Math.min(2, maxIn), true, false);
        if (m.isLineSupported(new DataLine.Info(TargetDataLine.class, testFmt))) supported = true;
      }
      if (supported) rates.add(rate);
    }
    int preferred = rates.isEmpty() ? 44100 : rates.get(0);
    if (rates.contains(48000)) preferred = 48000;
    else if (rates.contains(44100)) preferred = 44100;
    else if (rates.contains(22050)) preferred = 22050;

    for (AudioSampleFormat asf : AudioSampleFormat.values()) {
      if (maxOut > 0
          && m.isLineSupported(
              new DataLine.Info(
                  SourceDataLine.class, asf.toJavaAudioFormat(preferred, Math.min(2, maxOut))))) {
        outFmts.add(asf);
      }
      if (maxIn > 0
          && m.isLineSupported(
              new DataLine.Info(
                  TargetDataLine.class, asf.toJavaAudioFormat(preferred, Math.min(2, maxIn))))) {
        inFmts.add(asf);
      }
    }
    if (outFmts.isEmpty() && maxOut > 0) outFmts.add(AudioSampleFormat.INT16);
    if (inFmts.isEmpty() && maxIn > 0) inFmts.add(AudioSampleFormat.INT16);

    return new DeviceInfo(name, maxOut, maxIn, rates, preferred, outFmts, inFmts);
  }
}
