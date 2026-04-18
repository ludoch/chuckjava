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

  private String visitStmt(ChuckAST.Stmt stmt) {
    if (stmt instanceof ChuckAST.ExpStmt es) {
      return visitExp(es.exp()) + ";";
    } else if (stmt instanceof ChuckAST.DeclStmt ds) {
      String type = mapType(ds.type());
      StringBuilder sb = new StringBuilder();

      // Handle array declaration
      if (ds.arraySizes() != null && !ds.arraySizes().isEmpty()) {
        sb.append(type)
            .append("[] ")
            .append(ds.name())
            .append(" = new ")
            .append(type)
            .append("[")
            .append(visitExp(ds.arraySizes().get(0)))
            .append("];");
        return sb.toString();
      }

      // Base declaration
      sb.append(type)
          .append(" ")
          .append(ds.name())
          .append(" = new ")
          .append(type)
          .append("(sampleRate());");

      // Handle connection in declaration: SinOsc s => dac;
      if (ds.callArgs() instanceof ChuckAST.BinaryExp be && be.op() == ChuckAST.Operator.CHUCK) {
        sb.append("\n").append(ds.name()).append(".chuck(").append(visitExp(be.rhs())).append(");");
      }
      return sb.toString();
    } else if (stmt instanceof ChuckAST.WhileStmt ws) {
      String cond = visitExp(ws.condition());
      if (cond.equals("1")) cond = "true";
      return "while (" + cond + ") " + visitStmt(ws.body());
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
    } else if (stmt instanceof ChuckAST.ReturnStmt rs) {
      return "return" + (rs.exp() != null ? " " + visitExp(rs.exp()) : "") + ";";
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
        // s => dac
        return visitExp(be.lhs()) + ".chuck(" + visitExp(be.rhs()) + ")";
      } else if (be.op() == ChuckAST.Operator.AT_CHUCK) {
        // array literal assignment: [0,1] @=> int hi[]
        if (be.rhs() instanceof ChuckAST.DeclExp de) {
          String type = mapType(de.type());
          return type + "[] " + de.name() + " = " + visitExp(be.lhs());
        }
        return visitExp(be.rhs()) + " = " + visitExp(be.lhs());
      } else if (be.op() == ChuckAST.Operator.DUR_MUL) {
        // 1::second
        String unit = visitExp(be.rhs());
        String val = visitExp(be.lhs());
        return unit + "(" + val + ")";
      } else if (be.op() == ChuckAST.Operator.ASSIGN) {
        return visitExp(be.lhs()) + " = " + visitExp(be.rhs());
      }
      return "(" + visitExp(be.lhs()) + " " + mapOp(be.op()) + " " + visitExp(be.rhs()) + ")";
    } else if (exp instanceof ChuckAST.CallExp ce) {
      String base = visitExp(ce.base());
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
      return "new long[]{"
          + ale.elements().stream().map(this::visitExp).collect(Collectors.joining(", "))
          + "}";
    } else if (exp instanceof ChuckAST.ArrayAccessExp aae) {
      return visitExp(aae.base()) + "[" + visitExp(aae.indices().get(0)) + "]";
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
    return type;
  }

  private String mapOp(ChuckAST.Operator op) {
    return switch (op) {
      case PLUS -> "+";
      case MINUS -> "-";
      case TIMES -> "*";
      case DIVIDE -> "/";
      case EQ -> "==";
      case LT -> "<";
      case GT -> ">";
      case PERCENT -> "%";
      default -> op.name();
    };
  }

  private String indent(String s, int levels) {
    String prefix = "    ".repeat(levels);
    return prefix + s.replace("\n", "\n" + prefix);
  }
}
