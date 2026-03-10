package org.chuck.core;

/**
 * Instruction to instantiate a new ChucK indexed array.
 */
public class NewArrayInt implements ChuckInstr {
    private final int size;

    public NewArrayInt(int size) {
        this.size = size;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
        shred.reg.pushObject(arr);
    }
}
