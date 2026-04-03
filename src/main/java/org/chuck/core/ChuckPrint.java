package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Instruction to print values to the console (<<< ... >>>).
 */
public class ChuckPrint implements ChuckInstr {
    private final int numArgs;

    public ChuckPrint(int numArgs) {
        this.numArgs = numArgs;
    }

    private String formatValue(Object v) {
        switch (v) {
            case Double dv -> {
                if (Double.isInfinite(dv)) return dv > 0 ? "inf" : "-inf";
                if (Double.isNaN(dv)) return "nan";
                return String.format("%.6f", dv);
            }
            case Float fv -> {
                double dv = fv.doubleValue();
                if (Double.isInfinite(dv)) return dv > 0 ? "inf" : "-inf";
                if (Double.isNaN(dv)) return "nan";
                return String.format("%.6f", dv);
            }
            case Long lv -> { return lv.toString(); }
            case ChuckUGen ugen -> { return String.format("%.6f", (double) ugen.getLastOut()); }
            case null -> { return "null"; }
            default -> { return v.toString(); }
        }
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        StringBuilder sb = new StringBuilder();
        // Popping from stack in reverse order of how they were pushed
        int argc = numArgs;
        Object[] values = new Object[argc];
        for (int i = argc - 1; i >= 0; i--) {
            if (shred.reg.isObject(0)) {
                values[i] = shred.reg.popObject();
            } else if (shred.reg.isDouble(0)) {
                values[i] = shred.reg.popAsDouble();
            } else {
                values[i] = shred.reg.popAsLong();
            }
        }
        
        for (int i = 0; i < argc; i++) {
            sb.append(formatValue(values[i]));
            if (i < argc - 1) sb.append(" ");
        }
        
        vm.print(sb.toString() + "\n");
    }
}
