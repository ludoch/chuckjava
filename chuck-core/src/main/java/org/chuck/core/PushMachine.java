package org.chuck.core;

/** Instruction to push the Machine object onto the stack. */
public class PushMachine implements ChuckInstr {
  private static final ChuckObject MACHINE =
      new ChuckObject(new ChuckType("Machine", ChuckType.OBJECT, 0, 0)) {
        @SuppressWarnings("unused")
        public int add(String path, ChuckVM vm) {
          return vm.add(path);
        }

        @SuppressWarnings("unused")
        public int replace(int id, String path, ChuckVM vm) {
          return vm.replace(id, path);
        }

        @SuppressWarnings("unused")
        public void remove(int id, ChuckVM vm) {
          vm.removeShred(id);
        }

        @SuppressWarnings("unused")
        public void removeAll(ChuckVM vm) {
          vm.clear();
        }

        @SuppressWarnings("unused")
        public void clear(ChuckVM vm) {
          vm.clear();
        }

        @SuppressWarnings("unused")
        public int eval(String code, ChuckVM vm) {
          return vm.eval(code);
        }

        @SuppressWarnings("unused")
        public void crash(ChuckVM vm) {
          System.exit(1);
        }

        @SuppressWarnings("unused")
        public int numShreds(ChuckVM vm) {
          return vm.getNumShreds();
        }

        @SuppressWarnings("unused")
        public int shredExists(int id, ChuckVM vm) {
          return vm.getShred(id) != null ? 1 : 0;
        }

        @SuppressWarnings("unused")
        public ChuckObject shreds(ChuckVM vm) {
          int[] ids = vm.getActiveShredIds();
          ChuckArray arr = new ChuckArray(ChuckType.ARRAY, ids.length);
          for (int i = 0; i < ids.length; i++) {
            arr.setInt(i, (long) ids[i]);
          }
          return arr;
        }

        @SuppressWarnings("unused")
        public void silent(long val, ChuckVM vm) {
          /* Mute functionality if supported, otherwise no-op */
        }
      };

  @Override
  public void execute(ChuckVM vm, ChuckShred s) {
    s.reg.pushObject(MACHINE);
  }
}
