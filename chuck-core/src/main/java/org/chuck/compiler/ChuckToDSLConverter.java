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
  private final List<ChuckAST.DeclStmt> arraysToInit = new ArrayList<>();
  private final List<ChuckAST.DeclStmt> fields = new ArrayList<>();
  private boolean isFieldMode = false;
  private String currentClassName = null;

  public String convert(List<ChuckAST.Stmt> program, String className) {
    globals.clear();
    userClasses.clear();
    userClassParents.clear();
    userFunctions.clear();
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
        userFunctions.add(safeName(fds.name()));
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
          init = " = new ChuckArray(\"" + baseType + "\", " + size + ")";
        }
      } else if (isUGen(type)
          || userClasses.contains(type)
          || (!isPrimitive(type) && !type.equals("String") && !type.equals("Object"))) {
        init = " = new " + type + "()";
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
                    + ".size(); "
                    + loopVar
                    + "++) {\n",
                2));
        String init = isUGen(baseType) ? "new " + baseType + "()" : "new " + baseType + "()";
        sb.append(indent(ds.name() + ".setObject(" + loopVar + ", " + init + ");\n", 3));
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
    return userFunctions.contains(name) || name.equals("zero") || name.equals("pole") || name.equals("radius") || name.equals("width") || name.equals("sync") || name.equals("delay") || name.equals("max") || name.equals("next") || name.equals("pluck") || name.equals("freq") || name.equals("gain") || name.equals("Q");
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
    if ("ChuckDuration".equals(type)) return "(" + code + ".samples() != 0.0)";
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
      if (init.endsWith(";")) init = init.substring(0, init.length() - 1);
      String cond = "true";
      if (fs.condition() instanceof ChuckAST.ExpStmt es) {
        cond = visitBoolExp(es.exp());
      } else if (fs.condition() != null) {
        cond = visitStmt(fs.condition());
        if (cond.endsWith(";")) cond = cond.substring(0, cond.length() - 1);
      }
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
            while(baseType.endsWith("[]")) baseType = baseType.substring(0, baseType.length() - 2);
            varTypes.put(ds.name(), type); // Preserve full array type (e.g. BiQuad[])
            return declType + " " + ds.name() + " = new ChuckArray(\"" + baseType + "\", " + size + ");";
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
            return ds.name() + ".chuck(" + rhs + ");";
          }
          sb.append(" = new ").append(mappedType).append("();");
          sb.append("\n");
          sb.append(indent(ds.name() + ".chuck(" + rhs + ");", 0));
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
        sb.append(" = new ").append(type).append("()");
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
      String body = visitStmt(fes.body());
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
      String name = fds.name();
      if (name.equals("assert")) name = "_CHUCK_INTERNAL_ASSERT_";
      name = name.replace("@", "_CHUCK_SPECIAL_");
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
                userFunctions.add(safeName(fds.name()));
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
      if (id.name().equals("IO")) return "ChuckIO";
      if (id.name().equals("samp")
          || id.name().equals("ms")
          || id.name().equals("second")
          || id.name().equals("minute")
          || id.name().equals("hour")
          || id.name().equals("day")
          || id.name().equals("week")) {
        return id.name() + "()";
      }
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
        if (isPrimitive(lType) && (rType.equals("Complex") || rType.equals("Polar")) && be.op() == ChuckAST.Operator.TIMES) {
            return rhsCode + ".times((float)(" + lhsCode + "))";
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
        return rhsCode + " " + op + "= " + lhsCode;
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
        if (typeOf(be.lhs()).equals("String") && typeOf(be.rhs()).equals("String")) {
          String op = mapOp(be.op());
          return "(" + lhsCode + ".compareTo(" + rhsCode + ") " + op + " 0)";
        }
        if (isDur(be.lhs()) || isDur(be.rhs())) {
          String l = isDur(be.lhs()) ? lhsCode + ".samples()" : lhsCode;
          String r = isDur(be.rhs()) ? rhsCode + ".samples()" : rhsCode;
          return "(" + l + " " + mapOp(be.op()) + " " + r + ")";
        }
      }
      if (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK) {
        // Special case: duration => now OR event => now OR event_array => now
        if (be.rhs() instanceof ChuckAST.IdExp id && id.name().equals("now")) {
          return "advance(" + lhsCode + ")";
        }

        // Handle: Machine.getGlobalObject(...) $ int[] @=> data;
        if (be.lhs() instanceof ChuckAST.CastExp ce) {
          String targetType = mapType(ce.targetType());
          if (targetType.endsWith("[]") && !targetType.startsWith("ChuckEvent"))
            targetType = "ChuckArray";
          return rhsCode + " = (" + targetType + ")(" + visitExp(ce.value()) + ")";
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
            return lhsCode + ".chuck(" + de.name() + ")";
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
              arg = arg + ".samples()";
            } else if (lhsType.equals("double")
                || lhsType.equals("long")
                || lhsType.equals("String")
                || lhsType.equals("Object")) {
              // Known String methods
              if (member.equals("read")
                  || member.equals("write")
                  || member.equals("path")
                  || member.equals("name")) {
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
            return "org.chuck.core.ChuckDSL.set"
                + member.substring(0, 1).toUpperCase()
                + member.substring(1)
                + "("
                + visitExp(de.base())
                + ", "
                + arg
                + ")";
          }
          return visitExp(de.base()) + "." + member + "(" + arg + ")";
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
              baseCode = "((ChuckArray)" + baseCode + ".getObject(" + iCode + "))";
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
            String cast = isPrimitive(rhsType) ? "(" + rhsType + ")" : "";
            String typePrefix = (be.rhs() instanceof ChuckAST.DeclExp) ? rhsType + " " : "";
            return typePrefix + rhsCode + " = " + cast + "(" + lhsCode + ")";
          }
          if (rhsType != null && isConnectionTargetType(rhsType)) {
            return lhsCode + ".chuck(" + rhsCode + ")";
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

        // Default: s => dac
        return lhsCode + ".chuck(" + rhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.UNCHUCK) {
        return lhsCode + ".unchuck(" + rhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.DUR_MUL) {
        // 1::second -> second().times(1)
        if (isDur(be.rhs())) return rhsCode + ".times(" + lhsCode + ")";
        if (isDur(be.lhs())) return lhsCode + ".times(" + rhsCode + ")";
        return rhsCode + ".times(" + lhsCode + ")";
      } else if (be.op() == ChuckAST.Operator.ASSIGN) {
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
        if ("ChuckArray".equals(typeOf(be.lhs()))) {
          return lhsCode + ".append(" + rhsCode + ")";
        }
      }
      if (lhsCode.equals("now()")) lhsCode = "samp(now())";
      if (rhsCode.equals("now()")) rhsCode = "samp(now())";

      if (be.op() == ChuckAST.Operator.LE) {
        if ("cherr".equals(lhsCode)
            || "chout".equals(lhsCode)
            || lhsCode.startsWith("cherr.print")
            || lhsCode.startsWith("chout.print")) {
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
        String member = de.member();

        if (member.equals("charAt")) {
          return baseCode + ".charAt((int)(" + visitExp(ce.args().get(0)) + "))";
        }
        if (member.equals("getInt") || member.equals("getFloat") || member.equals("getString")) {
          return baseCode + "." + member + "((int)(" + visitExp(ce.args().get(0)) + "))";
        }

        String recType = typeOf(de.base());
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

        if (elemType.equals("String")) return "(String)" + baseCode + ".getObject(" + idxCode + ")";
        if (elemType.equals("double") || elemType.equals("float"))
          return baseCode + ".getFloat(" + idxCode + ")";
        if (elemType.equals("long") || elemType.equals("int"))
          return baseCode + ".getInt(" + idxCode + ")";

        // Detect if this is an intermediate array level
        if (baseType.contains("[][]")) {
          return "((ChuckArray)" + baseCode + ".getObject(" + idxCode + "))";
        }
        if (!isPrimitive(baseType.replace("[]", ""))) {
          return "((" + baseType.replace("[]", "") + ")" + baseCode + ".getObject(" + idxCode + "))";
        }
        return baseCode + ".getObject(" + idxCode + ")";
      }

      // Chained access: c[0][1]
      String res = visitExp(aae.base());
      for (ChuckAST.Exp idx : aae.indices()) {
        String iCode = wrapInt(idx);
        if (res.contains("ChuckArray") || res.contains("getObject")) {
          res = "((ChuckArray)" + res + ".getObject(" + iCode + "))";
        } else {
          res = res + "[(int)(" + visitExp(idx) + ")]";
        }
      }
      return res;
    } else if (exp instanceof ChuckAST.DotExp de) {
      String base = visitExp(de.base());
      String baseType = typeOf(de.base());
      String member = safeName(de.member());

      if (member.equals("newline")) return "ChIO.newline()";
      if (member.equals("nl")) return "ChIO.nl()";
      if (base.equals("Std") && member.equals("mtof")) return "mtof";
      if (base.equals("Std") && member.equals("ftom")) return "ftom";
      if (base.equals("Math") && member.startsWith("random")) return "Math." + member;

      // Known ChuckArray methods
      if (baseType.equals("ChuckArray")) {
        if (member.equals("size") || member.equals("cap") || member.equals("length"))
          return base + ".size()";
        if (member.equals("popBack") || member.equals("popFront") || member.equals("erase"))
          return base + "." + member;
        if (member.equals("clear") || member.equals("getKeys")) return base + "." + member;
      }

      // If it's a user class, assume field access unless we're in a CallExp
      if (userClasses.contains(baseType) || userClasses.contains(base)) {
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

      // Default to field access for user classes and others
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
          if (!t.equals("long") && !t.equals("double") && !t.equals("String") && !t.equals("ChuckArray") && !t.equals("int") && !t.equals("float")) {
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
              
      String javaArrayType =
          firstObjType != null
              ? "Object[]"
              : looksLikeString
              ? "String[]"
              : (looksLikeFloat ? "double[]" : (looksLikeArray ? "ChuckArray[]" : "long[]"));
              
      return "new ChuckArray(\"" + baseType + "\", new " + javaArrayType + "{" + elements + "})";
    } else if (exp instanceof ChuckAST.VectorLitExp vle) {
      return "new double[]{"
          + vle.elements().stream().map(this::visitExp).collect(Collectors.joining(", "))
          + "}";
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
      return "(" + type + ")(" + visitExp(ce.value()) + ")";
    } else if (exp instanceof ChuckAST.UnaryExp ue) {
      String op =
          switch (ue.op()) {
            case MINUS -> "-";
            case PLUS_PLUS, POSTFIX_PLUS_PLUS -> "++";
            case MINUS_MINUS, POSTFIX_MINUS_MINUS -> "--";
            case LOGICAL_NOT -> "!";
            default -> ue.op().name();
          };
      if (ue.isPostfix()) {
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
          case "complex" -> "Complex";
          case "polar" -> "Polar";
          case "ADSR" -> "Adsr";
          default -> type;        };
    return mapped + suffix;
  }

  private String wrapInt(ChuckAST.Exp exp) {
    String code = visitExp(exp);
    String type = typeOf(exp);
    if ("String".equals(type)) return code; // Don't cast string to int
    return "(int)(" + code + ")";
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
