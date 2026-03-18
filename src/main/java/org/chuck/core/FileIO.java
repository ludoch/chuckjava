package org.chuck.core;

import java.io.*;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * FileIO: File Input/Output utility.
 */
public class FileIO extends ChuckObject {
    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int APPEND = 4;
    public static final int BINARY = 8;
    public static final int ASCII = 16;

    private RandomAccessFile file;
    private Scanner scanner;
    private int mode;
    private String path;
    private boolean eof = false;

    public FileIO() {
        super(new ChuckType("FileIO", ChuckType.OBJECT, 0, 0));
    }

    public boolean open(String path, int mode) {
        this.path = path;
        this.mode = mode;
        this.eof = false;
        try {
            String rwm = "r";
            if ((mode & WRITE) != 0) rwm = "rw";
            if ((mode & APPEND) != 0) rwm = "rw";
            
            File f = new File(path);
            if ((mode & WRITE) != 0 && (mode & APPEND) == 0) {
                if (f.exists()) f.delete();
            }
            
            file = new RandomAccessFile(f, rwm);
            if ((mode & APPEND) != 0) file.seek(file.length());
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        try {
            if (file != null) file.close();
            file = null;
            scanner = null;
            eof = true;
        } catch (IOException ignored) {}
    }

    public boolean good() {
        if (file == null || eof) return false;
        if ((mode & WRITE) != 0) return true;
        try {
            long len = file.length();
            if (len == 0) return true; // empty file: open but not yet read
            return file.getFilePointer() < len;
        } catch (IOException e) {
            return false;
        }
    }

    public void seek(long pos) {
        try {
            if (file != null) {
                file.seek(pos);
                eof = pos >= file.length();
            }
        } catch (IOException ignored) {}
    }

    public long tell() {
        try {
            return file != null ? file.getFilePointer() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    // --- Binary read/write ---
    public long readInt(int format) {
        try {
            if (file == null) { eof = true; return 0; }
            return switch (format) {
                case 1  -> file.readByte();   // INT8
                case 2  -> file.readShort();  // INT16
                case 4  -> file.readInt();    // INT32
                case 8  -> file.readLong();   // INT64
                default -> readInt();
            };
        } catch (IOException e) { eof = true; return 0; }
    }

    public void write(long value, int format) {
        try {
            if (file == null) return;
            switch (format) {
                case 1  -> file.writeByte((int) value);
                case 2  -> file.writeShort((int) value);
                case 4  -> file.writeInt((int) value);
                case 8  -> file.writeLong(value);
                default -> write(value);
            }
        } catch (IOException ignored) {}
    }

    public double readFloat(int format) {
        try {
            if (file == null) { eof = true; return 0.0; }
            return switch (format) {
                case 16 -> file.readFloat();
                case 32 -> file.readDouble();
                default -> readFloat();
            };
        } catch (IOException e) { eof = true; return 0.0; }
    }

    public void write(double value, int format) {
        try {
            if (file == null) return;
            switch (format) {
                case 16 -> file.writeFloat((float) value);
                case 32 -> file.writeDouble(value);
                default -> write(value);
            }
        } catch (IOException ignored) {}
    }

    // --- Read operations ---
    public long readInt() {
        long v = 0;
        try {
            if (file != null) {
                String token = readToken();
                if (token.isEmpty()) eof = true;
                else {
                    try {
                        v = Long.parseLong(token);
                    } catch (NumberFormatException e) {
                        v = (long) Double.parseDouble(token);
                    }
                }
            }
        } catch (Exception e) { eof = true; }
        return v;
    }

    public double readFloat() {
        double v = 0.0;
        try {
            if (file != null) {
                String token = readToken();
                if (token.isEmpty()) eof = true;
                else v = Double.parseDouble(token);
            }
        } catch (Exception e) { eof = true; }
        return v;
    }

    public String readString() {
        String s = readToken();
        if (s.isEmpty()) eof = true;
        return s;
    }

    public boolean more() {
        return good();
    }

    public String readLine() {
        try {
            if (file == null) { eof = true; return ""; }
            String line = file.readLine();
            if (line == null) { eof = true; return ""; }
            return line;
        } catch (IOException e) { eof = true; return ""; }
    }

    private String readToken() {
        try {
            if (file == null) return "";
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = file.read()) != -1 && Character.isWhitespace(c));
            if (c == -1) { eof = true; return ""; }
            sb.append((char)c);
            while ((c = file.read()) != -1 && !Character.isWhitespace(c)) {
                sb.append((char)c);
            }
            if (c == -1) eof = true;
            return sb.toString();
        } catch (IOException e) { eof = true; return ""; }
    }

    // --- Write operations ---
    public void write(String s) {
        try {
            if (file != null) file.writeBytes(s);
        } catch (IOException ignored) {}
    }

    public void write(long v) { write(String.valueOf(v)); }
    public void write(double v) { write(String.valueOf(v)); }
}
