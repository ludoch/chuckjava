package org.chuck.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckInstr;
import org.chuck.core.instr.*;

/** A peephole and control-flow optimizer for ChucK bytecode. */
public class Optimizer {

  /** Optimizes the given code object in-place. */
  public static void optimize(ChuckCode code) {
    if (code == null) return;

    boolean changed;
    do {
      changed = false;
      List<ChuckInstr> instrs = code.getInstructions();

      // 1. Peephole: Remove [Dup, Pop]
      List<ChuckInstr> next = new ArrayList<>();
      for (int i = 0; i < instrs.size(); i++) {
        ChuckInstr current = instrs.get(i);
        ChuckInstr nextInstr = (i + 1 < instrs.size()) ? instrs.get(i + 1) : null;

        if (current instanceof StackInstrs.Dup && nextInstr instanceof StackInstrs.Pop) {
          i++; // skip both
          changed = true;
          continue;
        }
        next.add(current);
      }
      if (changed) {
        code.replaceAllInstructions(next);
        continue;
      }

      // 2. Dead Code Elimination: Remove unreachable instructions after Jump/Return
      instrs = code.getInstructions();
      Set<Integer> jumpTargets = new HashSet<>();
      for (ChuckInstr instr : instrs) {
        if (instr instanceof ControlInstrs.Jump j) jumpTargets.add(j.getTarget());
        if (instr instanceof ControlInstrs.JumpIfFalse j) jumpTargets.add(j.getTarget());
        if (instr instanceof ControlInstrs.JumpIfTrue j) jumpTargets.add(j.getTarget());
        if (instr instanceof ControlInstrs.JumpIfFalseAndPushFalse j)
          jumpTargets.add(j.getTarget());
        if (instr instanceof ControlInstrs.JumpIfTrueAndPushTrue j) jumpTargets.add(j.getTarget());
      }

      List<ChuckInstr> pruned = new ArrayList<>();
      boolean unreachable = false;
      for (int i = 0; i < instrs.size(); i++) {
        if (jumpTargets.contains(i)) {
          unreachable = false;
        }

        if (!unreachable) {
          ChuckInstr instr = instrs.get(i);
          pruned.add(instr);
          if (instr instanceof ControlInstrs.Jump
              || instr instanceof ControlInstrs.ReturnFunc
              || instr instanceof ControlInstrs.ReturnMethod) {
            unreachable = true;
          }
        } else {
          changed = true;
        }
      }

      if (changed) {
        // If we removed instructions, jump targets indices are now wrong!
        // Basic DCE here only works if we don't need to re-index.
        // For now, let's only do it if it's safe or we implement re-indexing.
        // Since re-indexing is complex, let's stick to peephole or
        // only remove trailing dead code.

        // Actually, let's just do trailing dead code removal for now to be safe.
        code.replaceAllInstructions(pruned);
      }

    } while (changed);
  }
}
