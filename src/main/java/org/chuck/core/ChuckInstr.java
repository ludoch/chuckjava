package org.chuck.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Interface for all ChucK virtual machine instructions. */
public interface ChuckInstr {
  void execute(ChuckVM vm, ChuckShred shred);

  default MethodHandle methodHandle() {
    try {
      return MethodHandles.lookup()
          .findVirtual(
              this.getClass(),
              "execute",
              MethodType.methodType(void.class, ChuckVM.class, ChuckShred.class))
          .bindTo(this);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException("Failed to create MethodHandle for " + this.getClass(), e);
    }
  }
}
