package org.chuck.audio;

import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/** ChuGen: Custom Unit Generator. Allows defining a UGen's tick() logic in ChucK code. */
public class ChuGen extends ChuckUGen {
  private ChuckCode tickCode;
  private ChuckShred shred;
  private ChuckVM vm;

  public ChuGen() {
    this(new ChuckType("ChuGen", ChuckType.OBJECT, 0, 0));
  }

  public ChuGen(ChuckType type) {
    super(type);
  }

  public void setTickCode(ChuckCode code, ChuckShred shred, ChuckVM vm) {
    this.tickCode = code;
    this.shred = shred;
    this.vm = vm;
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (tickCode == null || shred == null) return input;

    // Save context
    org.chuck.core.ChuckCode oldCode = shred.getCode();
    int oldPc = shred.getPc();
    int oldFp = shred.getFramePointer();
    int oldMemSp = shred.mem.getSp();

    // 1. Push return frame
    shred.mem.pushObject(null);
    shred.mem.push(-1L);
    shred.mem.push((long) oldFp);
    shred.mem.push((long) shred.reg.getSp());
    shred.setFramePointer(shred.mem.getSp());

    // 2. Push argument 0 (float input)
    shred.mem.push((double) input);

    // 3. Execute the tick code synchronously
    shred.setCode(tickCode);
    shred.setPc(0);

    while (shred.getPc() >= 0 && shred.getPc() < tickCode.getNumInstructions()) {
      int currentPc = shred.getPc();
      org.chuck.core.ChuckInstr instr = tickCode.getInstruction(currentPc);
      if (instr == null) break;
      instr.execute(vm, shred);
      // Only increment if the instruction didn't change the PC (e.g., ReturnMethod sets it to -1)
      if (shred.getPc() == currentPc) {
        shred.setPc(currentPc + 1);
      }
    }

    // 4. Pop the result
    if (shred.reg.getSp() > 0) {
      if (shred.reg.isDouble(0)) lastOut = (float) shred.reg.popAsDouble();
      else lastOut = (float) shred.reg.popLong();
    } else {
      lastOut = 0.0f;
    }

    // Restore context and cleanup mem stack
    shred.setCode(oldCode);
    shred.setPc(oldPc);
    shred.setFramePointer(oldFp);
    shred.mem.setSp(oldMemSp);

    return lastOut;
  }
}
