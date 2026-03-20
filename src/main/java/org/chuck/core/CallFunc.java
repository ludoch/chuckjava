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
        // 1. Move arguments from reg stack to mem stack first
        long[] prims = new long[argCount];
        boolean[] isD = new boolean[argCount];
        Object[] objs = new Object[argCount];
        for (int i = argCount - 1; i >= 0; i--) {
            isD[i] = shred.reg.isDouble(0);
            if (shred.reg.isObject(0)) objs[i] = shred.reg.popObject();
            else if (isD[i]) prims[i] = Double.doubleToRawLongBits(shred.reg.popDouble());
            else prims[i] = shred.reg.popLong();
        }

        // 2. Save return state
        shred.mem.pushObject(shred.getCode());
        shred.mem.push(shred.getPc());
        shred.mem.push(shred.getFramePointer());
        shred.mem.push(shred.reg.getSp()); // reg sp before return value is pushed

        // 3. Set new FP and push args to it
        shred.setFramePointer(shred.mem.getSp());
        for (int i = 0; i < argCount; i++) {
            if (objs[i] != null) shred.mem.pushObject(objs[i]);
            else if (isD[i]) shred.mem.push(Double.longBitsToDouble(prims[i]));
            else shred.mem.push(prims[i]);
        }

        // 4. Jump
        shred.setCode(targetCode);
        shred.setPc(-1);
    }
}
