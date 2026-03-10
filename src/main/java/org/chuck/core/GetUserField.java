package org.chuck.core;

/**
 * Reads a field from the current 'this' object (top of the shred's thisStack).
 * Emitted for bare field-name references inside user-defined class methods.
 */
public class GetUserField implements ChuckInstr {
    private final String name;
    public GetUserField(String name) { this.name = name; }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        UserObject obj = s.thisStack.peek();
        if (obj == null) { s.reg.push(0L); return; }
        ChuckObject objField = obj.getObjectField(name);
        if (objField != null) s.reg.pushObject(objField);
        else s.reg.push(obj.getPrimitiveField(name));
    }
}
