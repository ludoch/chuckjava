package org.chuck.core.instr;

import org.chuck.core.*;
import java.util.Map;
import java.util.List;

public class ObjectInstrs {
    public static class NewObject implements ChuckInstr {
        String type; public NewObject(String t) { type = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(new UserObject(type, null, null, false));
        }
    }

    public static class CallMethod implements ChuckInstr {
        String mName;
        int a;
        String fullKey;

        public CallMethod(String m, int v) { this(m, v, null); }
        public CallMethod(String m, int v, String key) {
            mName = m; a = v; fullKey = key;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                isD[i] = s.reg.isDouble(0);
                if (s.reg.isObject(0)) {
                    Object o = s.reg.popObject();
                    args[i] = (o == null) ? "" : o;
                } else if (isD[i]) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            Object obj = s.reg.popObject();
            
            if (obj == null) {
                if (mName.equals("size") && a == 0) {
                    s.reg.push(0L);
                    return;
                }
                throw new RuntimeException("NullPointerException: cannot call method '" + mName + "' on null object");
            }

            UserObject uo = (obj instanceof UserObject) ? (UserObject) obj : null;
            if (uo != null) {
                String key = (fullKey != null) ? fullKey : (mName + ":" + a);
                String fallbackKey = mName + ":" + a;
                ChuckCode target = null;
                String t = uo.className;
                for (int depth = 0; depth < 16 && t != null; depth++) {
                    UserClassDescriptor desc = vm.getUserClass(t);
                    if (desc == null) break;
                    if (desc.methods().containsKey(key)) { target = desc.methods().get(key); break; }
                    if (desc.staticMethods().containsKey(key)) { target = desc.staticMethods().get(key); break; }
                    if (desc.methods().containsKey(fallbackKey)) { target = desc.methods().get(fallbackKey); break; }
                    if (desc.staticMethods().containsKey(fallbackKey)) { target = desc.staticMethods().get(fallbackKey); break; }
                    t = desc.parentName();
                }
                if (target != null) {
                    s.mem.pushObject(s.getCode());
                    s.mem.push((long) s.getPc());
                    s.mem.push((long) s.getFramePointer());
                    s.mem.push((long) s.reg.getSp());
                    s.setFramePointer(s.mem.getSp());
                    for (int i = 0; i < a; i++) {
                        Object arg = args[i];
                        if (arg instanceof ChuckObject co) s.mem.pushObject(co);
                        else if (isD[i]) s.mem.push((Double) arg);
                        else s.mem.push((Long) arg);
                    }
                    s.thisStack.push(uo);
                    s.setCode(target);
                    s.setPc(-1);
                    return;
                }
            }

            // Fallback to Java reflection for built-in types
            try {
                java.lang.reflect.Method bestMethod = null;
                Object[] bestArgs = null;
                int bestScore = -1;
                for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                    if (!m.getName().equals(mName) || m.getParameterCount() != a) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    Object[] coe = new Object[a];
                    int score = 0; boolean valid = true;
                    for (int i = 0; i < a; i++) {
                        Object val = args[i];
                        if (val == null) {
                            if (!pts[i].isPrimitive()) { coe[i] = null; score += 3; }
                            else { valid = false; break; }
                        } else if (val instanceof Long l) {
                            if (pts[i] == long.class || pts[i] == Long.class) { coe[i] = l; score += 3; }
                            else if (pts[i] == int.class || pts[i] == Integer.class) { coe[i] = l.intValue(); score += 2; }
                            else if (pts[i] == double.class || pts[i] == Double.class) { coe[i] = l.doubleValue(); score += 1; }
                            else if (pts[i] == float.class || pts[i] == Float.class) { coe[i] = l.floatValue(); score += 1; }
                            else { valid = false; break; }
                        } else if (val instanceof Double d) {
                            if (pts[i] == double.class || pts[i] == Double.class) { coe[i] = d; score += 3; }
                            else if (pts[i] == float.class || pts[i] == Float.class) { coe[i] = d.floatValue(); score += 2; }
                            else { valid = false; break; }
                        } else if (val instanceof ChuckString cs) {
                            if (pts[i] == String.class) { coe[i] = cs.toString(); score += 3; }
                            else { valid = false; break; }
                        } else if (pts[i].isInstance(val)) {
                            coe[i] = val; score += 2;
                        } else {
                            valid = false; break;
                        }
                    }
                    if (valid && score > bestScore) { bestScore = score; bestMethod = m; bestArgs = coe; }
                }
                if (bestMethod != null) {
                    Object res = bestMethod.invoke(obj, bestArgs);
                    if (bestMethod.getReturnType() == void.class) {
                        s.reg.pushObject(obj);
                    } else if (res == null) {
                        s.reg.pushObject(null);
                    } else {
                        Class<?> rt = bestMethod.getReturnType();
                        if (rt == int.class || rt == long.class || rt == Integer.class || rt == Long.class) s.reg.push(((Number) res).longValue());
                        else if (rt == float.class || rt == double.class || rt == Float.class || rt == Double.class) s.reg.push(((Number) res).doubleValue());
                        else if (res instanceof String str) s.reg.pushObject(new ChuckString(str));
                        else s.reg.pushObject(res);
                    }
                    return;
                }
            } catch (Exception e) {}

