package org.chuck.core;

/**
 * Instruction to push the Machine object onto the stack.
 */
public class PushMachine implements ChuckInstr {
    private static final ChuckObject MACHINE = new ChuckObject(new ChuckType("Machine", ChuckType.OBJECT, 0, 0)) {
        public int add(String path, ChuckVM vm) { return vm.add(path); }
        public int replace(int id, String path, ChuckVM vm) { return vm.replace(id, path); }
        public void remove(int id, ChuckVM vm) { vm.removeShred(id); }
        public void removeAll(ChuckVM vm) { vm.clear(); }
        public void clear(ChuckVM vm) { vm.clear(); }
        public int eval(String code, ChuckVM vm) { return vm.eval(code); }
        public void crash(ChuckVM vm) { System.exit(1); }
        public int numShreds(ChuckVM vm) { return vm.getNumShreds(); }
        public int shredExists(int id, ChuckVM vm) { return vm.getShred(id) != null ? 1 : 0; }
        public ChuckObject shreds(ChuckVM vm) {
            int[] ids = vm.getActiveShredIds();
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, ids.length);
            for (int i = 0; i < ids.length; i++) {
                arr.setInt(i, (long) ids[i]);
            }
            return arr;
        }
        public void silent(long val, ChuckVM vm) { /* Mute functionality if supported, otherwise no-op */ }
    };

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
        s.reg.pushObject(MACHINE);
    }
}
