package org.chuck.core;

/** Thrown when an error occurs during ChucK VM execution. */
public class ChuckRuntimeException extends ChuckException {
  public ChuckRuntimeException(String message) {
    super(message);
  }

  public ChuckRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
