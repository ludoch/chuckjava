package org.chuck.audio;

import org.chuck.core.ChuckVM;
import javax.sound.sampled.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Handles Audio I/O for the ChuckVM.
 */
public class ChuckAudio {
    private final ChuckVM vm;
    private final int bufferSize;
    private final int numChannels;
    private final float sampleRate;
    private SourceDataLine line;
    private boolean running = false;
    
    // Optional recorder
    private WvOut recorder;

    public ChuckAudio(ChuckVM vm, int bufferSize, int numChannels, float sampleRate) {
        this.vm = vm;
        this.bufferSize = bufferSize;
        this.numChannels = numChannels;
        this.sampleRate = sampleRate;
        initJavaSound();
    }

    private void initJavaSound() {
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, numChannels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, bufferSize * numChannels * 4);
        } catch (LineUnavailableException e) {
            System.err.println("Audio output unavailable: " + e.getMessage());
        }
    }

    public void start() {
        if (running || line == null) return;
        running = true;
        line.start();

        Thread.ofPlatform().name("ChucK-Audio-Engine").start(() -> {
            byte[] byteBuffer = new byte[bufferSize * numChannels * 2];
            
            while (running) {
                for (int i = 0; i < bufferSize; i++) {
                    vm.advanceTime(1); 
                    
                    float left = vm.getChannelLastOut(0);
                    float right = numChannels > 1 ? vm.getChannelLastOut(1) : left;

                    // Record if active
                    if (recorder != null && recorder.isRecording()) {
                        try {
                            recorder.record(left, right);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    // Convert to 16-bit PCM for system output
                    for (int c = 0; c < numChannels; c++) {
                        float sample = (c == 0) ? left : right;
                        short pcm = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
                        int idx = (i * numChannels + c) * 2;
                        byteBuffer[idx] = (byte) (pcm & 0xFF);
                        byteBuffer[idx + 1] = (byte) ((pcm >> 8) & 0xFF);
                    }
                }
                line.write(byteBuffer, 0, byteBuffer.length);
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
        try {
            stopRecording();
        } catch (IOException e) {}
        if (line != null) {
            line.stop();
            line.close();
        }
    }
}
