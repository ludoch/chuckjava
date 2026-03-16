package org.chuck.core;

/**
 * A mutable string for ChucK, wrapping StringBuilder.
 */
public class ChuckString extends ChuckObject {
    private final StringBuilder sb;

    public ChuckString(String initial) {
        super(ChuckType.STRING);
        this.sb = new StringBuilder(initial == null ? "" : initial);
    }

    public long length() { return sb.length(); }
    public long charAt(long i) { return (i >= 0 && i < sb.length()) ? sb.charAt((int)i) : 0; }
    public void setCharAt(long i, long c) { if (i >= 0 && i < sb.length()) sb.setCharAt((int)i, (char)c); }
    
    public ChuckString substring(long start) {
        int s = Math.max(0, Math.min((int)start, sb.length()));
        return new ChuckString(sb.substring(s));
    }
    public ChuckString substring(long start, long len) {
        int s = Math.max(0, Math.min((int)start, sb.length()));
        int e = Math.max(s, Math.min(s + (int)len, sb.length()));
        return new ChuckString(sb.substring(s, e));
    }
    
    private String valToString(Object val) {
        if (val instanceof Long l) return String.valueOf((char)l.intValue());
        return String.valueOf(val);
    }

    public void insert(long i, Object val) {
        int idx = Math.max(0, Math.min((int)i, sb.length()));
        sb.insert(idx, valToString(val));
    }
    
    public void erase(long start, long len) {
        int s = Math.max(0, Math.min((int)start, sb.length()));
        int e = Math.max(s, Math.min(s + (int)len, sb.length()));
        sb.delete(s, e);
    }
    
    public void replace(long start, Object val) {
        int s = Math.max(0, Math.min((int)start, sb.length()));
        String v = valToString(val);
        int e = Math.max(s, Math.min(s + v.length(), sb.length()));
        sb.replace(s, e, v);
    }

    public void replace(long start, long len, Object val) {
        int s = Math.max(0, Math.min((int)start, sb.length()));
        int e = Math.max(s, Math.min(s + (int)len, sb.length()));
        sb.replace(s, e, valToString(val));
    }
    
    public long find(Object val) { return sb.indexOf(valToString(val)); }
    public long find(Object val, long start) { return sb.indexOf(valToString(val), (int)start); }
    public long rfind(Object val) { return sb.lastIndexOf(valToString(val)); }
    public long rfind(Object val, long start) { return sb.lastIndexOf(valToString(val), (int)start); }
    
    @Override
    public String toString() {
        return sb.toString();
    }
}
