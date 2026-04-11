package org.chuck.core;

/** Interface for all ChucK virtual machine instructions. */
public interface ChuckInstr {
  void execute(ChuckVM vm, ChuckShred shred);
}
