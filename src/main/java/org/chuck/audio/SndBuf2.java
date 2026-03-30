package org.chuck.audio;

import org.chuck.core.ChuckType;

/**
 * SndBuf2: Stereo sample playback.
 */
public class SndBuf2 extends StereoUGen {
    private float[][] samples; // [channel][sample]
    private double pos = 0.0;
    private double rate = 1.0;
    private boolean loop = false;
    private final float sampleRate;

    public SndBuf2(float sampleRate) {
        super();
        this.sampleRate = sampleRate;
        this.samples = new float[2][0];
    }

    public void setRead(String path) {
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) {
                samples = new float[2][0];
                return;
            }

            javax.sound.sampled.AudioInputStream ais = javax.sound.sampled.AudioSystem.getAudioInputStream(file);
            javax.sound.sampled.AudioFormat format = ais.getFormat();
            
            // Convert to PCM_SIGNED 16-bit if needed
            if (format.getEncoding() != javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED || format.getSampleSizeInBits() != 16) {
                javax.sound.sampled.AudioFormat targetFormat = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16, format.getChannels(),
                    format.getChannels() * 2, format.getSampleRate(), false);
                ais = javax.sound.sampled.AudioSystem.getAudioInputStream(targetFormat, ais);
                format = targetFormat;
            }

            int fileChannels = format.getChannels();
            long totalSamples = ais.getFrameLength();
            if (totalSamples > Integer.MAX_VALUE) totalSamples = Integer.MAX_VALUE;
            
            samples = new float[2][(int) totalSamples];
            byte[] buf = new byte[format.getFrameSize()];
            
            for (int i = 0; i < totalSamples; i++) {
                int read = ais.read(buf);
                if (read == -1) break;
                
                if (fileChannels >= 2) {
                    // Channel 0
                    short pcm0 = (short) ((buf[1] << 8) | (buf[0] & 0xFF));
                    samples[0][i] = pcm0 / 32768.0f;
                    // Channel 1
                    short pcm1 = (short) ((buf[3] << 8) | (buf[2] & 0xFF));
                    samples[1][i] = pcm1 / 32768.0f;
                } else {
                    // Mono to stereo
                    short pcm = (short) ((buf[1] << 8) | (buf[0] & 0xFF));
                    samples[0][i] = pcm / 32768.0f;
                    samples[1][i] = samples[0][i];
                }
            }
            ais.close();
        } catch (Exception e) {
            samples = new float[2][0];
        }
        pos = 0;
    }

    public void read(String path) { setRead(path); }

    public void rate(double r) { this.rate = r; }
    public void pos(double p) { this.pos = p; }
    public void loop(int l) { this.loop = (l != 0); }

    @Override
    protected void computeStereo(float input, long systemTime) {
        if (samples[0].length == 0 || pos >= samples[0].length || pos < 0) {
            if (loop && samples[0].length > 0) {
                pos = pos % samples[0].length;
                if (pos < 0) pos += samples[0].length;
            } else {
                lastOutChannels[0] = 0;
                lastOutChannels[1] = 0;
                return;
            }
        }

        int i0 = (int) pos;
        int i1 = (i0 + 1) % samples[0].length;
        float frac = (float) (pos - i0);

        for (int c = 0; c < 2; c++) {
            float s0 = samples[c][i0];
            float s1 = samples[c][i1];
            lastOutChannels[c] = s0 + (s1 - s0) * frac;
        }

        pos += rate;
    }
}
