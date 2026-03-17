package org.chuck.compiler;

import org.chuck.core.*;
import org.chuck.audio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Emits executable VM instructions from a parsed AST.
 */
public class ChuckEmitter {
    private final Map<String, ChuckCode> functions = new HashMap<>();
    private final java.util.Stack<Map<String, Integer>> localScopes = new java.util.Stack<>();
    /** Tracks variable name → declared type for operator overload dispatch. */
    private final Map<String, String> varTypes = new HashMap<>();

    private final Map<String, UserClassDescriptor> userClassRegistry = new HashMap<>();

    private String currentClass = null;
    private java.util.Set<String> currentClassFields = java.util.Collections.emptySet();
    private String currentFile = "";

    private final java.util.Stack<List<Integer>> breakJumps = new java.util.Stack<>();
    private final java.util.Stack<List<Integer>> continueJumps = new java.util.Stack<>();

    public ChuckEmitter() {
        this(new HashMap<>());
    }

    public ChuckEmitter(Map<String, UserClassDescriptor> registry) {
        this.userClassRegistry.putAll(registry);
    }

    public ChuckEmitter(Map<String, UserClassDescriptor> registry, Map<String, ChuckCode> preloadedFunctions) {
        this.userClassRegistry.putAll(registry);
        this.functions.putAll(preloadedFunctions);
    }

    /** Returns all classes this emitter has registered (including from imports). */
    public Map<String, UserClassDescriptor> getUserClassRegistry() {
        return userClassRegistry;
    }

    /** Returns public operator functions (keys starting with __pub_op__). */
    public Map<String, ChuckCode> getPublicFunctions() {
        Map<String, ChuckCode> result = new HashMap<>();
        for (var e : functions.entrySet()) {
            if (e.getKey().startsWith("__pub_op__")) result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    private String getVarType(ChuckAST.Exp exp) {
        if (exp instanceof ChuckAST.IdExp id) return varTypes.get(id.name());
        return null;
    }

    private void flattenStmts(List<ChuckAST.Stmt> input, List<ChuckAST.Stmt> output) {
        for (ChuckAST.Stmt s : input) {
            if (s instanceof ChuckAST.BlockStmt b) {
                flattenStmts(b.statements(), output);
            } else {
                output.add(s);
            }
        }
    }

    private ChuckCode resolveStaticMethod(String className, String methodKey) {
        if (className == null) return null;
        UserClassDescriptor d = userClassRegistry.get(className);
        if (d == null) return null;
        ChuckCode code = d.staticMethods().get(methodKey);
        if (code != null) return code;
        return resolveStaticMethod(d.parentName(), methodKey);
    }

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName) {
        localScopes.clear();
        varTypes.clear();
        currentFile = programName;
        // Pass 1: Collect all global function signatures
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.FuncDefStmt s) {
                String key = s.name() + ":" + s.argNames().size();
                if (!functions.containsKey(key)) functions.put(key, new ChuckCode(s.name()));
            }
        }

