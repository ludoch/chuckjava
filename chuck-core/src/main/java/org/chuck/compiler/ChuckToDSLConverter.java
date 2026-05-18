package org.chuck.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Converts a ChucK AST into Java DSL source code. */
public class ChuckToDSLConverter {

  private final Set<String> globals = new HashSet<>();
  private final Set<String> userClasses = new HashSet<>();
  private final Map<String, String> userClassParents = new HashMap<>();
  private final Map<String, Map<String, String>> classFieldTypes = new HashMap<>();
  private final Set<String> userFunctions = new HashSet<>();
  private final Map<String, List<List<String>>> functionSignatures = new HashMap<>();
  private final Map<String, String> varTypes = new HashMap<>();
  private final Map<String, String> arrayElementTypes = new HashMap<>();
  private final Map<String, Integer> arrayDepths = new HashMap<>();
  private final Map<String, Integer> methodNameCounts = new HashMap<>();
  private final List<ChuckAST.DeclStmt> arraysToInit = new ArrayList<>();
  private final List<ChuckAST.DeclStmt> fields = new ArrayList<>();
  private Set<String> currentFunctionLocals = null;
  private final List<String> activeLoopVars = new ArrayList<>();
  private boolean isFieldMode = false;
  private String currentClassName = null;
  private String currentReturnType = null;
  private int sporkCaptureCounter = 0;

