package org.chuck.core;

/** Interface for Java-based ChucK shreds. */
public interface Shred {
  /** Entry point for the shred. This method is executed in a virtual thread. */
  void shred();
}
