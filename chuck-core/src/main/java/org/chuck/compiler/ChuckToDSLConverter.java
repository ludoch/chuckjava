package org.chuck.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Converts a ChucK AST into Java DSL source code. */
public class ChuckToDSLConverter {

  private final Set<String> globals = new HashSet<>();
  private final Set<String> userClasses = new HashSet<>();
  private final Map<String, String> userClassParents = new HashMap<>();
  private final Set<String> userFunctions = new HashSet<>();
  private final Map<String, String> varTypes = new HashMap<>();
  private final Map<String, Integer> methodNameCounts = new HashMap<>();
  private final List<ChuckAST.DeclStmt> arraysToInit = new ArrayList<>();
  private final List<ChuckAST.DeclStmt> fields = new ArrayList<>();
  private boolean isFieldMode = false;
  private String currentClassName = null;

  public String convert(List<ChuckAST.Stmt> program, String className) {
    globals.clear();
    userClasses.clear();
    userClassParents.clear();
    userFunctions.clear();
    methodNameCounts.clear();
    varTypes.clear();
    arraysToInit.clear();
    fields.clear();
    currentClassName = className;

    for (ChuckAST.Stmt s : program) {
      if (s instanceof ChuckAST.DeclStmt ds) {
        String type = mapType(ds.type());
        varTypes.put(safeName(ds.name()), type);
        if (ds.isGlobal()) globals.add(safeName(ds.name()));
      }
      if (s instanceof ChuckAST.ClassDefStmt cds) {
        userClasses.add(safeName(cds.name()));
        if (cds.parentName() != null) {
          userClassParents.put(safeName(cds.name()), safeName(cds.parentName()));
        }
      }
      if (s instanceof ChuckAST.FuncDefStmt fds) {
        userFunctions.add(normalizeFunctionName(fds.name()));
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
    sb.append(indent("throw new RuntimeException(\"Cannot construct \" + cls.getName());", 2))
        .append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static ChuckUGen _ugenChan(ChuckUGen u, long i) {", 1)).append("\n");
    sb.append(indent("if (u == null) return null;", 2)).append("\n");
    sb.append(indent("String name = (i <= 0) ? \"left\" : ((i == 1) ? \"right\" : \"chan\");", 2))
        .append("\n");
    sb.append(indent("try {", 2)).append("\n");
    sb.append(indent("if (\"chan\".equals(name)) {", 3)).append("\n");
    sb.append(
            indent(
                "var m = u.getClass().getMethod(\"chan\", long.class); return (ChuckUGen) m.invoke(u, i);",
                4))
        .append("\n");
    sb.append(indent("}", 3)).append("\n");
    sb.append(indent("var m = u.getClass().getMethod(name);", 3)).append("\n");
    sb.append(indent("return (ChuckUGen) m.invoke(u);", 3)).append("\n");
    sb.append(indent("} catch (Exception ignored) {", 2)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("return u;", 2)).append("\n");
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
    sb.append(indent("private static ChuckArray _callArray(Object t, String m, Object... a) {", 1))
        .append("\n");
    sb.append(indent("Object r = _call(t, m, a);", 2)).append("\n");
    sb.append(indent("return (r instanceof ChuckArray ca) ? ca : new ChuckArray(\"float\", 0);", 2))
        .append("\n");
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
    sb.append(indent("String origin() { return typeOrigin; }", 2)).append("\n");
    sb.append(indent("static ChuckArray getTypes(long... ignoredFlags) {", 2)).append("\n");
    sb.append(indent("return new ChuckArray(\"Object\", 0);", 3)).append("\n");
    sb.append(indent("}", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
    sb.append("\n");
    sb.append(indent("private static final class BPM {", 1)).append("\n");
    sb.append(indent("private double bpm = 120.0;", 2)).append("\n");
    sb.append(indent("private long n = 0;", 2)).append("\n");
    sb.append(indent("BPM() { recalc(); }", 2)).append("\n");
    sb.append(
            indent(
                "double tempo(double value) { this.bpm = value <= 0 ? 120.0 : value; recalc(); return this.bpm; }",
                2))
        .append("\n");
    sb.append(indent("double tempo(int value) { return tempo((double) value); }", 2)).append("\n");
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
    sb.append(indent("private static final class AI {", 1)).append("\n");
    sb.append(indent("static final int MLP = 1;", 2)).append("\n");
    sb.append(indent("static int Regression() { return 0; }", 2)).append("\n");
    sb.append(indent("}", 1)).append("\n");
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
                "double __op__minus(Object o) { vec2 r = from(o); return Math.hypot(x - r.x, y - r.y); }",
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

        String loopVar = "i_" + ds.name();
        if ("ChuckEvent".equals(baseType)) {
          sb.append(
              indent(
                  "for (int "
                      + loopVar
                      + " = 0; "
                      + loopVar
                      + " < "
                      + ds.name()
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
                      + ds.name()
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
          sb.append(indent(ds.name() + "[" + loopVar + "] = " + init + ";\n", 3));
        } else {
          sb.append(indent(ds.name() + ".setObject(" + loopVar + ", " + init + ");\n", 3));
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
    return sb.toString().replace("ChuckMath.maybe()", "((long)(Math.random() * 2))");
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
        if (fs.init() != null) collectFields(List.of(fs.init()));
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
    String type = mapType(ds.type());
    varTypes.put(ds.name(), type);
    if (ds.isGlobal()) globals.add(ds.name());

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
    String type = typeOf(exp);
    String code = visitExp(exp);
    if ("long".equals(type)) return "(" + code + " != 0)";
    if ("double".equals(type)) return "(" + code + " != 0.0)";
    if ("ChuckDuration".equals(type)) return "(" + durationScalar(code) + " != 0.0)";
    if (type != null && type.endsWith("[]"))
      return "(" + code + " != null && " + code + ".length > 0)";
    if ("ChuckArray".equals(type)) return "(" + code + " != null && " + code + ".size() > 0)";
    if (code != null && code.endsWith(".args()"))
      return "(" + code + " != null && " + code + ".length > 0)";
    return code;
  }

  private String visitStmtInternal(ChuckAST.Stmt stmt) {
    if (stmt instanceof ChuckAST.ExpStmt es) {
      if (es.exp() instanceof ChuckAST.BinaryExp be
          && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)
          && containsNestedChuck(be)) {
        return emitChuckChain(be);
      }
      return visitExp(es.exp()) + ";";
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
      return "while (" + visitBoolExp(ws.condition()) + ") " + ensureBraces(visitStmt(ws.body()));
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
      String cond = "true";
      if (fs.condition() instanceof ChuckAST.ExpStmt es) {
        cond = visitBoolExp(es.exp());
      } else if (fs.condition() != null) {
        cond = visitStmt(fs.condition());
        if (cond.endsWith(";")) cond = cond.substring(0, cond.length() - 1);
      }
      String update = fs.update() != null ? visitExp(fs.update()) : "";
      if ((init == null || init.isBlank()) && update.matches("\\s*[A-Za-z_][A-Za-z0-9_]*.*")) {
        String loopVar = update.trim().replaceAll("^([A-Za-z_][A-Za-z0-9_]*).*", "$1");
        init = "long " + loopVar + " = 0";
      }
      return "for ("
          + init
          + "; "
          + cond
          + "; "
          + update
          + ") "
          + ensureBraces(visitStmt(fs.body()));
    } else if (stmt instanceof ChuckAST.DeclStmt ds) {
      String type = mapType(ds.type());
      varTypes.put(ds.name(), type);
      if (isFieldMode && currentClassName == null) {
        return null;
      }

      boolean isAlreadyField = fields.stream().anyMatch(f -> f.name().equals(ds.name()));

      if (ds.isGlobal()) {
        String getter =
            switch (type) {
              case "long" -> "Machine.getGlobalInt(\"" + ds.name() + "\")";
              case "double" -> "Machine.getGlobalFloat(\"" + ds.name() + "\")";
              default -> "Machine.getGlobalObject(\"" + ds.name() + "\")";
            };
        String castType = type;
        if (type.equals("ChuckArray")) castType = "ChuckArray"; // Explicit
        return type + " " + ds.name() + " = (" + castType + ")" + getter + ";";
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
          varTypes.put(ds.name(), type); // Preserve full array type (e.g. BiQuad[])
          return declType
              + " "
              + ds.name()
              + " = new ChuckArray(\""
              + baseType
              + "\", (int)("
              + size
              + "));";
        }
      }

      StringBuilder sb = new StringBuilder();
      // If it's a field, only emit the initialization part
      if (!isAlreadyField) {
        sb.append(type).append(" ").append(ds.name());
      } else {
        // It's a field, we just need the name for assignment if any
      }

      // Handle connection in declaration: SinOsc s => dac;
      if (ds.callArgs() instanceof ChuckAST.BinaryExp be
          && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)) {
        String lhs = visitExp(be.lhs());
        String rhs = visitExp(be.rhs());
        String mappedType = mapType(ds.type());
        if (isPrimitive(mappedType)) {
          if (isAlreadyField) {
            return ds.name() + " = (" + mappedType + ")(" + lhs + ");";
          }
          sb.append(" = (").append(mappedType).append(")(").append(lhs).append(");");
          return sb.toString();
        } else {
          if (isAlreadyField) {
            return "_chuckConnect(" + ds.name() + ", " + rhs + ");";
          }
          sb.append(" = _new(").append(mappedType).append(".class);");
          sb.append("\n");
          sb.append(indent("_chuckConnect(" + ds.name() + ", " + rhs + ");", 0));
          return sb.toString();
        }
      }

      if (isAlreadyField) return "";

      if (isPrimitive(type)) {
        sb.append(" = (").append(type).append(")(0)");
        if (type.equals("ChuckDuration")) {
          sb.setLength(sb.length() - (type.length() + 6)); // remove " = (type)(0)"
          sb.append(" = samp(0)");
        }
      } else if (isUGen(type) || userClasses.contains(type)) {
        if ("Complex".equals(type) || "Polar".equals(type)) {
          sb.append(" = new ").append(type).append("(0f, 0f)");
        } else {
          sb.append(" = new ").append(type).append("()");
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
      return "for (int i = 0; i < " + count + "; i++) " + ensureBraces(visitStmt(rs.body()));
    } else if (stmt instanceof ChuckAST.ForEachStmt fes) {
      String iterType = mapType(fes.iterType());
      String prevType = varTypes.put(fes.iterName(), iterType);
      String body = visitStmt(fes.body());
      if (prevType != null) {
        varTypes.put(fes.iterName(), prevType);
      } else {
        varTypes.remove(fes.iterName());
      }
      return "for (Object "
          + fes.iterName()
          + "_obj"
          + " : "
          + visitExp(fes.collection())
          + ") {\n"
          + indent(
              iterType + " " + fes.iterName() + " = (" + iterType + ") " + fes.iterName() + "_obj;",
              1)
          + "\n"
          + indent(body, 1)
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
      return "org.chuck.core.ChuckDSL.print("
          + ps.expressions().stream().map(this::visitExp).collect(Collectors.joining(" + \" \" + "))
          + ");";
    } else if (stmt instanceof ChuckAST.ImportStmt is) {
      return "// import " + is.path() + ";";
    } else if (stmt instanceof ChuckAST.FuncDefStmt fds) {
      StringBuilder sb = new StringBuilder();
      sb.append(mapAccess(fds.access())).append(" ");
      if (fds.isStatic()) sb.append("static ");
      String name = normalizeFunctionName(fds.name());
      String signatureKey =
          (currentClassName == null ? "__root__" : currentClassName)
              + "|"
              + name
              + "|"
              + fds.argTypes().size();
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
        String argType = mapType(fds.argTypes().get(i));
        if (argType.endsWith("[]") && !argType.startsWith("ChuckEvent")) {
          argType = "ChuckArray";
        }
        varTypes.put(fds.argNames().get(i), argType);
        sb.append(argType).append(" ").append(fds.argNames().get(i));
      }
      sb.append(")");
      if (isInterfaceMode) {
        sb.append(";");
      } else {
        sb.append(" ").append(visitStmt(fds.body()));
      }
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
        fields.clear();
        collectFields(cds.body());

        // Also collect local functions
        for (ChuckAST.Stmt s : cds.body()) {
          if (s instanceof ChuckAST.FuncDefStmt fds) {
            userFunctions.add(normalizeFunctionName(fds.name()));
          }
        }

        // Emit class fields
        for (ChuckAST.DeclStmt field : fields) {
          String type = mapType(field.type());
          sb.append(indent("public " + type + " " + safeName(field.name()) + ";", 1)).append("\n");
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
    } else if (stmt instanceof ChuckAST.ReturnStmt rs) {
      return "return " + (rs.exp() != null ? visitExp(rs.exp()) : "") + ";";
    }
    return "// Unsupported statement: " + stmt.getClass().getSimpleName();
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
      sb.append(visitExp(pair)).append(";");
    }
    return sb.toString();
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
      if (id.name().equals("dac")) return "dac()";
      if (id.name().equals("adc")) return "adc()";
      if (id.name().equals("blackhole")) return "blackhole()";
      if (id.name().equals("now")) return "now()";
      if (id.name().equals("me")) return "me()";
      if (id.name().equals("maybe")) return "ChuckMath.maybe()";
      if (id.name().equals("Math")) return "ChuckMath";
      if (id.name().equals("IO")) return "org.chuck.core.FileIO";
      if (id.name().equals("Foo") && !userClasses.contains("Foo") && !varTypes.containsKey("Foo"))
        return "\"Foo\"";
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
      if (id.name().equals("Type")) return "Type";
      if (id.name().equals("CKDoc")) return "CKDoc";
      if (id.name().equals("samp")) return "samp()";
      if (id.name().equals("ms")) return "ms()";
      if (id.name().equals("second")) return "second()";
      if (id.name().equals("minute")) return "second().times(60)";
      if (id.name().equals("hour")) return "second().times(3600)";
      if (id.name().equals("day")) return "second().times(86400)";
      if (id.name().equals("week")) return "second().times(604800)";
      return safeName(id.name());
    } else if (exp instanceof ChuckAST.BinaryExp be) {
      String lhsCode = visitExp(be.lhs());
      String rhsCode = visitExp(be.rhs());
      String lhsType = typeOf(be.lhs());
      String rhsType = typeOf(be.rhs());

      if (be.op() == ChuckAST.Operator.PERCENT) {
        if (isDur(be.lhs()) || isDur(be.rhs())) {
          if (lhsCode.equals("now()")) lhsCode = "samp(now())";
          if (rhsCode.equals("now()")) rhsCode = "samp(now())";
          return lhsCode + ".percent(" + rhsCode + ")";
        }
        return "(" + lhsCode + " % " + rhsCode + ")";
      }
      if (be.op() == ChuckAST.Operator.PLUS
          || be.op() == ChuckAST.Operator.MINUS
          || be.op() == ChuckAST.Operator.TIMES
          || be.op() == ChuckAST.Operator.DIVIDE) {
        if (isDur(be.lhs()) || isDur(be.rhs())) {
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
          if (isDur(be.lhs())) {
            return lhsCode + "." + method + "(" + rhsCode + ")";
          } else {
            // For commutative ops, we can flip. For subtraction/division it might be tricky.
            // But usually durations are on the left in ChucK for arithmetic.
            return rhsCode + "." + method + "(" + lhsCode + ")";
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
                default -> null;
              };
          if (opName != null) {
            // For Complex/Polar, use cleaner names
            if (lType.equals("Complex") || lType.equals("Polar")) {
              return lhsCode + "." + opName + "(" + rhsCode + ")";
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

        String op = mapOp(be.op());
        if (be.op() == ChuckAST.Operator.AND) {
          return "logicalAnd(" + lhsCode + ", " + rhsCode + ")";
        }
        if (be.op() == ChuckAST.Operator.OR) {
          return "logicalOr(" + lhsCode + ", " + rhsCode + ")";
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
        return rhsCode + " " + op + "= " + lhsCode;
      }
      if (be.op() == ChuckAST.Operator.PERCENT_CHUCK) {
        return rhsCode + " %= " + lhsCode;
      }

      if (be.op() == ChuckAST.Operator.EQ || be.op() == ChuckAST.Operator.NEQ) {
        if (typeOf(be.lhs()).equals("String") || typeOf(be.rhs()).equals("String")) {
          String prefix = be.op() == ChuckAST.Operator.EQ ? "" : "!";
          return prefix + lhsCode + ".equals(" + rhsCode + ")";
        }
        String op = be.op() == ChuckAST.Operator.EQ ? "==" : "!=";
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
        // Special case: duration => now OR event => now OR event_array => now
        if (be.rhs() instanceof ChuckAST.IdExp id && id.name().equals("now")) {
          return "advance(_toDur(" + lhsCode + "))";
        }

        // Handle: Machine.getGlobalObject(...) $ int[] @=> data;
        if (be.lhs() instanceof ChuckAST.CastExp ce) {
          String targetType = mapType(ce.targetType());
          if (targetType.endsWith("[]") && !targetType.startsWith("ChuckEvent"))
            targetType = "ChuckArray";
          return rhsCode + " = (" + targetType + ")(" + visitExp(ce.value()) + ")";
        }

        // Handle: UGen u => dac; and UGen u => Gain g;
        if (be.lhs() instanceof ChuckAST.DeclExp lde) {
          String lType = mapType(lde.type());
          String lName = safeName(lde.name());
          varTypes.put(lName, lType);
          StringBuilder chain = new StringBuilder();
          boolean lAlreadyField = fields.stream().anyMatch(f -> f.name().equals(lde.name()));
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

          String javaType = deType;
          if (deType.endsWith("[]") && !deType.startsWith("ChuckEvent")) {
            javaType = "ChuckArray";
          }
          varTypes.put(de.name(), javaType);

          if (isConnectionTargetType(deType)) {
            if (isAlreadyField) {
              return "_chuckConnect(" + lhsCode + ", " + de.name() + ")";
            }
            return javaType
                + " "
                + de.name()
                + " = _new("
                + javaType
                + ".class);\n"
                + "_chuckConnect("
                + lhsCode
                + ", "
                + de.name()
                + ")";
          }

          String cast = isPrimitive(deType) ? "(" + deType + ")" : "";
          if (isAlreadyField) {
            return de.name() + " = " + cast + "(" + lhsCode + ")";
          } else {
            return javaType + " " + de.name() + " = " + cast + "(" + lhsCode + ")";
          }
        }

        // val => s.freq
        if (be.rhs() instanceof ChuckAST.DotExp de) {
          String arg = lhsCode;
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
            return "_chuckSet(" + visitExp(de.base()) + ", \"" + member + "\", " + arg + ")";
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
            return "(__std_mtof_tmp = mtof(" + arg + "))";
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
          if (userClasses.contains(baseExpr)) {
            return baseExpr + "." + member + " = " + arg;
          }
          return "_chuckSet("
              + baseExpr
              + ", \""
              + member
              + "\", "
              + arg
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
          return baseCode + "." + method + "(" + lastIdxCode + ", " + lhsCode + ")";
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
              String typePrefix = (be.rhs() instanceof ChuckAST.DeclExp) ? rhsType + " " : "";
              return typePrefix + rhsCode + " = " + cast + "(" + lhsCode + ")";
            }
          }
          if (rhsType != null
              && (isPrimitive(rhsType) || rhsType.endsWith("[]") || "ChuckArray".equals(rhsType))) {
            if ("ChuckArray".equals(rhsType)) {
              return "_chuckConnect(" + lhsCode + ", " + rhsCode + ")";
            }
            if (rhsType.endsWith("[]") && be.lhs() instanceof ChuckAST.ArrayLitExp ale) {
              String typePrefix = (be.rhs() instanceof ChuckAST.DeclExp) ? rhsType + " " : "";
              return typePrefix + rhsCode + " = " + toTypedArrayLiteral(ale, rhsType);
            }
            String cast = isPrimitive(rhsType) ? "(" + rhsType + ")" : "";
            String typePrefix = (be.rhs() instanceof ChuckAST.DeclExp) ? rhsType + " " : "";
            return typePrefix + rhsCode + " = " + cast + "(" + lhsCode + ")";
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

        if (rhsType != null
            && (isPrimitive(rhsType)
                || "ChuckDuration".equals(rhsType)
                || "String".equals(rhsType))) {
          String cast = isPrimitive(rhsType) ? "(" + rhsType + ")" : "";
          if (be.rhs() instanceof ChuckAST.DeclExp de) {
            return rhsType + " " + de.name() + " = " + cast + "(" + lhsCode + ")";
          }
          if (be.lhs() instanceof ChuckAST.DeclExp de) {
            String declType = mapType(de.type());
            if (declType.endsWith("[]") && !declType.startsWith("ChuckEvent")) {
              declType = "ChuckArray";
            }
            return declType + " " + de.name() + " = " + cast + "(" + rhsCode + ")";
          }
          return rhsCode + " = " + cast + "(" + lhsCode + ")";
        }

        // Default connection-style fallback
        return "_chuckConnect(" + lhsCode + ", " + rhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.UNCHUCK) {
        return lhsCode + ".unchuck(" + rhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.DUR_MUL) {
        // 1::second -> second().times(1)
        if (isDur(be.rhs())) return rhsCode + ".times(" + lhsCode + ")";
        if (isDur(be.lhs())) return lhsCode + ".times(" + rhsCode + ")";
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
          return "_chuckSet(" + visitExp(de.base()) + ", \"" + de.member() + "\", " + rhsCode + ")";
        }
        if (globals.contains(lhsCode)) {
          return "Machine.setGlobalObject(\"" + lhsCode + "\", " + rhsCode + ")";
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
        if ("cherr".equals(lhsCode)
            || "chout".equals(lhsCode)
            || lhsCode.contains("cherr.print")
            || lhsCode.contains("chout.print")) {
          return lhsCode + ".print(" + rhsCode + ")";
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
      return "(" + String.join(" " + le.op() + " ", ops) + ")";
    } else if (exp instanceof ChuckAST.CallExp ce) {
      if (ce.base() instanceof ChuckAST.DotExp stdDe
          && stdDe.base() instanceof ChuckAST.IdExp stdBase
          && "Std".equals(stdBase.name())
          && ce.args().size() == 1) {
        if ("mtof".equals(stdDe.member())) {
          return "mtof(" + visitExp(ce.args().get(0)) + ")";
        }
        if ("ftom".equals(stdDe.member())) {
          return "_ftom(" + visitExp(ce.args().get(0)) + ")";
        }
      }
      String base = visitExp(ce.base());
      if (base.equals("assert")) base = "org.chuck.core.ChuckDSL._CHUCK_INTERNAL_ASSERT_";
      if (base.equals("me") || base.equals("me()")) base = "org.chuck.core.ChuckDSL.me";
      if (base.endsWith(".newline")) return "ChIO.newline()";
      if (base.endsWith(".nl")) return "ChIO.nl()";

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
        if ((member.equals("num") || member.equals("n")) && ce.args().isEmpty()) {
          return "_callLong(" + baseCode + ", \"" + member + "\")";
        }
        if ((member.equals("num") || member.equals("n")) && ce.args().size() == 1) {
          return "_call(" + baseCode + ", \"" + member + "\", " + visitExp(ce.args().get(0)) + ")";
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
          String idx = "(int)(" + visitExp(ce.args().get(0)) + ")";
          if (member.equals("getFloat")) {
            return "((double)(" + baseCode + "[" + idx + "]))";
          }
          if (member.equals("getInt")) {
            return "((long)(" + baseCode + "[" + idx + "]))";
          }
          if (member.equals("size")) {
            return "(long)(" + baseCode + ".length)";
          }
          if (member.equals("setObject") && ce.args().size() == 2) {
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
        if (member.equals("substring") && ce.args().size() >= 2) {
          return baseCode
              + ".substring((int)("
              + visitExp(ce.args().get(0))
              + "), (int)("
              + visitExp(ce.args().get(1))
              + "))";
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
          return "_callLong(" + baseCode + ", \"length\")";
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
        if (member.equals("typeOf")) {
          return "_call(" + baseCode + ", \"typeOf\")";
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
            || member.equals("opRatio")) {
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
        if (member.equals("opGain")
            || member.equals("opAM")
            || member.equals("duration")
            || member.equals("exit")
            || member.equals("id")
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
            || member.equals("lfoDepth")
            || member.equals("volume")
            || member.equals("noiseGain")
            || member.equals("vibratoFreq")
            || member.equals("vibratoGain")
            || member.equals("preset")
            || member.equals("pressure")
            || member.equals("value")
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
        if (member.equals("controlChange")
            || member.equals("getFMTableTime")
            || member.equals("name")
            || member.equals("setObject")
            || member.equals("getObject")
            || member.equals("cap")
            || member.equals("clear")
            || member.equals("read")
            || member.equals("printerr")
            || member.equals("stopBlowing")
            || member.equals("compress")
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
      }

      // If base ends with (), remove it because we're adding it back
      if (base.endsWith("()")) base = base.substring(0, base.length() - 2);

      // Expanded Math/Std Mappings
      if (base.equals("Math.sin") || base.equals("sin"))
        return "Math.sin(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("Math.cos") || base.equals("cos"))
        return "Math.cos(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("Math.sqrt") || base.equals("sqrt"))
        return "Math.sqrt(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("Math.pow") || base.equals("pow"))
        return "Math.pow(" + visitExp(ce.args().get(0)) + ", " + visitExp(ce.args().get(1)) + ")";
      if (base.equals("Math.random2"))
        return "random(" + visitExp(ce.args().get(0)) + ", " + visitExp(ce.args().get(1)) + ")";
      if (base.equals("Math.random2f"))
        return "randomf(" + visitExp(ce.args().get(0)) + ", " + visitExp(ce.args().get(1)) + ")";
      if (base.equals("Math.randomf") || base.equals("randomf")) return "randomf()";
      if (base.equals("maybe") || base.equals("ChuckMath.maybe"))
        return "((long)(Math.random() * 2))";
      if (base.equals("ChuckMath.mtof")) return "mtof(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("ftom")) {
        String a = visitExp(ce.args().get(0));
        return "(69.0 + (12.0 * (Math.log((" + a + ") / 440.0) / Math.log(2.0))))";
      }
      if (base.equals("ChuckMath.ftom")) {
        String a = visitExp(ce.args().get(0));
        return "(69.0 + (12.0 * (Math.log((" + a + ") / 440.0) / Math.log(2.0))))";
      }
      if (base.equals("ChuckMath.equal")) {
        String a = visitExp(ce.args().get(0));
        String b = visitExp(ce.args().get(1));
        return "(Math.abs((" + a + ") - (" + b + ")) < 1e-9)";
      }
      if (base.equals("ChuckMath.isinf"))
        return "Double.isInfinite(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("ChuckMath.isnan")) return "Double.isNaN(" + visitExp(ce.args().get(0)) + ")";
      if (base.equals("ChuckMath.gauss")) {
        String x = visitExp(ce.args().get(0));
        String mu = visitExp(ce.args().get(1));
        String sigma = visitExp(ce.args().get(2));
        return "(Math.exp(-0.5 * Math.pow((("
            + x
            + ")-("
            + mu
            + "))/("
            + sigma
            + "), 2.0)) / (("
            + sigma
            + ") * Math.sqrt(2.0 * Math.PI)))";
      }
      if (base.equals("advance") && ce.args().size() == 1) {
        String a = visitExp(ce.args().get(0));
        return "advance(_toDur(" + a + "))";
      }
      if (base.equals("ChuckMath.remap")) {
        String a = visitExp(ce.args().get(0));
        String in0 = visitExp(ce.args().get(1));
        String in1 = visitExp(ce.args().get(2));
        String out0 = visitExp(ce.args().get(3));
        String out1 = visitExp(ce.args().get(4));
        return "(" + out0 + " + ((" + a + " - " + in0 + ") * (" + out1 + " - " + out0 + ") / ("
            + in1 + " - " + in0 + ")))";
      }
      if (base.equals("ChuckMath.map")) {
        String a = visitExp(ce.args().get(0));
        String in0 = visitExp(ce.args().get(1));
        String in1 = visitExp(ce.args().get(2));
        String out0 = visitExp(ce.args().get(3));
        String out1 = visitExp(ce.args().get(4));
        return "(" + out0 + " + ((" + a + " - " + in0 + ") * (" + out1 + " - " + out0 + ") / ("
            + in1 + " - " + in0 + ")))";
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
        return "mtof("
            + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", "))
            + ")";
      }
      return base
          + "("
          + ce.args().stream().map(this::visitExp).collect(Collectors.joining(", "))
          + ")";
    } else if (exp instanceof ChuckAST.ArrayAccessExp aae) {
      String baseType = typeOf(aae.base());
      String baseCode = visitExp(aae.base());
      if ((baseType.endsWith("[]") || baseType.equals("ChuckArray")) && aae.indices().size() == 1) {
        String elemType =
            baseType.endsWith("[]") ? baseType.substring(0, baseType.length() - 2) : "float";
        String idxCode = wrapInt(aae.indices().get(0));

        if (elemType.equals("String"))
          return "(String)_call(" + baseCode + ", \"getObject\", " + idxCode + ")";
        if (elemType.equals("double") || elemType.equals("float"))
          return baseCode + ".getFloat(" + idxCode + ")";
        if (elemType.equals("long") || elemType.equals("int"))
          return baseCode + ".getInt(" + idxCode + ")";

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
      for (ChuckAST.Exp idx : aae.indices()) {
        String iCode = wrapInt(idx);
        if (res.contains("ChuckArray") || res.contains("getObject")) {
          res = "((ChuckArray)_call(" + res + ", \"getObject\", " + iCode + "))";
        } else {
          res = res + "[(int)(" + visitExp(idx) + ")]";
        }
      }
      return res;
    } else if (exp instanceof ChuckAST.DotExp de) {
      String base = visitExp(de.base());
      String baseType = typeOf(de.base());
      String member = safeName(de.member());
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

      // Known ChuckArray methods
      if (baseType.equals("ChuckArray")) {
        if (member.equals("size") || member.equals("cap") || member.equals("length"))
          return base + ".size()";
        if (member.equals("popBack") || member.equals("popFront") || member.equals("erase"))
          return base + "." + member;
        if (member.equals("clear") || member.equals("getKeys")) return base + "." + member;
      }

      // If it's a user class, assume field access unless we're in a CallExp
      if (member.equals("help") && userClasses.contains(base)) {
        return "new " + base + "().help()";
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
      if ("Object".equals(baseType) && (member.equals("num") || member.equals("n"))) {
        return "_callLong(" + base + ", \"" + member + "\")";
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
        return base + "." + member + "()";
      }

      return base + "." + member;
    } else if (exp instanceof ChuckAST.ArrayLitExp ale) {
      // Use ChuckArray for literals
      String elements =
          ale.elements().stream().map(this::visitExp).collect(Collectors.joining(", "));
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
      return "ChuckDSL.spork(() -> " + visitExp(se.call()) + ")";
    } else if (exp instanceof ChuckAST.TernaryExp te) {
      return "("
          + visitExp(te.condition())
          + " ? "
          + visitExp(te.thenExp())
          + " : "
          + visitExp(te.elseExp())
          + ")";
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
        if ("long".equals(innerType) || "int".equals(innerType)) return "(" + inner + " == 0)";
        if ("double".equals(innerType) || "float".equals(innerType))
          return "(" + inner + " == 0.0)";
        if ("ChuckDuration".equals(innerType)) return "(" + durationScalar(inner) + " == 0.0)";
        if (inner.startsWith("_callLong(")) return "(" + inner + " == 0)";
        return "!(" + inner + ")";
      }
      String op =
          switch (ue.op()) {
            case MINUS -> "-";
            case PLUS_PLUS, POSTFIX_PLUS_PLUS -> "++";
            case MINUS_MINUS, POSTFIX_MINUS_MINUS -> "--";
            default -> ue.op().name();
          };
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
        return visitExp(ue.exp()) + op;
      }
      return op + visitExp(ue.exp());
    } else if (exp instanceof ChuckAST.DeclExp de) {
      String type = mapType(de.type());
      varTypes.put(safeName(de.name()), type);
      return safeName(de.name());
    }
    return "// Unsupported expression: " + exp.getClass().getSimpleName();
  }

  private String typeOf(ChuckAST.Exp exp) {
    if (exp instanceof ChuckAST.IdExp id) {
      if (id.name().equals("now")) return "ChuckDuration";
      if (id.name().equals("me")) return "ChuckShred";
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
          return mapType(field.type());
        }
      }
      if (id.name().equals("dac") || id.name().equals("adc")) return "ChuckUGen";
      return "Object";
    }
    if (exp instanceof ChuckAST.IntExp) return "long";
    if (exp instanceof ChuckAST.FloatExp) return "double";
    if (exp instanceof ChuckAST.StringExp) return "String";
    if (exp instanceof ChuckAST.ArrayLitExp) return "ChuckArray";
    if (exp instanceof ChuckAST.DotExp de) {
      String baseType = typeOf(de.base());
      String member = de.member();
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
        if (lType.equals("double") || rType.equals("double")) return "double";
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
      if (base.endsWith(".dir")) return "String";

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

  private void flattenLogical(ChuckAST.Exp exp, List<String> result) {
    if (exp instanceof ChuckAST.LogicalExp le) {
      flattenLogical(le.lhs(), result);
      flattenLogical(le.rhs(), result);
    } else {
      result.add(visitExp(exp));
    }
  }

  private String safeName(String name) {
    if (name == null) return null;
    return name.replace("@", "_CHUCK_SPECIAL_");
  }

  private String normalizeFunctionName(String rawName) {
    if (rawName == null) return null;
    if ("assert".equals(rawName)) return "_CHUCK_INTERNAL_ASSERT_";
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
          case "auto" -> "long"; // Map auto to long (ChucK's default)
          case "OscIn" -> "OscIn";
          case "OscOut" -> "OscOut";
          case "OscMsg" -> "OscMsg";
          case "OscEvent" -> "OscEvent";
          case "IO" -> "org.chuck.core.ChuckIO";
          case "Hid" -> "org.chuck.hid.Hid";
          case "HidMsg" -> "org.chuck.hid.HidMsg";
          case "complex" -> "Complex";
          case "polar" -> "Polar";
          case "UGen" -> "ChuckUGen";
          case "BPM" -> "BPM";
          case "Foo" -> "Object";
          case "RollOff" -> "Rolloff";
          case "PitchTrack" -> "Object";
          case "Sigmund" -> "Object";
          case "Gain" -> "ChuckUGen";
          case "ADSR" -> "Adsr";
          case "StkInstrument" -> "ChuckUGen";
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

  private String toTypedArrayLiteral(ChuckAST.ArrayLitExp ale, String targetArrayType) {
    String elemType = targetArrayType.substring(0, targetArrayType.length() - 2);
    String values =
        ale.elements().stream()
            .map(
                e -> {
                  String v = visitExp(e);
                  if ("long".equals(elemType) || "int".equals(elemType)) {
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
    String type = typeOf(exp);
    return "ChuckDuration".equals(type);
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
        || type.equals("String")
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
        || type.equals("ChuckEvent[]")) return false;
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
