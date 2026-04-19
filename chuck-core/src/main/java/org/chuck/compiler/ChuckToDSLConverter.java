package org.chuck.compiler;

import java.util.List;
import java.util.stream.Collectors;

/** Converts a ChucK AST into Java DSL source code. */
public class ChuckToDSLConverter {

  public String convert(List<ChuckAST.Stmt> program, String className) {
    StringBuilder sb = new StringBuilder();
    sb.append("import static org.chuck.core.ChuckDSL.*;\n");
    sb.append("import org.chuck.audio.osc.*;\n");
    sb.append("import org.chuck.audio.filter.*;\n");
    sb.append("import org.chuck.audio.fx.*;\n");
    sb.append("import org.chuck.audio.stk.*;\n");
    sb.append("import org.chuck.audio.util.*;\n");
    sb.append("import org.chuck.core.Shred;\n\n");

    sb.append("public class ").append(className).append(" implements Shred {\n");
    sb.append("    @Override\n");
    sb.append("    public void shred() {\n");

    for (ChuckAST.Stmt stmt : program) {
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
      StringBuilder sb = new StringBuilder();

      // Handle array declaration
      if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
        String baseType = type;
        while (baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);
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
      if (type.equals("long") || type.equals("double") || type.equals("ChuckDuration")) {
        sb.append(" = 0;"); // Default for primitives, will be overridden by connection if any
        if (type.equals("ChuckDuration")) sb.setLength(sb.length() - 2); // remove "0;"
        if (type.equals("ChuckDuration")) sb.append(" = samp(0);");
      } else {
        sb.append(" = new ").append(type).append("(sampleRate());");
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
      return "// function definition: " + fds.name();
    } else if (stmt instanceof ChuckAST.ClassDefStmt cds) {
      return "// class definition: " + cds.name();
    }
    return "// Unsupported statement: " + stmt.getClass().getSimpleName();
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
      if (be.op() == ChuckAST.Operator.CHUCK) {
        // Special case: duration => now
        if (be.rhs() instanceof ChuckAST.IdExp id && id.name().equals("now")) {
          return "advance(" + visitExp(be.lhs()) + ")";
        }
        // val => s.freq
        if (be.rhs() instanceof ChuckAST.DotExp de) {
          return visitExp(de.base()) + "." + de.member() + "(" + visitExp(be.lhs()) + ")";
        }
        // Handle primitive assignment via =>
        if (be.rhs() instanceof ChuckAST.DeclExp de) {
          String type = mapType(de.type());
          if (type.equals("long") || type.equals("double") || type.equals("ChuckDuration")) {
            return mapType(de.type()) + " " + de.name() + " = " + visitExp(be.lhs());
          }
        }
        // s => dac
        return visitExp(be.lhs()) + ".chuck(" + visitExp(be.rhs()) + ")";
      } else if (be.op() == ChuckAST.Operator.UNCHUCK) {
        return visitExp(be.lhs()) + ".unchuck(" + visitExp(be.rhs()) + ")";
      } else if (be.op() == ChuckAST.Operator.AT_CHUCK || be.op() == ChuckAST.Operator.CHUCK) {
        // Handle: [0,1] @=> int hi[] OR 125::ms => dur T
        if (be.rhs() instanceof ChuckAST.DeclExp de) {
          String type = mapType(de.type());
          String suffix = (de.arraySizes() != null && !de.arraySizes().isEmpty()) ? "[]" : "";
          return type + suffix + " " + de.name() + " = " + visitExp(be.lhs());
        }
        return visitExp(be.rhs()) + " = " + visitExp(be.lhs());
      } else if (be.op() == ChuckAST.Operator.DUR_MUL) {
        // 1::second
        String unit = visitExp(be.rhs());
        String val = visitExp(be.lhs());
        return unit + "(" + val + ")";
      } else if (be.op() == ChuckAST.Operator.ASSIGN) {
        return visitExp(be.lhs()) + " = " + visitExp(be.rhs());
      } else if (be.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS) {
        return visitExp(be.rhs()) + "++";
      } else if (be.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS) {
        return visitExp(be.rhs()) + "--";
      }
      return "(" + visitExp(be.lhs()) + " " + mapOp(be.op()) + " " + visitExp(be.rhs()) + ")";
    } else if (exp instanceof ChuckAST.CallExp ce) {
      String base = visitExp(ce.base());
      // If base ends with (), remove it because we're adding it back
      if (base.endsWith("()")) base = base.substring(0, base.length() - 2);
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
      if (base.equals("Math") && de.member().equals("random2")) return "random";

      if (de.member().equals("size") || de.member().equals("cap")) return base + ".length";
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
      // Special case for chained connections: SinOsc s => JCRev r => dac;
      // Here 'r' might be a DeclExp in the middle of a CHUCK chain.
      if (de.type() != null && !de.type().isEmpty()) {
        // It's a declaration in the middle of a chain!
        // We need to return the name, but ALSO ensure it gets declared.
        // This is tricky for a simple one-pass visitor.
        // For now, let's just return the name and assume it's declared elsewhere
        // or we'll have to manually fix the generated code.
        return de.name();
      }
      return de.name();
    }
    return "// Unsupported expression: " + exp.getClass().getSimpleName();
  }

  private String mapType(String type) {
    if (type == null) return "Object";
    if (type.equals("int")) return "long";
    if (type.equals("float")) return "double";
    if (type.equals("dur")) return "ChuckDuration";
    if (type.equals("time")) return "long";
    return type;
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
}
