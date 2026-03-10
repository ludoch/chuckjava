package org.chuck.core;

/**
 * Instruction to set a member variable by name.
 */
public class SetMemberIntByName implements ChuckInstr {
    private final String memberName;

    public SetMemberIntByName(String memberName) {
        this.memberName = memberName;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        // ChucK order: Value => Object.member
        // Stack: [Value, Object] -> Top is Object
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        long val = shred.reg.popLong();
        
        if (obj == null) {
            throw new RuntimeException("SetMemberIntByName: target object is null");
        }

        if (memberName.equals("freq")) obj.setData(0, val);
        else if (memberName.equals("gain")) obj.setData(1, val);
    }
}
