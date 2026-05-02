import java.io.*;
import javax.sound.sampled.*;

public class AnalyzeDiag2 {
  public static void main(String[] args) throws Exception {
    String path = "C:\\Users\\ludo\\AppData\\Local\\Temp\\deluge-kitdiag\\kit_mastertap.wav";
    AudioInputStream ais = AudioSystem.getAudioInputStream(new File(path));
    byte[] all = ais.readAllBytes();
    ais.close();

    int dataOffset = 44;
    for (int i = 0; i < all.length - 4; i++) {
      if (all[i] == 'd' && all[i+1] == 'a' && all[i+2] == 't' && all[i+3] == 'a') {
        dataOffset = i + 8;
        break;
      }
    }

    int sampleCount = (all.length - dataOffset) / 2;
    float[] samples = new float[sampleCount];
    for (int i = 0; i < sampleCount; i++) {
      int o = dataOffset + i * 2;
      short pcm = (short)((all[o+1] << 8) | (all[o] & 0xFF));
      samples[i] = pcm / 32768.0f;
    }

    System.out.println("Identifying constant-value blocks:");
    int runStart = 0;
    float runVal = samples[0];
    for (int i = 1; i < sampleCount; i++) {
      if (Math.abs(samples[i] - runVal) > 1e-10 || i == sampleCount - 1) {
        int runLen = i - runStart;
        if (runLen >= 10) {
          System.out.printf("  [%d..%d] len=%d val=%.6f (%04x)%n",
            runStart, i-1, runLen, runVal, (int)(runVal * 32768) & 0xffff);
        }
        runStart = i;
        runVal = samples[i];
      }
    }

    // Find varying region
    int varyingStart = -1;
    int varyingEnd = -1;
    for (int i = 1000; i < sampleCount - 1; i++) {
      float diff = Math.abs(samples[i] - samples[i+1]);
      if (diff > 0.00001) {
        if (varyingStart < 0) varyingStart = i;
        varyingEnd = i;
      }
    }
    System.out.printf("\nVarying region: %d to %d (length %d)%n",
      varyingStart, varyingEnd, varyingEnd - varyingStart);

    if (varyingStart > 0) {
      int s = Math.max(0, varyingStart - 10);
      int e = Math.min(sampleCount, varyingStart + 200);
      System.out.println("Transition into varying region:");
      for (int i = s; i < e; i++) {
        System.out.printf("  %d: %.6f%n", i, samples[i]);
      }
    }

    // Check for kick-like attack
    System.out.println("\nLarge sample-to-sample changes (>0.02):");
    for (int i = 1; i < sampleCount; i++) {
      float diff = Math.abs(samples[i] - samples[i-1]);
      if (diff > 0.02) {
        System.out.printf("  Sample %d: %.6f -> %.6f (diff=%.6f)%n",
          i, samples[i-1], samples[i], diff);
      }
    }

    // Check if the varying region actually has real audio or just numerical noise
    if (varyingStart > 0) {
      System.out.println("\nVarying region sample values (every 100th):");
      for (int i = varyingStart; i < Math.min(sampleCount, varyingEnd + 1); i += 100) {
        System.out.printf("  %d: %.6f%n", i, samples[i]);
      }
    }
  }
}
