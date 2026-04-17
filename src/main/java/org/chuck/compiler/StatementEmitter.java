package org.chuck.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.chuck.core.*;
import org.chuck.core.instr.*;

public class StatementEmitter {
  private final ChuckEmitter parent;

  public StatementEmitter(ChuckEmitter parent) {
    this.parent = parent;
  }

  /**
   * Emits instructions for the given statement.
   *
   * <p><b>Stack Protocol:</b>
   *
   * <ul>
   *   <li>[Before]: (Empty or current context)
   *   <li>[After]: Stack state is preserved (net change is zero), as all intermediate results are
   *       popped or stored.
   * </ul>
   *
   * @param stmt The AST statement node.
   * @param code The instruction container to emit into.
   */
  public void emitStatement(ChuckAST.Stmt stmt, ChuckCode code) {
    if (stmt == null) return;
    if (code != null) code.setActiveLineNumber(stmt.line());
    switch (stmt) {
      case ChuckAST.ExpStmt s -> {
        // Check for standalone undefined variable (e.g. `x;`) — error-nspc-no-crash
        if (s.exp() instanceof ChuckAST.IdExp ide
            && parent.getLocalScopes().size() <= 1
            && parent.getCurrentClass() == null) {
          String nm = ide.name();
          boolean knownName =
              parent.getVarTypeByName(nm) != null
                  || parent.getUserClassRegistry().containsKey(nm)
                  || parent.isKnownUGenType(nm)
                  || parent.getCurrentClassFields().contains(nm)
                  || nm.equals("null")
                  || nm.equals("true")
                  || nm.equals("false")
                  || nm.equals("this")
                  || nm.equals("super")
                  || nm.equals("pi")
                  || nm.equals("e")
                  || nm.equals("now")
                  || nm.equals("dac")
                  || nm.equals("blackhole")
                  || nm.equals("adc")
                  || nm.equals("me")
                  || nm.equals("cherr")
                  || nm.equals("chout")
                  || nm.equals("Machine")
                  || nm.equals("maybe")
                  || nm.equals("second")
                  || nm.equals("ms")
                  || nm.equals("samp")
                  || nm.equals("minute")
                  || nm.equals("hour");
          if (!knownName) {
            throw new org.chuck.core.ChuckCompilerException(
                "undefined variable '" + nm + "'",
                parent.getCurrentFile(),
                ide.line(),
                ide.column());
          }
        }
        parent.emitExpression(s.exp(), code);
        code.addInstruction(new StackInstrs.Pop());
      }
      case ChuckAST.WhileStmt s -> {
        int startPc = code.getNumInstructions();
        parent.getContinueJumps().push(new ArrayList<>());
        parent.getBreakJumps().push(new ArrayList<>());

        parent.emitExpression(s.condition(), code);
        int jumpIdx = code.getNumInstructions();
        code.addInstruction(null); // placeholder for JumpIfFalse
        this.emitStatement(s.body(), code);
        code.addInstruction(new ControlInstrs.Jump(startPc));
        int endPc = code.getNumInstructions();
        code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));

        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
        }
      }
      case ChuckAST.UntilStmt s -> {
        int startPc = code.getNumInstructions();
        parent.getContinueJumps().push(new ArrayList<>());
        parent.getBreakJumps().push(new ArrayList<>());

        parent.emitExpression(s.condition(), code);
        code.addInstruction(new LogicInstrs.LogicalNot());
        int jumpIdx = code.getNumInstructions();
        code.addInstruction(null); // placeholder for JumpIfFalse
        this.emitStatement(s.body(), code);
        code.addInstruction(new ControlInstrs.Jump(startPc));
        int endPc = code.getNumInstructions();
        code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));

        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
        }
      }
      case ChuckAST.DoStmt s -> {
        int startPc = code.getNumInstructions();
        int bodyStart = code.getNumInstructions();
        parent.getBreakJumps().push(new ArrayList<>());
        parent.getContinueJumps().push(new ArrayList<>());

        this.emitStatement(s.body(), code);

        int condStart = code.getNumInstructions();

        parent.emitExpression(s.condition(), code);
        if (s.isUntil()) {
          code.addInstruction(new LogicInstrs.LogicalNot());
        }
        code.addInstruction(new ControlInstrs.JumpIfTrue(bodyStart));
        int endPc = code.getNumInstructions();

        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(condStart));
        }
      }
      case ChuckAST.BreakStmt _ -> {
        if (!parent.getBreakJumps().isEmpty()) {
          parent.getBreakJumps().peek().add(code.getNumInstructions());
          code.addInstruction(null); // placeholder for Jump
        }
      }
      case ChuckAST.ContinueStmt _ -> {
        if (!parent.getContinueJumps().isEmpty()) {
          parent.getContinueJumps().peek().add(code.getNumInstructions());
          code.addInstruction(null); // placeholder for Jump
        }
      }
      case ChuckAST.SwitchStmt s -> {
        parent.emitExpression(s.condition(), code);

        parent.getBreakJumps().push(new ArrayList<>());
        List<Integer> caseConditionJumps = new ArrayList<>();
        ChuckAST.CaseStmt defaultCase = null;
        int defaultBodyStartIndex = -1;

        for (ChuckAST.CaseStmt c : s.cases()) {
          if (c.isDefault()) {
            defaultCase = c;
          } else {
            code.addInstruction(new StackInstrs.Dup());
            parent.emitExpression(c.match(), code);
            code.addInstruction(new LogicInstrs.EqualsAny());
            caseConditionJumps.add(code.getNumInstructions());
            code.addInstruction(null); // JumpIfTrue
          }
        }

        code.addInstruction(new StackInstrs.Pop()); // pop switch value if no match
        int jumpToDefaultOrEnd = code.getNumInstructions();
        code.addInstruction(null); // Jump to default or end

        int caseIdx = 0;
        for (ChuckAST.CaseStmt c : s.cases()) {
          if (c.isDefault()) {
            defaultBodyStartIndex = code.getNumInstructions();
          } else {
            code.replaceInstruction(
                caseConditionJumps.get(caseIdx++),
                new ControlInstrs.JumpIfTrue(code.getNumInstructions()));
            code.addInstruction(new StackInstrs.Pop()); // pop switch value before body executes
          }
          for (ChuckAST.Stmt b : c.body()) {
            this.emitStatement(b, code);
          }
          // Implicit break: add jump-to-end unless body ends with explicit break
          java.util.List<ChuckAST.Stmt> body = c.body();
          boolean endsWithBreak =
              !body.isEmpty() && body.get(body.size() - 1) instanceof ChuckAST.BreakStmt;
          if (!endsWithBreak) {
            parent.getBreakJumps().peek().add(code.getNumInstructions());
            code.addInstruction(null); // implicit jump to end (no fall-through)
          }
        }

        int endPc = code.getNumInstructions();

        if (defaultCase != null) {
          code.replaceInstruction(
              jumpToDefaultOrEnd, new ControlInstrs.Jump(defaultBodyStartIndex));
        } else {
          code.replaceInstruction(jumpToDefaultOrEnd, new ControlInstrs.Jump(endPc));
        }

        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
      }
      case ChuckAST.ForStmt s -> {
        parent.getLocalScopes().push(new HashMap<>());
        parent.getLocalTypeScopes().push(new HashMap<>());
        int oldLocalCount = parent.getLocalCount();
        if (s.init() != null) this.emitStatement(s.init(), code);
        int startPc = code.getNumInstructions();
        parent.getBreakJumps().push(new ArrayList<>());
        parent.getContinueJumps().push(new ArrayList<>());

        if (s.condition() instanceof ChuckAST.ExpStmt es) {
          parent.emitExpression(es.exp(), code);
        } else {
          code.addInstruction(new PushInstrs.PushInt(1)); // truthy
        }
        int jumpIdx = code.getNumInstructions();
        code.addInstruction(null); // placeholder for JumpIfFalse

        this.emitStatement(s.body(), code);

        int updateStart = code.getNumInstructions();
        if (s.update() != null) {
          parent.emitExpression(s.update(), code);
          code.addInstruction(new StackInstrs.Pop()); // pop update result
        }
        code.addInstruction(new ControlInstrs.Jump(startPc));

        int endPc = code.getNumInstructions();
        code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));
        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
        }
        parent.getLocalScopes().pop();
        parent.getLocalTypeScopes().pop();
        parent.setLocalCount(oldLocalCount);
      }
      case ChuckAST.BlockStmt s -> {
        if (s.isScoped()) {
          parent.getLocalScopes().push(new HashMap<>());
          parent.getLocalTypeScopes().push(new HashMap<>());
        }
        int oldLocalCount = parent.getLocalCount();
        for (ChuckAST.Stmt inner : s.statements()) {
          this.emitStatement(inner, code);
        }
        if (s.isScoped()) {
          parent.getLocalScopes().pop();
          parent.getLocalTypeScopes().pop();
          parent.setLocalCount(oldLocalCount);
        }
      }
      case ChuckAST.ImportStmt _ -> {
        // already processed by VM before emit — skip
      }
      case ChuckAST.DeclStmt s -> {
        // Check for 'auto' without initialization
        if (s.type().equals("auto")) {
          throw new org.chuck.core.ChuckCompilerException(
              "'auto' requires initialization (cannot declare 'auto' without a value)",
              parent.getCurrentFile(),
              s.line(),
              s.column());
        }
        // Check for static variable declared outside class scope
        if (s.isStatic()
            && (parent.getCurrentClass() == null || parent.getLocalScopes().size() > 1)) {
          throw new org.chuck.core.ChuckCompilerException(
              "static variables must be declared at class scope",
              parent.getCurrentFile(),
              s.line(),
              s.column());
        }
        // Track empty-array variable declarations
        if (!s.arraySizes().isEmpty()
            && s.arraySizes().get(0) instanceof ChuckAST.IntExp sz0
            && sz0.value() < 0) {
          parent.getEmptyArrayVars().add(s.name());
        }
        if (!parent.getLocalTypeScopes().isEmpty()) {
          parent.getLocalTypeScopes().peek().put(s.name(), s.type());
        }
        int argCount = 0;
        boolean isUserClass = parent.getUserClassRegistry().containsKey(s.type());
        // For array declarations like Foo array1(2)[3], the type is "Foo[]" but the element type
        // "Foo" may be a user class. Compute the element type for ctor dispatch.
        String elemTypeS = parent.getBaseType(s.type());
        boolean isUserClassElem = parent.getUserClassRegistry().containsKey(elemTypeS);
        boolean isObject = parent.isObjectType(s.type());
        boolean forceGlobal = s.isGlobal();
        boolean useGlobal = forceGlobal; // Top-level vars are shred-local by default in ChucK
        // Compile-time check: detect global type conflicts
        if (useGlobal) {
          String prevType = parent.getGlobalVarTypes().get(s.name());
          if (prevType != null
              && !prevType.equals(s.type())
              && !prevType.equals("int")
              && !prevType.equals("float")
              && !prevType.equals("string")) {
            throw new org.chuck.core.ChuckCompilerException(
                "global "
                    + prevType
                    + " '"
                    + s.name()
                    + "' has different type '"
                    + s.type()
                    + "' than already existing global "
                    + prevType
                    + " of the same name",
                parent.getCurrentFile(),
                s.line(),
                s.column());
          }
          parent.getGlobalVarTypes().put(s.name(), s.type());
        }

        List<ChuckAST.Exp> ctorArgs =
            switch (s.callArgs()) {
              case ChuckAST.CallExp call -> call.args();
              case ChuckAST.ArrayLitExp arr -> arr.elements();
              case null -> List.of();
              default -> List.of();
            };
        // Emit constructor call if: explicit callArgs present (even zero-arg "()")
        // OR class defines a zero-arg @construct (Foo f; should call @construct()).
        boolean hasExplicitCallArgs = s.callArgs() != null;
        boolean hasZeroArgCtor =
            (isUserClass || isUserClassElem)
                && !hasExplicitCallArgs
                && parent.getUserClassRegistry().containsKey(elemTypeS)
                && parent
                    .getUserClassRegistry()
                    .get(elemTypeS)
                    .methods()
                    .containsKey(elemTypeS + ":0");
        boolean isArrayDeclS = !s.arraySizes().isEmpty();
        if (isUserClass
            && !s.isReference()
            && !isArrayDeclS
            && (hasExplicitCallArgs || hasZeroArgCtor)) {
          if (parent.isInPreCtor()) {
            code.addInstruction(new StackInstrs.PushThis());
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushField(
                    parent.getBaseType(s.type()),
                    s.name(),
                    0,
                    s.isReference(),
                    false,
                    parent.getUserClassRegistry()));
          } else if (useGlobal) {
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushGlobal(
                    parent.getBaseType(s.type()),
                    s.name(),
                    0,
                    s.isReference(),
                    false,
                    parent.getUserClassRegistry()));
          } else {
            Map<String, Integer> scope = parent.getLocalScopes().peek();
            int offset = parent.getLocalCount();
            parent.setLocalCount(offset + 1);
            scope.put(s.name(), offset);
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushLocal(
                    s.type(), offset, 0, s.isReference(), false, parent.getUserClassRegistry()));
          }
          code.addInstruction(new StackInstrs.Dup());
          for (ChuckAST.Exp arg : ctorArgs) {
            parent.emitExpression(arg, code);
          }
          List<String> ctorArgTypes = ctorArgs.stream().map(parent::getExprType).toList();
          String ctorKey = parent.getMethodKey(s.type(), ctorArgTypes);
          code.addInstruction(new ObjectInstrs.CallMethod(s.type(), ctorArgs.size(), ctorKey));
          code.addInstruction(new StackInstrs.Pop());
        } else if ((isUserClass || isUserClassElem)
            && !s.isReference()
            && isArrayDeclS
            && (hasExplicitCallArgs || hasZeroArgCtor)) {
          // Array declaration with per-element constructor: Foo arr(ctorArg)[size]
          // elemTypeS is the base element type (e.g. "Foo" from "Foo[]")
          List<String> ctorArgTypes = ctorArgs.stream().map(parent::getExprType).toList();
          String ctorKey = parent.getMethodKey(elemTypeS, ctorArgTypes);
          // Emit array size(s)
          for (ChuckAST.Exp sizeExp : s.arraySizes()) {
            parent.emitExpression(sizeExp, code);
          }
          // Emit ctor args
          for (ChuckAST.Exp arg : ctorArgs) {
            parent.emitExpression(arg, code);
          }
          if (useGlobal) {
            parent.getGlobalVarTypes().put(s.name(), s.type());
            code.addInstruction(
                new ObjectInstrs.InstantiateArrayWithCtorGlobal(
                    elemTypeS,
                    s.name(),
                    s.arraySizes().size(),
                    ctorArgs.size(),
                    ctorKey,
                    parent.getUserClassRegistry()));
          } else {
            Map<String, Integer> scope = parent.getLocalScopes().peek();
            int offset = parent.getLocalCount();
            parent.setLocalCount(offset + 1);
            scope.put(s.name(), offset);
            code.addInstruction(
                new ObjectInstrs.InstantiateArrayWithCtorLocal(
                    elemTypeS,
                    offset,
                    s.arraySizes().size(),
                    ctorArgs.size(),
                    ctorKey,
                    parent.getUserClassRegistry()));
          }
          code.addInstruction(new StackInstrs.Pop());
        } else {
          if (s.callArgs() instanceof ChuckAST.CallExp call) {
            for (ChuckAST.Exp arg : call.args()) {
              parent.emitExpression(arg, code);
            }
            argCount = call.args().size();
          }
          if (!s.arraySizes().isEmpty()) {
            for (ChuckAST.Exp sizeExp : s.arraySizes()) {
              parent.emitExpression(sizeExp, code);
            }
            argCount = s.arraySizes().size();
          }

          boolean isArrayDecl = !s.arraySizes().isEmpty();
          if (parent.isInPreCtor()) {
            code.addInstruction(new StackInstrs.PushThis());
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushField(
                    parent.getBaseType(s.type()),
                    s.name(),
                    argCount,
                    s.isReference(),
                    isArrayDecl,
                    parent.getUserClassRegistry()));
            code.addInstruction(new StackInstrs.Pop());
          } else if (useGlobal) {
            parent.getGlobalVarTypes().put(s.name(), s.type());
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushGlobal(
                    parent.getBaseType(s.type()),
                    s.name(),
                    argCount,
                    s.isReference(),
                    isArrayDecl,
                    parent.getUserClassRegistry()));
            code.addInstruction(new StackInstrs.Pop());
          } else {
            Map<String, Integer> scope = parent.getLocalScopes().peek();
            Integer offset = scope.get(s.name());
            if (offset == null) {
              offset = parent.getLocalCount();
              parent.setLocalCount(offset + 1);
              scope.put(s.name(), offset);
            }
            code.addInstruction(
                new ObjectInstrs.InstantiateSetAndPushLocal(
                    s.type(),
                    offset,
                    argCount,
                    s.isReference(),
                    isArrayDecl,
                    parent.getUserClassRegistry()));
            code.addInstruction(new StackInstrs.Pop());
          }
        }
      }
      case ChuckAST.PrintStmt s -> {
        for (ChuckAST.Exp exp : s.expressions()) {
          parent.emitExpression(exp, code);
        }
        code.addInstruction(new ChuckPrint(s.expressions().size()));
      }
      case ChuckAST.FuncDefStmt s -> {
        // Validate operator overloads
        if (s.name().startsWith("__op__") || s.name().startsWith("__pub_op__")) {
          String opSuffix =
              s.name().startsWith("__pub_op__")
                  ? s.name().substring("__pub_op__".length())
                  : s.name().substring("__op__".length());
          // ~ is never a valid overloadable operator
          if (opSuffix.equals("~")) {
            throw new org.chuck.core.ChuckCompilerException(
                "cannot overload operator '" + opSuffix + "'",
                parent.getCurrentFile(),
                s.line(),
                s.column());
          }
          // ++ and -- cannot be overloaded for primitive types
          java.util.Set<String> primitiveTypes =
              java.util.Set.of("int", "float", "string", "time", "dur", "void");
          if ((opSuffix.equals("++") || opSuffix.equals("--")) && s.argTypes().size() >= 1) {
            for (String argType : s.argTypes()) {
              if (primitiveTypes.contains(argType)) {
                throw new org.chuck.core.ChuckCompilerException(
                    "cannot overload operator '"
                        + opSuffix
                        + "' for primitive type '"
                        + argType
                        + "'",
                    parent.getCurrentFile(),
                    s.line(),
                    s.column());
              }
            }
          }
          // => cannot be overloaded for UGen subtypes
          if ((opSuffix.equals("=>") || opSuffix.equals("@=>")) && s.argTypes().size() >= 1) {
            for (String argType : s.argTypes()) {
              if (parent.isKnownUGenType(argType)) {
                throw new org.chuck.core.ChuckCompilerException(
                    "cannot overload '" + opSuffix + "' for UGen subtype '" + argType + "'",
                    parent.getCurrentFile(),
                    s.line(),
                    s.column());
              }
            }
          }
        }
        String key = parent.getMethodKey(s.name(), s.argTypes());
        ChuckCode funcCode = parent.getFunctions().get(key);
        if (funcCode == null) {
          // This can happen for class methods if not pre-registered correctly
          funcCode = new ChuckCode(s.name());
        } else if (funcCode.getNumInstructions() > 0) {
          // Already emitted body (e.g. if we are in Pass 2 and it was already hit)
          return;
        }

        String prevReturnType = parent.getCurrentFuncReturnType();
        boolean prevHasReturn = parent.isCurrentFuncHasReturn();
        boolean prevStaticCtx = parent.isInStaticFuncContext();
        parent.setCurrentFuncReturnType(s.returnType() != null ? s.returnType() : "void");
        parent.setCurrentFuncHasReturn(false);
        parent.setInStaticFuncContext(s.isStatic());

        funcCode.setSignature(s.argNames().size(), parent.getCurrentFuncReturnType());

        int savedLocalCount = parent.getLocalCount();
        boolean savedInPreCtor = parent.isInPreCtor();

        Map<String, Integer> scope = new HashMap<>();
        Map<String, String> typeScope = new HashMap<>();
        parent.setLocalCount(s.argNames().size());
        parent.setInPreCtor(false);
        for (int i = 0; i < s.argNames().size(); i++) {
          scope.put(s.argNames().get(i), i);
          typeScope.put(s.argNames().get(i), s.argTypes().get(i));
        }
        parent.getLocalScopes().push(scope);
        parent.getLocalTypeScopes().push(typeScope);
        parent.resetMaxLocalCount();
        funcCode.addInstruction(new VarInstrs.MoveArgs(s.argNames().size()));
        this.emitStatement(s.body(), funcCode);
        funcCode.addInstruction(new ControlInstrs.ReturnFunc());
        funcCode.setStackSize(parent.getMaxLocalCount());
        parent.getLocalScopes().pop();
        parent.getLocalTypeScopes().pop();
        parent.setLocalCount(savedLocalCount);
        parent.setInPreCtor(savedInPreCtor);

        // Check for missing return in non-void function
        if (!parent.getCurrentFuncReturnType().equals("void") && !parent.isCurrentFuncHasReturn()) {
          throw new org.chuck.core.ChuckCompilerException(
              "not all control paths in 'fun "
                  + parent.getCurrentFuncReturnType()
                  + " "
                  + s.name()
                  + "()' return a value",
              parent.getCurrentFile(),
              s.line(),
              s.column());
        }

        parent.setCurrentFuncReturnType(prevReturnType);
        parent.setCurrentFuncHasReturn(prevHasReturn);
        parent.setInStaticFuncContext(prevStaticCtx);

        parent.getFunctions().put(key, funcCode);
        if (s.returnType() != null && !s.returnType().equals("void")) {
          parent.getFunctionReturnTypes().put(key, s.returnType());
        }
      }
      case ChuckAST.ReturnStmt s -> {
        // Check for return value in void function
        if (s.exp() != null && "void".equals(parent.getCurrentFuncReturnType())) {
          throw new org.chuck.core.ChuckCompilerException(
              "function was defined with return type 'void' -- but returning a value",
              parent.getCurrentFile(),
              s.line(),
              s.column());
        }
        parent.setCurrentFuncHasReturn(true);
        if (s.exp() != null) {
          parent.emitExpression(s.exp(), code);
        }
        code.addInstruction(
            parent.getCurrentClass() != null ? new ControlInstrs.ReturnMethod() : new ReturnFunc());
      }
      case ChuckAST.ClassDefStmt s -> {
        // Check for extending primitive types
        if (s.parentName() != null) {
          java.util.Set<String> primitives =
              java.util.Set.of(
                  "int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4",
                  "complex", "polar");
          if (primitives.contains(s.parentName())) {
            throw new org.chuck.core.ChuckCompilerException(
                "cannot extend primitive type '" + s.parentName() + "'",
                parent.getCurrentFile(),
                s.line(),
                s.column());
          }
        }
        List<String[]> fieldDefs = new ArrayList<>();
        List<String[]> staticFieldDefs = new ArrayList<>();
        java.util.Set<String> fieldNames = new java.util.LinkedHashSet<>();
        List<ChuckAST.FuncDefStmt> methods = new ArrayList<>();

        UserClassDescriptor existing = parent.getUserClassRegistry().get(s.name());
        Map<String, Long> staticInts =
            existing != null
                ? existing.staticInts()
                : new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, Boolean> staticIsDouble =
            existing != null
                ? existing.staticIsDouble()
                : new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, Object> staticObjects =
            existing != null
                ? existing.staticObjects()
                : new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, ChuckAST.AccessModifier> fieldAccess =
            existing != null ? existing.fieldAccess() : new HashMap<>();
        Map<String, ChuckAST.AccessModifier> methodAccess =
            existing != null ? existing.methodAccess() : new HashMap<>();

        // Pre-check: static vars cannot be declared inside nested blocks within a class body
        for (ChuckAST.Stmt bodyItem : s.body()) {
          if (bodyItem instanceof ChuckAST.BlockStmt block) {
            parent.checkNoStaticInBlock(block.statements());
          }
        }
        List<ChuckAST.Stmt> flattenedBody = new ArrayList<>();
        parent.flattenStmts(s.body(), flattenedBody);

        Map<String, String> fieldDocs = new HashMap<>();
        for (ChuckAST.Stmt bodyStmt : flattenedBody) {
          switch (bodyStmt) {
            case ChuckAST.DeclStmt f -> {
              if (f.isStatic()) {
                staticFieldDefs.add(new String[] {f.type(), f.name()});
                fieldAccess.put(f.name(), f.access());
              } else {
                fieldDefs.add(new String[] {f.type(), f.name()});
                fieldAccess.put(f.name(), f.access());
                fieldNames.add(f.name());
              }
              if (f.doc() != null) fieldDocs.put(f.name(), f.doc());
            }
            case ChuckAST.FuncDefStmt m -> methods.add(m);
            case ChuckAST.ClassDefStmt inner -> this.emitStatement(inner, null);
            case ChuckAST.ExpStmt es
                when es.exp() instanceof ChuckAST.BinaryExp bexp
                    && (bexp.op() == ChuckAST.Operator.CHUCK
                        || bexp.op() == ChuckAST.Operator.AT_CHUCK)
                    && bexp.rhs() instanceof ChuckAST.DeclExp rDecl -> {
              if (rDecl.isStatic()) {
                staticFieldDefs.add(new String[] {rDecl.type(), rDecl.name()});
                fieldAccess.put(rDecl.name(), rDecl.access());
              } else {
                // e.g. `5 => int n;` — field declaration with literal initializer
                String initStr = null;
                switch (bexp.lhs()) {
                  case ChuckAST.IntExp iv -> initStr = String.valueOf(iv.value());
                  case ChuckAST.FloatExp fv -> initStr = String.valueOf(fv.value());
                  default -> {}
                }
                if (initStr != null) {
                  fieldDefs.add(new String[] {rDecl.type(), rDecl.name(), initStr});
                  fieldAccess.put(rDecl.name(), rDecl.access());
                } else {
                  fieldDefs.add(new String[] {rDecl.type(), rDecl.name()});
                  fieldAccess.put(rDecl.name(), rDecl.access());
                }
                fieldNames.add(rDecl.name());
              }
              if (rDecl.doc() != null) fieldDocs.put(rDecl.name(), rDecl.doc());
            }
            case ChuckAST.ExpStmt es when es.exp() instanceof ChuckAST.DeclExp rDecl -> {
              // Standalone declaration like 'static int a[];'
              if (rDecl.isStatic()) {
                staticFieldDefs.add(new String[] {rDecl.type(), rDecl.name()});
                fieldAccess.put(rDecl.name(), rDecl.access());
              } else {
                fieldDefs.add(new String[] {rDecl.type(), rDecl.name()});
                fieldAccess.put(rDecl.name(), rDecl.access());
                fieldNames.add(rDecl.name());
              }
              if (rDecl.doc() != null) fieldDocs.put(rDecl.name(), rDecl.doc());
            }
            default -> {}
          }
        }
        Map<String, ChuckCode> methodCodes = new HashMap<>();
        Map<String, ChuckCode> staticMethodCodes = new HashMap<>();
        Map<String, String> methodDocs = new HashMap<>();
        String prevClass = parent.getCurrentClass();
        java.util.Set<String> prevFields = parent.getCurrentClassFields();
        parent.setCurrentClass(s.name());
        parent.setCurrentClassFields(fieldNames);

        // Pre-register with actual field maps so static-field references in method bodies resolve.
        // methodCodes/staticMethodCodes are mutable maps; stubs added in pass 1 below become
        // visible here.
        parent
            .getUserClassRegistry()
            .put(
                s.name(),
                new UserClassDescriptor(
                    s.name(),
                    s.parentName(),
                    fieldDefs,
                    staticFieldDefs,
                    methodCodes,
                    staticMethodCodes,
                    staticInts,
                    staticIsDouble,
                    staticObjects,
                    null,
                    s.isAbstract(),
                    s.isInterface(),
                    null,
                    fieldAccess,
                    methodAccess,
                    s.access(),
                    s.doc(),
                    methodDocs,
                    fieldDocs));
        // Track methods defined so far to detect duplicates
        java.util.Set<String> definedMethods = new java.util.HashSet<>();
        parent.setCurrentClassMethodsList(methods);

        // Pass 1: register stub ChuckCode objects for all methods so forward
        // references (e.g. start() calls foo() defined later) resolve correctly.
        Map<ChuckAST.FuncDefStmt, ChuckCode> methodCodeMap = new java.util.LinkedHashMap<>();
        for (ChuckAST.FuncDefStmt m : methods) {
          String methodName = m.name();
          boolean isCtor = methodName.equals("@construct") || methodName.equals(s.name());
          if (methodName.equals("@construct")) {
            methodName = s.name();
          }

          if (isCtor || m.name().equals(s.name())) {
            if (m.isStatic()) {
              throw new org.chuck.core.ChuckCompilerException(
                  "constructor cannot be declared as 'static'",
                  parent.getCurrentFile(),
                  m.line(),
                  m.column());
            }
            if (m.returnType() != null
                && !m.returnType().equals("void")
                && !m.returnType().isEmpty()) {
              throw new org.chuck.core.ChuckCompilerException(
                  "constructor must return void -- returning type '" + m.returnType() + "'",
                  parent.getCurrentFile(),
                  m.line(),
                  m.column());
            }
          }

          String methodKey = parent.getMethodKey(methodName, m.argTypes());
          if (definedMethods.contains(methodKey)) {
            throw new org.chuck.core.ChuckCompilerException(
                "cannot overload function with identical arguments -- '"
                    + methodName
                    + "' already defined in class '"
                    + s.name()
                    + "'",
                parent.getCurrentFile(),
                m.line(),
                m.column());
          }
          definedMethods.add(methodKey);

          ChuckCode stub = new ChuckCode(methodName);
          stub.setSignature(m.argNames().size(), m.returnType() != null ? m.returnType() : "void");
          methodCodeMap.put(m, stub);
          if (m.isStatic()) {
            staticMethodCodes.put(methodKey, stub);
          } else {
            methodCodes.put(methodKey, stub);
          }
          methodAccess.put(methodKey, m.access());
          if (m.doc() != null) {
            methodDocs.put(methodKey, m.doc());
          }
        }

        // Always compile pre-constructor body into a dedicated ChuckCode.
        // This runs each time a new instance of this class is created.
        ChuckCode preCtorCodeLocal = new ChuckCode("__preCtor__" + s.name());
        parent.getLocalScopes().push(new HashMap<>());
        parent.getLocalTypeScopes().push(new HashMap<>());
        parent.resetMaxLocalCount();
        int savedLocalCount = parent.getLocalCount();
        boolean savedInPreCtor = parent.isInPreCtor();
        parent.setLocalCount(0);
        parent.setInPreCtor(true);

        for (ChuckAST.Stmt bodyStmt : flattenedBody) {
          if (bodyStmt instanceof ChuckAST.FuncDefStmt) {
            continue;
          }
          // Skip static variable declarations
          if (bodyStmt instanceof ChuckAST.DeclStmt ds && ds.isStatic()) {
            continue;
          }
          // Skip static variable declarations wrapped in ExpStmt
          if (bodyStmt instanceof ChuckAST.ExpStmt es
              && es.exp() instanceof ChuckAST.DeclExp rDecl
              && rDecl.isStatic()) {
            continue;
          }
          // Skip static variable initializers
          if (bodyStmt instanceof ChuckAST.ExpStmt es2
              && es2.exp() instanceof ChuckAST.BinaryExp bexp2
              && (bexp2.op() == ChuckAST.Operator.CHUCK || bexp2.op() == ChuckAST.Operator.AT_CHUCK)
              && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl2
              && rDecl2.isStatic()) {
            continue;
          }
          this.emitStatement(bodyStmt, preCtorCodeLocal);
        }

        preCtorCodeLocal.setStackSize(parent.getMaxLocalCount());
        parent.getLocalScopes().pop();
        parent.getLocalTypeScopes().pop();
        parent.setLocalCount(savedLocalCount);
        parent.setInPreCtor(savedInPreCtor);

        // Update descriptor with compiled pre-constructor
        parent
            .getUserClassRegistry()
            .put(
                s.name(),
                new UserClassDescriptor(
                    s.name(),
                    s.parentName(),
                    fieldDefs,
                    staticFieldDefs,
                    methodCodes,
                    staticMethodCodes,
                    staticInts,
                    staticIsDouble,
                    staticObjects,
                    preCtorCodeLocal,
                    s.isAbstract(),
                    s.isInterface(),
                    null,
                    fieldAccess,
                    methodAccess,
                    s.access(),
                    s.doc(),
                    methodDocs,
                    fieldDocs));

        // Pass 2: compile method bodies (all stubs already registered above)
        for (Map.Entry<ChuckAST.FuncDefStmt, ChuckCode> entry : methodCodeMap.entrySet()) {
          ChuckAST.FuncDefStmt m = entry.getKey();
          ChuckCode methodCode = entry.getValue();

          String methodName = m.name();
          if (methodName.equals("@construct")) {
            methodName = s.name();
          }

          String prevReturnType = parent.getCurrentFuncReturnType();
          boolean prevHasReturn = parent.isCurrentFuncHasReturn();
          boolean prevStaticCtx = parent.isInStaticFuncContext();
          parent.setCurrentFuncReturnType(m.returnType() != null ? m.returnType() : "void");
          parent.setCurrentFuncHasReturn(false);
          parent.setInStaticFuncContext(m.isStatic());

          Map<String, Integer> scope = new HashMap<>();
          Map<String, String> typeScope = new HashMap<>();
          for (int i = 0; i < m.argNames().size(); i++) {
            scope.put(m.argNames().get(i), i);
            typeScope.put(m.argNames().get(i), m.argTypes().get(i));
          }
          parent.getLocalScopes().push(scope);
          parent.getLocalTypeScopes().push(typeScope);
          parent.setLocalCount(m.argNames().size());
          methodCode.addInstruction(new VarInstrs.MoveArgs(m.argNames().size()));

          this.emitStatement(m.body(), methodCode);
          methodCode.addInstruction(
              m.isStatic() ? new ReturnFunc() : new ControlInstrs.ReturnMethod());
          parent.getLocalScopes().pop();
          parent.getLocalTypeScopes().pop();
          parent.setLocalCount(0); // Reset for next method

          if (!parent.getCurrentFuncReturnType().equals("void")
              && !parent.isCurrentFuncHasReturn()) {
            throw new org.chuck.core.ChuckCompilerException(
                "not all control paths in 'fun "
                    + parent.getCurrentFuncReturnType()
                    + " "
                    + s.name()
                    + "."
                    + m.name()
                    + "()' return a value",
                parent.getCurrentFile(),
                m.line(),
                m.column());
          }

          parent.setCurrentFuncReturnType(prevReturnType);
          parent.setCurrentFuncHasReturn(prevHasReturn);
          parent.setInStaticFuncContext(prevStaticCtx);
        }
        parent.setCurrentClass(prevClass);
        parent.setCurrentClassFields(prevFields);
        ChuckCode finalPreCtorCode =
            preCtorCodeLocal.getNumInstructions() > 0 ? preCtorCodeLocal : null;

        // Compile static initializers for ALL static fields
        ChuckCode staticInitCodeLocal = new ChuckCode("__staticInit__" + s.name());
        String savedClass = parent.getCurrentClass();
        parent.setCurrentClass(s.name());
        parent.resetMaxLocalCount();
        for (ChuckAST.Stmt bodyStmt : flattenedBody) {
          boolean isStaticInit = false;
          String staticFieldName = null;
          if (bodyStmt instanceof ChuckAST.ExpStmt es2
              && es2.exp() instanceof ChuckAST.BinaryExp bexp2
              && (bexp2.op() == ChuckAST.Operator.CHUCK || bexp2.op() == ChuckAST.Operator.AT_CHUCK)
              && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl2
              && rDecl2.isStatic()) {
            staticFieldName = rDecl2.name();
            isStaticInit = true;
          } else if (bodyStmt instanceof ChuckAST.DeclStmt ds && ds.isStatic()) {
            staticFieldName = ds.name();
            isStaticInit = true;
          } else if (bodyStmt instanceof ChuckAST.ExpStmt es3
              && es3.exp() instanceof ChuckAST.DeclExp rDecl3
              && rDecl3.isStatic()) {
            staticFieldName = rDecl3.name();
            isStaticInit = true;
          }
          if (isStaticInit && staticFieldName != null) {
            String fName = staticFieldName;
            if (bodyStmt instanceof ChuckAST.ExpStmt es2
                && es2.exp() instanceof ChuckAST.BinaryExp bexp2
                && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl2) {
              // e.g. 1 => static int S;
              parent.emitExpression(bexp2.lhs(), staticInitCodeLocal);
              staticInitCodeLocal.addInstruction(new FieldInstrs.SetStatic(s.name(), fName));
              staticInitCodeLocal.addInstruction(new StackInstrs.Pop()); // clean up
            } else if (bodyStmt instanceof ChuckAST.DeclStmt ds) {
              // e.g. static int S; or static SinOsc S;
              ChuckAST.DeclExp declExp =
                  new ChuckAST.DeclExp(
                      ds.type(),
                      ds.name(),
                      ds.arraySizes(),
                      ds.callArgs(),
                      ds.isReference(),
                      false,
                      false,
                      ds.isConst(),
                      ChuckAST.AccessModifier.PUBLIC,
                      null,
                      ds.line(),
                      ds.column());
              parent.getLocalScopes().push(new java.util.HashMap<>());
              parent.getLocalTypeScopes().push(new java.util.HashMap<>());
              parent.emitExpression(declExp, staticInitCodeLocal);
              parent.getLocalScopes().pop();
              parent.getLocalTypeScopes().pop();
              staticInitCodeLocal.addInstruction(new FieldInstrs.SetStatic(s.name(), fName));
              staticInitCodeLocal.addInstruction(new StackInstrs.Pop()); // clean up
            } else if (bodyStmt instanceof ChuckAST.ExpStmt es3
                && es3.exp() instanceof ChuckAST.DeclExp rDecl3) {
              ChuckAST.DeclExp rDecl3m =
                  new ChuckAST.DeclExp(
                      rDecl3.type(),
                      rDecl3.name(),
                      rDecl3.arraySizes(),
                      rDecl3.callArgs(),
                      rDecl3.isReference(),
                      false,
                      false,
                      rDecl3.isConst(),
                      ChuckAST.AccessModifier.PUBLIC,
                      null,
                      rDecl3.line(),
                      rDecl3.column());
              parent.getLocalScopes().push(new java.util.HashMap<>());
              parent.getLocalTypeScopes().push(new java.util.HashMap<>());
              parent.emitExpression(rDecl3m, staticInitCodeLocal);
              parent.getLocalScopes().pop();
              parent.getLocalTypeScopes().pop();
              staticInitCodeLocal.addInstruction(new FieldInstrs.SetStatic(s.name(), fName));
              staticInitCodeLocal.addInstruction(new StackInstrs.Pop()); // clean up
            }
          }
        }
        staticInitCodeLocal.setStackSize(parent.getMaxLocalCount());
        parent.setCurrentClass(savedClass);
        ChuckCode finalStaticInitCode =
            staticInitCodeLocal.getNumInstructions() > 0 ? staticInitCodeLocal : null;

        UserClassDescriptor descriptor =
            new UserClassDescriptor(
                s.name(),
                s.parentName(),
                fieldDefs,
                staticFieldDefs,
                methodCodes, // main methods map
                staticMethodCodes,
                staticInts,
                staticIsDouble,
                staticObjects,
                finalPreCtorCode,
                s.isAbstract(),
                s.isInterface(),
                finalStaticInitCode,
                fieldAccess,
                methodAccess,
                s.access(),
                s.doc(),
                methodDocs,
                fieldDocs);

        // Add static methods to the main methods map too, for resolution on instances
        methodCodes.putAll(staticMethodCodes);

        parent.getUserClassRegistry().put(s.name(), descriptor);
        if (code != null) {
          code.addInstruction(new MiscInstrs.RegisterClass(s.name(), descriptor));
        }
      }
      case ChuckAST.IfStmt s -> {
        parent.emitExpression(s.condition(), code);
        int jumpIdx = code.getNumInstructions();
        code.addInstruction(null); // placeholder for JumpIfFalse

        int bodyStart = code.getNumInstructions();
        this.emitStatement(s.thenBranch(), code);

        if (s.elseBranch() != null) {
          int thenEndIdx = code.getNumInstructions();
          code.addInstruction(null); // placeholder for Jump (skip else)
          int elseStartIdx = code.getNumInstructions();
          code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(elseStartIdx));
          this.emitStatement(s.elseBranch(), code);
          int elseEndIdx = code.getNumInstructions();
          code.replaceInstruction(thenEndIdx, new ControlInstrs.Jump(elseEndIdx));
        } else {
          int endIdx = code.getNumInstructions();
          code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endIdx));
        }
      }
      case ChuckAST.ForEachStmt s -> {
        parent.getLocalScopes().push(new HashMap<>());
        parent.getLocalTypeScopes().push(new HashMap<>());
        int oldLocalCount = parent.getLocalCount();
        Map<String, Integer> scope = parent.getLocalScopes().peek();

        // 1. Assign collection to a hidden local
        String collName = "__coll_" + s.iterName() + "_" + s.line() + "_" + s.column();
        int collOffset = parent.getLocalCount();
        parent.setLocalCount(collOffset + 1);
        scope.put(collName, collOffset);
        parent.emitExpression(s.collection(), code);
        code.addInstruction(new VarInstrs.StoreLocal(collOffset));
        code.addInstruction(new StackInstrs.Pop());

        // 2. Initialize index local to 0
        String idxName = "__idx_" + s.iterName() + "_" + s.line() + "_" + s.column();
        int idxOffset = parent.getLocalCount();
        parent.setLocalCount(idxOffset + 1);
        scope.put(idxName, idxOffset);
        code.addInstruction(new PushInstrs.PushInt(0));
        code.addInstruction(new VarInstrs.StoreLocal(idxOffset));
        code.addInstruction(new StackInstrs.Pop());

        // 3. Loop start
        int startPc = code.getNumInstructions();
        parent.getContinueJumps().push(new ArrayList<>());
        parent.getBreakJumps().push(new ArrayList<>());

        // 4. Condition: idx < collection.size()
        code.addInstruction(new VarInstrs.LoadLocal(idxOffset));
        code.addInstruction(new VarInstrs.LoadLocal(collOffset));
        code.addInstruction(new ObjectInstrs.CallMethod("size", 0));
        code.addInstruction(new LogicInstrs.LessThanAny());

        int jumpIdx = code.getNumInstructions();
        code.addInstruction(null); // placeholder for JumpIfFalse

        // 5. Load current element: collection[idx]
        code.addInstruction(new VarInstrs.LoadLocal(collOffset));
        code.addInstruction(new VarInstrs.LoadLocal(idxOffset));
        code.addInstruction(new ArrayInstrs.GetArrayInt()); // Runtime handles type correctly

        // 6. Store in iteration variable (always fresh in loop scope)
        int iterOffset = parent.getLocalCount();
        parent.setLocalCount(iterOffset + 1);
        scope.put(s.iterName(), iterOffset);
        // Track iter var type so method dispatch inside the body works
        String iterType = s.iterType();
        if ("auto".equals(iterType)) {
          // Infer from collection element type
          String collType = parent.getExprType(s.collection());
          if (collType != null && collType.endsWith("[]")) {
            iterType = collType.substring(0, collType.length() - 2);
          }
        }
        if (iterType != null) {
          parent.getLocalTypeScopes().peek().put(s.iterName(), iterType);
        }

        if ("float".equals(iterType)) code.addInstruction(new VarInstrs.StoreLocal(iterOffset));
        else if (org.chuck.core.ChuckLanguage.CORE_DATA_TYPES.contains(iterType))
          code.addInstruction(new VarInstrs.StoreLocal(iterOffset));
        else code.addInstruction(new VarInstrs.StoreLocal(iterOffset));
        code.addInstruction(new StackInstrs.Pop());

        // 7. Loop body
        this.emitStatement(s.body(), code);

        // 8. Update: idx++
        int updateStart = code.getNumInstructions();
        code.addInstruction(new VarInstrs.LoadLocal(idxOffset));
        code.addInstruction(new PushInstrs.PushInt(1));
        code.addInstruction(new ArithmeticInstrs.AddAny());
        code.addInstruction(new VarInstrs.StoreLocal(idxOffset));
        code.addInstruction(new StackInstrs.Pop());

        // 9. Jump back to condition
        code.addInstruction(new ControlInstrs.Jump(startPc));

        // 10. Loop end
        int endPc = code.getNumInstructions();
        code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));

        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
        }
      }
      case ChuckAST.RepeatStmt s -> {
        Map<String, Integer> scope = parent.getLocalScopes().peek();
        String cntName = "__repeat_cnt_" + s.line() + "_" + s.column();
        int cntOffset = parent.getLocalCount();
        parent.setLocalCount(cntOffset + 1);
        scope.put(cntName, cntOffset);

        // Store count in local
        parent.emitExpression(s.count(), code);
        code.addInstruction(new VarInstrs.StoreLocal(cntOffset));
        code.addInstruction(new StackInstrs.Pop());

        int startPc = code.getNumInstructions();
        parent.getContinueJumps().push(new ArrayList<>());
        parent.getBreakJumps().push(new ArrayList<>());

        // Condition: counter > 0
        code.addInstruction(new VarInstrs.LoadLocal(cntOffset));
        code.addInstruction(new PushInstrs.PushInt(0));
        code.addInstruction(new LogicInstrs.GreaterThanAny());
        int jumpIdx = code.getNumInstructions();
        code.addInstruction(null); // placeholder for JumpIfFalse

        // Body
        this.emitStatement(s.body(), code);

        // Decrement counter
        int updateStart = code.getNumInstructions();
        code.addInstruction(new VarInstrs.LoadLocal(cntOffset));
        code.addInstruction(new PushInstrs.PushInt(1));
        code.addInstruction(new ArithmeticInstrs.MinusAny());
        code.addInstruction(new VarInstrs.StoreLocal(cntOffset));
        code.addInstruction(new StackInstrs.Pop());
        code.addInstruction(new ControlInstrs.Jump(startPc));

        int endPc = code.getNumInstructions();
        code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));

        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
        }
      }
      case ChuckAST.LoopStmt s -> {
        // Infinite loop: emit body, jump back — only break exits
        int startPc = code.getNumInstructions();
        parent.getContinueJumps().push(new ArrayList<>());
        parent.getBreakJumps().push(new ArrayList<>());
        this.emitStatement(s.body(), code);
        code.addInstruction(new ControlInstrs.Jump(startPc));
        int endPc = code.getNumInstructions();
        for (int jump : parent.getBreakJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
        }
        for (int jump : parent.getContinueJumps().pop()) {
          code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
        }
      }
      default -> {}
    }
  }
}
