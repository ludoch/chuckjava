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
                    String formatted;
                    if (Double.isInfinite(dv)) formatted = dv > 0 ? "inf" : "-inf";
                    else if (Double.isNaN(dv)) formatted = "nan";
                    else formatted = String.format("%.6f", dv);
                    sb.append(formatted);
                }
                case Float fv -> {
                    double dv = fv.doubleValue();
                    String formatted;
                    if (Double.isInfinite(dv)) formatted = dv > 0 ? "inf" : "-inf";
                    else if (Double.isNaN(dv)) formatted = "nan";
                    else formatted = String.format("%.6f", dv);
                    sb.append(formatted);
                }
                case Long lv -> sb.append(lv.toString());
                case ChuckUGen ugen -> sb.append(String.format("%.6f", ugen.getLastOut()));
                case ChuckArray arr -> {
                    String tag = arr.vecTag;
                    if ("vec2".equals(tag) || "vec3".equals(tag) || "vec4".equals(tag)) {
                        sb.append("@(");
                        for (int j = 0; j < arr.size(); j++) {
                            if (arr.isDoubleAt(j)) sb.append(String.format("%.4f", arr.getFloat(j)));
                            else sb.append(arr.getInt(j));
                            if (j < arr.size() - 1) sb.append(",");
                        }
                        sb.append(")");
                    } else if ("complex".equals(tag)) {
                        sb.append("#(");
                        for (int j = 0; j < arr.size(); j++) {
                            if (arr.isDoubleAt(j)) sb.append(String.format("%.4f", arr.getFloat(j)));
                            else sb.append(arr.getInt(j));
                            if (j < arr.size() - 1) sb.append(",");
                        }
                        sb.append(")");
                    } else if ("polar".equals(tag)) {
                        sb.append("%(");
                        for (int j = 0; j < arr.size(); j++) {
                            if (arr.isDoubleAt(j)) sb.append(String.format("%.4f", arr.getFloat(j)));
                            else sb.append(arr.getInt(j));
                            if (j < arr.size() - 1) sb.append(",");
                        }
                        sb.append(")");
                    } else {
                        // Regular array
                        sb.append("@(");
                        for (int j = 0; j < arr.size(); j++) {
                            if (arr.isObjectAt(j)) sb.append(arr.getObject(j));
                            else if (arr.isDoubleAt(j)) sb.append(String.format("%.6f", arr.getFloat(j)));
                            else sb.append(arr.getInt(j));
                            if (j < arr.size() - 1) sb.append(",");
                        }
                        sb.append(")");
                    }
                }
                case null -> sb.append("null");
                default -> sb.append(v.toString());
            }
            if (i < numArgs - 1) sb.append(" ");
        }
        
        vm.print(sb.toString() + "\n");
    }
}
