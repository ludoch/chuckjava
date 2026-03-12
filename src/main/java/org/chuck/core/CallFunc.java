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
        // 1. Save return address and state
        shred.mem.pushObject(shred.getCode());
        shred.mem.push(shred.getPc());
        shred.mem.push(shred.getFramePointer());
        
        // 2. Save the sp BEFORE args so ReturnFunc can restore it
        int savedRegSp = shred.reg.getSp() - argCount;
        shred.mem.push(savedRegSp);

        // 3. Move arguments from reg stack to mem stack (local variables)
        // Store in temporary arrays to preserve order
        long[] prims = new long[argCount];
        boolean[] isD = new boolean[argCount];
        Object[] objs = new Object[argCount];
        
        for (int i = argCount - 1; i >= 0; i--) {
            isD[i] = shred.reg.isDouble(0);
            if (shred.reg.isObject(0)) {
                objs[i] = shred.reg.popObject();
            } else {
                prims[i] = shred.reg.popLong();
            }
        }
        
        // 4. Set new Frame Pointer to current mem stack top
        shred.setFramePointer(shred.mem.getSp());
        
        // 5. Push them to mem stack in order (FP+0, FP+1...)
        for (int i = 0; i < argCount; i++) {
            if (objs[i] != null) {
                shred.mem.pushObject(objs[i]);
            } else if (isD[i]) {
                shred.mem.push(Double.longBitsToDouble(prims[i]));
            } else {
                shred.mem.push(prims[i]);
            }
        }

        // 6. Switch to target code
        shred.setCode(targetCode);
        shred.setPc(-1); // Will be incremented to 0 by the loop
    }
}
