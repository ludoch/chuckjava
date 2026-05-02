import java.io.*;
import javax.sound.sampled.*;

public class AnalyzeDiag {
  public static void main(String[] args) throws Exception {
    String path = "C:\\Users\\ludo\\AppData\\Local\\Temp\\deluge-kitdiag\\kit_mastertap.wav";
    File f = new File(path);
    if (!f.exists()) { System.out.println("FILE NOT FOUND: " + path); return; }
    AudioInputStream ais = AudioSystem.getAudioInputStream(f);
    AudioFormat fmt = ais.getFormat();
    System.out.println("Format: " + fmt);
    byte[] all = ais.readAllBytes();
    ais.close();
    System.out.println("Total bytes: " + all.length);

    // Find data chunk start
    int dataOffset = 44;
    // Try to find "data" marker
    for (int i = 0; i < all.length - 4; i++) {
      if (all[i] == 'd' && all[i+1] == 'a' && all[i+2] == 't' && all[i+3] == 'a') {
        int chunkSize = (all[i+4] & 0xff) | ((all[i+5] & 0xff) << 8) | ((all[i+6] & 0xff) << 16) | ((all[i+7] & 0xff) << 24);
        dataOffset = i + 8;
        System.out.println("Found 'data' chunk at offset " + i + ", size=" + chunkSize + ", data starts at " + dataOffset);
        break;
      }
    }

    int sampleCount = (all.length - dataOffset) / 2;
    System.out.println("Samples from dataOffset: " + sampleCount);

    // Hex dump first 32 data bytes
    System.out.println("\nHex dump of data region (first 32 bytes after header):");
    for (int i = 0; i < 32 && dataOffset + i < all.length; i += 2) {
      int b0 = all[dataOffset + i] & 0xff;
      int b1 = all[dataOffset + i + 1] & 0xff;
      short pcm = (short)((b1 << 8) | b0);
      float val = pcm / 32768.0f;
      System.out.printf("  [%d]: 0x%02x 0x%02x => pcm=%d (%04x) => %.6f%n",
        dataOffset + i, b0, b1, (int)pcm, pcm & 0xffff, val);
    }

    // Convert to float and analyze
    float[] samples = new float[sampleCount];
    double peak = 0;
    int peakIdx = 0;
    double sumSq = 0;
    int zeroCrossings = 0;
    for (int i = 0; i < sampleCount; i++) {
      int offset = dataOffset + i * 2;
      short pcm = (short)((all[offset+1] << 8) | (all[offset] & 0xFF));
      samples[i] = pcm / 32768.0f;
      double abs = Math.abs(samples[i]);
      if (abs > peak) { peak = abs; peakIdx = i; }
      sumSq += samples[i] * samples[i];
      if (i > 0 && samples[i] * samples[i-1] < 0) zeroCrossings++;
    }
    double rms = Math.sqrt(sumSq / sampleCount);
    System.out.println("\nAnalysis:");
    System.out.println("  Peak: " + peak + " at sample " + peakIdx);
    System.out.println("  RMS: " + rms);
    System.out.println("  Zero crossings: " + zeroCrossings);

    // Find first non-zero
    int fnz = -1;
    for (int i = 0; i < sampleCount; i++) {
      if (Math.abs(samples[i]) > 0.001) { fnz = i; break; }
    }
    System.out.println("  First non-zero (>0.001): " + fnz);

    // Check for constant value
    int constantRun = 0;
    int maxConstantRun = 0;
    float prevVal = 0;
    for (int i = 0; i < sampleCount; i++) {
      if (Math.abs(samples[i] - prevVal) < 1e-10) {
        constantRun++;
      } else {
        constantRun = 1;
        prevVal = samples[i];
      }
      if (constantRun > maxConstantRun) maxConstantRun = constantRun;
    }
    System.out.println("  Max constant sample run: " + maxConstantRun);

    // Check if we see the specific 0.022827 (748/32768) pattern
    float expectedDC = 748.0f / 32768.0f;
    int dcCount = 0;
    for (int i = 0; i < sampleCount; i++) {
      if (Math.abs(samples[i] - expectedDC) < 1e-8) dcCount++;
    }
    System.out.println("  Samples == 0.022827 (748/32768): " + dcCount + " / " + sampleCount);

    // Check for a different common value
    java.util.Map<Integer, Integer> histogram = new java.util.HashMap<>();
    for (int i = 0; i < sampleCount; i++) {
      int quantized = (int)(samples[i] * 32768);
      histogram.put(quantized, histogram.getOrDefault(quantized, 0) + 1);
    }
    // Find top values
    System.out.println("\nTop 10 most common sample values:");
    histogram.entrySet().stream()
      .sorted((a,b) -> b.getValue().compareTo(a.getValue()))
      .limit(10)
      .forEach(e -> {
        int pcmVal = e.getKey();
        float val = pcmVal / 32768.0f;
        System.out.printf("  %d (%04x, %.6f): %d times%n", pcmVal, pcmVal & 0xffff, val, e.getValue());
      });

    // Print around first non-zero
    if (fnz > 0) {
      System.out.println("\nAround first non-zero sample " + fnz + ":");
      for (int i = Math.max(0, fnz-5); i < Math.min(sampleCount, fnz+30); i++) {
        System.out.printf("  %d: %.6f%n", i, samples[i]);
      }
    }

    // Print around peak
    System.out.println("\nAround peak at sample " + peakIdx + ":");
    for (int i = Math.max(0, peakIdx-5); i < Math.min(sampleCount, peakIdx+10); i++) {
      int pcm = (int)(samples[i] * 32768);
      System.out.printf("  %d: %.6f (%04x)%n", i, samples[i], pcm & 0xffff);
    }

    // Load original
    String origPath = "C:\\Users\\ludo\\a\\chuckjava\\deluge\\target\\classes\\SAMPLES\\DRUMS\\Kick\\808 Kick.wav";
    File origF = new File(origPath);
    AudioInputStream ais2 = AudioSystem.getAudioInputStream(origF);
    AudioFormat fmt2 = ais2.getFormat();
    if (fmt2.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || fmt2.getSampleSizeInBits() != 16) {
      AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, fmt2.getSampleRate(), 16, 1, 2, fmt2.getSampleRate(), false);
      ais2 = AudioSystem.getAudioInputStream(target, ais2);
    }
    byte[] origAll = ais2.readAllBytes();
    ais2.close();

    int origSamples = origAll.length / 2;
    float[] orig = new float[origSamples];
    double origPeak = 0;
    int origPeakIdx = 0;
    for (int i = 0; i < origSamples; i++) {
      short pcm = (short)((origAll[i*2+1] << 8) | (origAll[i*2] & 0xFF));
      orig[i] = pcm / 32768.0f;
      double a = Math.abs(orig[i]);
      if (a > origPeak) { origPeak = a; origPeakIdx = i; }
    }
    System.out.println("\nOriginal: " + origSamples + " samples, peak=" + origPeak + " at " + origPeakIdx);
    System.out.println("Original first 20 samples:");
    for (int i = 0; i < 20; i++) System.out.printf("  %d: %.6f%n", i, orig[i]);
  }
}
