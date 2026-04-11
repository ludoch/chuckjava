package org.chuck.compiler;

import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckVM;

/** Interface for JIT-compiled ChucK code fragments. */
public interface JitExecutable {
  void execute(ChuckVM vm, ChuckShred shred);
}
