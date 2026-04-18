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

      // 1. Peephole: Constant Folding and simple removals
      List<ChuckInstr> next = new ArrayList<>();
      for (int i = 0; i < instrs.size(); i++) {
        ChuckInstr i1 = instrs.get(i);
        ChuckInstr i2 = (i + 1 < instrs.size()) ? instrs.get(i + 1) : null;
        ChuckInstr i3 = (i + 2 < instrs.size()) ? instrs.get(i + 2) : null;
        ChuckInstr i4 = (i + 3 < instrs.size()) ? instrs.get(i + 3) : null;

        /*
        // 1a. Remove [Dup, Pop]
        if (i1 instanceof StackInstrs.Dup && i2 instanceof StackInstrs.Pop) {
          i++; // skip both
          changed = true;
          continue;
        }

        // 1b. Constant Folding: [PushInt(a), PushInt(b), AddInt] -> [PushInt(a+b)]
        if (i1 instanceof PushInstrs.PushInt p1
            && i2 instanceof PushInstrs.PushInt p2
            && i3 != null) {
          long v1 = p1.getVal(), v2 = p2.getVal();
          if (i3 instanceof ArithmeticInstrs.AddInt) {
            next.add(new PushInstrs.PushInt(v1 + v2));
            i += 2;
            changed = true;
            continue;
          }
          if (i3 instanceof ArithmeticInstrs.MinusInt) {
            next.add(new PushInstrs.PushInt(v1 - v2));
            i += 2;
            changed = true;
            continue;
          }
          if (i3 instanceof ArithmeticInstrs.TimesInt) {
            next.add(new PushInstrs.PushInt(v1 * v2));
            i += 2;
            changed = true;
            continue;
          }
          if (i3 instanceof ArithmeticInstrs.DivideInt && v2 != 0) {
            next.add(new PushInstrs.PushInt(v1 / v2));
            i += 2;
            changed = true;
            continue;
          }
        }

        // 1c. Constant Folding: [LdcFloat(a), LdcFloat(b), AddFloat] -> [LdcFloat(a+b)]
        if (i1 instanceof PushInstrs.LdcFloat p1
            && i2 instanceof PushInstrs.LdcFloat p2
            && i3 != null) {
          Double v1 = (Double) code.getConstant(p1.getIndex());
          Double v2 = (Double) code.getConstant(p2.getIndex());
          if (v1 != null && v2 != null) {
            if (i3 instanceof ArithmeticInstrs.AddFloat) {
              next.add(new PushInstrs.LdcFloat(code.addConstant(v1 + v2)));
              i += 2;
              changed = true;
              continue;
            }
            if (i3 instanceof ArithmeticInstrs.MinusFloat) {
              next.add(new PushInstrs.LdcFloat(code.addConstant(v1 - v2)));
              i += 2;
              changed = true;
              continue;
            }
            if (i3 instanceof ArithmeticInstrs.TimesFloat) {
              next.add(new PushInstrs.LdcFloat(code.addConstant(v1 * v2)));
              i += 2;
              changed = true;
              continue;
            }
            if (i3 instanceof ArithmeticInstrs.DivideFloat && v2 != 0) {
              next.add(new PushInstrs.LdcFloat(code.addConstant(v1 / v2)));
              i += 2;
              changed = true;
              continue;
            }
          }
        }

        // 1d. IncLocal Optimization: [LoadLocalInt(o), PushInt(d), AddInt, StoreLocalInt(o)] ->
        // [IncLocalInt(o, d)]
        if (i1 instanceof VarInstrs.LoadLocalInt l
            && i2 instanceof PushInstrs.PushInt p
            && i3 instanceof ArithmeticInstrs.AddInt
            && i4 instanceof VarInstrs.StoreLocalInt s) {
          if (l.getOffset() == s.getOffset()) {
            next.add(new VarInstrs.IncLocalInt(l.getOffset(), p.getVal()));
            i += 3;
            changed = true;
            continue;
          }
        }

        // 1e. Redundant Load Pruning: [StoreLocalInt(o), LoadLocalInt(o)] -> [StoreLocalInt(o)]
        if (i1 instanceof VarInstrs.StoreLocalInt s && i2 instanceof VarInstrs.LoadLocalInt l) {
          if (s.getOffset() == l.getOffset()) {
            next.add(s);
            i += 1;
            changed = true;
            continue;
          }
        }

        // 1f. Store-Pop-Load Pruning: [StoreLocalInt(o), Pop, LoadLocalInt(o)] ->
        // [StoreLocalInt(o)]
        if (i1 instanceof VarInstrs.StoreLocalInt s
            && i2 instanceof StackInstrs.Pop
            && i3 instanceof VarInstrs.LoadLocalInt l) {
          if (s.getOffset() == l.getOffset()) {
            next.add(s);
            i += 2;
            changed = true;
            continue;
          }
        }
        */

        next.add(i1);
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
        code.replaceAllInstructions(pruned);
      }

    } while (changed);
  }
}
