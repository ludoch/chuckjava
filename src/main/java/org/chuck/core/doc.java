package org.chuck.core;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Annotation for ChucK documentation. */
@Retention(RetentionPolicy.RUNTIME)
public @interface doc {
  String value();
}
