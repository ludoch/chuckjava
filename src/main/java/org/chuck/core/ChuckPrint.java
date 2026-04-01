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

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        StringBuilder sb = new StringBuilder();
        // Popping from stack in reverse order of how they were pushed
        Object[] values = new Object[numArgs];
        for (int i = numArgs - 1; i >= 0; i--) {
            values[i] = shred.reg.pop();
        }
        
        for (int i = 0; i < numArgs; i++) {
            Object v = values[i];
            switch (v) {
                case Double dv -> {
                    if (Double.isInfinite(dv)) sb.append(dv > 0 ? "inf" : "-inf");
                    else if (Double.isNaN(dv)) sb.append("nan");
                    else sb.append(String.format("%.6f", dv));
                }
                case Float fv -> {
                    double dv = fv.doubleValue();
                    if (Double.isInfinite(dv)) sb.append(dv > 0 ? "inf" : "-inf");
                    else if (Double.isNaN(dv)) sb.append("nan");
                    else sb.append(String.format("%.6f", dv));
                }
                case Long lv -> sb.append(lv.toString());
                case ChuckUGen ugen -> sb.append(String.format("%.6f", ugen.getLastOut()));
                case null -> sb.append("null");
                default -> sb.append(v.toString());
            }
            if (i < numArgs - 1) sb.append(" ");
        }
        
        vm.print(sb.toString() + "\n");
    }
}
