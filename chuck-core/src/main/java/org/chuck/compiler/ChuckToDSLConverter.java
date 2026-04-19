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
  private final Map<String, String> varTypes = new HashMap<>();
  private final List<ChuckAST.DeclStmt> arraysToInit = new ArrayList<>();

  public String convert(List<ChuckAST.Stmt> program, String className) {
    globals.clear();
    varTypes.clear();
    arraysToInit.clear();
    StringBuilder sb = new StringBuilder();
    sb.append("import static org.chuck.core.ChuckDSL.*;\n");
    sb.append("import org.chuck.audio.*;\n");
    sb.append("import org.chuck.audio.osc.*;\n");
    sb.append("import org.chuck.audio.filter.*;\n");
    sb.append("import org.chuck.audio.fx.*;\n");
    sb.append("import org.chuck.audio.stk.*;\n");
    sb.append("import org.chuck.audio.util.*;\n");
    sb.append("import org.chuck.core.*;\n\n");

    sb.append("public class ").append(className).append(" implements Shred {\n");

    // 1. Separate top-level constructs
    for (ChuckAST.Stmt s : program) {
      if (s instanceof ChuckAST.DeclStmt ds) {
        String type = mapType(ds.type());
        varTypes.put(ds.name(), type);
        if (ds.isGlobal()) {
          globals.add(ds.name());
        }
      }
    }

    List<ChuckAST.DeclStmt> fields =
        program.stream()
            .filter(s -> s instanceof ChuckAST.DeclStmt)
            .map(s -> (ChuckAST.DeclStmt) s)
            .toList();
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
                s ->
                    !(s instanceof ChuckAST.FuncDefStmt)
                        && !(s instanceof ChuckAST.ClassDefStmt)
                        && !(s instanceof ChuckAST.DeclStmt))
            .toList();

    // 2. Emit Fields
    for (ChuckAST.DeclStmt field : fields) {
      String s = visitStmt(field);
      if (s != null && !s.isEmpty()) {
        sb.append(indent(s, 1)).append("\n");
      }
    }
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
        String init =
            isUGen(baseType) ? "new " + baseType + "(sampleRate())" : "new " + baseType + "()";
        sb.append(indent(ds.name() + "[" + loopVar + "] = " + init + ";\n", 3));
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
    return sb.toString();
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

  private String visitStmt(ChuckAST.Stmt stmt) {
    String comment = formatComment(stmt.doc(), 0);
    String code = visitStmtInternal(stmt);
    if (comment.isEmpty()) return code;
    return comment + code;
  }

  private String visitStmtInternal(ChuckAST.Stmt stmt) {
    if (stmt instanceof ChuckAST.ExpStmt es) {
      return visitExp(es.exp()) + ";";
    } else if (stmt instanceof ChuckAST.IfStmt is) {
      StringBuilder sb = new StringBuilder();
      sb.append("if (")
          .append(visitExp(is.condition()))
          .append(") ")
          .append(ensureBraces(visitStmt(is.thenBranch())));
      if (is.elseBranch() != null) {
        sb.append(" else ").append(ensureBraces(visitStmt(is.elseBranch())));
      }
      return sb.toString();
    } else if (stmt instanceof ChuckAST.ForStmt fs) {
      String init = fs.init() != null ? visitStmt(fs.init()) : ";";
      if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
      String cond = fs.condition() != null ? visitStmt(fs.condition()) : ";";
      if (cond.endsWith(";")) cond = cond.substring(0, cond.length() - 1);
      String update = fs.update() != null ? visitExp(fs.update()) : "";
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
      if (ds.isGlobal()) {
        String getter =
            switch (type) {
              case "long" -> "Machine.getGlobalInt(\"" + ds.name() + "\")";
              case "double" -> "Machine.getGlobalFloat(\"" + ds.name() + "\")";
              default -> "Machine.getGlobalObject(\"" + ds.name() + "\")";
            };
        return type + " " + ds.name() + " = (" + type + ")" + getter + ";";
      }
      StringBuilder sb = new StringBuilder();

      // Handle array declaration
      if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
        String baseType = type;
        while (baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);

        if (!isPrimitive(baseType)) {
          arraysToInit.add(ds);
        }

        sb.append(baseType)
            .append("[] ".repeat(ds.arraySizes().size()))
            .append(ds.name())
            .append(" = new ")
            .append(baseType)
            .append("[")
            .append(visitExp(ds.arraySizes().get(0)))
            .append("]")
            .append("[]".repeat(ds.arraySizes().size() - 1))
            .append(";");
        return sb.toString();
      }

      // Base declaration
      sb.append(type).append(" ").append(ds.name());
      if (isPrimitive(type)) {
        sb.append(" = 0;"); // Default for primitives, will be overridden by connection if any
        if (type.equals("ChuckDuration")) sb.setLength(sb.length() - 2); // remove "0;"
        if (type.equals("ChuckDuration")) sb.append(" = samp(0);");
      } else if (isUGen(type)) {
        sb.append(" = new ").append(type).append("(sampleRate());");
      } else {
        sb.append(" = new ").append(type).append("();");
      }

      // Handle connection in declaration: SinOsc s => dac;
      if (ds.callArgs() instanceof ChuckAST.BinaryExp be && be.op() == ChuckAST.Operator.CHUCK) {
        if (type.equals("long") || type.equals("double") || type.equals("ChuckDuration")) {
          sb.setLength(sb.length() - 1); // remove last ";"
          sb.append(" = ").append(visitExp(be.lhs())).append(";");
        } else {
          sb.append(" ")
              .append(ds.name())
              .append(".chuck(")
              .append(visitExp(be.rhs()))
              .append(");");
        }
      }
      return sb.toString();
    } else if (stmt instanceof ChuckAST.DoStmt ds) {
      String cond = visitExp(ds.condition());
      if (ds.isUntil()) cond = "!(" + cond + ")";
      return "do " + ensureBraces(visitStmt(ds.body())) + " while (" + cond + ");";
    } else if (stmt instanceof ChuckAST.RepeatStmt rs) {
      String count = visitExp(rs.count());
      return "for (int i = 0; i < " + count + "; i++) " + ensureBraces(visitStmt(rs.body()));
    } else if (stmt instanceof ChuckAST.ForEachStmt fes) {
      return "for ("
          + mapType(fes.iterType())
          + " "
          + fes.iterName()
          + " : "
          + visitExp(fes.collection())
          + ") "
          + ensureBraces(visitStmt(fes.body()));
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
        sb.append(indent(visitStmt(s), 1)).append("\n");
      }
      return sb.toString();
    } else if (stmt instanceof ChuckAST.BlockStmt bs) {
      // If it's the body of a while/if, we might want braces, but visitStmt should handle it
      StringBuilder b = new StringBuilder("{\n");
      for (ChuckAST.Stmt s : bs.statements()) {
        b.append(indent(visitStmt(s), 1)).append("\n");
      }
      b.append("}");
      return b.toString();
    } else if (stmt instanceof ChuckAST.PrintStmt ps) {
      return "System.out.println("
          + ps.expressions().stream().map(this::visitExp).collect(Collectors.joining(" + \" \" + "))
          + ");";
    } else if (stmt instanceof ChuckAST.ImportStmt is) {
      return "// import " + is.path() + ";";
    } else if (stmt instanceof ChuckAST.FuncDefStmt fds) {
      StringBuilder sb = new StringBuilder();
      sb.append(mapAccess(fds.access())).append(" ");
      if (fds.isStatic()) sb.append("static ");
      sb.append(mapType(fds.returnType())).append(" ").append(fds.name()).append("(");
      for (int i = 0; i < fds.argNames().size(); i++) {
        if (i > 0) sb.append(", ");
        String argType = mapType(fds.argTypes().get(i));
        varTypes.put(fds.argNames().get(i), argType);
        sb.append(argType).append(" ").append(fds.argNames().get(i));
      }
      sb.append(") ").append(visitStmt(fds.body()));
      return sb.toString();
    } else if (stmt instanceof ChuckAST.ClassDefStmt cds) {
      StringBuilder sb = new StringBuilder();
      sb.append(mapAccess(cds.access())).append(" ");
      if (cds.isAbstract()) sb.append("abstract ");
      if (cds.isInterface()) sb.append("interface ");
      else sb.append("class ");
      sb.append(cds.name());
      if (cds.parentName() != null) {
        sb.append(" extends ").append(mapType(cds.parentName()));
      }
      sb.append(" {\n");
      for (ChuckAST.Stmt s : cds.body()) {
        if (s instanceof ChuckAST.DeclStmt ds) {
          varTypes.put(ds.name(), mapType(ds.type()));
        }
        sb.append(indent(visitStmt(s), 1)).append("\n");
      }
      sb.append("}");
      return sb.toString();
    }
    return "// Unsupported statement: " + stmt.getClass().getSimpleName();
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
      return "\"" + se.value() + "\"";
    } else if (exp instanceof ChuckAST.MeExp) {
      return "me()";
    } else if (exp instanceof ChuckAST.IdExp id) {
      if (id.name().equals("dac")) return "dac()";
      if (id.name().equals("adc")) return "adc()";
      if (id.name().equals("blackhole")) return "blackhole()";
      return id.name();
    } else if (exp instanceof ChuckAST.BinaryExp be) {
      if (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK) {
        // Special case: duration => now OR event => now OR event_array => now
        if (be.rhs() instanceof ChuckAST.IdExp id && id.name().equals("now")) {
          String lhs = visitExp(be.lhs());
          String type = varTypes.get(lhs);
          if (type != null && type.startsWith("ChuckEvent") && type.endsWith("[]")) {
            return "advance(" + lhs + ")";
          }
          return "advance(" + lhs + ")";
        }
        String lhs = visitExp(be.lhs());
        String rhs = visitExp(be.rhs());

        // val => s.freq
        if (be.rhs() instanceof ChuckAST.DotExp de) {
          return visitExp(de.base()) + "." + de.member() + "(" + lhs + ")";
        }

        // Handle primitive assignment via =>
        if (be.rhs() instanceof ChuckAST.DeclExp de) {
          String type = mapType(de.type());
          if (isPrimitive(type)) {
            varTypes.put(de.name(), type);
            return type + " " + de.name() + " = " + lhs;
          }
        }

        String type = varTypes.get(rhs);
        if (type != null && isPrimitive(type)) {
          return rhs + " = " + lhs;
        }

        if (globals.contains(rhs)) {
          return "Machine.setGlobalObject(\"" + rhs + "\", " + lhs + ")";
        }
        if (type == null
            && (rhs.equals("i")
                || rhs.equals("j")
                || rhs.equals("k")
                || rhs.equals("r")
                || rhs.equals("x")
                || rhs.equals("y")
                || rhs.equals("z")
                || rhs.equals("val")
                || rhs.equals("step"))) {
          return rhs + " = " + lhs;
        }

        // Default: s => dac
        return lhs + ".chuck(" + rhs + ")";
      } else if (be.op() == ChuckAST.Operator.UNCHUCK) {
        return visitExp(be.lhs()) + ".unchuck(" + visitExp(be.rhs()) + ")";
      } else if (be.op() == ChuckAST.Operator.DUR_MUL) {
        // 1::second
        String unit = visitExp(be.rhs());
        String val = visitExp(be.lhs());
        return unit + "(" + val + ")";
      } else if (be.op() == ChuckAST.Operator.ASSIGN) {
        String lhs = visitExp(be.lhs());
        String rhs = visitExp(be.rhs());
        if (globals.contains(lhs)) {
          return "Machine.setGlobalObject(\"" + lhs + "\", " + rhs + ")";
        }
        return lhs + " = " + rhs;
      } else if (be.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS) {
        return visitExp(be.rhs()) + "++";
      } else if (be.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS) {
        return visitExp(be.rhs()) + "--";
      }
      return "(" + visitExp(be.lhs()) + " " + mapOp(be.op()) + " " + visitExp(be.rhs()) + ")";
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
      String base = visitExp(ce.base());
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
      if (base.equals("Math.random2") || base.equals("Math.random2f"))
        return "random(" + visitExp(ce.args().get(0)) + ", " + visitExp(ce.args().get(1)) + ")";

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
    } else if (exp instanceof ChuckAST.DotExp de) {
      String base = visitExp(de.base());
      if (base.equals("Std") && de.member().equals("mtof")) return "mtof";
      if (base.equals("Std") && de.member().equals("ftom")) return "ftom";
      if (base.equals("Std") && de.member().equals("abs")) return "Math.abs";
      if (base.equals("Math") && de.member().equals("random2")) return "random";
      if (base.equals("Math") && de.member().equals("random2f")) return "random";

      if (de.member().equals("size") || de.member().equals("cap")) return base + ".length";

      // ADSR / Envelope triggers
      if (de.member().equals("keyOn")) return base + ".keyOn";
      if (de.member().equals("keyOff")) return base + ".keyOff";

      return base + "." + de.member() + "()";
    } else if (exp instanceof ChuckAST.ArrayLitExp ale) {
      // Very basic: assume long[] if all elements look like ints, else double[]
      // In a real compiler we'd have type info.
      return "new long[]{"
          + ale.elements().stream().map(this::visitExp).collect(Collectors.joining(", "))
          + "}";
    } else if (exp instanceof ChuckAST.VectorLitExp vle) {
      return "new double[]{"
          + vle.elements().stream().map(this::visitExp).collect(Collectors.joining(", "))
          + "}";
    } else if (exp instanceof ChuckAST.SporkExp se) {
      return "spork(() -> " + visitExp(se.call()) + ")";
    } else if (exp instanceof ChuckAST.ArrayAccessExp aae) {
      return visitExp(aae.base()) + "[" + visitExp(aae.indices().get(0)) + "]";
    } else if (exp instanceof ChuckAST.TernaryExp te) {
      return "("
          + visitExp(te.condition())
          + " ? "
          + visitExp(te.thenExp())
          + " : "
          + visitExp(te.elseExp())
          + ")";
    } else if (exp instanceof ChuckAST.ComplexLit cl) {
      return "new ChuckComplex(" + visitExp(cl.re()) + ", " + visitExp(cl.im()) + ")";
    } else if (exp instanceof ChuckAST.PolarLit pl) {
      return "new ChuckPolar(" + visitExp(pl.mag()) + ", " + visitExp(pl.phase()) + ")";
    } else if (exp instanceof ChuckAST.TypeofExp te) {
      return visitExp(te.expr()) + ".getClass().getSimpleName()";
    } else if (exp instanceof ChuckAST.InstanceofExp ie) {
      return "(" + visitExp(ie.expr()) + " instanceof " + mapType(ie.typeName()) + ")";
    } else if (exp instanceof ChuckAST.CastExp ce) {
      return "(" + mapType(ce.targetType()) + ")(" + visitExp(ce.value()) + ")";
    } else if (exp instanceof ChuckAST.UnaryExp ue) {
      String op =
          switch (ue.op()) {
            case MINUS -> "-";
            case S_OR -> "!";
            default -> ue.op().name();
          };
      return op + "(" + visitExp(ue.exp()) + ")";
    } else if (exp instanceof ChuckAST.DeclExp de) {
      String type = mapType(de.type());
      if (isUGen(type)) {
        return "new " + type + "(sampleRate())";
      }
      return "new " + type + "()";
    }
    return "// Unsupported expression: " + exp.getClass().getSimpleName();
  }

  private void flattenLogical(ChuckAST.Exp exp, List<String> result) {
    if (exp instanceof ChuckAST.LogicalExp le) {
      flattenLogical(le.lhs(), result);
      flattenLogical(le.rhs(), result);
    } else {
      result.add(visitExp(exp));
    }
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
          case "time" -> "long";
          case "Event" -> "ChuckEvent";
          case "OscEvent" -> "OscEvent";
          default -> type;
        };
    return mapped + suffix;
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
      case PERCENT -> "%";
      default -> op.name();
    };
  }

  private String ensureBraces(String s) {
    if (s.startsWith("{")) return s;
    return "{\n" + indent(s, 1) + "\n}";
  }

  private String indent(String s, int levels) {
    String prefix = "    ".repeat(levels);
    return prefix + s.replace("\n", "\n" + prefix);
  }

  private boolean isEvent(String operand) {
    String type = varTypes.get(operand);
    if (type == null) return false;
    return type.startsWith("ChuckEvent");
  }

  private boolean isPrimitive(String type) {
    return type.equals("long")
        || type.equals("double")
        || type.equals("ChuckDuration")
        || type.equals("boolean");
  }

  private boolean isUGen(String type) {
    // Very incomplete list, but covers basics.
    // Ideally we'd have the UGenRegistry here.
    return type.endsWith("Osc")
        || type.equals("Gain")
        || type.equals("LPF")
        || type.equals("HPF")
        || type.equals("BPF")
        || type.equals("Pan2")
        || type.equals("SndBuf")
        || type.equals("JCRev");
  }
}
