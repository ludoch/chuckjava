package org.chuck.audio;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * WvOut: Record audio to a WAV file as a UGen.
 */
public class WvOutUGen extends ChuckUGen {
    private FileOutputStream fos;
    private long totalSamples = 0;
    private float sampleRate = 44100.0f;
    private int numChannels = 1;

    public WvOutUGen() {
        super();
    }

    public WvOutUGen(float sampleRate) {
        super();
        this.sampleRate = sampleRate;
    }

    @Override
    protected float compute(float input, long systemTime) {
        if (fos != null) {
            try {
                writeSample(input);
                totalSamples++;
            } catch (IOException e) {
                System.err.println("WvOut: Write error: " + e.getMessage());
                closeFile();
            }
        }
        return input;
    }

    public void wavWrite(String filename) {
        try {
            openFile(filename);
        } catch (IOException e) {
            System.err.println("WvOut: Could not open file '" + filename + "': " + e.getMessage());
        }
    }

    public void closeFile() {
        try {
            if (fos != null) {
                finalizeWav();
                fos.close();
                fos = null;
            }
        } catch (IOException e) {
            System.err.println("WvOut: Close error: " + e.getMessage());
        }
    }

    private void openFile(String filename) throws IOException {
        if (fos != null) closeFile();
        fos = new FileOutputStream(filename);
        // Write placeholder for WAV header
        byte[] header = new byte[44];
        fos.write(header);
        totalSamples = 0;
    }

    private void writeSample(float sample) throws IOException {
        short pcm = (short) (Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
        fos.write(pcm & 0xFF);
        fos.write((pcm >> 8) & 0xFF);
    }

    private void finalizeWav() throws IOException {
        if (fos == null) return;
        
        long byteRate = (long) sampleRate * numChannels * 2;
        long dataSize = totalSamples * numChannels * 2;
        long fileSize = 36 + dataSize;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt((int) fileSize);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16); // subchunk1size
        header.putShort((short) 1); // audio format (PCM)
        header.putShort((short) numChannels);
        header.putInt((int) sampleRate);
        header.putInt((int) byteRate);
        header.putShort((short) (numChannels * 2)); // block align
        header.putShort((short) 16); // bits per sample
        header.put("data".getBytes());
        header.putInt((int) dataSize);

        try (java.nio.channels.FileChannel fc = fos.getChannel()) {
            fc.position(0);
            fc.write(header);
        }
    }
}
