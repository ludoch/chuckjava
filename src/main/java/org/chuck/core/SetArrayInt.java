package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Sets an element in an indexed array via the ChucK => operator.
 * Stack layout (top first): [Index, ArrayObj, Value]
 *
 * For UGen arrays: if both value and array[index] are ChuckUGens,
 * wires them (src.chuckTo(dest)) and pushes dest for chaining.
 * For primitive/object arrays: stores the value and pushes it back.
 */
public class SetArrayInt implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        int index = (int) shred.reg.popLong();
        ChuckArray arr = (ChuckArray) shred.reg.popObject();

        if (arr == null) {
            // Discard value and push 0 to keep stack balanced
            if (shred.reg.isObject(0)) shred.reg.popObject();
            else shred.reg.popLong();
            shred.reg.push(0L);
            return;
        }

        if (index < 0 || index >= arr.size()) {
            if (shred.reg.isObject(0)) shred.reg.popObject();
            else shred.reg.popLong();
            shred.reg.push(0L);
            return;
        }

        if (shred.reg.isObject(0)) {
            Object value = shred.reg.popObject();
            // UGen chucking: wire src into dest if both are UGens
            Object dest = arr.getObject(index);
            if (value instanceof ChuckUGen srcUgen && dest instanceof ChuckUGen destUgen) {
                srcUgen.chuckTo(destUgen);
                shred.reg.pushObject(destUgen); // push dest for chaining
            } else {
                arr.setObject(index, value);
                shred.reg.pushObject(value);
            }
        } else {
            long value = shred.reg.popLong();
            arr.setInt(index, value);
            shred.reg.push(value); // push back for chaining
        }
    }
}
