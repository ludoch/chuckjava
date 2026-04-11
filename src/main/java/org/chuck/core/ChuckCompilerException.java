package org.chuck.core;

/**
 * Thrown when an error occurs during ChucK compilation (parsing or emission).
 */
public class ChuckCompilerException extends ChuckException {
    private final String file;
    private final int line;
    private final int column;

    public ChuckCompilerException(String message, String file, int line, int column) {
        super(formatMessage(message, file, line, column));
        this.file = file;
        this.line = line;
        this.column = column;
    }

    private static String formatMessage(String message, String file, int line, int column) {
        return (file != null ? file : "unknown") + ":" + line + ":" + column + ": error: " + message;
    }

    public String getFile() { return file; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
}
