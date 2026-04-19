package org.chuck.core;

/**
 * ChucK ConsoleInput — reads lines from stdin. Usage in ChucK: ConsoleInput cin; cin.prompt("enter:
 * ") => string line => now; // blocks until Enter cin.readline() => string line;
 */
public class ConsoleInput extends ChuckEvent {
  private static final java.io.BufferedReader READER =
      new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
  private String lastLine = "";

  public ConsoleInput() {
    super();
  }

  /** Block until the user presses Enter; return the line (without newline). */
  public String readline() {
    try {
      lastLine = READER.readLine();
      return lastLine != null ? lastLine : "";
    } catch (Exception e) {
      return "";
    }
  }

  public boolean more() {
    return true; // Simplified
  }

  public String getLine() {
    return lastLine;
  }

  /** Print a prompt string, then block until the user presses Enter. */
  public ConsoleInput prompt(String msg) {
    System.out.print(msg);
    System.out.flush();
    readline();
    return this;
  }

  /** Non-blocking: 1 if a line is ready to read, 0 otherwise. */
  public long ready() {
    try {
      return READER.ready() ? 1L : 0L;
    } catch (Exception e) {
      return 0L;
    }
  }

  /** Always returns true — ConsoleInput can always be waited on. */
  public boolean can_wait() {
    return true;
  }
}
