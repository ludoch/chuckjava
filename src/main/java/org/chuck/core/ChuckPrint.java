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
            if (v instanceof Double || v instanceof Float) {
                sb.append(String.format("%.6f", ((Number) v).doubleValue()));
            } else if (v instanceof ChuckUGen ugen) {
                sb.append(String.format("%.6f", ugen.getLastOut()));
            } else if (v instanceof ChuckArray arr) {
                sb.append("@(");
                for (int j = 0; j < arr.size(); j++) {
                    if (arr.isObjectAt(j)) sb.append(arr.getObject(j));
                    else if (arr.isDoubleAt(j)) sb.append(String.format("%.6f", arr.getFloat(j)));
                    else sb.append(arr.getInt(j));
                    if (j < arr.size() - 1) sb.append(",");
                }
                sb.append(")");
            } else if (v instanceof String || v instanceof ChuckString) {
                sb.append(v);
            } else {
                sb.append(v);
            }
            if (i < numArgs - 1) sb.append(" ");
        }
        
        vm.print(sb.toString() + "\n");
    }
}
