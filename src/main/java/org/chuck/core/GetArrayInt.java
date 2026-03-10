package org.chuck.core;

/**
 * Gets an integer value from an indexed array.
 */
public class GetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // ChucK order: Array[Index]
        // Stack: [ArrayObj, Index]
        // Top is Index
        int index = (int) shred.reg.popLong();
        ChuckArray arr = (ChuckArray) shred.reg.popObject();
        
        if (arr == null) {
            throw new RuntimeException("GetArrayInt: array object is null");
        }
        shred.reg.push(arr.getInt(index));
    }
}
