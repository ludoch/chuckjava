package org.chuck.core;

/**
 * Instruction to get a global integer variable.
 */
public class GetGlobalInt implements ChuckInstr {
    private final String name;

    public GetGlobalInt(String name) {
        this.name = name;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        shred.reg.push(vm.getGlobalInt(name));
    }
}