  public String convert(List<ChuckAST.Stmt> program, String className) {
    globals.clear();
    userClasses.clear();
    userClassParents.clear();
    classFieldTypes.clear();
    userFunctions.clear();
    functionSignatures.clear();
    methodNameCounts.clear();
    varTypes.clear();
    arrayElementTypes.clear();
    arrayDepths.clear();
    arraysToInit.clear();
    fields.clear();
    currentFunctionLocals = null;
    activeLoopVars.clear();
    currentClassName = className;
    currentReturnType = null;
    sporkCaptureCounter = 0;

    for (ChuckAST.Stmt s : program) {
      if (s instanceof ChuckAST.DeclStmt ds) {
        String rawType = mapType(ds.type());
        String type = rawType;
        String safe = safeName(ds.name());
        if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
          if (type.startsWith("ChuckEvent")) {
            type = "ChuckEvent[]";
          } else {
            type = "ChuckArray";
          }
        }
        varTypes.put(safe, type);
        if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
          arrayElementTypes.put(safe, normalizeArrayElementType(rawType));
          arrayDepths.put(safe, ds.arraySizes().size());
        }
        if (ds.isGlobal()) globals.add(safe);
      }
      if (s instanceof ChuckAST.ClassDefStmt cds) {
        String clsName = safeName(cds.name());
        userClasses.add(clsName);
        if (cds.parentName() != null) {
          userClassParents.put(clsName, safeName(cds.parentName()));
        }
        Map<String, String> memberTypes = new HashMap<>();
        for (ChuckAST.Stmt bodyStmt : cds.body()) {
          if (bodyStmt instanceof ChuckAST.DeclStmt ds) {
            String mappedType = mapType(ds.type());
            if (ds.arraySizes() != null
                && !ds.arraySizes().isEmpty()
                && !mappedType.startsWith("ChuckEvent")) {
              mappedType = "ChuckArray";
            }
            memberTypes.put(safeName(ds.name()), mappedType);
          }
        }
        classFieldTypes.put(clsName, memberTypes);
      }
      if (s instanceof ChuckAST.FuncDefStmt fds) {
        String functionName = normalizeFunctionName(fds.name());
        userFunctions.add(functionName);
        registerFunctionSignature(currentClassName, functionName, fds.argTypes());
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("import static org.chuck.core.ChuckDSL.*;\n");
    sb.append("import org.chuck.audio.*;\n");
    sb.append("import org.chuck.audio.osc.*;\n");
    sb.append("import org.chuck.audio.filter.*;\n");
    sb.append("import org.chuck.audio.fx.*;\n");
    sb.append("import org.chuck.audio.stk.*;\n");
    sb.append("import org.chuck.audio.util.*;\n");
    sb.append("import org.chuck.audio.chugins.*;\n");
    sb.append("import org.chuck.audio.analysis.*;\n");
    sb.append("import org.chuck.core.ai.*;\n");
    sb.append("import org.chuck.network.*;\n");
    sb.append("import org.chuck.midi.*;\n");
    sb.append("import org.chuck.hid.*;\n");
    sb.append("import org.chuck.core.*;\n\n");

    sb.append("public class ").append(className).append(" implements Shred {\n");
    sb.append(indent("private static double __std_mtof_tmp = 0.0;", 1)).append("\n");
    sb.append(indent("private static double __std_ftom_tmp = 0.0;", 1)).append("\n");
    sb.append(indent("private static long __maxBin_tmp = 0L;", 1)).append("\n");
    sb.append("\n");

    List<ChuckAST.FuncDefStmt> methods =
        program.stream()
            .filter(s -> s instanceof ChuckAST.FuncDefStmt)
            .map(s -> (ChuckAST.FuncDefStmt) s)
            .toList();
    List<ChuckAST.ClassDefStmt> classes =
        program.stream()
            .filter(s -> s instanceof ChuckAST.ClassDefStmt)
            .map(s -> (ChuckAST.ClassDefStmt) s)
            .toList();
    List<ChuckAST.Stmt> shredBody =
        program.stream()
            .filter(
                s -> !(s instanceof ChuckAST.FuncDefStmt) && !(s instanceof ChuckAST.ClassDefStmt))
            .toList();

    collectFields(program);

    // 2. Emit Fields
    isFieldMode = true;
    Set<String> emittedFields = new HashSet<>();
    for (ChuckAST.DeclStmt field : fields) {
      String sName = safeName(field.name());
      if (emittedFields.contains(sName)) continue;
      emittedFields.add(sName);
      String type = mapType(field.type());
      String declType = type;
      String init = "";
      String modifiers = "public ";
      if (field.isStatic()) modifiers += "static ";

      if (field.arraySizes() != null && !field.arraySizes().isEmpty()) {
        String baseType = type;
        while (baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);
        String size = visitExp(field.arraySizes().get(0));
        if (size.equals("-1") || size.startsWith("(-1")) size = "0";

        if (type.startsWith("ChuckEvent")) {
          declType = "ChuckEvent[]";
          init = " = new ChuckEvent[" + size + "]" + "[]".repeat(field.arraySizes().size() - 1);
        } else {
          declType = "ChuckArray";
          init = " = new ChuckArray(\"" + baseType + "\", (int)(" + size + "))";
        }
      } else if (isUGen(type)
          || userClasses.contains(type)
          || (!isPrimitive(type) && !type.equals("String") && !type.equals("Object"))) {
        if ("Complex".equals(type) || "Polar".equals(type)) {
          init = " = new " + type + "(0f, 0f)";
        } else {
          init = " = _new(" + type + ".class)";
        }
      } else if (type.equals("ChuckDuration")) {
        init = " = new ChuckDuration(0)";
      } else if (type.equals("String")) {
        init = " = null";
      } else if (isPrimitive(type)) {
        init = " = (" + type + ")(0)";
      }
      sb.append(indent(modifiers + declType + " " + sName + init + ";", 1)).append("\n");
    }
    isFieldMode = false;
    sb.append("\n");
    sb.append(indent("private static <T> T _new(Class<T> cls) {", 1)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("return cls.getDeclaredConstructor().newInstance();", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("float sr = sampleRate();", 2)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("return cls.getDeclaredConstructor(float.class).newInstance(sr);", 3))
        .append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(
            indent("return cls.getDeclaredConstructor(double.class).newInstance((double) sr);", 3))
        .append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {", 2))
        .append("\n");
    sb.append(indent("try {", 3)).append("\n");
    sb.append(indent("Class<?>[] ps = ctor.getParameterTypes();", 4)).append("\n");
    sb.append(indent("Object[] args = new Object[ps.length];", 4)).append("\n");
    sb.append(
            indent("for (int i = 0; i < ps.length; i++) args[i] = _defaultCtorArg(ps[i], sr);", 4))
        .append("\n");
    sb.append(indent("ctor.setAccessible(true);", 4)).append("\n");
    sb.append(indent("@SuppressWarnings(\"unchecked\")", 4)).append("\n");
    sb.append(indent("T instance = (T) ctor.newInstance(args);", 4)).append("\n");
    sb.append(indent("return instance;", 4)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 3)).append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("throw new RuntimeException(\"Cannot construct \" + cls.getName());", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static Object _defaultCtorArg(Class<?> p, float sr) {", 1))
        .append("\n");
    sb.append(indent("if (p == float.class || p == Float.class) return sr;", 2)).append("\n");
    sb.append(indent("if (p == double.class || p == Double.class) return (double) sr;", 2))
        .append("\n");
    sb.append(indent("if (p == int.class || p == Integer.class) return 0;", 2)).append("\n");
    sb.append(indent("if (p == long.class || p == Long.class) return 0L;", 2)).append("\n");
    sb.append(indent("if (p == short.class || p == Short.class) return (short) 0;", 2))
        .append("\n");
    sb.append(indent("if (p == byte.class || p == Byte.class) return (byte) 0;", 2)).append("\n");
    sb.append(indent("if (p == boolean.class || p == Boolean.class) return false;", 2))
        .append("\n");
    sb.append(indent("if (p == char.class || p == Character.class) return (char) 0;", 2))
        .append("\n");
    sb.append(indent("return null;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static ChuckUGen _ugenChan(Object u, long i) {", 1)).append("\n");
    sb.append(indent("if (u == null) return null;", 2)).append("\n");
    sb.append(indent("if (!(u instanceof ChuckUGen cu)) {", 2)).append("\n");
    sb.append(
            indent(
                "Object r = (i <= 0) ? _call(u, \"left\") : ((i == 1) ? _call(u, \"right\") : _call(u, \"chan\", i));",
                3))
        .append("\n");
    sb.append(indent("return (r instanceof ChuckUGen ru) ? ru : null;", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("String name = (i <= 0) ? \"left\" : ((i == 1) ? \"right\" : \"chan\");", 2))
        .append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("if (\"chan\".equals(name)) {", 3)).append("\n");
    sb.append(
            indent(
                "var m = cu.getClass().getMethod(\"chan\", long.class); return (ChuckUGen) m.invoke(cu, i);",
                4))
        .append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("var m = cu.getClass().getMethod(name);", 3)).append("\n");
    sb.append(indent("return (ChuckUGen) m.invoke(cu);", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("return cu;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static ChuckArray _arrObj(String t, Object... vals) {", 1))
        .append("\n");
    sb.append(indent("ChuckArray a = new ChuckArray(t, vals.length);", 2)).append("\n");
    sb.append(indent("for (int i = 0; i < vals.length; i++) a.setObject(i, vals[i]);", 2))
        .append("\n");
    sb.append(indent("return a;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static void _stmt(Object ignored) {", 1)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static void _ioFlush(Object io) {", 1)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("var m = io.getClass().getMethod(\"flush\");", 3)).append("\n");
    sb.append(indent("m.invoke(io);", 3)).append("\n");
    sb.append(indent("return;", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("if (io == cherr) System.err.flush(); else System.out.flush();", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static Object _machineCall(String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("for (var mm : Machine.class.getMethods()) {", 3)).append("\n");
    sb.append(
            indent(
                "if (!mm.getName().equals(m) || mm.getParameterCount() != a.length) continue;", 4))
        .append("\n");
    sb.append(indent("Class<?>[] p = mm.getParameterTypes();", 4)).append("\n");
    sb.append(indent("Object[] c = new Object[a.length];", 4)).append("\n");
    sb.append(indent("for (int i = 0; i < a.length; i++) c[i] = _coerce(a[i], p[i]);", 4))
        .append("\n");
    sb.append(indent("return mm.invoke(null, c);", 4)).append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("return null;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static Object _call(Object t, String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("if (t == null) return null;", 2)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("for (var mm : t.getClass().getMethods()) {", 3)).append("\n");
    sb.append(
            indent(
                "if (!mm.getName().equals(m) || mm.getParameterCount() != a.length) continue;", 4))
        .append("\n");
    sb.append(indent("Object[] c = new Object[a.length];", 4)).append("\n");
    sb.append(indent("Class<?>[] p = mm.getParameterTypes();", 4)).append("\n");
    sb.append(indent("for (int i = 0; i < a.length; i++) c[i] = _coerce(a[i], p[i]);", 4))
        .append("\n");
    sb.append(indent("return mm.invoke(t, c);", 4)).append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("if (a.length == 0) {", 3)).append("\n");
    sb.append(indent("var f = t.getClass().getField(m);", 4)).append("\n");
    sb.append(indent("return f.get(t);", 4)).append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("if (a.length == 1) {", 3)).append("\n");
    sb.append(indent("var f = t.getClass().getField(m);", 4)).append("\n");
    sb.append(indent("f.set(t, _coerce(a[0], f.getType()));", 4)).append("\n");
    sb.append(indent("return a[0];", 4)).append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("return null;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static boolean _callBool(Object t, String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("Object r = _call(t, m, a);", 2)).append("\n");
    sb.append(indent("if (r instanceof Boolean b) return b;", 2)).append("\n");
    sb.append(indent("if (r instanceof java.lang.Number n) return n.doubleValue() != 0.0;", 2))
        .append("\n");
    sb.append(indent("return r != null;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static long _callLong(Object t, String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("Object r = _call(t, m, a);", 2)).append("\n");
    sb.append(indent("return (r instanceof java.lang.Number n) ? n.longValue() : 0L;", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static double _callDouble(Object t, String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("Object r = _call(t, m, a);", 2)).append("\n");
    sb.append(indent("return (r instanceof java.lang.Number n) ? n.doubleValue() : 0.0;", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static double _num(Object v) {", 1)).append("\n");
    sb.append(indent("if (v instanceof java.lang.Number n) return n.doubleValue();", 2))
        .append("\n");
    sb.append(indent("if (v instanceof Boolean b) return b ? 1.0 : 0.0;", 2)).append("\n");
    sb.append(indent("if (v instanceof Complex c) return c.re;", 2)).append("\n");
    sb.append(indent("if (v instanceof Polar p) return p.mag;", 2)).append("\n");
    sb.append(indent("return 0.0;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static boolean _truthy(Object v) {", 1)).append("\n");
    sb.append(indent("if (v instanceof Boolean b) return b;", 2)).append("\n");
    sb.append(indent("if (v instanceof java.lang.Number n) return n.doubleValue() != 0.0;", 2))
        .append("\n");
    sb.append(indent("if (v instanceof ChuckDuration d) return d.samples() != 0.0;", 2))
        .append("\n");
    sb.append(indent("if (v instanceof String s) return !s.isEmpty();", 2)).append("\n");
    sb.append(indent("if (v instanceof ChuckArray a) return a.size() > 0;", 2)).append("\n");
    sb.append(indent("if (v instanceof Object[] a) return a.length > 0;", 2)).append("\n");
    sb.append(indent("return v != null;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static ChuckArray _callArray(Object t, String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("Object r = _call(t, m, a);", 2)).append("\n");
    sb.append(indent("return (r instanceof ChuckArray ca) ? ca : new ChuckArray(\"float\", 0);", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static ChuckArray _toChuckArray(Object value) {", 1)).append("\n");
    sb.append(indent("if (value instanceof ChuckArray ca) return ca;", 2)).append("\n");
    sb.append(indent("if (value == null) return new ChuckArray(\"Object\", 0);", 2)).append("\n");
    sb.append(indent("if (value instanceof Object[] arr) {", 2)).append("\n");
    sb.append(indent("ChuckArray out = new ChuckArray(\"Object\", arr.length);", 3)).append("\n");
    sb.append(indent("for (int i = 0; i < arr.length; i++) out.setObject(i, arr[i]);", 3))
        .append("\n");
    sb.append(indent("return out;", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent("if (value instanceof String[] arr) return new ChuckArray(\"String\", arr);", 2))
        .append("\n");
    sb.append(
            indent("if (value instanceof double[] arr) return new ChuckArray(\"float\", arr);", 2))
        .append("\n");
    sb.append(indent("if (value instanceof float[] arr) return new ChuckArray(arr);", 2))
        .append("\n");
    sb.append(indent("if (value instanceof long[] arr) return new ChuckArray(\"int\", arr);", 2))
        .append("\n");
    sb.append(indent("if (value instanceof int[] arr) return new ChuckArray(arr);", 2))
        .append("\n");
    sb.append(indent("if (value instanceof boolean[] arr) {", 2)).append("\n");
    sb.append(indent("long[] iv = new long[arr.length];", 3)).append("\n");
    sb.append(indent("for (int i = 0; i < arr.length; i++) iv[i] = arr[i] ? 1L : 0L;", 3))
        .append("\n");
    sb.append(indent("return new ChuckArray(\"int\", iv);", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("if (value.getClass().isArray()) {", 2)).append("\n");
    sb.append(indent("int n = java.lang.reflect.Array.getLength(value);", 3)).append("\n");
    sb.append(indent("Object[] boxed = new Object[n];", 3)).append("\n");
    sb.append(
            indent(
                "for (int i = 0; i < n; i++) boxed[i] = java.lang.reflect.Array.get(value, i);", 3))
        .append("\n");
    sb.append(indent("ChuckArray out = new ChuckArray(\"Object\", boxed.length);", 3)).append("\n");
    sb.append(indent("for (int i = 0; i < boxed.length; i++) out.setObject(i, boxed[i]);", 3))
        .append("\n");
    sb.append(indent("return out;", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("return new ChuckArray(\"Object\", 0);", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static long _sizeOf(Object o) {", 1)).append("\n");
    sb.append(indent("if (o == null) return 0L;", 2)).append("\n");
    sb.append(indent("if (o instanceof ChuckArray a) return a.size();", 2)).append("\n");
    sb.append(indent("if (o instanceof Object[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof double[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof float[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof long[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof int[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof short[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof byte[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof char[] a) return a.length;", 2)).append("\n");
    sb.append(indent("if (o instanceof boolean[] a) return a.length;", 2)).append("\n");
    sb.append(indent("Object r = _call(o, \"size\");", 2)).append("\n");
    sb.append(indent("if (r instanceof java.lang.Number n) return n.longValue();", 2)).append("\n");
    sb.append(indent("r = _call(o, \"length\");", 2)).append("\n");
    sb.append(indent("if (r instanceof java.lang.Number n) return n.longValue();", 2)).append("\n");
    sb.append(indent("if (o.getClass().isArray()) return java.lang.reflect.Array.getLength(o);", 2))
        .append("\n");
    sb.append(indent("return 0L;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static long _isa(Object o, String typeName) {", 1)).append("\n");
    sb.append(indent("if (o == null || typeName == null) return 0L;", 2)).append("\n");
    sb.append(indent("Object r = _call(o, \"isa\", typeName);", 2)).append("\n");
    sb.append(indent("if (r instanceof java.lang.Number n) return n.longValue();", 2)).append("\n");
    sb.append(indent("if (r instanceof Boolean b) return b ? 1L : 0L;", 2)).append("\n");
    sb.append(indent("String cn = o.getClass().getSimpleName();", 2)).append("\n");
    sb.append(indent("return cn.equals(typeName) ? 1L : 0L;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static Object _chuckConnect(Object lhs, Object rhs) {", 1))
        .append("\n");
    sb.append(indent("if (lhs == null) return rhs;", 2)).append("\n");
    sb.append(indent("Object r = _call(lhs, \"chuck\", rhs);", 2)).append("\n");
    sb.append(indent("if (r == null) _call(lhs, \"chuckTo\", rhs);", 2)).append("\n");
    sb.append(indent("return rhs;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static Object _chuckWrite(Object lhs, Object rhs) {", 1))
        .append("\n");
    sb.append(indent("if (lhs == null) return null;", 2)).append("\n");
    sb.append(indent("_call(lhs, \"write\", rhs);", 2)).append("\n");
    sb.append(indent("return lhs;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static Object _coerce(Object value, Class<?> t) {", 1)).append("\n");
    sb.append(indent("if (value == null) return null;", 2)).append("\n");
    sb.append(indent("if (t.isInstance(value)) return value;", 2)).append("\n");
    sb.append(indent("if (!(value instanceof java.lang.Number n)) return value;", 2)).append("\n");
    sb.append(indent("if (t == float.class || t == Float.class) return n.floatValue();", 2))
        .append("\n");
    sb.append(indent("if (t == double.class || t == Double.class) return n.doubleValue();", 2))
        .append("\n");
    sb.append(indent("if (t == int.class || t == Integer.class) return n.intValue();", 2))
        .append("\n");
    sb.append(indent("if (t == long.class || t == Long.class) return n.longValue();", 2))
        .append("\n");
    sb.append(indent("if (t == short.class || t == Short.class) return n.shortValue();", 2))
        .append("\n");
    sb.append(indent("if (t == byte.class || t == Byte.class) return n.byteValue();", 2))
        .append("\n");
    sb.append(indent("return value;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static ChuckDuration _toDur(Object value) {", 1)).append("\n");
    sb.append(indent("if (value instanceof ChuckDuration d) return d;", 2)).append("\n");
    sb.append(
            indent(
                "if (value instanceof java.lang.Number n) return samp().times(n.longValue());", 2))
        .append("\n");
    sb.append(indent("return samp().times(0);", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static boolean _advanceAndTrue(Object value) {", 1)).append("\n");
    sb.append(indent("advance(_toDur(value));", 2)).append("\n");
    sb.append(indent("return true;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static double _ftom(double a) {", 1)).append("\n");
    sb.append(indent("return (69.0 + (12.0 * (Math.log((a) / 440.0) / Math.log(2.0))));", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(
            indent(
                "private static void _chuckSetAny(Object target, String member, Object value) {",
                1))
        .append("\n");
    sb.append(indent("if (target == null) return;", 2)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("for (var m : target.getClass().getMethods()) {", 3)).append("\n");
    sb.append(indent("if (!m.getName().equals(member) || m.getParameterCount() != 1) continue;", 4))
        .append("\n");
    sb.append(indent("Class<?> t = m.getParameterTypes()[0];", 4)).append("\n");
    sb.append(indent("m.invoke(target, _coerce(value, t));", 4)).append("\n");
    sb.append(indent("return;", 4)).append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("var f = target.getClass().getField(member);", 3)).append("\n");
    sb.append(indent("f.set(target, _coerce(value, f.getType()));", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(
            indent(
                "private static double _chuckSet(Object target, String member, double value) {", 1))
        .append("\n");
    sb.append(indent("_chuckSetAny(target, member, value);", 2)).append("\n");
    sb.append(indent("return value;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(
            indent(
                "private static float _chuckSet(Object target, String member, float value) {", 1))
        .append("\n");
    sb.append(indent("_chuckSetAny(target, member, value);", 2)).append("\n");
    sb.append(indent("return value;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(
            indent(
                "private static Object _chuckSet(Object target, String member, Object value) {", 1))
        .append("\n");
    sb.append(indent("_chuckSetAny(target, member, value);", 2)).append("\n");
    sb.append(indent("return value;", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class CKDoc {", 1)).append("\n");
    sb.append(indent("private CKDoc() {}", 2)).append("\n");
    sb.append(indent("static String describe(Object value) { return String.valueOf(value); }", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class Type {", 1)).append("\n");
    sb.append(indent("static final long PRIMITIVE = 1L;", 2)).append("\n");
    sb.append(indent("static final long BUILTIN = 2L;", 2)).append("\n");
    sb.append(indent("static final long OBJECT = 3L;", 2)).append("\n");
    sb.append(indent("static final long CHUGIN = 4L;", 2)).append("\n");
    sb.append(indent("private final String typeName;", 2)).append("\n");
    sb.append(indent("private final String typeOrigin;", 2)).append("\n");
    sb.append(indent("private Type(String typeName, String typeOrigin) {", 2)).append("\n");
    sb.append(indent("this.typeName = typeName;", 3)).append("\n");
    sb.append(indent("this.typeOrigin = typeOrigin;", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("Type() { this(\"Unknown\", \"builtin\"); }", 2)).append("\n");
    sb.append(indent("String name() { return typeName; }", 2)).append("\n");
    sb.append(indent("String baseName() { return typeName; }", 2)).append("\n");
    sb.append(indent("String origin() { return typeOrigin; }", 2)).append("\n");
    sb.append(
            indent(
                "boolean isPrimitive() { return \"int\".equals(typeName) || \"float\".equals(typeName) || \"long\".equals(typeName) || \"double\".equals(typeName) || \"dur\".equals(typeName) || \"time\".equals(typeName) || \"string\".equals(typeName) || \"boolean\".equals(typeName); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "boolean isArray() { return typeName != null && typeName.endsWith(\"[]\"); }", 2))
        .append("\n");
    sb.append(indent("long arrayDepth() {", 2)).append("\n");
    sb.append(indent("if (typeName == null) return 0L;", 3)).append("\n");
    sb.append(indent("long d = 0L;", 3)).append("\n");
    sb.append(indent("String n = typeName;", 3)).append("\n");
    sb.append(indent("while (n.endsWith(\"[]\")) { d++; n = n.substring(0, n.length() - 2); }", 3))
        .append("\n");
    sb.append(indent("return d;", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("static ChuckArray getTypes(long... ignoredFlags) {", 2)).append("\n");
    sb.append(indent("return new ChuckArray(\"Object\", 0);", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("static Type of(Object value) {", 2)).append("\n");
    sb.append(indent("if (value instanceof Type t) return t;", 3)).append("\n");
    sb.append(
            indent(
                "if (value instanceof java.lang.Integer || value instanceof java.lang.Long) return new Type(\"int\", \"primitive\");",
                3))
        .append("\n");
    sb.append(
            indent(
                "if (value instanceof java.lang.Float || value instanceof java.lang.Double) return new Type(\"float\", \"primitive\");",
                3))
        .append("\n");
    sb.append(
            indent(
                "if (value instanceof ChuckDuration) return new Type(\"dur\", \"primitive\");", 3))
        .append("\n");
    sb.append(indent("if (value instanceof Complex) return new Type(\"Complex\", \"object\");", 3))
        .append("\n");
    sb.append(indent("if (value instanceof Polar) return new Type(\"Polar\", \"object\");", 3))
        .append("\n");
    sb.append(indent("if (value instanceof vec3) return new Type(\"vec3\", \"object\");", 3))
        .append("\n");
    sb.append(indent("if (value instanceof vec4) return new Type(\"vec4\", \"object\");", 3))
        .append("\n");
    sb.append(indent("if (value instanceof ChuckUGen) return new Type(\"UGen\", \"builtin\");", 3))
        .append("\n");
    sb.append(indent("if (value instanceof ChuckArray) return new Type(\"array\", \"object\");", 3))
        .append("\n");
    sb.append(indent("if (value == null) return new Type(\"null\", \"builtin\");", 3)).append("\n");
    sb.append(indent("return new Type(value.getClass().getSimpleName(), \"object\");", 3))
        .append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("static Type of(int value) { return of((Object) value); }", 2)).append("\n");
    sb.append(indent("static Type of(long value) { return of((Object) value); }", 2)).append("\n");
    sb.append(indent("static Type of(double value) { return of((Object) value); }", 2))
        .append("\n");
    sb.append(indent("static Type of(ChuckDuration value) { return of((Object) value); }", 2))
        .append("\n");
    sb.append(indent("static Type of(Complex value) { return of((Object) value); }", 2))
        .append("\n");
    sb.append(indent("static Type of(Polar value) { return of((Object) value); }", 2)).append("\n");
    sb.append(indent("static Type of(vec3 value) { return of((Object) value); }", 2)).append("\n");
    sb.append(indent("static Type of(vec4 value) { return of((Object) value); }", 2)).append("\n");
    sb.append(indent("static Type of(ChuckUGen value) { return of((Object) value); }", 2))
        .append("\n");
    sb.append(indent("static Type of(ChuckArray value) { return of((Object) value); }", 2))
        .append("\n");
    sb.append(indent("static Type find(String name) { return new Type(name, \"builtin\"); }", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    if (!userClasses.contains("BPM")) {
      sb.append(indent("private static final class BPM {", 1)).append("\n");
      sb.append(indent("private double bpm = 120.0;", 2)).append("\n");
      sb.append(indent("private long n = 0;", 2)).append("\n");
      sb.append(indent("BPM() { recalc(); }", 2)).append("\n");
      sb.append(
              indent(
                  "double tempo(double value) { this.bpm = value <= 0 ? 120.0 : value; recalc(); return this.bpm; }",
                  2))
          .append("\n");
      sb.append(indent("double tempo(int value) { return tempo((double) value); }", 2))
          .append("\n");
      sb.append(indent("double tempo() { return bpm; }", 2)).append("\n");
      sb.append(
              indent(
                  "ChuckDuration quarterNote() { return second().times((long) (60.0 / bpm)); }", 2))
          .append("\n");
      sb.append(indent("ChuckDuration eighthNote() { return quarterNote().div(2); }", 2))
          .append("\n");
      sb.append(indent("ChuckDuration sixteenthNote() { return quarterNote().div(4); }", 2))
          .append("\n");
      sb.append(indent("long num() { return n; }", 2)).append("\n");
      sb.append(indent("long n() { return n; }", 2)).append("\n");
      sb.append(indent("void recalc() { if (bpm <= 0) bpm = 120.0; }", 2)).append("\n");
      sb.append(indent("}", 1)).append("\n");
      sb.append("\n");
    }
    sb.append(indent("private static final class AI {", 1)).append("\n");
    sb.append(indent("static final int MLP = 1;", 2)).append("\n");
    sb.append(indent("static final int KNN = 2;", 2)).append("\n");
    sb.append(indent("static final int Tracking = 3;", 2)).append("\n");
    sb.append(indent("static int Regression() { return 0; }", 2)).append("\n");
    sb.append(indent("static int Classification() { return 1; }", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class TrackingProxy {", 1)).append("\n");
    sb.append(indent("public double the_freq;", 2)).append("\n");
    sb.append(indent("public double the_gain;", 2)).append("\n");
    sb.append(indent("public Object the_event;", 2)).append("\n");
    sb.append(indent("double the_freq() { return the_freq; }", 2)).append("\n");
    sb.append(indent("double the_gain() { return the_gain; }", 2)).append("\n");
    sb.append(indent("Object the_event() { return the_event; }", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append(indent("private static final TrackingProxy Tracking = new TrackingProxy();", 1))
        .append("\n");
    sb.append("\n");
    if (!userClasses.contains("Smacking")) {
      sb.append(indent("private static final class Smacking {", 1)).append("\n");
      sb.append(indent("static Object the_event;", 2)).append("\n");
      sb.append(indent("static Object the_event() { return the_event; }", 2)).append("\n");
      sb.append(indent("static void reset() {}", 2)).append("\n");
      sb.append(indent("static ChuckDuration duration() { return new ChuckDuration(0); }", 2))
          .append("\n");
      sb.append(indent("}", 1)).append("\n");
      sb.append("\n");
    }
    sb.append(indent("private static final class MouseCursorProxy {", 1)).append("\n");
    sb.append(indent("String xy() { return \"\"; }", 2)).append("\n");
    sb.append(indent("String scaled() { return \"\"; }", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append(
            indent(
                "private static final MouseCursorProxy MouseCursor = new MouseCursorProxy();", 1))
        .append("\n");
    sb.append("\n");
    sb.append(
            indent(
                "private static final org.chuck.core.ChuckMath ChuckMath = new org.chuck.core.ChuckMath();",
                1))
        .append("\n");
    sb.append("\n");
    sb.append(indent("private static final class Complex {", 1)).append("\n");
    sb.append(indent("public double re, im;", 2)).append("\n");
    sb.append(indent("Complex() { this(0, 0); }", 2)).append("\n");
    sb.append(indent("Complex(double re, double im) { this.re = re; this.im = im; }", 2))
        .append("\n");
    sb.append(
            indent(
                "Complex plus(Object o) { Complex c = from(o); return new Complex(re + c.re, im + c.im); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "Complex minus(Object o) { Complex c = from(o); return new Complex(re - c.re, im - c.im); }",
                2))
        .append("\n");
    sb.append(indent("Complex times(Object o) {", 2)).append("\n");
    sb.append(
            indent(
                "if (o instanceof java.lang.Number n) return new Complex(re * n.doubleValue(), im * n.doubleValue());",
                3))
        .append("\n");
    sb.append(indent("Complex c = from(o);", 3)).append("\n");
    sb.append(indent("return new Complex(re * c.re - im * c.im, re * c.im + im * c.re);", 3))
        .append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("Complex div(Object o) {", 2)).append("\n");
    sb.append(
            indent(
                "if (o instanceof java.lang.Number n) { double d=n.doubleValue(); return new Complex(re/d, im/d); }",
                3))
        .append("\n");
    sb.append(indent("Complex c = from(o); double d = c.re*c.re + c.im*c.im;", 3)).append("\n");
    sb.append(indent("if (d == 0) return new Complex();", 3)).append("\n");
    sb.append(indent("return new Complex((re*c.re + im*c.im)/d, (im*c.re - re*c.im)/d);", 3))
        .append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("double magnitude() { return Math.hypot(re, im); }", 2)).append("\n");
    sb.append(indent("double phase() { return Math.atan2(im, re); }", 2)).append("\n");
    sb.append(indent("static Complex fromPolar(Object o) { return Polar.from(o).toComplex(); }", 2))
        .append("\n");
    sb.append(indent("static Complex from(Object o) {", 2)).append("\n");
    sb.append(indent("if (o instanceof Complex c) return c;", 3)).append("\n");
    sb.append(indent("if (o instanceof Polar p) return p.toComplex();", 3)).append("\n");
    sb.append(
            indent(
                "if (o instanceof java.lang.Number n) return new Complex(n.doubleValue(), 0);", 3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof double[] a) return new Complex(a.length>0?a[0]:0, a.length>1?a[1]:0);",
                3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof ChuckArray a) return new Complex(a.size()>0?a.getFloat(0):0, a.size()>1?a.getFloat(1):0);",
                3))
        .append("\n");
    sb.append(indent("return new Complex();", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "@Override public String toString() { return \"(\" + re + \", \" + im + \")\"; }",
                2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class Polar {", 1)).append("\n");
    sb.append(indent("public double mag, phase;", 2)).append("\n");
    sb.append(indent("Polar() { this(0, 0); }", 2)).append("\n");
    sb.append(indent("Polar(double mag, double phase) { this.mag = mag; this.phase = phase; }", 2))
        .append("\n");
    sb.append(
            indent(
                "Polar plus(Object o) { return fromComplex(toComplex().plus(from(o).toComplex())); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "Polar minus(Object o) { return fromComplex(toComplex().minus(from(o).toComplex())); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "Polar times(Object o) { return fromComplex(toComplex().times(from(o).toComplex())); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "Polar div(Object o) { return fromComplex(toComplex().div(from(o).toComplex())); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "Complex toComplex() { return new Complex(mag * Math.cos(phase), mag * Math.sin(phase)); }",
                2))
        .append("\n");
    sb.append(indent("double mag() { return mag; }", 2)).append("\n");
    sb.append(indent("double magnitude() { return mag; }", 2)).append("\n");
    sb.append(indent("double phase() { return phase; }", 2)).append("\n");
    sb.append(indent("static Polar fromComplex(Object o) {", 2)).append("\n");
    sb.append(indent("Complex c = Complex.from(o);", 3)).append("\n");
    sb.append(indent("return new Polar(c.magnitude(), c.phase());", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("static Polar from(Object o) {", 2)).append("\n");
    sb.append(indent("if (o instanceof Polar p) return p;", 3)).append("\n");
    sb.append(indent("if (o instanceof Complex c) return fromComplex(c);", 3)).append("\n");
    sb.append(
            indent("if (o instanceof java.lang.Number n) return new Polar(n.doubleValue(), 0);", 3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof double[] a) return new Polar(a.length>0?a[0]:0, a.length>1?a[1]:0);",
                3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof ChuckArray a) return new Polar(a.size()>0?a.getFloat(0):0, a.size()>1?a.getFloat(1):0);",
                3))
        .append("\n");
    sb.append(indent("return new Polar();", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "@Override public String toString() { return \"(\" + mag + \", \" + phase + \")\"; }",
                2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static vec2 _vec2(double x, double y) { return new vec2(x, y); }", 1))
        .append("\n");
    sb.append(
            indent(
                "private static vec3 _vec3(double x, double y, double z) { return new vec3(x, y, z); }",
                1))
        .append("\n");
    sb.append(
            indent(
                "private static vec4 _vec4(double x, double y, double z, double w) { return new vec4(x, y, z, w); }",
                1))
        .append("\n");
    sb.append(indent("private static final class vec2 {", 1)).append("\n");
    sb.append(indent("public double x, y, u, v, s, t;", 2)).append("\n");
    sb.append(indent("vec2() { this(0, 0); }", 2)).append("\n");
    sb.append(indent("vec2(double x, double y) { set(x, y); }", 2)).append("\n");
    sb.append(
            indent(
                "void set(double x, double y) { this.x=x; this.y=y; this.u=x; this.v=y; this.s=x; this.t=y; }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec2 __op__plus(Object o) { vec2 r = from(o); return new vec2(x + r.x, y + r.y); }",
                2))
        .append("\n");
    sb.append(indent("vec2 __op__times(Object o) {", 2)).append("\n");
    sb.append(
            indent(
                "if (o instanceof java.lang.Number n) return new vec2(x * n.doubleValue(), y * n.doubleValue());",
                3))
        .append("\n");
    sb.append(indent("vec2 r = from(o); return new vec2(x * r.x, y * r.y);", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "vec2 __op__div(Object o) { double d=(o instanceof java.lang.Number n)?n.doubleValue():1.0; return new vec2(x/d, y/d); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec2 __op__minus(Object o) { vec2 r = from(o); return new vec2(x - r.x, y - r.y); }",
                2))
        .append("\n");
    sb.append(indent("double magnitude() { return Math.hypot(x, y); }", 2)).append("\n");
    sb.append(indent("void normalize() { double m=magnitude(); if (m!=0) set(x/m, y/m); }", 2))
        .append("\n");
    sb.append(indent("static vec2 from(Object o) {", 2)).append("\n");
    sb.append(indent("if (o instanceof vec2 v) return v;", 3)).append("\n");
    sb.append(indent("if (o instanceof vec3 v) return new vec2(v.x, v.y);", 3)).append("\n");
    sb.append(indent("if (o instanceof vec4 v) return new vec2(v.x, v.y);", 3)).append("\n");
    sb.append(
            indent("if (o instanceof double[] a && a.length >= 2) return new vec2(a[0], a[1]);", 3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof ChuckArray a && a.size() >= 2) return new vec2(a.getFloat(0), a.getFloat(1));",
                3))
        .append("\n");
    sb.append(indent("return new vec2();", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("@Override public String toString() { return \"[\"+x+\", \"+y+\"]\"; }", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class vec3 {", 1)).append("\n");
    sb.append(indent("public double x, y, z;", 2)).append("\n");
    sb.append(indent("vec3() { this(0, 0, 0); }", 2)).append("\n");
    sb.append(indent("vec3(double x, double y, double z) { set(x, y, z); }", 2)).append("\n");
    sb.append(indent("void set(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }", 2))
        .append("\n");
    sb.append(
            indent(
                "vec3 __op__plus(Object o) { vec3 r = from(o); return new vec3(x + r.x, y + r.y, z + r.z); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec3 __op__minus(Object o) { vec3 r = from(o); return new vec3(x - r.x, y - r.y, z - r.z); }",
                2))
        .append("\n");
    sb.append(indent("vec3 __op__times(Object o) {", 2)).append("\n");
    sb.append(
            indent(
                "if (o instanceof java.lang.Number n) return new vec3(x*n.doubleValue(), y*n.doubleValue(), z*n.doubleValue());",
                3))
        .append("\n");
    sb.append(indent("vec3 r = from(o);", 3)).append("\n");
    sb.append(indent("return new vec3(y*r.z - z*r.y, z*r.x - x*r.z, x*r.y - y*r.x);", 3))
        .append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "vec3 __op__div(Object o) { double d=(o instanceof java.lang.Number n)?n.doubleValue():1.0; return new vec3(x/d, y/d, z/d); }",
                2))
        .append("\n");
    sb.append(indent("double dot(Object o) { vec3 r = from(o); return x*r.x + y*r.y + z*r.z; }", 2))
        .append("\n");
    sb.append(indent("vec3 cross(Object o) { return __op__times(o); }", 2)).append("\n");
    sb.append(indent("double magnitude() { return Math.sqrt(x*x + y*y + z*z); }", 2)).append("\n");
    sb.append(indent("void normalize() { double m=magnitude(); if (m!=0) set(x/m, y/m, z/m); }", 2))
        .append("\n");
    sb.append(indent("static vec3 from(Object o) {", 2)).append("\n");
    sb.append(indent("if (o instanceof vec3 v) return v;", 3)).append("\n");
    sb.append(indent("if (o instanceof vec2 v) return new vec3(v.x, v.y, 0);", 3)).append("\n");
    sb.append(indent("if (o instanceof vec4 v) return new vec3(v.x, v.y, v.z);", 3)).append("\n");
    sb.append(
            indent(
                "if (o instanceof double[] a) return new vec3(a.length>0?a[0]:0, a.length>1?a[1]:0, a.length>2?a[2]:0);",
                3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof ChuckArray a) return new vec3(a.size()>0?a.getFloat(0):0, a.size()>1?a.getFloat(1):0, a.size()>2?a.getFloat(2):0);",
                3))
        .append("\n");
    sb.append(indent("return new vec3();", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "@Override public String toString() { return \"[\"+x+\", \"+y+\", \"+z+\"]\"; }",
                2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class vec4 {", 1)).append("\n");
    sb.append(indent("public double x, y, z, w;", 2)).append("\n");
    sb.append(indent("vec4() { this(0, 0, 0, 0); }", 2)).append("\n");
    sb.append(indent("vec4(double x, double y, double z, double w) { set(x, y, z, w); }", 2))
        .append("\n");
    sb.append(
            indent(
                "void set(double x, double y, double z, double w) { this.x=x; this.y=y; this.z=z; this.w=w; }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec4 __op__plus(Object o) { vec4 r = from(o); return new vec4(x + r.x, y + r.y, z + r.z, w + r.w); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec4 __op__minus(Object o) { vec4 r = from(o); return new vec4(x - r.x, y - r.y, z - r.z, w - r.w); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec4 __op__times(Object o) { if (o instanceof java.lang.Number n) return new vec4(x*n.doubleValue(), y*n.doubleValue(), z*n.doubleValue(), w*n.doubleValue()); vec4 r=from(o); return new vec4(x*r.x, y*r.y, z*r.z, w*r.w); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec4 __op__div(Object o) { double d=(o instanceof java.lang.Number n)?n.doubleValue():1.0; return new vec4(x/d, y/d, z/d, w/d); }",
                2))
        .append("\n");
    sb.append(
            indent(
                "double dot(Object o) { vec4 r = from(o); return x*r.x + y*r.y + z*r.z + w*r.w; }",
                2))
        .append("\n");
    sb.append(
            indent(
                "vec4 cross(Object o) { vec4 r = from(o); return new vec4(y*r.z - z*r.y, z*r.x - x*r.z, x*r.y - y*r.x, w); }",
                2))
        .append("\n");
    sb.append(indent("double magnitude() { return Math.sqrt(x*x + y*y + z*z + w*w); }", 2))
        .append("\n");
    sb.append(
            indent(
                "void normalize() { double m=magnitude(); if (m!=0) set(x/m, y/m, z/m, w/m); }", 2))
        .append("\n");
    sb.append(indent("static vec4 from(Object o) {", 2)).append("\n");
    sb.append(indent("if (o instanceof vec4 v) return v;", 3)).append("\n");
    sb.append(indent("if (o instanceof vec3 v) return new vec4(v.x, v.y, v.z, 0);", 3))
        .append("\n");
    sb.append(indent("if (o instanceof vec2 v) return new vec4(v.x, v.y, 0, 0);", 3)).append("\n");
    sb.append(
            indent(
                "if (o instanceof double[] a) return new vec4(a.length>0?a[0]:0, a.length>1?a[1]:0, a.length>2?a[2]:0, a.length>3?a[3]:0);",
                3))
        .append("\n");
    sb.append(
            indent(
                "if (o instanceof ChuckArray a) return new vec4(a.size()>0?a.getFloat(0):0, a.size()>1?a.getFloat(1):0, a.size()>2?a.getFloat(2):0, a.size()>3?a.getFloat(3):0);",
                3))
        .append("\n");
    sb.append(indent("return new vec4();", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(
            indent(
                "@Override public String toString() { return \"[\"+x+\", \"+y+\", \"+z+\", \"+w+\"]\"; }",
                2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");

    // 3. Emit Methods
    for (ChuckAST.FuncDefStmt method : methods) {
      String s = visitStmt(method);
      if (s != null && !s.isEmpty()) {
        sb.append(indent(s, 1)).append("\n");
      }
    }
    sb.append("\n");

    // 4. Emit Classes
    for (ChuckAST.ClassDefStmt cls : classes) {
      String s = visitStmt(cls);
      if (s != null && !s.isEmpty()) {
        sb.append(indent(s, 1)).append("\n");
      }
    }
    sb.append("\n");

    // 5. Emit Shred method
    sb.append("    @Override\n");
    sb.append("    public void shred() {\n");

    // 5a. Auto-initialize UGen/Object arrays
    if (!arraysToInit.isEmpty()) {
      for (ChuckAST.DeclStmt ds : arraysToInit) {
        String type = mapType(ds.type());
        String baseType = type;
        while (baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);

        String safeArrayName = safeName(ds.name());
        String loopVar = "i_" + safeArrayName;
        if ("ChuckEvent".equals(baseType)) {
          sb.append(
              indent(
                  "for (int "
                      + loopVar
                      + " = 0; "
                      + loopVar
                      + " < "
                      + safeArrayName
                      + ".length; "
                      + loopVar
                      + "++) {\n",
                  2));
        } else {
          sb.append(
              indent(
                  "for (int "
                      + loopVar
                      + " = 0; "
                      + loopVar
                      + " < "
                      + safeArrayName
                      + ".size(); "
                      + loopVar
                      + "++) {\n",
                  2));
        }
        String init;
        if ("Complex".equals(baseType)) {
          init = "new Complex(0f, 0f)";
        } else if ("Polar".equals(baseType)) {
          init = "new Polar(0f, 0f)";
        } else if ("ChuckEvent".equals(baseType)) {
          init = "new ChuckEvent()";
        } else {
          init = "_new(" + baseType + ".class)";
        }
        if ("ChuckEvent".equals(baseType)) {
          sb.append(indent(safeArrayName + "[" + loopVar + "] = " + init + ";\n", 3));
        } else {
          sb.append(indent(safeArrayName + ".setObject(" + loopVar + ", " + init + ");\n", 3));
        }
        sb.append(indent("}\n", 2));
      }
      sb.append("\n");
    }

    for (ChuckAST.Stmt stmt : shredBody) {
      String s = visitStmt(stmt);
      if (s != null && !s.isEmpty()) {
        sb.append(indent(s, 2)).append("\n");
      }
    }

    sb.append("    }\n");
    sb.append("}\n");
    return postProcessGeneratedCode(sb.toString());
  }

  private void collectFields(List<ChuckAST.Stmt> stmts) {
    if (stmts == null) return;
    for (ChuckAST.Stmt s : stmts) {
      if (s instanceof ChuckAST.DeclStmt ds) {
        addField(ds);
      } else if (s instanceof ChuckAST.ExpStmt es) {
        collectFromExp(es.exp());
      } else if (s instanceof ChuckAST.IfStmt is) {
        collectFields(List.of(is.thenBranch()));
        if (is.elseBranch() != null) collectFields(List.of(is.elseBranch()));
      } else if (s instanceof ChuckAST.WhileStmt ws) {
        collectFields(List.of(ws.body()));
      } else if (s instanceof ChuckAST.ForStmt fs) {
        collectFields(List.of(fs.body()));
      } else if (s instanceof ChuckAST.BlockStmt bs) {
        collectFields(bs.statements());
      }
      // Explicitly skip ClassDefStmt to avoid global promotion of its internal fields
    }
  }

  private void collectFromExp(ChuckAST.Exp exp) {
    if (exp instanceof ChuckAST.DeclExp de) {
      addField(
          new ChuckAST.DeclStmt(
              de.type(),
              de.name(),
              de.arraySizes(),
              de.callArgs(),
              de.isReference(),
              de.isStatic(),
              de.isGlobal(),
              de.isConst(),
              de.access(),
              de.doc(),
              de.line(),
              de.column()));
    } else if (exp instanceof ChuckAST.BinaryExp be) {
      collectFromExp(be.lhs());
      collectFromExp(be.rhs());
    }
  }

  private void addField(ChuckAST.DeclStmt ds) {
    if (fields.stream().anyMatch(f -> f.name().equals(ds.name()))) return;
    fields.add(ds);
    String rawType = mapType(ds.type());
    String type = rawType;
    if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
      if (type.startsWith("ChuckEvent")) {
        type = "ChuckEvent[]";
      } else {
        type = "ChuckArray";
      }
    }
    String safe = safeName(ds.name());
    varTypes.put(safe, type);
    if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
      arrayElementTypes.put(safe, normalizeArrayElementType(rawType));
      arrayDepths.put(safe, ds.arraySizes().size());
    }
    if (ds.isGlobal()) globals.add(safe);

    // Array tracking for auto-init
    if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
      String baseType = type;
      while (baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);
      if (!isPrimitive(baseType)) {
        arraysToInit.add(ds);
      }
    }
  }

  private String formatComment(String doc, int indentLevels) {
    if (doc == null || doc.trim().isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    String[] lines = doc.split("\n");
    for (String line : lines) {
      sb.append("// ").append(line).append("\n");
    }
    return sb.toString();
  }

  private boolean isFunction(String name) {
    return userFunctions.contains(name)
        || name.equals("zero")
        || name.equals("pole")
        || name.equals("radius")
        || name.equals("width")
        || name.equals("sync")
        || name.equals("delay")
        || name.equals("max")
        || name.equals("next")
        || name.equals("pluck")
        || name.equals("freq")
        || name.equals("gain")
        || name.equals("Q");
  }

  private boolean isInterfaceMode = false;

  private String visitStmt(ChuckAST.Stmt stmt) {
    String comment = formatComment(stmt.doc(), 0);
    String code = visitStmtInternal(stmt);
    if (comment.isEmpty()) return code;
    return comment + code;
  }

  private String visitBoolExp(ChuckAST.Exp exp) {
    if (exp instanceof ChuckAST.BinaryExp be
        && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)
        && be.rhs() instanceof ChuckAST.IdExp id
        && "now".equals(id.name())) {
      return "_advanceAndTrue(" + visitExp(be.lhs()) + ")";
    }
    String type = typeOf(exp);
    String code = visitExp(exp);
    if ("long".equals(type)) return "((" + code + ") != 0)";
    if ("double".equals(type)) return "((" + code + ") != 0.0)";
    if ("String".equals(type)) return "_truthy(" + code + ")";
    if ("ChuckDuration".equals(type)) return "((" + durationScalar(code) + ") != 0.0)";
    if (type != null && type.endsWith("[]"))
      return "(" + code + " != null && " + code + ".length > 0)";
    if ("ChuckArray".equals(type)) return "(" + code + " != null && " + code + ".size() > 0)";
    if (code != null && code.endsWith(".args()"))
      return "(" + code + " != null && " + code + ".length > 0)";
    if ("Object".equals(type)) return "_truthy(" + code + ")";
    return code;
  }

  private String visitStmtInternal(ChuckAST.Stmt stmt) {
    if (stmt instanceof ChuckAST.ExpStmt es) {
      if (es.exp() instanceof ChuckAST.BinaryExp be
          && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)
          && containsNestedChuck(be)) {
        return emitChuckChain(be);
      }
      String exprCode = visitExp(es.exp());
      exprCode =
          exprCode.replaceAll(
              "CKDoc\\.describe\\(([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\(\\)\\)",
              "CKDoc.describe(_call($1, \"$2\"))");
      if (es.exp() instanceof ChuckAST.BinaryExp be) {
        if (be.op() == ChuckAST.Operator.PLUS
            || be.op() == ChuckAST.Operator.MINUS
            || be.op() == ChuckAST.Operator.TIMES
            || be.op() == ChuckAST.Operator.DIVIDE
            || be.op() == ChuckAST.Operator.PERCENT
            || be.op() == ChuckAST.Operator.EQ
            || be.op() == ChuckAST.Operator.NEQ
            || be.op() == ChuckAST.Operator.GT
            || be.op() == ChuckAST.Operator.GE
            || be.op() == ChuckAST.Operator.LT
            || be.op() == ChuckAST.Operator.LE
            || be.op() == ChuckAST.Operator.AND
            || be.op() == ChuckAST.Operator.OR
            || be.op() == ChuckAST.Operator.S_AND
            || be.op() == ChuckAST.Operator.S_OR) {
          return "_stmt(" + exprCode + ");";
        }
      }
      return exprCode + ";";
    } else if (stmt instanceof ChuckAST.IfStmt is) {
      StringBuilder sb = new StringBuilder();
      sb.append("if (")
          .append(visitBoolExp(is.condition()))
          .append(") ")
          .append(ensureBraces(visitStmt(is.thenBranch())));
      if (is.elseBranch() != null) {
        sb.append(" else ").append(ensureBraces(visitStmt(is.elseBranch())));
      }
      return sb.toString();
    } else if (stmt instanceof ChuckAST.WhileStmt ws) {
      String cond = visitBoolExp(ws.condition());
      if ("((1) != 0)".equals(cond) || "(1L != 0L)".equals(cond) || "(true)".equals(cond)) {
        cond = "_truthy(1)";
      }
      return "while (" + cond + ") " + ensureBraces(visitStmt(ws.body()));
    } else if (stmt instanceof ChuckAST.UntilStmt us) {
      return "while (!("
          + visitBoolExp(us.condition())
          + ")) "
          + ensureBraces(visitStmt(us.body()));
    } else if (stmt instanceof ChuckAST.ForStmt fs) {
      boolean oldFieldMode = isFieldMode;
      isFieldMode = false; // Disable field promotion during loop init
      String init = fs.init() != null ? visitStmt(fs.init()) : ";";
      isFieldMode = oldFieldMode;
      if (init != null && init.matches("\\s*[A-Za-z_][A-Za-z0-9_]*\\s*;\\s*")) init = ";";
      if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
      if (init != null && init.matches("\\s*[A-Za-z_][A-Za-z0-9_]*\\s*=.*")) {
        String loopVar = init.trim().replaceAll("^([A-Za-z_][A-Za-z0-9_]*).*", "$1");
        if (!init.trim().matches("^(long|int|double|float|boolean|String)\\s+.*")) {
          String inferredType = inferForLoopVarType(init, null, null);
          init = inferredType + " " + init.trim();
          varTypes.put(loopVar, inferredType);
        }
      }
      String cond = "true";
      if (fs.condition() instanceof ChuckAST.ExpStmt es) {
        cond = visitBoolExp(es.exp());
      } else if (fs.condition() != null) {
        cond = visitStmt(fs.condition());
        if (cond.endsWith(";")) cond = cond.substring(0, cond.length() - 1);
      }
      String update = fs.update() != null ? visitExp(fs.update()) : "";
      if ((init == null || init.isBlank())
          && update.matches("\\s*[\\(]*[A-Za-z_][A-Za-z0-9_]*.*")) {
        String loopVar = update.trim().replaceAll("^[\\(\\s]*([A-Za-z_][A-Za-z0-9_]*).*", "$1");
        init = "long " + loopVar + " = 0";
      }
      String body = visitStmt(fs.body());
      String declaredLoopVar = extractForInitDeclaredVar(init);
      if (declaredLoopVar != null) {
        String renamedLoopVar = declaredLoopVar + "__" + (sporkCaptureCounter++);
        init = init.replaceAll("\\b\\Q" + declaredLoopVar + "\\E\\b", renamedLoopVar);
        cond = cond.replaceAll("\\b\\Q" + declaredLoopVar + "\\E\\b", renamedLoopVar);
        update = update.replaceAll("\\b\\Q" + declaredLoopVar + "\\E\\b", renamedLoopVar);
        body = body.replaceAll("\\b\\Q" + declaredLoopVar + "\\E\\b", renamedLoopVar);
        declaredLoopVar = renamedLoopVar;
      }
      if (declaredLoopVar != null) {
        activeLoopVars.add(declaredLoopVar);
      }
      String out = "for (" + init + "; " + cond + "; " + update + ") " + ensureBraces(body);
      if (declaredLoopVar != null && !activeLoopVars.isEmpty()) {
        activeLoopVars.remove(activeLoopVars.size() - 1);
      }
      return out;
    } else if (stmt instanceof ChuckAST.DeclStmt ds) {
      String rawType = mapType(ds.type());
      String type = rawType;
      String safe = safeName(ds.name());
      if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
        if (type.startsWith("ChuckEvent")) {
          type = "ChuckEvent[]";
        } else {
          type = "ChuckArray";
        }
      }
      varTypes.put(safe, type);
      if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
        arrayElementTypes.put(safe, normalizeArrayElementType(rawType));
        arrayDepths.put(safe, ds.arraySizes().size());
      }
      if (isFieldMode && currentClassName == null) {
        return null;
      }

      boolean isAlreadyField = fields.stream().anyMatch(f -> f.name().equals(ds.name()));
      boolean duplicateFunctionLocal =
          currentFunctionLocals != null && currentFunctionLocals.contains(safe);
      if (!duplicateFunctionLocal && !isAlreadyField && currentFunctionLocals != null) {
        currentFunctionLocals.add(safe);
      }

      if (ds.isGlobal()) {
        String getter =
            switch (type) {
              case "long" -> "Machine.getGlobalInt(\"" + ds.name() + "\")";
              case "double" -> "Machine.getGlobalFloat(\"" + ds.name() + "\")";
              default -> "Machine.getGlobalObject(\"" + ds.name() + "\")";
            };
        return type + " " + safe + " = " + coerceToTypeExpr(getter, "Object", type) + ";";
      }

      // Handle array declaration
      if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
        if (isAlreadyField) {
          // It is already initialized via arraysToInit block at start of shred()
          return "";
        } else {
          String size = visitExp(ds.arraySizes().get(0));
          if (size.equals("-1") || size.startsWith("(-1")) size = "0";
          String declType = "ChuckArray";
          String baseType = type;
          while (baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);
          varTypes.put(safe, "ChuckArray");
          return declType
              + " "
              + safe
              + " = new ChuckArray(\""
              + baseType
              + "\", (int)("
              + size
              + "));";
        }
      }

      StringBuilder sb = new StringBuilder();
      // If it's a field, only emit the initialization part
      if (!isAlreadyField && !duplicateFunctionLocal) {
        sb.append(type).append(" ").append(safe);
      } else {
        // It's a field or duplicate local declaration, emit assignment form only
        sb.append(safe);
      }

      // Handle connection in declaration: SinOsc s => dac;
      if (ds.callArgs() instanceof ChuckAST.BinaryExp be
          && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)) {
        return visitExp(ds.callArgs()) + ";";
      }

      if (isAlreadyField) return "";

      if (isPrimitive(type)) {
        if (type.equals("ChuckDuration")) {
          sb.append(" = samp(0)");
        } else {
          sb.append(" = (").append(type).append(")(0)");
        }
      } else if (isUGen(type) || userClasses.contains(type)) {
        if ("Complex".equals(type) || "Polar".equals(type)) {
          sb.append(" = new ").append(type).append("(0f, 0f)");
        } else {
          sb.append(" = _new(").append(type).append(".class)");
        }
      } else {
        sb.append(" = null");
      }
      String res = sb.toString();
      if (!isFieldMode
          && !res.isEmpty()
          && !res.endsWith(";")
          && !res.endsWith("\n")
          && !res.endsWith("}")) {
        res += ";";
      }
      return res;
    } else if (stmt instanceof ChuckAST.DoStmt ds) {
      String cond = visitExp(ds.condition());
      if (ds.isUntil()) cond = "!(" + cond + ")";
      return "do " + ensureBraces(visitStmt(ds.body())) + " while (" + cond + ");";
    } else if (stmt instanceof ChuckAST.RepeatStmt rs) {
      String count = visitExp(rs.count());
      String loopVar = "i";
      if ((currentFunctionLocals != null && currentFunctionLocals.contains("i"))
          || activeLoopVars.contains("i")) {
        loopVar = "i__" + (sporkCaptureCounter++);
      }
      return "for (int "
          + loopVar
          + " = 0; "
          + loopVar
          + " < "
          + count
          + "; "
          + loopVar
          + "++) "
          + ensureBraces(visitStmt(rs.body()));
    } else if (stmt instanceof ChuckAST.ForEachStmt fes) {
      String iterType = mapType(fes.iterType());
      if (iterType.endsWith("[]") && !iterType.startsWith("ChuckEvent")) iterType = "ChuckArray";
      String iterName = safeName(fes.iterName());
      String prevType = varTypes.put(iterName, iterType);
      String body = visitStmt(fes.body());
      if (prevType != null) {
        varTypes.put(iterName, prevType);
      } else {
        varTypes.remove(iterName);
      }
      String collName = "__fe_coll_" + iterName;
      String idxName = "__fe_i_" + iterName;
      String elemExpr = "_toChuckArray(" + collName + ").getObject(" + idxName + ")";
      String iterInit =
          iterType + " " + iterName + " = " + coerceToTypeExpr(elemExpr, "Object", iterType) + ";";
      return "{\n"
          + indent("Object " + collName + " = " + visitExp(fes.collection()) + ";", 1)
          + "\n"
          + indent(
              "for (int "
                  + idxName
                  + " = 0; "
                  + idxName
                  + " < (int)(_sizeOf("
                  + collName
                  + ")); "
                  + idxName
                  + "++) {",
              1)
          + "\n"
          + indent(iterInit, 2)
          + "\n"
          + indent(body, 2)
          + "\n"
          + indent("}", 1)
          + "\n"
          + "}";
    } else if (stmt instanceof ChuckAST.WhileStmt ws) {
      String cond = visitExp(ws.condition());
      if (cond.equals("1")) cond = "true";
      return "while (" + cond + ") " + ensureBraces(visitStmt(ws.body()));
    } else if (stmt instanceof ChuckAST.UntilStmt us) {
      return "while (!(" + visitExp(us.condition()) + ")) " + ensureBraces(visitStmt(us.body()));
    } else if (stmt instanceof ChuckAST.SwitchStmt ss) {
      StringBuilder sb = new StringBuilder();
      sb.append("switch (").append(visitExp(ss.condition())).append(") {\n");
      for (ChuckAST.CaseStmt cs : ss.cases()) {
        sb.append(indent(visitStmt(cs), 1)).append("\n");
      }
      sb.append("}");
      return sb.toString();
    } else if (stmt instanceof ChuckAST.CaseStmt cs) {
      StringBuilder sb = new StringBuilder();
      if (cs.isDefault()) {
        sb.append("default:\n");
      } else {
        sb.append("case ").append(visitExp(cs.match())).append(":\n");
      }
      for (ChuckAST.Stmt s : cs.body()) {
        String res = visitStmt(s);
        if (res != null) {
          sb.append(indent(res, 1)).append("\n");
        }
      }
      return sb.toString();
    } else if (stmt instanceof ChuckAST.BlockStmt bs) {
      StringBuilder b = new StringBuilder();
      if (bs.isScoped()) b.append("{\n");
      for (ChuckAST.Stmt s : bs.statements()) {
        if (s instanceof ChuckAST.DeclStmt ds) {
          varTypes.put(ds.name(), mapType(ds.type()));
        }
        String res = visitStmt(s);
        if (res != null) {
          if (bs.isScoped()) b.append(indent(res, 1)).append("\n");
          else b.append(res).append("\n");
        }
      }
      if (bs.isScoped()) b.append("}");
      return b.toString();
    } else if (stmt instanceof ChuckAST.PrintStmt ps) {
      String printed =
          ps.expressions().stream().map(this::visitExp).collect(Collectors.joining(" + \" \" + "));
      printed =
          printed.replaceAll(
              "CKDoc\\.describe\\(([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\(\\)\\)",
              "CKDoc.describe(_call($1, \"$2\"))");
      return "org.chuck.core.ChuckDSL.print(" + printed + ");";
    } else if (stmt instanceof ChuckAST.ImportStmt is) {
      return "// import " + is.path() + ";";
    } else if (stmt instanceof ChuckAST.FuncDefStmt fds) {
      Map<String, String> savedVarTypes = new HashMap<>(varTypes);
      String savedReturnType = currentReturnType;
      Set<String> savedFunctionLocals = currentFunctionLocals;
      currentFunctionLocals = new HashSet<>();
      StringBuilder sb = new StringBuilder();
      sb.append(mapAccess(fds.access())).append(" ");
      if (fds.isStatic()) sb.append("static ");
      String name = normalizeFunctionName(fds.name());
      registerFunctionSignature(currentClassName, name, fds.argTypes());
      String argSignature =
          fds.argTypes().stream().map(this::mapType).collect(Collectors.joining(","));
      String signatureKey =
          (currentClassName == null ? "__root__" : currentClassName)
              + "|"
              + name
              + "|"
              + argSignature;
      int duplicateCount = methodNameCounts.getOrDefault(signatureKey, 0);
      methodNameCounts.put(signatureKey, duplicateCount + 1);
      if (duplicateCount > 0) {
        name = name + "__" + duplicateCount;
      }
      String retType = mapType(fds.returnType());
      if (retType.endsWith("[]") && !retType.startsWith("ChuckEvent")) {
        retType = "ChuckArray";
      }
      sb.append(retType).append(" ").append(name).append("(");
      for (int i = 0; i < fds.argNames().size(); i++) {
        if (i > 0) sb.append(", ");
        String rawArgType = mapType(fds.argTypes().get(i));
        String argType = rawArgType;
        if (argType.endsWith("[]") && !argType.startsWith("ChuckEvent")) {
          argType = "ChuckArray";
          String argSafe = safeName(fds.argNames().get(i));
          arrayElementTypes.put(argSafe, normalizeArrayElementType(rawArgType));
          int depth = 0;
          String t = rawArgType;
          while (t.endsWith("[]")) {
            depth++;
            t = t.substring(0, t.length() - 2);
          }
          arrayDepths.put(argSafe, Math.max(1, depth));
        }
        varTypes.put(fds.argNames().get(i), argType);
        currentFunctionLocals.add(safeName(fds.argNames().get(i)));
        sb.append(argType).append(" ").append(fds.argNames().get(i));
      }
      sb.append(")");
      if (isInterfaceMode) {
        sb.append(";");
      } else {
        currentReturnType = retType;
        String bodyCode = visitStmt(fds.body());
        bodyCode = ensureFunctionFallbackReturn(bodyCode, retType);
        sb.append(" ").append(bodyCode);
      }
      currentReturnType = savedReturnType;
      varTypes.clear();
      varTypes.putAll(savedVarTypes);
      currentFunctionLocals = savedFunctionLocals;
      return sb.toString();
    } else if (stmt instanceof ChuckAST.ClassDefStmt cds) {
      String oldClassName = currentClassName;
      currentClassName = safeName(cds.name());

      StringBuilder sb = new StringBuilder();
      sb.append(mapAccess(cds.access())).append(" ");
      if (cds.isAbstract()) sb.append("abstract ");
      if (cds.isInterface()) {
        sb.append("interface ");
      } else {
        sb.append("class "); // Non-static so it can access Shred fields
      }
      sb.append(currentClassName);
      if (cds.parentName() != null) {
        sb.append(cds.isInterface() ? " extends " : " extends ").append(mapType(cds.parentName()));
      }
      sb.append(" {\n");

      boolean oldInterfaceMode = isInterfaceMode;
      isInterfaceMode = cds.isInterface();

      if (!isInterfaceMode) {
        // Perform local field collection for this class
        List<ChuckAST.DeclStmt> savedFields = new ArrayList<>(fields);
        Set<String> savedFunctions = new HashSet<>(userFunctions);
        int savedArrayInitCount = arraysToInit.size();
        fields.clear();
        collectFields(cds.body());
        if (arraysToInit.size() > savedArrayInitCount) {
          arraysToInit.subList(savedArrayInitCount, arraysToInit.size()).clear();
        }

        // Also collect local functions
        for (ChuckAST.Stmt s : cds.body()) {
          if (s instanceof ChuckAST.FuncDefStmt fds) {
            userFunctions.add(normalizeFunctionName(fds.name()));
          }
        }

        // Emit class fields
        for (ChuckAST.DeclStmt field : fields) {
          String type = mapType(field.type());
          String declType = type;
          String init = "";
          if (field.arraySizes() != null && !field.arraySizes().isEmpty()) {
            String baseType = type;
            while (baseType.endsWith("[]")) {
              baseType = baseType.substring(0, baseType.length() - 2);
            }
            String size = visitExp(field.arraySizes().get(0));
            if (size.equals("-1") || size.startsWith("(-1")) size = "0";
            if (type.startsWith("ChuckEvent")) {
              declType = "ChuckEvent[]";
              init = " = new ChuckEvent[" + size + "]" + "[]".repeat(field.arraySizes().size() - 1);
            } else {
              declType = "ChuckArray";
              init = " = new ChuckArray(\"" + baseType + "\", (int)(" + size + "))";
            }
          }
          sb.append(
                  indent(
                      "public static " + declType + " " + safeName(field.name()) + init + ";", 1))
              .append("\n");
        }

        // Emit Constructor for initialization code
        List<ChuckAST.Stmt> initCode =
            cds.body().stream()
                .filter(
                    s ->
                        !(s instanceof ChuckAST.FuncDefStmt)
                            && !(s instanceof ChuckAST.ClassDefStmt))
                .toList();

        if (!initCode.isEmpty()) {
          sb.append("\n").append(indent("public " + currentClassName + "() {", 1)).append("\n");
          for (ChuckAST.Stmt s : initCode) {
            String res = visitStmt(s);
            if (res != null && !res.isEmpty()) {
              sb.append(indent(res, 2)).append("\n");
            }
          }
          sb.append(indent("}", 1)).append("\n");
        }

        sb.append("\n")
            .append(
                indent(
                    "public void help() { org.chuck.core.ChuckDSL.print(\"Type: \" + getClass().getSimpleName()); }",
                    1))
            .append("\n");

        fields.clear();
        fields.addAll(savedFields);
        userFunctions.clear();
        userFunctions.addAll(savedFunctions);
      }

      for (ChuckAST.Stmt s : cds.body()) {
        if (s instanceof ChuckAST.FuncDefStmt || s instanceof ChuckAST.ClassDefStmt) {
          String res = visitStmt(s);
          if (res != null && !res.isEmpty()) {
            sb.append(indent(res, 1)).append("\n");
          }
        }
      }

      isInterfaceMode = oldInterfaceMode;
      currentClassName = oldClassName;

      sb.append("}");
      return sb.toString();
    } else if (stmt instanceof ChuckAST.BreakStmt) {
      return "break;";
    } else if (stmt instanceof ChuckAST.ContinueStmt) {
      return "continue;";
    } else if (stmt instanceof ChuckAST.ReturnStmt rs) {
      if (rs.exp() == null) return "return;";
      String value = visitExp(rs.exp());
      if (currentReturnType != null) {
        value = coerceToTypeExpr(value, typeOf(rs.exp()), currentReturnType);
      }
      return "return " + value + ";";
    }
    return "// Unsupported statement: " + stmt.getClass().getSimpleName();
  }

  private String inferForLoopVarType(String init, String cond, String update) {
    String joined =
        (init == null ? "" : init)
            + " "
            + (cond == null ? "" : cond)
            + " "
            + (update == null ? "" : update);
    if (joined.contains("(long)(") || joined.contains("((long)")) {
      return "long";
    }
    if (joined.matches(".*\\(double\\).*")
        || joined.contains("_num(")
        || joined.matches(".*\\b\\d+\\.\\d+\\b.*")
        || joined.matches(".*\\b\\d+[eE][+-]?\\d+\\b.*")) {
      return "double";
    }
    return "long";
  }

  private String extractForInitDeclaredVar(String init) {
    if (init == null) return null;
    String s = init.trim();
    int eq = s.indexOf('=');
    if (eq < 0) return null;
    String left = s.substring(0, eq).trim();
    if (left.isEmpty()) return null;
    String[] parts = left.split("\\s+");
    if (parts.length < 2) return null;
    String candidate = parts[parts.length - 1].replaceAll("[^A-Za-z0-9_]", "");
    if (candidate.isEmpty() || !candidate.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
    return candidate;
  }

  private String emitSporkExpression(ChuckAST.Exp callExp) {
    String callCode = visitExp(callExp);
    StringBuilder prefix = new StringBuilder();
    for (Map.Entry<String, String> e : varTypes.entrySet()) {
      String var = safeName(e.getKey());
      if (var == null || var.isEmpty() || var.startsWith("__sp_")) continue;
      if (!callCode.matches("(?s).*\\b\\Q" + var + "\\E\\b.*")) continue;
      String alias = "__sp_" + (sporkCaptureCounter++) + "_" + var;
      String type = e.getValue() == null ? "Object" : e.getValue();
      if ("int".equals(type)) type = "long";
      if ("float".equals(type)) type = "double";
      if (type.endsWith("[]") && !type.startsWith("ChuckEvent")) type = "ChuckArray";
      prefix
          .append("final ")
          .append(type)
          .append(" ")
          .append(alias)
          .append(" = ")
          .append(var)
          .append("; ");
      varTypes.put(alias, type);
      callCode = callCode.replaceAll("\\b\\Q" + var + "\\E\\b", alias);
    }
    return prefix + "ChuckDSL.spork(() -> " + callCode + ")";
  }

  private boolean containsNestedChuck(ChuckAST.BinaryExp exp) {
    return (exp.lhs() instanceof ChuckAST.BinaryExp lbe
            && (lbe.op() == ChuckAST.Operator.CHUCK || lbe.op() == ChuckAST.Operator.AT_CHUCK))
        || (exp.rhs() instanceof ChuckAST.BinaryExp rbe
            && (rbe.op() == ChuckAST.Operator.CHUCK || rbe.op() == ChuckAST.Operator.AT_CHUCK));
  }

  private String emitChuckChain(ChuckAST.BinaryExp exp) {
    List<ChuckAST.Exp> operands = new ArrayList<>();
    List<ChuckAST.Operator> operators = new ArrayList<>();
    flattenChuckChain(exp, operands, operators);
    StringBuilder sb = new StringBuilder();
    Set<String> chainDeclared = new HashSet<>();
    for (int i = 0; i < operators.size(); i++) {
      ChuckAST.BinaryExp pair =
          new ChuckAST.BinaryExp(
              operands.get(i),
              operators.get(i),
              operands.get(i + 1),
              exp.doc(),
              exp.line(),
              exp.column());
      if (i > 0) sb.append("\n");
      String emitted = suppressDuplicateChainDeclarations(visitExp(pair), chainDeclared);
      sb.append(emitted).append(";");
    }
    return sb.toString();
  }

  private String suppressDuplicateChainDeclarations(String emitted, Set<String> declared) {
    if (emitted == null || emitted.isEmpty()) return emitted;
    String[] lines = emitted.split("\n", -1);
    Pattern decl =
        Pattern.compile(
            "^(\\s*)([A-Za-z_][A-Za-z0-9_\\[\\]]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$");
    for (int i = 0; i < lines.length; i++) {
      Matcher m = decl.matcher(lines[i]);
      if (!m.matches()) continue;
      String type = m.group(2);
      String name = m.group(3);
      if ("return".equals(type) || "if".equals(type) || "for".equals(type) || "while".equals(type))
        continue;
      if (declared.contains(name)) {
        lines[i] = m.group(1) + name + " = " + m.group(4);
      } else {
        declared.add(name);
      }
    }
    return String.join("\n", lines);
  }

  private void flattenChuckChain(
      ChuckAST.Exp exp, List<ChuckAST.Exp> operands, List<ChuckAST.Operator> operators) {
    if (exp instanceof ChuckAST.BinaryExp be
        && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)) {
      operands.add(be.lhs());
      operators.add(be.op());
      flattenChuckChain(be.rhs(), operands, operators);
      return;
    }
    operands.add(exp);
  }

  private String mapAccess(ChuckAST.AccessModifier access) {
    if (access == null) return "public";
    return switch (access) {
      case PUBLIC -> "public";
      case PRIVATE -> "private";
      case PROTECTED -> "protected";
    };
  }

  private String visitExp(ChuckAST.Exp exp) {
    if (exp instanceof ChuckAST.IntExp ie) {
      return String.valueOf(ie.value());
    } else if (exp instanceof ChuckAST.FloatExp fe) {
      return String.valueOf(fe.value());
    } else if (exp instanceof ChuckAST.StringExp se) {
      return "\"" + escape(se.value()) + "\"";
    } else if (exp instanceof ChuckAST.MeExp) {
      return "me()";
    } else if (exp instanceof ChuckAST.IdExp id) {
      if (id.name().endsWith(".length")) {
        String base = id.name().substring(0, id.name().length() - ".length".length());
        return "_sizeOf(" + safeName(base) + ")";
      }
      String safeId = safeName(id.name());
      if (id.name().equals("dac")) return "dac()";
      if (id.name().equals("adc")) return "adc()";
      if (id.name().equals("blackhole")) return "blackhole()";
      if (id.name().equals("now")) return "now()";
      if (id.name().equals("me")) return "me()";
      if (id.name().equals("maybe")) return "ChuckMath.maybe()";
      if (id.name().equals("Math")) return "ChuckMath";
      if (id.name().equals("W2V")) return "_new(Word2Vec.class)";
      if (id.name().equals("IO")) return "org.chuck.core.FileIO";
      if (id.name().equals("Hid")) return "org.chuck.hid.Hid";
      if (id.name().equals("Foo") && !userClasses.contains("Foo") && !varTypes.containsKey("Foo"))
        return "\"Foo\"";
      if ("result".equals(id.name())
          && !varTypes.containsKey(safeId)
          && fields.stream().noneMatch(f -> safeName(f.name()).equals(safeId))) {
        return "0";
      }
      if (id.name().equals("maxBin")) return "__maxBin_tmp";
      if (id.name().equals("pi")) return "Math.PI";
      if (id.name().equals("B9600")) return "9600";
      if (id.name().equals("READ")) return "org.chuck.core.FileIO.READ";
      if (id.name().equals("WRITE")) return "org.chuck.core.FileIO.WRITE";
      if (id.name().equals("APPEND")) return "org.chuck.core.FileIO.APPEND";
      if (id.name().equals("BINARY")) return "org.chuck.core.FileIO.BINARY";
      if (id.name().equals("ASCII")) return "org.chuck.core.FileIO.ASCII";
      if (id.name().equals("INT8")) return "1";
      if (id.name().equals("INT16")) return "2";
      if (id.name().equals("INT32")) return "4";
      if (id.name().equals("INT64")) return "8";
      if (id.name().equals("SINT16")) return "2";
      if (id.name().equals("S_INT")) return "\"int\"";
      if (id.name().equals("S_FLOAT")) return "\"float\"";
      if (id.name().equals("S_DUR")) return "\"dur\"";
      if (id.name().equals("S_TIME")) return "\"time\"";
      if (id.name().equals("S_VEC3")) return "\"vec3\"";
      if (id.name().equals("S_INT_ARRAY")) return "\"int[]\"";
      if (id.name().equals("S_STRING")) return "\"string\"";
      if (id.name().equals("S_SINOSC")) return "\"SinOsc\"";
      if (id.name().equals("Type")) return "Type";
      if (id.name().equals("CKDoc")) return "CKDoc";
      if (id.name().equals("NULL")) return "null";
      if (safeId.startsWith("_CHUCK_SPECIAL_new_")) return "_new(Object.class)";
      if (userFunctions.contains(normalizeFunctionName(id.name()))
          && !safeId.startsWith("S_")
          && !varTypes.containsKey(safeId)
          && fields.stream().noneMatch(f -> safeName(f.name()).equals(safeId))
          && !hasFieldOnType(safeName(currentClassName), safeId)) {
        return normalizeFunctionName(id.name()) + "()";
      }
      if ((id.name().equals("result") || id.name().equals("beth") || id.name().equals("kenny"))
          && !userFunctions.contains(normalizeFunctionName(id.name()))
          && !varTypes.containsKey(safeId)
          && fields.stream().noneMatch(f -> safeName(f.name()).equals(safeId))) {
        return "\"" + id.name() + "\"";
      }
      if (id.name().equals("samp")) return "samp()";
      if (id.name().equals("ms")) return "ms()";
      if (id.name().equals("second")) return "second()";
      if (id.name().equals("minute")) return "second().times(60)";
      if (id.name().equals("hour")) return "second().times(3600)";
      if (id.name().equals("day")) return "second().times(86400)";
      if (id.name().equals("week")) return "second().times(604800)";
      return safeId;
    } else if (exp instanceof ChuckAST.BinaryExp be) {
      String lhsCode = visitExp(be.lhs());
      String rhsCode = visitExp(be.rhs());
      String lhsType = typeOf(be.lhs());
      String rhsType = typeOf(be.rhs());
      if (rhsCode.startsWith("_CHUCK_SPECIAL_new_array_")) {
        rhsType = "ChuckArray";
      }

      if (be.op() == ChuckAST.Operator.PERCENT) {
        boolean lhsIsDur = isDur(be.lhs()) || "ChuckDuration".equals(lhsType);
        boolean rhsIsDur = isDur(be.rhs()) || "ChuckDuration".equals(rhsType);
        if (lhsIsDur || rhsIsDur) {
          if (lhsCode.equals("now()")) lhsCode = "samp(now())";
          if (rhsCode.equals("now()")) rhsCode = "samp(now())";
          return lhsCode + ".percent(" + rhsCode + ")";
        }
        return "(" + lhsCode + " % " + rhsCode + ")";
      }
      if (be.op() == ChuckAST.Operator.PLUS
          || be.op() == ChuckAST.Operator.MINUS
          || be.op() == ChuckAST.Operator.TIMES
          || be.op() == ChuckAST.Operator.DIVIDE
          || be.op() == ChuckAST.Operator.AND
          || be.op() == ChuckAST.Operator.OR) {
        boolean lhsIsDur = isDur(be.lhs()) || "ChuckDuration".equals(lhsType);
        boolean rhsIsDur = isDur(be.rhs()) || "ChuckDuration".equals(rhsType);
        boolean lhsDurLike = lhsIsDur || looksLikeDurationCode(lhsCode);
        boolean rhsDurLike = rhsIsDur || looksLikeDurationCode(rhsCode);
        if (lhsDurLike || rhsDurLike) {
          if (lhsCode.equals("now()")) lhsCode = "samp(now())";
          if (rhsCode.equals("now()")) rhsCode = "samp(now())";
          String method =
              switch (be.op()) {
                case PLUS -> "plus";
                case MINUS -> "minus";
                case TIMES -> "times";
                case DIVIDE -> "div";
                default -> "plus";
              };
          if (lhsDurLike) {
            String durArg = rhsDurLike ? rhsCode : "_num(" + rhsCode + ")";
            if ("Object".equals(lhsType) || lhsCode.contains("_call(")) {
              return "_toDur(_call(" + lhsCode + ", \"" + method + "\", " + durArg + "))";
            }
            return lhsCode + "." + method + "(" + durArg + ")";
          } else {
            String durArg = lhsDurLike ? lhsCode : "_num(" + lhsCode + ")";
            // For commutative ops, we can flip. For subtraction/division it might be tricky.
            // But usually durations are on the left in ChucK for arithmetic.
            if ("Object".equals(rhsType) || rhsCode.contains("_call(")) {
              return "_toDur(_call(" + rhsCode + ", \"" + method + "\", " + durArg + "))";
            }
            return rhsCode + "." + method + "(" + durArg + ")";
          }
        }

        // Deep Operator Overloading: Check if non-primitive
        String lType = typeOf(be.lhs());
        String rType = typeOf(be.rhs());
        if (lType != null
            && !isPrimitive(lType)
            && !lType.equals("String")
            && !lType.equals("Object")
            && !lType.equals("ChuckArray")) {
          String opName =
              switch (be.op()) {
                case PLUS -> "plus";
                case MINUS -> "minus";
                case TIMES -> "times";
                case DIVIDE -> "div";
                case AND -> "and";
                case OR -> "or";
                default -> null;
              };
          if (opName != null) {
            // For Complex/Polar, use cleaner names
            if (lType.equals("Complex") || lType.equals("Polar")) {
              return lhsCode + "." + opName + "(" + rhsCode + ")";
            }
            if (lType.equals("vec2") || lType.equals("vec3") || lType.equals("vec4")) {
              return lhsCode + ".__op__" + opName + "(" + rhsCode + ")";
            }
            if (userFunctions.contains("__op__" + opName)) {
              return "__op__" + opName + "(" + lhsCode + ", " + rhsCode + ")";
            }
            return lhsCode + ".__op__" + opName + "(" + rhsCode + ")";
          }
        }

        // Handle swap for symmetric ops: float * Complex -> Complex.times(float)
        if (isPrimitive(lType)
            && (rType.equals("Complex") || rType.equals("Polar"))
            && be.op() == ChuckAST.Operator.TIMES) {
          return rhsCode + ".times((float)(" + lhsCode + "))";
        }
        if (isPrimitive(lType)
            && (rType.equals("vec2") || rType.equals("vec3") || rType.equals("vec4"))
            && be.op() == ChuckAST.Operator.TIMES) {
          return rhsCode + ".__op__times(" + lhsCode + ")";
        }

        if (be.op() == ChuckAST.Operator.AND) {
          return "(" + visitBoolExp(be.lhs()) + " && " + visitBoolExp(be.rhs()) + ")";
        }
        if (be.op() == ChuckAST.Operator.OR) {
          return "(" + visitBoolExp(be.lhs()) + " || " + visitBoolExp(be.rhs()) + ")";
        }

        String op = mapOp(be.op());
        boolean lhsDynamicValue =
            lhsCode.contains("getObject")
                || lhsCode.contains("\"getObject\"")
                || lhsCode.contains("_call(")
                || lhsCode.contains(".cval(");
        boolean rhsDynamicValue =
            rhsCode.contains("getObject")
                || rhsCode.contains("\"getObject\"")
                || rhsCode.contains("_call(")
                || rhsCode.contains(".cval(");
        if ((be.op() == ChuckAST.Operator.PLUS
                || be.op() == ChuckAST.Operator.MINUS
                || be.op() == ChuckAST.Operator.TIMES
                || be.op() == ChuckAST.Operator.DIVIDE)
            && ("Object".equals(lhsType) || "Object".equals(rhsType))
            && !"String".equals(typeOf(be.lhs()))
            && !"String".equals(typeOf(be.rhs()))) {
          return "(_num(" + lhsCode + ") " + op + " _num(" + rhsCode + "))";
        }
        if ((be.op() == ChuckAST.Operator.PLUS
                || be.op() == ChuckAST.Operator.MINUS
                || be.op() == ChuckAST.Operator.TIMES
                || be.op() == ChuckAST.Operator.DIVIDE)
            && (lhsDynamicValue || rhsDynamicValue)
            && !"String".equals(typeOf(be.lhs()))
            && !"String".equals(typeOf(be.rhs()))) {
          return "(_num(" + lhsCode + ") " + op + " _num(" + rhsCode + "))";
        }
        return "(" + lhsCode + " " + op + " " + rhsCode + ")";
      }

      if (be.op() == ChuckAST.Operator.PLUS_CHUCK
          || be.op() == ChuckAST.Operator.MINUS_CHUCK
          || be.op() == ChuckAST.Operator.TIMES_CHUCK
          || be.op() == ChuckAST.Operator.DIVIDE_CHUCK) {
        String op =
            switch (be.op()) {
              case PLUS_CHUCK -> "+";
              case MINUS_CHUCK -> "-";
              case TIMES_CHUCK -> "*";
              case DIVIDE_CHUCK -> "/";
              default -> "+";
            };
        String targetType = typeOf(be.rhs());
        String sourceType = typeOf(be.lhs());
        String lhsBoolExpr = visitBoolExp(be.lhs());
        boolean lhsBooleanLike =
            "boolean".equals(sourceType)
                || lhsCode.contains("==")
                || lhsCode.contains("!=")
                || lhsCode.contains(">=")
                || lhsCode.contains("<=")
                || lhsCode.contains(" > ")
                || lhsCode.contains(" < ")
                || lhsCode.startsWith("!")
                || lhsCode.contains(".equals(");
        if (("long".equals(targetType) || "int".equals(targetType) || "double".equals(targetType))
            && lhsBooleanLike) {
          return rhsCode + " " + op + "= ((" + lhsBoolExpr + ") ? 1 : 0)";
        }
        if (targetType != null && targetType.startsWith("vec")) {
          String vecMethod =
              switch (be.op()) {
                case PLUS_CHUCK -> "__op__plus";
                case MINUS_CHUCK -> "__op__minus";
                case TIMES_CHUCK -> "__op__times";
                case DIVIDE_CHUCK -> "__op__div";
                default -> "__op__plus";
              };
          return rhsCode + " = " + rhsCode + "." + vecMethod + "(" + lhsCode + ")";
        }
        if ("Complex".equals(targetType) || "Polar".equals(targetType)) {
          String method =
              switch (be.op()) {
                case PLUS_CHUCK -> "plus";
                case MINUS_CHUCK -> "minus";
                case TIMES_CHUCK -> "times";
                case DIVIDE_CHUCK -> "div";
                default -> "plus";
              };
          return rhsCode + " = " + rhsCode + "." + method + "(" + lhsCode + ")";
        }
        if (isDur(be.lhs()) || isDur(be.rhs())) {
          String method =
              switch (be.op()) {
                case PLUS_CHUCK -> "plus";
                case MINUS_CHUCK -> "minus";
                case TIMES_CHUCK -> "times";
                case DIVIDE_CHUCK -> "div";
                default -> "plus";
              };
          return rhsCode + " = " + rhsCode + "." + method + "(" + lhsCode + ")";
        }
        if (be.rhs() instanceof ChuckAST.CallExp ce
            && ce.base() instanceof ChuckAST.DotExp de
            && ce.args().size() == 1
            && (de.member().equals("getFloat") || de.member().equals("getInt"))) {
          String baseCode = visitExp(de.base());
          String idxCode = wrapInt(ce.args().get(0));
          String cur =
              de.member().equals("getFloat")
                  ? "_callDouble(" + baseCode + ", \"getFloat\", " + idxCode + ")"
                  : "_callLong(" + baseCode + ", \"getInt\", " + idxCode + ")";
          return "_call("
              + baseCode
              + ", \"setObject\", "
              + idxCode
              + ", "
              + "("
              + cur
              + " "
              + op
              + " "
              + lhsCode
              + "))";
        }
        if (be.rhs() instanceof ChuckAST.ArrayAccessExp aae) {
          String rhsElemType = typeOf(aae);
          String getter = "getObject";
          String setter = "setObject";
          if ("double".equals(rhsElemType) || "float".equals(rhsElemType)) {
            getter = "getFloat";
            setter = "setFloat";
          } else if ("long".equals(rhsElemType) || "int".equals(rhsElemType)) {
            getter = "getInt";
            setter = "setInt";
          }
          String baseCode = visitExp(aae.base());
          for (int i = 0; i < aae.indices().size() - 1; i++) {
            String iCode = wrapInt(aae.indices().get(i));
            baseCode = "((ChuckArray)_call(" + baseCode + ", \"getObject\", " + iCode + "))";
          }
          String lastIdxCode = wrapInt(aae.indices().get(aae.indices().size() - 1));
          String cur =
              "getObject".equals(getter)
                  ? "_call(" + baseCode + ", \"getObject\", " + lastIdxCode + ")"
                  : baseCode + "." + getter + "(" + lastIdxCode + ")";
          String next =
              "getObject".equals(getter)
                  ? "(_num(" + cur + ") " + op + " _num(" + lhsCode + "))"
                  : "(_num(" + cur + ") " + op + " _num(" + lhsCode + "))";
          if ("setInt".equals(setter)) {
            next = "(long)(" + next + ")";
          }
          if ("setObject".equals(setter)) {
            return "_call(" + baseCode + ", \"setObject\", " + lastIdxCode + ", " + next + ")";
          }
          return "_call(" + baseCode + ", \"" + setter + "\", " + lastIdxCode + ", " + next + ")";
        }
        if ("Object".equals(targetType)) {
          return rhsCode + " = (_num(" + rhsCode + ") " + op + " _num(" + lhsCode + "))";
        }
        return rhsCode + " " + op + "= (" + lhsCode + ")";
      }
      if (be.op() == ChuckAST.Operator.PERCENT_CHUCK) {
        return rhsCode + " %= " + lhsCode;
      }

      if (be.op() == ChuckAST.Operator.EQ || be.op() == ChuckAST.Operator.NEQ) {
        if ("String[]".equals(typeOf(be.lhs()))
            || ("Object".equals(typeOf(be.lhs())) && lhsCode.endsWith(".args()"))) {
          lhsCode = lhsCode + ".length";
        }
        if ("String[]".equals(typeOf(be.rhs()))
            || ("Object".equals(typeOf(be.rhs())) && rhsCode.endsWith(".args()"))) {
          rhsCode = rhsCode + ".length";
        }
        if (typeOf(be.lhs()).equals("String") || typeOf(be.rhs()).equals("String")) {
          String prefix = be.op() == ChuckAST.Operator.EQ ? "" : "!";
          return prefix + "String.valueOf(" + lhsCode + ").equals(String.valueOf(" + rhsCode + "))";
        }
        boolean lhsDynamicValue =
            "Object".equals(typeOf(be.lhs()))
                || "ChuckArray".equals(typeOf(be.lhs()))
                || lhsCode.contains("getObject")
                || lhsCode.contains("\"getObject\"")
                || lhsCode.contains("_call(")
                || lhsCode.contains(".cval(");
        boolean rhsDynamicValue =
            "Object".equals(typeOf(be.rhs()))
                || "ChuckArray".equals(typeOf(be.rhs()))
                || rhsCode.contains("getObject")
                || rhsCode.contains("\"getObject\"")
                || rhsCode.contains("_call(")
                || rhsCode.contains(".cval(");
        String op = be.op() == ChuckAST.Operator.EQ ? "==" : "!=";
        if (lhsDynamicValue || rhsDynamicValue) {
          return "(_num(" + lhsCode + ") " + op + " _num(" + rhsCode + "))";
        }
        return "(" + lhsCode + " " + op + " " + rhsCode + ")";
      }

      if (be.op() == ChuckAST.Operator.GE
          || be.op() == ChuckAST.Operator.GT
          || be.op() == ChuckAST.Operator.LE
          || be.op() == ChuckAST.Operator.LT) {
        if ("String[]".equals(typeOf(be.lhs()))
            || ("Object".equals(typeOf(be.lhs())) && lhsCode.endsWith(".args()"))) {
          lhsCode = lhsCode + ".length";
        }
        if ("String[]".equals(typeOf(be.rhs()))
            || ("Object".equals(typeOf(be.rhs())) && rhsCode.endsWith(".args()"))) {
          rhsCode = rhsCode + ".length";
        }
        if (typeOf(be.lhs()).equals("String") && typeOf(be.rhs()).equals("String")) {
          String op = mapOp(be.op());
          return "(" + lhsCode + ".compareTo(" + rhsCode + ") " + op + " 0)";
        }
        if (isDur(be.lhs()) || isDur(be.rhs())) {
          String l = isDur(be.lhs()) ? durationScalar(lhsCode) : lhsCode;
          String r = isDur(be.rhs()) ? durationScalar(rhsCode) : rhsCode;
          return "(" + l + " " + mapOp(be.op()) + " " + r + ")";
        }
        boolean lhsDynamicValue =
            "Object".equals(typeOf(be.lhs()))
                || lhsCode.contains("getObject")
                || lhsCode.contains("\"getObject\"")
                || lhsCode.contains("_call(")
                || lhsCode.contains(".cval(");
        boolean rhsDynamicValue =
            "Object".equals(typeOf(be.rhs()))
                || rhsCode.contains("getObject")
                || rhsCode.contains("\"getObject\"")
                || rhsCode.contains("_call(")
                || rhsCode.contains(".cval(");
        if ((be.op() == ChuckAST.Operator.GT
                || be.op() == ChuckAST.Operator.GE
                || be.op() == ChuckAST.Operator.LT)
            && (lhsDynamicValue || rhsDynamicValue)) {
          return "(_num(" + lhsCode + ") " + mapOp(be.op()) + " _num(" + rhsCode + "))";
        }
        if (be.op() != ChuckAST.Operator.LE) {
          String leftCmp = lhsCode;
          String rightCmp = rhsCode;
          if (leftCmp.contains("+=")
              || leftCmp.contains("-=")
              || leftCmp.contains("*=")
              || leftCmp.contains("/=")) {
            leftCmp = "(" + leftCmp + ")";
          }
          if (rightCmp.contains("+=")
              || rightCmp.contains("-=")
              || rightCmp.contains("*=")
              || rightCmp.contains("/=")) {
            rightCmp = "(" + rightCmp + ")";
          }
          return "(" + leftCmp + " " + mapOp(be.op()) + " " + rightCmp + ")";
        }
      }
      if (be.op() == ChuckAST.Operator.UPCHUCK) {
        String rhsT = typeOf(be.rhs());
        if (be.rhs() instanceof ChuckAST.IdExp || be.rhs() instanceof ChuckAST.DeclExp) {
          if (!"UAnaBlob".equals(rhsT) && !"Object".equals(rhsT)) {
            return "_chuckConnect(" + lhsCode + ".upchuck(), " + visitExp(be.rhs()) + ")";
          }
          return visitExp(be.rhs()) + " = " + lhsCode + ".upchuck()";
        }
        return lhsCode + ".upchuck()";
      }
      if (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK) {
        // () => foo;  (empty-tuple chuck-call)
        if (be.rhs() instanceof ChuckAST.IdExp rid
            && userFunctions.contains(normalizeFunctionName(rid.name()))
            && be.lhs() instanceof ChuckAST.IntExp ie
            && ie.value() == 0) {
          return normalizeFunctionName(rid.name()) + "()";
        }
        // Special case: duration => now OR event => now OR event_array => now
        if (be.rhs() instanceof ChuckAST.IdExp id && id.name().equals("now")) {
          return "advance(_toDur(" + lhsCode + "))";
        }

        // Handle: Machine.getGlobalObject(...) $ int[] @=> data;
        if (be.lhs() instanceof ChuckAST.CastExp ce) {
          String targetType = mapType(ce.targetType());
          if (targetType.endsWith("[]") && !targetType.startsWith("ChuckEvent"))
            targetType = "ChuckArray";
          String casted = coerceToTypeExpr(visitExp(ce.value()), typeOf(ce.value()), targetType);
          if (be.rhs() instanceof ChuckAST.DotExp de) {
            return "_chuckSet("
                + visitExp(de.base())
                + ", \""
                + de.member()
                + "\", "
                + normalizeChuckSetRhs(casted, targetType)
                + ")";
          }
          if (be.rhs() instanceof ChuckAST.CallExp rhsCall
              && rhsCall.base() instanceof ChuckAST.DotExp de
              && rhsCall.args().isEmpty()) {
            return "_chuckSet("
                + visitExp(de.base())
                + ", \""
                + de.member()
                + "\", "
                + normalizeChuckSetRhs(casted, targetType)
                + ")";
          }
          if (be.rhs() instanceof ChuckAST.DeclExp de) {
            String deName = safeName(de.name());
            boolean isAlreadyField = fields.stream().anyMatch(f -> f.name().equals(de.name()));
            varTypes.put(deName, targetType);
            if (isAlreadyField) {
              return deName + " = " + casted;
            }
            return targetType + " " + deName + " = " + casted;
          }
          return rhsCode + " = " + casted;
        }

        // Handle: UGen u => dac; and UGen u => Gain g;
        if (be.lhs() instanceof ChuckAST.DeclExp lde) {
          String lType = mapType(lde.type());
          String lName = safeName(lde.name());
          boolean lAlreadyField = fields.stream().anyMatch(f -> f.name().equals(lde.name()));
          varTypes.put(lName, lType);
          StringBuilder chain = new StringBuilder();
          if (!lAlreadyField) {
            if (isPrimitive(lType)) {
              chain
                  .append(lType)
                  .append(" ")
                  .append(lName)
                  .append(" = (")
                  .append(lType)
                  .append(")(0);")
                  .append("\n");
            } else if ("String".equals(lType)) {
              chain.append("String ").append(lName).append(" = null;\n");
            } else if (lType.endsWith("[]")) {
              chain.append(lType).append(" ").append(lName).append(" = null;\n");
            } else {
              chain
                  .append(lType)
                  .append(" ")
                  .append(lName)
                  .append(" = _new(")
                  .append(lType)
                  .append(".class);")
                  .append("\n");
            }
          }
          if (be.rhs() instanceof ChuckAST.DeclExp rde) {
            String rType = mapType(rde.type());
            String rName = safeName(rde.name());
            varTypes.put(rName, rType);
            boolean rAlreadyField = fields.stream().anyMatch(f -> f.name().equals(rde.name()));
            if (!rAlreadyField) {
              if (isPrimitive(rType)) {
                chain
                    .append(rType)
                    .append(" ")
                    .append(rName)
                    .append(" = (")
                    .append(rType)
                    .append(")(0);")
                    .append("\n");
              } else if ("String".equals(rType)) {
                chain.append("String ").append(rName).append(" = null;\n");
              } else if (rType.endsWith("[]")) {
                chain.append(rType).append(" ").append(rName).append(" = null;\n");
              } else {
                chain
                    .append(rType)
                    .append(" ")
                    .append(rName)
                    .append(" = _new(")
                    .append(rType)
                    .append(".class);")
                    .append("\n");
              }
            }
            if (isConnectionTargetType(lType) || isConnectionTargetType(rType)) {
              chain.append("_chuckConnect(").append(lName).append(", ").append(rName).append(")");
              return chain.toString();
            }
          }
          if (isConnectionTargetType(lType)) {
            chain.append("_chuckConnect(").append(lName).append(", ").append(rhsCode).append(")");
            return chain.toString();
          }
          if (isPrimitive(lType)) {
            chain
                .append(lName)
                .append(" = (")
                .append(lType)
                .append(")(")
                .append(rhsCode)
                .append(")");
            return chain.toString();
          }
        }

        // Handle: 0 => int i
        if (be.rhs() instanceof ChuckAST.DeclExp de) {
          String deType = mapType(de.type());
          boolean isAlreadyField = fields.stream().anyMatch(f -> f.name().equals(de.name()));
          String deSafe = safeName(de.name());

          String javaType = deType;
          if (deSafe.startsWith("_CHUCK_SPECIAL_new_array_")
              && lhsCode.contains("(ChuckArray)_call(")) {
            javaType = "ChuckArray";
            deType = "ChuckArray";
          }
          if (deType.endsWith("[]") && !deType.startsWith("ChuckEvent")) {
            javaType = "ChuckArray";
          }
          varTypes.put(deSafe, javaType);

          if (isConnectionTargetType(deType)) {
            if (isAlreadyField) {
              return "_chuckConnect(" + lhsCode + ", " + deSafe + ")";
            }
            return javaType
                + " "
                + deSafe
                + " = _new("
                + javaType
                + ".class);\n"
                + "_chuckConnect("
                + lhsCode
                + ", "
                + deSafe
                + ")";
          }

          String rhsValue = coerceToTypeExpr(lhsCode, lhsType, javaType);
          if (isAlreadyField) {
            return deSafe + " = " + rhsValue;
          } else {
            return javaType + " " + deSafe + " = " + rhsValue;
          }
        }

        // val => s.freq
        if (be.rhs() instanceof ChuckAST.CallExp ce
            && ce.base() instanceof ChuckAST.DotExp de
            && ce.args().isEmpty()) {
          return "_chuckSet("
              + visitExp(de.base())
              + ", \""
              + de.member()
              + "\", "
              + normalizeChuckSetRhs(lhsCode, lhsType)
              + ")";
        }
        if (be.rhs() instanceof ChuckAST.DotExp de) {
          String arg = lhsCode;
          if (arg.contains("=") && !arg.contains("==")) {
            int p = arg.lastIndexOf('=');
            if (p >= 0 && p + 1 < arg.length()) {
              arg = arg.substring(p + 1).trim();
            }
            int opens = 0;
            int closes = 0;
            for (int i = 0; i < arg.length(); i++) {
              char ch = arg.charAt(i);
              if (ch == '(') opens++;
              else if (ch == ')') closes++;
            }
            if (arg.endsWith(")") && closes > opens) {
              arg = arg.substring(0, arg.length() - 1).trim();
            }
          }
          String baseType = typeOf(de.base());
          String member = de.member();
          boolean isLikelyUGen = isUGen(baseType) || baseType.equals("Object");

          if (isLikelyUGen) {
            if (lhsType.equals("ChuckDuration")) {
              arg = durationScalar(arg);
            } else if (lhsType.equals("double")
                || lhsType.equals("long")
                || lhsType.equals("String")
                || lhsType.equals("Object")) {
              // Known String methods
              if (member.equals("read")
                  || member.equals("write")
                  || member.equals("path")
                  || member.equals("name")
                  || member.equals("window")) {
                // No cast for strings
              } else if (member.equals("bits")
                  || member.equals("downsample")
                  || member.equals("port")
                  || member.equals("order")
                  || member.equals("reps")
                  || member.equals("channel")
                  || member.equals("id")
                  || member.equals("sync")) {
                arg = "(int)(" + arg + ")";
              } else if (member.equals("atoi")) {
                // Keep as String for Std.atoi
              } else if (!lhsType.equals("String")) {
                arg = "(float)(" + arg + ")";
              }
            }
          }
          if ("MidiMsg".equals(typeOf(de.base()))) {
            return "_chuckSet("
                + visitExp(de.base())
                + ", \""
                + member
                + "\", "
                + normalizeChuckSetRhs(arg, lhsType)
                + ")";
          }
          String baseExpr = visitExp(de.base());
          if (member.equals("left")) {
            return "_chuckConnect(" + lhsCode + ", _ugenChan(" + baseExpr + ", 0))";
          }
          if (member.equals("right")) {
            return "_chuckConnect(" + lhsCode + ", _ugenChan(" + baseExpr + ", 1))";
          }
          if (member.equals("pfreq")) {
            member = "freq";
          }
          if ("Std".equals(baseExpr) && member.equals("atoi")) {
            return "Std.atoi((String)(" + arg + "))";
          }
          if ("Std".equals(baseExpr) && member.equals("atof")) {
            return "Std.atof((String)(" + arg + "))";
          }
          if ("Std".equals(baseExpr) && member.equals("mtof")) {
            return "(__std_mtof_tmp = mtof(_num(" + arg + ")))";
          }
          if ("Std".equals(baseExpr) && member.equals("ftom")) {
            return "(__std_ftom_tmp = _ftom(" + arg + "))";
          }
          if ("ChuckMath".equals(baseExpr) && member.equals("isinf")) {
            return "Double.isInfinite(" + arg + ")";
          }
          if ("ChuckMath".equals(baseExpr) && member.equals("isnan")) {
            return "Double.isNaN(" + arg + ")";
          }
          if ("ChuckMath".equals(baseExpr) && member.equals("dbtorms")) {
            return "org.chuck.core.Std.dbtorms(" + arg + ")";
          }
          if ("ChuckMath".equals(baseExpr) && member.equals("rmstodb")) {
            return "org.chuck.core.Std.rmstodb(" + arg + ")";
          }
          if ("ChuckMath".equals(baseExpr)) {
            return "ChuckMath." + member + "(" + arg + ")";
          }
          if (userClasses.contains(baseExpr)) {
            return baseExpr + "." + member + " = " + arg;
          }
          return "_chuckSet("
              + baseExpr
              + ", \""
              + member
              + "\", "
              + normalizeChuckSetRhs(arg, lhsType)
              + ") /*"
              + baseExpr
              + "."
              + member
              + "*/";
        }

        // val => m["key"] or val => m[0][1]
        if (be.rhs() instanceof ChuckAST.ArrayAccessExp aae) {
          String valType = typeOf(be.lhs());
          String method = "setObject";
          if ("long".equals(valType) || "int".equals(valType)) method = "setInt";
          else if ("double".equals(valType) || "float".equals(valType)) method = "setFloat";

          String baseCode = visitExp(aae.base());
          for (int i = 0; i < aae.indices().size() - 1; i++) {
            String iCode = wrapInt(aae.indices().get(i));
            baseCode = "((ChuckArray)_call(" + baseCode + ", \"getObject\", " + iCode + "))";
          }
          String lastIdxCode = wrapInt(aae.indices().get(aae.indices().size() - 1));

          if (typeOf(aae.indices().get(aae.indices().size() - 1)).equals("String")) {
            return "org.chuck.core.ChuckDSL."
                + method
                + "("
                + baseCode
                + ", "
                + visitExp(aae.indices().get(aae.indices().size() - 1))
                + ", "
                + lhsCode
                + ")";
          }
          if ("setObject".equals(method)) {
            return "_call("
                + baseCode
                + ", \""
                + method
                + "\", "
                + lastIdxCode
                + ", "
                + lhsCode
                + ")";
          }
          String rhsValue = lhsCode;
          if ("setInt".equals(method)) {
            rhsValue = "(long)(" + lhsCode + ")";
          } else if ("setFloat".equals(method)) {
            rhsValue = "(double)(" + lhsCode + ")";
          }
          return "_call("
              + baseCode
              + ", \""
              + method
              + "\", "
              + lastIdxCode
              + ", "
              + rhsValue
              + ")";
        }

        // Handle primitive or array assignment via =>
        if (be.rhs() instanceof ChuckAST.IdExp || be.rhs() instanceof ChuckAST.DeclExp) {
          if (be.op() == ChuckAST.Operator.AT_CHUCK) {
            if (rhsType != null
                && !isPrimitive(rhsType)
                && !rhsType.endsWith("[]")
                && !"ChuckArray".equals(rhsType)
                && !isConnectionTargetType(rhsType)) {
              String cast = "(" + rhsType + ")";
              String typePrefix = declPrefixFor(be.rhs(), rhsType);
              if ("String".equals(rhsType)) {
                return typePrefix + rhsCode + " = String.valueOf(" + lhsCode + ")";
              }
              return typePrefix + rhsCode + " = " + cast + "(" + lhsCode + ")";
            }
          }
          if (rhsType != null
              && (isPrimitive(rhsType) || rhsType.endsWith("[]") || "ChuckArray".equals(rhsType))) {
            if ("ChuckArray".equals(rhsType)) {
              return "_chuckConnect(" + lhsCode + ", " + rhsCode + ")";
            }
            if (rhsType.endsWith("[]") && be.lhs() instanceof ChuckAST.ArrayLitExp ale) {
              String typePrefix = declPrefixFor(be.rhs(), rhsType);
              return typePrefix + rhsCode + " = " + toTypedArrayLiteral(ale, rhsType);
            }
            String lhsValueType = typeOf(be.lhs());
            if (("long".equals(rhsType) || "int".equals(rhsType))
                && "boolean".equals(lhsValueType)) {
              String typePrefix = declPrefixFor(be.rhs(), rhsType);
              return typePrefix + rhsCode + " = ((" + lhsCode + ") ? 1L : 0L)";
            }
            String typePrefix = declPrefixFor(be.rhs(), rhsType);
            return typePrefix
                + rhsCode
                + " = "
                + coerceToTypeExpr(lhsCode, typeOf(be.lhs()), rhsType);
          }
          if (rhsType != null && isConnectionTargetType(rhsType)) {
            if (be.rhs() instanceof ChuckAST.DeclExp de) {
              String declType = mapType(de.type());
              if (declType.endsWith("[]") && !declType.startsWith("ChuckEvent")) {
                declType = "ChuckArray";
              }
              return declType
                  + " "
                  + rhsCode
                  + " = _new("
                  + declType
                  + ".class);\n"
                  + "_chuckConnect("
                  + lhsCode
                  + ", "
                  + rhsCode
                  + ")";
            }
            return "_chuckConnect(" + lhsCode + ", " + rhsCode + ")";
          }
          if (rhsType == null) {
            String inferredType = typeOf(be.lhs());
            if (inferredType != null && isPrimitive(inferredType)) {
              varTypes.put(rhsCode, inferredType);
              return rhsCode + " = (" + inferredType + ")(" + lhsCode + ")";
            }
          }
        }

        if (globals.contains(rhsCode)) {
          String setter =
              switch (rhsType != null ? rhsType : "Object") {
                case "long" -> "Machine.setGlobalInt(\"" + rhsCode + "\", " + lhsCode + ")";
                case "double" -> "Machine.setGlobalFloat(\"" + rhsCode + "\", " + lhsCode + ")";
                default -> "Machine.setGlobalObject(\"" + rhsCode + "\", " + lhsCode + ")";
              };
          return setter;
        }

        // Handle method call on RHS: val => freq
        String inferredRhsType = typeOf(be.rhs());
        if (be.rhs() instanceof ChuckAST.IdExp id && isFunction(id.name())) {
          return safeName(id.name()) + "(" + lhsCode + ")";
        }

        if (be.rhs() instanceof ChuckAST.CallExp ce
            && ce.base() instanceof ChuckAST.DotExp de
            && ce.args().isEmpty()) {
          String rhsValue = coerceToTypeExpr(lhsCode, typeOf(be.lhs()), typeOf(be.rhs()));
          return "_chuckSet("
              + visitExp(de.base())
              + ", \""
              + de.member()
              + "\", "
              + rhsValue
              + ")";
        }

        if (rhsType != null
            && (isPrimitive(rhsType)
                || "ChuckDuration".equals(rhsType)
                || "String".equals(rhsType))) {
          String lhsValueType = typeOf(be.lhs());
          if (("long".equals(rhsType) || "int".equals(rhsType)) && "boolean".equals(lhsValueType)) {
            if (be.rhs() instanceof ChuckAST.DeclExp de) {
              return rhsType + " " + safeName(de.name()) + " = ((" + lhsCode + ") ? 1L : 0L)";
            }
            return rhsCode + " = ((" + lhsCode + ") ? 1L : 0L)";
          }
          String rhsValue = coerceToTypeExpr(lhsCode, lhsValueType, rhsType);
          if (be.rhs() instanceof ChuckAST.DeclExp de) {
            return rhsType + " " + safeName(de.name()) + " = " + rhsValue;
          }
          if (be.lhs() instanceof ChuckAST.DeclExp de) {
            String declType = mapType(de.type());
            if (declType.endsWith("[]") && !declType.startsWith("ChuckEvent")) {
              declType = "ChuckArray";
            }
            if ("String".equals(declType)) {
              return declType + " " + safeName(de.name()) + " = String.valueOf(" + rhsCode + ")";
            }
            return declType
                + " "
                + safeName(de.name())
                + " = "
                + coerceToTypeExpr(rhsCode, typeOf(be.rhs()), declType);
          }
          return rhsCode + " = " + rhsValue;
        }

        // Default connection-style fallback
        return "_chuckConnect(" + lhsCode + ", " + rhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.UNCHUCK) {
        return "_call(" + lhsCode + ", \"unchuck\", " + rhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.DUR_MUL) {
        // 1::second -> second().times(1)
        String lhsTypeDur = typeOf(be.lhs());
        String rhsTypeDur = typeOf(be.rhs());
        if (isDur(be.rhs())) {
          if ("Object".equals(rhsTypeDur) || rhsCode.contains("_call("))
            return "_toDur(_call(" + rhsCode + ", \"times\", " + lhsCode + "))";
          return rhsCode + ".times(" + lhsCode + ")";
        }
        if (isDur(be.lhs())) {
          if ("Object".equals(lhsTypeDur) || lhsCode.contains("_call("))
            return "_toDur(_call(" + lhsCode + ", \"times\", " + rhsCode + "))";
          return lhsCode + ".times(" + rhsCode + ")";
        }
        if ("Object".equals(rhsTypeDur))
          return "_call(" + rhsCode + ", \"times\", " + lhsCode + ")";
        if ("Object".equals(lhsTypeDur))
          return "_call(" + lhsCode + ", \"times\", " + rhsCode + ")";
        return rhsCode + ".times(" + lhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.ASSIGN) {
        if (be.lhs() instanceof ChuckAST.CallExp ce
            && ce.base() instanceof ChuckAST.DotExp de
            && ce.args().size() == 1
            && (de.member().equals("getFloat") || de.member().equals("getInt"))) {
          String baseCode = visitExp(de.base());
          String idxCode = wrapInt(ce.args().get(0));
          return "_call(" + baseCode + ", \"setObject\", " + idxCode + ", " + rhsCode + ")";
        }
        if (be.lhs() instanceof ChuckAST.CallExp ce
            && ce.base() instanceof ChuckAST.DotExp de
            && ce.args().isEmpty()) {
          return "_chuckSet("
              + visitExp(de.base())
              + ", \""
              + de.member()
              + "\", "
              + normalizeChuckSetRhs(rhsCode, typeOf(be.rhs()))
              + ")";
        }
        if (be.lhs() instanceof ChuckAST.DotExp de) {
          return "_chuckSet("
              + visitExp(de.base())
              + ", \""
              + de.member()
              + "\", "
              + normalizeChuckSetRhs(rhsCode, typeOf(be.rhs()))
              + ")";
        }
        if (globals.contains(lhsCode)) {
          return "Machine.setGlobalObject(\"" + lhsCode + "\", " + rhsCode + ")";
        }
        if (lhsCode.startsWith("_CHUCK_SPECIAL_new_array_")
            && rhsCode.startsWith("(long)(((ChuckArray)_call(")
            && rhsCode.endsWith(")))")) {
          rhsCode = rhsCode.replaceFirst("^\\(long\\)\\(", "");
          rhsCode = rhsCode.substring(0, rhsCode.length() - 1);
        }
        String lhsAssignType = typeOf(be.lhs());
        if (lhsCode.startsWith("_CHUCK_SPECIAL_new_array_")
            && rhsCode.contains("ChuckArray")
            && ("long".equals(lhsAssignType) || "int".equals(lhsAssignType))) {
          rhsCode = "(long)(_sizeOf(" + rhsCode + "))";
        }
        if (lhsAssignType != null
            && (isPrimitive(lhsAssignType)
                || "String".equals(lhsAssignType)
                || "ChuckDuration".equals(lhsAssignType))) {
          rhsCode = coerceToTypeExpr(rhsCode, typeOf(be.rhs()), lhsAssignType);
        }
        return lhsCode + " = " + rhsCode;
      } else if (be.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS) {
        return rhsCode + "++";
      } else if (be.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS) {
        return rhsCode + "--";
      }
      if (be.op() == ChuckAST.Operator.SHIFT_LEFT) {
        if ("ChuckArray".equals(typeOf(be.lhs()))
            || ("Object".equals(typeOf(be.lhs())) && lhsCode.contains("ChuckArray"))
            || lhsCode.contains("_call(")
            || typeOf(be.lhs()).endsWith("[]")) {
          return lhsCode + ".append(" + rhsCode + ")";
        }
      }
      if (lhsCode.equals("now()")) lhsCode = "samp(now())";
      if (rhsCode.equals("now()")) rhsCode = "samp(now())";

      if (be.op() == ChuckAST.Operator.LE) {
        if ("ChuckIO".equals(lhsType)
            || "org.chuck.core.ChuckIO".equals(lhsType)
            || "org.chuck.core.ChuckDSL.ChuckIO".equals(lhsType)
            || (lhsType != null && lhsType.endsWith("ChuckIO"))
            || "FileIO".equals(lhsType)
            || "org.chuck.core.FileIO".equals(lhsType)
            || "SerialIO".equals(lhsType)
            || "Object".equals(lhsType)
            || "chout".equals(lhsType)
            || "cherr".equals(lhsType)
            || "out".equals(lhsCode)
            || "cherr".equals(lhsCode)
            || "chout".equals(lhsCode)
            || lhsCode.startsWith("_chuckWrite(")
            || lhsCode.contains(".print(")
            || lhsCode.contains("cherr.print")
            || lhsCode.contains("chout.print")) {
          return "_chuckWrite(" + lhsCode + ", " + rhsCode + ")";
        }
      }

      return "(" + lhsCode + " " + mapOp(be.op()) + " " + rhsCode + ")";
    } else if (exp instanceof ChuckAST.LogicalExp le) {
      List<String> ops = new ArrayList<>();
      flattenLogical(le, ops);

      boolean anyEvent = false;
      for (String operand : ops) {
        if (isEvent(operand)) {
          anyEvent = true;
          break;
        }
      }

      if (anyEvent) {
        String method = "&&".equals(le.op()) ? "eventAnd" : "eventOr";
        return method + "(" + String.join(", ", ops) + ")";
      }

      String op = "&&".equals(le.op()) ? "&&" : "||";
      return "(" + visitBoolExp(le.lhs()) + " " + op + " " + visitBoolExp(le.rhs()) + ")";
    } else if (exp instanceof ChuckAST.CallExp ce) {
      if (ce.base() instanceof ChuckAST.DotExp deCk
          && deCk.base() instanceof ChuckAST.IdExp idCk
          && "CKDoc".equals(idCk.name())
          && "describe".equals(deCk.member())
          && ce.args().size() == 1) {
        ChuckAST.Exp arg = ce.args().get(0);
        if (arg instanceof ChuckAST.IdExp id && userClasses.contains(safeName(id.name()))) {
          return "CKDoc.describe(" + safeName(id.name()) + ".class)";
        }
        if (arg instanceof ChuckAST.DotExp deArg
            && deArg.base() instanceof ChuckAST.IdExp bid
            && userClasses.contains(safeName(bid.name()))) {
          return "CKDoc.describe(_call(new "
              + safeName(bid.name())
              + "(), \""
              + safeName(deArg.member())
              + "\"))";
        }
        if (arg instanceof ChuckAST.CallExp ace
            && ace.base() instanceof ChuckAST.DotExp deArgCall) {
          String callBase = visitExp(deArgCall.base());
          String args =
              ace.args().isEmpty()
                  ? ""
                  : ", "
                      + ace.args().stream().map(this::visitExp).collect(Collectors.joining(", "));
          return "CKDoc.describe(_call("
              + callBase
              + ", \""
              + safeName(deArgCall.member())
              + "\""
              + args
              + "))";
        }
      }
      if (ce.base() instanceof ChuckAST.DotExp stdDe
          && stdDe.base() instanceof ChuckAST.IdExp stdBase
          && "Std".equals(stdBase.name())
          && ce.args().size() == 1) {
        if ("mtof".equals(stdDe.member())) {
          return "mtof(_num(" + visitExp(ce.args().get(0)) + "))";
        }
        if ("ftom".equals(stdDe.member())) {
          return "_ftom(_num(" + visitExp(ce.args().get(0)) + "))";
        }
      }
      String base = visitExp(ce.base());
      if (base.startsWith("\"") && base.endsWith("\"") && base.length() > 2) {
        String unquoted = base.substring(1, base.length() - 1);
        if (unquoted.matches("[A-Za-z_][A-Za-z0-9_]*")) {
          base = safeName(unquoted);
        }
      }
      if (base.startsWith("_CHUCK_INTERNAL_ASSERT_")) {
        String a0 =
            ce.args().isEmpty()
                ? "1L"
                : coerceToTypeExpr(visitExp(ce.args().get(0)), typeOf(ce.args().get(0)), "long");
        if (ce.args().size() == 1) {
          return "_CHUCK_INTERNAL_ASSERT_(" + a0 + ", \"\")";
        }
        String rest =
            ce.args().subList(1, ce.args().size()).stream()
                .map(this::visitExp)
                .collect(Collectors.joining(", "));
        return "_CHUCK_INTERNAL_ASSERT_(" + a0 + (rest.isEmpty() ? "" : ", " + rest) + ")";
      }
      if (base.equals("assert")) {
        if (!ce.args().isEmpty()) {
          String a0 = visitExp(ce.args().get(0));
          String a0Type = typeOf(ce.args().get(0));
          if ("boolean".equals(a0Type)
              || a0.contains(".equals(")
              || a0.startsWith("!")
              || a0.contains("==")
              || a0.contains("!=")
              || a0.contains(">=")
              || a0.contains("<=")
              || a0.contains(" > ")
              || a0.contains(" < ")) {
            a0 = "((" + a0 + ") ? 1L : 0L)";
          }
          if (ce.args().size() == 1) {
            return "org.chuck.core.ChuckDSL._CHUCK_INTERNAL_ASSERT_(" + a0 + ", \"\")";
          }
          String rest =
              ce.args().subList(1, ce.args().size()).stream()
                  .map(this::visitExp)
                  .collect(Collectors.joining(", "));
          return "org.chuck.core.ChuckDSL._CHUCK_INTERNAL_ASSERT_("
              + a0
              + (rest.isEmpty() ? "" : ", " + rest)
              + ")";
        }
        return "org.chuck.core.ChuckDSL._CHUCK_INTERNAL_ASSERT_(1L, \"\")";
      }
      if (base.equals("me") || base.equals("me()")) base = "org.chuck.core.ChuckDSL.me";
      if (base.endsWith(".newline")) return "ChIO.newline()";
      if (base.endsWith(".nl")) return "ChIO.nl()";
      if (ce.args().isEmpty() && (base.endsWith(".noteOff") || base.endsWith(".noteOff()"))) {
        if (base.endsWith(".noteOff()")) {
          return base.substring(0, base.length() - 1) + "1.0)";
        }
        return base + "(1.0)";
      }

      // If the base is a DotExp or IdExp and not already a method call, add ()
      if (!base.endsWith(")") && !base.contains("Math.") && !base.contains("Std.")) {
        // Special case: SinOsc etc are constructors when in CallExp? No, SinOsc is a type.
        // But methods on objects need ().
        base = base + "()";
      }

      // String Parity Mappings
      if (ce.base() instanceof ChuckAST.DotExp de) {
        String baseCode = visitExp(de.base());
        String recType = typeOf(de.base());
        String member = de.member();
        String hidMember = mapHidMember(member);
        if (isHidMsgType(recType)) {
          if (member.equals("isHatMotion")) {
            return "_callBool(" + baseCode + ", \"isAxisMotion\")";
          }
          if (hidMember != null) {
            if (ce.args().isEmpty()) {
              return baseCode + "." + hidMember;
            }
            if (ce.args().size() == 1) {
              return "_chuckSet("
                  + baseCode
                  + ", \""
                  + hidMember
                  + "\", "
                  + visitExp(ce.args().get(0))
                  + ")";
            }
          }
        }

        if (member.equals("charAt")) {
          return baseCode + ".charAt((int)(" + visitExp(ce.args().get(0)) + "))";
        }
        if (member.equals("getInt")) {
          if (recType != null && recType.endsWith("[]")) {
            String idx = wrapInt(ce.args().get(0));
            return "(long)(" + baseCode + "[" + idx + "])";
          }
          return "_callLong("
              + baseCode
              + ", \"getInt\", (int)("
              + visitExp(ce.args().get(0))
              + "))";
        }
        if (member.equals("getFloat")) {
          if (recType != null && recType.endsWith("[]")) {
            String idx = wrapInt(ce.args().get(0));
            return "(double)(" + baseCode + "[" + idx + "])";
          }
          return "_callDouble("
              + baseCode
              + ", \"getFloat\", (int)("
              + visitExp(ce.args().get(0))
              + "))";
        }
        if ((member.equals("setInt") || member.equals("setFloat")) && ce.args().size() == 2) {
          return "_call("
              + baseCode
              + ", \""
              + member
              + "\", (int)("
              + visitExp(ce.args().get(0))
              + "), "
              + visitExp(ce.args().get(1))
              + ")";
        }
        if (member.equals("times") && "Object".equals(recType)) {
          return "_call("
              + baseCode
              + ", \"times\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if ((member.equals("num") || member.equals("n")) && ce.args().isEmpty()) {
          return "_callLong(" + baseCode + ", \"" + member + "\")";
        }
        if ((member.equals("num") || member.equals("n")) && ce.args().size() == 1) {
          return "_call(" + baseCode + ", \"" + member + "\", " + visitExp(ce.args().get(0)) + ")";
        }
        if (member.equals("children") && ce.args().isEmpty()) {
          return "_callArray(" + baseCode + ", \"children\")";
        }
        if (member.equals("isa") && ce.args().size() == 1) {
          return "_isa(" + baseCode + ", (String)(" + visitExp(ce.args().get(0)) + "))";
        }
        if (ce.args().isEmpty()) {
          if ("Complex".equals(recType) && (member.equals("mag") || member.equals("phase"))) {
            return member.equals("mag") ? baseCode + ".magnitude()" : baseCode + ".phase()";
          }
          if ("Polar".equals(recType) && (member.equals("mag") || member.equals("phase"))) {
            return baseCode + "." + member;
          }
        }
        if (member.equals("getString")) {
          return "(String)_call("
              + baseCode
              + ", \"getString\", (int)("
              + visitExp(ce.args().get(0))
              + "))";
        }

        if ("ChuckIO".equals(recType) && member.equals("flush")) {
          return "_ioFlush(" + baseCode + ")";
        }
        if (member.equals("div")
            && ce.args().size() == 1
            && ("ChuckDuration".equals(recType) || "Object".equals(recType))) {
          return "_toDur(" + baseCode + ").div(_num(" + visitExp(ce.args().get(0)) + "))";
        }
        if (member.equals("div")
            && ce.args().size() == 1
            && (baseCode.contains("ms()")
                || baseCode.contains("second()")
                || baseCode.contains("samp()")
                || baseCode.contains(".times(")
                || baseCode.contains(".plus("))) {
          return "_toDur(" + baseCode + ").div(_num(" + visitExp(ce.args().get(0)) + "))";
        }
        if (ce.args().isEmpty()
            && (member.equals("data1")
                || member.equals("data2")
                || member.equals("data3")
                || member.equals("when"))) {
          return baseCode + "." + member;
        }
        if (ce.args().size() == 1
            && (member.equals("data1")
                || member.equals("data2")
                || member.equals("data3")
                || member.equals("when"))) {
          return "_chuckSet("
              + baseCode
              + ", \""
              + member
              + "\", "
              + visitExp(ce.args().get(0))
              + ")";
        }
        if ("MidiMsg".equals(recType)) {
          if (ce.args().isEmpty()
              && (member.equals("data1")
                  || member.equals("data2")
                  || member.equals("data3")
                  || member.equals("when"))) {
            return baseCode + "." + member;
          }
          if (ce.args().size() == 1
              && (member.equals("data1")
                  || member.equals("data2")
                  || member.equals("data3")
                  || member.equals("when"))) {
            return "_chuckSet("
                + baseCode
                + ", \""
                + member
                + "\", "
                + visitExp(ce.args().get(0))
                + ")";
          }
        }
        if ((member.equals("left") || member.equals("right") || member.equals("chan"))
            && (isUGen(recType) || "ChuckUGen".equals(recType) || "Object".equals(recType))) {
          long fallback = member.equals("right") ? 1L : 0L;
          String idx = ce.args().isEmpty() ? Long.toString(fallback) : visitExp(ce.args().get(0));
          return "_ugenChan(" + baseCode + ", " + idx + ")";
        }
        if ("String".equals(recType)) {
          if (member.equals("setCharAt"))
            return "org.chuck.core.ChuckDSL.setCharAt("
                + baseCode
                + ", "
                + wrapInt(ce.args().get(0))
                + ", "
                + wrapInt(ce.args().get(1))
                + ")";
          if (member.equals("setCharAt2"))
            return "org.chuck.core.ChuckDSL.setCharAt2("
                + baseCode
                + ", "
                + wrapInt(ce.args().get(0))
                + ", "
                + visitExp(ce.args().get(1))
                + ")";
          if (member.equals("charAt2"))
            return "org.chuck.core.ChuckDSL.charAt2("
                + baseCode
                + ", "
                + wrapInt(ce.args().get(0))
                + ")";
          if (member.equals("lower")) return "org.chuck.core.ChuckDSL.lower(" + baseCode + ")";
          if (member.equals("upper")) return "org.chuck.core.ChuckDSL.upper(" + baseCode + ")";
          if (member.equals("ltrim")) return "org.chuck.core.ChuckDSL.ltrim(" + baseCode + ")";
          if (member.equals("rtrim")) return "org.chuck.core.ChuckDSL.rtrim(" + baseCode + ")";
          if (member.equals("trim")) return "org.chuck.core.ChuckDSL.trim(" + baseCode + ")";
          if (member.equals("find")) {
            String arg = visitExp(ce.args().get(0));
            if (ce.args().size() > 1)
              return baseCode + ".indexOf(" + arg + ", " + wrapInt(ce.args().get(1)) + ")";
            return "org.chuck.core.ChuckDSL.find(" + baseCode + ", " + arg + ")";
          }
          if (member.equals("rfind")) {
            String arg = visitExp(ce.args().get(0));
            if (ce.args().size() > 1)
              return baseCode + ".lastIndexOf(" + arg + ", " + wrapInt(ce.args().get(1)) + ")";
            return "org.chuck.core.ChuckDSL.rfind(" + baseCode + ", " + arg + ")";
          }
          if (member.equals("open")) {
            if (ce.args().size() > 0) {
              String arg = visitExp(ce.args().get(0));
              if ("long".equals(typeOf(ce.args().get(0)))) {
                arg = "(int)(" + arg + ")";
              }
              return baseCode + ".open(" + arg + ")";
            }
          }
          if (member.equals("insert"))
            return "org.chuck.core.ChuckDSL.insert("
                + baseCode
                + ", "
                + wrapInt(ce.args().get(0))
                + ", "
                + visitExp(ce.args().get(1))
                + ")";
          if (member.equals("erase"))
            return "org.chuck.core.ChuckDSL.erase("
                + baseCode
                + ", "
                + wrapInt(ce.args().get(0))
                + ", "
                + wrapInt(ce.args().get(1))
                + ")";
          if (member.equals("replace")) {
            if (ce.args().size() > 2)
              return "org.chuck.core.ChuckDSL.replace("
                  + baseCode
                  + ", "
                  + wrapInt(ce.args().get(0))
                  + ", "
                  + wrapInt(ce.args().get(1))
                  + ", "
                  + visitExp(ce.args().get(2))
                  + ")";
            String arg1 = visitExp(ce.args().get(0));
            String arg2 = visitExp(ce.args().get(1));
            if (typeOf(ce.args().get(0)).equals("String")) {
              return baseCode + ".replace(" + arg1 + ", " + arg2 + ")";
            }
            return "org.chuck.core.ChuckDSL.replace("
                + baseCode
                + ", "
                + wrapInt(ce.args().get(0))
                + ", "
                + arg2
                + ")";
          }
        }
        if ("ChuckArray".equals(recType)) {
          if (member.equals("getObject")) {
            return baseCode + ".getObject((int)(" + visitExp(ce.args().get(0)) + "))";
          }
          if (member.equals("cap")) {
            return baseCode + ".size()";
          }
        }
        if (recType.endsWith("[]")) {
          String elemType = recType.substring(0, recType.length() - 2);
          if (member.equals("getFloat") && !ce.args().isEmpty()) {
            String idx = "(int)(" + visitExp(ce.args().get(0)) + ")";
            return "((double)(" + baseCode + "[" + idx + "]))";
          }
          if (member.equals("getInt") && !ce.args().isEmpty()) {
            String idx = "(int)(" + visitExp(ce.args().get(0)) + ")";
            return "((long)(" + baseCode + "[" + idx + "]))";
          }
          if (member.equals("size")) {
            return "_sizeOf(" + baseCode + ")";
          }
          if (member.equals("setObject") && ce.args().size() == 2) {
            String idx = "(int)(" + visitExp(ce.args().get(0)) + ")";
            String value = visitExp(ce.args().get(1));
            return "(" + baseCode + "[" + idx + "] = (" + elemType + ")(" + value + "))";
          }
        }
        if (member.equals("open") && ce.args().size() == 1) {
          String arg = visitExp(ce.args().get(0));
          if ("long".equals(typeOf(ce.args().get(0)))) arg = "(int)(" + arg + ")";
          return "_callBool(" + baseCode + ", \"open\", " + arg + ")";
        }
        if (member.equals("load")) {
          return "_callBool("
              + baseCode
              + ", \"load\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("arg") && ce.args().size() == 1) {
          return "_call(" + baseCode + ", \"arg\", (int)(" + visitExp(ce.args().get(0)) + "))";
        }
        if (member.equals("getInts")) {
          return "_toChuckArray("
              + "_call("
              + baseCode
              + ", \"getInts\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + "))";
        }
        if (member.equals("substring")) {
          if (ce.args().size() == 1) {
            return baseCode + ".substring((int)(" + visitExp(ce.args().get(0)) + "))";
          }
          if (ce.args().size() >= 2) {
            return baseCode
                + ".substring((int)("
                + visitExp(ce.args().get(0))
                + "), (int)("
                + visitExp(ce.args().get(1))
                + "))";
          }
        }
        if (member.equals("noteOn") && ce.args().size() >= 1) {
          String first = "(int)(" + visitExp(ce.args().get(0)) + ")";
          String rest =
              ce.args().subList(1, ce.args().size()).stream()
                  .map(this::visitExp)
                  .collect(Collectors.joining(", "));
          return "_call("
              + baseCode
              + ", \"noteOn\", "
              + first
              + (rest.isEmpty() ? "" : ", " + rest)
              + ")";
        }
        if (member.equals("noteOff")) {
          if (ce.args().isEmpty()) {
            return "_call(" + baseCode + ", \"noteOff\", 1.0)";
          }
          return "_call("
              + baseCode
              + ", \"noteOff\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("dest") && ce.args().size() == 2) {
          String a0 = visitExp(ce.args().get(0));
          String a1 = visitExp(ce.args().get(1));
          if ("long".equals(typeOf(ce.args().get(1)))) a1 = "(int)(" + a1 + ")";
          return "_call(" + baseCode + ", \"dest\", " + a0 + ", " + a1 + ")";
        }
        if (member.equals("write") && ce.args().size() >= 2) {
          String a0 = visitExp(ce.args().get(0));
          if ("long".equals(typeOf(ce.args().get(0)))) a0 = "(int)(" + a0 + ")";
          String rest =
              ce.args().subList(1, ce.args().size()).stream()
                  .map(this::visitExp)
                  .collect(Collectors.joining(", "));
          return "_call("
              + baseCode
              + ", \"write\", "
              + a0
              + (rest.isEmpty() ? "" : ", " + rest)
              + ")";
        }
        if (member.equals("setBpm") && ce.args().size() == 1) {
          return "_call(" + baseCode + ", \"setBpm\", (float)(" + visitExp(ce.args().get(0)) + "))";
        }
        if (member.equals("size")) {
          if (ce.args().isEmpty()) {
            return "_sizeOf(" + baseCode + ")";
          }
          return "_callLong("
              + baseCode
              + ", \"size\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("fval")) {
          return "_callDouble("
              + baseCode
              + ", \"fval\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("fvals") || member.equals("cvals")) {
          return "_callArray("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("chuck") && ce.args().size() == 1) {
          return "_chuckConnect(" + baseCode + ", " + visitExp(ce.args().get(0)) + ")";
        }
        if (member.equals("length") && ce.args().isEmpty()) {
          return "_sizeOf(" + baseCode + ")";
        }
        if (member.equals("isAxisMotion")) {
          return "_callBool(" + baseCode + ", \"isAxisMotion\")";
        }
        if (member.equals("isMouseMotion") || member.equals("openTiltSensor")) {
          return "_callBool(" + baseCode + ", \"" + member + "\")";
        }
        if (member.equals("openKeyboard")
            || member.equals("openJoystick")
            || member.equals("openMouse")) {
          return "_callBool("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("typeOf") || member.equals("typeOfInstance")) {
          String typeOfTarget = baseCode;
          if (de.base() instanceof ChuckAST.IdExp id
              && !id.name().isEmpty()
              && Character.isUpperCase(id.name().charAt(0))
              && !varTypes.containsKey(id.name())
              && fields.stream().noneMatch(f -> f.name().equals(id.name()))
              && !userClasses.contains(id.name())
              && !id.name().equals("Std")
              && !id.name().equals("Math")
              && !id.name().equals("Machine")
              && !id.name().equals("Windowing")) {
            typeOfTarget = "\"" + id.name() + "\"";
          }
          return "((Type)_call(" + typeOfTarget + ", \"typeOf\"))";
        }
        if (member.equals("arrayDepth") && ce.args().isEmpty()) {
          return "_callLong(" + baseCode + ", \"arrayDepth\")";
        }
        if (member.equals("controlOne")
            || member.equals("controlTwo")
            || member.equals("strikePosition")
            || member.equals("bowRate")
            || member.equals("bowPressure")
            || member.equals("bowPosition")
            || member.equals("filterSweepRate")
            || member.equals("lfoSpeed")
            || member.equals("stickHardness")
            || member.equals("directGain")
            || member.equals("masterGain")
            || member.equals("bodySize")
            || member.equals("stringDamping")
            || member.equals("stringDetune")
            || member.equals("reed")
            || member.equals("target")
            || member.equals("mix")
            || member.equals("sampleRate")
            || member.equals("getFMTableGain")
            || member.equals("opRatio")
            || member.equals("randomGain")
            || member.equals("jetDelay")
            || member.equals("jetReflection")
            || member.equals("endReflection")
            || member.equals("op4Feedback")
            || member.equals("stiffness")
            || member.equals("aperture")
            || member.equals("blowPosition")
            || member.equals("pickupPosition")
            || member.equals("sustain")) {
          return "_callDouble("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("which")) {
          return "_callLong(" + baseCode + ", \"which\")";
        }
        if (member.equals("opADSR") || member.equals("opWave") || member.equals("filename")) {
          return "_call("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (ce.args().isEmpty()
            && (member.equals("duration")
                || member.equals("releaseTime")
                || member.equals("attackTime")
                || member.equals("decayTime"))) {
          return "_toDur(_call(" + baseCode + ", \"" + member + "\"))";
        }
        if (member.equals("id")) {
          return "(int)(_callLong("
              + baseCode
              + ", \"id\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + "))";
        }
        if (member.equals("opGain")
            || member.equals("opAM")
            || member.equals("exit")
            || member.equals("parent")
            || member.equals("ancestor")
            || member.equals("onLine")
            || member.equals("getLine")
            || member.equals("onInts")
            || member.equals("getInts")
            || member.equals("isWheelMotion")
            || member.equals("ramp")) {
          return "_call("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("isButtonDown") || member.equals("isButtonUp")) {
          return "_callBool("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("isPrimitive") || member.equals("isArray")) {
          return "_callBool("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("good")) {
          return "_callBool("
              + baseCode
              + ", \"good\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("more")) {
          return "_callBool("
              + baseCode
              + ", \"more\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("channels")) {
          return "_callLong("
              + baseCode
              + ", \"channels\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("freq")
            || member.equals("gain")
            || member.equals("frame")
            || member.equals("lfoDepth")
            || member.equals("volume")
            || member.equals("noiseGain")
            || member.equals("vibratoFreq")
            || member.equals("vibratoGain")
            || member.equals("preset")
            || member.equals("pressure")
            || member.equals("value")
            || member.equals("samples")
            || member.equals("getFloat")
            || member.equals("getFMTableSusLevel")) {
          return "_callDouble("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("get") || member.equals("mag") || member.equals("env")) {
          return "_callDouble("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("damp") || member.equals("roll") || member.equals("strum")) {
          return "_call("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("controlChange")
            || member.equals("getFMTableTime")
            || member.equals("name")
            || member.equals("feedback")
            || member.equals("tune")
            || member.equals("table")
            || member.equals("broadcast")
            || member.equals("setObject")
            || member.equals("getObject")
            || member.equals("cap")
            || member.equals("clear")
            || member.equals("read")
            || member.equals("printerr")
            || member.equals("stopBlowing")
            || member.equals("compress")
            || member.equals("reset")
            || member.equals("chuck")) {
          return "_call("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        if (member.equals("pfreq")) {
          return baseCode + ".freq(" + visitExp(ce.args().get(0)) + ")";
        }
        if (member.equals("lower"))
          return "org.chuck.core.ChuckDSL.lower((String)(" + baseCode + "))";
        if (member.equals("upper"))
          return "org.chuck.core.ChuckDSL.upper((String)(" + baseCode + "))";
        if (member.equals("trim"))
          return "org.chuck.core.ChuckDSL.trim((String)(" + baseCode + "))";
        if (ce.args().isEmpty() && userClasses.contains(baseCode) && member.equals("help")) {
          return "new " + baseCode + "().help()";
        }
        if ("super".equals(baseCode)) {
          if (ce.args().isEmpty()) {
            return "super." + member + "()";
          }
          return "super."
              + member
              + "("
              + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", "))
              + ")";
        }
        if ("Machine".equals(baseCode)
            && (member.equals("os")
                || member.equals("timeOfDay")
                || member.equals("timeOfDay2")
                || member.equals("printStatus")
                || member.equals("printTimeCheck")
                || member.equals("resetShredID"))) {
          return "_machineCall(\""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
        String recTypeSafe = recType == null ? null : safeName(recType);
        String memberSafe = safeName(member);
        if (ce.args().isEmpty() && hasFieldOnType(recTypeSafe, memberSafe)) {
          return baseCode + "." + memberSafe;
        }
        if (!isLikelyStaticNamespace(de.base())) {
          return "_call("
              + baseCode
              + ", \""
              + member
              + "\""
              + (ce.args().isEmpty()
                  ? ""
                  : ", " + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", ")))
              + ")";
        }
      }

      // If base ends with (), remove it because we're adding it back
      if (base.endsWith("()")) base = base.substring(0, base.length() - 2);

      // Expanded Math/Std Mappings
      if (base.equals("Math.sin") || base.equals("sin"))
        return "Math.sin(_num(" + visitExp(ce.args().get(0)) + "))";
      if (base.equals("Math.cos") || base.equals("cos"))
        return "Math.cos(_num(" + visitExp(ce.args().get(0)) + "))";
      if (base.equals("Math.sqrt") || base.equals("sqrt"))
        return "Math.sqrt(_num(" + visitExp(ce.args().get(0)) + "))";
      if (base.equals("Math.abs") || base.equals("ChuckMath.abs") || base.equals("abs"))
        return "ChuckMath.abs(_num(" + visitExp(ce.args().get(0)) + "))";
      if (base.equals("Math.pow") || base.equals("pow"))
        return "Math.pow(_num("
            + visitExp(ce.args().get(0))
            + "), _num("
            + visitExp(ce.args().get(1))
            + "))";
      if (base.equals("Math.random2"))
        return "random(" + visitExp(ce.args().get(0)) + ", " + visitExp(ce.args().get(1)) + ")";
      if (base.equals("ChuckMath.random2") || base.equals("random2")) {
        return "ChuckMath.random2((long)(_num("
            + visitExp(ce.args().get(0))
            + ")), (long)(_num("
            + visitExp(ce.args().get(1))
            + ")))";
      }
      if (base.equals("Math.random2f"))
        return "randomf(" + visitExp(ce.args().get(0)) + ", " + visitExp(ce.args().get(1)) + ")";
      if (base.equals("Math.randomf") || base.equals("randomf")) return "randomf()";
      if (base.equals("maybe") || base.equals("ChuckMath.maybe"))
        return "((long)(Math.random() * 2))";
      if (base.equals("ChuckMath.mtof") || base.equals("mtof"))
        return "mtof(_num(" + visitExp(ce.args().get(0)) + "))";
      if (base.equals("ftom")) {
        String a = visitExp(ce.args().get(0));
        return "(69.0 + (12.0 * (Math.log(_num(" + a + ") / 440.0) / Math.log(2.0))))";
      }
      if (base.equals("ChuckMath.ftom")) {
        String a = visitExp(ce.args().get(0));
        return "(69.0 + (12.0 * (Math.log(_num(" + a + ") / 440.0) / Math.log(2.0))))";
      }
      if (base.equals("ChuckMath.equal")) {
        String a = visitExp(ce.args().get(0));
        String b = visitExp(ce.args().get(1));
        return "(Math.abs(_num(" + a + ") - _num(" + b + ")) < 1e-9)";
      }
      if (base.equals("ChuckMath.isinf"))
        return "Double.isInfinite(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("ChuckMath.isnan")) return "Double.isNaN(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("ChuckMath.gauss")) {
        String x = visitExp(ce.args().get(0));
        String mu = visitExp(ce.args().get(1));
        String sigma = visitExp(ce.args().get(2));
        return "(Math.exp(-0.5 * Math.pow(((_num("
            + x
            + "))-(_num("
            + mu
            + ")))/(_num("
            + sigma
            + ")), 2.0)) / ((_num("
            + sigma
            + ")) * Math.sqrt(2.0 * Math.PI)))";
      }
      if (base.equals("advance") && ce.args().size() == 1) {
        String a = visitExp(ce.args().get(0));
        return "advance(_toDur(" + a + "))";
      }
      if (base.equals("ChuckMath.remap")) {
        String a = "_num(" + visitExp(ce.args().get(0)) + ")";
        String in0 = "_num(" + visitExp(ce.args().get(1)) + ")";
        String in1 = "_num(" + visitExp(ce.args().get(2)) + ")";
        String out0 = "_num(" + visitExp(ce.args().get(3)) + ")";
        String out1 = "_num(" + visitExp(ce.args().get(4)) + ")";
        return "(" + out0 + " + ((" + a + " - " + in0 + ") * (" + out1 + " - " + out0 + ") / ("
            + in1 + " - " + in0 + ")))";
      }
      if (base.equals("ChuckMath.map")) {
        String a = "_num(" + visitExp(ce.args().get(0)) + ")";
        String in0 = "_num(" + visitExp(ce.args().get(1)) + ")";
        String in1 = "_num(" + visitExp(ce.args().get(2)) + ")";
        String out0 = "_num(" + visitExp(ce.args().get(3)) + ")";
        String out1 = "_num(" + visitExp(ce.args().get(4)) + ")";
        return "(" + out0 + " + ((" + a + " - " + in0 + ") * (" + out1 + " - " + out0 + ") / ("
            + in1 + " - " + in0 + ")))";
      }
      if (base.equals("ChuckMath.clampf") && ce.args().size() == 3) {
        String x = visitExp(ce.args().get(0));
        String lo = visitExp(ce.args().get(1));
        String hi = visitExp(ce.args().get(2));
        return "Math.max(" + lo + ", Math.min(" + hi + ", " + x + "))";
      }
      if (base.equals("ChuckMath.map2") && ce.args().size() == 5) {
        String x = "_num(" + visitExp(ce.args().get(0)) + ")";
        String in0 = "_num(" + visitExp(ce.args().get(1)) + ")";
        String in1 = "_num(" + visitExp(ce.args().get(2)) + ")";
        String out0 = "_num(" + visitExp(ce.args().get(3)) + ")";
        String out1 = "_num(" + visitExp(ce.args().get(4)) + ")";
        return "("
            + out0
            + " + ((Math.max("
            + in0
            + ", Math.min("
            + in1
            + ", "
            + x
            + ")) - "
            + in0
            + ") * ("
            + out1
            + " - "
            + out0
            + ") / ("
            + in1
            + " - "
            + in0
            + ")))";
      }
      if (base.equals("ChuckMath.help")) return "_call(ChuckMath, \"help\")";
      if (base.equals("ChuckMath.srandom")) {
        return "_call(ChuckMath, \"srandom\", "
            + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", "))
            + ")";
      }
      if (base.equals("ChuckMath.randomize")) return "_call(ChuckMath, \"randomize\")";
      if (base.equals("ChuckMath.j")) return "new Complex(0, 1)";
      if (base.equals("ChuckMath.re") && ce.args().size() == 1)
        return "Complex.from(" + visitExp(ce.args().get(0)) + ").re";
      if (base.equals("ChuckMath.im") && ce.args().size() == 1)
        return "Complex.from(" + visitExp(ce.args().get(0)) + ").im";
      if (base.equals("ChuckMath.phase") && ce.args().size() == 1)
        return "Polar.from(" + visitExp(ce.args().get(0)) + ").phase";
      if (base.equals("ChuckMath.rtop")) {
        return "_call(ChuckMath, \"rtop\", "
            + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", "))
            + ")";
      }
      if (base.equals("ChuckMath.ptor")) {
        return "_call(ChuckMath, \"ptor\", "
            + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", "))
            + ")";
      }
      if (base.equals("Machine.replace") && ce.args().size() >= 2) {
        String idArg = visitExp(ce.args().get(0));
        if ("long".equals(typeOf(ce.args().get(0)))) {
          idArg = "(int)(" + idArg + ")";
        }
        return "Machine.replace(" + idArg + ", " + visitExp(ce.args().get(1)) + ")";
      }
      if (base.equals("Machine.remove") && ce.args().size() == 1) {
        String idArg = visitExp(ce.args().get(0));
        if ("long".equals(typeOf(ce.args().get(0)))) {
          idArg = "(int)(" + idArg + ")";
        }
        return "Machine.remove(" + idArg + ")";
      }

      if (base.equals("random") && ce.args().size() == 2) {
        return "(long)(Math.random() * ("
            + visitExp(ce.args().get(1))
            + " - "
            + visitExp(ce.args().get(0))
            + " + 1) + "
            + visitExp(ce.args().get(0))
            + ")";
      }
      if (base.equals("mtof")) {
        return "mtof(_num(" + visitExp(ce.args().get(0)) + "))";
      }
      if (base.endsWith(".div") && ce.args().size() == 1) {
        return base + "(_num(" + visitExp(ce.args().get(0)) + "))";
      }
      List<String> callArgs = null;
      if (ce.base() instanceof ChuckAST.IdExp idBase) {
        callArgs =
            coerceCallArgsForKnownFunction(
                currentClassName, normalizeFunctionName(idBase.name()), ce.args());
      }
      if (callArgs == null) {
        callArgs = ce.args().stream().map(this::visitExp).collect(Collectors.toList());
      }
      return base + "(" + String.join(", ", callArgs) + ")";
    } else if (exp instanceof ChuckAST.ArrayAccessExp aae) {
      String baseType = typeOf(aae.base());
      String baseCode = visitExp(aae.base());
      if ((baseType.endsWith("[]") || baseType.equals("ChuckArray")) && aae.indices().size() == 1) {
        String elemType = resolveArrayElementType(aae.base(), baseType);
        String idxCode = wrapInt(aae.indices().get(0));
        String idxType = typeOf(aae.indices().get(0));

        if (baseType.equals("ChuckArray")) {
          int depth = resolveArrayDepth(aae.base());
          if (depth > 1) {
            return "((ChuckArray)_call(" + baseCode + ", \"getObject\", " + idxCode + "))";
          }
          if ("String".equals(idxType)) {
            if (elemType.equals("String")) {
              return "(String)_call("
                  + baseCode
                  + ", \"getObject\", "
                  + visitExp(aae.indices().get(0))
                  + ")";
            }
            return "_call(" + baseCode + ", \"getObject\", " + visitExp(aae.indices().get(0)) + ")";
          }
          if (elemType.equals("double") || elemType.equals("float")) {
            return baseCode + ".getFloat(" + idxCode + ")";
          }
          if (elemType.equals("long") || elemType.equals("int")) {
            return baseCode + ".getInt(" + idxCode + ")";
          }
          if (elemType.equals("String")) {
            return "(String)_call(" + baseCode + ", \"getObject\", " + idxCode + ")";
          }
          if (!"Object".equals(elemType) && !isPrimitive(elemType)) {
            return "((" + elemType + ")_call(" + baseCode + ", \"getObject\", " + idxCode + "))";
          }
          return "_call(" + baseCode + ", \"getObject\", " + idxCode + ")";
        }

        if (elemType.equals("String"))
          return "(String)_call(" + baseCode + ", \"getObject\", " + idxCode + ")";
        if (elemType.equals("double") || elemType.equals("float")) {
          if ("String".equals(idxType)) {
            return "(((Object)"
                + baseCode
                + " instanceof ChuckArray) ? org.chuck.core.ChuckDSL.getFloat((ChuckArray)((Object)"
                + baseCode
                + "), "
                + visitExp(aae.indices().get(0))
                + ") : 0.0)";
          }
          return "(((Object)"
              + baseCode
              + " instanceof ChuckArray) ? ((ChuckArray)((Object)"
              + baseCode
              + ")).getFloat("
              + idxCode
              + ") : ((double)((double[])((Object)"
              + baseCode
              + "))[(int)("
              + idxCode
              + ")]))";
        }
        if (elemType.equals("long") || elemType.equals("int")) {
          if ("String".equals(idxType)) {
            return "(((Object)"
                + baseCode
                + " instanceof ChuckArray) ? org.chuck.core.ChuckDSL.getInt((ChuckArray)((Object)"
                + baseCode
                + "), "
                + visitExp(aae.indices().get(0))
                + ") : 0L)";
          }
          return "(((Object)"
              + baseCode
              + " instanceof ChuckArray) ? ((ChuckArray)((Object)"
              + baseCode
              + ")).getInt("
              + idxCode
              + ") : ((long)((long[])((Object)"
              + baseCode
              + "))[(int)("
              + idxCode
              + ")]))";
        }

        // Detect if this is an intermediate array level
        if (baseType.contains("[][]")) {
          return "((ChuckArray)_call(" + baseCode + ", \"getObject\", " + idxCode + "))";
        }
        if (!isPrimitive(baseType.replace("[]", ""))) {
          return "(("
              + baseType.replace("[]", "")
              + ")"
              + "_call("
              + baseCode
              + ", \"getObject\", "
              + idxCode
              + "))";
        }
        return "_call(" + baseCode + ", \"getObject\", " + idxCode + ")";
      }

      // Chained access: c[0][1]
      String res = visitExp(aae.base());
      boolean dynamicArray =
          "ChuckArray".equals(baseType)
              || res.contains("ChuckArray")
              || res.contains("getObject")
              || res.contains("_call(");
      for (int i = 0; i < aae.indices().size(); i++) {
        ChuckAST.Exp idx = aae.indices().get(i);
        String iCode = wrapInt(idx);
        boolean last = (i == aae.indices().size() - 1);
        if (dynamicArray) {
          String fetched = "_call(" + res + ", \"getObject\", " + iCode + ")";
          res = last ? fetched : "((ChuckArray)(" + fetched + "))";
          dynamicArray = !last;
        } else {
          res = res + "[(int)(" + visitExp(idx) + ")]";
        }
      }
      return res;
    } else if (exp instanceof ChuckAST.DotExp de) {
      String base = visitExp(de.base());
      String baseType = typeOf(de.base());
      String member = safeName(de.member());
      if ("\"Foo\"".equals(base) || "Foo".equals(base)) {
        if (member.equals("S_INT")) return "0L";
        if (member.equals("S_FLOAT")) return "0.0";
        if (member.equals("S_DUR")) return "1L";
        if (member.equals("S_TIME")) return "samp().times(0)";
        if (member.equals("S_VEC3")) return "_vec3(0, 0, 0)";
        if (member.equals("S_INT_ARRAY")) return "new long[]{}";
        if (member.equals("S_STRING")) return "\"\"";
        if (member.equals("S_SINOSC")) return "_new(SinOsc.class)";
      }
      if (isHidMsgType(baseType)) {
        if ("isHatMotion".equals(member)) {
          return "_callBool(" + base + ", \"isAxisMotion\")";
        }
        String hidMember = mapHidMember(member);
        if (hidMember != null) {
          member = hidMember;
        }
      }

      if (member.equals("newline")) return "ChIO.newline()";
      if (member.equals("nl")) return "ChIO.nl()";
      if (base.equals("Std") && member.equals("mtof")) return "__std_mtof_tmp";
      if (base.equals("Std") && member.equals("ftom")) return "__std_ftom_tmp";
      if ((base.equals("SerialIO") || base.endsWith(".SerialIO")) && member.equals("B9600"))
        return "9600";
      if ((base.equals("SerialIO") || base.endsWith(".SerialIO")) && member.equals("ASCII"))
        return "0";
      if ((base.equals("SerialIO") || base.endsWith(".SerialIO")) && member.equals("BINARY"))
        return "1";
      if ((base.equals("Hid") || base.endsWith(".Hid")) && member.equals("ACCELEROMETER"))
        return "0";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("READ")) return "org.chuck.core.FileIO.READ";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("WRITE")) return "org.chuck.core.FileIO.WRITE";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("APPEND")) return "org.chuck.core.FileIO.APPEND";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("BINARY")) return "org.chuck.core.FileIO.BINARY";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("ASCII")) return "org.chuck.core.FileIO.ASCII";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("INT8")) return "1";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("INT16")) return "2";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("INT32")) return "4";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("INT64")) return "8";
      if ((base.equals("org.chuck.core.ChuckIO") || base.equals("org.chuck.core.FileIO"))
          && member.equals("SINT16")) return "2";
      if (base.equals("Std") && member.equals("mtof")) return "mtof";
      if (base.equals("Std") && member.equals("ftom")) return "ftom";
      if (base.equals("Math") && member.startsWith("random")) return "Math." + member;
      if (base.equals("ChuckMath") && member.equals("maybe")) return "maybe";
      if (base.equals("ChuckMath") && member.equals("INFINITY")) return "Double.POSITIVE_INFINITY";
      if (base.equals("ChuckMath") && member.equals("j")) return "new Complex(0, 1)";

      // Known ChuckArray methods
      if ("ChuckArray".equals(baseType)
          || (baseType != null && baseType.endsWith("[]") && !baseType.startsWith("ChuckEvent"))) {
        if (member.equals("size") || member.equals("cap") || member.equals("length"))
          return "_sizeOf(" + base + ")";
        if (member.equals("popBack") || member.equals("popFront") || member.equals("erase"))
          return base + "." + member;
        if (member.equals("clear") || member.equals("getKeys")) return base + "." + member;
      }

      if ("Complex".equals(baseType) && (member.equals("mag") || member.equals("phase"))) {
        return member.equals("mag") ? base + ".magnitude()" : base + ".phase()";
      }
      if ("Polar".equals(baseType) && (member.equals("mag") || member.equals("phase"))) {
        return base + "." + member;
      }
      if (member.equals("size") || member.equals("cap") || member.equals("length")) {
        return "_sizeOf(" + base + ")";
      }

      // If it's a user class, assume field access unless we're in a CallExp
      if (member.equals("help") && userClasses.contains(base)) {
        return "new " + base + "().help()";
      }
      if ("super".equals(base)) {
        return "super." + member;
      }
      if (userClasses.contains(base)) {
        return "new " + base + "()." + member;
      }
      if (base.equals("me()") && member.equals("sourceDir"))
        return "(String)_call(me(), \"sourceDir\")";
      if ("ChuckUGen".equals(baseType)
          && (member.equals("left") || member.equals("right") || member.equals("chan"))) {
        long idx = member.equals("right") ? 1L : 0L;
        return "_ugenChan(" + base + ", " + idx + ")";
      }
      if ("BPM".equals(baseType)
          && (member.equals("quarterNote")
              || member.equals("halfNote")
              || member.equals("wholeNote")
              || member.equals("eighthNote")
              || member.equals("sixteenthNote"))) {
        return "_call(" + base + ", \"" + member + "\")";
      }
      if ("Object".equals(baseType) && (member.equals("num") || member.equals("n"))) {
        return "_callLong(" + base + ", \"" + member + "\")";
      }
      if (("double".equals(baseType)
              || "float".equals(baseType)
              || "long".equals(baseType)
              || "int".equals(baseType))
          && (member.equals("num") || member.equals("n"))) {
        return "(long)(_num(" + base + "))";
      }
      if (userClasses.contains(baseType) || userClasses.contains(base)) {
        return base + "." + member;
      }
      if (member.equals("data1")
          || member.equals("data2")
          || member.equals("data3")
          || member.equals("when")) {
        return base + "." + member;
      }

      // If member is all caps, it's likely a constant (e.g., IO.INT8, FileIO.READ)
      if (member.equals(member.toUpperCase()) && member.length() > 1) {
        return base + "." + member;
      }

      // If it's a UGen or built-in, it's likely a method call (freq, gain, etc.)
      if (isUGen(baseType) || baseType.equals("Object") || baseType.equals("ChuckUGen")) {
        if (member.equals("noteOff")) {
          return "_call(" + base + ", \"noteOff\", 1.0)";
        }
        if (member.equals("duration") || member.equals("broadcast")) {
          return "_call(" + base + ", \"" + member + "\")";
        }
        if (member.equals("db") || member.equals("strikePosition") || member.equals("slide")) {
          return "_callDouble(" + base + ", \"" + member + "\")";
        }
        return base + "." + member + "()";
      }

      return base + "." + member;
    } else if (exp instanceof ChuckAST.ArrayLitExp ale) {
      // Use ChuckArray for literals
      String elements =
          ale.elements().stream().map(this::visitExp).collect(Collectors.joining(", "));
      elements = elements.replaceAll("_CHUCK_SPECIAL_new_[0-9_]+", "_new(Object.class)");
      boolean looksLikeFloat = ale.elements().stream().anyMatch(e -> typeOf(e).equals("double"));
      boolean looksLikeString = ale.elements().stream().anyMatch(e -> typeOf(e).equals("String"));
      boolean looksLikeArray =
          ale.elements().stream().anyMatch(e -> typeOf(e).equals("ChuckArray"));

      String firstObjType = null;
      for (ChuckAST.Exp e : ale.elements()) {
        String t = typeOf(e);
        if (!t.equals("long")
            && !t.equals("double")
            && !t.equals("String")
            && !t.equals("ChuckArray")
            && !t.equals("int")
            && !t.equals("float")) {
          firstObjType = t;
          break;
        }
      }

      String baseType =
          firstObjType != null
              ? firstObjType
              : looksLikeString
                  ? "String"
                  : (looksLikeFloat ? "float" : (looksLikeArray ? "ChuckArray" : "int"));

      if (firstObjType != null) {
        return "_arrObj(\"" + baseType + "\", " + elements + ")";
      }
      String javaArrayType =
          looksLikeString
              ? "String[]"
              : (looksLikeFloat ? "double[]" : (looksLikeArray ? "ChuckArray[]" : "long[]"));

      return "new ChuckArray(\"" + baseType + "\", new " + javaArrayType + "{" + elements + "})";
    } else if (exp instanceof ChuckAST.VectorLitExp vle) {
      String elems = vle.elements().stream().map(this::visitExp).collect(Collectors.joining(", "));
      return switch (vle.elements().size()) {
        case 2 -> "_vec2(" + elems + ")";
        case 3 -> "_vec3(" + elems + ")";
        case 4 -> "_vec4(" + elems + ")";
        default -> "new double[]{" + elems + "}";
      };
    } else if (exp instanceof ChuckAST.SporkExp se) {
      return emitSporkExpression(se.call());
    } else if (exp instanceof ChuckAST.TernaryExp te) {
      String condCode = visitBoolExp(te.condition());
      return "(" + condCode + " ? " + visitExp(te.thenExp()) + " : " + visitExp(te.elseExp()) + ")";
    } else if (exp instanceof ChuckAST.ComplexLit cl) {
      return "new Complex(" + visitExp(cl.re()) + ", " + visitExp(cl.im()) + ")";
    } else if (exp instanceof ChuckAST.PolarLit pl) {
      return "new Polar(" + visitExp(pl.mag()) + ", " + visitExp(pl.phase()) + ")";
    } else if (exp instanceof ChuckAST.TypeofExp te) {
      return visitExp(te.expr()) + ".getClass().getSimpleName()";
    } else if (exp instanceof ChuckAST.InstanceofExp ie) {
      return "(" + visitExp(ie.expr()) + " instanceof " + mapType(ie.typeName()) + ")";
    } else if (exp instanceof ChuckAST.CastExp ce) {
      String type = mapType(ce.targetType());
      if (type.endsWith("[]") && !type.startsWith("ChuckEvent")) type = "ChuckArray";
      String valueCode = visitExp(ce.value());
      String valueType = typeOf(ce.value());
      if ("vec2".equals(type)) return "vec2.from(" + valueCode + ")";
      if ("vec3".equals(type)) return "vec3.from(" + valueCode + ")";
      if ("vec4".equals(type)) return "vec4.from(" + valueCode + ")";
      if (("long".equals(type) || "int".equals(type)) && "boolean".equals(valueType)) {
        return "((" + valueCode + ") ? 1L : 0L)";
      }
      if ("String".equals(type)) {
        return "String.valueOf(" + valueCode + ")";
      }
      if ("ChuckDuration".equals(type)) {
        return "_toDur(" + valueCode + ")";
      }
      if ("boolean".equals(type)) {
        return "_truthy(" + valueCode + ")";
      }
      if ("double".equals(type)) {
        return "_num(" + valueCode + ")";
      }
      if ("float".equals(type)) {
        return "((float)(_num(" + valueCode + ")))";
      }
      if ("long".equals(type) || "int".equals(type)) {
        if ("ChuckArray".equals(valueType) || valueCode.contains("ChuckArray")) {
          return "(long)(_sizeOf(" + valueCode + "))";
        }
        return "((" + type + ")(_num(" + valueCode + ")))";
      }
      if ("ChuckArray".equals(type)) {
        return "_toChuckArray(" + valueCode + ")";
      }
      if ("Polar".equals(type)
          && ("long".equals(valueType)
              || "int".equals(valueType)
              || "double".equals(valueType)
              || "float".equals(valueType)
              || "Object".equals(valueType))) {
        return "new Polar(_num(" + valueCode + "), 0)";
      }
      if ("Complex".equals(type)
          && ("long".equals(valueType)
              || "int".equals(valueType)
              || "double".equals(valueType)
              || "float".equals(valueType)
              || "Object".equals(valueType))) {
        return "new Complex(_num(" + valueCode + "), 0)";
      }
      if ("Polar".equals(type) && "Complex".equals(valueType)) {
        return "Polar.fromComplex(" + valueCode + ")";
      }
      if ("Complex".equals(type) && "Polar".equals(valueType)) {
        return "Complex.fromPolar(" + valueCode + ")";
      }
      return "((" + type + ")(" + valueCode + "))";
    } else if (exp instanceof ChuckAST.UnaryExp ue) {
      if (ue.op() == ChuckAST.Operator.LOGICAL_NOT) {
        String inner = visitExp(ue.exp());
        String innerType = typeOf(ue.exp());
        if ("boolean".equals(innerType)) return "!(" + inner + ")";
        if ("long".equals(innerType) || "int".equals(innerType)) return "(" + inner + " == 0)";
        if ("double".equals(innerType) || "float".equals(innerType))
          return "(" + inner + " == 0.0)";
        if ("ChuckDuration".equals(innerType)) return "(" + durationScalar(inner) + " == 0.0)";
        if (inner.startsWith("_callLong(")) return "(" + inner + " == 0)";
        return "(!_truthy(" + inner + "))";
      }
      String op =
          switch (ue.op()) {
            case MINUS -> "-";
            case PLUS_PLUS, POSTFIX_PLUS_PLUS -> "++";
            case MINUS_MINUS, POSTFIX_MINUS_MINUS -> "--";
            default -> ue.op().name();
          };
      boolean isIncDec = "++".equals(op) || "--".equals(op);
      String incDelta = "--".equals(op) ? "-1" : "1";
      if (isIncDec
          && ue.exp() instanceof ChuckAST.CallExp ce2
          && ce2.base() instanceof ChuckAST.DotExp de2
          && ce2.args().isEmpty()) {
        String baseCode = visitExp(de2.base());
        String member = de2.member();
        return "_chuckSet("
            + baseCode
            + ", \""
            + member
            + "\", (_num(_call("
            + baseCode
            + ", \""
            + member
            + "\")) + "
            + incDelta
            + "))";
      }
      if (isIncDec && ue.exp() instanceof ChuckAST.DotExp de3) {
        String baseCode = visitExp(de3.base());
        String member = de3.member();
        return "_chuckSet("
            + baseCode
            + ", \""
            + member
            + "\", (_num(_call("
            + baseCode
            + ", \""
            + member
            + "\")) + "
            + incDelta
            + "))";
      }
      if (ue.isPostfix()) {
        if (ue.exp() instanceof ChuckAST.CallExp ce
            && ce.base() instanceof ChuckAST.DotExp de
            && ce.args().size() == 1
            && (de.member().equals("getInt") || de.member().equals("getFloat"))) {
          String baseCode = visitExp(de.base());
          String idxCode = wrapInt(ce.args().get(0));
          String delta =
              (ue.op() == ChuckAST.Operator.MINUS_MINUS
                      || ue.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS)
                  ? "-1"
                  : "1";
          return "_call("
              + baseCode
              + ", \"setObject\", "
              + idxCode
              + ", (_callDouble("
              + baseCode
              + ", \""
              + de.member()
              + "\", "
              + idxCode
              + ") + "
              + delta
              + "))";
        }
        if (ue.exp() instanceof ChuckAST.ArrayAccessExp aae) {
          String baseCode = visitExp(aae.base());
          for (int i = 0; i < aae.indices().size() - 1; i++) {
            String iCode = wrapInt(aae.indices().get(i));
            baseCode = "((ChuckArray)_call(" + baseCode + ", \"getObject\", " + iCode + "))";
          }
          String idxCode = wrapInt(aae.indices().get(aae.indices().size() - 1));
          String delta =
              (ue.op() == ChuckAST.Operator.MINUS_MINUS
                      || ue.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS)
                  ? "-1"
                  : "1";
          String elemType = typeOf(aae);
          if ("long".equals(elemType) || "int".equals(elemType)) {
            String arr = "((ChuckArray)(" + baseCode + "))";
            return "_call("
                + baseCode
                + ", \"setInt\", "
                + idxCode
                + ", (long)("
                + arr
                + ".getInt("
                + idxCode
                + ") + "
                + delta
                + "))";
          }
          if ("double".equals(elemType) || "float".equals(elemType)) {
            String arr = "((ChuckArray)(" + baseCode + "))";
            return "_call("
                + baseCode
                + ", \"setFloat\", "
                + idxCode
                + ", ("
                + arr
                + ".getFloat("
                + idxCode
                + ") + "
                + delta
                + "))";
          }
          return "_call("
              + baseCode
              + ", \"setObject\", "
              + idxCode
              + ", (_num(_call("
              + baseCode
              + ", \"getObject\", "
              + idxCode
              + ")) + "
              + delta
              + "))";
        }
        if (ue.exp() instanceof ChuckAST.BinaryExp be
            && (be.op() == ChuckAST.Operator.TIMES
                || be.op() == ChuckAST.Operator.DIVIDE
                || be.op() == ChuckAST.Operator.PLUS
                || be.op() == ChuckAST.Operator.MINUS)
            && be.rhs() instanceof ChuckAST.IdExp) {
          return "("
              + visitExp(be.lhs())
              + " "
              + mapOp(be.op())
              + " "
              + visitExp(be.rhs())
              + op
              + ")";
        }
        String target = visitExp(ue.exp());
        if (isIncDec
            && (target.startsWith("_call(")
                || target.startsWith("_callLong(")
                || target.startsWith("_sizeOf(")
                || target.startsWith("_chuckWrite("))) {
          return target;
        }
        if (isIncDec && "Object".equals(typeOf(ue.exp())) && ue.exp() instanceof ChuckAST.IdExp) {
          return target + " = (_num(" + target + ") + " + incDelta + ")";
        }
        return target + op;
      }
      String target = visitExp(ue.exp());
      if (isIncDec
          && (target.startsWith("_call(")
              || target.startsWith("_callLong(")
              || target.startsWith("_sizeOf(")
              || target.startsWith("_chuckWrite("))) {
        return target;
      }
      if (isIncDec && "Object".equals(typeOf(ue.exp())) && ue.exp() instanceof ChuckAST.IdExp) {
        return target + " = (_num(" + target + ") + " + incDelta + ")";
      }
      return op + target;
    } else if (exp instanceof ChuckAST.DeclExp de) {
      String rawType = mapType(de.type());
      String type = rawType;
      String safe = safeName(de.name());
      if (safe.startsWith("_CHUCK_SPECIAL_new_")
          && !isPrimitive(type)
          && !"String".equals(type)
          && !"Object".equals(type)) {
        return "_new(" + type + ".class)";
      }
      if (safe.startsWith("_CHUCK_SPECIAL_new_")) {
        return "_new(Object.class)";
      }
      if (de.arraySizes() != null && !de.arraySizes().isEmpty()) {
        if (type.startsWith("ChuckEvent")) {
          type = "ChuckEvent[]";
        } else {
          type = "ChuckArray";
        }
      }
      varTypes.put(safe, type);
      if (de.arraySizes() != null && !de.arraySizes().isEmpty()) {
        arrayElementTypes.put(safe, normalizeArrayElementType(rawType));
        arrayDepths.put(safe, de.arraySizes().size());
      }
      return safe;
    }
    return "// Unsupported expression: " + exp.getClass().getSimpleName();
  }

  private String typeOf(ChuckAST.Exp exp) {
    if (exp instanceof ChuckAST.IdExp id) {
      if (id.name().endsWith(".length")) return "long";
      if (id.name().equals("now")) return "ChuckDuration";
      if (id.name().equals("me")) return "ChuckShred";
      if (id.name().startsWith("_CHUCK_SPECIAL_new_array_")) return "ChuckArray";
      if (id.name().equals("samp")
          || id.name().equals("ms")
          || id.name().equals("second")
          || id.name().equals("minute")
          || id.name().equals("hour")
          || id.name().equals("day")
          || id.name().equals("week")) {
        return "ChuckDuration";
      }
      if ("cherr".equals(id.name()) || "chout".equals(id.name())) return "ChuckIO";
      if ("Type".equals(id.name())) return "Type";
      String type = varTypes.get(id.name());
      if (type != null) {
        return type;
      }
      // Check fields for global variables
      for (ChuckAST.DeclStmt field : fields) {
        if (field.name().equals(id.name())) {
          if (field.arraySizes() != null && !field.arraySizes().isEmpty()) {
            String ft = mapType(field.type());
            if (ft.startsWith("ChuckEvent")) return "ChuckEvent[]";
            return "ChuckArray";
          }
          return mapType(field.type());
        }
      }
      if (id.name().equals("dac") || id.name().equals("adc")) return "ChuckUGen";
      return "Object";
    }
    if (exp instanceof ChuckAST.IntExp) return "long";
    if (exp instanceof ChuckAST.FloatExp) return "double";
    if (exp instanceof ChuckAST.StringExp) return "String";
    if (exp instanceof ChuckAST.CastExp ce) {
      String type = mapType(ce.targetType());
      if (type.endsWith("[]") && !type.startsWith("ChuckEvent")) return "ChuckArray";
      return type;
    }
    if (exp instanceof ChuckAST.ComplexLit) return "Complex";
    if (exp instanceof ChuckAST.PolarLit) return "Polar";
    if (exp instanceof ChuckAST.VectorLitExp ve) {
      int n = ve.elements() == null ? 0 : ve.elements().size();
      if (n == 2) return "vec2";
      if (n == 3) return "vec3";
      if (n == 4) return "vec4";
      return "Object";
    }
    if (exp instanceof ChuckAST.ArrayLitExp) return "ChuckArray";
    if (exp instanceof ChuckAST.DotExp de) {
      String baseType = typeOf(de.base());
      String member = de.member();
      String memberSafe = safeName(member);
      if (member.equals("size") || member.equals("cap") || member.equals("length")) {
        return "long";
      }
      if (isHidMsgType(baseType)) {
        String hidMember = mapHidMember(member);
        if ("x".equals(hidMember)
            || "y".equals(hidMember)
            || "ascii".equals(hidMember)
            || "scaledCursorX".equals(member)
            || "scaledCursorY".equals(member)
            || "axisPosition".equals(member)
            || "deltaX".equals(member)
            || "deltaY".equals(member)
            || "z".equals(member)) {
          return "double";
        }
        if ("which".equals(hidMember) || "type".equals(hidMember) || "idata".equals(member)) {
          return "long";
        }
        if ("isHatMotion".equals(member)) {
          return "boolean";
        }
      }
      if ("MidiMsg".equals(baseType)) {
        if (member.equals("data1") || member.equals("data2") || member.equals("data3"))
          return "long";
        if (member.equals("when")) return "double";
      }
      if ("Type".equals(baseType) && (member.equals("name") || member.equals("origin"))) {
        return "String";
      }
      String userFieldType = resolveUserClassFieldType(baseType, memberSafe);
      if (userFieldType != null) {
        return userFieldType;
      }
    }
    if (exp instanceof ChuckAST.ArrayAccessExp aae) {
      String baseType = typeOf(aae.base());
      if ("ChuckArray".equals(baseType)) {
        String elemType = resolveArrayElementType(aae.base(), baseType);
        return elemType == null ? "Object" : elemType;
      }
      if (baseType.endsWith("[]")) {
        String elemType = baseType;
        for (int i = 0; i < aae.indices().size(); i++) {
          if (!elemType.endsWith("[]")) break;
          elemType = elemType.substring(0, elemType.length() - 2);
        }
        if (elemType.endsWith("[]")) return "ChuckArray";
        return elemType;
      }
      return "Object";
    }
    if (exp instanceof ChuckAST.BinaryExp be) {
      if (be.op() == ChuckAST.Operator.EQ
          || be.op() == ChuckAST.Operator.NEQ
          || be.op() == ChuckAST.Operator.LT
          || be.op() == ChuckAST.Operator.LE
          || be.op() == ChuckAST.Operator.GT
          || be.op() == ChuckAST.Operator.GE
          || be.op() == ChuckAST.Operator.AND
          || be.op() == ChuckAST.Operator.OR) {
        return "boolean";
      }
      if (be.op() == ChuckAST.Operator.DUR_MUL) return "ChuckDuration";
      if (be.op() == ChuckAST.Operator.PLUS
          || be.op() == ChuckAST.Operator.MINUS
          || be.op() == ChuckAST.Operator.TIMES
          || be.op() == ChuckAST.Operator.DIVIDE) {
        String lType = typeOf(be.lhs());
        String rType = typeOf(be.rhs());
        if (lType.equals("ChuckDuration")
            && rType.equals("ChuckDuration")
            && be.op() == ChuckAST.Operator.DIVIDE) return "double";
        if (lType.equals("ChuckDuration") || rType.equals("ChuckDuration")) return "ChuckDuration";
        if (lType.equals("String") || rType.equals("String")) {
          if (be.op() == ChuckAST.Operator.PLUS) return "String";
          return "Object";
        }
        if (lType.equals("Complex") || rType.equals("Complex")) return "Complex";
        if (lType.equals("Polar") || rType.equals("Polar")) return "Polar";
        if (lType.startsWith("vec") || rType.startsWith("vec")) return "Object";
        if (lType.equals("double") || rType.equals("double")) return "double";
        if (lType.equals("float") || rType.equals("float")) return "double";
        if (lType.equals("Object") || rType.equals("Object")) return "double";
        return "long";
      }
      if (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK) {
        return typeOf(be.rhs());
      }
      return typeOf(be.lhs());
    }
    if (exp instanceof ChuckAST.UnaryExp ue) {
      if (ue.op() == ChuckAST.Operator.LOGICAL_NOT) return "boolean";
      return typeOf(ue.exp());
    }
    if (exp instanceof ChuckAST.DeclExp de) {
      if (!de.arraySizes().isEmpty()) return "ChuckArray";
      return mapType(de.type());
    }
    if (exp instanceof ChuckAST.CallExp ce) {
      String base = visitExp(ce.base());
      if (base.contains("Math.sin")
          || base.contains("Math.cos")
          || base.contains("Math.sqrt")
          || base.contains("Math.random")
          || base.contains("mtof")
          || base.contains("ftom")) {
        return "double";
      }
      if ("_vec2".equals(base)) return "vec2";
      if ("_vec3".equals(base)) return "vec3";
      if ("_vec4".equals(base)) return "vec4";
      if (base.endsWith(".dir")) return "String";
      if (base.equals("Polar.fromComplex") || base.equals("Polar.from")) return "Polar";
      if (base.equals("Complex.fromPolar") || base.equals("Complex.from")) return "Complex";

      // If it's a duration method, check if it's a ratio (div)
      if (ce.base() instanceof ChuckAST.DotExp de) {
        String recType = typeOf(de.base());
        String member = de.member();
        if ("ChuckArray".equals(recType)) {
          if (member.equals("getInt")) return "long";
          if (member.equals("getFloat")) return "double";
          if (member.equals("size") || member.equals("cap")) return "long";
        }
        if ("String".equals(recType)) {
          if (member.equals("find") || member.equals("rfind")) return "long";
          if (member.equals("setCharAt")) return "String";
        }
        if ("MidiFileOut".equals(recType) && member.equals("addTrack")) return "long";
        if ("FFT".equals(recType) && member.equals("cval")) return "Complex";
        if ("FFT".equals(recType) && member.equals("upchuck")) return "UAnaBlob";
        if ("ChuckShred".equals(recType) && member.equals("args")) return "String[]";
        if ("UAnaBlob".equals(recType)) {
          if (member.equals("fvals") || member.equals("cvals")) return "ChuckArray";
          if (member.equals("fval")) return "double";
        }
        if ("MidiMsg".equals(recType)) {
          if (member.equals("data1") || member.equals("data2") || member.equals("data3")) {
            return "long";
          }
          if (member.equals("when")) {
            return "double";
          }
        }
        if ("ChuckDuration".equals(recType)) {
          if (de.member().equals("div")
              && ce.args().size() > 0
              && "ChuckDuration".equals(typeOf(ce.args().get(0)))) {
            return "double";
          }
          return "ChuckDuration";
        }
        if (member.equals("quarterNote")
            || member.equals("halfNote")
            || member.equals("wholeNote")
            || member.equals("eighthNote")
            || member.equals("sixteenthNote")
            || member.equals("thirtysecondNote")) {
          return "ChuckDuration";
        }
      }

      if (base.endsWith(".plus")
          || base.endsWith(".minus")
          || base.endsWith(".times")
          || base.endsWith(".div")
          || base.endsWith(".percent")) {
        return "ChuckDuration"; // Fallback
      }
      return "Object";
    }
    return "Object";
  }

  private String resolveArrayElementType(ChuckAST.Exp baseExp, String baseType) {
    if (baseType != null && baseType.endsWith("[]")) {
      return baseType.substring(0, baseType.length() - 2);
    }
    if (baseExp instanceof ChuckAST.IdExp id) {
      String elem = arrayElementTypes.get(safeName(id.name()));
      if (elem != null) return elem;
    }
    return "Object";
  }

  private String normalizeArrayElementType(String type) {
    String t = type;
    while (t.endsWith("[]")) t = t.substring(0, t.length() - 2);
    return t;
  }

  private int resolveArrayDepth(ChuckAST.Exp baseExp) {
    if (baseExp instanceof ChuckAST.IdExp id) {
      Integer depth = arrayDepths.get(safeName(id.name()));
      if (depth != null) return depth;
    }
    return 1;
  }

  private void flattenLogical(ChuckAST.Exp exp, List<String> result) {
    if (exp instanceof ChuckAST.LogicalExp le) {
      flattenLogical(le.lhs(), result);
      flattenLogical(le.rhs(), result);
    } else {
      result.add(visitExp(exp));
    }
  }

  private String functionScopeKey(String scope, String name) {
    return (scope == null ? "__root__" : scope) + "|" + name;
  }

  private void registerFunctionSignature(String scope, String name, List<String> rawArgTypes) {
    if (name == null) return;
    List<String> mapped = new ArrayList<>();
    for (String raw : rawArgTypes) {
      String t = mapType(raw);
      if (t.endsWith("[]") && !t.startsWith("ChuckEvent")) t = "ChuckArray";
      mapped.add(t);
    }
    String key = functionScopeKey(scope, name);
    List<List<String>> existing = functionSignatures.computeIfAbsent(key, k -> new ArrayList<>());
    if (!existing.contains(mapped)) existing.add(mapped);
  }

  private List<String> coerceCallArgsForKnownFunction(
      String scope, String name, List<ChuckAST.Exp> args) {
    List<List<String>> candidates = functionSignatures.get(functionScopeKey(scope, name));
    if (candidates == null || candidates.isEmpty()) return null;
    int arity = args.size();
    List<String> best = null;
    int bestScore = Integer.MIN_VALUE;
    for (List<String> c : candidates) {
      if (c.size() != arity) continue;
      int score = 0;
      for (int i = 0; i < arity; i++) {
        String src = typeOf(args.get(i));
        String target = c.get(i);
        if (target.equals(src)) score += 3;
        else if (isPrimitive(target) && isPrimitive(src)) score += 1;
      }
      if (score > bestScore) {
        bestScore = score;
        best = c;
      }
    }
    if (best == null) return null;
    List<String> out = new ArrayList<>();
    for (int i = 0; i < arity; i++) {
      String argCode = visitExp(args.get(i));
      String srcType = typeOf(args.get(i));
      out.add(coerceToTypeExpr(argCode, srcType, best.get(i)));
    }
    return out;
  }

  private String safeName(String name) {
    if (name == null) return null;
    String safe = name.replace("@", "_CHUCK_SPECIAL_");
    if ("byte".equals(safe)) return "byte_";
    return safe;
  }

  private String normalizeFunctionName(String rawName) {
    if (rawName == null) return null;
    if ("assert".equals(rawName)) return "_CHUCK_INTERNAL_ASSERT_";
    if ("wait".equals(rawName)) return "wait_";
    String name = safeName(rawName);
    String prefix = null;
    if (name.startsWith("__pub_op__")) {
      prefix = "__pub_op__";
    } else if (name.startsWith("__op__")) {
      prefix = "__op__";
    }
    if (prefix != null) {
      String suffix = name.substring(prefix.length());
      return prefix + normalizeOperatorSuffix(suffix);
    }
    return name.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private String normalizeOperatorSuffix(String suffix) {
    return switch (suffix) {
      case "+" -> "plus";
      case "-" -> "minus";
      case "*" -> "times";
      case "/" -> "div";
      case "%" -> "mod";
      case "!" -> "not";
      case "++" -> "inc";
      case "--" -> "dec";
      case "=>" -> "chuck";
      case "=^" -> "upchuck";
      case "==" -> "eq";
      case "!=" -> "neq";
      case "<" -> "lt";
      case "<=" -> "le";
      case ">" -> "gt";
      case ">=" -> "ge";
      case "&&" -> "and";
      case "||" -> "or";
      case "&" -> "band";
      case "|" -> "bor";
      case "^" -> "bxor";
      case "<<" -> "shl";
      case ">>" -> "shr";
      default -> suffix.replaceAll("[^A-Za-z0-9_]", "_");
    };
  }

  private boolean isHidMsgType(String type) {
    return "HidMsg".equals(type) || "org.chuck.hid.HidMsg".equals(type);
  }

  private String resolveUserClassFieldType(String className, String member) {
    if (className == null || member == null) return null;
    String safeClass = safeName(className);
    String safeMember = safeName(member);
    Map<String, String> members = classFieldTypes.get(safeClass);
    if (members != null) {
      String direct = members.get(safeMember);
      if (direct != null) return direct;
    }
    String parent = userClassParents.get(safeClass);
    if (parent != null && !parent.equals(safeClass)) {
      return resolveUserClassFieldType(parent, safeMember);
    }
    return null;
  }

  private boolean hasFieldOnType(String type, String member) {
    return resolveUserClassFieldType(type, member) != null;
  }

  private boolean isLikelyStaticNamespace(ChuckAST.Exp exp) {
    if (!(exp instanceof ChuckAST.IdExp id) || id.name() == null || id.name().isEmpty()) {
      return false;
    }
    String safe = safeName(id.name());
    if (varTypes.containsKey(safe)) return false;
    if (fields.stream().anyMatch(f -> safeName(f.name()).equals(safe))) return false;
    if (userClasses.contains(safe)) return false;
    return Character.isUpperCase(id.name().charAt(0));
  }

  private String mapHidMember(String member) {
    return switch (member) {
      case "deltaX", "axisPosition", "scaledCursorX" -> "x";
      case "deltaY", "scaledCursorY" -> "y";
      case "z" -> "ascii";
      case "idata" -> "which";
      default -> null;
    };
  }

  private String mapType(String type) {
    if (type == null) return "Object";
    String suffix = "";
    while (type.endsWith("[]")) {
      suffix += "[]";
      type = type.substring(0, type.length() - 2);
    }

    String mapped =
        switch (type) {
          case "int" -> "long";
          case "float" -> "double";
          case "dur" -> "ChuckDuration";
          case "time" -> "ChuckDuration";
          case "Event" -> "ChuckEvent";
          case "string" -> "String";
          case "auto" -> "Object"; // Map auto to Object
          case "OscIn" -> "OscIn";
          case "OscOut" -> "OscOut";
          case "OscMsg" -> "OscMsg";
          case "OscEvent" -> "OscEvent";
          case "IO" -> "Object";
          case "Hid" -> "org.chuck.hid.Hid";
          case "HidMsg" -> "org.chuck.hid.HidMsg";
          case "complex" -> "Complex";
          case "polar" -> "Polar";
          case "UGen" -> "ChuckUGen";
          case "BPM" -> "BPM";
          case "Foo" -> "Object";
          case "MandoPlayer" -> "Object";
          case "RollOff" -> "Rolloff";
          case "PitchTrack" -> "Object";
          case "Sigmund" -> "Object";
          case "Chubgraph" -> "Chugraph";
          case "Gain" -> "ChuckUGen";
          case "ADSR" -> "Adsr";
          case "StkInstrument" -> "ChuckUGen";
          case "Dinky" -> "Object";
          case "Multicomb" -> "Object";
          case "Elliptic" -> "Object";
          case "AmbPan3" -> "Object";
          case "AmbisonicEncoder" -> "Object";
          case "AmbisonicDecoder" -> "Object";
          case "ABSaturator" -> "Object";
          case "Spectacle" -> "Object";
          case "KSChord" -> "Object";
          case "Smacking" -> "Object";
          default -> type;
        };
    return mapped + suffix;
  }

  private String wrapInt(ChuckAST.Exp exp) {
    String code = visitExp(exp);
    String type = typeOf(exp);
    if ("String".equals(type)) return code; // Don't cast string to int
    return "(int)(" + code + ")";
  }

  private String coerceToTypeExpr(String code, String sourceType, String targetType) {
    if (targetType == null || "Object".equals(targetType)) return code;
    if ("vec2".equals(targetType)) return "vec2.from(" + code + ")";
    if ("vec3".equals(targetType)) return "vec3.from(" + code + ")";
    if ("vec4".equals(targetType)) return "vec4.from(" + code + ")";
    if ("String".equals(targetType)) return "String.valueOf(" + code + ")";
    if ("ChuckDuration".equals(targetType)) return "_toDur(" + code + ")";
    if ("boolean".equals(targetType)) return "_truthy(" + code + ")";
    if ("double".equals(targetType)) return "_num(" + code + ")";
    if ("float".equals(targetType)) return "((float)(_num(" + code + ")))";
    if ("long".equals(targetType) || "int".equals(targetType)) {
      if ("boolean".equals(sourceType)) return "((" + code + ") ? 1L : 0L)";
      if ("ChuckArray".equals(sourceType)
          || code.contains("ChuckArray")
          || code.contains("_toChuckArray(")) {
        return "(long)(_sizeOf(" + code + "))";
      }
      return "((" + targetType + ")(_num(" + code + ")))";
    }
    if (targetType.endsWith("[]") || "ChuckArray".equals(targetType)) {
      if ("ChuckArray".equals(targetType)) return "_toChuckArray(" + code + ")";
      if (!targetType.startsWith("ChuckEvent")) return "_toChuckArray(" + code + ")";
      return "((" + targetType + ")(" + code + "))";
    }
    if ("Polar".equals(targetType)
        && ("long".equals(sourceType)
            || "int".equals(sourceType)
            || "double".equals(sourceType)
            || "float".equals(sourceType)
            || "Object".equals(sourceType))) {
      return "new Polar(_num(" + code + "), 0)";
    }
    if ("Complex".equals(targetType)
        && ("long".equals(sourceType)
            || "int".equals(sourceType)
            || "double".equals(sourceType)
            || "float".equals(sourceType)
            || "Object".equals(sourceType))) {
      return "new Complex(_num(" + code + "), 0)";
    }
    return "((" + targetType + ")(" + code + "))";
  }

  private String normalizeChuckSetRhs(String rhsCode, String rhsType) {
    if (rhsCode != null && rhsCode.contains("(float)(")) {
      return "(Object)Double.valueOf(_num(" + rhsCode + "))";
    }
    if (rhsType == null) return rhsCode;
    if ("long".equals(rhsType)
        || "int".equals(rhsType)
        || "double".equals(rhsType)
        || "float".equals(rhsType)
        || "boolean".equals(rhsType)) {
      return "(Object)Double.valueOf(_num(" + rhsCode + "))";
    }
    return rhsCode;
  }

  private String postProcessGeneratedCode(String code) {
    String out = code.replace("ChuckMath.maybe()", "((long)(Math.random() * 2))");
    out =
        out.replaceAll(
            "(\\b_CHUCK_SPECIAL_new_array_[A-Za-z0-9_]+\\s*=\\s*)\\(long\\)\\(\\(\\(ChuckArray\\)_call\\(([^;]*)\\)\\)\\)",
            "$1((ChuckArray)_call($2))");
    return out;
  }

  private String ensureFunctionFallbackReturn(String bodyCode, String retType) {
    if (bodyCode == null) return null;
    if ("void".equals(retType)) return bodyCode;
    if (bodyCode.contains("return ")) return bodyCode;
    String fallback =
        switch (retType) {
          case "long", "int" -> "return 0L;";
          case "double", "float" -> "return 0.0;";
          case "boolean" -> "return false;";
          case "String" -> "return \"\";";
          case "ChuckDuration" -> "return samp().times(0);";
          default -> "return null;";
        };
    int end = bodyCode.lastIndexOf('}');
    if (end <= 0) return bodyCode;
    String before = bodyCode.substring(0, end).stripTrailing();
    String indent = before.endsWith("{") ? "  " : "  ";
    return before + "\n" + indent + fallback + "\n" + bodyCode.substring(end);
  }

  private String declPrefixFor(ChuckAST.Exp rhsExp, String rhsType) {
    if (!(rhsExp instanceof ChuckAST.DeclExp de)) return "";
    if (rhsType == null || rhsType.isBlank()) return "";
    String safe = safeName(de.name());
    if (currentFunctionLocals != null) {
      if (currentFunctionLocals.contains(safe)) return "";
      currentFunctionLocals.add(safe);
    }
    return rhsType + " ";
  }

  private String toTypedArrayLiteral(ChuckAST.ArrayLitExp ale, String targetArrayType) {
    String elemType = targetArrayType.substring(0, targetArrayType.length() - 2);
    String values =
        ale.elements().stream()
            .map(
                e -> {
                  String v = visitExp(e);
                  if ("long".equals(elemType) || "int".equals(elemType)) {
                    if ("ChuckArray".equals(typeOf(e)) || v.contains("ChuckArray")) {
                      return "(long)(_sizeOf(" + v + "))";
                    }
                    return "(long)(" + v + ")";
                  }
                  if ("double".equals(elemType) || "float".equals(elemType)) {
                    return "(double)(" + v + ")";
                  }
                  return "(" + elemType + ")(" + v + ")";
                })
            .collect(Collectors.joining(", "));
    return "new " + elemType + "[]{" + values + "}";
  }

  private String mapOp(ChuckAST.Operator op) {
    return switch (op) {
      case PLUS -> "+";
      case MINUS -> "-";
      case TIMES -> "*";
      case DIVIDE -> "/";
      case EQ -> "==";
      case NEQ -> "!=";
      case LT -> "<";
      case LE -> "<=";
      case GT -> ">";
      case GE -> ">=";
      case AND -> "&&";
      case OR -> "||";
      case S_OR -> "|";
      case S_AND -> "&";
      case PERCENT -> "%";
      case PLUS_CHUCK -> "+=";
      case MINUS_CHUCK -> "-=";
      case TIMES_CHUCK -> "*=";
      case DIVIDE_CHUCK -> "/=";
      case PERCENT_CHUCK -> "%=";
      case SHIFT_LEFT -> "<<";
      case SHIFT_RIGHT -> ">>";
      default -> op.name();
    };
  }

  private String ensureBraces(String s) {
    if (s.startsWith("{")) return s;
    return "{\n" + indent(s, 1) + "\n}";
  }

  private String indent(String s, int levels) {
    if (s == null) return null;
    String prefix = "    ".repeat(levels);
    return prefix + s.replace("\n", "\n" + prefix);
  }

  private boolean isEvent(String operand) {
    String type = varTypes.get(operand);
    if (type == null) return false;
    return type.startsWith("ChuckEvent") || type.contains("Event");
  }

  private boolean isDur(ChuckAST.Exp exp) {
    if (exp instanceof ChuckAST.CallExp ce && ce.base() instanceof ChuckAST.DotExp de) {
      String member = de.member();
      if (member.equals("quarterNote")
          || member.equals("halfNote")
          || member.equals("wholeNote")
          || member.equals("eighthNote")
          || member.equals("sixteenthNote")
          || member.equals("thirtysecondNote")) {
        return true;
      }
    }
    String type = typeOf(exp);
    return "ChuckDuration".equals(type);
  }

  private boolean looksLikeDurationCode(String code) {
    if (code == null) return false;
    return code.endsWith(".quarterNote()")
        || code.endsWith(".halfNote()")
        || code.endsWith(".wholeNote()")
        || code.endsWith(".eighthNote()")
        || code.endsWith(".sixteenthNote()")
        || code.endsWith(".thirtysecondNote()")
        || code.startsWith("_toDur(")
        || "samp()".equals(code)
        || "ms()".equals(code)
        || "second()".equals(code)
        || code.contains("samp().times(")
        || code.contains("ms().times(")
        || code.contains("second().times(");
  }

  private String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private String durationScalar(String code) {
    if ("now()".equals(code)) return code;
    return code + ".samples()";
  }

  private boolean isPrimitive(String type) {
    return type.equals("long")
        || type.equals("double")
        || type.equals("ChuckDuration")
        || type.equals("boolean");
  }

  private boolean isUGen(String type) {
    if (type == null || type.isEmpty()) return false;
    if (isPrimitive(type)
        || type.equals("Object")
        || type.equals("String")
        || type.equals("ChuckArray")
        || type.equals("ChuckEvent")
        || type.equals("ChuckEvent[]")
        || type.equals("Complex")
        || type.equals("Polar")
        || type.equals("vec2")
        || type.equals("vec3")
        || type.equals("vec4")) return false;
    if (userClasses.contains(type)) return false;
    // Assume any TitleCase identifier that isn't a known non-UGen is a UGen/Instrument
    return Character.isUpperCase(type.charAt(0));
  }

  private boolean isConnectionTargetType(String type) {
    if (type == null || type.isEmpty()) return false;
    if (type.equals("ChuckArray") || type.endsWith("[]")) return false;
    if (isPrimitive(type) || type.equals("Object") || type.equals("String")) return false;
    if (isUGen(type)) return true;
    String parent = userClassParents.get(type);
    if (parent == null) return false;
    return isConnectionTargetType(parent);
  }
}
