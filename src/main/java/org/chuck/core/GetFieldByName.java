package org.chuck.core;

/**
 * Pops an object from the reg stack and pushes the named field's value.
 * Used for 'f.x' read access from outside the class (top-level code).
 * Falls back gracefully for non-UserObject targets.
 */
public class GetFieldByName implements ChuckInstr {
    private final String name;
    public GetFieldByName(String name) { this.name = name; }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        Object raw = s.reg.popObject();
        if (raw instanceof UserObject uo) {
            ChuckObject objField = uo.getObjectField(name);
            if (objField != null) s.reg.pushObject(objField);
            else s.reg.push(uo.getPrimitiveField(name));
        } else {
            // Not a user object — push 0 as a safe fallback
            s.reg.push(0L);
        }
    }
}
