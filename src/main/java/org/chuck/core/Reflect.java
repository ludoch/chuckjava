package org.chuck.core;

/**
 * Reflect: Object and Shred introspection.
 */
public class Reflect extends ChuckObject {
    public Reflect() {
        super(ChuckType.OBJECT);
    }

    public static int id(ChuckShred s) { return s != null ? s.id() : -1; }
    public static String name(ChuckShred s) { return s != null ? s.getName() : ""; }
    public static int done(ChuckShred s) { return s != null && s.isDone() ? 1 : 0; }
    
    public static String type(Object o) {
        if (o == null) return "null";
        if (o instanceof ChuckObject co) return co.getType().getName();
        return o.getClass().getSimpleName();
    }
}
