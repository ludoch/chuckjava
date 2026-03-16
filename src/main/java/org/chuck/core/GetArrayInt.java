package org.chuck.core;

/**
 * Gets an element from an indexed array.
 * Correctly handles int, float, and object elements.
 */
public class GetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        if (shred.reg.getSp() < 2) { 
            shred.reg.pop(shred.reg.getSp());
            shred.reg.push(0L); 
            return; 
        }
        int index = (int) shred.reg.popLong();
        Object rawArr = shred.reg.popObject();

        if (!(rawArr instanceof ChuckArray arr)) {
            shred.reg.push(0L);
            return;
        }
        if (index < 0 || index >= arr.size()) {
            shred.reg.push(0L);
            return;
        }
        
        if (arr.isObjectAt(index)) {
            shred.reg.pushObject(arr.getObject(index));
        } else if (arr.isDoubleAt(index)) {
            shred.reg.push(arr.getFloat(index));
        } else {
            shred.reg.push(arr.getInt(index));
        }
    }
}
