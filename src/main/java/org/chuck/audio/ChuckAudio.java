package org.chuck.audio;

import org.chuck.core.ChuckVM;
import javax.sound.sampled.*;
import java.io.IOException;

/**
 * Handles Audio I/O for the ChuckVM.
 * Output: SourceDataLine (playback).
 * Input:  TargetDataLine (microphone/ADC) — opened gracefully; silent if unavailable.
 */
public class ChuckAudio {
    private final ChuckVM vm;
    private final int bufferSize;
    private final int numChannels;
    private final float sampleRate;
    private SourceDataLine outputLine;
    private TargetDataLine inputLine;   // null if no mic available
    private boolean running = false;
    private float masterGain = 0.8f;
    private int verbose = 1;

    // Optional recorder
    private WvOut recorder;

    public ChuckAudio(ChuckVM vm, int bufferSize, int numChannels, float sampleRate) {
        this.vm = vm;
        this.bufferSize = bufferSize;
        this.numChannels = numChannels;
        this.sampleRate = sampleRate;
        initJavaSound();
    }

    public void setVerbose(int verbose) {
        this.verbose = verbose;
    }

    public void setMasterGain(float gain) {
        this.masterGain = gain;
    }

    private void initJavaSound() {
        AudioFormat format = new AudioFormat(sampleRate, 16, numChannels, true, false);
        // Output
        try {
            DataLine.Info outInfo = new DataLine.Info(SourceDataLine.class, format);
            outputLine = (SourceDataLine) AudioSystem.getLine(outInfo);
            outputLine.open(format, bufferSize * numChannels * 4);
        } catch (LineUnavailableException e) {
            System.err.println("Audio output unavailable: " + e.getMessage());
        }
        // Input (microphone) — optional
        try {
            DataLine.Info inInfo = new DataLine.Info(TargetDataLine.class, format);
            if (AudioSystem.isLineSupported(inInfo)) {
                inputLine = (TargetDataLine) AudioSystem.getLine(inInfo);
                inputLine.open(format, bufferSize * numChannels * 4);
            }
        } catch (LineUnavailableException | SecurityException e) {
            // No mic access — adc will produce silence
            inputLine = null;
        }
    }

    public void start() {
        if (running || outputLine == null) return;
        running = true;
        outputLine.start();
        if (inputLine != null) inputLine.start();

        Thread.ofPlatform().name("ChucK-Audio-Engine").start(() -> {
            try {
                int bytesPerBuffer = bufferSize * numChannels * 2; // 16-bit PCM
                byte[] outBuf = new byte[bytesPerBuffer];
                byte[] inBuf  = inputLine != null ? new byte[bytesPerBuffer] : null;

                while (running) {
                    // ── Capture: read only if available to avoid blocking output
                    if (inputLine != null && inBuf != null) {
                        int available = inputLine.available();
                        if (available >= inBuf.length) {
                            inputLine.read(inBuf, 0, inBuf.length);
                        }
                    }

                    double sumSq = 0;
                    // ── Per-sample processing ─────────────────────────────────────
                    for (int i = 0; i < bufferSize; i++) {
                        // Feed ADC with captured frame
                        if (inBuf != null) {
                            for (int c = 0; c < numChannels; c++) {
                                int idx = (i * numChannels + c) * 2;
                                short pcm = (short) ((inBuf[idx + 1] << 8) | (inBuf[idx] & 0xFF));
                                vm.adc.setInputSample(c, pcm / 32768.0f);
                            }
                        }

                        vm.advanceTime(1);

                        // Interleave Left/Right for stereo output
                        for (int c = 0; c < numChannels; c++) {
                            float sample = vm.getChannelLastOut(c) * masterGain;
                            sumSq += sample * sample;
                            short s16 = (short) (Math.max(-1f, Math.min(1f, sample)) * 32767f);
                            int idx = (i * numChannels + c) * 2;
                            outBuf[idx]     = (byte)  (s16 & 0xFF);
                            outBuf[idx + 1] = (byte) ((s16 >> 8) & 0xFF);
                            
                            // Record
                            if (c == 0 && recorder != null && recorder.isRecording()) {
                                 try { recorder.record(vm.getChannelLastOut(0) * masterGain, 
                                                       vm.getChannelLastOut(1) * masterGain); }
                                 catch (IOException e) {}
                            }
                        }
                    }
                    
                    if (verbose > 1) {
                        double rms = Math.sqrt(sumSq / (bufferSize * numChannels));
                        if (rms > 1e-9) {
                            System.out.printf("[Audio] RMS: %.9f\n", rms);
                        }
                    }
                    
                    outputLine.write(outBuf, 0, outBuf.length);
                }
            } catch (Throwable t) {
                System.err.println("CRITICAL: Audio Engine Thread Crashed!");
                t.printStackTrace();
                vm.print("Audio Engine Error: " + t.getMessage());
            }
        });
    }

    public void startRecording(String filename) throws IOException {
        if (recorder == null) {
            recorder = new WvOut(sampleRate, numChannels);
        }
        recorder.open(filename);
    }

    public void stopRecording() throws IOException {
        if (recorder != null) {
            recorder.close();
        }
    }

    public boolean isRecording() {
        return recorder != null && recorder.isRecording();
    }

    public void stop() {
        running = false;
        try { stopRecording(); } catch (IOException e) {}
        if (inputLine != null)  { inputLine.stop();  inputLine.close(); }
        if (outputLine != null) { outputLine.stop(); outputLine.close(); }
    }
}
