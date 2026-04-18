package org.chuck.core;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/** A sequence of executable instructions. */
public class ChuckCode {
  private final List<ChuckInstr> instructions = new ArrayList<>();
  private final List<Integer> lineNumbers = new ArrayList<>();
  private final List<MethodHandle> handles = new ArrayList<>();
  private String name;
  private final List<Object> constantPool = new ArrayList<>();
  private int activeLineNumber = -1;
  private int numArgs = 0;
  private int stackSize = 0;
  private String returnType = "void";
  private final java.util.concurrent.atomic.AtomicInteger hotness =
      new java.util.concurrent.atomic.AtomicInteger(0);
  private org.chuck.compiler.JitExecutable jitExecutable = null;

  public ChuckCode(String name) {
    this.name = name;
  }

  public int incrementHotness() {
    return hotness.incrementAndGet();
  }

  public void setJitExecutable(org.chuck.compiler.JitExecutable exec) {
    this.jitExecutable = exec;
  }

  public org.chuck.compiler.JitExecutable getJitExecutable() {
    return jitExecutable;
  }

  public void setStackSize(int s) {
    this.stackSize = s;
  }

  public int getStackSize() {
    return stackSize;
  }

  public List<Object> getConstantPool() {
    return constantPool;
  }

  public int addConstant(Object value) {
    for (int i = 0; i < constantPool.size(); i++) {
      if (constantPool.get(i).equals(value)) return i;
    }
    constantPool.add(value);
    return constantPool.size() - 1;
  }

  public Object getConstant(int index) {
    if (index < 0 || index >= constantPool.size()) return null;
    return constantPool.get(index);
  }

  public void setSignature(int numArgs, String returnType) {
    this.numArgs = numArgs;
    this.returnType = returnType;
  }

  public int getNumArgs() {
    return numArgs;
  }

  public String getReturnType() {
    return returnType;
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
      out.println(
          "  ["
              + i
              + "] "
              + (instr != null ? instr.toString() : "null")
              + " (line "
              + getLineNumber(i)
              + ")");
    }
  }

  public void addInstruction(ChuckInstr instr) {
    instructions.add(instr);
    lineNumbers.add(activeLineNumber);
    handles.add(instr != null ? instr.methodHandle() : null);
  }

  public void addInstruction(ChuckInstr instr, int line) {
    instructions.add(instr);
    lineNumbers.add(line);
    handles.add(instr != null ? instr.methodHandle() : null);
  }

  public void prependInstruction(ChuckInstr instr) {
    instructions.add(0, instr);
    lineNumbers.add(0, -1);
    handles.add(0, instr != null ? instr.methodHandle() : null);
  }

  public void replaceInstruction(int index, ChuckInstr instr) {
    instructions.set(index, instr);
    handles.set(index, instr != null ? instr.methodHandle() : null);
  }

  public ChuckInstr getInstruction(int pc) {
    if (pc < 0 || pc >= instructions.size()) {
      return null;
    }
    return instructions.get(pc);
  }

  public MethodHandle getHandle(int pc) {
    if (pc < 0 || pc >= handles.size()) {
      return null;
    }
    return handles.get(pc);
  }

  public int getLineNumber(int pc) {
    if (pc < 0 || pc >= lineNumbers.size()) return -1;
    return lineNumbers.get(pc);
  }

  public int getNumInstructions() {
    return instructions.size();
  }

  public List<ChuckInstr> getInstructions() {
    return new ArrayList<>(instructions);
  }

  public void replaceAllInstructions(List<ChuckInstr> newInstrs) {
    this.instructions.clear();
    this.instructions.addAll(newInstrs);
    this.handles.clear();
    for (ChuckInstr instr : newInstrs) {
      this.handles.add(instr != null ? instr.methodHandle() : null);
    }
    this.lineNumbers.clear();
    for (int i = 0; i < newInstrs.size(); i++) this.lineNumbers.add(-1);
  }
}
