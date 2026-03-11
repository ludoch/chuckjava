package org.chuck.core;

/**
 * Instruction to swap the values of two global variables or object members.
 * Implements the <=> operator.
 */
public class ChuckSwap implements ChuckInstr {
    private final String name1;
    private final String name2;
    private final boolean isMember;

    public ChuckSwap(String name1, String name2, boolean isMember) {
        this.name1 = name1;
        this.name2 = name2;
        this.isMember = isMember;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        if (isMember) {
            // Swap members of an object on the stack
            ChuckObject obj = (ChuckObject) shred.reg.popObject();
            if (obj instanceof UserObject uo) {
                long val1 = uo.getPrimitiveField(name1);
                long val2 = uo.getPrimitiveField(name2);
                uo.setPrimitiveField(name1, val2);
                uo.setPrimitiveField(name2, val1);
            }
        } else {
            // Swap global variables
            ChuckObject obj1 = vm.getGlobalObject(name1);
            ChuckObject obj2 = vm.getGlobalObject(name2);
            
            if (obj1 != null || obj2 != null) {
                vm.setGlobalObject(name1, obj2);
                vm.setGlobalObject(name2, obj1);
            } else {
                long val1 = vm.getGlobalInt(name1);
                long val2 = vm.getGlobalInt(name2);
                vm.setGlobalInt(name1, val2);
                vm.setGlobalInt(name2, val1);
            }
        }
    }
}
