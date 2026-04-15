package org.chuck.core.instr;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckDuration;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckFactory;
import org.chuck.core.ChuckInstr;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckString;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;
import org.chuck.core.UserClassDescriptor;
import org.chuck.core.UserObject;

public class ObjectInstrs {
  public static class NewObject implements ChuckInstr {
    String type;

    public NewObject(String t) {
      type = t;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      ChuckObject obj =
          ChuckFactory.instantiateType(type, 0, null, vm.getSampleRate(), vm, s, null);
      s.reg.pushObject(obj);
    }
  }

  public static void runPreCtors(ChuckVM vm, ChuckShred s, String type, UserObject uo) {
    UserClassDescriptor desc = vm.getUserClass(type);
    if (desc == null) return;
    if (desc.parentName() != null) {
      runPreCtors(vm, s, desc.parentName(), uo);
    }
    if (desc.preCtorCode() != null) {
      s.thisStack.push(uo);
      vm.executeSynchronous(desc.preCtorCode(), s);
      s.thisStack.pop();
    }
  }

  public static class CallMethod implements ChuckInstr {
    String mName;
    int a;
    String fullKey;

    public CallMethod(String m, int v) {
      this(m, v, null);
    }

    public CallMethod(String m, int v, String key) {
      mName = m;
      a = v;
      fullKey = key;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
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
            args[i] = s.reg.popAsLong();
          }
        }
      }
      Object obj = s.reg.popObject();

      if (obj == null) {
        if (mName.equals("size") && a == 0) {
          s.reg.push(0L);
          return;
        }
        throw new org.chuck.core.ChuckRuntimeException(
            "NullPointerException: cannot call method '" + mName + "' on null object");
      }

      String className = null;
      UserObject uo = null;
      if (obj instanceof UserObject) {
        uo = (UserObject) obj;
        className = uo.className;
      } else if (obj instanceof ChuckArray a) {
        className = a.vecTag != null ? a.vecTag : "array";
      } else if (obj instanceof ChuckString) {
        className = "string";
      }
      if (obj instanceof ChuckShred) {
        className = "Shred";
      } else if (obj instanceof ChuckEvent) {
        className = "Event";
      }

      if (obj instanceof ChuckEvent ce) {
        switch (mName) {
          case "timeout" -> {
            if (a == 1 && args[0] instanceof ChuckDuration cd) ce.timeout(cd);
            s.reg.pushObject(ce);
            return;
          }
          case "signal" -> {
            ce.signal(vm);
            s.reg.pushObject(ce);
            return;
          }
          case "broadcast" -> {
            ce.broadcast(vm);
            s.reg.pushObject(ce);
            return;
          }
          case "can_wait" -> {
            s.reg.push(ce.can_wait() ? 1L : 0L);
            return;
          }
          case "wait" -> {
            ce.waitOn(s, vm);
            s.reg.pushObject(ce);
            return;
          }
        }
        if (mName.equals("signal") && a == 0) {
          ce.signal(vm);
          s.reg.pushObject(ce);
          return;
        }
        if (mName.equals("broadcast") && a == 0) {
          ce.broadcast(vm);
          s.reg.pushObject(ce);
          return;
        }
      }

      if (obj instanceof ChuckArray ca) {
        switch (mName) {
          case "size" -> {
            s.reg.push((long) ca.size());
            return;
          }
          case "cap", "capacity" -> {
            s.reg.push((long) ca.cap());
            return;
          }
          case "clear" -> {
            ca.clear();
            s.reg.pushObject(ca);
            return;
          }
          case "erase" -> {
            if (a == 1) ca.erase(((Number) args[0]).intValue());
            else if (a == 2) ca.erase(((Number) args[0]).intValue(), ((Number) args[1]).intValue());
            s.reg.pushObject(ca);
            return;
          }
          case "popFront" -> {
            ca.popFront();
            s.reg.pushObject(ca);
            return;
          }
          case "popBack" -> {
            ca.popBack();
            s.reg.pushObject(ca);
            return;
          }
          case "popOut" -> {
            if (a == 1) ca.popOut(((Number) args[0]).intValue());
            s.reg.pushObject(ca);
            return;
          }
          case "getKeys" -> {
            if (a == 1 && args[0] instanceof ChuckArray outArr) {
              ca.getKeys(outArr);
            }
            s.reg.pushObject(ca);
            return;
          }
          case "isInMap" -> {
            String key =
                args[0] instanceof ChuckString cs ? cs.toString() : String.valueOf(args[0]);
            s.reg.push(ca.isInMap(key));
            return;
          }
          case "find" -> {
            // array.find(val) — linear search returning index or -1
            long foundIdx = -1L;
            for (int i2 = 0; i2 < ca.size(); i2++) {
              if (ca.isObjectAt(i2)) {
                Object elem = ca.getObject(i2);
                if (args[0] != null && args[0].equals(elem)) {
                  foundIdx = i2;
                  break;
                }
              } else if (ca.isDoubleAt(i2)) {
                if (args[0] instanceof Number n && ca.getFloat(i2) == n.doubleValue()) {
                  foundIdx = i2;
                  break;
                }
              } else {
                if (args[0] instanceof Number n && ca.getInt(i2) == n.longValue()) {
                  foundIdx = i2;
                  break;
                }
              }
            }
            s.reg.push(foundIdx);
            return;
          }
          case "shuffle" -> {
            for (int i2 = ca.size() - 1; i2 > 0; i2--) {
              int j2 = (int) (Math.random() * (i2 + 1));
              // swap i2 and j2 via temporary using existing methods
              if (ca.isObjectAt(i2) || ca.isObjectAt(j2)) {
                Object tmp = ca.getObject(i2);
                ca.setObject(i2, ca.getObject(j2));
                ca.setObject(j2, tmp);
              } else if (ca.isDoubleAt(i2) || ca.isDoubleAt(j2)) {
                double tmp = ca.getFloat(i2);
                ca.setFloat(i2, ca.getFloat(j2));
                ca.setFloat(j2, tmp);
              } else {
                long tmp = ca.getInt(i2);
                ca.setInt(i2, ca.getInt(j2));
                ca.setInt(j2, tmp);
              }
            }
            s.reg.pushObject(ca);
            return;
          }
          case "reverse" -> {
            int left = 0, right = ca.size() - 1;
            while (left < right) {
              if (ca.isObjectAt(left) || ca.isObjectAt(right)) {
                Object tmp = ca.getObject(left);
                ca.setObject(left, ca.getObject(right));
                ca.setObject(right, tmp);
              } else if (ca.isDoubleAt(left) || ca.isDoubleAt(right)) {
                double tmp = ca.getFloat(left);
                ca.setFloat(left, ca.getFloat(right));
                ca.setFloat(right, tmp);
              } else {
                long tmp = ca.getInt(left);
                ca.setInt(left, ca.getInt(right));
                ca.setInt(right, tmp);
              }
              left++;
              right--;
            }
            s.reg.pushObject(ca);
            return;
          }
        }
      }

      if (className != null) {
        // Explicit dispatch for ChuckString (already handled array above)
        if (obj instanceof ChuckString cs) {
          switch (mName) {
            case "length" -> {
              s.reg.push(cs.length());
              return;
            }
            case "charAt" -> {
              s.reg.push(cs.charAt(((Number) args[0]).longValue()));
              return;
            }
            case "setCharAt" -> {
              cs.setCharAt(((Number) args[0]).longValue(), ((Number) args[1]).longValue());
              s.reg.pushObject(cs);
              return;
            }
            case "substring" -> {
              if (a == 1) s.reg.pushObject(cs.substring(((Number) args[0]).longValue()));
              else
                s.reg.pushObject(
                    cs.substring(((Number) args[0]).longValue(), ((Number) args[1]).longValue()));
              return;
            }
            case "insert" -> {
              s.reg.pushObject(cs.insert(((Number) args[0]).longValue(), args[1]));
              return;
            }
            case "erase" -> {
              s.reg.pushObject(
                  cs.erase(((Number) args[0]).longValue(), ((Number) args[1]).longValue()));
              return;
            }
            case "replace" -> {
              if (a == 2 && (args[0] instanceof Long || args[0] instanceof Double)) {
                s.reg.pushObject(cs.replace(((Number) args[0]).longValue(), args[1]));
              } else if (a == 2) {
                s.reg.pushObject(cs.replace(args[0], args[1]));
              } else if (a == 3) {
                s.reg.pushObject(
                    cs.replace(
                        ((Number) args[0]).longValue(), ((Number) args[1]).longValue(), args[2]));
              }
              return;
            }
            case "find" -> {
              if (a == 1) s.reg.push(cs.find(args[0]));
              else s.reg.push(cs.find(args[0], ((Number) args[1]).longValue()));
              return;
            }
            case "rfind" -> {
              if (a == 1) s.reg.push(cs.rfind(args[0]));
              else s.reg.push(cs.rfind(args[0], ((Number) args[1]).longValue()));
              return;
            }
            case "lower" -> {
              s.reg.pushObject(cs.lower());
              return;
            }
            case "upper" -> {
              s.reg.pushObject(cs.upper());
              return;
            }
            case "trim" -> {
              s.reg.pushObject(cs.trim());
              return;
            }
            case "ltrim" -> {
              s.reg.pushObject(cs.ltrim());
              return;
            }
            case "rtrim" -> {
              s.reg.pushObject(cs.rtrim());
              return;
            }
          }
        }

        String key = (fullKey != null) ? fullKey : (mName + ":" + a);
        String fallbackKey = mName + ":" + a;
        // Constructor call pattern: CallMethod("ClassName", argc) — also try @construct
        String ctorKey = mName.equals(className) ? ("@construct:" + a) : null;
        ChuckCode target = null;
        boolean isStatic = false;
        String t = className;
        for (int depth = 0; depth < 16 && t != null; depth++) {
          UserClassDescriptor desc = vm.getUserClass(t);
          if (desc == null) break;
          if (desc.methods().containsKey(key)) {
            target = desc.methods().get(key);
            isStatic = false;
            break;
          }
          if (desc.staticMethods().containsKey(key)) {
            target = desc.staticMethods().get(key);
            isStatic = true;
            break;
          }
          if (desc.methods().containsKey(fallbackKey)) {
            target = desc.methods().get(fallbackKey);
            isStatic = false;
            break;
          }
          if (desc.staticMethods().containsKey(fallbackKey)) {
            target = desc.staticMethods().get(fallbackKey);
            isStatic = true;
            break;
          }
          if (ctorKey != null && desc.methods().containsKey(ctorKey)) {
            target = desc.methods().get(ctorKey);
            isStatic = false;
            break;
          }
          t = desc.parentName();
        }
        if (target != null) {
          // 1. Save return frame
          s.mem.pushObject(s.getCode());
          s.mem.push((long) (s.getPc() + 1));
          s.mem.push((long) s.getFramePointer());
          s.mem.push((long) s.reg.getSp());

          // 2. Push arguments BACK to reg stack for MoveArgs to handle
          for (int i = 0; i < a; i++) {
            Object arg = args[i];
            if (arg instanceof ChuckObject co) s.reg.pushObject(co);
            else if (isD[i]) s.reg.push((Double) arg);
            else s.reg.push((Long) arg);
          }

          if (!isStatic) s.thisStack.push(uo);
          else s.thisStack.push(null);

          s.setCode(target);
          s.setPc(0);
          s.setFramePointer(s.mem.getSp());
          return;
        }
      }

      try {
        java.lang.reflect.Method bestMethod = null;
        Object[] bestArgs = null;
        int bestScore = -1;
        // In GraalVM native image, unregistered subclasses return empty from getMethods().
        // Walk up the hierarchy until we find a registered class (e.g. ChuckUGen).
        // Method.invoke() still does virtual dispatch on the actual instance.
        java.lang.reflect.Method[] reflMethods = obj.getClass().getMethods();
        if (reflMethods.length == 0) {
          Class<?> parent = obj.getClass().getSuperclass();
          while (parent != null && reflMethods.length == 0) {
            reflMethods = parent.getMethods();
            parent = parent.getSuperclass();
          }
        }
        for (java.lang.reflect.Method m : reflMethods) {
          if (!m.getName().equals(mName) || m.getParameterCount() != a) continue;
          Class<?>[] pts = m.getParameterTypes();
          Object[] coe = new Object[a];
          int score = 0;
          boolean valid = true;
          for (int i = 0; i < a; i++) {
            Object val = args[i];
            if (val == null) {
              if (!pts[i].isPrimitive()) {
                coe[i] = null;
                score += 3;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof Long l) {
              if (pts[i] == long.class || pts[i] == Long.class) {
                coe[i] = l;
                score += 3;
              } else if (pts[i] == int.class || pts[i] == Integer.class) {
                coe[i] = l.intValue();
                score += 2;
              } else if (pts[i] == double.class || pts[i] == Double.class) {
                coe[i] = l.doubleValue();
                score += 1;
              } else if (pts[i] == float.class || pts[i] == Float.class) {
                coe[i] = l.floatValue();
                score += 1;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof Double d) {
              if (pts[i] == double.class || pts[i] == Double.class) {
                coe[i] = d;
                score += 3;
              } else if (pts[i] == float.class || pts[i] == Float.class) {
                coe[i] = d.floatValue();
                score += 2;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof ChuckDuration cd) {
              if (pts[i] == double.class || pts[i] == Double.class) {
                coe[i] = cd.samples();
                score += 3;
              } else if (pts[i] == float.class || pts[i] == Float.class) {
                coe[i] = (float) cd.samples();
                score += 2;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof ChuckString cs) {
              if (pts[i] == String.class) {
                coe[i] = cs.toString();
                score += 3;
              } else {
                valid = false;
                break;
              }
            } else if (pts[i].isInstance(val)) {
              coe[i] = val;
              score += 2;
            } else if (pts[i] == Object.class) {
              coe[i] = val;
              score += 1;
            } else {
              valid = false;
              break;
            }
          }
          if (valid && score > bestScore) {
            bestScore = score;
            bestMethod = m;
            bestArgs = coe;
          }
        }
        if (bestMethod != null) {
          try {
            Object res = bestMethod.invoke(obj, bestArgs);
            if (bestMethod.getReturnType() == void.class) {
              s.reg.pushObject(obj);
            } else if (res == null) {
              s.reg.pushObject(null);
            } else {
              Class<?> rt = bestMethod.getReturnType();
              if (rt == int.class || rt == long.class || rt == Integer.class || rt == Long.class)
                s.reg.push(((Number) res).longValue());
              else if (rt == float.class
                  || rt == double.class
                  || rt == Float.class
                  || rt == Double.class) s.reg.push(((Number) res).doubleValue());
              else if (res instanceof String str) s.reg.pushObject(new ChuckString(str));
              else s.reg.pushObject(res);
            }
            return;
          } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new org.chuck.core.ChuckRuntimeException(
                ite.getClass().getSimpleName() + ": " + ite.getMessage());
          } catch (Exception e) {
            throw new org.chuck.core.ChuckRuntimeException(
                e.getClass().getSimpleName() + ": " + e.getMessage());
          }
        }
      } catch (Exception e) {
      }

      s.reg.pushObject(null);
    }
  }

  public static class Spork implements ChuckInstr {
    ChuckCode t;
    int a;

    public Spork(ChuckCode target, int argCount) {
      t = target;
      a = argCount;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
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
            args[i] = s.reg.popAsLong();
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
    String mName;
    int a;
    String fullKey;

    public SporkMethod(String m, int argCount) {
      this(m, argCount, null);
    }

    public SporkMethod(String m, int argCount, String key) {
      mName = m;
      a = argCount;
      fullKey = key;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
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
            args[i] = s.reg.popAsLong();
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
        // Constructor call: CallMethod("ClassName", argc) — resolve to @construct
        if (target == null && mName.equals(uo.className)) {
          String ctorKey = "@construct:" + a;
          target = uo.methods.get(ctorKey);
        }
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
    String startClass, mName;
    int a;

    public CallSuperMethod(String sc, String m, int argc) {
      startClass = sc;
      mName = m;
      a = argc;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
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
            args[i] = s.reg.popAsLong();
          }
        }
      }
      UserObject uo = s.thisStack.isEmpty() ? null : s.thisStack.peek();
      String t = startClass;
      ChuckCode target = null;
      for (int depth = 0; depth < 16 && t != null; depth++) {
        UserClassDescriptor desc = vm.getUserClass(t);
        if (desc == null) break;
        String key = mName + ":" + a;
        if (desc.methods().containsKey(key)) {
          target = desc.methods().get(key);
          break;
        }
        if (desc.staticMethods().containsKey(key)) {
          target = desc.staticMethods().get(key);
          break;
        }
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

  public static class InstantiateSetAndPushLocal implements ChuckInstr {
    String t;
    int o, a;
    boolean r, ar;
    Map<String, UserClassDescriptor> rm;

    public InstantiateSetAndPushLocal(
        String type,
        int off,
        int arg,
        boolean ref,
        boolean arr,
        Map<String, UserClassDescriptor> m) {
      t = type;
      o = off;
      a = arg;
      r = ref;
      ar = arr;
      rm = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object[] args = new Object[a];
      boolean[] isD = new boolean[a];
      for (int i = a - 1; i >= 0; i--) {
        if (s.reg.getSp() > 0) {
          isD[i] = s.reg.isDouble(0);
          if (s.reg.isObject(0)) args[i] = s.reg.popObject();
          else if (isD[i]) args[i] = s.reg.popAsDouble();
          else args[i] = s.reg.popAsLong();
        }
      }
      int fp = s.getFramePointer();
      if (r) {
        s.mem.setRef(fp + o, null);
        s.reg.pushObject(null);
        return;
      }

      // Check for existing object at this local offset?
      // Usually local variables are not re-instantiated if they are in the same shred run.
      // But ChucK variables are typically initialized once per scope entry.
      // For now, we assume if it's already an object, we use it.
      if (s.mem.isObjectAt(fp + o)) {
        Object existing = s.mem.getRef(fp + o);
        if (existing != null) {
          s.reg.pushObject(existing);
          return;
        }
      }

      Object obj = null;
      if (ar) {
        int sz = ((Number) args[0]).intValue();
        if (sz < 0) {
          ChuckArray ca =
              switch (t) {
                case "vec2" -> new ChuckArray(ChuckType.ARRAY, 2);
                case "vec3" -> new ChuckArray(ChuckType.ARRAY, 3);
                case "vec4" -> new ChuckArray(ChuckType.ARRAY, 4);
                case "complex", "polar" -> new ChuckArray(ChuckType.ARRAY, 2);
                default -> null;
              };
          if (ca != null) ca.vecTag = t;
          obj = ca;
        } else if (a > 1) {
          long[] dims = new long[a];
          for (int di = 0; di < a; di++) dims[di] = ((Number) args[di]).longValue();
          obj = ChuckFactory.buildMultiDimArray(dims, 0, t, vm, s, rm);
        } else {
          ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
          arr.elementTypeName = t;
          for (int i = 0; i < sz; i++) {
            ChuckObject elem =
                ChuckFactory.instantiateType(t, 0, null, vm.getSampleRate(), vm, s, rm);
            if (elem != null) {
              arr.setObject(i, elem);
              if (elem instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
            }
          }
          obj = arr;
        }
      } else if (t.equals("string")) {
        String initVal = "";
        if (a > 0 && args[0] instanceof ChuckString cs) initVal = cs.toString();
        else if (a > 0 && args[0] instanceof String sv) initVal = sv;
        obj = new ChuckString(initVal);
      } else if (!t.equals("int") && !t.equals("float") && !t.equals("dur") && !t.equals("time")) {
        obj = ChuckFactory.instantiateType(t, a, args, vm.getSampleRate(), vm, s, rm);
        if (a > 0
            && obj instanceof org.chuck.audio.ChuckUGen ugen
            && args[0] instanceof Number n2) {
          try {
            ugen.getClass().getMethod("freq", double.class).invoke(ugen, n2.doubleValue());
          } catch (Exception ignored) {
            try {
              ugen.setData(0, n2.longValue());
            } catch (Exception ignored2) {
            }
          }
        }
      }

      if (obj instanceof ChuckObject co) {
        s.mem.setRef(fp + o, co);
        if (co instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
        if (co instanceof AutoCloseable ac) s.registerCloseable(ac);
        s.reg.pushObject(co);
      } else {
        if (t.equals("float")) {
          s.mem.setData(fp + o, 0.0);
          s.reg.push(0.0);
        } else {
          s.mem.setData(fp + o, 0L);
          s.reg.push(0L);
        }
      }
      if (fp + o >= s.mem.getSp()) s.mem.setSp(fp + o + 1);
    }
  }

  public static class InstantiateSetAndPushGlobal implements ChuckInstr {
    String t, n;
    int a;
    boolean r, ar;
    Map<String, UserClassDescriptor> rm;

    public InstantiateSetAndPushGlobal(
        String type,
        String name,
        int arg,
        boolean ref,
        boolean arr,
        Map<String, UserClassDescriptor> m) {
      t = type;
      n = name;
      a = arg;
      r = ref;
      ar = arr;
      rm = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object[] args = new Object[a];
      boolean[] isD = new boolean[a];
      for (int i = a - 1; i >= 0; i--) {
        if (s.reg.getSp() > 0) {
          isD[i] = s.reg.isDouble(0);
          if (s.reg.isObject(0)) args[i] = s.reg.popObject();
          else if (isD[i]) args[i] = s.reg.popAsDouble();
          else args[i] = s.reg.popAsLong();
        }
      }
      if (r) {
        vm.setGlobalObject(n, null);
        s.reg.pushObject(null);
        return;
      }
      if (vm.isGlobalObject(n) && !n.startsWith("@new_")) {
        Object existing = vm.getGlobalObject(n);
        if (existing != null) {
          // Re-register with the current shred so cleanup() disconnects it when the shred ends.
          // Without this, a second spork reusing the same global UGen would leave it connected
          // to the DAC forever (the UGen would not be in the new shred's ownedUGens).
          if (existing instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
          s.reg.pushObject(existing);
          return;
        }
      }
      Object obj = null;
      if (ar) {
        int sz = ((Number) args[0]).intValue();
        if (sz < 0) {
          ChuckArray ca =
              switch (t) {
                case "vec2" -> new ChuckArray(ChuckType.ARRAY, 2);
                case "vec3" -> new ChuckArray(ChuckType.ARRAY, 3);
                case "vec4" -> new ChuckArray(ChuckType.ARRAY, 4);
                case "complex", "polar" -> new ChuckArray(ChuckType.ARRAY, 2);
                default -> null;
              };
          if (ca != null) ca.vecTag = t;
          obj = ca;
        } else if (a > 1) {
          long[] dims = new long[a];
          for (int di = 0; di < a; di++) dims[di] = ((Number) args[di]).longValue();
          obj = ChuckFactory.buildMultiDimArray(dims, 0, t, vm, s, rm);
        } else {
          ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
          arr.elementTypeName = t;
          for (int i = 0; i < sz; i++) {
            ChuckObject elem =
                ChuckFactory.instantiateType(t, 0, null, vm.getSampleRate(), vm, s, rm);
            if (elem != null) {
              arr.setObject(i, elem);
              if (elem instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
            }
          }
          obj = arr;
        }
      } else if (t.equals("string")) {
        // string ctor arg: string s("hello") initializes the value
        String initVal = "";
        if (a > 0 && args[0] instanceof ChuckString cs) initVal = cs.toString();
        else if (a > 0 && args[0] instanceof String sv) initVal = sv;
        obj = new ChuckString(initVal);
      } else if (!t.equals("int") && !t.equals("float") && !t.equals("dur") && !t.equals("time")) {
        obj = ChuckFactory.instantiateType(t, a, args, vm.getSampleRate(), vm, s, rm);
        // Apply single numeric ctor arg to UGen (e.g. SinOsc s(440) sets freq=440)
        if (a > 0
            && obj instanceof org.chuck.audio.ChuckUGen ugen
            && args[0] instanceof Number n2) {
          try {
            ugen.getClass().getMethod("freq", double.class).invoke(ugen, n2.doubleValue());
          } catch (Exception ignored) {
            try {
              ugen.setData(0, n2.longValue());
            } catch (Exception ignored2) {
            }
          }
        }
      }

      if (obj instanceof ChuckObject co) {
        vm.setGlobalObject(n, co);
        if (co instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
        if (co instanceof AutoCloseable ac) s.registerCloseable(ac);
        s.reg.pushObject(co);
      } else {
        if (t.equals("float")) {
          if (vm.isGlobalDouble(n)) {
            s.reg.push(Double.longBitsToDouble(vm.getGlobalInt(n)));
          } else {
            vm.setGlobalFloat(n, 0.0);
            s.reg.push(0.0);
          }
        } else if (t.equals("int")) {
          if (vm.isGlobalInt(n)) {
            s.reg.push(vm.getGlobalInt(n));
          } else {
            vm.setGlobalInt(n, 0L);
            s.reg.push(0L);
          }
        } else {
          vm.setGlobalObject(n, null);
          s.reg.pushObject(null);
        }
      }
    }
  }

  public static class CallBuiltinStatic implements ChuckInstr {
    String className, methodName;
    int argc;

    public CallBuiltinStatic(String c, String m, int a) {
      className = c;
      methodName = m;
      argc = a;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object[] args = new Object[argc];
      boolean[] isD = new boolean[argc];
      for (int i = argc - 1; i >= 0; i--) {
        if (s.reg.getSp() > 0) {
          isD[i] = s.reg.isDouble(0);
          if (s.reg.isObject(0)) args[i] = s.reg.popObject();
          else if (isD[i]) args[i] = s.reg.popAsDouble();
          else args[i] = s.reg.popAsLong();
        }
      }
      try {
        Class<?> clazz = Class.forName(className);
        java.lang.reflect.Method bestMethod = null;
        Object[] bestArgs = null;
        int bestScore = -1;
        for (java.lang.reflect.Method m : clazz.getMethods()) {
          if (!m.getName().equals(methodName)
              || m.getParameterCount() != argc
              || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
          Class<?>[] pts = m.getParameterTypes();
          Object[] coe = new Object[argc];
          int score = 0;
          boolean valid = true;
          for (int i = 0; i < argc; i++) {
            Object val = args[i];
            if (val == null) {
              if (!pts[i].isPrimitive()) {
                coe[i] = null;
                score += 3;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof Long l) {
              if (pts[i] == long.class || pts[i] == Long.class) {
                coe[i] = l;
                score += 3;
              } else if (pts[i] == int.class || pts[i] == Integer.class) {
                coe[i] = l.intValue();
                score += 2;
              } else if (pts[i] == double.class || pts[i] == Double.class) {
                coe[i] = l.doubleValue();
                score += 1;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof Double d) {
              if (pts[i] == double.class || pts[i] == Double.class) {
                coe[i] = d;
                score += 3;
              } else if (pts[i] == float.class || pts[i] == Float.class) {
                coe[i] = d.floatValue();
                score += 2;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof ChuckDuration cd) {
              if (pts[i] == double.class || pts[i] == Double.class) {
                coe[i] = cd.samples();
                score += 3;
              } else if (pts[i] == float.class || pts[i] == Float.class) {
                coe[i] = (float) cd.samples();
                score += 2;
              } else {
                valid = false;
                break;
              }
            } else if (val instanceof ChuckString cs) {
              if (pts[i] == String.class) {
                coe[i] = cs.toString();
                score += 3;
              } else {
                valid = false;
                break;
              }
            } else {
              if (pts[i].isInstance(val)) {
                coe[i] = val;
                score += 2;
              } else {
                coe[i] = val;
                score += 0;
              }
            }
          }
          if (valid && score > bestScore) {
            bestScore = score;
            bestMethod = m;
            bestArgs = coe;
          }
        }
        if (bestMethod != null) {
          Object res = bestMethod.invoke(null, bestArgs);
          if (bestMethod.getReturnType() == void.class) s.reg.push(0L);
          else if (res == null) s.reg.pushObject(null);
          else {
            Class<?> rt = bestMethod.getReturnType();
            if (rt == int.class || rt == long.class) s.reg.push(((Number) res).longValue());
            else if (rt == float.class || rt == double.class)
              s.reg.push(((Number) res).doubleValue());
            else if (res instanceof String str) s.reg.pushObject(new ChuckString(str));
            else s.reg.pushObject(res);
          }
        } else s.reg.push(0L);
      } catch (Exception e) {
        s.reg.push(0L);
      }
    }
  }

  public static class InstantiateSetAndPushField implements ChuckInstr {
    String t, n;
    int a;
    boolean r, ar;
    Map<String, UserClassDescriptor> rm;

    public InstantiateSetAndPushField(
        String type,
        String name,
        int arg,
        boolean ref,
        boolean arr,
        Map<String, UserClassDescriptor> m) {
      t = type;
      n = name;
      a = arg;
      r = ref;
      ar = arr;
      rm = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      Object thisObj = s.reg.popObject();
      if (!(thisObj instanceof UserObject uo_this)) {
        s.reg.pushObject(null);
        return;
      }

      Object[] args = new Object[a];
      boolean[] isD = new boolean[a];
      for (int i = a - 1; i >= 0; i--) {
        if (s.reg.getSp() > 0) {
          isD[i] = s.reg.isDouble(0);
          if (s.reg.isObject(0)) args[i] = s.reg.popObject();
          else if (isD[i]) args[i] = s.reg.popAsDouble();
          else args[i] = s.reg.popAsLong();
        }
      }

      if (r) {
        uo_this.setObjectField(n, null);
        s.reg.pushObject(null);
        return;
      }

      Object obj = null;
      if (ar) {
        if (a > 0 && args[0] != null) {
          int sz = ((Number) args[0]).intValue();
          if (sz < 0) {
            ChuckArray ca =
                switch (t) {
                  case "vec2" -> new ChuckArray(ChuckType.ARRAY, 2);
                  case "vec3" -> new ChuckArray(ChuckType.ARRAY, 3);
                  case "vec4" -> new ChuckArray(ChuckType.ARRAY, 4);
                  case "complex", "polar" -> new ChuckArray(ChuckType.ARRAY, 2);
                  default -> null;
                };
            if (ca != null) ca.vecTag = t;
            obj = ca;
          } else if (a > 1) {
            long[] dims = new long[a];
            for (int di = 0; di < a; di++) dims[di] = ((Number) args[di]).longValue();
            obj = ChuckFactory.buildMultiDimArray(dims, 0, t, vm, s, rm);
          } else {
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
            for (int i = 0; i < sz; i++) {
              ChuckObject elem =
                  ChuckFactory.instantiateType(t, 0, null, vm.getSampleRate(), vm, s, rm);
              if (elem != null) {
                arr.setObject(i, elem);
                if (elem instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
              }
            }
            obj = arr;
          }
        }
      } else if (t.equals("string")) {
        String initVal = "";
        if (a > 0 && args[0] instanceof ChuckString cs) initVal = cs.toString();
        else if (a > 0 && args[0] instanceof String sv) initVal = sv;
        obj = new ChuckString(initVal);
      } else {
        obj = ChuckFactory.instantiateType(t, a, args, vm.getSampleRate(), vm, s, rm);
        if (a > 0
            && obj instanceof org.chuck.audio.ChuckUGen ugen
            && args[0] instanceof Number n2) {
          try {
            ugen.getClass().getMethod("freq", double.class).invoke(ugen, n2.doubleValue());
          } catch (Exception ignored) {
            try {
              ugen.setData(0, n2.longValue());
            } catch (Exception ignored2) {
            }
          }
        }
      }

      if (obj instanceof ChuckObject co) {
        uo_this.setObjectField(n, co);
        if (co instanceof org.chuck.audio.ChuckUGen u) s.registerUGen(u);
        if (co instanceof AutoCloseable ac) s.registerCloseable(ac);
        s.reg.pushObject(co);
      } else {
        if (t.equals("float")) {
          uo_this.setFloatField(n, 0.0);
          s.reg.push(Double.doubleToRawLongBits(0.0));
        } else if (t.equals("int")) {
          uo_this.setPrimitiveField(n, 0L);
          s.reg.push(0L);
        } else {
          uo_this.setObjectField(n, null);
          s.reg.pushObject(null);
        }
      }
    }
  }

  public static class CallBuiltinFloat implements ChuckInstr {
    public String mName;

    public CallBuiltinFloat(String m) {
      mName = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      double val = s.reg.popAsDouble();
      Object obj = s.reg.popObject();
      if (obj instanceof org.chuck.audio.osc.Osc o) {
        if (mName.equals("freq")) o.freq(val);
        else if (mName.equals("gain")) o.gain((float) val);
        else if (mName.equals("phase")) o.setPhase(val);
      } else if (obj instanceof org.chuck.audio.ChuckUGen u) {
        if (mName.equals("gain")) u.gain((float) val);
      }
      s.reg.push(val);
    }
  }

  public static class CallBuiltinInt implements ChuckInstr {
    public String mName;

    public CallBuiltinInt(String m) {
      mName = m;
    }

    @Override
    public void execute(ChuckVM vm, ChuckShred s) {
      long val = s.reg.popAsLong();
      Object obj = s.reg.popObject();
      if (obj instanceof org.chuck.audio.osc.Osc o) {
        if (mName.equals("sync")) o.setSync((int) val);
      } else if (obj instanceof org.chuck.audio.filter.Lpf l) {
        if (mName.equals("cutoff")) l.setCutoff((float) val);
      }
      s.reg.push(val);
    }
  }
}
