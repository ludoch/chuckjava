package org.chuck.core;

/**
 * Pushes a constant string object onto the operand stack.
 */
public class PushString implements ChuckInstr {
    private final ChuckString value;

    public PushString(String value) {
        this.value = new ChuckString(value);
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        shred.reg.pushObject(value);
    }
}
