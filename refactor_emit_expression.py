
import sys

file_path = '/Users/ludo/a/chuckjava/src/main/java/org/chuck/compiler/ChuckEmitter.java'

with open(file_path, 'r') as f:
    content = f.read()

start_marker = '    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {'
end_marker = '    private static final java.util.Set<String> READ_ONLY_GLOBALS'

start_idx = content.find(start_marker)
if start_idx == -1:
    print("Start marker not found")
    sys.exit(1)

end_idx = content.find(end_marker, start_idx)
if end_idx == -1:
    print("End marker not found")
    sys.exit(1)

# Find the last closing brace of emitExpression before end_marker
last_brace_idx = content.rfind('}', start_idx, end_idx)
if last_brace_idx == -1:
    print("Closing brace not found")
    sys.exit(1)

new_emit_expression = """    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        switch (exp) {
            case ChuckAST.IntExp e -> code.addInstruction(new PushInt(e.value()));
            case ChuckAST.FloatExp e -> code.addInstruction(new PushFloat(e.value()));
            case ChuckAST.StringExp e -> code.addInstruction(new PushString(e.value()));
            case ChuckAST.MeExp _ -> code.addInstruction(new PushMe());
            case ChuckAST.UnaryExp e -> {
                if (e.op() == ChuckAST.Operator.S_OR) {
                    String innerType = getVarType(e.exp());
                    if (innerType != null && userClassRegistry.containsKey(innerType)) {
                        ChuckCode opCode = functions.get("__pub_op__!:1");
                        if (opCode == null) opCode = functions.get("__op__!:1");
                        if (opCode != null) {
                            emitExpression(e.exp(), code);
                            code.addInstruction(new CallFunc(opCode, 1));
                            return;
                        }
                    }
                }
                emitExpression(e.exp(), code);
                switch (e.op()) {
                    case MINUS -> code.addInstruction(new NegateAny());
                    case S_OR  -> code.addInstruction(new LogicalNot());
                    case PLUS  -> {}
                    default    -> {}
                }
            }
            case ChuckAST.DeclExp e -> {
                // Check for 'new' on primitive types or undefined types
                if (e.name().startsWith("@new_")) {
                    boolean isArrayNew = e.name().startsWith("@new_array_");
                    java.util.Set<String> primitives = java.util.Set.of("int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", "complex", "polar");
                    if (primitives.contains(e.type()) && !isArrayNew) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot use 'new' on primitive type '" + e.type() + "'");
                    }
                    // Check for undefined type (not a known class or UGen)
                    if (!userClassRegistry.containsKey(e.type()) && !isKnownUGenType(e.type()) && !primitives.contains(e.type())) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: undefined type '" + e.type() + "'");
                    }
                }
                // Check for static variable declared outside class scope
                if (e.isStatic() && (currentClass == null || !localScopes.isEmpty())) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                        + ": error: static variables must be declared at class scope");
                }
                // Track empty-array variable declarations
                if (!e.arraySizes().isEmpty() && e.arraySizes().get(0) instanceof ChuckAST.IntExp sz0e && sz0e.value() < 0) {
                    emptyArrayVars.add(e.name());
                }
                varTypes.put(e.name(), e.type()); // track variable type
                int argCount = 0;
                boolean isUserClass = userClassRegistry.containsKey(e.type());
                boolean forceGlobal = e.isGlobal();
                // Compile-time check: detect global type conflicts
                if (forceGlobal || localScopes.isEmpty()) {
                    String prevType = globalVarTypes.get(e.name());
                    if (prevType != null && !prevType.equals(e.type()) && !prevType.equals("int") && !prevType.equals("float") && !prevType.equals("string")) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: global " + prevType + " '" + e.name() + "' has different type '" + e.type()
                            + "' than already existing global " + prevType + " of the same name");
                    }
                    globalVarTypes.put(e.name(), e.type());
                }

                if (isUserClass && e.callArgs() instanceof ChuckAST.CallExp call) {
                    Integer localOffset = forceGlobal ? null : getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, 0, e.isReference(), false, userClassRegistry));
                    } else if (!forceGlobal && !localScopes.isEmpty()) {
                        Map<String, Integer> scope = localScopes.peek();
                        localOffset = scope.size();
                        scope.put(e.name(), localOffset);
                        code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, 0, e.isReference(), false, userClassRegistry));
                    } else {
                        code.addInstruction(new InstantiateSetAndPushGlobal(e.type(), e.name(), 0, e.isReference(), false, userClassRegistry));
                    }
                    code.addInstruction(new Dup());
                    for (ChuckAST.Exp arg : call.args()) emitExpression(arg, code);
                    code.addInstruction(new CallMethod(e.type(), call.args().size()));
                } else {
                    if (e.callArgs() instanceof ChuckAST.CallExp call) {
                        for (ChuckAST.Exp arg : call.args()) emitExpression(arg, code);
                        argCount = call.args().size();
                    }
                    if (!e.arraySizes().isEmpty()) {
                        for (ChuckAST.Exp sizeExp : e.arraySizes()) emitExpression(sizeExp, code);
                        argCount = e.arraySizes().size();
                    }

                    boolean isArrayDecl = !e.arraySizes().isEmpty();
                    boolean useGlobal = forceGlobal || localScopes.isEmpty() || (localScopes.size() == 1 && isArrayDecl);

                    Integer localOffset = (forceGlobal || useGlobal) ? null : getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, argCount, e.isReference(), isArrayDecl, userClassRegistry));
                    } else if (!forceGlobal && !useGlobal && !localScopes.isEmpty()) {
                        Map<String, Integer> scope = localScopes.peek();
                        localOffset = scope.size();
                        scope.put(e.name(), localOffset);
                        code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, argCount, e.isReference(), isArrayDecl, userClassRegistry));
                    } else {
                        code.addInstruction(new InstantiateSetAndPushGlobal(e.type(), e.name(), argCount, e.isReference(), isArrayDecl, userClassRegistry));
                    }
                }
            }
            case ChuckAST.BinaryExp e -> {
                if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                    // Check for 'auto' type inference errors
                    if (e.rhs() instanceof ChuckAST.DeclExp rDecl && rDecl.type().equals("auto")) {
                        if (e.lhs() instanceof ChuckAST.IdExp lhsId) {
                            String n = lhsId.name();
                            if (n.equals("null")) throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot infer 'auto' type from 'null'");
                            if (n.equals("void")) throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot infer 'auto' type from 'void'");
                            String lhsType = varTypes.get(n);
                            if ("auto".equals(lhsType)) throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot infer 'auto' type from another 'auto' variable");
                        }
                    }
                    // Check for empty-array DeclExp as chuck target (error-ugen-array-link-1)
                    if (e.op() == ChuckAST.Operator.CHUCK && e.rhs() instanceof ChuckAST.DeclExp rDecl2
                            && !rDecl2.arraySizes().isEmpty()) {
                        ChuckAST.Exp sz = rDecl2.arraySizes().get(0);
                        if (sz instanceof ChuckAST.IntExp szInt && szInt.value() < 0) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot connect '=>' to empty array '[ ]' declaration");
                        }
                    }
                    // Check for empty-array DeclExp as chuck source (error-ugen-array-link-2)
                    if (e.op() == ChuckAST.Operator.CHUCK && e.lhs() instanceof ChuckAST.DeclExp lDecl2
                            && !lDecl2.arraySizes().isEmpty()) {
                        ChuckAST.Exp sz = lDecl2.arraySizes().get(0);
                        if (sz instanceof ChuckAST.IntExp szInt && szInt.value() < 0) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot connect '=>' from empty array '[ ]' declaration");
                        }
                    }
                    // Check static decl with static-scope violations
                    if (e.rhs() instanceof ChuckAST.DeclExp rDecl3 && rDecl3.isStatic()) {
                        if (currentClass == null || !localScopes.isEmpty()) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: static variables must be declared at class scope");
                        }
                        // Check static init source for invalid references
                        checkStaticInitSource(e.lhs());
                    }
                    // Check if LHS is an empty-array variable (error-ugen-array-link-2)
                    if (e.op() == ChuckAST.Operator.CHUCK && e.lhs() instanceof ChuckAST.IdExp lhsId2
                            && emptyArrayVars.contains(lhsId2.name())) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot connect empty array '" + lhsId2.name() + "[]' => to other UGens");
                    }
                    // Check AT_CHUCK to explicit-sized array declaration (error-array-assign)
                    if (e.op() == ChuckAST.Operator.AT_CHUCK && e.rhs() instanceof ChuckAST.DeclExp rDecl4
                            && !rDecl4.arraySizes().isEmpty()
                            && rDecl4.arraySizes().get(0) instanceof ChuckAST.IntExp szI4 && szI4.value() >= 0) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot use '@=>' to assign to array declaration with explicit size");
                    }
                    // Check for multi-variable declaration error (test 114.ck)
                    Object rhs = e.rhs();
                    if (rhs instanceof ChuckAST.BlockStmt) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot '=>' from/to a multi-variable declaration");
                    }
                    emitExpression(e.lhs(), code);
                    emitChuckTarget(e.rhs(), code);
                } else if (e.op() == ChuckAST.Operator.UNCHUCK) {
                    emitExpression(e.lhs(), code);
                    emitExpression(e.rhs(), code);
                    code.addInstruction(new ChuckUnchuck());
                } else if (e.op() == ChuckAST.Operator.UPCHUCK) {
                    // Error if used on user-class objects (local =^ operator not exported)
                    String lhsType = getVarType(e.lhs());
                    if (lhsType != null && userClassRegistry.containsKey(lhsType)) {
                        String rhsType = getVarType(e.rhs());
                        String rhsName = rhsType != null ? rhsType : lhsType;
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot resolve operator '=^' on types '"
                                + lhsType + "' and '" + rhsName + "'");
                    }
                    emitExpression(e.lhs(), code);
                    emitChuckTarget(e.rhs(), code);
                } else if (e.op() == ChuckAST.Operator.APPEND) {
                    emitExpression(e.lhs(), code);
                    emitExpression(e.rhs(), code);
                    code.addInstruction(new CallMethod("append", 1));
                } else if (e.op() == ChuckAST.Operator.NEW) {
                    // Check for empty brackets: new SinOsc[] (error-empty-bracket)
                    if (e.rhs() instanceof ChuckAST.IntExp szNew && szNew.value() < 0) {
                        String typeName = e.lhs() instanceof ChuckAST.IdExp tid ? tid.name() : "?";
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot use 'new " + typeName + "[]' with empty brackets");
                    }
                    emitExpression(e.rhs(), code); // size
                    code.addInstruction(new NewArray());
                } else if (e.op() == ChuckAST.Operator.PLUS_CHUCK || e.op() == ChuckAST.Operator.MINUS_CHUCK
                        || e.op() == ChuckAST.Operator.TIMES_CHUCK || e.op() == ChuckAST.Operator.DIVIDE_CHUCK
                        || e.op() == ChuckAST.Operator.PERCENT_CHUCK) {
                    // Disallow compound assignment to a declaration (error-add-assign-decl)
                    if (e.rhs() instanceof ChuckAST.DeclExp) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot use compound assignment operator with a variable declaration");
                    }
                    // User-class prefix ++ dispatch (++a where a is user class with __op__++:1)
                    if (e.op() == ChuckAST.Operator.PLUS_CHUCK
                            && e.lhs() instanceof ChuckAST.IntExp lhsInt && lhsInt.value() == 1
                            && e.rhs() instanceof ChuckAST.IdExp rhsId) {
                        String rhsType = varTypes.get(rhsId.name());
                        if (rhsType != null && userClassRegistry.containsKey(rhsType)) {
                            ChuckCode opCode = functions.get("__pub_op__++:1");
                            if (opCode == null) opCode = functions.get("__op__++:1");
                            if (opCode != null) {
                                emitExpression(e.rhs(), code);
                                code.addInstruction(new CallFunc(opCode, 1));
                                return;
                            }
                        }
                    }
                    // Disallow compound assignments on read-only builtins (e.g. now++, 1 +=> pi)
                    if (e.rhs() instanceof ChuckAST.IdExp rid) {
                        if (rid.name().equals("now")) throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot perform compound assignment on 'now'");
                        if (rid.name().equals("pi") || rid.name().equals("e") || rid.name().equals("maybe"))
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot assign to read-only value '" + rid.name() + "'");
                    } else if (e.rhs() instanceof ChuckAST.DotExp rdot
                            && rdot.base() instanceof ChuckAST.IdExp baseId
                            && baseId.name().equals("Math")) {
                        switch (rdot.member()) {
                            case "PI", "TWO_PI", "HALF_PI", "E", "INFINITY", "NEGATIVE_INFINITY", "NaN", "nan" ->
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: 'Math." + rdot.member() + "' is a constant, and is not assignable");
                            default -> {}
                        }
                    }
                    ChuckAST.Operator arith = switch (e.op()) {
                        case PLUS_CHUCK    -> ChuckAST.Operator.PLUS;
                        case MINUS_CHUCK   -> ChuckAST.Operator.MINUS;
                        case TIMES_CHUCK   -> ChuckAST.Operator.TIMES;
                        case DIVIDE_CHUCK  -> ChuckAST.Operator.DIVIDE;
                        case PERCENT_CHUCK -> ChuckAST.Operator.PERCENT;
                        default -> ChuckAST.Operator.PLUS;
                    };
                    emitExpression(e.rhs(), code);  // push current value of target
                    emitExpression(e.lhs(), code);  // push the operand
                    switch (arith) {
                        case PLUS    -> code.addInstruction(new AddAny());
                        case MINUS   -> code.addInstruction(new MinusAny());
                        case TIMES   -> code.addInstruction(new TimesAny());
                        case DIVIDE  -> code.addInstruction(new DivideAny());
                        case PERCENT -> code.addInstruction(new ModuloAny());
                        default -> {}
                    }
                    emitChuckTarget(e.rhs(), code);
                } else if (e.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS || e.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS) {
                    // Postfix ++ / -- : dispatch to user-class operator, or return old value for primitives
                    if (e.rhs() instanceof ChuckAST.DeclExp) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: cannot use compound assignment operator with a variable declaration");
                    }
                    boolean isPostfixPlus = e.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS;
                    String rhsType = getVarType(e.rhs());
                    if (rhsType != null && userClassRegistry.containsKey(rhsType)) {
                        // Call postfix operator: __pub_op__:1 or __op__:1 (no symbol = postfix ++)
                        String opKey = isPostfixPlus ? ":1" : "--:1";
                        ChuckCode opCode = functions.get("__pub_op__" + opKey);
                        if (opCode == null) opCode = functions.get("__op__" + opKey);
                        if (opCode != null) {
                            emitExpression(e.rhs(), code);
                            code.addInstruction(new CallFunc(opCode, 1));
                            return;
                        }
                    }
                    // Primitive postfix: push old value, push current value again, add/sub 1, store back, pop new value
                    // Old value remains on stack as the expression result
                    emitExpression(e.rhs(), code);  // push old value (for return)
                    emitExpression(e.rhs(), code);  // push current value again (for arithmetic)
                    emitExpression(e.lhs(), code);  // push 1
                    if (isPostfixPlus) code.addInstruction(new AddAny());
                    else code.addInstruction(new MinusAny());
                    emitChuckTarget(e.rhs(), code); // store new value, pushes new value back
                    code.addInstruction(new Pop());  // discard new value, old value remains
                } else if (e.op() == ChuckAST.Operator.WRITE_IO) {
                    emitExpression(e.rhs(), code);
                    emitExpression(e.lhs(), code);
                    code.addInstruction(new WriteIO());
                } else if (e.op() == ChuckAST.Operator.SWAP) {
                    emitSwapTarget(e.lhs(), e.rhs(), code);
                } else if (e.op() == ChuckAST.Operator.AT_CHUCK) {
                    emitExpression(e.lhs(), code);
                    if (e.rhs() instanceof ChuckAST.IdExp id) {
                        Integer lo = getLocalOffset(id.name());
                        if (lo != null) code.addInstruction(new StoreLocal(lo));
                        else {
                            // For @=>, we want direct assignment to the global
                            code.addInstruction(new SetGlobalObjectOnly(id.name()));
                        }
                    } else if (e.rhs() instanceof ChuckAST.DotExp dot) {
                        emitExpression(dot.base(), code);
                        code.addInstruction(new SetMemberIntByName(dot.member()));
                    } else if (e.rhs() instanceof ChuckAST.ArrayAccessExp ae) {
                        emitExpression(ae.base(), code);
                        for (int i = 0; i < ae.indices().size(); i++) {
                            emitExpression(ae.indices().get(i), code);
                            if (i < ae.indices().size() - 1) code.addInstruction(new GetArrayInt());
                            else code.addInstruction(new SetArrayInt());
                        }
                    }
                } else if (e.op() == ChuckAST.Operator.ASSIGN) {
                    emitExpression(e.rhs(), code);
                    if (e.lhs() instanceof ChuckAST.IdExp id) {
                        Integer localOffset = getLocalOffset(id.name());
                        if (localOffset != null) code.addInstruction(new StoreLocal(localOffset));
                        else code.addInstruction(new SetGlobalObjectOrInt(id.name()));
                    } else if (e.lhs() instanceof ChuckAST.DotExp dot) {
                        emitExpression(dot.base(), code);
                        code.addInstruction(new SetMemberIntByName(dot.member()));
                    } else if (e.lhs() instanceof ChuckAST.ArrayAccessExp ae) {
                        emitExpression(ae.base(), code);
                        for (int i = 0; i < ae.indices().size(); i++) {
                            emitExpression(ae.indices().get(i), code);
                            if (i < ae.indices().size() - 1) code.addInstruction(new GetArrayInt());
                            else code.addInstruction(new SetArrayInt());
                        }
                    }
                } else if (e.op() == ChuckAST.Operator.DUR_MUL) {
                    emitExpression(e.lhs(), code);
                    if (e.rhs() instanceof ChuckAST.IdExp id) {
                        code.addInstruction(new CreateDuration(id.name()));
                    }
                } else {
                    // Check for public operator overload on user-class types
                    if (e.op() == ChuckAST.Operator.PLUS) {
                        // Detect function reference used in binary + (e.g., foo + "" or Foo.bar + "")
                        if (e.lhs() instanceof ChuckAST.IdExp idExp) {
                            String fnName = idExp.name();
                            boolean isFuncRef = functions.keySet().stream().anyMatch(k -> k.startsWith(fnName + ":"));
                            if (isFuncRef) {
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                    + ": error: cannot perform '+' on '[fun]" + fnName + "()' and value");
                            }
                        }
                        if (e.lhs() instanceof ChuckAST.DotExp lhsDot
                                && lhsDot.base() instanceof ChuckAST.IdExp baseId
                                && userClassRegistry.containsKey(baseId.name())) {
                            UserClassDescriptor d = userClassRegistry.get(baseId.name());
                            String memName = lhsDot.member();
                            boolean isMemberFunc = d.methods().containsKey(memName)
                                || d.staticMethods().keySet().stream().anyMatch(k -> k.startsWith(memName + ":"));
                            if (isMemberFunc) {
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                    + ": error: cannot perform '+' on '[fun]" + baseId.name() + "." + memName + "()' and value");
                            }
                        }
                    }
                    // Check for user-class binary operator overload (+, -, *, /, %, <, <=, >, >=, ==, !=)
                    {
                        String lhsType = getExprType(e.lhs());
                        if (lhsType != null && userClassRegistry.containsKey(lhsType)) {
                            String opSymbol = switch (e.op()) {
                                case PLUS -> "+"; case MINUS -> "-"; case TIMES -> "*";
                                case DIVIDE -> "/"; case PERCENT -> "%";
                                case LT -> "<"; case LE -> "<="; case GT -> ">";
                                case GE -> ">="; case EQ -> "=="; case NEQ -> "!=";
                                default -> null;
                            };
                            if (opSymbol != null) {
                                ChuckCode opFunc = functions.get("__pub_op__" + opSymbol + ":2");
                                if (opFunc == null) opFunc = functions.get("__op__" + opSymbol + ":2");
                                if (opFunc != null) {
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    code.addInstruction(new CallFunc(opFunc, 2));
                                    return;
                                }

                                // Also check for method-style overload in user class
                                UserClassDescriptor desc = userClassRegistry.get(lhsType);
                                if (desc != null) {
                                    String mName = desc.methods().containsKey("__pub_op__" + opSymbol + ":1") ? "__pub_op__" + opSymbol :
                                                  (desc.methods().containsKey("__op__" + opSymbol + ":1") ? "__op__" + opSymbol : null);
                                    if (mName != null) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new CallMethod(mName, 1));
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    // Check for complex/polar built-in arithmetic
                    {
                        String lhsType = getExprType(e.lhs());
                        if ("complex".equals(lhsType) || "polar".equals(lhsType)) {
                            boolean isPolar = "polar".equals(lhsType);
                            ChuckInstr complexInstr = switch (e.op()) {
                                case PLUS  -> isPolar ? new PolarAdd()  : new ComplexAdd();
                                case MINUS -> isPolar ? new PolarSub()  : new ComplexSub();
                                case TIMES -> isPolar ? new PolarMul()  : new ComplexMul();
                                case DIVIDE -> isPolar ? new PolarDiv() : new ComplexDiv();
                                default -> null;
                            };
                            if (complexInstr != null) {
                                emitExpression(e.lhs(), code);
                                emitExpression(e.rhs(), code);
                                code.addInstruction(complexInstr);
                                return;
                            }
                        }
                    }
                    // Check for vec2/vec3/vec4 element-wise arithmetic
                    {
                        String lhsType = getExprType(e.lhs());
                        if ("vec2".equals(lhsType) || "vec3".equals(lhsType) || "vec4".equals(lhsType)) {
                            switch (e.op()) {
                                case PLUS -> {
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    code.addInstruction(new VecAdd());
                                    return;
                                }
                                case MINUS -> {
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    code.addInstruction(new VecSub());
                                    return;
                                }
                                case TIMES -> {
                                    // vec * scalar: check if rhs is scalar (float/int)
                                    String rhsType = getExprType(e.rhs());
                                    if (rhsType == null || "float".equals(rhsType) || "int".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new VecScale());
                                        return;
                                    }
                                    // vec * vec → dot product
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    code.addInstruction(new VecDot());
                                    return;
                                }
                                default -> {} // fall through to generic
                            }
                        }
                    }
                    // Check for UGen in arithmetic context (error-binary: 1 + dac)
                    if (e.op() == ChuckAST.Operator.PLUS || e.op() == ChuckAST.Operator.MINUS
                            || e.op() == ChuckAST.Operator.TIMES || e.op() == ChuckAST.Operator.DIVIDE) {
                        java.util.Set<String> ugenGlobals = java.util.Set.of("dac", "blackhole", "adc");
                        if (e.lhs() instanceof ChuckAST.IdExp lid && ugenGlobals.contains(lid.name())) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot perform arithmetic on UGen '" + lid.name() + "'");
                        }
                        if (e.rhs() instanceof ChuckAST.IdExp rid && ugenGlobals.contains(rid.name())) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot perform arithmetic on UGen '" + rid.name() + "'");
                        }
                    }
                    if (e.op() != ChuckAST.Operator.AND && e.op() != ChuckAST.Operator.OR) {
                        emitExpression(e.lhs(), code);
                        emitExpression(e.rhs(), code);
                    }
                    switch (e.op()) {
                        case PLUS    -> code.addInstruction(new AddAny());
                        case MINUS   -> code.addInstruction(new MinusAny());
                        case TIMES   -> code.addInstruction(new TimesAny());
                        case DIVIDE  -> code.addInstruction(new DivideAny());
                        case PERCENT -> code.addInstruction(new ModuloAny());
                        case S_OR    -> code.addInstruction(new BitwiseOrAny());
                        case S_AND   -> code.addInstruction(new BitwiseAndAny());
                        case LT      -> code.addInstruction(new LessThanAny());
                        case LE      -> code.addInstruction(new LessOrEqualAny());
                        case GT      -> code.addInstruction(new GreaterThanAny());
                        case GE      -> code.addInstruction(new GreaterOrEqualAny());
                        case EQ      -> code.addInstruction(new EqualsAny());
                        case NEQ     -> code.addInstruction(new NotEqualsAny());
                        case DUR_MUL -> {
                            emitExpression(e.lhs(), code);
                            if (e.rhs() instanceof ChuckAST.IdExp id) {
                                code.addInstruction(new CreateDuration(id.name()));
                            } else {
                                emitExpression(e.rhs(), code);
                                // Fallback?
                            }
                        }
                        case WRITE_IO -> {
                            emitExpression(e.lhs(), code);
                            emitExpression(e.rhs(), code);
                            code.addInstruction(new ChuckWriteIO());
                        }
                        case AND -> {
                            String lt = getExprType(e.lhs());
                            String rt = getExprType(e.rhs());
                            if ("Event".equals(lt) || "Event".equals(rt)) {
                                emitExpression(e.lhs(), code);
                                emitExpression(e.rhs(), code);
                                code.addInstruction(new CreateEventConjunction());
                            } else {
                                emitExpression(e.lhs(), code);
                                int jumpIdx = code.getNumInstructions();
                                code.addInstruction(null); // placeholder for JumpIfFalse
                                emitExpression(e.rhs(), code);
                                int endIdx = code.getNumInstructions();
                                code.replaceInstruction(jumpIdx, new JumpIfFalseAndPushFalse(endIdx));
                            }
                        }
                        case OR -> {
                            String lt = getExprType(e.lhs());
                            String rt = getExprType(e.rhs());
                            if ("Event".equals(lt) || "Event".equals(rt)) {
                                emitExpression(e.lhs(), code);
                                emitExpression(e.rhs(), code);
                                code.addInstruction(new CreateEventDisjunction());
                            } else {
                                emitExpression(e.lhs(), code);
                                int jumpIdx = code.getNumInstructions();
                                code.addInstruction(null); // placeholder for JumpIfTrue
                                emitExpression(e.rhs(), code);
                                int endIdx = code.getNumInstructions();
                                code.replaceInstruction(jumpIdx, new JumpIfTrueAndPushTrue(endIdx));
                            }
                        }
                        default -> {}
                    }
                }
            }
            case ChuckAST.IdExp e -> {
                Integer localOffset = getLocalOffset(e.name());
                if (e.name().equals("this") && currentClass == null) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                        + ": error: keyword 'this' cannot be used outside class definition");
                }
                if (e.name().equals("super") && inStaticFuncContext) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                        + ": error: keyword 'super' cannot be used inside static functions");
                }
                if (localOffset != null) code.addInstruction(new LoadLocal(localOffset));
                else if (e.name().equals("now")) code.addInstruction(new PushNow());
                else if (e.name().equals("dac")) code.addInstruction(new PushDac());
                else if (e.name().equals("blackhole")) code.addInstruction(new PushBlackhole());
                else if (e.name().equals("adc")) code.addInstruction(new PushAdc());
                else if (e.name().equals("me")) code.addInstruction(new PushMe());
                else if (e.name().equals("cherr")) code.addInstruction(new PushCherr());
                else if (e.name().equals("chout")) code.addInstruction(new PushChout());
                else if (e.name().equals("Machine")) code.addInstruction(new PushMachine());
                else if (e.name().equals("maybe")) code.addInstruction(new PushMaybe());
                else if (e.name().equals("second") || e.name().equals("ms") || e.name().equals("samp")
                        || e.name().equals("minute") || e.name().equals("hour")) {
                    code.addInstruction(new PushInt(1));
                    code.addInstruction(new CreateDuration(e.name()));
                } else if (currentClassFields.contains(e.name())) {
                    code.addInstruction(new GetUserField(e.name()));
                } else if (currentClass != null && userClassRegistry.get(currentClass) != null &&
                        (userClassRegistry.get(currentClass).staticInts().containsKey(e.name()) ||
                         userClassRegistry.get(currentClass).staticObjects().containsKey(e.name()))) {
                    code.addInstruction(new GetStatic(currentClass, e.name()));
                } else {
                    code.addInstruction(new GetGlobalObjectOrInt(e.name()));
                }
            }
            case ChuckAST.DotExp e -> {
                // Handle static field access: ClassName.staticField
                if (e.base() instanceof ChuckAST.IdExp id) {
                    String potentialClassName = id.name();
                    UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                    if (classDesc != null && (classDesc.staticInts().containsKey(e.member()) || classDesc.staticObjects().containsKey(e.member()))) {
                        code.addInstruction(new GetStatic(potentialClassName, e.member()));
                        return;
                    }
                }
                if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
                    if (e.member().equals("nl") || e.member().equals("newline")) {
                        code.addInstruction(new PushString("\\n"));
                    } else {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.ChuckIO", e.member()));
                    }
                    return;
                }
                if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                    switch (e.member()) {
                        case "INFINITY", "infinity" -> { code.addInstruction(new PushFloat(Double.POSITIVE_INFINITY)); return; }
                        case "NEGATIVE_INFINITY"    -> { code.addInstruction(new PushFloat(Double.NEGATIVE_INFINITY)); return; }
                        case "NaN", "nan"           -> { code.addInstruction(new PushFloat(Double.NaN)); return; }
                        case "PI"                   -> { code.addInstruction(new PushFloat(Math.PI)); return; }
                        case "TWO_PI"               -> { code.addInstruction(new PushFloat(2.0 * Math.PI)); return; }
                        case "HALF_PI"              -> { code.addInstruction(new PushFloat(Math.PI / 2.0)); return; }
                        case "E"                    -> { code.addInstruction(new PushFloat(Math.E)); return; }
                        case "SQRT2"                -> { code.addInstruction(new PushFloat(Math.sqrt(2.0))); return; }
                    }
                }
                if (e.member().equals("size")) {
                    emitExpression(e.base(), code);
                    code.addInstruction(new CallMethod("size", 0));
                    return;
                } else if (e.member().equals("zero")) {
                    emitExpression(e.base(), code);
                    code.addInstruction(new ArrayZero());
                    // ChucK's .zero() returns the array itself for chaining
                    return;
                }
                if (e.base() instanceof ChuckAST.IdExp id) {
                    String baseType = getExprType(e.base());
                    if (baseType != null && userClassRegistry.containsKey(baseType)) {
                        UserClassDescriptor d = userClassRegistry.get(baseType);
                        if (d.staticInts().containsKey(e.member()) || d.staticObjects().containsKey(e.member())) {
                            code.addInstruction(new GetStatic(baseType, e.member()));
                            return;
                        }
                    }
                    if (id.name().equals("ADSR") || id.name().equals("Adsr")) {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.audio.Adsr", e.member()));
                        return;
                    }
                    if (id.name().equals("Std")) {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.Std", e.member()));
                        return;
                    }
                    if (id.name().equals("RegEx")) {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.RegEx", e.member()));
                        return;
                    }
                    if (id.name().equals("Reflect")) {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.Reflect", e.member()));
                        return;
                    }
                    if (id.name().equals("SerialIO")) {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.SerialIO", e.member()));
                        return;
                    }
                    if (id.name().equals("FileIO")) {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.FileIO", e.member()));
                        return;
                    }
                    if (userClassRegistry.containsKey(id.name())) {
                        UserClassDescriptor d = userClassRegistry.get(id.name());
                        if (d.staticInts().containsKey(e.member()) || d.staticObjects().containsKey(e.member())) {
                            code.addInstruction(new GetStatic(id.name(), e.member()));
                            return;
                        }
                    }
                    if (currentClass != null && userClassRegistry.containsKey(currentClass)) {
                        UserClassDescriptor d = userClassRegistry.get(currentClass);
                        if (d.staticObjects().containsKey(id.name())) {
                            code.addInstruction(new GetStatic(currentClass, id.name()));
                            code.addInstruction(new GetFieldByName(e.member()));
                            return;
                        }
                    }
                }
                // Vector field accessor: v.x / v.y / v.z / v.w / v.re / v.im / v.mag / v.phase
                {
                    int vecIdx = vecFieldIndex(e.member());
                    if (vecIdx >= 0 && e.base() instanceof ChuckAST.IdExp baseId) {
                        String baseType = varTypes.get(baseId.name());
                        if (baseType == null) baseType = globalVarTypes.get(baseId.name());
                        if (isVecType(baseType)) {
                            emitExpression(e.base(), code);
                            code.addInstruction(new PushInt(vecIdx));
                            code.addInstruction(new GetArrayInt());
                            return;
                        }
                    }
                }
                emitExpression(e.base(), code);
                code.addInstruction(new GetFieldByName(e.member()));
            }
            case ChuckAST.ArrayLitExp e -> {
                for (ChuckAST.Exp el : e.elements()) emitExpression(el, code);
                code.addInstruction(new NewArrayFromStack(e.elements().size()));
            }
            case ChuckAST.VectorLitExp e -> {
                for (ChuckAST.Exp el : e.elements()) emitExpression(el, code);
                code.addInstruction(new NewArrayFromStack(e.elements().size()));
            }
            case ChuckAST.ComplexLit e -> {
                emitExpression(e.re(), code);
                emitExpression(e.im(), code);
                code.addInstruction(new NewArrayFromStack(2));
            }
            case ChuckAST.PolarLit e -> {
                emitExpression(e.mag(), code);
                emitExpression(e.phase(), code);
                code.addInstruction(new NewArrayFromStack(2));
            }
            case ChuckAST.ArrayAccessExp e -> {
                emitExpression(e.base(), code);
                for (ChuckAST.Exp index : e.indices()) {
                    emitExpression(index, code);
                    code.addInstruction(new GetArrayInt());
                }
            }
            case ChuckAST.CallExp e -> {
                // Check for super.method() in wrong context
                if (e.base() instanceof ChuckAST.DotExp dot2
                        && dot2.base() instanceof ChuckAST.IdExp superId && superId.name().equals("super")) {
                    if (inStaticFuncContext) {
                        throw new RuntimeException(currentFile + ":" + superId.line() + ":" + superId.column()
                            + ": error: keyword 'super' cannot be used inside static functions");
                    }
                    // Check if parent class has the method
                    if (currentClass != null) {
                        UserClassDescriptor cd = userClassRegistry.get(currentClass);
                        String parentName = cd != null ? cd.parentName() : null;
                        if (parentName == null) {
                            // Extends Object implicitly - Object has no user-defined methods
                            throw new RuntimeException(currentFile + ":" + dot2.base().line() + ":" + dot2.base().column()
                                + ": error: class 'Object' has no member '" + dot2.member() + "'");
                        }
                        UserClassDescriptor parentDesc = userClassRegistry.get(parentName);
                        if (parentDesc != null && !parentDesc.methods().containsKey(dot2.member())) {
                            throw new RuntimeException(currentFile + ":" + dot2.base().line() + ":" + dot2.base().column()
                                + ": error: class '" + parentName + "' has no member '" + dot2.member() + "'");
                        }
                    }
                }
                if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
                    if (dot.member().equals("nl") || dot.member().equals("newline")) {
                        code.addInstruction(new PushString("\\n"));
                        return;
                    }
                }
                if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Std")) {
                    String member = dot.member();
                    java.util.Set<String> builtinStd = java.util.Set.of(
                        "mtof", "ftom", "powtodb", "rmstodb", "dbtopow", "dbtorms", "dbtolin", "lintodb",
                        "abs", "fabs", "sgn", "rand2", "rand2f", "clamp", "clampf", "scalef", "atoi", "atof", "itoa", "ftoi", "systemTime"
                    );
                    if (builtinStd.contains(member)) {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new StdFunc(member, e.args().size()));
                    } else {
                        // General fallback: try CallBuiltinStatic for Std
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new CallBuiltinStatic("org.chuck.core.Std", dot.member(), e.args().size()));
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("RegEx")) {
                    for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                    code.addInstruction(new CallBuiltinStatic("org.chuck.core.RegEx", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Reflect")) {
                    for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                    code.addInstruction(new CallBuiltinStatic("org.chuck.core.Reflect", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("SerialIO")) {
                    for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                    code.addInstruction(new CallBuiltinStatic("org.chuck.core.SerialIO", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot && dot.member().equals("last")) {
                    emitExpression(dot.base(), code);
                    code.addInstruction(new GetLastOut());
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                    switch (dot.member()) {
                        case "random", "randf" -> code.addInstruction(new MathRandom());
                        case "sin"  -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("sin")); } }
                        case "cos"  -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("cos")); } }
                        case "pow"  -> { if(e.args().size() >= 2) { emitExpression(e.args().get(0), code); emitExpression(e.args().get(1), code); code.addInstruction(new MathFunc("pow")); } }
                        case "sqrt" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("sqrt")); } }
                        case "abs"  -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("abs")); } }
                        case "floor"-> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("floor")); } }
                        case "ceil" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("ceil")); } }
                        case "equal" -> { if(e.args().size() >= 2) { emitExpression(e.args().get(0), code); emitExpression(e.args().get(1), code); code.addInstruction(new MathFunc("equal")); } }
                        case "euclidean" -> { if(e.args().size() >= 2) { emitExpression(e.args().get(0), code); emitExpression(e.args().get(1), code); code.addInstruction(new MathFunc("euclidean")); } }
                        case "isinf" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("isinf")); } }
                        case "isnan" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("isnan")); } }
                        case "log"   -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("log")); } }
                        case "log2"  -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("log2")); } }
                        case "log10" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("log10")); } }
                        case "exp"   -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("exp")); } }
                        case "round" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("round")); } }
                        case "trunc" -> { if(!e.args().isEmpty()) { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("trunc")); } }
                        case "help" -> code.addInstruction(new MathHelp());
                        default     -> {
                            if (!e.args().isEmpty()) {
                                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                                code.addInstruction(new MathFunc(dot.member()));
                            } else {
                                code.addInstruction(new MathHelp());
                            }
                        }
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Machine")) {
                    String machMember = dot.member();
                    if (machMember.equals("realtime")) { code.addInstruction(new PushInt(0)); }
                    else if (machMember.equals("silent")) { code.addInstruction(new PushInt(1)); }
                    else {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new MachineCall(machMember, e.args().size()));
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && (dot.base() instanceof ChuckAST.MeExp || (dot.base() instanceof ChuckAST.IdExp idMe && idMe.name().equals("me")))) {
                    if (dot.member().equals("yield")) {
                        code.addInstruction(new Yield());
                    } else if (dot.member().equals("dir")) {
                        if (!e.args().isEmpty()) emitExpression(e.args().get(0), code);
                        else code.addInstruction(new PushInt(0));
                        code.addInstruction(new MeDir());
                    } else if (dot.member().equals("args")) {
                        code.addInstruction(new MeArgs());
                    } else if (dot.member().equals("arg")) {
                        if (!e.args().isEmpty()) emitExpression(e.args().get(0), code);
                        else code.addInstruction(new PushInt(0));
                        code.addInstruction(new MeArg());
                    } else if (dot.member().equals("exit")) {
                        code.addInstruction(new MeExit());
                    } else {
                        code.addInstruction(new PushMe());
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new CallMethod(dot.member(), e.args().size()));
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot) {
                    if (dot.base() instanceof ChuckAST.IdExp id) {
                        if (userClassRegistry.containsKey(id.name())) {
                            String staticKey = dot.member() + ":" + e.args().size();
                            ChuckCode target = resolveStaticMethod(id.name(), staticKey);
                            if (target != null) {
                                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                                code.addInstruction(new CallFunc(target, e.args().size()));
                                return;
                            }
                        }
                        // Handle static method called via a static field access (e.g., Foo.ours.foo())
                        // First, try to resolve the base as a class type
                        String baseType = getExprType(dot.base());
                        if (baseType != null && userClassRegistry.containsKey(baseType)) {
                            String staticKey = dot.member() + ":" + e.args().size();
                            ChuckCode target = resolveStaticMethod(baseType, staticKey);
                            if (target != null) {
                                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                                code.addInstruction(new CallFunc(target, e.args().size()));
                                return;
                            }
                        }
                    }
                    emitExpression(dot.base(), code);
                    for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                    code.addInstruction(new CallMethod(dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.IdExp id) {
                    String key = id.name() + ":" + e.args().size();

                    if (currentClass != null && userClassRegistry.containsKey(currentClass)) {
                        UserClassDescriptor d = userClassRegistry.get(currentClass);
                        ChuckCode target = resolveStaticMethod(currentClass, key);
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new CallFunc(target, e.args().size()));
                            return;
                        }
                    }
                    if (functions.containsKey(key)) {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new CallFunc(functions.get(key), e.args().size()));
                    }
                }
            }
            case ChuckAST.SporkExp e -> {
                String funcName = null;
                if (e.call().base() instanceof ChuckAST.IdExp id) {
                    funcName = id.name();
                    String key = funcName + ":" + e.call().args().size();

                    ChuckCode target = resolveStaticMethod(currentClass, key);
                    if (target != null) {
                        for (ChuckAST.Exp arg : e.call().args()) emitExpression(arg, code);
                        code.addInstruction(new Spork(target, e.call().args().size()));
                        return;
                    }

                    if (functions.containsKey(key)) {
                        for (ChuckAST.Exp arg : e.call().args()) emitExpression(arg, code);
                        code.addInstruction(new Spork(functions.get(key), e.call().args().size()));
                        return;
                    }
                } else if (e.call().base() instanceof ChuckAST.DotExp dot) {
                    if (dot.base() instanceof ChuckAST.IdExp id && userClassRegistry.containsKey(id.name())) {
                        String staticKey = dot.member() + ":" + e.call().args().size();
                        ChuckCode target = resolveStaticMethod(id.name(), staticKey);
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.call().args()) emitExpression(arg, code);
                            code.addInstruction(new Spork(target, e.call().args().size()));
                            return;
                        }
                    }
                    emitExpression(dot.base(), code);
                    for (ChuckAST.Exp arg : e.call().args()) emitExpression(arg, code);
                    code.addInstruction(new SporkMethod(dot.member(), e.call().args().size()));
                    return;
                }
                emitExpression(e.call(), code);
            }
            case ChuckAST.TernaryExp e -> {
                emitExpression(e.condition(), code);
                int jumpFalseIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder JumpIfFalse to else branch
                emitExpression(e.thenExp(), code);
                int jumpEndIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder Jump to end
                code.replaceInstruction(jumpFalseIdx, new JumpIfFalse(code.getNumInstructions()));
                emitExpression(e.elseExp(), code);
                code.replaceInstruction(jumpEndIdx, new Jump(code.getNumInstructions()));
            }
        }
    }
"""

new_content = content[:start_idx] + new_emit_expression + content[last_brace_idx+1:]

with open(file_path, 'w') as f:
    f.write(new_content)
