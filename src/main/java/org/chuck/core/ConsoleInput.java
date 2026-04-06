package org.chuck.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * ChucK ConsoleInput — reads lines from stdin.
 * Usage in ChucK:
 *   ConsoleInput cin;
 *   cin.prompt("enter: ") => string line => now;  // blocks until Enter
 *   cin.readline() => string line;
 */
public class ConsoleInput extends ChuckObject {
    private static final BufferedReader READER =
            new BufferedReader(new InputStreamReader(System.in));

    public ConsoleInput() {
        super(ChuckType.OBJECT);
    }

    /** Block until the user presses Enter; return the line (without newline). */
    public String readline() {
        try {
            String line = READER.readLine();
            return line != null ? line : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Print a prompt string, then block until the user presses Enter. */
    public String prompt(String msg) {
        System.out.print(msg);
        System.out.flush();
        return readline();
    }

    /** Non-blocking: 1 if a line is ready to read, 0 otherwise. */
    public long ready() {
        try {
            return READER.ready() ? 1L : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Always returns 1 — ConsoleInput can always be waited on. */
    public long can_wait() { return 1L; }
}
