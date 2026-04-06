package org.chuck.core;

/**
 * Instruction to call a ChucK function.
 * Pushes the current Code, PC, and the pre-args reg.sp onto the mem stack,
 * so ReturnFunc can restore the stack pointer and eliminate the arg leak.
 */
public class CallFunc implements ChuckInstr {
    private final ChuckCode targetCode;
    private final int argCount;

    public CallFunc(ChuckCode targetCode, int argCount) {
        this.targetCode = targetCode;
        this.argCount = argCount;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // 1. Save return state
        shred.mem.pushObject(shred.getCode());
        shred.mem.push((long) (shred.getPc() + 1));
        shred.mem.push((long) shred.getFramePointer());
        shred.mem.push((long) (shred.reg.getSp() - argCount));

        // 2. Set new FP
        shred.setFramePointer(shred.mem.getSp());

        // 3. Jump
        shred.setCode(targetCode);
        shred.setPc(0);
    }
}
