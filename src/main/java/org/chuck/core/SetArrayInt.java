package org.chuck.core;

/**
 * Sets an integer value in an indexed array.
 */
public class SetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // ChucK order: Value => Array[Index]
        // Stack: [Value, ArrayObj, Index]
        // Top is Index
        int index = (int) shred.reg.popLong();
        ChuckArray arr = (ChuckArray) shred.reg.popObject();
        long value = shred.reg.popLong();
        
        if (arr == null) {
            throw new RuntimeException("SetArrayInt: array object is null");
        }
        arr.setInt(index, value);
    }
}
