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

    // Should evaluate 10 + 20 = 30
    assertEquals(1, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof PushInstrs.PushInt);
    assertEquals(30, ((PushInstrs.PushInt) code.getInstruction(0)).getVal());
  }

  @Test
  public void testDupPopRemoval() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new StackInstrs.Dup());
    code.addInstruction(new StackInstrs.Pop());
    code.addInstruction(new PushInstrs.PushInt(42));

    assertEquals(3, code.getNumInstructions());

    Optimizer.optimize(code);

    // Should remove Dup and Pop, leaving only PushInt
    assertEquals(1, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof PushInstrs.PushInt);
  }

  @Test
  public void testNestedDupPopRemoval() {
    ChuckCode code = new ChuckCode("test");
    code.addInstruction(new StackInstrs.Dup());
    code.addInstruction(new StackInstrs.Dup());
    code.addInstruction(new StackInstrs.Pop());
    code.addInstruction(new StackInstrs.Pop());

    Optimizer.optimize(code);

    assertEquals(0, code.getNumInstructions());
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

    // Should combine into IncLocalInt(5, 1)
    assertEquals(1, code.getNumInstructions());
    assertTrue(code.getInstruction(0) instanceof VarInstrs.IncLocalInt);
    VarInstrs.IncLocalInt inc = (VarInstrs.IncLocalInt) code.getInstruction(0);
    assertEquals(5, inc.getOffset());
    assertEquals(1, inc.getDelta());
  }
}
