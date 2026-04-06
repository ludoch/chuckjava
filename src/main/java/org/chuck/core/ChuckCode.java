package org.chuck.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A sequence of executable instructions.
 */
public class ChuckCode {
    private final List<ChuckInstr> instructions = new ArrayList<>();
    private final List<Integer> lineNumbers = new ArrayList<>();
    private String name;
    private int activeLineNumber = -1;

    public ChuckCode(String name) {
        this.name = name;
    }

    public void setActiveLineNumber(int line) {
        this.activeLineNumber = line;
    }

    public String getName() {
        return name;
    }

    public void dump(java.io.PrintStream out) {
        for (int i = 0; i < instructions.size(); i++) {
            ChuckInstr instr = instructions.get(i);
            out.println("  [" + i + "] " + (instr != null ? instr.toString() : "null") + " (line " + getLineNumber(i) + ")");
        }
    }

    public void addInstruction(ChuckInstr instr) {
        instructions.add(instr);
        lineNumbers.add(activeLineNumber);
    }

    public void addInstruction(ChuckInstr instr, int line) {
        instructions.add(instr);
        lineNumbers.add(line);
    }

    public void prependInstruction(ChuckInstr instr) {
        instructions.add(0, instr);
        lineNumbers.add(0, -1);
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

    public int getLineNumber(int pc) {
        if (pc < 0 || pc >= lineNumbers.size()) return -1;
        return lineNumbers.get(pc);
    }

    public int getNumInstructions() {
        return instructions.size();
    }
}
