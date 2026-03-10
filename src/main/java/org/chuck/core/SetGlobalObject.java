package org.chuck.core;

/**
 * Instruction to set a global object variable.
 */
public class SetGlobalObject implements ChuckInstr {
    private final String name;

    public SetGlobalObject(String name) {
        this.name = name;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        ChuckObject obj = (ChuckObject) shred.reg.popObject();
        vm.setGlobalObject(name, obj);
    }
}
