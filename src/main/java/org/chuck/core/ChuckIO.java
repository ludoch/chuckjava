package org.chuck.core;

import java.io.PrintStream;

/**
 * Represents an IO stream in ChucK (chout, cherr).
 */
public class ChuckIO extends ChuckObject {
    private final PrintStream stream;
    private ChuckVM vm;

    public ChuckIO(PrintStream stream, ChuckVM vm) {
        super(new ChuckType("IO", ChuckType.OBJECT, 0, 0));
        this.stream = stream;
        this.vm = vm;
    }

    // Overloaded write methods for <= operator
    public ChuckIO write(String s) {
        stream.print(s);
        vm.print(s); // Also route to IDE console
        return this;
    }

    public ChuckIO write(long l) {
        stream.print(l);
        vm.print(String.valueOf(l));
        return this;
    }

    public ChuckIO write(double d) {
        stream.print(d);
        vm.print(String.valueOf(d));
        return this;
    }

    public ChuckIO write(Object o) {
        stream.print(o);
        vm.print(String.valueOf(o));
        return this;
    }

    // Static helper for IO.newline()
    public static String newline() {
        return System.lineSeparator();
    }
}
