package org.chuck.audio;

/**
 * A Unit Generator for sample playback.
 * Loads a float array of samples and plays them back at a given rate.
 */
public class SndBuf extends ChuckUGen {
    private float[] samples;
    private double pos = 0.0;
    private double rate = 1.0;
    private boolean loop = false;
    private float sampleRate = 44100.0f;

    public SndBuf() {
        this.samples = new float[0];
    }

    public SndBuf(float sampleRate) {
        this.samples = new float[0];
        this.sampleRate = sampleRate;
    }

    public void setSamples(float[] samples) {
        this.samples = samples;
        this.pos = 0;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public void setPos(double pos) {
        this.pos = pos;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void set(String path) {
        setRead(path);
    }

    public void setRead(String path) {
        if (path == null || path.isEmpty()) {
            samples = new float[0];
            return;
        }

        // Handle "special:..." paths
        String p = path.toLowerCase();
        if (p.startsWith("special:")) {
            if (p.contains("doh") || p.contains("kick") || p.contains("hihat") || p.contains("snare") 
                || p.contains("glot") || p.contains("ooo")) {
                samples = new float[4410]; // 0.1s
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = (float) Math.sin(i * 0.1) * (1.0f - (float)i/samples.length);
                }
                pos = 0;
                return;
            }
        }

        // Try loading as a real file
        try {
            java.io.File file = new java.io.File(path);
            if (!file.exists()) {
                System.err.println("[Audio] SndBuf: File not found: " + file.getAbsolutePath());
                samples = new float[0];
                return;
            }

            javax.sound.sampled.AudioInputStream ais = javax.sound.sampled.AudioSystem.getAudioInputStream(file);
            javax.sound.sampled.AudioFormat format = ais.getFormat();
            System.out.println("[Audio] SndBuf: Loading " + path + " (" + format + ")");
            
            // Convert to PCM_SIGNED 16-bit if needed
            if (format.getEncoding() != javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED || format.getSampleSizeInBits() != 16) {
                javax.sound.sampled.AudioFormat targetFormat = new javax.sound.sampled.AudioFormat(
                    javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16, format.getChannels(),
                    format.getChannels() * 2, format.getSampleRate(), false);
                ais = javax.sound.sampled.AudioSystem.getAudioInputStream(targetFormat, ais);
                format = targetFormat;
            }

            int numChannels = format.getChannels();
            long totalSamples = ais.getFrameLength();
            System.out.println("[Audio] SndBuf: totalSamples=" + totalSamples + " channels=" + numChannels);
            
            if (totalSamples <= 0) {
                // Read manually if frame length is unknown
                java.util.ArrayList<Float> samplesList = new java.util.ArrayList<>();
                byte[] buf = new byte[format.getFrameSize()];
                while (ais.read(buf) != -1) {
                    float sum = 0;
                    for (int c = 0; c < numChannels; c++) {
                        int idx = c * 2;
                        short pcm = (short) ((buf[idx + 1] << 8) | (buf[idx] & 0xFF));
                        sum += pcm / 32768.0f;
                    }
                    samplesList.add(sum / numChannels);
                }
                samples = new float[samplesList.size()];
                for (int i = 0; i < samples.length; i++) samples[i] = samplesList.get(i);
            } else {
                if (totalSamples > Integer.MAX_VALUE) totalSamples = Integer.MAX_VALUE;
                samples = new float[(int) totalSamples];
                byte[] buf = new byte[format.getFrameSize()];
                for (int i = 0; i < totalSamples; i++) {
                    int read = ais.read(buf);
                    if (read == -1) break;
                    float sum = 0;
                    for (int c = 0; c < numChannels; c++) {
                        int idx = c * 2;
                        short pcm = (short) ((buf[idx + 1] << 8) | (buf[idx] & 0xFF));
                        sum += pcm / 32768.0f;
                    }
                    samples[i] = sum / numChannels;
                }
            }
            System.out.println("[Audio] SndBuf: Successfully loaded " + samples.length + " samples.");
            ais.close();
        } catch (Exception e) {
            System.err.println("[Audio] Error loading WAV file '" + path + "': " + e.getMessage());
            samples = new float[0];
        }
        pos = 0;
    }

    public long samples() {
        return samples.length;
    }

    public long length() {
        return samples.length;
    }

    public float valueAt(long index) {
        if (index < 0 || index >= samples.length) return 0.0f;
        return samples[(int) index];
    }

    public long pos() {
        return (long) pos;
    }

    public float db(float db) {
        this.gain = (float) Math.pow(10.0, db / 20.0);
        return db;
    }

    public float db() {
        return (float) (20.0 * Math.log10(this.gain));
    }

    @Override
    protected float compute(float input, long systemTime) {
        if (samples.length == 0 || pos >= samples.length || pos < 0) {
            if (loop && samples.length > 0) {
                pos = pos % samples.length;
                if (pos < 0) pos += samples.length;
            } else {
                return 0.0f;
            }
        }

        // Linear interpolation
        int i0 = (int) pos;
        int i1 = (i0 + 1) % samples.length;
        float frac = (float) (pos - i0);
        
        float s0 = samples[i0];
        float s1 = samples[i1];
        float out = s0 + (s1 - s0) * frac;

        pos += rate;
        
        return out;
    }

    public boolean isDone() {
        return !loop && (pos >= samples.length || pos < 0);
    }
}
