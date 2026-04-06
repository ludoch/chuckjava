package org.chuck.network;

import org.chuck.core.ChuckVM;

/**
 * OscEvent — a named alias for OscIn, providing the ChucK OscEvent API.
 * In ChucK, OscEvent and OscIn are interchangeable; this class simply
 * subclasses OscIn so that "OscEvent oe;" declarations instantiate correctly.
 *
 * Usage:  OscEvent oe;  oe.port(6449);  oe.addAddress("/test, i");  oe => now;
 */
public class OscEvent extends OscIn {
    public OscEvent() { super(null); }
    public OscEvent(ChuckVM vm) { super(vm); }
}