            s.reg.pushObject(null);
        }
    }

    public static class Spork implements ChuckInstr {
        ChuckCode t; int a;
        public Spork(ChuckCode target, int argCount) {
            t = target; a = argCount;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckShred ns = new ChuckShred(t);
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    isD[i] = s.reg.isDouble(0);
                    if (s.reg.isObject(0)) {
                        args[i] = s.reg.popObject();
                    } else if (isD[i]) {
                        args[i] = s.reg.popAsDouble();
                    } else {
                        args[i] = s.reg.popLong();
                    }
                }
            }
            for (int i = 0; i < a; i++) {
                Object arg = args[i];
                if (arg instanceof ChuckObject co) ns.reg.pushObject(co);
                else if (isD[i]) ns.reg.push((Double) arg);
                else if (arg instanceof Long l) ns.reg.push(l);
                else ns.reg.pushObject(arg);
            }
            ns.setParentShred(s);
            vm.spork(ns);
            s.reg.pushObject(ns);
        }
    }

    public static class SporkMethod implements ChuckInstr {
        String mName; int a; String fullKey;
        public SporkMethod(String m, int argCount) { this(m, argCount, null); }
        public SporkMethod(String m, int argCount, String key) {
            mName = m; a = argCount; fullKey = key;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    isD[i] = s.reg.isDouble(0);
                    if (s.reg.isObject(0)) {
                        args[i] = s.reg.popObject();
                    } else if (isD[i]) {
                        args[i] = s.reg.popAsDouble();
                    } else {
                        args[i] = s.reg.popLong();
                    }
                }
            }
            Object obj = s.reg.popObject();
            UserObject uo = (obj instanceof UserObject) ? (UserObject) obj : null;
            
            String key = (fullKey != null) ? fullKey : (mName + ":" + a);
            String fallbackKey = mName + ":" + a;
            ChuckCode target = null;
            boolean isStatic = false;

            if (uo != null) {
                target = uo.methods.get(key);
                if (target == null) target = uo.methods.get(fallbackKey);
                if (target == null) {
                    UserClassDescriptor d = vm.getUserClass(uo.className);
                    if (d != null) {
                        target = d.staticMethods().get(key);
                        if (target == null) target = d.staticMethods().get(fallbackKey);
                        isStatic = (target != null);
                    }
                }
            }

            if (target == null) {
                s.reg.pushObject(null);
                return;
            }
            
            ChuckShred ns = new ChuckShred(target);
            for (int i = 0; i < a; i++) {
                Object arg = args[i];
                if (arg instanceof ChuckObject co) ns.reg.pushObject(co);
                else if (isD[i]) ns.reg.push((Double) arg);
                else if (arg instanceof Long l) ns.reg.push(l);
                else ns.reg.pushObject(arg);
            }
            if (!isStatic && uo != null) ns.thisStack.push(uo);
            ns.setParentShred(s);
            vm.spork(ns);
            s.reg.pushObject(ns);
        }
    }

    public static class CallSuperMethod implements ChuckInstr {
        String startClass, mName; int a;
        public CallSuperMethod(String sc, String m, int argc) {
            startClass = sc; mName = m; a = argc;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                isD[i] = s.reg.isDouble(0);
                if (s.reg.isObject(0)) {
                    Object o = s.reg.popObject();
                    args[i] = (o == null) ? "" : o;
                } else if (isD[i]) args[i] = s.reg.popAsDouble();
                else args[i] = s.reg.popLong();
            }
            UserObject uo = s.thisStack.isEmpty() ? null : s.thisStack.peek();
            String t = startClass; ChuckCode target = null;
            for (int depth = 0; depth < 16 && t != null; depth++) {
                UserClassDescriptor desc = vm.getUserClass(t);
                if (desc == null) break;
                String key = mName + ":" + a;
                if (desc.methods().containsKey(key)) { target = desc.methods().get(key); break; }
                if (desc.staticMethods().containsKey(key)) { target = desc.staticMethods().get(key); break; }
                t = desc.parentName();
            }
            if (target != null) {
                s.mem.pushObject(s.getCode());
                s.mem.push((long) s.getPc());
                s.mem.push((long) s.getFramePointer());
                s.mem.push((long) s.reg.getSp());
                s.setFramePointer(s.mem.getSp());
                for (int i = 0; i < a; i++) {
                    Object arg = args[i];
                    if (arg instanceof ChuckObject co) s.mem.pushObject(co);
                    else if (isD[i]) s.mem.push((Double) arg);
                    else s.mem.push((Long) arg);
                }
                s.thisStack.push(uo);
                s.setCode(target);
                s.setPc(-1);
            }
        }
    }
}
