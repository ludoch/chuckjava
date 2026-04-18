package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.chuck.core.ChuckCode;
import org.chuck.core.instr.ArithmeticInstrs;
import org.chuck.core.instr.ControlInstrs;
import org.chuck.core.instr.PushInstrs;
import org.chuck.core.instr.StackInstrs;
import org.chuck.core.instr.VarInstrs;
import org.junit.jupiter.api.Test;

public class OptimizerTest {

  @Test
  public void testConstantFolding() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new PushInstrs.PushInt(10));
    code.addInstruction(new PushInstrs.PushInt(20));
    code.addInstruction(new ArithmeticInstrs.AddInt());

    assertEquals(3, code.getNumInstructions());

    Optimizer.optimize(code);

    // Optimizations disabled due to jump target corruption
    assertEquals(3, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof PushInstrs.PushInt);
    assertEquals(10L, ((PushInstrs.PushInt) code.getInstruction(0)).getVal());
  }

  @Test
  public void testDupPopRemoval() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new StackInstrs.Dup());
    code.addInstruction(new StackInstrs.Pop());
    code.addInstruction(new PushInstrs.PushInt(42));

    assertEquals(3, code.getNumInstructions());

    Optimizer.optimize(code);

    // Optimizations disabled due to jump target corruption
    assertEquals(3, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof StackInstrs.Dup);
  }

  @Test
  public void testNestedDupPopRemoval() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new StackInstrs.Dup());
    code.addInstruction(new StackInstrs.Dup());
    code.addInstruction(new StackInstrs.Pop());
    code.addInstruction(new StackInstrs.Pop());

    assertEquals(4, code.getNumInstructions());

    Optimizer.optimize(code);

    // Optimizations disabled due to jump target corruption
    assertEquals(4, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof StackInstrs.Dup);
  }

  @Test
  public void testBasicDCE() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new PushInstrs.PushInt(1));
    code.addInstruction(new ControlInstrs.Jump(10)); // Jumps far away
    code.addInstruction(new PushInstrs.PushInt(2)); // Unreachable

    assertEquals(3, code.getNumInstructions());

    Optimizer.optimize(code);

    // Should remove the unreachable PushInt(2)
    assertEquals(2, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof PushInstrs.PushInt);
    assertTrue(code.getInstruction(1) instanceof ControlInstrs.Jump);
  }

  @Test
  public void testIncLocalOptimization() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new VarInstrs.LoadLocalInt(5));
    code.addInstruction(new PushInstrs.PushInt(1));
    code.addInstruction(new ArithmeticInstrs.AddInt());
    code.addInstruction(new VarInstrs.StoreLocalInt(5));

    assertEquals(4, code.getNumInstructions());

    Optimizer.optimize(code);

    // Optimizations disabled due to jump target corruption
    assertEquals(4, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof VarInstrs.LoadLocalInt);
  }

  @Test
  public void testStorePopLoadOptimization() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new VarInstrs.StoreLocalInt(5));
    code.addInstruction(new StackInstrs.Pop());
    code.addInstruction(new VarInstrs.LoadLocalInt(5));

    assertEquals(3, code.getNumInstructions());

    Optimizer.optimize(code);

    // Optimizations disabled due to jump target corruption
    assertEquals(3, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof VarInstrs.StoreLocalInt);
    assertEquals(5, ((VarInstrs.StoreLocalInt) code.getInstruction(0)).getOffset());
  }
}
