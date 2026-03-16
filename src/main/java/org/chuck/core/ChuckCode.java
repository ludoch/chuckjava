package org.chuck.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A sequence of executable instructions.
 */
public class ChuckCode {
    private final List<ChuckInstr> instructions = new ArrayList<>();
    private String name;

    public ChuckCode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addInstruction(ChuckInstr instr) {
        instructions.add(instr);
    }

    public void prependInstruction(ChuckInstr instr) {
        instructions.add(0, instr);
    }

    public void replaceInstruction(int index, ChuckInstr instr) {
        instructions.set(index, instr);
    }

    public ChuckInstr getInstruction(int pc) {
        if (pc < 0 || pc >= instructions.size()) {
            return null;
        }
        return instructions.get(pc);
    }

    public int getNumInstructions() {
        return instructions.size();
    }
}
