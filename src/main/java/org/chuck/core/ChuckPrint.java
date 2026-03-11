package org.chuck.core;

/**
 * Instruction to print values to the console (<<< ... >>>).
 */
public class ChuckPrint implements ChuckInstr {
    private final int numArgs;

    public ChuckPrint(int numArgs) {
        this.numArgs = numArgs;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        StringBuilder sb = new StringBuilder();
        // Popping from stack in reverse order of how they were pushed
        Object[] values = new Object[numArgs];
        for (int i = numArgs - 1; i >= 0; i--) {
            values[i] = shred.reg.pop();
        }
        
        for (int i = 0; i < numArgs; i++) {
            sb.append(values[i]);
            if (i < numArgs - 1) sb.append(" ");
        }
        
        vm.print(sb.toString());
    }
}
