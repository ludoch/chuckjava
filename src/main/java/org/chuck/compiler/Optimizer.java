package org.chuck.compiler;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckInstr;
import org.chuck.core.instr.*;

/**
 * A peephole optimizer for ChucK bytecode. Scans the instruction stream and applies localized
 * simplifications.
 */
public class Optimizer {

  /** Optimizes the given code object in-place. */
  public static void optimize(ChuckCode code) {
    if (code == null) return;
    List<ChuckInstr> instrs = new ArrayList<>(code.getInstructions());
    boolean changed;
    do {
      changed = false;
      List<ChuckInstr> next = new ArrayList<>();
      for (int i = 0; i < instrs.size(); i++) {
        ChuckInstr current = instrs.get(i);
        ChuckInstr nextInstr = (i + 1 < instrs.size()) ? instrs.get(i + 1) : null;

        // 1. Remove [Dup, Pop]
        if (current instanceof StackInstrs.Dup && nextInstr instanceof StackInstrs.Pop) {
          i++; // skip both
          changed = true;
          continue;
        }

        // 2. Replace [PushInt(1), AddAny] with something better?
        // ChucK doesn't have IncLocal yet, but we could add it.
        // For now, let's just do simple stack removals.

        next.add(current);
      }
      if (changed) instrs = next;
    } while (changed);

    // Update the code object
    code.replaceAllInstructions(instrs);
  }
}
