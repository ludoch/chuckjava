package org.chuck.core;

/**
 * Writes the top-of-stack value into a field on 'this' (top of the shred's thisStack).
 * Emitted for field assignments inside user-defined class methods.
 * Leaves the value on the stack (ChucK chuck-operator convention).
 */
public class SetUserField implements ChuckInstr {
    private final String name;
    public SetUserField(String name) { this.name = name; }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        UserObject obj = s.thisStack.peek();
        if (obj == null) return;
        Object top = s.reg.peekObject(0);
        if (top instanceof ChuckObject co) {
            obj.setObjectField(name, co);
        } else {
            obj.setPrimitiveField(name, s.reg.peekLong(0));
        }
        // leave value on stack
    }
}
