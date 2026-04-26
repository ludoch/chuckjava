package org.chuck;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * ParityTester: A Java-based regression suite that compares ChucK-Java
 * output against native C++ ChucK.
 */
public class ParityTester {

    private static final String[] CK_FILES = {
        "01_filter_lpf.ck", "02_filter_resonz.ck", "03_stk_mandolin.ck",
        "04_stk_stifkarp.ck", "05_stk_modalbar.ck", "06_event_broadcast.ck",
        "07_shred_spork.ck", "08_envelope.ck", "09_filter_onepole.ck",
        "10_filter_twopole.ck", "11_filter_onezero.ck", "12_filter_twozero.ck",
        "13_stk_beethree.ck", "14_stk_moog.ck", "15_sndbuf.ck"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("🎸 ChucK-Java v1.5 Parity Regression Suite");
        System.out.println("-------------------------------------------");

        Path comparisonDir = Paths.get("comparison");
        if (!Files.exists(comparisonDir)) {
            System.err.println("Error: 'comparison' directory not found.");
            return;
        }

        Files.createDirectories(comparisonDir.resolve("native"));
        Files.createDirectories(comparisonDir.resolve("java"));

        int passed = 0;
        for (String ck : CK_FILES) {
            String base = ck.replace(".ck", "");
            System.out.print("[" + base + "] ... ");
            System.out.flush();

            String ckPath = comparisonDir.resolve(ck).toString();
            String nativeMainWav = "comparison/native/" + base + ".wav";
            
            // 1. Run Native ChucK (using temporary script with native paths)
            runNative(ckPath, nativeMainWav);

            // 2. Run ChucK-Java
            String javaMainWav = "comparison/java/" + base + ".wav";
            runJava(ckPath, javaMainWav);

            // 3. Compare Main File
            ComparisonResult result = compareWavs(new File(nativeMainWav), new File(javaMainWav));
            
            // 4. Handle sub-files for 06/07
            if (base.equals("06_event_broadcast")) {
                compareWavs(new File("comparison/native/06_event_broadcast_440.wav"), new File("comparison/java/06_event_broadcast_440.wav"));
                compareWavs(new File("comparison/native/06_event_broadcast_880.wav"), new File("comparison/java/06_event_broadcast_880.wav"));
            } else if (base.equals("07_shred_spork")) {
                for (int i=0; i<5; i++) {
                    compareWavs(new File("comparison/native/07_shred_spork_"+i+".wav"), new File("comparison/java/07_shred_spork_"+i+".wav"));
                }
            }

            System.out.println(result.status);
            if (result.rms < 0.2) passed++;
        }

        System.out.println("\nFinal Score: " + passed + "/" + CK_FILES.length + " cases within tolerance.");
    }

    private static void runNative(String ckFile, String wavFile) throws Exception {
        String content = Files.readString(Paths.get(ckFile));
        String nativeScript = ckFile + ".native.ck";
        Files.writeString(Paths.get(nativeScript), content.replace("comparison/java/", "comparison/native/"));
        
        ProcessBuilder pb = new ProcessBuilder("chuck", "--silent", nativeScript + ":" + wavFile, "--halt");
        Process p = pb.start();
        p.waitFor();
        Files.deleteIfExists(Paths.get(nativeScript));
    }

    private static void runJava(String ckFile, String wavFile) {
        ChuckCLI cli = new ChuckCLI();
        cli.run(new String[]{"--silent", ckFile + ":" + wavFile, "--halt", "--timeout:10", "--verbose:0"});
    }

    private static class ComparisonResult {
        double rms;
        String status;
        ComparisonResult(double rms, String status) { this.rms = rms; this.status = status; }
    }

    private static ComparisonResult compareWavs(File f1, File f2) throws IOException {
        if (!f1.exists() || !f2.exists()) return new ComparisonResult(1.0, "FAIL (Missing File)");

        byte[] b1 = Files.readAllBytes(f1.toPath());
        byte[] b2 = Files.readAllBytes(f2.toPath());

        if (b1.length < 44 || b2.length < 44) return new ComparisonResult(1.0, "FAIL (Empty File)");

        short[] d1 = bytesToShorts(b1, 44);
        short[] d2 = bytesToShorts(b2, 44);

        int n = Math.min(d1.length, d2.length);
        if (n == 0) return new ComparisonResult(1.0, "FAIL (No Audio Data)");

        double bestRms = 1.0;
        int searchRange = 100;
        for (int shift = -searchRange; shift < searchRange; shift++) {
            double sumSq = 0;
            int count = 0;
            for (int i = Math.max(0, -shift); i < Math.min(n, n - shift); i++) {
                double diff = (d1[i] - d2[i + shift]) / 32768.0;
                sumSq += diff * diff;
                count++;
            }
            if (count > 0) {
                double rms = Math.sqrt(sumSq / count);
                if (rms < bestRms) bestRms = rms;
            }
        }

        String label;
        if (bestRms == 0) label = "BIT-EXACT";
        else if (bestRms < 1e-6) label = "NEAR-EXACT (" + String.format("%.2e", bestRms) + ")";
        else if (bestRms < 0.05) label = "HIGH PARITY (" + String.format("%.4f", bestRms) + ")";
        else if (bestRms < 0.2) label = "ACCEPTABLE (" + String.format("%.4f", bestRms) + ")";
        else label = "DIVERGED (" + String.format("%.4f", bestRms) + ")";

        return new ComparisonResult(bestRms, label);
    }

    private static short[] bytesToShorts(byte[] b, int offset) {
        int n = (b.length - offset) / 2;
        short[] s = new short[n];
        ByteBuffer bb = ByteBuffer.wrap(b, offset, b.length - offset).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) s[i] = bb.getShort();
        return s;
    }
}
