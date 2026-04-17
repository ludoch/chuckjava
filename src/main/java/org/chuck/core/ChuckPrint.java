package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/** Instruction to print values to the console (<<< ... >>>). */
public class ChuckPrint implements ChuckInstr {
  private final int numArgs;

  public ChuckPrint(int numArgs) {
    this.numArgs = numArgs;
  }

  private String formatValue(Object v, boolean includeTag) {
    String tag = "";
    if (includeTag && Boolean.getBoolean("chuck.print.tags")) {
      tag =
          switch (v) {
            case Double _, Float _ -> " :(float)";
            case Long _, Integer _ -> " :(int)";
            case ChuckString _ -> " :(string)";
            case ChuckArray a -> " :(" + (a.vecTag != null ? a.vecTag : "array") + ")";
            case null -> "";
            default -> " :(" + v.getClass().getSimpleName() + ")";
          };
    }

    String val;
    switch (v) {
      case Double dv -> {
        val = formatFloat(dv);
      }
      case Float fv -> {
        val = formatFloat(fv.doubleValue());
      }
      case Long lv -> {
        val = lv.toString();
      }
      case Integer iv -> {
        val = iv.toString();
      }
      case ChuckUGen ugen -> {
        val = formatFloat(ugen.getLastOut());
      }
      case ChuckString cs -> {
        String s = cs.toString();
        if (includeTag && Boolean.getBoolean("chuck.print.tags")) val = "\"" + s + "\"";
        else val = s;
      }
      case String s -> {
        if (includeTag && Boolean.getBoolean("chuck.print.tags")) val = "\"" + s + "\"";
        else val = s;
      }
      case ChuckArray a -> {
        if ("complex".equals(a.vecTag)) {
          val = "#(" + formatFloat(a.getFloat(0)) + "," + formatFloat(a.getFloat(1)) + ")";
        } else if ("polar".equals(a.vecTag)) {
          val =
              "%("
                  + formatFloat(a.getFloat(0))
                  + ","
                  + formatFloat(a.getFloat(1) / Math.PI)
                  + "*pi)";
        } else {
          val = a.toString();
        }
      }
      case null -> {
        val = "0";
        if (includeTag && Boolean.getBoolean("chuck.print.tags")) {
          return "0 :(UserObject)";
        }
      }
      default -> {
        val = v.toString();
      }
    }
    return val + tag;
  }

  private String formatFloat(double dv) {
    if (Double.isInfinite(dv)) return dv > 0 ? "inf" : "-inf";
    if (Double.isNaN(dv)) return "nan";

    return String.format("%.6f", dv);
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

    boolean includeTags = (argc == 1);
    for (int i = 0; i < argc; i++) {
      sb.append(formatValue(values[i], includeTags));
      if (i < argc - 1) sb.append(" ");
    }

    vm.print(sb.toString() + "\n");
  }
}
