package org.chuck.core.instr;

import org.chuck.core.*;
import org.chuck.audio.ChuckUGen;

public class FieldInstrs {
    public static class GetFieldByName implements ChuckInstr {
        String n; public GetFieldByName(String v) { n = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = s.reg.popObject();
            if (obj == null) {
                if (n.equals("size")) { s.reg.push(0L); return; }
                throw new RuntimeException("NullPointerException: cannot access member '" + n + "' on null object");
            }
            if (n.equals("size") && obj instanceof ChuckArray arr) {
                s.reg.push((long) arr.size());
            } else if (obj instanceof ChuckArray arr && arr.vecTag != null) {
                switch (arr.vecTag) {
                    case "complex" -> {
                        if (n.equals("re")) { s.reg.push(arr.getFloat(0)); return; }
                        if (n.equals("im")) { s.reg.push(arr.getFloat(1)); return; }
                    }
                    case "polar" -> {
                        if (n.equals("mag")) { s.reg.push(arr.getFloat(0)); return; }
                        if (n.equals("phase")) { s.reg.push(arr.getFloat(1)); return; }
                    }
                    case String t when t.startsWith("vec") -> {
                        if (n.equals("x")) { s.reg.push(arr.getFloat(0)); return; }
                        if (n.equals("y")) { s.reg.push(arr.getFloat(1)); return; }
                        if (n.equals("z")) { s.reg.push(arr.getFloat(2)); return; }
                        if (n.equals("w")) { s.reg.push(arr.getFloat(3)); return; }
                    }
                    default -> {}
                }
                s.reg.push(0L);
            } else if (obj instanceof UserObject uo) {
                ChuckObject fo = uo.getObjectField(n);
                if (fo != null) s.reg.pushObject(fo);
                else if (uo.isFloatField(n)) s.reg.push(uo.getFloatField(n));
                else s.reg.push(uo.getPrimitiveField(n));
            } else if (obj instanceof ChuckUGen ugen) {
                if (n.equals("last")) s.reg.push((double) ugen.getLastOut());
                else s.reg.push(0L);
            } else s.reg.push(0L);
        }
    }

    public static class GetUserField implements ChuckInstr {
        String n; public GetUserField(String v) { n = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek();
            if (uo == null) { s.reg.push(0L); return; }
            ChuckObject obj = uo.getObjectField(n);
            if (obj != null) s.reg.pushObject(obj);
            else if (uo.isFloatField(n)) s.reg.push(uo.getFloatField(n));
            else s.reg.push(uo.getPrimitiveField(n));
        }
    }

    public static class SetUserField implements ChuckInstr {
        String n; public SetUserField(String v) { n = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek();
            if (uo == null) return;
            if (s.reg.isObject(0)) uo.setObjectField(n, (ChuckObject) s.reg.peekObject(0));
            else if (uo.isFloatField(n)) uo.setFloatField(n, s.reg.peekAsDouble(0));
            else uo.setPrimitiveField(n, s.reg.peekLong(0));
        }
    }

    public static class GetStatic implements ChuckInstr {
        String cName, fName; public GetStatic(String c, String f) { cName = c; fName = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserClassDescriptor d = vm.getUserClass(cName);
            if (d == null) { s.reg.pushObject(null); return; }
            if (d.staticObjects().containsKey(fName)) s.reg.pushObject(d.staticObjects().get(fName));
            else if (d.staticIsDouble().getOrDefault(fName, false)) s.reg.push(Double.longBitsToDouble(d.staticInts().getOrDefault(fName, 0L)));
            else s.reg.push(d.staticInts().getOrDefault(fName, 0L));
        }
    }

    public static class SetStatic implements ChuckInstr {
        String cName, fName; public SetStatic(String c, String f) { cName = c; fName = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserClassDescriptor d = vm.getUserClass(cName);
            if (d == null) throw new RuntimeException("Static field set: class '" + cName + "' not found");
            if (s.reg.isObject(0)) {
                Object o = s.reg.peekObject(0);
                d.staticObjects().put(fName, o);
                if (o instanceof ChuckUGen u) s.registerUGen(u);
                if (o instanceof AutoCloseable ac) s.registerCloseable(ac);
            } else if (s.reg.isDouble(0)) {
                d.staticInts().put(fName, Double.doubleToRawLongBits(s.reg.peekAsDouble(0)));
            } else d.staticInts().put(fName, s.reg.peekLong(0));
        }
    }

    public static class GetBuiltinStatic implements ChuckInstr {
        String cName, fName; public GetBuiltinStatic(String c, String f) { cName = c; fName = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            try {
                java.lang.reflect.Field f = Class.forName(cName).getField(fName);
                Object val = f.get(null);
                if (val instanceof Number n) s.reg.push(n.longValue());
                else s.reg.pushObject(val);
            } catch (Exception e) { s.reg.push(0L); }
        }
    }
}