        // Pass 2: Populate function and class bodies
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.FuncDefStmt || stmt instanceof ChuckAST.ClassDefStmt) {
                emitStatement(stmt, null);
            }
        }

        // Pass 3: Emit top-level statements
        ChuckCode code = new ChuckCode(programName);
        code.addInstruction(new MoveArgs(0));
        for (ChuckAST.Stmt stmt : statements) {
            if (!(stmt instanceof ChuckAST.FuncDefStmt) && !(stmt instanceof ChuckAST.ImportStmt)) {
                emitStatement(stmt, code);
            }
        }
        return code;
    }

    private void emitStatement(ChuckAST.Stmt stmt, ChuckCode code) {
        if (stmt instanceof ChuckAST.ExpStmt s) {
            emitExpression(s.exp(), code);
            code.addInstruction(new Pop());
        } else if (stmt instanceof ChuckAST.WhileStmt s) {
            int startPc = code.getNumInstructions();
            continueJumps.push(new ArrayList<>());
            breakJumps.push(new ArrayList<>());

            emitExpression(s.condition(), code);
            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null); // placeholder for JumpIfFalse
            emitStatement(s.body(), code);
            code.addInstruction(new Jump(startPc));
            int endPc = code.getNumInstructions();
            code.replaceInstruction(jumpIdx, new JumpIfFalse(endPc));

            for (int jump : breakJumps.pop()) {
                code.replaceInstruction(jump, new Jump(endPc));
            }
            for (int jump : continueJumps.pop()) {
                code.replaceInstruction(jump, new Jump(startPc));
            }
        } else if (stmt instanceof ChuckAST.UntilStmt s) {
            int startPc = code.getNumInstructions();
            continueJumps.push(new ArrayList<>());
            breakJumps.push(new ArrayList<>());

            emitExpression(s.condition(), code);
            code.addInstruction(new LogicalNot());
            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null); // placeholder for JumpIfFalse
            emitStatement(s.body(), code);
            code.addInstruction(new Jump(startPc));
            int endPc = code.getNumInstructions();
            code.replaceInstruction(jumpIdx, new JumpIfFalse(endPc));

            for (int jump : breakJumps.pop()) {
                code.replaceInstruction(jump, new Jump(endPc));
            }
            for (int jump : continueJumps.pop()) {
                code.replaceInstruction(jump, new Jump(startPc));
            }
        } else if (stmt instanceof ChuckAST.DoStmt s) {
            int startPc = code.getNumInstructions();
            int bodyStart = code.getNumInstructions();
            breakJumps.push(new ArrayList<>());
            continueJumps.push(new ArrayList<>());

            emitStatement(s.body(), code);

            int condStart = code.getNumInstructions();

            emitExpression(s.condition(), code);
            if (s.isUntil()) {
                code.addInstruction(new LogicalNot());
            }
            code.addInstruction(new JumpIfTrue(bodyStart));
            int endPc = code.getNumInstructions();

            for (int jump : breakJumps.pop()) {
                code.replaceInstruction(jump, new Jump(endPc));
            }
            for (int jump : continueJumps.pop()) {
                code.replaceInstruction(jump, new Jump(condStart));
            }
        } else if (stmt instanceof ChuckAST.BreakStmt) {
            if (!breakJumps.isEmpty()) {
                breakJumps.peek().add(code.getNumInstructions());
                code.addInstruction(null); // placeholder for Jump
            }
        } else if (stmt instanceof ChuckAST.ContinueStmt) {
            if (!continueJumps.isEmpty()) {
                continueJumps.peek().add(code.getNumInstructions());
                code.addInstruction(null); // placeholder for Jump
            }
        } else if (stmt instanceof ChuckAST.ForStmt s) {
            emitStatement(s.init(), code);
            int startPc = code.getNumInstructions();
            breakJumps.push(new ArrayList<>());
            continueJumps.push(new ArrayList<>());

            if (s.condition() instanceof ChuckAST.ExpStmt es) {
                emitExpression(es.exp(), code);
            } else {
                code.addInstruction(new PushInt(1)); // truthy
            }
            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null); // placeholder for JumpIfFalse

            emitStatement(s.body(), code);

            int updateStart = code.getNumInstructions();
            emitExpression(s.update(), code);
            code.addInstruction(new Pop()); // pop update result
            code.addInstruction(new Jump(startPc));

            int endPc = code.getNumInstructions();
            code.replaceInstruction(jumpIdx, new JumpIfFalse(endPc));
            for (int jump : breakJumps.pop()) {
                code.replaceInstruction(jump, new Jump(endPc));
            }
            for (int jump : continueJumps.pop()) {
                code.replaceInstruction(jump, new Jump(updateStart));
            }
        } else if (stmt instanceof ChuckAST.BlockStmt s) {
            for (ChuckAST.Stmt inner : s.statements()) {
                emitStatement(inner, code);
            }
        } else if (stmt instanceof ChuckAST.ImportStmt) {
            // already processed by VM before emit — skip
        } else if (stmt instanceof ChuckAST.DeclStmt s) {
            varTypes.put(s.name(), s.type()); // track variable type for operator dispatch
            int argCount = 0;
            boolean isUserClass = userClassRegistry.containsKey(s.type());
            boolean isVec = s.type().equals("vec3") || s.type().equals("vec4") || s.type().equals("complex") || s.type().equals("polar");

            if (isUserClass && s.callArgs() instanceof ChuckAST.CallExp call) {
                if (localScopes.isEmpty()) {
                    code.addInstruction(new InstantiateSetAndPushGlobal(s.type(), s.name(), 0, s.isReference(), false, userClassRegistry));
                } else {
                    Map<String, Integer> scope = localScopes.peek();
                    int offset = scope.size();
                    scope.put(s.name(), offset);
                    code.addInstruction(new InstantiateSetAndPushLocal(s.type(), offset, 0, s.isReference(), false, userClassRegistry));
                }
                code.addInstruction(new Dup());
                for (ChuckAST.Exp arg : call.args()) emitExpression(arg, code);
                code.addInstruction(new CallMethod(s.type(), call.args().size()));
                code.addInstruction(new Pop());
            } else {
                if (s.callArgs() instanceof ChuckAST.CallExp call) {
                    for (ChuckAST.Exp arg : call.args()) emitExpression(arg, code);
                    argCount = call.args().size();
                }
                if (!s.arraySizes().isEmpty()) {
                    for (ChuckAST.Exp sizeExp : s.arraySizes()) emitExpression(sizeExp, code);
                    argCount = s.arraySizes().size();
                }

                if (isVec && !s.isReference() && argCount == 0) {
                    code.addInstruction(new PushInt(0)); // dummy size for instantiation
                    argCount = 1;
                }

                if (localScopes.isEmpty()) {
                    code.addInstruction(new InstantiateSetAndPushGlobal(s.type(), s.name(), argCount, s.isReference(), !s.arraySizes().isEmpty(), userClassRegistry));
                    code.addInstruction(new Pop());
                } else {
                    Map<String, Integer> scope = localScopes.peek();
                    Integer offset = scope.get(s.name());
                    if (offset == null) {
                        offset = scope.size();
                        scope.put(s.name(), offset);
                    }
                    code.addInstruction(new InstantiateSetAndPushLocal(s.type(), offset, argCount, s.isReference(), !s.arraySizes().isEmpty(), userClassRegistry));
                    code.addInstruction(new Pop());
                }
            }
        } else if (stmt instanceof ChuckAST.PrintStmt s) {
            for (ChuckAST.Exp exp : s.expressions()) {
                emitExpression(exp, code);
            }
            code.addInstruction(new ChuckPrint(s.expressions().size()));
        } else if (stmt instanceof ChuckAST.FuncDefStmt s) {
            String key = s.name() + ":" + s.argNames().size();
            ChuckCode funcCode = functions.get(key);
            if (funcCode == null) funcCode = new ChuckCode(s.name());

            Map<String, Integer> scope = new HashMap<>();
            localScopes.push(scope);
            for (int i = 0; i < s.argNames().size(); i++) {
                scope.put(s.argNames().get(i), i);
            }
            emitStatement(s.body(), funcCode);
            funcCode.addInstruction(new ReturnFunc());
            localScopes.pop();
            functions.put(key, funcCode);
        } else if (stmt instanceof ChuckAST.ReturnStmt s) {
            if (s.exp() != null) emitExpression(s.exp(), code);
            code.addInstruction(currentClass != null ? new ReturnMethod() : new ReturnFunc());
        } else if (stmt instanceof ChuckAST.ClassDefStmt s) {
            List<String[]> fieldDefs = new ArrayList<>();
            java.util.Set<String> fieldNames = new java.util.LinkedHashSet<>();
            List<ChuckAST.FuncDefStmt> methods = new ArrayList<>();

            Map<String, Long> staticInts = new java.util.concurrent.ConcurrentHashMap<>();
            Map<String, Boolean> staticIsDouble = new java.util.concurrent.ConcurrentHashMap<>();
            Map<String, Object> staticObjects = new java.util.concurrent.ConcurrentHashMap<>();

            List<ChuckAST.Stmt> flattenedBody = new ArrayList<>();
            flattenStmts(s.body(), flattenedBody);

            for (ChuckAST.Stmt bodyStmt : flattenedBody) {
                if (bodyStmt instanceof ChuckAST.DeclStmt f) {
                    if (f.isStatic()) {
                        if (f.type().equals("int")) staticInts.put(f.name(), 0L);
                        else if (f.type().equals("float")) { staticInts.put(f.name(), Double.doubleToRawLongBits(0.0)); staticIsDouble.put(f.name(), true); }
                        else if (f.type().equals("vec3")) staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 3));
                        else if (f.type().equals("vec4")) staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 4));
                        else if (f.type().equals("complex") || f.type().equals("polar")) staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 2));
                        else staticObjects.put(f.name(), null);
                    } else {
                        fieldDefs.add(new String[]{f.type(), f.name()});
                        fieldNames.add(f.name());
                    }
                } else if (bodyStmt instanceof ChuckAST.FuncDefStmt m) {
                    methods.add(m);
                }
            }
            Map<String, ChuckCode> methodCodes = new HashMap<>();
            Map<String, ChuckCode> staticMethodCodes = new HashMap<>();
            String prevClass = currentClass;
            java.util.Set<String> prevFields = currentClassFields;
            currentClass = s.name();
            currentClassFields = fieldNames;

            if (code != null) {
                for (ChuckAST.Stmt bodyStmt : flattenedBody) {
                    if (!(bodyStmt instanceof ChuckAST.DeclStmt || bodyStmt instanceof ChuckAST.FuncDefStmt)) {
                        emitStatement(bodyStmt, code);
                    }
                }
            }

            for (ChuckAST.FuncDefStmt m : methods) {
                String methodName = m.name();
                if (methodName.equals("@construct")) methodName = s.name(); // constructor
                ChuckCode methodCode = new ChuckCode(methodName);

                Map<String, Integer> scope = new HashMap<>();
                localScopes.push(scope);
                for (int i = 0; i < m.argNames().size(); i++) {
                    scope.put(m.argNames().get(i), i);
                }

                emitStatement(m.body(), methodCode);
                methodCode.addInstruction(m.isStatic() ? new ReturnFunc() : new ReturnMethod());
                if (m.isStatic()) {
                    staticMethodCodes.put(methodName + ":" + m.argNames().size(), methodCode);
                } else {
                    methodCodes.put(methodName, methodCode);
                }
                localScopes.pop();
            }
            currentClass = prevClass;
            currentClassFields = prevFields;
            UserClassDescriptor descriptor = new UserClassDescriptor(s.name(), s.parentName(), fieldDefs, methodCodes, staticMethodCodes, staticInts, staticIsDouble, staticObjects);
            userClassRegistry.put(s.name(), descriptor);
            if (code != null) {
                code.addInstruction(new RegisterClass(s.name(), descriptor));
            }
        } else if (stmt instanceof ChuckAST.IfStmt s) {
            emitExpression(s.condition(), code);
            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null); // placeholder for JumpIfFalse

            int bodyStart = code.getNumInstructions();
            emitStatement(s.thenBranch(), code);

            if (s.elseBranch() != null) {
                int thenEndIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for Jump (skip else)
                int elseStartIdx = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new JumpIfFalse(elseStartIdx));
                emitStatement(s.elseBranch(), code);
                int elseEndIdx = code.getNumInstructions();
                code.replaceInstruction(thenEndIdx, new Jump(elseEndIdx));
            } else {
                int endIdx = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new JumpIfFalse(endIdx));
            }
        } else if (stmt instanceof ChuckAST.ForEachStmt s) {
            if (localScopes.isEmpty()) {
                localScopes.push(new HashMap<>());
            }
            Map<String, Integer> scope = localScopes.peek();

            // 1. Assign collection to a hidden local
            String collName = "__coll_" + s.iterName() + "_" + s.line() + "_" + s.column();
            int collOffset = scope.size();
            scope.put(collName, collOffset);
            emitExpression(s.collection(), code);
            code.addInstruction(new StoreLocal(collOffset));
            code.addInstruction(new Pop());

            // 2. Initialize index local to 0
            String idxName = "__idx_" + s.iterName() + "_" + s.line() + "_" + s.column();
            int idxOffset = scope.size();
            scope.put(idxName, idxOffset);
            code.addInstruction(new PushInt(0));
            code.addInstruction(new StoreLocal(idxOffset));
            code.addInstruction(new Pop());

            // 3. Loop start
            int startPc = code.getNumInstructions();
            continueJumps.push(new ArrayList<>());
            breakJumps.push(new ArrayList<>());

            // 4. Condition: idx < collection.size()
            code.addInstruction(new LoadLocal(idxOffset));
            code.addInstruction(new LoadLocal(collOffset));
            code.addInstruction(new CallMethod("size", 0));
            code.addInstruction(new LessThanAny());

            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null); // placeholder for JumpIfFalse

            // 5. Load current element: collection[idx]
            code.addInstruction(new LoadLocal(collOffset));
            code.addInstruction(new LoadLocal(idxOffset));
            code.addInstruction(new GetArrayInt());

            // 6. Store in iteration variable
            Integer iterOffset = getLocalOffset(s.iterName());
            if (iterOffset == null) {
                iterOffset = scope.size();
                scope.put(s.iterName(), iterOffset);
            }
            code.addInstruction(new StoreLocal(iterOffset));
            code.addInstruction(new Pop());

            // 7. Loop body
            emitStatement(s.body(), code);

            // 8. Update: idx++
            int updateStart = code.getNumInstructions();
            code.addInstruction(new LoadLocal(idxOffset));
            code.addInstruction(new PushInt(1));
            code.addInstruction(new AddAny());
            code.addInstruction(new StoreLocal(idxOffset));
            code.addInstruction(new Pop());

            // 9. Jump back to condition
            code.addInstruction(new Jump(startPc));

            // 10. Loop end
            int endPc = code.getNumInstructions();
            code.replaceInstruction(jumpIdx, new JumpIfFalse(endPc));

            for (int jump : breakJumps.pop()) {
                code.replaceInstruction(jump, new Jump(endPc));
            }
            for (int jump : continueJumps.pop()) {
                code.replaceInstruction(jump, new Jump(updateStart));
            }
        }
    }

    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        if (exp instanceof ChuckAST.IntExp e) {
            code.addInstruction(new PushInt(e.value()));
        } else if (exp instanceof ChuckAST.FloatExp e) {
            code.addInstruction(new PushFloat(e.value()));
        } else if (exp instanceof ChuckAST.StringExp e) {
            code.addInstruction(new PushString(e.value()));
        } else if (exp instanceof ChuckAST.MeExp e) {
            code.addInstruction(new PushMe());
        } else if (exp instanceof ChuckAST.UnaryExp e) {
            emitExpression(e.exp(), code);
            switch (e.op()) {
                case MINUS -> code.addInstruction(new NegateAny());
                case S_OR  -> code.addInstruction(new LogicalNot());
                case PLUS  -> {}
                default    -> {}
            }
        } else if (exp instanceof ChuckAST.DeclExp e) {
            varTypes.put(e.name(), e.type()); // track variable type
            int argCount = 0;
            boolean isUserClass = userClassRegistry.containsKey(e.type());

            if (isUserClass && e.callArgs() instanceof ChuckAST.CallExp call) {
                Integer localOffset = getLocalOffset(e.name());
                if (localOffset != null) {
                    code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, 0, e.isReference(), false, userClassRegistry));
                } else if (!localScopes.isEmpty()) {
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

                Integer localOffset = getLocalOffset(e.name());
                if (localOffset != null) {
                    code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, argCount, e.isReference(), !e.arraySizes().isEmpty(), userClassRegistry));
                } else if (!localScopes.isEmpty()) {
                    Map<String, Integer> scope = localScopes.peek();
                    localOffset = scope.size();
                    scope.put(e.name(), localOffset);
                    code.addInstruction(new InstantiateSetAndPushLocal(e.type(), localOffset, argCount, e.isReference(), !e.arraySizes().isEmpty(), userClassRegistry));
                } else {
                    code.addInstruction(new InstantiateSetAndPushGlobal(e.type(), e.name(), argCount, e.isReference(), !e.arraySizes().isEmpty(), userClassRegistry));
                }
            }
        } else if (exp instanceof ChuckAST.BinaryExp e) {
            if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
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
                emitExpression(e.rhs(), code); // size
                code.addInstruction(new NewArray());
            } else if (e.op() == ChuckAST.Operator.PLUS_CHUCK || e.op() == ChuckAST.Operator.MINUS_CHUCK
                    || e.op() == ChuckAST.Operator.TIMES_CHUCK || e.op() == ChuckAST.Operator.DIVIDE_CHUCK
                    || e.op() == ChuckAST.Operator.PERCENT_CHUCK) {
                ChuckAST.Operator arith = switch (e.op()) {
                    case PLUS_CHUCK    -> ChuckAST.Operator.PLUS;
                    case MINUS_CHUCK   -> ChuckAST.Operator.MINUS;
                    case TIMES_CHUCK   -> ChuckAST.Operator.TIMES;
                    case DIVIDE_CHUCK  -> ChuckAST.Operator.DIVIDE;
                    case PERCENT_CHUCK -> ChuckAST.Operator.PERCENT;
                    default -> ChuckAST.Operator.PLUS;
                };
                emitExpression(e.lhs(), code);
                emitExpression(e.rhs(), code);
                switch (arith) {
                    case PLUS    -> code.addInstruction(new AddAny());
                    case MINUS   -> code.addInstruction(new MinusAny());
                    case TIMES   -> code.addInstruction(new TimesAny());
                    case DIVIDE  -> code.addInstruction(new DivideAny());
                    case PERCENT -> code.addInstruction(new ModuloAny());
                    default -> {}
                }
                emitChuckTarget(e.rhs(), code);
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
            } else if (e.op() == ChuckAST.Operator.WRITE_IO) {
                emitExpression(e.rhs(), code);
                emitExpression(e.lhs(), code);
                code.addInstruction(new WriteIO());
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
                    String lhsType = getVarType(e.lhs());
                    if (lhsType != null && userClassRegistry.containsKey(lhsType)) {
                        ChuckCode op = functions.get("__pub_op__+:2");
                        if (op != null) {
                            emitExpression(e.lhs(), code);
                            emitExpression(e.rhs(), code);
                            code.addInstruction(new CallFunc(op, 2));
                            return;
                        }
                    }
                }
                emitExpression(e.lhs(), code);
                emitExpression(e.rhs(), code);
                switch (e.op()) {
                    case PLUS    -> code.addInstruction(new AddAny());
                    case MINUS   -> code.addInstruction(new MinusAny());
                    case TIMES   -> code.addInstruction(new TimesAny());
                    case DIVIDE  -> code.addInstruction(new DivideAny());
                    case PERCENT -> code.addInstruction(new ModuloAny());
                    case LT      -> code.addInstruction(new LessThanAny());
                    case GT      -> code.addInstruction(new GreaterThanAny());
                    case EQ      -> code.addInstruction(new EqualsAny());
                    case NEQ     -> code.addInstruction(new NotEqualsAny());
                    case AND -> {
                        emitExpression(e.lhs(), code);
                        int jumpIdx = code.getNumInstructions();
                        code.addInstruction(null); // placeholder for JumpIfFalse
                        emitExpression(e.rhs(), code);
                        int endIdx = code.getNumInstructions();
                        code.replaceInstruction(jumpIdx, new JumpIfFalseAndPushFalse(endIdx));
                    }
                    case OR -> {
                        emitExpression(e.lhs(), code);
                        int jumpIdx = code.getNumInstructions();
                        code.addInstruction(null); // placeholder for JumpIfTrue
                        emitExpression(e.rhs(), code);
                        int endIdx = code.getNumInstructions();
                        code.replaceInstruction(jumpIdx, new JumpIfTrueAndPushTrue(endIdx));
                    }
                    default -> {}
                }
            }
        } else if (exp instanceof ChuckAST.IdExp e) {
            Integer localOffset = getLocalOffset(e.name());
            if (localOffset != null) code.addInstruction(new LoadLocal(localOffset));
            else if (e.name().equals("now")) code.addInstruction(new PushNow());
            else if (e.name().equals("dac")) code.addInstruction(new PushDac());
            else if (e.name().equals("blackhole")) code.addInstruction(new PushBlackhole());
            else if (e.name().equals("adc")) code.addInstruction(new PushAdc());
            else if (e.name().equals("me")) code.addInstruction(new PushMe());
            else if (e.name().equals("cherr")) code.addInstruction(new PushCherr());
            else if (e.name().equals("chout")) code.addInstruction(new PushChout());
            else if (e.name().equals("Machine")) code.addInstruction(new PushMachine());
            else if (e.name().equals("second") || e.name().equals("ms") || e.name().equals("samp")
                    || e.name().equals("minute") || e.name().equals("hour")) {
                code.addInstruction(new PushInt(1));
                code.addInstruction(new CreateDuration(e.name()));
            } else if (currentClassFields.contains(e.name())) {
                code.addInstruction(new GetUserField(e.name()));
            } else {
                code.addInstruction(new GetGlobalObjectOrInt(e.name()));
            }
        } else if (exp instanceof ChuckAST.DotExp e) {
            if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("IO") && (e.member().equals("nl") || e.member().equals("newline"))) {
                code.addInstruction(new PushString("\n"));
                return;
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
                if (id.name().equals("ADSR") || id.name().equals("Adsr")) {
                    code.addInstruction(new GetBuiltinStatic("org.chuck.audio.Adsr", e.member()));
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
            emitExpression(e.base(), code);
            code.addInstruction(new GetFieldByName(e.member()));
        } else if (exp instanceof ChuckAST.IntExp e) {
            code.addInstruction(new PushInt(e.value()));
        } else if (exp instanceof ChuckAST.FloatExp e) {
            code.addInstruction(new PushFloat(e.value()));
        } else if (exp instanceof ChuckAST.StringExp e) {
            code.addInstruction(new PushString(e.value()));
        } else if (exp instanceof ChuckAST.ArrayLitExp e) {
            for (ChuckAST.Exp el : e.elements()) emitExpression(el, code);
            code.addInstruction(new NewArrayFromStack(e.elements().size()));
        } else if (exp instanceof ChuckAST.VectorLitExp e) {
            for (ChuckAST.Exp el : e.elements()) emitExpression(el, code);
            code.addInstruction(new NewArrayFromStack(e.elements().size()));
        } else if (exp instanceof ChuckAST.ComplexLit e) {
            emitExpression(e.re(), code);
            emitExpression(e.im(), code);
            code.addInstruction(new NewArrayFromStack(2));
        } else if (exp instanceof ChuckAST.PolarLit e) {
            emitExpression(e.mag(), code);
            emitExpression(e.phase(), code);
            code.addInstruction(new NewArrayFromStack(2));
        } else if (exp instanceof ChuckAST.ArrayAccessExp e) {
            emitExpression(e.base(), code);
            for (ChuckAST.Exp index : e.indices()) {
                emitExpression(index, code);
                code.addInstruction(new GetArrayInt());
            }
        } else if (exp instanceof ChuckAST.CallExp e) {
            if (e.base() instanceof ChuckAST.DotExp dot
                    && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
                if (dot.member().equals("nl") || dot.member().equals("newline")) {
                    code.addInstruction(new PushString("\n"));
                    return;
                }
            }
            if (e.base() instanceof ChuckAST.DotExp dot
                    && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Std")) {
                if (dot.member().equals("mtof")) {
                    emitExpression(e.args().get(0), code);
                    code.addInstruction(new StdMtof());
                } else if (dot.member().equals("ftom")) {
                    emitExpression(e.args().get(0), code);
                    code.addInstruction(new StdFtom());
                } else if (dot.member().equals("rand") || dot.member().equals("randf")) {
                    code.addInstruction(new MathRandom());
                } else if (dot.member().equals("fabs") || dot.member().equals("abs")) {
                    emitExpression(e.args().get(0), code);
                    code.addInstruction(new MathFunc("abs"));
                } else if (dot.member().equals("rand2f") && e.args().size() >= 2) {
                    emitExpression(e.args().get(0), code);
                    emitExpression(e.args().get(1), code);
                    code.addInstruction(new Std2RandF());
                } else if (dot.member().equals("rand2") && e.args().size() >= 2) {
                    emitExpression(e.args().get(0), code);
                    emitExpression(e.args().get(1), code);
                    code.addInstruction(new Std2RandI());
                } else if (dot.member().equals("powtodb") || dot.member().equals("dbtopow")
                        || dot.member().equals("rmstodb") || dot.member().equals("dbtorms")
                        || dot.member().equals("dbtolin") || dot.member().equals("lintodb")) {
                    emitExpression(e.args().get(0), code);
                    code.addInstruction(new MathFunc(dot.member()));
                } else if (dot.member().equals("clamp") && e.args().size() == 3) {
                    emitExpression(e.args().get(0), code);
                    emitExpression(e.args().get(1), code);
                    emitExpression(e.args().get(2), code);
                    code.addInstruction(new StdClamp(false));
                } else if (dot.member().equals("clampf") && e.args().size() == 3) {
                    emitExpression(e.args().get(0), code);
                    emitExpression(e.args().get(1), code);
                    emitExpression(e.args().get(2), code);
                    code.addInstruction(new StdClamp(true));
                } else if (!e.args().isEmpty()) {
                    emitExpression(e.args().get(0), code);
                } else {
                    code.addInstruction(new PushInt(0));
                }
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
                if (dot.member().equals("realtime")) code.addInstruction(new PushInt(0));
                else if (dot.member().equals("silent")) code.addInstruction(new PushInt(1));
                else code.addInstruction(new PushInt(0));
            } else if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("me")) {
                if (dot.member().equals("yield")) {
                    code.addInstruction(new Yield());
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
                    if (currentClass != null && userClassRegistry.containsKey(currentClass)) {
                        UserClassDescriptor d = userClassRegistry.get(currentClass);
                        if (d.staticObjects().containsKey(id.name())) {
                            code.addInstruction(new GetStatic(currentClass, id.name()));
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new CallMethod(dot.member(), e.args().size()));
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
        } else if (exp instanceof ChuckAST.SporkExp e) {
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
    }

    private void emitChuckTarget(ChuckAST.Exp target, ChuckCode code) {
        if (target instanceof ChuckAST.IdExp e) {
            Integer localOffset = getLocalOffset(e.name());
            if (localOffset != null) code.addInstruction(new StoreLocal(localOffset));
            else if (e.name().equals("now")) code.addInstruction(new AdvanceTime());
            else if (e.name().equals("dac")) code.addInstruction(new ConnectToDac());
            else if (e.name().equals("blackhole")) code.addInstruction(new ConnectToBlackhole());
            else if (e.name().equals("adc")) code.addInstruction(new ConnectToAdc());
            else if (currentClassFields.contains(e.name())) code.addInstruction(new SetUserField(e.name()));
            else code.addInstruction(new SetGlobalObjectOrInt(e.name()));
        } else if (target instanceof ChuckAST.DotExp e) {
            if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("mtof")) {
                code.addInstruction(new StdMtof());
            } else if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("ftom")) {
                code.addInstruction(new StdFtom());
            } else if (e.base() instanceof ChuckAST.IdExp id && userClassRegistry.containsKey(id.name())) {
                UserClassDescriptor d = userClassRegistry.get(id.name());
                if (d.staticInts().containsKey(e.member()) || d.staticObjects().containsKey(e.member())) {
                    code.addInstruction(new SetStatic(id.name(), e.member()));
                } else {
                    emitExpression(e.base(), code);
                    code.addInstruction(new SetMemberIntByName(e.member()));
                }
            } else {
                emitExpression(e.base(), code);
                code.addInstruction(new SetMemberIntByName(e.member()));
            }
        } else if (target instanceof ChuckAST.ArrayAccessExp e) {
            emitExpression(e.base(), code);
            for (int i = 0; i < e.indices().size() - 1; i++) {
                emitExpression(e.indices().get(i), code);
                code.addInstruction(new GetArrayInt());
            }
            emitExpression(e.indices().get(e.indices().size() - 1), code);
            code.addInstruction(new SetArrayInt());
        } else if (target instanceof ChuckAST.DeclExp e) {
            emitExpression(e, code);
            code.addInstruction(new Pop());
            Integer localOffset = getLocalOffset(e.name());
            if (localOffset != null) {
                code.addInstruction(new StoreLocal(localOffset));
            } else {
                code.addInstruction(new SetGlobalObjectOrInt(e.name()));
            }
        } else if (target instanceof ChuckAST.BinaryExp e) {
            if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                emitChuckTarget(e.lhs(), code);
                emitChuckTarget(e.rhs(), code);
            } else {
                emitExpression(target, code);
            }
        }
    }

    private void emitSwapTarget(ChuckAST.Exp lhs, ChuckAST.Exp rhs, ChuckCode code) {
        if (lhs instanceof ChuckAST.IdExp l && rhs instanceof ChuckAST.IdExp r) {
            Integer lo = getLocalOffset(l.name()), ro = getLocalOffset(r.name());
            if (lo != null && ro != null) {
                code.addInstruction(new SwapLocal(lo, ro));
            } else if (lo == null && ro == null) {
                code.addInstruction(new org.chuck.core.ChuckSwap(l.name(), r.name(), false));
            } else {
                emitExpression(lhs, code);
                emitExpression(rhs, code);
                code.addInstruction(new StackSwap());
                code.addInstruction(new StoreLocalOrGlobal(r.name()));
                code.addInstruction(new Pop());
                code.addInstruction(new StoreLocalOrGlobal(l.name()));
            }
        } else {
            emitExpression(lhs, code);
            emitExpression(rhs, code);
            code.addInstruction(new StackSwap());
            if (rhs instanceof ChuckAST.IdExp rid) code.addInstruction(new StoreLocalOrGlobal(rid.name()));
            code.addInstruction(new Pop());
            if (lhs instanceof ChuckAST.IdExp lid) code.addInstruction(new StoreLocalOrGlobal(lid.name()));
        }
    }

    private Integer getLocalOffset(String name) {
        if (localScopes.isEmpty()) return null;
        return localScopes.peek().get(name);
    }

    // --- Instructions ---

    static class StackSwap implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            Object r = s.reg.pop(); Object l = s.reg.pop();
            if (r instanceof ChuckObject) s.reg.pushObject(r); else if (r instanceof Double) s.reg.push((Double)r); else s.reg.push((Long)r);
            if (l instanceof ChuckObject) s.reg.pushObject(l); else if (l instanceof Double) s.reg.push((Double)l); else s.reg.push((Long)l);
        }
    }

    static class StoreLocalOrGlobal implements ChuckInstr {
        String name; StoreLocalOrGlobal(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
        }
    }

    static class SwapLocal implements ChuckInstr {
        int o1, o2; SwapLocal(int a, int b) { o1 = a; o2 = b; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer();
            long d1 = s.mem.getData(fp + o1); Object r1 = s.mem.getRef(fp + o1);
            s.mem.setData(fp + o1, s.mem.getData(fp + o2)); s.mem.setRef(fp + o1, s.mem.getRef(fp + o2));
            s.mem.setData(fp + o2, d1); s.mem.setRef(fp + o2, r1);
            if (r1 != null) s.reg.pushObject(r1); else s.reg.push(d1);
        }
    }

    static class LogicalNot implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            double v = s.reg.popAsDouble();
            s.reg.push(v == 0.0 ? 1 : 0);
        }
    }

    static class NegateAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.isDouble(0)) s.reg.push(-s.reg.popAsDouble());
            else s.reg.push(-s.reg.popLong());
        }
    }

    static class TimesAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l * r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong(); s.reg.push(l * r);
            }
        }
    }

    static class DivideAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l / r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong(); if (r != 0) s.reg.push(l / r); else s.reg.push(0L);
            }
        }
    }

    static class ModuloAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l % r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong(); if (r != 0) s.reg.push(l % r); else s.reg.push(0L);
            }
        }
    }

    static class NotEqualsAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l == r) s.reg.push(0);
                else if (l == null || r == null) s.reg.push(1);
                else s.reg.push(!l.toString().equals(r.toString()) ? 1 : 0);
            }
            else { double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l != r ? 1 : 0); }
        }
    }

    static class LogicalAnd implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
            s.reg.push((l != 0.0 && r != 0.0) ? 1 : 0);
        }
    }
    static class LogicalOr implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
            s.reg.push((l != 0.0 || r != 0.0) ? 1 : 0);
        }
    }

    static class ConnectToDac implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            connectAny(src, vm.getDacChannel(0), vm);
            if (vm.getNumChannels() > 1) connectAny(src, vm.getDacChannel(1), vm);
        }
    }

    static class ConnectToBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            connectAny(src, vm.blackhole, vm);
        }
    }

    static class ConnectToAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            connectAny(src, vm.adc, vm);
        }
    }

    static class Jump implements ChuckInstr {
        int target; Jump(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.setPc(target - 1); }
    }
    static class JumpIfFalseAndPushFalse implements ChuckInstr {
        int target; JumpIfFalseAndPushFalse(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.popAsDouble() == 0.0) {
                s.reg.push(0);
                s.setPc(target - 1);
            }
        }
    }
    static class JumpIfTrueAndPushTrue implements ChuckInstr {
        int target; JumpIfTrueAndPushTrue(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.popAsDouble() != 0.0) {
                s.reg.push(1);
                s.setPc(target - 1);
            }
        }
    }
    static class JumpIfFalse implements ChuckInstr {
        int target; JumpIfFalse(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.popAsDouble() == 0.0) s.setPc(target - 1);
        }
    }
    static class JumpIfTrue implements ChuckInstr {
        int target; JumpIfTrue(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.popAsDouble() != 0.0) s.setPc(target - 1);
        }
    }
    static class PushInt implements ChuckInstr {
        long val; PushInt(long v) { val = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { 
            s.reg.push(val); 
        }
    }
    static class PushFloat implements ChuckInstr {
        double val; PushFloat(double v) { val = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(val); }
    }
    static class PushString implements ChuckInstr {
        String val; PushString(String v) { val = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(new ChuckString(val)); }
    }
    static class PushNow implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(vm.getCurrentTime()); }
    }
    static class PushDac implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.getDacChannel(0)); }
    }
    static class PushBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.blackhole); }
    }
    static class PushAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(vm.adc); }
    }
    static class PushMe implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(s); }
    }
    static class PushMachine implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(new MachineObject()); }
    }
    static class PushCherr implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject("stderr"); }
    }
    static class PushChout implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject("stdout"); }
    }
    static class Pop implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { if (s.reg.getSp() > 0) s.reg.popLong(); }
    }
    static class Dup implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.isObject(0)) s.reg.pushObject(s.reg.peekObject(0));
            else if (s.reg.isDouble(0)) s.reg.push(Double.longBitsToDouble(s.reg.peekLong(0)));
            else s.reg.push(s.reg.peekLong(0));
        }
    }
    static class PeekStack implements ChuckInstr {
        int depth; PeekStack(int d) { depth = d; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(depth)) s.reg.pushObject(s.reg.peekObject(depth));
            else if (s.reg.isDouble(depth)) s.reg.push(s.reg.peekAsDouble(depth));
            else s.reg.push(s.reg.peekLong(depth));
        }
    }
    static class LoadLocal implements ChuckInstr {
        int offset; LoadLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = s.getFramePointer() + offset;
            if (s.mem.isObjectAt(idx)) s.reg.pushObject(s.mem.getRef(idx));
            else if (s.mem.isDoubleAt(idx)) s.reg.push(Double.longBitsToDouble(s.mem.getData(idx)));
            else s.reg.push(s.mem.getData(idx));
        }
    }
    static class StoreLocal implements ChuckInstr {
        int offset; StoreLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            int fp = s.getFramePointer(); int idx = fp + offset;
            
            // Polymorphic: if top of stack is FileIO, read from it instead of popping it
            if (s.reg.peekObject(0) instanceof FileIO fio) {
                if (s.mem.isObjectAt(idx)) {
                    s.mem.setRef(idx, new ChuckString(fio.readString()));
                } else if (s.mem.isDoubleAt(idx)) {
                    double val = fio.readFloat(); s.mem.setData(idx, val);
                } else {
                    long val = fio.readInt(); s.mem.setData(idx, val);
                }
                return; // Leave fio on stack for chaining
            }

            if (s.reg.isObject(0)) {
                Object obj = s.reg.popObject();
                Object existing = s.mem.isObjectAt(idx) ? s.mem.getRef(idx) : null;
                boolean isAssign = !(obj instanceof ChuckUGen || obj instanceof ChuckArray);
                if (!isAssign && existing != null) connectAny(obj, existing, vm);
                else { s.mem.setRef(idx, (ChuckObject)obj); }
                s.reg.pushObject(existing != null && !isAssign ? existing : obj);
            } else if (s.reg.isDouble(0)) {
                double val = s.reg.popAsDouble(); s.mem.setData(idx, Double.doubleToRawLongBits(val)); s.reg.push(val);
            } else {
                long val = s.reg.popLong(); s.mem.setData(idx, val); s.reg.push(val);
            }
        }
    }
    static class GetGlobalObjectOrInt implements ChuckInstr {
        String name; GetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (vm.isGlobalObject(name)) s.reg.pushObject(vm.getGlobalObject(name));
            else if (vm.isGlobalDouble(name)) s.reg.push(Double.longBitsToDouble(vm.getGlobalInt(name)));
            else s.reg.push(vm.getGlobalInt(name));
        }
    }
    static class SetGlobalObjectOnly implements ChuckInstr {
        String name; SetGlobalObjectOnly(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;
            if (s.reg.peekObject(0) instanceof FileIO fio) {
                vm.setGlobalObject(name, new ChuckString(fio.readString()));
                return;
            }
            Object obj = s.reg.popObject();
            vm.setGlobalObject(name, obj);
            s.reg.pushObject(obj);
        }
    }
    static class SetGlobalObjectOrInt implements ChuckInstr {
        String name; SetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return;

            // Polymorphic: if top of stack is FileIO, read from it
            if (s.reg.peekObject(0) instanceof FileIO fio) {
                if (vm.isGlobalObject(name)) {
                    vm.setGlobalObject(name, new ChuckString(fio.readString()));
                } else if (vm.isGlobalDouble(name)) {
                    double val = fio.readFloat(); vm.setGlobalFloat(name, val);
                } else {
                    long val = fio.readInt(); vm.setGlobalInt(name, val);
                }
                return; // Leave fio on stack for chaining
            }

            if (s.reg.isObject(0)) {
                Object obj = s.reg.popObject(); Object existing = vm.getGlobalObject(name);
                Object result;
                if (name.equals("dac")) { result = connectAny(obj, vm.getDacChannel(0), vm); if (vm.getNumChannels() > 1) connectAny(obj, vm.getDacChannel(1), vm); }
                else if (name.equals("blackhole")) result = connectAny(obj, vm.blackhole, vm);
                else if (existing instanceof ChuckUGen) {
                    result = connectAny(obj, existing, vm);
                } else {
                    vm.setGlobalObject(name, obj);
                    result = obj;
                }
                s.reg.pushObject(result);
            } else if (s.reg.isDouble(0)) {
                double val = s.reg.popAsDouble(); vm.setGlobalFloat(name, val); s.reg.push(val);
            } else {
                long val = s.reg.popLong(); vm.setGlobalInt(name, val); s.reg.push(val);
            }
        }
    }
    static class AddAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(); Object l = s.reg.pop();
                s.reg.pushObject(new ChuckString(String.valueOf(l) + String.valueOf(r)));
            } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); 
                s.reg.push(l + r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong(); 
                s.reg.push(l + r);
            }
        }
    }
    static class MinusAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l - r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong(); s.reg.push(l - r);
            }
        }
    }
    static class LessThanAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l < r ? 1 : 0);
        }
    }
    static class GreaterThanAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l > r ? 1 : 0);
        }
    }
    static class EqualsAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l == r) s.reg.push(1);
                else if (l == null || r == null) s.reg.push(0);
                else s.reg.push(l.toString().equals(r.toString()) ? 1 : 0);
            }
            else { double r = s.reg.popAsDouble(), l = s.reg.popAsDouble(); s.reg.push(l == r ? 1 : 0); }
        }
    }
    static class Yield implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { vm.advanceTime(0); }
    }
    static class AdvanceTime implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { 
            long samples = s.reg.popLong();
            s.yield(samples); 
            s.reg.push(vm.getCurrentTime());
        }
    }
    static class ChuckUnchuck implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object dest = s.reg.popObject(), src = s.reg.popObject();
            if (src instanceof ChuckUGen su && dest instanceof ChuckUGen du) su.unchuck(du);
            s.reg.pushObject(src);
        }
    }
    static class WriteIO implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) return;
            Object dest = s.reg.popObject();
            Object val = s.reg.pop();
            
            if (dest instanceof FileIO fio) {
                if (val instanceof Double d) fio.write(d.doubleValue());
                else if (val instanceof Long l) fio.write(l.longValue());
                else fio.write(String.valueOf(val));
            } else if (dest instanceof ChuckIO cio) {
                if (val instanceof Double d) cio.write(d.doubleValue());
                else if (val instanceof Long l) cio.write(l.longValue());
                else cio.write(String.valueOf(val));
            } else {
                String out;
                if (val instanceof Double d) {
                    out = java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
                    if (out.endsWith(".0")) out = out.substring(0, out.length() - 2);
                } else if (val instanceof ChuckString cs) {
                    out = cs.toString();
                } else {
                    out = String.valueOf(val);
                }
                vm.print(out);
            }
            s.reg.pushObject(dest);
        }
    }
    static class ArrayZero implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = s.reg.popObject();
            if (obj instanceof ChuckArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.isObjectAt(i)) {
                        Object elem = arr.getObject(i);
                        if (elem instanceof ChuckArray inner) {
                            arr.setObject(i, elem); // keep reference
                        } else {
                            arr.setObject(i, null);
                        }
                    } else if (arr.isDoubleAt(i)) arr.setFloat(i, 0.0);
                    else arr.setInt(i, 0);
                }
                s.reg.pushObject(arr);
            } else s.reg.push(0L);
        }
    }
    static class GetArrayInt implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long idx = s.reg.popLong(); Object obj = s.reg.popObject();
            if (obj instanceof ChuckArray arr) {
                if (arr.isObjectAt((int) idx)) s.reg.pushObject(arr.getObject((int) idx));
                else if (arr.isDoubleAt((int) idx)) s.reg.push(arr.getFloat((int) idx));
                else s.reg.push(arr.getInt((int) idx));
            } else s.reg.push(0L);
        }
    }
    static class SetArrayInt implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long idx = s.reg.popLong();
            ChuckArray arr = (ChuckArray) s.reg.popObject();

            boolean isObj = s.reg.isObject(0), isDbl = s.reg.isDouble(0);
            Object valObj = isObj ? s.reg.popObject() : null;
            double valDbl = isDbl ? s.reg.popAsDouble() : 0;
            long valLong = (!isObj && !isDbl) ? s.reg.popLong() : 0;

            if (isObj) arr.setObject((int) idx, valObj);
            else if (isDbl) arr.setFloat((int) idx, valDbl);
            else arr.setInt((int) idx, valLong);

            if (isObj) s.reg.pushObject(valObj);
            else if (isDbl) s.reg.push(valDbl);
            else s.reg.push(valLong);
        }
    }
    static class NewArray implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(new ChuckArray(ChuckType.ARRAY, (int) s.reg.popLong()));
        }
    }
    static class NewArrayFromStack implements ChuckInstr {
        int size; NewArrayFromStack(int sz) { size = sz; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
            for (int i = size - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) { Object obj = s.reg.popObject(); arr.setObject(i, obj); }
                else if (s.reg.isDouble(0)) { double d = s.reg.popAsDouble(); arr.setFloat(i, d); }
                else { long l = s.reg.popLong(); arr.setInt(i, l); }
            }
            s.reg.pushObject(arr);
        }
    }
    static class CreateDuration implements ChuckInstr {
        String unit; CreateDuration(String u) { unit = u; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double v = s.reg.popAsDouble(); long smp = 0;
            if (unit.equals("ms")) smp = Math.round(v * vm.getSampleRate() / 1000.0);
            else if (unit.equals("second")) smp = Math.round(v * vm.getSampleRate());
            else if (unit.equals("samp")) smp = Math.round(v);
            s.reg.push(smp);
        }
    }
    static class StdMtof implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(Std.mtof(s.reg.popAsDouble())); }
    }
    static class StdFtom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(Std.ftom(s.reg.popAsDouble())); }
    }
    static class MathFunc implements ChuckInstr {
        String fn; MathFunc(String f) { fn = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (fn.equals("equal")) {
                double b = s.reg.popAsDouble(), a = s.reg.popAsDouble();
                s.reg.push(Math.abs(a - b) < 1e-6 ? 1 : 0);
                return;
            }
            if (fn.equals("euclidean")) {
                Object b = s.reg.pop(), a = s.reg.pop();
                ChuckArray arrA = (a instanceof ChuckArray) ? (ChuckArray) a : null;
                ChuckArray arrB = (b instanceof ChuckArray) ? (ChuckArray) b : null;
                if (arrA != null && arrB != null) {
                    double sum = 0;
                    int size = Math.min(arrA.size(), arrB.size());
                    for (int i = 0; i < size; i++) {
                        double da = arrA.getFloat(i);
                        double db = arrB.getFloat(i);
                        sum += (da - db) * (da - db);
                    }
                    s.reg.push(Math.sqrt(sum));
                } else s.reg.push(0.0);
                return;
            }
            double v = s.reg.popAsDouble();
            s.reg.push(switch (fn) {
                case "sin" -> Math.sin(v); case "cos" -> Math.cos(v);
                case "sqrt" -> Math.sqrt(v); case "abs" -> Math.abs(v);
                case "floor" -> Math.floor(v); case "ceil" -> Math.ceil(v);
                case "dbtolin", "dbtopow" -> Math.pow(10.0, v / 20.0);
                case "lintodb", "powtodb" -> (v <= 0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(v));
                case "dbtorms" -> Math.sqrt(Math.pow(10.0, v / 10.0));
                case "rmstodb" -> (v <= 0 ? Double.NEGATIVE_INFINITY : 10.0 * Math.log10(v * v));
                default -> v;
            });
        }
    }
    static class StdClamp implements ChuckInstr {
        boolean isFloat; StdClamp(boolean f) { isFloat = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (isFloat) {
                double hi = s.reg.popAsDouble(), lo = s.reg.popAsDouble(), val = s.reg.popAsDouble();
                s.reg.push(Math.max(lo, Math.min(hi, val)));
            } else {
                long hi = s.reg.popLong(), lo = s.reg.popLong(), val = s.reg.popLong();
                s.reg.push(Math.max(lo, Math.min(hi, val)));
            }
        }
    }
    static class Std2RandF implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double max = s.reg.popAsDouble(), min = s.reg.popAsDouble();
            s.reg.push(min + Math.random() * (max - min));
        }
    }
    static class Std2RandI implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long max = s.reg.popLong(), min = s.reg.popLong();
            s.reg.push(min + (long) (Math.random() * (max - min + 1)));
        }
    }
    static class MathRandom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(Math.random()); }
    }
    static class MathHelp implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { }
    }
    static class Spork implements ChuckInstr {
        ChuckCode t; int a; Spork(ChuckCode target, int argCount) {
            t = target; a = argCount;
            if (t.getNumInstructions() == 0 || !(t.getInstruction(0) instanceof MoveArgs)) {
                t.prependInstruction(new MoveArgs(a));
            }
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckShred ns = new ChuckShred(t);
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                    else if (s.reg.isDouble(0)) args[i] = s.reg.popAsDouble();
                    else args[i] = s.reg.popLong();
                }
            }
            for (Object arg : args) {
                if (arg instanceof ChuckObject co) ns.reg.pushObject(co);
                else if (arg instanceof Double d) ns.reg.push(d);
                else if (arg instanceof Long l) ns.reg.push(l);
                else ns.reg.pushObject(arg);
            }
            s.reg.push(vm.spork(ns));
        }
    }
    static class SporkMethod implements ChuckInstr {
        String mName; int a; SporkMethod(String m, int argCount) { mName = m; a = argCount; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                else if (s.reg.isDouble(0)) args[i] = s.reg.popAsDouble();
                else args[i] = s.reg.popLong();
            }
            Object obj = s.reg.popObject(); if (!(obj instanceof UserObject uo)) { s.reg.push(0L); return; }

            ChuckCode target = uo.methods.get(mName);
            boolean isStatic = false;
            if (target == null) {
                UserClassDescriptor d = vm.getUserClass(uo.className);
                if (d != null) {
                    target = d.staticMethods().get(mName + ":" + a);
                    isStatic = (target != null);
                }
            }
            if (target == null) { s.reg.push(0L); return; }
            if (target.getNumInstructions() == 0 || !(target.getInstruction(0) instanceof MoveArgs)) {
                target.prependInstruction(new MoveArgs(a));
            }
            ChuckShred ns = new ChuckShred(target);
            for (Object arg : args) {
                if (arg instanceof ChuckObject co) ns.reg.pushObject(co);
                else if (arg instanceof Double d) ns.reg.push(d);
                else if (arg instanceof Long l) ns.reg.push(l);
                else ns.reg.pushObject(arg);
            }
            if (!isStatic) ns.thisStack.push(uo);
            s.reg.push(vm.spork(ns));
        }
    }
    static class ReturnMethod implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer(); if (fp < 4) { s.abort(); return; }
            int savedSp = (int) s.mem.getData(fp - 1), savedFp = (int) s.mem.getData(fp - 2), savedPc = (int) s.mem.getData(fp - 3);
            ChuckCode savedCode = (ChuckCode) s.mem.getRef(fp - 4);
            long retP = 0; Object retO = null; boolean retD = false;
            if (s.reg.getSp() > savedSp && s.reg.getSp() > 0) {
                retD = s.reg.isDouble(0); retP = s.reg.peekLong(0); retO = s.reg.peekObject(0);
            }
            s.reg.setSp(savedSp); s.setPc(savedPc); s.setCode(savedCode);
            UserObject uo = s.thisStack.pop(); boolean isCtor = uo.className.equals(savedCode.getName());
            s.setFramePointer(savedFp); s.mem.setSp(fp - 4);
            if (retO != null || retP != 0 || retD) {
                if (retO != null) s.reg.pushObject(retO);
                else if (retD) s.reg.push(Double.longBitsToDouble(retP));
                else s.reg.push(retP);
            } else if (isCtor) s.reg.pushObject(uo);
        }
    }
    static class GetFieldByName implements ChuckInstr {
        String n; GetFieldByName(String v) { n = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = s.reg.popObject(); if (obj == null) { s.reg.push(0L); return; }
            if (n.equals("size") && obj instanceof ChuckArray arr) s.reg.push((long) arr.size());
            else if (obj instanceof UserObject uo) {
                ChuckObject fo = uo.getObjectField(n);
                if (fo != null) s.reg.pushObject(fo);
                else s.reg.push(uo.getPrimitiveField(n));
            } else if (obj instanceof ChuckUGen ugen) {
                if (n.equals("last")) s.reg.push((double) ugen.getLastOut());
                else if (n.equals("left") && obj instanceof Pan2 p) s.reg.pushObject(p.left);
                else if (n.equals("right") && obj instanceof Pan2 p) s.reg.pushObject(p.right);
                else s.reg.push(0L);
            } else s.reg.push(0L);
        }
    }
    static class GetUserField implements ChuckInstr {
        String n; GetUserField(String v) { n = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek(); if (uo == null) { s.reg.push(0L); return; }
            ChuckObject obj = uo.getObjectField(n);
            if (obj != null) s.reg.pushObject(obj);
            else s.reg.push(uo.getPrimitiveField(n));
        }
    }
    static class SetUserField implements ChuckInstr {
        String n; SetUserField(String v) { n = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek(); if (uo == null) return;
            if (s.reg.isObject(0)) {
                ChuckObject val = (ChuckObject) s.reg.popObject(); uo.setObjectField(n, val); s.reg.pushObject(val);
            } else {
                long val = s.reg.popLong(); uo.setPrimitiveField(n, val); s.reg.push(val);
            }
        }
    }
    static class InstantiateSetAndPushLocal implements ChuckInstr {
        String t; int o, a; boolean r, ar; Map<String, UserClassDescriptor> rm;
        InstantiateSetAndPushLocal(String type, int off, int arg, boolean ref, boolean arr, Map<String, UserClassDescriptor> m) { t = type; o = off; a = arg; r = ref; ar = arr; rm = m; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a]; for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                else if (s.reg.isDouble(0)) args[i] = s.reg.popAsDouble();
                else args[i] = s.reg.popLong();
            }
            int fp = s.getFramePointer(); if (r) { s.mem.setRef(fp + o, null); s.reg.pushObject(null); return; }
            Object obj;
            if (ar) {
                int sz = ((Number) args[0]).intValue();
                if (sz < 0) {
                    if (t.equals("vec3")) obj = new ChuckArray(ChuckType.ARRAY, 3);
                    else if (t.equals("vec4")) obj = new ChuckArray(ChuckType.ARRAY, 4);
                    else if (t.equals("complex") || t.equals("polar")) obj = new ChuckArray(ChuckType.ARRAY, 2);
                    else obj = null;
                } else {
                    ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
                    for (int i = 0; i < sz; i++) {
                        ChuckObject elem = instantiateType(t, vm.getSampleRate(), vm, rm);
                        if (elem != null) { arr.setObject(i, elem); if (elem instanceof ChuckUGen u) s.registerUGen(u); }
                    }
                    obj = arr;
                }
            } else obj = instantiateType(t, vm.getSampleRate(), vm, rm);

            if (obj instanceof ChuckObject co) {
                s.mem.setRef(fp + o, co); if (co instanceof ChuckUGen u) s.registerUGen(u);
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
        }
    }
    static class InstantiateSetAndPushGlobal implements ChuckInstr {
        String t, n; int a; boolean r, ar; Map<String, UserClassDescriptor> rm;
        InstantiateSetAndPushGlobal(String type, String name, int arg, boolean ref, boolean arr, Map<String, UserClassDescriptor> m) { t = type; n = name; a = arg; r = ref; ar = arr; rm = m; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a]; for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                else if (s.reg.isDouble(0)) args[i] = s.reg.popAsDouble();
                else args[i] = s.reg.popLong();
            }
            if (r) { vm.setGlobalObject(n, null); s.reg.pushObject(null); return; }
            Object obj;
            if (ar) {
                int sz = ((Number) args[0]).intValue();
                if (sz < 0) {
                    if (t.equals("vec3")) obj = new ChuckArray(ChuckType.ARRAY, 3);
                    else if (t.equals("vec4")) obj = new ChuckArray(ChuckType.ARRAY, 4);
                    else if (t.equals("complex") || t.equals("polar")) obj = new ChuckArray(ChuckType.ARRAY, 2);
                    else obj = null;
                } else {
                    ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
                    for (int i = 0; i < sz; i++) {
                        ChuckObject elem = instantiateType(t, vm.getSampleRate(), vm, rm);
                        if (elem != null) { arr.setObject(i, elem); if (elem instanceof ChuckUGen u) s.registerUGen(u); }
                    }
                    obj = arr;
                }
            } else obj = instantiateType(t, vm.getSampleRate(), vm, rm);

            if (obj instanceof ChuckObject co) {
                vm.setGlobalObject(n, co); if (co instanceof ChuckUGen u) s.registerUGen(u);
                s.reg.pushObject(co);
            } else {
                if (t.equals("float")) {
                    vm.setGlobalFloat(n, 0.0);
                    s.reg.push(0.0);
                } else {
                    vm.setGlobalInt(n, 0L);
                    s.reg.push(0L);
                }
            }
        }
    }
    private static Object connectAny(Object src, Object dest, ChuckVM vm) {
        if (src instanceof ChuckUGen su && dest instanceof ChuckUGen du) {
            if (su.getNumOutputs() > 1 && du.getNumInputs() == 1) {
                for (int i = 0; i < su.getNumOutputs(); i++) su.getOutputChannel(i).chuckTo(du);
            } else if (su.getNumOutputs() > 1 && du.getNumInputs() > 1) {
                int len = Math.max(su.getNumOutputs(), du.getNumInputs());
                for (int i = 0; i < len; i++) su.getOutputChannel(i % su.getNumOutputs()).chuckTo(du.getInputChannel(i % du.getNumInputs()));
            } else su.chuckTo(du);
        } else if (src instanceof ChuckArray sa && dest instanceof ChuckUGen du) {
            int len = Math.max(sa.size(), du.getNumInputs());
            if (sa.size() > 0) {
                for (int i = 0; i < len; i++) {
                    Object e = sa.getObject(i % sa.size());
                    if (e instanceof ChuckUGen su2) su2.chuckTo(du.getInputChannel(i % du.getNumInputs()));
                }
            }
        } else if (src instanceof ChuckUGen su && dest instanceof ChuckArray da) {
            int len = Math.max(su.getNumOutputs(), da.size());
            if (da.size() > 0) {
                for (int i = 0; i < len; i++) {
                    Object e = da.getObject(i % da.size());
                    if (e instanceof ChuckUGen du2) su.getOutputChannel(i % su.getNumOutputs()).chuckTo(du2);
                }
            }
        } else if (src instanceof ChuckArray sa && dest instanceof ChuckArray da) {
            if (sa.size() > 0 && da.size() > 0) {
                for (int i = 0; i < Math.min(sa.size(), da.size()); i++) {
                    if (sa.isObjectAt(i)) da.setObject(i, sa.getObject(i));
                    else if (sa.isDoubleAt(i)) da.setFloat(i, sa.getFloat(i));
                    else da.setInt(i, sa.getInt(i));
                }
            }
        }
        return dest;
    }
    static ChuckObject instantiateType(String t, float sr, ChuckVM vm, Map<String, UserClassDescriptor> rm) {
        if (t == null) return null; UserClassDescriptor d = rm.get(t); if (d != null) return new UserObject(t, d.fields(), d.methods());
        return switch (t) {
            case "SinOsc" -> new SinOsc(sr); case "Gain" -> new Gain();
            case "Pan2" -> new Pan2(); case "Noise" -> new Noise();
            case "ADSR", "Adsr" -> new Adsr(sr); case "string" -> new ChuckString("");
            case "vec3" -> new ChuckArray(ChuckType.ARRAY, 3);
            case "vec4" -> new ChuckArray(ChuckType.ARRAY, 4);
            case "complex", "polar" -> new ChuckArray(ChuckType.ARRAY, 2);
            case "Echo" -> new Echo((int) (sr * 2)); case "Delay" -> new Delay((int) (sr * 2), sr);
            case "DelayL" -> new DelayL((int) (sr * 2), sr); case "JCRev", "NRev" -> new JCRev(sr);
            case "Chorus" -> new Chorus(sr); case "Lpf", "LPF" -> new Lpf(sr);
            case "SndBuf" -> new SndBuf(); 
            case "Dyno" -> new Dyno();
            case "LiSa" -> new LiSa(sr);
            case "FileIO" -> new FileIO();
            case "StringTokenizer" -> new StringTokenizer();
            default -> null;
        };
    }
    public static class MoveArgs implements ChuckInstr {
        public int a; public MoveArgs(int v) { a = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.mem.getSp() == 0) {
                s.mem.pushObject(null); s.mem.push(0); s.mem.push(0); s.mem.push(0);
                s.setFramePointer(4);
            }
            if (s.getFramePointer() > 4) return;
            if (a == 0) return;
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                    else if (s.reg.isDouble(0)) args[i] = s.reg.popAsDouble();
                    else args[i] = s.reg.popLong();
                }
            }
            for (Object arg : args) {
                if (arg instanceof ChuckObject co) s.mem.pushObject(co);
                else if (arg instanceof Double d) s.mem.push(d);
                else if (arg instanceof Long l) s.mem.push(l);
                else if (arg != null) s.mem.pushObject(arg);
            }
        }
    }
    static class GetBuiltinStatic implements ChuckInstr {
        String className, fieldName;
        GetBuiltinStatic(String c, String f) { className = c; fieldName = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            try {
                Class<?> clazz = Class.forName(className);
                java.lang.reflect.Field f = clazz.getField(fieldName);
                Object val = f.get(null);
                if (val instanceof Number) s.reg.push(((Number) val).longValue());
                else s.reg.pushObject(val);
            } catch (Exception e) { s.reg.push(0L); }
        }
    }
    static class GetStatic implements ChuckInstr {
        String cName, fName; GetStatic(String c, String f) { cName = c; fName = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserClassDescriptor d = vm.getUserClass(cName);
            if (d.staticObjects().containsKey(fName)) s.reg.pushObject(d.staticObjects().get(fName));
            else if (d.staticIsDouble().getOrDefault(fName, false)) s.reg.push(Double.longBitsToDouble(d.staticInts().getOrDefault(fName, 0L)));
            else if (d.staticInts().containsKey(fName)) s.reg.push(d.staticInts().get(fName));
            else s.reg.push(0L);
        }
    }
    static class SetStatic implements ChuckInstr {
        String cName, fName; SetStatic(String c, String f) { cName = c; fName = f; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserClassDescriptor d = vm.getUserClass(cName);
            if (s.reg.getSp() > 0 && s.reg.peekObject(0) instanceof FileIO fio) {
                if (d.staticIsDouble().getOrDefault(fName, false)) {
                    double val = fio.readFloat(); d.staticInts().put(fName, Double.doubleToRawLongBits(val));
                } else if (d.staticInts().containsKey(fName)) {
                    long val = fio.readInt(); d.staticInts().put(fName, val);
                } else {
                    d.staticObjects().put(fName, new ChuckString(fio.readString()));
                }
                return;
            }
            if (s.reg.isObject(0)) { Object o = s.reg.popObject(); d.staticObjects().put(fName, o); s.reg.pushObject(o); }
            else if (s.reg.isDouble(0)) { double val = s.reg.popAsDouble(); d.staticInts().put(fName, Double.doubleToRawLongBits(val)); s.reg.push(val); }
            else { long val = s.reg.popLong(); d.staticInts().put(fName, val); s.reg.push(val); }
        }
    }
    static class RegisterClass implements ChuckInstr {
        String name; UserClassDescriptor d; RegisterClass(String n, UserClassDescriptor desc) { name = n; d = desc; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { vm.registerUserClass(name, d); }
    }
    static class CallMethod implements ChuckInstr {
        String mName; int a; CallMethod(String m, int v) { mName = m; a = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) args[i] = s.reg.popObject();
                else if (s.reg.isDouble(0)) args[i] = s.reg.popAsDouble();
                else args[i] = s.reg.popLong();
            }
            Object obj = s.reg.popObject(); if (obj == null) { s.reg.push(0L); return; }
            if (obj instanceof UserObject uo) {
                ChuckCode target = uo.methods.get(mName);
                if (target == null) {
                    UserClassDescriptor d = vm.getUserClass(uo.className);
                    if (d != null) target = d.staticMethods().get(mName + ":" + a);
                }
                if (target != null) {
                    s.mem.pushObject(s.getCode()); s.mem.push(s.getPc()); s.mem.push(s.getFramePointer()); s.mem.push(s.reg.getSp());
                    s.setFramePointer(s.mem.getSp());
                    for (Object arg : args) {
                        if (arg instanceof ChuckObject co) s.mem.pushObject(co);
                        else if (arg instanceof Double d) s.mem.push(d);
                        else if (arg instanceof Long l) s.mem.push(l);
                        else s.mem.pushObject(arg);
                    }
                    if (uo.methods.containsKey(mName)) s.thisStack.push(uo);
                    s.setCode(target); s.setPc(-1); return;
                }
            }
            try {
                // Two-pass method resolution: prefer exact type match over widening
                java.lang.reflect.Method bestMethod = null;
                Object[] bestArgs = null;
                int bestScore = -1;
                for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                    if (!m.getName().equals(mName) || m.getParameterCount() != a) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    Object[] coe = new Object[a];
                    int score = 0;
                    boolean valid = true;
                    for (int i = 0; i < a; i++) {
                        Object val = args[i];
                        if (val instanceof Long l) {
                            if (pts[i] == long.class || pts[i] == Long.class) { coe[i] = l; score += 3; }
                            else if (pts[i] == int.class || pts[i] == Integer.class) { coe[i] = l.intValue(); score += 2; }
                            else if (pts[i] == double.class || pts[i] == Double.class) { coe[i] = l.doubleValue(); score += 1; }
                            else if (pts[i] == float.class || pts[i] == Float.class) { coe[i] = l.floatValue(); score += 1; }
                            else if (pts[i] == String.class) { coe[i] = String.valueOf(l); score += 0; }
                            else { valid = false; break; }
                        } else if (val instanceof Double d) {
                            if (pts[i] == double.class || pts[i] == Double.class) { coe[i] = d; score += 3; }
                            else if (pts[i] == float.class || pts[i] == Float.class) { coe[i] = d.floatValue(); score += 2; }
                            else if (pts[i] == String.class) { coe[i] = String.valueOf(d); score += 0; }
                            else { valid = false; break; }
                        } else if (val instanceof ChuckString cs) {
                            if (pts[i] == String.class) { coe[i] = cs.toString(); score += 3; }
                            else if (pts[i].isAssignableFrom(ChuckString.class)) { coe[i] = cs; score += 2; }
                            else { valid = false; break; }
                        } else {
                            if (val != null && pts[i].isInstance(val)) { coe[i] = val; score += 2; }
                            else { coe[i] = val; score += 0; }
                        }
                    }
                    if (valid && score > bestScore) { bestScore = score; bestMethod = m; bestArgs = coe; }
                }
                if (bestMethod != null) {
                    Object res = bestMethod.invoke(obj, bestArgs);
                    if (res != null && bestMethod.getReturnType() != void.class) {
                        Class<?> rt = bestMethod.getReturnType();
                        if (rt == int.class || rt == long.class) s.reg.push(((Number) res).longValue());
                        else if (rt == char.class || rt == Character.class) s.reg.push((long) (Character) res);
                        else if (rt == boolean.class) s.reg.push((Boolean) res ? 1L : 0L);
                        else if (rt == float.class || rt == double.class) s.reg.push(((Number) res).doubleValue());
                        else if (res instanceof String str) s.reg.pushObject(new ChuckString(str));
                        else s.reg.pushObject(res);
                    } else s.reg.pushObject(obj);
                    return;
                }
            } catch (Exception ignored) { }
            s.reg.pushObject(obj);
        }
    }
    static class ShredObject extends ChuckObject { ShredObject() { super(new ChuckType("Shred", ChuckType.OBJECT, 0, 0)); } public String dir() { return "examples/data/"; } }
    static class MachineObject extends ChuckObject { MachineObject() { super(new ChuckType("Machine", ChuckType.OBJECT, 0, 0)); } public int add(String p, ChuckVM v) { return v.add(p); } public void remove(int i, ChuckVM v) { v.removeShred(i); } public void clear(ChuckVM v) { v.clear(); } public int refcount(Object o) { return 1; } }
}
