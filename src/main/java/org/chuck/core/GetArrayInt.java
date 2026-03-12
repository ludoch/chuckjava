package org.chuck.core;

/**
 * Gets an element from an indexed array.
 * If objectData[index] is non-null, pushes it as an object (for UGen/object arrays).
 * Otherwise pushes intData[index] as a long.
 */
public class GetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        if (shred.reg.getSp() < 2) { shred.reg.push(0L); return; }
        int index = (int) shred.reg.popLong();
        ChuckArray arr = (ChuckArray) shred.reg.popObject();

        if (arr == null) {
            shred.reg.push(0L);
            return;
        }
        if (index < 0 || index >= arr.size()) {
            shred.reg.push(0L);
            return;
        }
        Object obj = arr.getObject(index);
        if (obj != null) {
            shred.reg.pushObject(obj);
        } else {
            shred.reg.push(arr.getInt(index));
        }
    }
}
