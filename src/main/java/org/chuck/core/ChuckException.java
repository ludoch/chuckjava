package org.chuck.core;

/** Base class for all ChucK-related exceptions. */
public class ChuckException extends RuntimeException {
  public ChuckException(String message) {
    super(message);
  }

  public ChuckException(String message, Throwable cause) {
    super(message, cause);
  }
}
