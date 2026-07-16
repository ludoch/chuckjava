package org.chuck.audio.chugins;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import org.chuck.audio.ChuckUGen;

/**
 * A universal Project Panama FFM bridge that wraps a native C/C++ audio processing symbol (such as
 * from a .chug, .so, .dylib, or .dll plugin library) inside a standard ChucK-Java UGen.
 */
public class NativeUGenBridge extends ChuckUGen {
  private final String ugenName;
  private final MemorySegment nativeSymbol;
  private MethodHandle computeHandle;
  private boolean hasNativeTick = false;

  public NativeUGenBridge(String ugenName, MemorySegment nativeSymbol, float sampleRate) {
    this.ugenName = ugenName;
    this.nativeSymbol = nativeSymbol;

    if (nativeSymbol != null && !nativeSymbol.equals(MemorySegment.NULL)) {
      try {
        // Link to native function: float chugin_compute(float input)
        this.computeHandle =
            Linker.nativeLinker()
                .downcallHandle(
                    nativeSymbol,
                    FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        this.hasNativeTick = true;
      } catch (Exception e) {
        System.err.println(
            "[ChuginLoader] Failed to downcall link symbol for "
                + ugenName
                + ": "
                + e.getMessage());
        this.hasNativeTick = false;
      }
    }
  }

  public String getUgenName() {
    return ugenName;
  }

  public boolean isNativeLinked() {
    return hasNativeTick && computeHandle != null;
  }

  @Override
  protected float compute(float input, long systemTime) {
    if (!hasNativeTick || computeHandle == null) {
      return input; // Pass-through if native symbol unlinked or simulated
    }
    try {
      return (float) computeHandle.invokeExact(input);
    } catch (Throwable t) {
      // If native call fails at runtime, disable to prevent engine crashes
      hasNativeTick = false;
      return input;
    }
  }
}
