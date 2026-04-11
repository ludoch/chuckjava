package org.chuck;

/**
 * Entry point for the GraalVM native image build. Bypasses JavaFX and the reflective IDE launcher;
 * all arguments are passed directly to ChuckCLI which handles headless .ck execution.
 */
public class NativeMain {
  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("ChucK-Java (native) - Usage: chuck [options] file.ck ...");
      System.out.println("Run with --help for full option list.");
      System.exit(0);
    }
    new ChuckCLI().run(args);
  }
}
