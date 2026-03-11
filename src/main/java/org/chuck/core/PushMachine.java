package org.chuck.core;

/**
 * Instruction to push the Machine object onto the stack.
 */
public class PushMachine implements ChuckInstr {
    private static final ChuckObject MACHINE = new ChuckObject(new ChuckType("Machine", ChuckType.OBJECT, 0, 0)) {
        public int add(String path, ChuckVM vm) { return vm.add(path); }
        public void remove(int id, ChuckVM vm) { vm.removeShred(id); }
        public void clear(ChuckVM vm) { vm.clear(); }
    };

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        s.reg.pushObject(MACHINE);
    }
}
