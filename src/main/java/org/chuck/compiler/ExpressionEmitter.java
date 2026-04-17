package org.chuck.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.chuck.core.*;
import org.chuck.core.instr.*;

/** Specialized emitter for ChucK expressions. */
public class ExpressionEmitter {

  private final ChuckEmitter parent;

  public ExpressionEmitter(ChuckEmitter parent) {
    this.parent = parent;
  }

  /**
   * Emits instructions for the given expression.
   *
   * <p><b>Stack Protocol:</b>
   *
   * <ul>
   *   <li>[Before]: (Empty or current context)
   *   <li>[After]: Exactly one value pushed (int, float, or object reference), unless the
   *       expression is a void call.
   * </ul>
   */
  void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
    if (exp == null) return;
    if (code != null) code.setActiveLineNumber(exp.line());
    switch (exp) {
      case ChuckAST.IntExp e -> {
        code.addInstruction(new PushInstrs.PushInt(e.value()));
      }
      case ChuckAST.FloatExp e -> {
        int idx = code.addConstant(e.value());
        code.addInstruction(new PushInstrs.LdcFloat(idx));
      }
      case ChuckAST.StringExp e -> {
        int idx = code.addConstant(e.value());
        code.addInstruction(new PushInstrs.LdcString(idx));
      }
      case ChuckAST.MeExp _ -> code.addInstruction(new PushInstrs.PushMe());
      case ChuckAST.UnaryExp e -> {
        if (e.op() == ChuckAST.Operator.S_OR) {
          String innerType = parent.getVarType(e.exp());
          if (innerType != null && parent.getUserClassRegistry().containsKey(innerType)) {
            ChuckCode opCode = parent.getFunctions().get("__pub_op__!:1");
            if (opCode == null) {
              opCode = parent.getFunctions().get("__op__!:1");
            }
            if (opCode != null) {
              this.emitExpression(e.exp(), code);
              code.addInstruction(new CallFunc(opCode, 1));
              return;
            }
          }
        }
        this.emitExpression(e.exp(), code);
        switch (e.op()) {
          case MINUS -> code.addInstruction(new ArithmeticInstrs.NegateAny());
          case S_OR -> code.addInstruction(new LogicInstrs.LogicalNot());
          case PLUS -> {}
          default -> {}
        }
      }
      case ChuckAST.DeclExp e -> {
        if (!parent.isKnownType(e.type())) {
          throw error(e, "undefined type '" + e.type() + "'");
        }

        if (e.name().startsWith("@new_")) {
          boolean isArrayNew = e.name().startsWith("@new_array_");
          java.util.Set<String> primitives =
              java.util.Set.of(
                  "int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4",
                  "complex", "polar");
          if (primitives.contains(e.type()) && !isArrayNew) {
            throw error(e, "cannot use 'new' on primitive type '" + e.type() + "'");
          }
        }
        if (e.isStatic()
            && (parent.getCurrentClass() == null || parent.getLocalScopes().size() > 1)) {
          throw error(e, "static variables must be declared at class scope");
        }
        if (!e.arraySizes().isEmpty()
            && e.arraySizes().get(0) instanceof ChuckAST.IntExp sz0e
            && sz0e.value() < 0) {
          parent.getEmptyArrayVars().add(e.name());
        }
        if (!parent.getLocalTypeScopes().isEmpty()) {
          parent.getLocalTypeScopes().peek().put(e.name(), e.type());
        }
        if (e.isConst()) {
          parent.getConstants().add(e.name());
        }
        int argCount = 0;
        boolean isUserClass = parent.getUserClassRegistry().containsKey(e.type());
        boolean forceGlobal = e.isGlobal();
        boolean useGlobal = forceGlobal; // Top-level vars are shred-local by default in ChucK

        if (useGlobal) {
          String prevType = parent.getGlobalVarTypes().get(e.name());
          if (prevType != null
              && !prevType.equals(e.type())
              && !prevType.equals("int")
              && !prevType.equals("float")
              && !prevType.equals("string")) {
            throw new org.chuck.core.ChuckCompilerException(
                "global "
                    + prevType
                    + " '"
                    + e.name()
                    + "' has different type '"
                    + e.type()
                    + "' than already existing global "
                    + prevType
                    + " of the same name",
                parent.getCurrentFile(),
                e.line(),
                e.column());
          }
          parent.getGlobalVarTypes().put(e.name(), e.type());
        }

        boolean isBuiltinPseudoClass =
            Set.of("string", "vec2", "vec3", "vec4", "complex", "polar").contains(e.type());
        List<ChuckAST.Exp> ctorArgsList =
            switch (e.callArgs()) {
              case ChuckAST.CallExp call -> call.args();
              case ChuckAST.ArrayLitExp arr -> arr.elements();
              case null -> List.of();
              default -> List.of();
            };
        boolean hasExplicitCallArgsE = e.callArgs() != null;
        boolean hasZeroArgCtorE =
            isUserClass
                && !isBuiltinPseudoClass
                && !hasExplicitCallArgsE
                && parent.getUserClassRegistry().containsKey(e.type())
                && parent
                    .getUserClassRegistry()
                    .get(e.type())
                    .methods()
                    .containsKey(e.type() + ":0");
        boolean isArrayDeclE = !e.arraySizes().isEmpty();
        if (isUserClass
            && !isBuiltinPseudoClass
            && !e.isReference()
            && !isArrayDeclE
            && (hasExplicitCallArgsE || hasZeroArgCtorE)) {
          argCount = ctorArgsList.size();
          if (parent.isInPreCtor()) {
            code.addInstruction(new StackInstrs.PushThis());
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushField(
                    parent.getBaseType(e.type()),
                    e.name(),
                    argCount,
                    e.isReference(),
                    false,
                    parent.getUserClassRegistry()));
          } else {
            boolean isField =
                parent.getCurrentClass() != null
                    && (parent.getCurrentClassFields().contains(e.name())
                        || parent.hasInstanceField(parent.getCurrentClass(), e.name()));
            Integer localOffset =
                (forceGlobal || useGlobal || isField) ? null : parent.getLocalOffset(e.name());
            if (localOffset != null) {
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushLocal(
                      e.type(),
                      localOffset,
                      argCount,
                      e.isReference(),
                      false,
                      parent.getUserClassRegistry()));
            } else if (!forceGlobal
                && !useGlobal
                && !isField
                && !parent.getLocalScopes().isEmpty()) {
              Map<String, Integer> scope = parent.getLocalScopes().peek();
              localOffset = parent.getLocalCount();
              parent.setLocalCount(localOffset + 1);
              scope.put(e.name(), localOffset);
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushLocal(
                      e.type(),
                      localOffset,
                      argCount,
                      e.isReference(),
                      false,
                      parent.getUserClassRegistry()));
            } else if (isField) {
              code.addInstruction(new StackInstrs.PushThis());
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushField(
                      parent.getBaseType(e.type()),
                      e.name(),
                      argCount,
                      e.isReference(),
                      false,
                      parent.getUserClassRegistry()));
            } else {
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushGlobal(
                      parent.getBaseType(e.type()),
                      e.name(),
                      argCount,
                      e.isReference(),
                      false,
                      parent.getUserClassRegistry()));
            }
          }
          code.addInstruction(new StackInstrs.Dup());
          for (ChuckAST.Exp arg : ctorArgsList) {
            this.emitExpression(arg, code);
          }
          List<String> ctorArgTypes = ctorArgsList.stream().map(parent::getExprType).toList();
          String ctorKey = parent.getMethodKey(e.type(), ctorArgTypes);
          code.addInstruction(new ObjectInstrs.CallMethod(e.type(), argCount, ctorKey));
        } else if (isUserClass
            && !isBuiltinPseudoClass
            && !e.isReference()
            && isArrayDeclE
            && (hasExplicitCallArgsE || hasZeroArgCtorE)) {
          // Array declaration with per-element constructor: new Foo(ctorArg)[size]
          List<String> ctorArgTypes = ctorArgsList.stream().map(parent::getExprType).toList();
          String ctorKey = parent.getMethodKey(e.type(), ctorArgTypes);
          // Emit array size(s)
          for (ChuckAST.Exp sizeExp : e.arraySizes()) {
            this.emitExpression(sizeExp, code);
          }
          // Emit ctor args
          for (ChuckAST.Exp arg : ctorArgsList) {
            this.emitExpression(arg, code);
          }
          boolean isField =
              parent.getCurrentClass() != null
                  && (parent.getCurrentClassFields().contains(e.name())
                      || parent.hasInstanceField(parent.getCurrentClass(), e.name()));
          Integer localOffset =
              (forceGlobal || useGlobal || isField) ? null : parent.getLocalOffset(e.name());
          if (localOffset != null) {
            code.addInstruction(
                new ObjectInstrs.InstantiateArrayWithCtorLocal(
                    e.type(),
                    localOffset,
                    e.arraySizes().size(),
                    ctorArgsList.size(),
                    ctorKey,
                    parent.getUserClassRegistry()));
          } else if (!forceGlobal && !useGlobal && !isField && !parent.getLocalScopes().isEmpty()) {
            Map<String, Integer> scope = parent.getLocalScopes().peek();
            localOffset = parent.getLocalCount();
            parent.setLocalCount(localOffset + 1);
            scope.put(e.name(), localOffset);
            code.addInstruction(
                new ObjectInstrs.InstantiateArrayWithCtorLocal(
                    e.type(),
                    localOffset,
                    e.arraySizes().size(),
                    ctorArgsList.size(),
                    ctorKey,
                    parent.getUserClassRegistry()));
          } else {
            code.addInstruction(
                new ObjectInstrs.InstantiateArrayWithCtorGlobal(
                    e.type(),
                    e.name(),
                    e.arraySizes().size(),
                    ctorArgsList.size(),
                    ctorKey,
                    parent.getUserClassRegistry()));
          }
        } else {
          if (e.callArgs() instanceof ChuckAST.CallExp call) {
            for (ChuckAST.Exp arg : call.args()) {
              this.emitExpression(arg, code);
            }
            argCount = call.args().size();
          }
          if (!e.arraySizes().isEmpty()) {
            for (ChuckAST.Exp sizeExp : e.arraySizes()) {
              this.emitExpression(sizeExp, code);
            }
            argCount = e.arraySizes().size();
          }

          boolean isObject = parent.isObjectType(e.type());
          boolean isArrayDecl = !e.arraySizes().isEmpty();

          if (parent.isInPreCtor()) {
            code.addInstruction(new StackInstrs.PushThis());
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushField(
                    parent.getBaseType(e.type()),
                    e.name(),
                    argCount,
                    e.isReference(),
                    isArrayDecl,
                    parent.getUserClassRegistry()));
          } else {
            boolean isField =
                parent.getCurrentClass() != null
                    && (parent.getCurrentClassFields().contains(e.name())
                        || parent.hasInstanceField(parent.getCurrentClass(), e.name()));
            Integer localOffset =
                (forceGlobal || useGlobal || isField) ? null : parent.getLocalOffset(e.name());
            if (localOffset != null) {
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushLocal(
                      e.type(),
                      localOffset,
                      argCount,
                      e.isReference(),
                      isArrayDecl,
                      parent.getUserClassRegistry()));
            } else if (!forceGlobal
                && !useGlobal
                && !isField
                && !parent.getLocalScopes().isEmpty()) {
              Map<String, Integer> scope = parent.getLocalScopes().peek();
              localOffset = parent.getLocalCount();
              parent.setLocalCount(localOffset + 1);
              scope.put(e.name(), localOffset);
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushLocal(
                      e.type(),
                      localOffset,
                      argCount,
                      e.isReference(),
                      isArrayDecl,
                      parent.getUserClassRegistry()));
            } else if (isField) {
              code.addInstruction(new StackInstrs.PushThis());
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushField(
                      parent.getBaseType(e.type()),
                      e.name(),
                      argCount,
                      e.isReference(),
                      isArrayDecl,
                      parent.getUserClassRegistry()));
            } else {
              code.addInstruction(
                  new ObjectInstrs.InstantiateSetAndPushGlobal(
                      parent.getBaseType(e.type()),
                      e.name(),
                      argCount,
                      e.isReference(),
                      isArrayDecl,
                      parent.getUserClassRegistry()));
            }
          }
        }
      }
      case ChuckAST.BinaryExp e -> {
        if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
          if (e.rhs() instanceof ChuckAST.DeclExp rDecl && rDecl.type().equals("auto")) {
            String inferredLhsType = parent.getTypeInferenceEngine().getExprType(e.lhs());
            if ("void".equals(inferredLhsType)) {
              throw new org.chuck.core.ChuckCompilerException(
                  "cannot infer 'auto' type from 'void'",
                  parent.getCurrentFile(),
                  e.line(),
                  e.column());
            }
            if ("null".equals(inferredLhsType)) {
              throw new org.chuck.core.ChuckCompilerException(
                  "cannot infer 'auto' type from 'null'",
                  parent.getCurrentFile(),
                  e.line(),
                  e.column());
            }
            if ("auto".equals(inferredLhsType)) {
              throw new org.chuck.core.ChuckCompilerException(
                  "cannot infer 'auto' type from another 'auto' variable",
                  parent.getCurrentFile(),
                  e.line(),
                  e.column());
            }
          }
          if (e.op() == ChuckAST.Operator.CHUCK
              && e.rhs() instanceof ChuckAST.DeclExp rDecl2
              && !rDecl2.arraySizes().isEmpty()) {
            ChuckAST.Exp sz = rDecl2.arraySizes().get(0);
            if (sz instanceof ChuckAST.IntExp szInt && szInt.value() < 0) {
              throw new org.chuck.core.ChuckCompilerException(
                  "cannot connect '=>' to empty array '[ ]' declaration",
                  parent.getCurrentFile(),
                  e.line(),
                  e.column());
            }
          }
          if (e.op() == ChuckAST.Operator.CHUCK
              && e.lhs() instanceof ChuckAST.DeclExp lDecl2
              && !lDecl2.arraySizes().isEmpty()) {
            ChuckAST.Exp sz = lDecl2.arraySizes().get(0);
            if (sz instanceof ChuckAST.IntExp szInt && szInt.value() < 0) {
              throw new org.chuck.core.ChuckCompilerException(
                  "cannot connect '=>' from empty array '[ ]' declaration",
                  parent.getCurrentFile(),
                  e.line(),
                  e.column());
            }
          }
          if (e.rhs() instanceof ChuckAST.DeclExp rDecl3 && rDecl3.isStatic()) {
            if (parent.getCurrentClass() == null || parent.getLocalScopes().size() > 1) {
              throw new org.chuck.core.ChuckCompilerException(
                  "static variables must be declared at class scope",
                  parent.getCurrentFile(),
                  e.line(),
                  e.column());
            }
            parent.checkStaticInitSource(e.lhs());
          }
          if (e.op() == ChuckAST.Operator.CHUCK
              && e.lhs() instanceof ChuckAST.IdExp lhsId2
              && parent.getEmptyArrayVars().contains(lhsId2.name())) {
            throw new org.chuck.core.ChuckCompilerException(
                "cannot connect empty array '" + lhsId2.name() + "[]' => to other UGens",
                parent.getCurrentFile(),
                e.line(),
                e.column());
          }
          if (e.op() == ChuckAST.Operator.AT_CHUCK
              && e.rhs() instanceof ChuckAST.DeclExp rDecl4
              && !rDecl4.arraySizes().isEmpty()
              && rDecl4.arraySizes().get(0) instanceof ChuckAST.IntExp szI4
              && szI4.value() >= 0) {
            throw new org.chuck.core.ChuckCompilerException(
                "cannot use '@=>' to assign to array declaration with explicit size",
                parent.getCurrentFile(),
                e.line(),
                e.column());
          }
          Object rhs = e.rhs();
          if (rhs instanceof ChuckAST.BlockStmt) {
            throw new org.chuck.core.ChuckCompilerException(
                "cannot '=>' from/to a multi-variable declaration",
                parent.getCurrentFile(),
                e.line(),
                e.column());
          }
          if (e.rhs() instanceof ChuckAST.DeclExp rDecl) {
            ChuckAST.DeclExp resolvedDecl = rDecl;
            if ("auto".equals(rDecl.type())) {
              String inferredType = parent.getExprType(e.lhs());
              if (inferredType == null) inferredType = "int";
              resolvedDecl =
                  new ChuckAST.DeclExp(
                      inferredType,
                      rDecl.name(),
                      rDecl.arraySizes(),
                      rDecl.callArgs(),
                      rDecl.isReference(),
                      rDecl.isStatic(),
                      rDecl.isGlobal(),
                      rDecl.isConst(),
                      rDecl.access(),
                      rDecl.doc(),
                      rDecl.line(),
                      rDecl.column());
            }

            boolean resolvedUseGlobal =
                resolvedDecl.isGlobal()
                    || (parent.getLocalScopes().size() <= 1 && parent.getCurrentClass() == null);
            if (!resolvedUseGlobal) {
              this.emitExpression(resolvedDecl, code);
              code.addInstruction(new StackInstrs.Pop());
            }
            this.emitExpression(e.lhs(), code);
            parent.emitChuckTarget(resolvedDecl, code, e.op());
          } else {
            // FileIO/IO => variable: read from file into variable, push good() as bool
            String lhsType = parent.getExprType(e.lhs());
            if (e.op() == ChuckAST.Operator.CHUCK
                && ("FileIO".equals(lhsType) || "IO".equals(lhsType))
                && e.rhs() instanceof ChuckAST.IdExp rhsId) {
              String rhsType = parent.getVarTypeByName(rhsId.name());
              if (rhsType == null) rhsType = "float";
              Integer localOffset = parent.getLocalOffset(rhsId.name());
              String varName = (localOffset == null) ? rhsId.name() : null;
              this.emitExpression(e.lhs(), code);
              code.addInstruction(new MiscInstrs.FileIOReadTo(varName, localOffset, rhsType));
            } else {
              this.emitExpression(e.lhs(), code);
              parent.emitChuckTarget(e.rhs(), code, e.op());
            }
          }
        } else if (e.op() == ChuckAST.Operator.UNCHUCK) {
          this.emitExpression(e.lhs(), code);
          this.emitExpression(e.rhs(), code);
          code.addInstruction(new MiscInstrs.ChuckUnchuck());
        } else if (e.op() == ChuckAST.Operator.UPCHUCK) {
          String lhsType = parent.getVarType(e.lhs());
          if (lhsType != null && parent.getUserClassRegistry().containsKey(lhsType)) {
            String rhsType = parent.getVarType(e.rhs());
            String rhsName = rhsType != null ? rhsType : lhsType;
            throw new org.chuck.core.ChuckCompilerException(
                "cannot resolve operator '=^' on types '" + lhsType + "' and '" + rhsName + "'",
                parent.getCurrentFile(),
                e.line(),
                e.column());
          }
          this.emitExpression(e.lhs(), code);
          parent.emitChuckTarget(e.rhs(), code, e.op());
        } else if (e.op() == ChuckAST.Operator.APPEND) {
          this.emitExpression(e.lhs(), code);
          this.emitExpression(e.rhs(), code);
          code.addInstruction(new ObjectInstrs.CallMethod("append", 1));
        } else if (e.op() == ChuckAST.Operator.NEW) {
          if (e.rhs() instanceof ChuckAST.IntExp szNew && szNew.value() < 0) {
            String typeName = e.lhs() instanceof ChuckAST.IdExp tid ? tid.name() : "?";
            throw new org.chuck.core.ChuckCompilerException(
                "cannot use 'new " + typeName + "[]' with empty brackets",
                parent.getCurrentFile(),
                e.line(),
                e.column());
          }
          this.emitExpression(e.rhs(), code);
          code.addInstruction(new ArrayInstrs.NewArray(null, 1));
        } else if (e.op() == ChuckAST.Operator.PLUS_CHUCK
            || e.op() == ChuckAST.Operator.MINUS_CHUCK
            || e.op() == ChuckAST.Operator.TIMES_CHUCK
            || e.op() == ChuckAST.Operator.DIVIDE_CHUCK
            || e.op() == ChuckAST.Operator.PERCENT_CHUCK) {
          if (e.rhs() instanceof ChuckAST.DeclExp) {
            throw error(e, "cannot use compound assignment operator with a variable declaration");
          }
          if (e.op() == ChuckAST.Operator.PLUS_CHUCK
              && e.lhs() instanceof ChuckAST.IntExp lhsInt
              && lhsInt.value() == 1
              && e.rhs() instanceof ChuckAST.IdExp rhsId) {
            String rhsType = parent.getVarTypeByName(rhsId.name());
            if (rhsType != null && parent.getUserClassRegistry().containsKey(rhsType)) {
              ChuckCode opCode = parent.getFunctions().get("__pub_op__++:1");
              if (opCode == null) {
                opCode = parent.getFunctions().get("__op__++:1");
              }
              if (opCode != null) {
                this.emitExpression(e.rhs(), code);
                code.addInstruction(new CallFunc(opCode, 1));
                return;
              }
            }
          }
          if (e.rhs() instanceof ChuckAST.IdExp rid) {
            if (rid.name().equals("now")) {
              throw error(e, "cannot perform compound assignment on 'now'");
            }
            if (rid.name().equals("pi") || rid.name().equals("e") || rid.name().equals("maybe")) {
              throw error(e, "cannot assign to read-only value '" + rid.name() + "'");
            }
          } else if (e.rhs() instanceof ChuckAST.DotExp rdot
              && rdot.base() instanceof ChuckAST.IdExp baseId
              && baseId.name().equals("Math")) {
            switch (rdot.member()) {
              case "PI", "TWO_PI", "HALF_PI", "E", "INFINITY", "NEGATIVE_INFINITY", "NaN", "nan" ->
                  throw error(
                      e, "'Math." + rdot.member() + "' is a constant, and is not assignable");
              default -> {}
            }
          }
          ChuckAST.Operator arith =
              switch (e.op()) {
                case PLUS_CHUCK -> ChuckAST.Operator.PLUS;
                case MINUS_CHUCK -> ChuckAST.Operator.MINUS;
                case TIMES_CHUCK -> ChuckAST.Operator.TIMES;
                case DIVIDE_CHUCK -> ChuckAST.Operator.DIVIDE;
                case PERCENT_CHUCK -> ChuckAST.Operator.PERCENT;
                default -> ChuckAST.Operator.PLUS;
              };
          this.emitExpression(e.rhs(), code);
          this.emitExpression(e.lhs(), code);
          String targetType = parent.getExprType(e.rhs());
          if (ChuckEmitter.isVecType(targetType)) {
            switch (arith) {
              case PLUS -> {
                if ("complex".equals(targetType)) code.addInstruction(new ComplexInstrs.Add());
                else if ("polar".equals(targetType)) code.addInstruction(new PolarInstrs.Add());
                else code.addInstruction(new VecInstrs.Add());
              }
              case MINUS -> {
                if ("complex".equals(targetType)) code.addInstruction(new ComplexInstrs.Sub());
                else if ("polar".equals(targetType)) code.addInstruction(new PolarInstrs.Sub());
                else code.addInstruction(new VecInstrs.Sub());
              }
              case TIMES -> {
                String opType = parent.getExprType(e.lhs());
                if ("complex".equals(targetType) && "complex".equals(opType))
                  code.addInstruction(new ComplexInstrs.Mul());
                else if ("polar".equals(targetType) && "polar".equals(opType))
                  code.addInstruction(new PolarInstrs.Mul());
                else if (opType == null || "float".equals(opType) || "int".equals(opType))
                  code.addInstruction(new VecInstrs.VecScale());
                else code.addInstruction(new ArithmeticInstrs.TimesAny());
              }
              case DIVIDE -> {
                String opType = parent.getExprType(e.lhs());
                if ("complex".equals(targetType) && "complex".equals(opType))
                  code.addInstruction(new ComplexInstrs.Div());
                else if ("polar".equals(targetType) && "polar".equals(opType))
                  code.addInstruction(new PolarInstrs.Div());
                else if (opType == null || "float".equals(opType) || "int".equals(opType)) {
                  code.addInstruction(new PushInstrs.PushFloat(1.0));
                  code.addInstruction(new StackInstrs.Swap());
                  code.addInstruction(new ArithmeticInstrs.DivideAny());
                  code.addInstruction(new VecInstrs.VecScale());
                } else code.addInstruction(new ArithmeticInstrs.DivideAny());
              }
              default -> code.addInstruction(new ArithmeticInstrs.AddAny());
            }
          } else {
            switch (arith) {
              case PLUS -> code.addInstruction(new ArithmeticInstrs.AddAny());
              case MINUS -> code.addInstruction(new ArithmeticInstrs.MinusAny());
              case TIMES -> code.addInstruction(new ArithmeticInstrs.TimesAny());
              case DIVIDE -> code.addInstruction(new ArithmeticInstrs.DivideAny());
              case PERCENT -> code.addInstruction(new ArithmeticInstrs.ModuloAny());
              default -> {}
            }
          }
          parent.emitChuckTarget(e.rhs(), code, e.op());
        } else if (e.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS
            || e.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS) {
          if (e.rhs() instanceof ChuckAST.DeclExp) {
            throw error(e, "cannot use compound assignment operator with a variable declaration");
          }
          boolean isPostfixPlus = e.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS;
          String rhsType = parent.getVarType(e.rhs());
          if (rhsType != null && parent.getUserClassRegistry().containsKey(rhsType)) {
            String opSym = isPostfixPlus ? "++" : "--";
            List<String> pArgTypes = List.of(rhsType);
            String fullKey = parent.getMethodKey("__pub_op__" + opSym, pArgTypes);
            String privKey = parent.getMethodKey("__op__" + opSym, pArgTypes);
            ChuckCode opCode = parent.getFunctions().get(fullKey);
            if (opCode == null) opCode = parent.getFunctions().get(privKey);
            if (opCode == null) {
              String opKey = opSym + ":1";
              opCode = parent.getFunctions().get("__pub_op__" + opKey);
              if (opCode == null) opCode = parent.getFunctions().get("__op__" + opKey);
            }
            if (opCode != null) {
              this.emitExpression(e.rhs(), code);
              code.addInstruction(new CallFunc(opCode, 1));
              return;
            }
          }
          if (e.rhs() instanceof ChuckAST.DotExp dot) {
            String potentialClassName = parent.resolveClassName(dot.base(), code);
            if (potentialClassName != null) {
              UserClassDescriptor classDesc = parent.getUserClassRegistry().get(potentialClassName);
              if (classDesc != null
                  && (classDesc.staticInts().containsKey(dot.member())
                      || classDesc.staticObjects().containsKey(dot.member()))) {
                code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, dot.member()));
                code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, dot.member()));
                code.addInstruction(new PushInstrs.PushInt(1));
                if (isPostfixPlus) code.addInstruction(new ArithmeticInstrs.AddAny());
                else code.addInstruction(new ArithmeticInstrs.MinusAny());
                code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, dot.member()));
                return;
              }
            }
          }
          this.emitExpression(e.rhs(), code);
          this.emitExpression(e.rhs(), code);
          code.addInstruction(new PushInstrs.PushInt(1));
          if (isPostfixPlus) code.addInstruction(new ArithmeticInstrs.AddAny());
          else code.addInstruction(new ArithmeticInstrs.MinusAny());
          parent.emitChuckTarget(e.rhs(), code, e.op());
        } else if (e.op() == ChuckAST.Operator.WRITE_IO
            || (e.op() == ChuckAST.Operator.LE && parent.isIOType(parent.getExprType(e.lhs())))) {
          this.emitExpression(e.lhs(), code);
          this.emitExpression(e.rhs(), code);
          code.addInstruction(new ArrayInstrs.ShiftLeftOrAppend());
        } else if (e.op() == ChuckAST.Operator.SWAP) {
          parent.emitSwapTarget(e.lhs(), e.rhs(), code);
        } else if (e.op() == ChuckAST.Operator.AT_CHUCK) {
          this.emitExpression(e.lhs(), code);
          switch (e.rhs()) {
            case ChuckAST.IdExp id -> {
              Integer lo = parent.getLocalOffset(id.name());
              if (lo != null) code.addInstruction(new VarInstrs.StoreLocal(lo));
              else code.addInstruction(new VarInstrs.SetGlobalObjectOnly(id.name()));
            }
            case ChuckAST.DotExp dot -> {
              String potentialClassName = parent.resolveClassName(dot.base(), code);
              if (potentialClassName != null) {
                String actualClassWithField =
                    parent.findStaticFieldOwner(potentialClassName, dot.member());
                if (actualClassWithField != null) {
                  code.addInstruction(new PushInstrs.PushString(actualClassWithField));
                  code.addInstruction(
                      new FieldInstrs.SetStatic(actualClassWithField, dot.member()));
                  return;
                }
              }
              // If not static, might be a member of an instance
              this.emitExpression(dot.base(), code);
              code.addInstruction(new SetMemberIntByName(dot.member()));
            }
            case ChuckAST.ArrayAccessExp ae -> {
              this.emitExpression(ae.base(), code);
              String baseType = parent.getExprType(ae.base());
              String elemType =
                  (baseType != null && baseType.endsWith("[]"))
                      ? baseType.substring(0, baseType.length() - 2)
                      : null;
              for (int i = 0; i < ae.indices().size(); i++) {
                this.emitExpression(ae.indices().get(i), code);
                if (i < ae.indices().size() - 1) code.addInstruction(new ArrayInstrs.GetArrayInt());
                else {
                  if (ae.indices().size() == 1 && "int".equals(elemType))
                    code.addInstruction(new ArrayInstrs.SetArrayIntFast());
                  else if (ae.indices().size() == 1 && "float".equals(elemType))
                    code.addInstruction(new ArrayInstrs.SetArrayFloatFast());
                  else code.addInstruction(new ArrayInstrs.SetArrayInt());
                }
              }
            }
            default -> {}
          }
        } else if (e.op() == ChuckAST.Operator.ASSIGN) {
          this.emitExpression(e.rhs(), code);
          switch (e.lhs()) {
            case ChuckAST.IdExp id -> {
              Integer localOffset = parent.getLocalOffset(id.name());
              if (localOffset != null) code.addInstruction(new VarInstrs.StoreLocal(localOffset));
              else code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(id.name()));
            }
            case ChuckAST.DotExp dot -> {
              String potentialClassName = parent.resolveClassName(dot.base(), code);
              if (potentialClassName != null) {
                String actualClassWithField =
                    parent.findStaticFieldOwner(potentialClassName, dot.member());
                if (actualClassWithField != null) {
                  code.addInstruction(new PushInstrs.PushString(actualClassWithField));
                  code.addInstruction(
                      new FieldInstrs.SetStatic(actualClassWithField, dot.member()));
                  return;
                }
              }
              // If not static, might be a member of an instance
              this.emitExpression(dot.base(), code);
              code.addInstruction(new SetMemberIntByName(dot.member()));
            }
            case ChuckAST.ArrayAccessExp ae -> {
              this.emitExpression(ae.base(), code);
              String baseType = parent.getExprType(ae.base());
              String elemType =
                  (baseType != null && baseType.endsWith("[]"))
                      ? baseType.substring(0, baseType.length() - 2)
                      : null;
              for (int i = 0; i < ae.indices().size(); i++) {
                this.emitExpression(ae.indices().get(i), code);
                if (i < ae.indices().size() - 1) code.addInstruction(new ArrayInstrs.GetArrayInt());
                else {
                  if (ae.indices().size() == 1 && "int".equals(elemType))
                    code.addInstruction(new ArrayInstrs.SetArrayIntFast());
                  else if (ae.indices().size() == 1 && "float".equals(elemType))
                    code.addInstruction(new ArrayInstrs.SetArrayFloatFast());
                  else code.addInstruction(new ArrayInstrs.SetArrayInt());
                }
              }
            }
            default -> {}
          }
        } else if (e.op() == ChuckAST.Operator.DUR_MUL) {
          this.emitExpression(e.lhs(), code);
          if (e.rhs() instanceof ChuckAST.IdExp id) {
            code.addInstruction(new MiscInstrs.CreateDuration(id.name()));
          }
        } else {
          if (e.op() == ChuckAST.Operator.PLUS) {
            if (e.lhs() instanceof ChuckAST.IdExp(String fnName, int _, int _)) {
              boolean isFuncRef =
                  parent.getFunctions().keySet().stream().anyMatch(k -> k.startsWith(fnName + ":"));
              if (isFuncRef)
                throw error(e, "cannot perform '+' on '[fun]" + fnName + "()' and value");
            }
            if (e.lhs() instanceof ChuckAST.DotExp lhsDot
                && lhsDot.base() instanceof ChuckAST.IdExp baseId
                && parent.getUserClassRegistry().containsKey(baseId.name())) {
              UserClassDescriptor d = parent.getUserClassRegistry().get(baseId.name());
              String memName = lhsDot.member();
              boolean isMemberFunc =
                  d.methods().containsKey(memName)
                      || d.staticMethods().keySet().stream()
                          .anyMatch(k -> k.startsWith(memName + ":"));
              if (isMemberFunc)
                throw error(
                    e,
                    "cannot perform '+' on '[fun]"
                        + baseId.name()
                        + "."
                        + memName
                        + "()' and value");
            }
          }
          {
            String lhsType = parent.getExprType(e.lhs());
            if (lhsType != null && parent.getUserClassRegistry().containsKey(lhsType)) {
              String opSymbol = parent.getOpSymbol(e.op());
              if (opSymbol != null) {
                String rhsType = parent.getExprType(e.rhs());
                List<String> bArgTypes =
                    (rhsType != null) ? List.of(lhsType, rhsType) : List.of(lhsType);
                String fullKey = parent.getMethodKey("__pub_op__" + opSymbol, bArgTypes);
                String privKey = parent.getMethodKey("__op__" + opSymbol, bArgTypes);
                ChuckCode opFunc = parent.getFunctions().get(fullKey);
                if (opFunc == null) opFunc = parent.getFunctions().get(privKey);
                if (opFunc == null) {
                  opFunc = parent.getFunctions().get("__pub_op__" + opSymbol + ":2");
                  if (opFunc == null)
                    opFunc = parent.getFunctions().get("__op__" + opSymbol + ":2");
                }
                if (opFunc != null) {
                  this.emitExpression(e.lhs(), code);
                  this.emitExpression(e.rhs(), code);
                  code.addInstruction(new CallFunc(opFunc, 2));
                  return;
                }
                UserClassDescriptor desc = parent.getUserClassRegistry().get(lhsType);
                if (desc != null) {
                  List<String> mArgTypes = (rhsType != null) ? List.of(rhsType) : new ArrayList<>();
                  String mPubKey =
                      parent.resolveMethodKey(lhsType, "__pub_op__" + opSymbol, mArgTypes);
                  String mPrivKey =
                      parent.resolveMethodKey(lhsType, "__op__" + opSymbol, mArgTypes);
                  if (desc.methods().containsKey(mPubKey)
                      || desc.staticMethods().containsKey(mPubKey)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(
                        new ObjectInstrs.CallMethod("__pub_op__" + opSymbol, 1, mPubKey));
                    return;
                  }
                  if (desc.methods().containsKey(mPrivKey)
                      || desc.staticMethods().containsKey(mPrivKey)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(
                        new ObjectInstrs.CallMethod("__op__" + opSymbol, 1, mPrivKey));
                    return;
                  }
                }
              }
            }
          }
          {
            String lhsType = parent.getExprType(e.lhs());
            if ("complex".equals(lhsType) || "polar".equals(lhsType)) {
              boolean isPolar = "polar".equals(lhsType);
              ChuckInstr complexInstr =
                  switch (e.op()) {
                    case PLUS -> isPolar ? new PolarInstrs.Add() : new ComplexInstrs.Add();
                    case MINUS -> isPolar ? new PolarInstrs.Sub() : new ComplexInstrs.Sub();
                    case TIMES -> isPolar ? new PolarInstrs.Mul() : new ComplexInstrs.Mul();
                    case DIVIDE -> isPolar ? new PolarInstrs.Div() : new ComplexInstrs.Div();
                    default -> null;
                  };
              if (complexInstr != null) {
                this.emitExpression(e.lhs(), code);
                this.emitExpression(e.rhs(), code);
                code.addInstruction(complexInstr);
                return;
              }
            }
          }
          {
            String lhsTypeS = parent.getExprType(e.lhs());
            String rhsTypeS = parent.getExprType(e.rhs());
            if (e.op() == ChuckAST.Operator.TIMES
                && ("float".equals(lhsTypeS) || "int".equals(lhsTypeS))
                && ChuckEmitter.isVecType(rhsTypeS)) {
              this.emitExpression(e.rhs(), code);
              this.emitExpression(e.lhs(), code);
              code.addInstruction(new VecInstrs.VecScale());
              return;
            }
          }
          {
            String lhsType = parent.getExprType(e.lhs());
            if (ChuckEmitter.isVecType(lhsType)) {
              switch (e.op()) {
                case PLUS -> {
                  this.emitExpression(e.lhs(), code);
                  this.emitExpression(e.rhs(), code);
                  if ("complex".equals(lhsType)) code.addInstruction(new ComplexInstrs.Add());
                  else if ("polar".equals(lhsType)) code.addInstruction(new PolarInstrs.Add());
                  else code.addInstruction(new VecInstrs.Add());
                  return;
                }
                case MINUS -> {
                  this.emitExpression(e.lhs(), code);
                  this.emitExpression(e.rhs(), code);
                  if ("complex".equals(lhsType)) code.addInstruction(new ComplexInstrs.Sub());
                  else if ("polar".equals(lhsType)) code.addInstruction(new PolarInstrs.Sub());
                  else code.addInstruction(new VecInstrs.Sub());
                  return;
                }
                case TIMES -> {
                  String rhsType = parent.getExprType(e.rhs());
                  if ("complex".equals(lhsType) && "complex".equals(rhsType)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new ComplexInstrs.Mul());
                    return;
                  }
                  if ("polar".equals(lhsType) && "polar".equals(rhsType)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new PolarInstrs.Mul());
                    return;
                  }
                  if (rhsType == null || "float".equals(rhsType) || "int".equals(rhsType)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new VecInstrs.VecScale());
                    return;
                  }
                  if (("vec3".equals(lhsType) || "vec4".equals(lhsType))
                      && ("vec3".equals(rhsType) || "vec4".equals(rhsType))) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new VecInstrs.Cross());
                    return;
                  }
                  this.emitExpression(e.lhs(), code);
                  this.emitExpression(e.rhs(), code);
                  code.addInstruction(new VecInstrs.Dot());
                  return;
                }
                case DIVIDE -> {
                  String rhsType = parent.getExprType(e.rhs());
                  if ("complex".equals(lhsType) && "complex".equals(rhsType)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new ComplexInstrs.Div());
                    return;
                  }
                  if ("polar".equals(lhsType) && "polar".equals(rhsType)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new PolarInstrs.Div());
                    return;
                  }
                  if (rhsType == null || "float".equals(rhsType) || "int".equals(rhsType)) {
                    this.emitExpression(e.lhs(), code);
                    this.emitExpression(e.rhs(), code);
                    code.addInstruction(new PushInstrs.PushFloat(1.0));
                    code.addInstruction(new StackInstrs.Swap());
                    code.addInstruction(new ArithmeticInstrs.DivideAny());
                    code.addInstruction(new VecInstrs.VecScale());
                    return;
                  }
                }
                default -> {}
              }
            }
          }
          if (e.op() == ChuckAST.Operator.PLUS
              || e.op() == ChuckAST.Operator.MINUS
              || e.op() == ChuckAST.Operator.TIMES
              || e.op() == ChuckAST.Operator.DIVIDE) {
            java.util.Set<String> ugenGlobals = java.util.Set.of("dac", "blackhole", "adc");
            if (e.lhs() instanceof ChuckAST.IdExp lid && ugenGlobals.contains(lid.name()))
              throw error(e, "cannot perform arithmetic on UGen '" + lid.name() + "'");
            if (e.rhs() instanceof ChuckAST.IdExp rid && ugenGlobals.contains(rid.name()))
              throw error(e, "cannot perform arithmetic on UGen '" + rid.name() + "'");
          }
          String lhsType = parent.getExprType(e.lhs());
          String rhsType = parent.getExprType(e.rhs());
          boolean isInt = "int".equals(lhsType) && "int".equals(rhsType);
          boolean isFloat =
              ("float".equals(lhsType) || "int".equals(lhsType))
                  && ("float".equals(rhsType) || "int".equals(rhsType))
                  && !isInt;

          if (e.op() != ChuckAST.Operator.AND && e.op() != ChuckAST.Operator.OR) {
            this.emitExpression(e.lhs(), code);
            this.emitExpression(e.rhs(), code);
          }
          switch (e.op()) {
            case PLUS -> {
              if (isInt) code.addInstruction(new ArithmeticInstrs.AddInt());
              else if (isFloat) code.addInstruction(new ArithmeticInstrs.AddFloat());
              else code.addInstruction(new ArithmeticInstrs.AddAny());
            }
            case MINUS -> {
              if (isInt) code.addInstruction(new ArithmeticInstrs.MinusInt());
              else if (isFloat) code.addInstruction(new ArithmeticInstrs.MinusFloat());
              else code.addInstruction(new ArithmeticInstrs.MinusAny());
            }
            case TIMES -> {
              if (isInt) code.addInstruction(new ArithmeticInstrs.TimesInt());
              else if (isFloat) code.addInstruction(new ArithmeticInstrs.TimesFloat());
              else code.addInstruction(new ArithmeticInstrs.TimesAny());
            }
            case DIVIDE -> {
              if (isInt) code.addInstruction(new ArithmeticInstrs.DivideInt());
              else if (isFloat) code.addInstruction(new ArithmeticInstrs.DivideFloat());
              else code.addInstruction(new ArithmeticInstrs.DivideAny());
            }
            case PERCENT -> code.addInstruction(new ArithmeticInstrs.ModuloAny());

            case S_OR -> code.addInstruction(new ArithmeticInstrs.BitwiseOrAny());
            case S_AND -> code.addInstruction(new ArithmeticInstrs.BitwiseAndAny());
            case LT -> {
              if (isInt) code.addInstruction(new LogicInstrs.LtInt());
              else if (isFloat) code.addInstruction(new LogicInstrs.LtFloat());
              else code.addInstruction(new LogicInstrs.LessThanAny());
            }
            case LE -> {
              String lt = parent.getExprType(e.lhs());
              if ("IO".equals(lt) || "FileIO".equals(lt))
                code.addInstruction(new MiscInstrs.ChuckWriteIO());
              else if (isInt) code.addInstruction(new LogicInstrs.LeInt());
              else if (isFloat) code.addInstruction(new LogicInstrs.LeFloat());
              else code.addInstruction(new LogicInstrs.LessOrEqualAny());
            }
            case GT -> {
              if (isInt) code.addInstruction(new LogicInstrs.GtInt());
              else if (isFloat) code.addInstruction(new LogicInstrs.GtFloat());
              else code.addInstruction(new LogicInstrs.GreaterThanAny());
            }
            case GE -> {
              if (isInt) code.addInstruction(new LogicInstrs.GeInt());
              else if (isFloat) code.addInstruction(new LogicInstrs.GeFloat());
              else code.addInstruction(new LogicInstrs.GreaterOrEqualAny());
            }
            case EQ -> {
              if (isInt) code.addInstruction(new LogicInstrs.EqInt());
              else if (isFloat) code.addInstruction(new LogicInstrs.EqFloat());
              else code.addInstruction(new LogicInstrs.EqualsAny());
            }
            case NEQ -> {
              if (isInt) code.addInstruction(new LogicInstrs.NeqInt());
              else if (isFloat) code.addInstruction(new LogicInstrs.NeqFloat());
              else code.addInstruction(new LogicInstrs.NotEqualsAny());
            }
            case DUR_MUL -> {
              this.emitExpression(e.lhs(), code);
              if (e.rhs() instanceof ChuckAST.IdExp id)
                code.addInstruction(new MiscInstrs.CreateDuration(id.name()));
              else this.emitExpression(e.rhs(), code);
            }
            case WRITE_IO -> code.addInstruction(new MiscInstrs.ChuckWriteIO());
            case AND -> {
              String rt = parent.getExprType(e.rhs());
              String lt = parent.getExprType(e.lhs());
              boolean bothInt = "int".equals(lt) && "int".equals(rt);

              if ("Event".equals(lt) || "Event".equals(rt)) {
                this.emitExpression(e.lhs(), code);
                this.emitExpression(e.rhs(), code);
                code.addInstruction(new MiscInstrs.CreateEventConjunction());
              } else {
                this.emitExpression(e.lhs(), code);
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null);
                this.emitExpression(e.rhs(), code);
                if (bothInt) code.addInstruction(new LogicInstrs.AndInt());
                int endIdx = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalseAndPushFalse(endIdx));
              }
            }
            case OR -> {
              String rt = parent.getExprType(e.rhs());
              String lt = parent.getExprType(e.lhs());
              boolean bothInt = "int".equals(lt) && "int".equals(rt);

              if ("Event".equals(lt) || "Event".equals(rt)) {
                this.emitExpression(e.lhs(), code);
                this.emitExpression(e.rhs(), code);
                code.addInstruction(new MiscInstrs.CreateEventDisjunction());
              } else {
                this.emitExpression(e.lhs(), code);
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null);
                this.emitExpression(e.rhs(), code);
                if (bothInt) code.addInstruction(new LogicInstrs.OrInt());
                int endIdx = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfTrueAndPushTrue(endIdx));
              }
            }
            case SHIFT_LEFT ->
                code.addInstruction(new ArrayInstrs.ShiftLeftOrAppend(parent.getExprType(e.lhs())));
            default -> {}
          }
        }
      }
      case ChuckAST.IdExp e -> {
        Integer localOffset = parent.getLocalOffset(e.name());
        String varType = parent.getVarType(e);
        if (e.name().equals("this") && parent.getCurrentClass() == null)
          throw error(e, "keyword 'this' cannot be used outside class definition");
        if (e.name().equals("super") && parent.isInStaticFuncContext())
          throw error(e, "keyword 'super' cannot be used inside static functions");
        boolean isField =
            parent.getCurrentClass() != null
                && (parent.getCurrentClassFields().contains(e.name())
                    || parent.hasInstanceField(parent.getCurrentClass(), e.name()));
        if (isField) {
          code.addInstruction(new FieldInstrs.GetUserField(e.name()));
        } else if (localOffset != null) {
          if ("int".equals(varType)) code.addInstruction(new VarInstrs.LoadLocalInt(localOffset));
          else if ("float".equals(varType))
            code.addInstruction(new VarInstrs.LoadLocalFloat(localOffset));
          else code.addInstruction(new VarInstrs.LoadLocal(localOffset));
        } else if (parent.getCurrentClass() != null
            && parent.hasStaticField(parent.getCurrentClass(), e.name())) {
          code.addInstruction(new FieldInstrs.GetStatic(parent.getCurrentClass(), e.name()));
        } else if (varType != null) {
          if ("int".equals(varType)) code.addInstruction(new VarInstrs.GetGlobalInt(e.name()));
          else if ("float".equals(varType))
            code.addInstruction(new VarInstrs.GetGlobalFloat(e.name()));
          else code.addInstruction(new VarInstrs.GetGlobalObjectOrInt(e.name()));
        } else if (e.name().equals("null")) code.addInstruction(new PushInstrs.PushNull());
        else if (e.name().equals("true")) code.addInstruction(new PushInstrs.PushInt(1));
        else if (e.name().equals("false")) code.addInstruction(new PushInstrs.PushInt(0));
        else if (e.name().equals("now")) code.addInstruction(new PushInstrs.PushNow());
        else if (e.name().equals("dac")) code.addInstruction(new PushInstrs.PushDac());
        else if (e.name().equals("blackhole")) code.addInstruction(new PushInstrs.PushBlackhole());
        else if (e.name().equals("adc")) code.addInstruction(new PushInstrs.PushAdc());
        else if (e.name().equals("me")) code.addInstruction(new PushInstrs.PushMe());
        else if (e.name().equals("cherr")) code.addInstruction(new PushInstrs.PushCherr());
        else if (e.name().equals("chout")) code.addInstruction(new PushInstrs.PushChout());
        else if (e.name().equals("Machine")) code.addInstruction(new PushInstrs.PushMachine());
        else if (e.name().equals("maybe")) code.addInstruction(new PushInstrs.PushMaybe());
        else if (e.name().equals("this")) code.addInstruction(new StackInstrs.PushThis());
        else if (Set.of("second", "ms", "samp", "minute", "hour").contains(e.name())) {
          code.addInstruction(new PushInstrs.PushInt(1));
          code.addInstruction(new MiscInstrs.CreateDuration(e.name()));
        } else if (e.name().equals("pi")) code.addInstruction(new PushInstrs.PushFloat(Math.PI));
        else if (e.name().equals("e")) code.addInstruction(new PushInstrs.PushFloat(Math.E));
        else code.addInstruction(new VarInstrs.GetGlobalObjectOrInt(e.name()));
      }
      case ChuckAST.DotExp e -> {
        if (e.base() instanceof ChuckAST.IdExp supId && supId.name().equals("super")) {
          code.addInstruction(new StackInstrs.PushThis());
          code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
          return;
        }
        String potentialClassName = parent.resolveClassName(e.base(), code);
        if (potentialClassName != null) {
          String actualClassWithField = parent.findStaticFieldOwner(potentialClassName, e.member());
          if (actualClassWithField != null) {
            parent.checkAccess(actualClassWithField, e.member(), false, e.line(), e.column());
            code.addInstruction(new FieldInstrs.GetStatic(actualClassWithField, e.member()));
            return;
          }
        }
        if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
          if (e.member().equals("nl") || e.member().equals("newline"))
            code.addInstruction(new PushInstrs.PushString("\n"));
          else
            code.addInstruction(
                new FieldInstrs.GetBuiltinStatic("org.chuck.core.ChuckIO", e.member()));
          return;
        }
        if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
          switch (e.member()) {
            case "INFINITY", "infinity" ->
                code.addInstruction(new PushInstrs.PushFloat(Double.POSITIVE_INFINITY));
            case "NEGATIVE_INFINITY" ->
                code.addInstruction(new PushInstrs.PushFloat(Double.NEGATIVE_INFINITY));
            case "NaN", "nan" -> code.addInstruction(new PushInstrs.PushFloat(Double.NaN));
            case "PI", "pi" -> code.addInstruction(new PushInstrs.PushFloat(Math.PI));
            case "TWO_PI", "two_pi" -> code.addInstruction(new PushInstrs.PushFloat(2.0 * Math.PI));
            case "HALF_PI", "half_pi" ->
                code.addInstruction(new PushInstrs.PushFloat(Math.PI / 2.0));
            case "E", "e" -> code.addInstruction(new PushInstrs.PushFloat(Math.E));
            case "SQRT2", "sqrt2" -> code.addInstruction(new PushInstrs.PushFloat(Math.sqrt(2.0)));
            case "j" -> {
              code.addInstruction(new PushInstrs.PushFloat(0.0));
              code.addInstruction(new PushInstrs.PushFloat(1.0));
              code.addInstruction(new ArrayInstrs.NewArrayFromStack(2, "complex"));
            }
          }
          return;
        }
        // AI static constants: AI.MLP, AI.KNN, AI.Regression, etc.
        if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("AI")) {
          switch (e.member()) {
            case "MLP" -> code.addInstruction(new PushInstrs.PushInt(0));
            case "KNN", "kNN" -> code.addInstruction(new PushInstrs.PushInt(1));
            case "SVM" -> code.addInstruction(new PushInstrs.PushInt(2));
            case "GMM" -> code.addInstruction(new PushInstrs.PushInt(3));
            case "HMM" -> code.addInstruction(new PushInstrs.PushInt(4));
            case "LSTM" -> code.addInstruction(new PushInstrs.PushInt(5));
            case "Regression" -> code.addInstruction(new PushInstrs.PushInt(0));
            case "Classification" -> code.addInstruction(new PushInstrs.PushInt(1));
            // activation-function constants
            case "Sigmoid" -> code.addInstruction(new PushInstrs.PushInt(0));
            case "Tanh" -> code.addInstruction(new PushInstrs.PushInt(1));
            case "ReLU", "Relu" -> code.addInstruction(new PushInstrs.PushInt(2));
            case "Linear" -> code.addInstruction(new PushInstrs.PushInt(3));
            case "Softmax" -> code.addInstruction(new PushInstrs.PushInt(4));
            default -> code.addInstruction(new PushInstrs.PushInt(0));
          }
          return;
        }
        if (e.member().equals("size")) {
          this.emitExpression(e.base(), code);
          code.addInstruction(new ObjectInstrs.CallMethod("size", 0));
          return;
        } else if (e.member().equals("zero")) {
          this.emitExpression(e.base(), code);
          code.addInstruction(new ArrayInstrs.ArrayZero());
          return;
        }
        if (e.base() instanceof ChuckAST.IdExp id) {
          String bt = parent.getExprType(e.base());
          if (bt != null) {
            String actualClassWithField = parent.findStaticFieldOwner(bt, e.member());
            if (actualClassWithField != null) {
              code.addInstruction(new PushInstrs.PushString(actualClassWithField));
              code.addInstruction(new FieldInstrs.SetStatic(actualClassWithField, e.member()));
              return;
            }
          }
          if (Set.of("ADSR", "Adsr").contains(id.name())) {
            code.addInstruction(
                new FieldInstrs.GetBuiltinStatic("org.chuck.audio.Adsr", e.member()));
            return;
          }
          if (id.name().equals("Std")) {
            code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.Std", e.member()));
            return;
          }
          if (id.name().equals("Machine")) {
            switch (e.member()) {
              case "realtime" -> code.addInstruction(new PushInstrs.PushInt(0));
              case "silent" -> code.addInstruction(new PushInstrs.PushInt(1));
              case "intsize" -> code.addInstruction(new PushInstrs.PushInt(64));
              case "version", "platform", "os", "loglevel", "timeofday" ->
                  code.addInstruction(new org.chuck.core.instr.MachineCall(e.member(), 0));
              default -> code.addInstruction(new org.chuck.core.instr.MachineCall(e.member(), 0));
            }
            return;
          }
          if (Set.of("RegEx", "Reflect", "SerialIO", "FileIO").contains(id.name())) {
            code.addInstruction(
                new FieldInstrs.GetBuiltinStatic("org.chuck.core." + id.name(), e.member()));
            return;
          }
          if (parent.getUserClassRegistry().containsKey(id.name())) {
            UserClassDescriptor d = parent.getUserClassRegistry().get(id.name());
            if (d.staticInts().containsKey(e.member())
                || d.staticObjects().containsKey(e.member())) {
              code.addInstruction(new FieldInstrs.GetStatic(id.name(), e.member()));
              return;
            }
          }
          if (parent.getCurrentClass() != null
              && parent.getUserClassRegistry().containsKey(parent.getCurrentClass())
              && parent
                  .getUserClassRegistry()
                  .get(parent.getCurrentClass())
                  .staticObjects()
                  .containsKey(id.name())) {
            code.addInstruction(new FieldInstrs.GetStatic(parent.getCurrentClass(), id.name()));
            code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
            return;
          }
        }
        if (ChuckEmitter.vecFieldIndex(e.member()) >= 0
            && ChuckEmitter.isVecType(parent.getExprType(e.base()))) {
          this.emitExpression(e.base(), code);
          code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
          return;
        }
        this.emitExpression(e.base(), code);
        String baseType = parent.getExprType(e.base());
        if (baseType != null) {
          if (parent.getUserClassRegistry().containsKey(baseType))
            parent.checkAccess(baseType, e.member(), false, e.line(), e.column());
          if (parent.isKnownUGenType(baseType) || parent.isSubclassOfUGen(baseType))
            code.addInstruction(new ObjectInstrs.CallMethod(e.member(), 0));
          else code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
        } else code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
      }
      case ChuckAST.ArrayLitExp e -> {
        for (ChuckAST.Exp el : e.elements()) this.emitExpression(el, code);
        code.addInstruction(new ArrayInstrs.NewArrayFromStack(e.elements().size()));
      }
      case ChuckAST.VectorLitExp e -> {
        for (ChuckAST.Exp el : e.elements()) {
          this.emitExpression(el, code);
          code.addInstruction(new TypeInstrs.EnsureFloat());
        }
        String vTag =
            switch (e.elements().size()) {
              case 2 -> "vec2";
              case 3 -> "vec3";
              case 4 -> "vec4";
              default -> null;
            };
        code.addInstruction(new ArrayInstrs.NewArrayFromStack(e.elements().size(), vTag));
      }
      case ChuckAST.ComplexLit e -> {
        this.emitExpression(e.re(), code);
        this.emitExpression(e.im(), code);
        code.addInstruction(new ArrayInstrs.NewArrayFromStack(2, "complex"));
      }
      case ChuckAST.PolarLit e -> {
        this.emitExpression(e.mag(), code);
        this.emitExpression(e.phase(), code);
        code.addInstruction(new ArrayInstrs.NewArrayFromStack(2, "polar"));
      }
      case ChuckAST.ArrayAccessExp e -> {
        this.emitExpression(e.base(), code);
        String baseType = parent.getExprType(e.base());
        String elemType =
            (baseType != null && baseType.endsWith("[]"))
                ? baseType.substring(0, baseType.length() - 2)
                : null;

        for (int i = 0; i < e.indices().size(); i++) {
          this.emitExpression(e.indices().get(i), code);
          if (i < e.indices().size() - 1) {
            code.addInstruction(
                new ArrayInstrs.GetArrayInt()); // Still need generic for intermediate dims if any
          } else {
            if (e.indices().size() == 1 && "int".equals(elemType))
              code.addInstruction(new ArrayInstrs.GetArrayIntFast());
            else if (e.indices().size() == 1 && "float".equals(elemType))
              code.addInstruction(new ArrayInstrs.GetArrayFloatFast());
            else code.addInstruction(new ArrayInstrs.GetArrayInt());
          }
        }
      }
      case ChuckAST.CallExp e -> {
        List<String> argTypes = e.args().stream().map(parent::getExprType).toList();
        int argc = e.args().size();
        if (e.base() instanceof ChuckAST.DotExp dot
            && dot.base() instanceof ChuckAST.IdExp baseId) {
          String bn = baseId.name(), mn = dot.member();
          if (bn.equals("IO") && Set.of("nl", "newline").contains(mn)) {
            code.addInstruction(new PushInstrs.PushString("\n"));
            return;
          }
          if (bn.equals("Std")
              && Set.of(
                      "mtof",
                      "ftom",
                      "powtodb",
                      "rmstodb",
                      "dbtopow",
                      "dbtorms",
                      "dbtolin",
                      "lintodb",
                      "abs",
                      "fabs",
                      "sgn",
                      "rand2",
                      "rand2f",
                      "clamp",
                      "clampf",
                      "scalef",
                      "atoi",
                      "atof",
                      "itoa",
                      "ftoi",
                      "systemTime",
                      "range",
                      "getenv",
                      "setenv")
                  .contains(mn)) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new MathInstrs.StdFunc(mn, argc));
            return;
          }
          if (bn.equals("Math")) {
            if (Set.of("random", "randf").contains(mn)) {
              code.addInstruction(new MathInstrs.MathRandom());
              return;
            }
            if (mn.equals("help")) {
              code.addInstruction(new MathInstrs.MathHelp());
              return;
            }
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new MathInstrs.MathFunc(mn));
            return;
          }
          if (bn.equals("Machine")) {
            if (mn.equals("realtime")) {
              code.addInstruction(new PushInstrs.PushInt(0));
              return;
            }
            if (mn.equals("silent")) {
              code.addInstruction(new PushInstrs.PushInt(1));
              return;
            }
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new org.chuck.core.instr.MachineCall(mn, argc));
            return;
          }
          if (Set.of("RegEx", "Reflect", "SerialIO").contains(bn)) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(
                new ObjectInstrs.CallBuiltinStatic("org.chuck.core." + bn, mn, argc));
            return;
          }
          // AI/ML static class methods: MLP.shuffle(X, Y), PCA.reduce(X, k, out), etc.
          if (bn.equals("MLP") || bn.equals("PCA")) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(
                new ObjectInstrs.CallBuiltinStatic("org.chuck.core.ai." + bn, mn, argc));
            return;
          }
          // Windowing static factory: Windowing.hann(n), Windowing.hamming(n), etc.
          if (bn.equals("Windowing")) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(
                new ObjectInstrs.CallBuiltinStatic("org.chuck.audio.analysis.Windowing", mn, argc));
            return;
          }
        }
        if (e.base() instanceof ChuckAST.DotExp dot
            && (dot.base() instanceof ChuckAST.MeExp
                || (dot.base() instanceof ChuckAST.IdExp idMe && idMe.name().equals("me")))) {
          String mn = dot.member();
          switch (mn) {
            case "yield" -> {
              code.addInstruction(new MiscInstrs.Yield());
              return;
            }
            case "dir", "arg" -> {
              if (!e.args().isEmpty()) this.emitExpression(e.args().get(0), code);
              else code.addInstruction(new PushInstrs.PushInt(0));
              if (mn.equals("dir")) code.addInstruction(new MeInstrs.MeDir());
              else code.addInstruction(new MeInstrs.MeArg());
              return;
            }
            case "args" -> {
              code.addInstruction(new MeInstrs.MeArgs());
              return;
            }
            case "id" -> {
              code.addInstruction(new MeInstrs.MeId());
              return;
            }
            case "exit" -> {
              code.addInstruction(new MeInstrs.MeExit());
              return;
            }
          }
          code.addInstruction(new PushInstrs.PushMe());
          for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
          String fk = parent.resolveMethodKey("Shred", mn, argTypes);
          code.addInstruction(new ObjectInstrs.CallMethod(mn, argc, fk));
          return;
        }
        if (e.base() instanceof ChuckAST.DotExp dot
            && dot.base() instanceof ChuckAST.IdExp supId
            && supId.name().equals("super")
            && parent.getCurrentClass() != null) {
          UserClassDescriptor cd = parent.getUserClassRegistry().get(parent.getCurrentClass());
          if (cd != null && cd.parentName() != null) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(
                new ObjectInstrs.CallSuperMethod(cd.parentName(), dot.member(), argc));
            return;
          }
        }
        if (e.base() instanceof ChuckAST.DotExp dot) {
          String bt = parent.getExprType(dot.base());
          if (bt == null
              && dot.base() instanceof ChuckAST.IdExp id
              && parent.getUserClassRegistry().containsKey(id.name())) bt = id.name();
          if (bt == null) {
            // Type unknown (e.g. chained call: fft.upchuck().cvals()) — emit base then call by name
            this.emitExpression(dot.base(), code);
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc));
            return;
          }
          if (bt != null) {
            // Specialization for built-in UGen method calls (single arg)
            if (argc == 1 && (parent.isKnownUGenType(bt) || parent.isSubclassOfUGen(bt))) {
              String argType = argTypes.get(0);
              String mn = dot.member();
              Set<String> safeFloat = Set.of("freq", "gain", "phase", "cutoff");
              Set<String> safeInt = Set.of("sync", "cutoff");

              if (("float".equals(argType) && safeFloat.contains(mn))
                  || ("int".equals(argType) && safeInt.contains(mn))) {
                this.emitExpression(dot.base(), code);
                this.emitExpression(e.args().get(0), code);
                if ("float".equals(argType))
                  code.addInstruction(new ObjectInstrs.CallBuiltinFloat(mn));
                else code.addInstruction(new ObjectInstrs.CallBuiltinInt(mn));
                return;
              }
            }
            if (bt.equals("string") || bt.endsWith("[]")) {
              this.emitExpression(dot.base(), code);
              for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
              String fk = parent.getMethodKey(dot.member(), argTypes);
              code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc, fk));
              return;
            }
            if (dot.member().equals("last")
                && (parent.isKnownUGenType(bt) || parent.isSubclassOfUGen(bt))) {
              this.emitExpression(dot.base(), code);
              code.addInstruction(new UgenInstrs.GetLastOut());
              return;
            }
            if (dot.member().equals("dot") && ChuckEmitter.isVecType(bt)) {
              this.emitExpression(dot.base(), code);
              for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
              code.addInstruction(new VecInstrs.Dot());
              return;
            }
            if (parent.getUserClassRegistry().containsKey(bt)) {
              String fk = parent.getMethodKey(dot.member(), argTypes);
              ChuckCode tgt = parent.resolveStaticMethod(bt, fk);
              if (tgt == null) tgt = parent.resolveStaticMethod(bt, dot.member() + ":" + argc);
              if (tgt != null) {
                String finalK =
                    (tgt.getName().equals(dot.member())) ? fk : dot.member() + ":" + argc;
                parent.checkAccess(bt, finalK, true, e.line(), e.column());
                for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
                code.addInstruction(new CallFunc(tgt, argc));
                return;
              }
            }
            this.emitExpression(dot.base(), code);
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            String fk = parent.resolveMethodKey(bt, dot.member(), argTypes);
            if (parent.getUserClassRegistry().containsKey(bt))
              parent.checkAccess(bt, fk, true, e.line(), e.column());
            code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc, fk));
            return;
          }
        } else if (e.base() instanceof ChuckAST.IdExp id) {
          String name = id.name();
          if (parent.getCurrentClass() != null
              && (name.equals(parent.getCurrentClass()) || name.equals("this"))) {
            String ck = parent.getMethodKey(parent.getCurrentClass(), argTypes);
            code.addInstruction(new StackInstrs.PushThis());
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new ObjectInstrs.CallMethod(parent.getCurrentClass(), argc, ck));
            return;
          }
          if (parent.getCurrentClass() != null) {
            String fk = parent.getMethodKey(name, argTypes);
            ChuckCode tgt = parent.resolveStaticMethod(parent.getCurrentClass(), fk);
            if (tgt == null)
              tgt = parent.resolveStaticMethod(parent.getCurrentClass(), name + ":" + argc);
            if (tgt != null) {
              for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
              code.addInstruction(new CallFunc(tgt, argc));
              return;
            }
            UserClassDescriptor desc = parent.getUserClassRegistry().get(parent.getCurrentClass());
            if (desc != null && desc.methods().containsKey(fk)) {
              code.addInstruction(new StackInstrs.PushThis());
              for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
              code.addInstruction(new ObjectInstrs.CallMethod(name, argc, fk));
              return;
            }
          }
          String fk = parent.getMethodKey(name, argTypes);
          if (parent.getFunctions().containsKey(fk)) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new CallFunc(parent.getFunctions().get(fk), argc));
            return;
          }
          String fbk = name + ":" + argc;
          if (parent.getFunctions().containsKey(fbk)) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new CallFunc(parent.getFunctions().get(fbk), argc));
            return;
          }
          if (Set.of("print", "chout", "cherr").contains(name)) {
            for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
            code.addInstruction(new ChuckPrint(argc));
            return;
          }
        }
        this.emitExpression(e.base(), code);
        for (ChuckAST.Exp arg : e.args()) this.emitExpression(arg, code);
        code.addInstruction(new ObjectInstrs.CallMethod("unknown", argc));
      }
      case ChuckAST.SporkExp e -> {
        String fn = null;
        switch (e.call().base()) {
          case ChuckAST.IdExp id -> {
            fn = id.name();
            List<String> ats = new ArrayList<>();
            for (ChuckAST.Exp arg : e.call().args()) ats.add(parent.getExprType(arg));
            String key = parent.getMethodKey(fn, ats);
            ChuckCode tgt = parent.resolveStaticMethod(parent.getCurrentClass(), key);
            if (tgt == null)
              tgt =
                  parent.resolveStaticMethod(
                      parent.getCurrentClass(), fn + ":" + e.call().args().size());
            if (tgt != null) {
              for (ChuckAST.Exp arg : e.call().args()) this.emitExpression(arg, code);
              code.addInstruction(new ObjectInstrs.Spork(tgt, e.call().args().size()));
              return;
            }
            if (parent.getFunctions().containsKey(key)) {
              for (ChuckAST.Exp arg : e.call().args()) this.emitExpression(arg, code);
              code.addInstruction(
                  new ObjectInstrs.Spork(parent.getFunctions().get(key), e.call().args().size()));
              return;
            }
            String fbk = fn + ":" + e.call().args().size();
            if (parent.getFunctions().containsKey(fbk)) {
              for (ChuckAST.Exp arg : e.call().args()) this.emitExpression(arg, code);
              code.addInstruction(
                  new ObjectInstrs.Spork(parent.getFunctions().get(fbk), e.call().args().size()));
              return;
            }
          }
          case ChuckAST.DotExp dot -> {
            List<String> ats = e.call().args().stream().map(parent::getExprType).toList();
            String bcn = null;
            if (dot.base() instanceof ChuckAST.IdExp id
                && parent.getUserClassRegistry().containsKey(id.name())) bcn = id.name();
            else {
              String bt = parent.getExprType(dot.base());
              if (bt != null && parent.getUserClassRegistry().containsKey(bt)) bcn = bt;
            }
            if (bcn != null) {
              String rk = parent.resolveMethodKey(bcn, dot.member(), ats);
              ChuckCode tgt = parent.resolveStaticMethod(bcn, rk);
              if (tgt != null) {
                for (ChuckAST.Exp arg : e.call().args()) this.emitExpression(arg, code);
                code.addInstruction(new ObjectInstrs.Spork(tgt, e.call().args().size()));
                return;
              }
            }
            this.emitExpression(dot.base(), code);
            for (ChuckAST.Exp arg : e.call().args()) this.emitExpression(arg, code);
            String bt = parent.getExprType(dot.base());
            String rk =
                (bt != null)
                    ? parent.resolveMethodKey(bt, dot.member(), ats)
                    : parent.getMethodKey(dot.member(), ats);
            code.addInstruction(
                new ObjectInstrs.SporkMethod(dot.member(), e.call().args().size(), rk));
            return;
          }
          default -> {}
        }
        this.emitExpression(e.call(), code);
      }
      case ChuckAST.TernaryExp e -> {
        this.emitExpression(e.condition(), code);
        int jf = code.getNumInstructions();
        code.addInstruction(null);
        this.emitExpression(e.thenExp(), code);
        int je = code.getNumInstructions();
        code.addInstruction(null);
        code.replaceInstruction(jf, new ControlInstrs.JumpIfFalse(code.getNumInstructions()));
        this.emitExpression(e.elseExp(), code);
        code.replaceInstruction(je, new ControlInstrs.Jump(code.getNumInstructions()));
      }
      case ChuckAST.CastExp e -> {
        this.emitExpression(e.value(), code);
        switch (e.targetType()) {
          case "int" -> code.addInstruction(new TypeInstrs.CastToInt());
          case "float" -> code.addInstruction(new TypeInstrs.CastToFloat());
          case "string" -> code.addInstruction(new TypeInstrs.CastToString());
          case "complex" -> code.addInstruction(new TypeInstrs.CastToComplex());
          case "polar" -> code.addInstruction(new TypeInstrs.CastToPolar());
          default -> {
            if (parent.isObjectType(e.targetType())) {
              code.addInstruction(new TypeInstrs.CastToObject());
            }
          }
        }
      }
      case ChuckAST.TypeofExp e -> {
        this.emitExpression(e.expr(), code);
        code.addInstruction(new TypeInstrs.TypeofInstr());
      }
      case ChuckAST.InstanceofExp e -> {
        this.emitExpression(e.expr(), code);
        code.addInstruction(new TypeInstrs.InstanceofInstr(e.typeName()));
      }
    }
  }

  private org.chuck.core.ChuckCompilerException error(ChuckAST.Exp e, String message) {
    return new org.chuck.core.ChuckCompilerException(
        message, parent.getCurrentFile(), e.line(), e.column());
  }
}
