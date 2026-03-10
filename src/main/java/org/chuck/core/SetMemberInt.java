package org.chuck.core;

/**
 * Sets an integer member value in an object.
 */
public class SetMemberInt implements ChuckInstr {
    private final int offset;
    public SetMemberInt(int offset) { this.offset = offset; }
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // ChucK order: Value => Object.member
        // Stack: [Value, Object] -> Top is Object
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        long value = shred.reg.popLong();
        
        if (obj == null) {
            throw new RuntimeException("SetMemberInt: target object is null");
        }
        obj.setData(offset, value);
    }
}
