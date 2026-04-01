package org.chuck.compiler;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.Gen10;
import org.chuck.audio.Gen17;
import org.chuck.audio.Gen5;
import org.chuck.audio.Gen7;
import org.chuck.audio.Gen9;
import org.chuck.audio.StereoUGen;
import org.chuck.chugin.ChuginLoader;
import org.chuck.core.AdvanceTime;
import org.chuck.core.CallFunc;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckDuration;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckEventConjunction;
import org.chuck.core.ChuckEventDisjunction;
import org.chuck.core.ChuckIO;
import org.chuck.core.ChuckInstr;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckPrint;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckString;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;
import org.chuck.core.Duplicate;
import org.chuck.core.EqualAny;
import org.chuck.core.FileIO;
import org.chuck.core.GetLastOut;
import org.chuck.core.Reflect;
import org.chuck.core.RegEx;
import org.chuck.core.ReturnFunc;
import org.chuck.core.SerialIO;
import org.chuck.core.SetMemberIntByName;
import org.chuck.core.Std;
import org.chuck.core.StringTokenizer;
import org.chuck.core.UGenRegistry;
import org.chuck.core.UserClassDescriptor;
import org.chuck.core.UserObject;

/**
 * Emits executable VM instructions from a parsed AST.
 */
public class ChuckEmitter {

    private final Map<String, ChuckCode> functions = new HashMap<>();
    private final java.util.Stack<Map<String, Integer>> localScopes = new java.util.Stack<>();
    /**
     * Tracks variable name → declared type for operator overload dispatch.
     */
    private final Map<String, String> varTypes = new HashMap<>();

    private final Map<String, UserClassDescriptor> userClassRegistry = new HashMap<>();
    /**
     * Tracks global variable name → declared type for compile-time conflict
     * detection.
     */
    private final Map<String, String> globalVarTypes = new HashMap<>();

    /**
     * Tracks operator function return types for expression type inference.
     */
    private final Map<String, String> functionReturnTypes = new HashMap<>();

    private String currentClass = null;
    private java.util.Set<String> currentClassFields = java.util.Collections.emptySet();
    private String currentFile = "";

    private final java.util.Stack<List<Integer>> breakJumps = new java.util.Stack<>();
    private final java.util.Stack<List<Integer>> continueJumps = new java.util.Stack<>();

    // Compile-time function analysis state
    private String currentFuncReturnType = null;
    private boolean currentFuncHasReturn = false;
    private boolean inStaticFuncContext = false;
    private List<ChuckAST.FuncDefStmt> currentClassMethodsList = new ArrayList<>();

    // Track variables declared with empty-array syntax (e.g., SinOsc foos[])
    private final java.util.Set<String> emptyArrayVars = new java.util.HashSet<>();

    // Additional core UGens not in UGenRegistry
    private static final java.util.Set<String> CORE_UGENS = java.util.Set.of(
            "OscIn", "OscOut", "OscMsg", "FileIO", "IO", "Std", "Math", "Machine", "UGen", "UGen_Multi", "UGen_Stereo",
            "UAna", "Shred", "Thread", "ChucK"
    );

    // Core non-UGen data types
    private static final java.util.Set<String> CORE_DATA_TYPES = java.util.Set.of(
            "int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", "complex", "polar", "Object", "Array"
    );

    private boolean isKnownUGenType(String type) {
        return UGenRegistry.isRegistered(type) || CORE_UGENS.contains(type);
    }

    private boolean isKnownType(String type) {
        return isKnownUGenType(type) || CORE_DATA_TYPES.contains(type) || userClassRegistry.containsKey(type);
    }

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

    /**
     * Returns all classes this emitter has registered (including from imports).
     */
    public Map<String, UserClassDescriptor> getUserClassRegistry() {
        return userClassRegistry;
    }

    /**
     * Returns public operator functions (keys starting with __pub_op__).
     */
    public Map<String, ChuckCode> getPublicFunctions() {
        Map<String, ChuckCode> result = new HashMap<>();
        for (var e : functions.entrySet()) {
            if (e.getKey().startsWith("__pub_op__")) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private String getVarType(ChuckAST.Exp exp) {
        return switch (exp) {
            case ChuckAST.IdExp id ->
                varTypes.get(id.name());
            default ->
                null;
        };
    }

    /**
     * Infers the type of an expression, including results of binary operator
     * overloads.
     */
    private String getExprType(ChuckAST.Exp exp) {
        return switch (exp) {
            case ChuckAST.IdExp id -> {
                String type = getVarType(exp);
                if (type != null) {
                    yield type;
                }
                if (userClassRegistry.containsKey(id.name())) {
                    yield id.name();
                }
                // Handle core types as types themselves
                if (CORE_DATA_TYPES.contains(id.name())) {
                    yield id.name();
                }
                if (isKnownUGenType(id.name())) {
                    yield id.name();
                }
                yield null;
            }
            case ChuckAST.IntExp _ ->
                "int";
            case ChuckAST.FloatExp _ ->
                "float";
            case ChuckAST.StringExp _ ->
                "string";
            case ChuckAST.DeclExp e ->
                e.type();
            case ChuckAST.BinaryExp bin -> {
                String lhsType = getExprType(bin.lhs());
                if (lhsType == null) {
                    yield null;
                }

                // Handle built-in vector/complex arithmetic results
                if (lhsType.equals("vec2") || lhsType.equals("vec3") || lhsType.equals("vec4")
                        || lhsType.equals("complex") || lhsType.equals("polar")) {
                    if (bin.op() == ChuckAST.Operator.EQ || bin.op() == ChuckAST.Operator.NEQ
                            || bin.op() == ChuckAST.Operator.LT || bin.op() == ChuckAST.Operator.LE
                            || bin.op() == ChuckAST.Operator.GT || bin.op() == ChuckAST.Operator.GE) {
                        yield "int";
                    }
                    if (bin.op() == ChuckAST.Operator.TIMES) {
                        String rhsType = getExprType(bin.rhs());
                        if (rhsType != null && (rhsType.equals("vec2") || rhsType.equals("vec3") || rhsType.equals("vec4"))) {
                            yield "float"; // Dot product
                        }
                    }
                    yield lhsType; // Element-wise arithmetic returns same type
                }

                String opSymbol = switch (bin.op()) {
                    case PLUS ->
                        "+";
                    case MINUS ->
                        "-";
                    case TIMES ->
                        "*";
                    case DIVIDE ->
                        "/";
                    case PERCENT ->
                        "%";
                    case LT ->
                        "<";
                    case LE ->
                        "<=";
                    case GT ->
                        ">";
                    case GE ->
                        ">=";
                    case EQ ->
                        "==";
                    case NEQ ->
                        "!=";
                    default ->
                        null;
                };
                if (opSymbol != null && userClassRegistry.containsKey(lhsType)) {
                    String retType = functionReturnTypes.get("__pub_op__" + opSymbol + ":2");
                    if (retType == null) {
                        retType = functionReturnTypes.get("__op__" + opSymbol + ":2");
                    }
                    if (retType == null) {
                        UserClassDescriptor desc = userClassRegistry.get(lhsType);
                        if (desc != null) {
                            if (desc.methods().containsKey("__pub_op__" + opSymbol + ":1")
                                    || desc.methods().containsKey("__op__" + opSymbol + ":1")) {
                                yield (bin.op() == ChuckAST.Operator.EQ || bin.op() == ChuckAST.Operator.NEQ
                                || bin.op() == ChuckAST.Operator.LT || bin.op() == ChuckAST.Operator.LE
                                || bin.op() == ChuckAST.Operator.GT || bin.op() == ChuckAST.Operator.GE) ? "int" : lhsType;
                            }
                        }
                    }
                    if (retType != null) {
                        yield retType;
                    }
                }
                if (bin.op() == ChuckAST.Operator.SHIFT_LEFT) {
                    yield lhsType;
                }
                yield null;
            }
            default ->
                null;
        };
    }

    private void flattenStmts(List<ChuckAST.Stmt> input, List<ChuckAST.Stmt> output) {
        for (ChuckAST.Stmt s : input) {
            switch (s) {
                case ChuckAST.BlockStmt b ->
                    flattenStmts(b.statements(), output);
                default ->
                    output.add(s);
            }
        }
    }

    private boolean isSubclassOfUGen(String className) {
        if (className == null) {
            return false;
        }
        if (isKnownUGenType(className)) {
            // Built-in non-UGens like vec3 shouldn't return true here
            return !CORE_DATA_TYPES.contains(className);
        }
        UserClassDescriptor d = userClassRegistry.get(className);
        if (d == null) {
            return false;
        }
        return isSubclassOfUGen(d.parentName());
    }

    private ChuckCode resolveStaticMethod(String className, String methodKey) {
        if (className == null) {
            return null;
        }
        UserClassDescriptor d = userClassRegistry.get(className);
        if (d == null) {
            return null;
        }
        ChuckCode code = d.staticMethods().get(methodKey);
        if (code != null) {
            return code;
        }
        return resolveStaticMethod(d.parentName(), methodKey);
    }

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName) {
        localScopes.clear();
        // Push a top-level local scope so script variables are shred-local
        localScopes.push(new HashMap<>());

        varTypes.clear();
        globalVarTypes.clear();
        currentFile = programName;
        // Empty/comment-only programs are errors in ChucK
        boolean hasContent = statements.stream().anyMatch(s
                -> !(s instanceof ChuckAST.BlockStmt bs && bs.statements().isEmpty()));
        if (!hasContent) {
            throw new RuntimeException(programName + ":1:1: syntax error\n(empty file)");
        }
        // Pass 0: Register all class names so they are known as types
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.ClassDefStmt s) {
                userClassRegistry.put(s.name(), new UserClassDescriptor(s.name(), s.parentName(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>()));
            }
        }

        // Pass 1: Collect all global function signatures
        // Pass 2: Populate function and class bodies
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.FuncDefStmt || stmt instanceof ChuckAST.ClassDefStmt) {
                emitStatement(stmt, null);
            }
        }

        // Pass 3: Emit top-level statements and register classes
        ChuckCode code = new ChuckCode(programName);
        code.addInstruction(new MoveArgs(0));

        // First, register classes so they are available for static access in the script
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.ClassDefStmt s) {
                UserClassDescriptor desc = userClassRegistry.get(s.name());
                if (desc != null) {
                    code.addInstruction(new RegisterClass(s.name(), desc));
                }
            }
        }

        for (ChuckAST.Stmt stmt : statements) {
            if (!(stmt instanceof ChuckAST.FuncDefStmt) && !(stmt instanceof ChuckAST.ImportStmt) && !(stmt instanceof ChuckAST.ClassDefStmt)) {
                emitStatement(stmt, code);
            }
        }
        return code;
    }

    private void emitStatement(ChuckAST.Stmt stmt, ChuckCode code) {
        switch (stmt) {
            case ChuckAST.ExpStmt s -> {
                // Check for standalone undefined variable (e.g. `x;`) — error-nspc-no-crash
                if (s.exp() instanceof ChuckAST.IdExp ide && localScopes.isEmpty() && currentClass == null) {
                    String nm = ide.name();
                    boolean knownName = varTypes.containsKey(nm) || globalVarTypes.containsKey(nm)
                            || userClassRegistry.containsKey(nm) || isKnownUGenType(nm)
                            || currentClassFields.contains(nm)
                            || nm.equals("null") || nm.equals("true") || nm.equals("false")
                            || nm.equals("this") || nm.equals("super") || nm.equals("pi") || nm.equals("e")
                            || nm.equals("now") || nm.equals("dac") || nm.equals("blackhole") || nm.equals("adc")
                            || nm.equals("me") || nm.equals("cherr") || nm.equals("chout") || nm.equals("Machine")
                            || nm.equals("maybe") || nm.equals("second") || nm.equals("ms") || nm.equals("samp")
                            || nm.equals("minute") || nm.equals("hour");
                    if (!knownName) {
                        throw new RuntimeException(currentFile + ":" + ide.line() + ":" + ide.column()
                                + ": error: undefined variable '" + nm + "'");
                    }
                }
                emitExpression(s.exp(), code);
                code.addInstruction(new Pop());
            }
            case ChuckAST.WhileStmt s -> {
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
            }
            case ChuckAST.UntilStmt s -> {
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
            }
            case ChuckAST.DoStmt s -> {
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
            }
            case ChuckAST.BreakStmt _ -> {
                if (!breakJumps.isEmpty()) {
                    breakJumps.peek().add(code.getNumInstructions());
                    code.addInstruction(null); // placeholder for Jump
                }
            }
            case ChuckAST.ContinueStmt _ -> {
                if (!continueJumps.isEmpty()) {
                    continueJumps.peek().add(code.getNumInstructions());
                    code.addInstruction(null); // placeholder for Jump
                }
            }
            case ChuckAST.SwitchStmt s -> {
                emitExpression(s.condition(), code);

                breakJumps.push(new ArrayList<>());
                List<Integer> caseConditionJumps = new ArrayList<>();
                ChuckAST.CaseStmt defaultCase = null;
                int defaultBodyStartIndex = -1;

                for (ChuckAST.CaseStmt c : s.cases()) {
                    if (c.isDefault()) {
                        defaultCase = c;
                    } else {
                        code.addInstruction(new Duplicate());
                        emitExpression(c.match(), code);
                        code.addInstruction(new EqualAny());
                        caseConditionJumps.add(code.getNumInstructions());
                        code.addInstruction(null); // JumpIfTrue
                    }
                }

                code.addInstruction(new Pop()); // pop switch value if no match
                int jumpToDefaultOrEnd = code.getNumInstructions();
                code.addInstruction(null); // Jump to default or end

                int caseIdx = 0;
                for (ChuckAST.CaseStmt c : s.cases()) {
                    if (c.isDefault()) {
                        defaultBodyStartIndex = code.getNumInstructions();
                    } else {
                        code.replaceInstruction(caseConditionJumps.get(caseIdx++), new JumpIfTrue(code.getNumInstructions()));
                        code.addInstruction(new Pop()); // pop switch value before body executes
                    }
                    for (ChuckAST.Stmt b : c.body()) {
                        emitStatement(b, code);
                    }
                    // Implicit break: add jump-to-end unless body ends with explicit break
                    java.util.List<ChuckAST.Stmt> body = c.body();
                    boolean endsWithBreak = !body.isEmpty() && body.get(body.size() - 1) instanceof ChuckAST.BreakStmt;
                    if (!endsWithBreak) {
                        breakJumps.peek().add(code.getNumInstructions());
                        code.addInstruction(null); // implicit jump to end (no fall-through)
                    }
                }

                int endPc = code.getNumInstructions();

                if (defaultCase != null) {
                    code.replaceInstruction(jumpToDefaultOrEnd, new Jump(defaultBodyStartIndex));
                } else {
                    code.replaceInstruction(jumpToDefaultOrEnd, new Jump(endPc));
                }

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new Jump(endPc));
                }
            }
            case ChuckAST.ForStmt s -> {
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
            }
            case ChuckAST.BlockStmt s -> {
                for (ChuckAST.Stmt inner : s.statements()) {
                    emitStatement(inner, code);
                }
            }
            case ChuckAST.ImportStmt _ -> {
                // already processed by VM before emit — skip
            }
            case ChuckAST.DeclStmt s -> {
                // Check for 'auto' without initialization
                if (s.type().equals("auto")) {
                    throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                            + ": error: 'auto' requires initialization (cannot declare 'auto' without a value)");
                }
                // Check for static variable declared outside class scope
                if (s.isStatic() && (currentClass == null || !localScopes.isEmpty())) {
                    throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                            + ": error: static variables must be declared at class scope");
                }
                // Track empty-array variable declarations
                if (!s.arraySizes().isEmpty() && s.arraySizes().get(0) instanceof ChuckAST.IntExp sz0 && sz0.value() < 0) {
                    emptyArrayVars.add(s.name());
                }
                varTypes.put(s.name(), s.type()); // track variable type for operator dispatch
                int argCount = 0;
                boolean isUserClass = userClassRegistry.containsKey(s.type());
                boolean isVec = s.type().equals("vec2") || s.type().equals("vec3") || s.type().equals("vec4") || s.type().equals("complex") || s.type().equals("polar");
                boolean forceGlobal = s.isGlobal();
                // Compile-time check: detect global type conflicts
                if (forceGlobal || localScopes.isEmpty()) {
                    String prevType = globalVarTypes.get(s.name());
                    if (prevType != null && !prevType.equals(s.type()) && !prevType.equals("int") && !prevType.equals("float") && !prevType.equals("string")) {
                        throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                                + ": error: global " + prevType + " '" + s.name() + "' has different type '" + s.type()
                                + "' than already existing global " + prevType + " of the same name");
                    }
                    globalVarTypes.put(s.name(), s.type());
                }

                if (isUserClass && s.callArgs() instanceof ChuckAST.CallExp call) {
                    if (forceGlobal || localScopes.isEmpty()) {
                        code.addInstruction(new InstantiateSetAndPushGlobal(s.type(), s.name(), 0, s.isReference(), false, userClassRegistry));
                    } else {
                        Map<String, Integer> scope = localScopes.peek();
                        int offset = scope.size();
                        scope.put(s.name(), offset);
                        code.addInstruction(new InstantiateSetAndPushLocal(s.type(), offset, 0, s.isReference(), false, userClassRegistry));
                    }
                    code.addInstruction(new Dup());
                    for (ChuckAST.Exp arg : call.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new CallMethod(s.type(), call.args().size()));
                    code.addInstruction(new Pop());
                } else {
                    if (s.callArgs() instanceof ChuckAST.CallExp call) {
                        for (ChuckAST.Exp arg : call.args()) {
                            emitExpression(arg, code);
                        }
                        argCount = call.args().size();
                    }
                    if (!s.arraySizes().isEmpty()) {
                        for (ChuckAST.Exp sizeExp : s.arraySizes()) {
                            emitExpression(sizeExp, code);
                        }
                        argCount = s.arraySizes().size();
                    }

                    if (isVec && !s.isReference() && argCount == 0) {
                        code.addInstruction(new PushInt(0)); // dummy size for instantiation
                        argCount = 1;
                    }

                    boolean isArrayDecl = !s.arraySizes().isEmpty();
                    boolean useGlobal = forceGlobal || localScopes.isEmpty() || (localScopes.size() == 1 && isArrayDecl);
                    if (useGlobal) {
                        globalVarTypes.put(s.name(), s.type());
                        code.addInstruction(new InstantiateSetAndPushGlobal(s.type(), s.name(), argCount, s.isReference(), isArrayDecl, userClassRegistry));
                        code.addInstruction(new Pop());
                    } else {
                        Map<String, Integer> scope = localScopes.peek();
                        Integer offset = scope.get(s.name());
                        if (offset == null) {
                            offset = scope.size();
                            scope.put(s.name(), offset);
                        }
                        code.addInstruction(new InstantiateSetAndPushLocal(s.type(), offset, argCount, s.isReference(), isArrayDecl, userClassRegistry));
                        code.addInstruction(new Pop());
                    }
                }
            }
            case ChuckAST.PrintStmt s -> {
                for (ChuckAST.Exp exp : s.expressions()) {
                    emitExpression(exp, code);
                }
                code.addInstruction(new ChuckPrint(s.expressions().size()));
            }
            case ChuckAST.FuncDefStmt s -> {
                // Validate operator overloads
                if (s.name().startsWith("__op__") || s.name().startsWith("__pub_op__")) {
                    String opSuffix = s.name().startsWith("__pub_op__") ? s.name().substring("__pub_op__".length()) : s.name().substring("__op__".length());
                    // ~ is never a valid overloadable operator
                    if (opSuffix.equals("~")) {
                        throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                                + ": error: cannot overload operator '" + opSuffix + "'");
                    }
                    // ++ and -- cannot be overloaded for primitive types
                    java.util.Set<String> primitiveTypes = java.util.Set.of("int", "float", "string", "time", "dur", "void");
                    if ((opSuffix.equals("++") || opSuffix.equals("--")) && s.argTypes().size() >= 1) {
                        for (String argType : s.argTypes()) {
                            if (primitiveTypes.contains(argType)) {
                                throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                                        + ": error: cannot overload operator '" + opSuffix + "' for primitive type '" + argType + "'");
                            }
                        }
                    }
                    // => cannot be overloaded for UGen subtypes
                    if ((opSuffix.equals("=>") || opSuffix.equals("@=>")) && s.argTypes().size() >= 1) {
                        for (String argType : s.argTypes()) {
                            if (isKnownUGenType(argType)) {
                                throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                                        + ": error: cannot overload '" + opSuffix + "' for UGen subtype '" + argType + "'");
                            }
                        }
                    }
                }
                String key = s.name() + ":" + s.argNames().size();
                ChuckCode funcCode = functions.get(key);
                if (funcCode == null) {
                    funcCode = new ChuckCode(s.name());
                }

                String prevReturnType = currentFuncReturnType;
                boolean prevHasReturn = currentFuncHasReturn;
                boolean prevStaticCtx = inStaticFuncContext;
                currentFuncReturnType = s.returnType() != null ? s.returnType() : "void";
                currentFuncHasReturn = false;
                inStaticFuncContext = s.isStatic();

                Map<String, Integer> scope = new HashMap<>();
                localScopes.push(scope);
                for (int i = 0; i < s.argNames().size(); i++) {
                    scope.put(s.argNames().get(i), i);
                }
                emitStatement(s.body(), funcCode);
                funcCode.addInstruction(new ReturnFunc());
                localScopes.pop();

                // Check for missing return in non-void function
                if (!currentFuncReturnType.equals("void") && !currentFuncHasReturn) {
                    throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                            + ": error: not all control paths in 'fun " + currentFuncReturnType + " " + s.name() + "()' return a value");
                }

                currentFuncReturnType = prevReturnType;
                currentFuncHasReturn = prevHasReturn;
                inStaticFuncContext = prevStaticCtx;

                functions.put(key, funcCode);
                if (s.returnType() != null && !s.returnType().equals("void")) {
                    functionReturnTypes.put(key, s.returnType());
                }
            }
            case ChuckAST.ReturnStmt s -> {
                // Check for return value in void function
                if (s.exp() != null && "void".equals(currentFuncReturnType)) {
                    throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                            + ": error: function was defined with return type 'void' -- but returning a value");
                }
                currentFuncHasReturn = true;
                if (s.exp() != null) {
                    emitExpression(s.exp(), code);
                }
                code.addInstruction(currentClass != null ? new ReturnMethod() : new ReturnFunc());
            }
            case ChuckAST.ClassDefStmt s -> {
                // Check for extending primitive types
                if (s.parentName() != null) {
                    java.util.Set<String> primitives = java.util.Set.of("int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", "complex", "polar");
                    if (primitives.contains(s.parentName())) {
                        throw new RuntimeException(currentFile + ": error: cannot extend primitive type '" + s.parentName() + "'");
                    }
                }
                List<String[]> fieldDefs = new ArrayList<>();
                java.util.Set<String> fieldNames = new java.util.LinkedHashSet<>();
                List<ChuckAST.FuncDefStmt> methods = new ArrayList<>();

                Map<String, Long> staticInts = new HashMap<>();
                Map<String, Boolean> staticIsDouble = new HashMap<>();
                Map<String, Object> staticObjects = new HashMap<>();

                // Pre-check: static vars cannot be declared inside nested blocks within a class body
                for (ChuckAST.Stmt bodyItem : s.body()) {
                    if (bodyItem instanceof ChuckAST.BlockStmt block) {
                        checkNoStaticInBlock(block.statements());
                    }
                }
                List<ChuckAST.Stmt> flattenedBody = new ArrayList<>();
                flattenStmts(s.body(), flattenedBody);

                for (ChuckAST.Stmt bodyStmt : flattenedBody) {
                    switch (bodyStmt) {
                        case ChuckAST.DeclStmt f -> {
                            if (f.isStatic()) {
                                boolean isArray = !f.arraySizes().isEmpty();
                                if (!isArray && f.type().equals("int")) {
                                    staticInts.put(f.name(), 0L);
                                } else if (!isArray && f.type().equals("float")) {
                                    staticInts.put(f.name(), Double.doubleToRawLongBits(0.0));
                                    staticIsDouble.put(f.name(), true);
                                } else if (f.type().equals("vec2")) {
                                    staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 2));
                                } else if (f.type().equals("vec3")) {
                                    staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 3));
                                } else if (f.type().equals("vec4")) {
                                    staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 4));
                                } else if (f.type().equals("complex") || f.type().equals("polar")) {
                                    staticObjects.put(f.name(), new ChuckArray(ChuckType.ARRAY, 2));
                                } else {
                                    staticObjects.put(f.name(), null); // Includes static arrays like int a[]
                                }
                            } else {
                                fieldDefs.add(new String[]{f.type(), f.name()});
                                fieldNames.add(f.name());
                            }
                        }
                        case ChuckAST.FuncDefStmt m -> methods.add(m);
                        case ChuckAST.ExpStmt es
                                when es.exp() instanceof ChuckAST.BinaryExp bexp
                                && bexp.op() == ChuckAST.Operator.CHUCK
                                && bexp.rhs() instanceof ChuckAST.DeclExp rDecl -> {
                            if (rDecl.isStatic()) {
                                boolean isArray = !rDecl.arraySizes().isEmpty();
                                if (!isArray && rDecl.type().equals("int")) {
                                    long initVal = bexp.lhs() instanceof ChuckAST.IntExp iv ? iv.value() : 0L;
                                    staticInts.put(rDecl.name(), initVal);
                                } else if (!isArray && rDecl.type().equals("float")) {
                                    double initVal = bexp.lhs() instanceof ChuckAST.FloatExp fv ? fv.value()
                                            : bexp.lhs() instanceof ChuckAST.IntExp iv ? (double) iv.value() : 0.0;
                                    staticInts.put(rDecl.name(), Double.doubleToRawLongBits(initVal));
                                    staticIsDouble.put(rDecl.name(), true);
                                } else {
                                    staticObjects.put(rDecl.name(), null);
                                }
                            } else {
                                // e.g. `5 => int n;` — field declaration with literal initializer
                                String initStr = null;
                                switch (bexp.lhs()) {
                                    case ChuckAST.IntExp iv -> initStr = String.valueOf(iv.value());
                                    case ChuckAST.FloatExp fv -> initStr = String.valueOf(fv.value());
                                    default -> {
                                    }
                                }
                                if (initStr != null) {
                                    fieldDefs.add(new String[]{rDecl.type(), rDecl.name(), initStr});
                                } else {
                                    fieldDefs.add(new String[]{rDecl.type(), rDecl.name()});
                                }
                                fieldNames.add(rDecl.name());
                            }
                        }
                        case ChuckAST.ExpStmt es when es.exp() instanceof ChuckAST.DeclExp rDecl -> {
                            // Standalone declaration like 'static int a[];'
                            if (rDecl.isStatic()) {
                                boolean isArray = !rDecl.arraySizes().isEmpty();
                                if (!isArray && rDecl.type().equals("int")) {
                                    staticInts.put(rDecl.name(), 0L);
                                } else if (!isArray && rDecl.type().equals("float")) {
                                    staticInts.put(rDecl.name(), Double.doubleToRawLongBits(0.0));
                                    staticIsDouble.put(rDecl.name(), true);
                                } else {
                                    staticObjects.put(rDecl.name(), null);
                                }
                            } else {
                                fieldDefs.add(new String[]{rDecl.type(), rDecl.name()});
                                fieldNames.add(rDecl.name());
                            }
                        }
                        default -> {
                        }
                    }
                }
                Map<String, ChuckCode> methodCodes = new HashMap<>();
                Map<String, ChuckCode> staticMethodCodes = new HashMap<>();
                String prevClass = currentClass;
                java.util.Set<String> prevFields = currentClassFields;
                currentClass = s.name();
                currentClassFields = fieldNames;

                // Pre-register with actual field maps so static-field references in method bodies resolve.
                // methodCodes/staticMethodCodes are mutable maps; stubs added in pass 1 below become visible here.
                userClassRegistry.put(s.name(), new UserClassDescriptor(
                        s.name(), s.parentName(), fieldDefs, methodCodes, staticMethodCodes,
                        staticInts, staticIsDouble, staticObjects));

                // Always compile pre-constructor body into a dedicated ChuckCode.
                // This runs each time a new instance of this class is created.
                ChuckCode preCtorCodeLocal = new ChuckCode("__preCtor__" + s.name());
                for (ChuckAST.Stmt bodyStmt : flattenedBody) {
                    if (bodyStmt instanceof ChuckAST.DeclStmt || bodyStmt instanceof ChuckAST.FuncDefStmt) {
                        continue;
                    }
                    // Skip static variable initializers — static vars are initialized in first pass
                    if (bodyStmt instanceof ChuckAST.ExpStmt es2
                            && es2.exp() instanceof ChuckAST.BinaryExp bexp2
                            && bexp2.op() == ChuckAST.Operator.CHUCK
                            && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl2
                            && rDecl2.isStatic()) {
                        continue;
                    }
                    // Field initializers like `1 => int x` — emit as field assignment on 'this'
                    if (bodyStmt instanceof ChuckAST.ExpStmt es
                            && es.exp() instanceof ChuckAST.BinaryExp bexp
                            && bexp.op() == ChuckAST.Operator.CHUCK
                            && bexp.rhs() instanceof ChuckAST.DeclExp rDecl
                            && fieldNames.contains(rDecl.name())) {
                        emitExpression(bexp.lhs(), preCtorCodeLocal);
                        preCtorCodeLocal.addInstruction(new PushThis());
                        preCtorCodeLocal.addInstruction(new SetMemberIntByName(rDecl.name()));
                        preCtorCodeLocal.addInstruction(new Pop());
                        continue;
                    }
                    emitStatement(bodyStmt, preCtorCodeLocal);
                }

                // Track methods defined so far to detect duplicates
                java.util.Set<String> definedMethods = new java.util.HashSet<>();
                currentClassMethodsList = methods;

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
                            throw new RuntimeException(currentFile + ":" + m.line() + ":" + m.column()
                                    + ": error: constructor cannot be declared as 'static'");
                        }
                        if (m.returnType() != null && !m.returnType().equals("void") && !m.returnType().isEmpty()) {
                            throw new RuntimeException(currentFile + ":" + m.line() + ":" + m.column()
                                    + ": error: constructor must return void -- returning type '" + m.returnType() + "'");
                        }
                    }

                    String methodKey = methodName + ":" + m.argNames().size();
                    if (definedMethods.contains(methodKey)) {
                        throw new RuntimeException(currentFile + ":" + m.line() + ":" + m.column()
                                + ": error: cannot overload function with identical arguments -- '"
                                + methodName + "' already defined in class '" + s.name() + "'");
                    }
                    definedMethods.add(methodKey);

                    ChuckCode stub = new ChuckCode(methodName);
                    methodCodeMap.put(m, stub);
                    if (m.isStatic()) {
                        staticMethodCodes.put(methodKey, stub);
                    } else {
                        methodCodes.put(methodKey, stub);
                    }
                }

                // Pass 2: compile method bodies (all stubs already registered above)
                for (Map.Entry<ChuckAST.FuncDefStmt, ChuckCode> entry : methodCodeMap.entrySet()) {
                    ChuckAST.FuncDefStmt m = entry.getKey();
                    ChuckCode methodCode = entry.getValue();

                    String methodName = m.name();
                    if (methodName.equals("@construct")) {
                        methodName = s.name();
                    }

                    String prevReturnType = currentFuncReturnType;
                    boolean prevHasReturn = currentFuncHasReturn;
                    boolean prevStaticCtx = inStaticFuncContext;
                    currentFuncReturnType = m.returnType() != null ? m.returnType() : "void";
                    currentFuncHasReturn = false;
                    inStaticFuncContext = m.isStatic();

                    Map<String, Integer> scope = new HashMap<>();
                    localScopes.push(scope);
                    for (int i = 0; i < m.argNames().size(); i++) {
                        scope.put(m.argNames().get(i), i);
                    }

                    emitStatement(m.body(), methodCode);
                    methodCode.addInstruction(m.isStatic() ? new ReturnFunc() : new ReturnMethod());

                    if (!currentFuncReturnType.equals("void") && !currentFuncHasReturn) {
                        throw new RuntimeException(currentFile + ":" + m.line() + ":" + m.column()
                                + ": error: not all control paths in 'fun " + currentFuncReturnType + " "
                                + s.name() + "." + m.name() + "()' return a value");
                    }

                    currentFuncReturnType = prevReturnType;
                    currentFuncHasReturn = prevHasReturn;
                    inStaticFuncContext = prevStaticCtx;
                    localScopes.pop();
                }
                currentClass = prevClass;
                currentClassFields = prevFields;
                ChuckCode finalPreCtorCode = preCtorCodeLocal.getNumInstructions() > 0 ? preCtorCodeLocal : null;
                UserClassDescriptor descriptor = new UserClassDescriptor(s.name(), s.parentName(), fieldDefs, methodCodes, staticMethodCodes, staticInts, staticIsDouble, staticObjects, finalPreCtorCode);
                userClassRegistry.put(s.name(), descriptor);
                if (code != null) {
                    code.addInstruction(new RegisterClass(s.name(), descriptor));
                }
            }
            case ChuckAST.IfStmt s -> {
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
            }
            case ChuckAST.ForEachStmt s -> {
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
                // Track iter var type so method dispatch inside the body works
                if (s.iterType() != null && !s.iterType().equals("auto")) {
                    varTypes.put(s.iterName(), s.iterType());
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
            case ChuckAST.RepeatStmt s -> {
                if (localScopes.isEmpty()) {
                    localScopes.push(new HashMap<>());
                }
                Map<String, Integer> scope = localScopes.peek();
                String cntName = "__repeat_cnt_" + s.line() + "_" + s.column();
                int cntOffset = scope.size();
                scope.put(cntName, cntOffset);

                // Store count in local
                emitExpression(s.count(), code);
                code.addInstruction(new StoreLocal(cntOffset));
                code.addInstruction(new Pop());

                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                // Condition: counter > 0
                code.addInstruction(new LoadLocal(cntOffset));
                code.addInstruction(new PushInt(0));
                code.addInstruction(new GreaterThanAny());
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                // Body
                emitStatement(s.body(), code);

                // Decrement counter
                int updateStart = code.getNumInstructions();
                code.addInstruction(new LoadLocal(cntOffset));
                code.addInstruction(new PushInt(1));
                code.addInstruction(new MinusAny());
                code.addInstruction(new StoreLocal(cntOffset));
                code.addInstruction(new Pop());
                code.addInstruction(new Jump(startPc));

                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new JumpIfFalse(endPc));

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new Jump(updateStart));
                }
            }
            default -> {
            }
        }
    }

    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        switch (exp) {
            case ChuckAST.IntExp e ->
                code.addInstruction(new PushInt(e.value()));
            case ChuckAST.FloatExp e ->
                code.addInstruction(new PushFloat(e.value()));
            case ChuckAST.StringExp e ->
                code.addInstruction(new PushString(e.value()));
            case ChuckAST.MeExp _ ->
                code.addInstruction(new PushMe());
            case ChuckAST.UnaryExp e -> {
                if (e.op() == ChuckAST.Operator.S_OR) {
                    String innerType = getVarType(e.exp());
                    if (innerType != null && userClassRegistry.containsKey(innerType)) {
                        ChuckCode opCode = functions.get("__pub_op__!:1");
                        if (opCode == null) {
                            opCode = functions.get("__op__!:1");
                        }
                        if (opCode != null) {
                            emitExpression(e.exp(), code);
                            code.addInstruction(new CallFunc(opCode, 1));
                            return;
                        }
                    }
                }
                emitExpression(e.exp(), code);
                switch (e.op()) {
                    case MINUS ->
                        code.addInstruction(new NegateAny());
                    case S_OR ->
                        code.addInstruction(new LogicalNot());
                    case PLUS -> {
                    }
                    default -> {
                    }
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
                    for (ChuckAST.Exp arg : call.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new CallMethod(e.type(), call.args().size()));
                } else {
                    if (e.callArgs() instanceof ChuckAST.CallExp call) {
                        for (ChuckAST.Exp arg : call.args()) {
                            emitExpression(arg, code);
                        }
                        argCount = call.args().size();
                    }
                    if (!e.arraySizes().isEmpty()) {
                        for (ChuckAST.Exp sizeExp : e.arraySizes()) {
                            emitExpression(sizeExp, code);
                        }
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
                        if (e.lhs() instanceof ChuckAST.IdExp(String n, int _, int _)) {
                            if (n.equals("null")) {
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                        + ": error: cannot infer 'auto' type from 'null'");
                            }
                            if (n.equals("void")) {
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                        + ": error: cannot infer 'auto' type from 'void'");
                            }
                            String lhsType = varTypes.get(n);
                            if ("auto".equals(lhsType)) {
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                        + ": error: cannot infer 'auto' type from another 'auto' variable");
                            }
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
                            if (opCode == null) {
                                opCode = functions.get("__op__++:1");
                            }
                            if (opCode != null) {
                                emitExpression(e.rhs(), code);
                                code.addInstruction(new CallFunc(opCode, 1));
                                return;
                            }
                        }
                    }
                    // Disallow compound assignments on read-only builtins (e.g. now++, 1 +=> pi)
                    if (e.rhs() instanceof ChuckAST.IdExp rid) {
                        if (rid.name().equals("now")) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                    + ": error: cannot perform compound assignment on 'now'");
                        }
                        if (rid.name().equals("pi") || rid.name().equals("e") || rid.name().equals("maybe")) {
                            throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                    + ": error: cannot assign to read-only value '" + rid.name() + "'");
                        }
                    } else if (e.rhs() instanceof ChuckAST.DotExp rdot
                            && rdot.base() instanceof ChuckAST.IdExp baseId
                            && baseId.name().equals("Math")) {
                        switch (rdot.member()) {
                            case "PI", "TWO_PI", "HALF_PI", "E", "INFINITY", "NEGATIVE_INFINITY", "NaN", "nan" ->
                                throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                        + ": error: 'Math." + rdot.member() + "' is a constant, and is not assignable");
                            default -> {
                            }
                        }
                    }
                    ChuckAST.Operator arith = switch (e.op()) {
                        case PLUS_CHUCK ->
                            ChuckAST.Operator.PLUS;
                        case MINUS_CHUCK ->
                            ChuckAST.Operator.MINUS;
                        case TIMES_CHUCK ->
                            ChuckAST.Operator.TIMES;
                        case DIVIDE_CHUCK ->
                            ChuckAST.Operator.DIVIDE;
                        case PERCENT_CHUCK ->
                            ChuckAST.Operator.PERCENT;
                        default ->
                            ChuckAST.Operator.PLUS;
                    };
                    emitExpression(e.rhs(), code);  // push current value of target
                    emitExpression(e.lhs(), code);  // push the operand
                    switch (arith) {
                        case PLUS ->
                            code.addInstruction(new AddAny());
                        case MINUS ->
                            code.addInstruction(new MinusAny());
                        case TIMES ->
                            code.addInstruction(new TimesAny());
                        case DIVIDE ->
                            code.addInstruction(new DivideAny());
                        case PERCENT ->
                            code.addInstruction(new ModuloAny());
                        default -> {
                        }
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
                        if (opCode == null) {
                            opCode = functions.get("__op__" + opKey);
                        }
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
                    if (isPostfixPlus) {
                        code.addInstruction(new AddAny());
                    } else {
                        code.addInstruction(new MinusAny());
                    }
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
                    switch (e.rhs()) {
                        case ChuckAST.IdExp id -> {
                            Integer lo = getLocalOffset(id.name());
                            if (lo != null) {
                                code.addInstruction(new StoreLocal(lo));
                            } else {
                                // For @=>, we want direct assignment to the global
                                code.addInstruction(new SetGlobalObjectOnly(id.name()));
                            }
                        }
                        case ChuckAST.DotExp dot -> {
                            emitExpression(dot.base(), code);
                            code.addInstruction(new SetMemberIntByName(dot.member()));
                        }
                        case ChuckAST.ArrayAccessExp ae -> {
                            emitExpression(ae.base(), code);
                            for (int i = 0; i < ae.indices().size(); i++) {
                                emitExpression(ae.indices().get(i), code);
                                if (i < ae.indices().size() - 1) {
                                    code.addInstruction(new GetArrayInt());
                                } else {
                                    code.addInstruction(new SetArrayInt());
                                }
                            }
                        }
                        default -> {
                        }
                    }
                } else if (e.op() == ChuckAST.Operator.ASSIGN) {
                    emitExpression(e.rhs(), code);
                    switch (e.lhs()) {
                        case ChuckAST.IdExp id -> {
                            Integer localOffset = getLocalOffset(id.name());
                            if (localOffset != null) {
                                code.addInstruction(new StoreLocal(localOffset));
                            } else {
                                code.addInstruction(new SetGlobalObjectOrInt(id.name()));
                            }
                        }
                        case ChuckAST.DotExp dot -> {
                            emitExpression(dot.base(), code);
                            code.addInstruction(new SetMemberIntByName(dot.member()));
                        }
                        case ChuckAST.ArrayAccessExp ae -> {
                            emitExpression(ae.base(), code);
                            for (int i = 0; i < ae.indices().size(); i++) {
                                emitExpression(ae.indices().get(i), code);
                                if (i < ae.indices().size() - 1) {
                                    code.addInstruction(new GetArrayInt());
                                } else {
                                    code.addInstruction(new SetArrayInt());
                                }
                            }
                        }
                        default -> {
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
                        if (e.lhs() instanceof ChuckAST.IdExp(String fnName, int line, int column)) {
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
                                case PLUS ->
                                    "+";
                                case MINUS ->
                                    "-";
                                case TIMES ->
                                    "*";
                                case DIVIDE ->
                                    "/";
                                case PERCENT ->
                                    "%";
                                case LT ->
                                    "<";
                                case LE ->
                                    "<=";
                                case GT ->
                                    ">";
                                case GE ->
                                    ">=";
                                case EQ ->
                                    "==";
                                case NEQ ->
                                    "!=";
                                default ->
                                    null;
                            };
                            if (opSymbol != null) {
                                ChuckCode opFunc = functions.get("__pub_op__" + opSymbol + ":2");
                                if (opFunc == null) {
                                    opFunc = functions.get("__op__" + opSymbol + ":2");
                                }
                                if (opFunc != null) {
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    code.addInstruction(new CallFunc(opFunc, 2));
                                    return;
                                }

                                // Also check for method-style overload in user class
                                UserClassDescriptor desc = userClassRegistry.get(lhsType);
                                if (desc != null) {
                                    String mName = desc.methods().containsKey("__pub_op__" + opSymbol + ":1") ? "__pub_op__" + opSymbol
                                            : (desc.methods().containsKey("__op__" + opSymbol + ":1") ? "__op__" + opSymbol : null);
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
                                case PLUS ->
                                    isPolar ? new PolarAdd() : new ComplexAdd();
                                case MINUS ->
                                    isPolar ? new PolarSub() : new ComplexSub();
                                case TIMES ->
                                    isPolar ? new PolarMul() : new ComplexMul();
                                case DIVIDE ->
                                    isPolar ? new PolarDiv() : new ComplexDiv();
                                default ->
                                    null;
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
                                default -> {
                                } // fall through to generic
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
                        case PLUS ->
                            code.addInstruction(new AddAny());
                        case MINUS ->
                            code.addInstruction(new MinusAny());
                        case TIMES ->
                            code.addInstruction(new TimesAny());
                        case DIVIDE ->
                            code.addInstruction(new DivideAny());
                        case PERCENT ->
                            code.addInstruction(new ModuloAny());
                        case S_OR ->
                            code.addInstruction(new BitwiseOrAny());
                        case S_AND ->
                            code.addInstruction(new BitwiseAndAny());
                        case LT ->
                            code.addInstruction(new LessThanAny());
                        case LE ->
                            code.addInstruction(new LessOrEqualAny());
                        case GT ->
                            code.addInstruction(new GreaterThanAny());
                        case GE ->
                            code.addInstruction(new GreaterOrEqualAny());
                        case EQ ->
                            code.addInstruction(new EqualsAny());
                        case NEQ ->
                            code.addInstruction(new NotEqualsAny());
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
                        case SHIFT_LEFT -> {
                            String lhsElemType = getExprType(e.lhs());
                            code.addInstruction(new ShiftLeftOrAppend(lhsElemType));
                        }
                        default -> {
                        }
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
                if (localOffset != null) {
                    code.addInstruction(new LoadLocal(localOffset));
                } else if (e.name().equals("null")) {
                    code.addInstruction(new PushNull());
                } else if (e.name().equals("true")) {
                    code.addInstruction(new PushInt(1));
                } else if (e.name().equals("false")) {
                    code.addInstruction(new PushInt(0));
                } else if (e.name().equals("now")) {
                    code.addInstruction(new PushNow());
                } else if (e.name().equals("dac")) {
                    code.addInstruction(new PushDac());
                } else if (e.name().equals("blackhole")) {
                    code.addInstruction(new PushBlackhole());
                } else if (e.name().equals("adc")) {
                    code.addInstruction(new PushAdc());
                } else if (e.name().equals("me")) {
                    code.addInstruction(new PushMe());
                } else if (e.name().equals("cherr")) {
                    code.addInstruction(new PushCherr());
                } else if (e.name().equals("chout")) {
                    code.addInstruction(new PushChout());
                } else if (e.name().equals("Machine")) {
                    code.addInstruction(new PushMachine());
                } else if (e.name().equals("maybe")) {
                    code.addInstruction(new PushMaybe());
                } else if (e.name().equals("this")) {
                    code.addInstruction(new PushThis());
                } else if (e.name().equals("second") || e.name().equals("ms") || e.name().equals("samp")
                        || e.name().equals("minute") || e.name().equals("hour")) {
                    code.addInstruction(new PushInt(1));
                    code.addInstruction(new CreateDuration(e.name()));
                } else if (currentClassFields.contains(e.name())) {
                    code.addInstruction(new GetUserField(e.name()));
                } else if (currentClass != null && userClassRegistry.get(currentClass) != null
                        && (userClassRegistry.get(currentClass).staticInts().containsKey(e.name())
                        || userClassRegistry.get(currentClass).staticObjects().containsKey(e.name()))) {
                    code.addInstruction(new GetStatic(currentClass, e.name()));
                } else {
                    code.addInstruction(new GetGlobalObjectOrInt(e.name()));
                }
            }
            case ChuckAST.DotExp e -> {
                // super.field — access field on 'this' (fields are per-instance, not per-class)
                if (e.base() instanceof ChuckAST.IdExp supId && supId.name().equals("super")) {
                    code.addInstruction(new PushThis());
                    code.addInstruction(new GetFieldByName(e.member()));
                    return;
                }
                // Handle static field access: ClassName.staticField
                if (e.base() instanceof ChuckAST.IdExp(String potentialClassName, _, _)) {
                    UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                    if (classDesc != null && (classDesc.staticInts().containsKey(e.member()) || classDesc.staticObjects().containsKey(e.member()))) {
                        code.addInstruction(new GetStatic(potentialClassName, e.member()));
                        return;
                    }
                }
                if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
                    if (e.member().equals("nl") || e.member().equals("newline")) {
                        code.addInstruction(new PushString("\n"));
                    } else {
                        code.addInstruction(new GetBuiltinStatic("org.chuck.core.ChuckIO", e.member()));
                    }
                    return;
                }
                if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                    switch (e.member()) {
                        case "INFINITY", "infinity" -> {
                            code.addInstruction(new PushFloat(Double.POSITIVE_INFINITY));
                            return;
                        }
                        case "NEGATIVE_INFINITY" -> {
                            code.addInstruction(new PushFloat(Double.NEGATIVE_INFINITY));
                            return;
                        }
                        case "NaN", "nan" -> {
                            code.addInstruction(new PushFloat(Double.NaN));
                            return;
                        }
                        case "PI" -> {
                            code.addInstruction(new PushFloat(Math.PI));
                            return;
                        }
                        case "TWO_PI" -> {
                            code.addInstruction(new PushFloat(2.0 * Math.PI));
                            return;
                        }
                        case "HALF_PI" -> {
                            code.addInstruction(new PushFloat(Math.PI / 2.0));
                            return;
                        }
                        case "E" -> {
                            code.addInstruction(new PushFloat(Math.E));
                            return;
                        }
                        case "SQRT2" -> {
                            code.addInstruction(new PushFloat(Math.sqrt(2.0)));
                            return;
                        }
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
                        if (baseType == null) {
                            baseType = globalVarTypes.get(baseId.name());
                        }
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
                for (ChuckAST.Exp el : e.elements()) {
                    emitExpression(el, code);
                }
                code.addInstruction(new NewArrayFromStack(e.elements().size()));
            }
            case ChuckAST.VectorLitExp e -> {
                for (ChuckAST.Exp el : e.elements()) {
                    emitExpression(el, code);
                    code.addInstruction(new EnsureFloat());
                }
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
                        // Check hierarchy for method (key is "name:argCount")
                        String superKey = dot2.member() + ":" + e.args().size();
                        boolean found = false;
                        for (String cls = parentName; cls != null && !found;) {
                            UserClassDescriptor desc = userClassRegistry.get(cls);
                            if (desc == null) {
                                break;
                            }
                            found = desc.methods().containsKey(superKey) || desc.staticMethods().containsKey(superKey);
                            cls = desc.parentName();
                        }
                        if (!found) {
                            throw new RuntimeException(currentFile + ":" + dot2.base().line() + ":" + dot2.base().column()
                                    + ": error: class '" + parentName + "' has no member '" + dot2.member() + "'");
                        }
                    }
                }
                if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
                    if (dot.member().equals("nl") || dot.member().equals("newline")) {
                        code.addInstruction(new PushString("\n"));
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
                        for (ChuckAST.Exp arg : e.args()) {
                            emitExpression(arg, code);
                        }
                        code.addInstruction(new StdFunc(member, e.args().size()));
                    } else {
                        // General fallback: try CallBuiltinStatic for Std
                        for (ChuckAST.Exp arg : e.args()) {
                            emitExpression(arg, code);
                        }
                        code.addInstruction(new CallBuiltinStatic("org.chuck.core.Std", dot.member(), e.args().size()));
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("RegEx")) {
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new CallBuiltinStatic("org.chuck.core.RegEx", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Reflect")) {
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new CallBuiltinStatic("org.chuck.core.Reflect", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("SerialIO")) {
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new CallBuiltinStatic("org.chuck.core.SerialIO", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot && dot.member().equals("last")) {
                    emitExpression(dot.base(), code);
                    code.addInstruction(new GetLastOut());
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                    switch (dot.member()) {
                        case "random", "randf" ->
                            code.addInstruction(new MathRandom());
                        case "sin" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("sin"));
                            }
                        }
                        case "cos" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("cos"));
                            }
                        }
                        case "pow" -> {
                            if (e.args().size() >= 2) {
                                emitExpression(e.args().get(0), code);
                                emitExpression(e.args().get(1), code);
                                code.addInstruction(new MathFunc("pow"));
                            }
                        }
                        case "sqrt" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("sqrt"));
                            }
                        }
                        case "abs" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("abs"));
                            }
                        }
                        case "floor" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("floor"));
                            }
                        }
                        case "ceil" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("ceil"));
                            }
                        }
                        case "equal" -> {
                            if (e.args().size() >= 2) {
                                emitExpression(e.args().get(0), code);
                                emitExpression(e.args().get(1), code);
                                code.addInstruction(new MathFunc("equal"));
                            }
                        }
                        case "euclidean" -> {
                            if (e.args().size() >= 2) {
                                emitExpression(e.args().get(0), code);
                                emitExpression(e.args().get(1), code);
                                code.addInstruction(new MathFunc("euclidean"));
                            }
                        }
                        case "srandom" -> {
                            emitExpression(e.args().get(0), code);
                            code.addInstruction(new MathFunc("srandom"));
                        }
                        case "srandom_init", "randomize" ->
                            code.addInstruction(new MathFunc("srandom"));
                        case "isinf" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("isinf"));
                            }
                        }
                        case "isnan" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("isnan"));
                            }
                        }
                        case "log" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("log"));
                            }
                        }
                        case "log2" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("log2"));
                            }
                        }
                        case "log10" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("log10"));
                            }
                        }
                        case "exp" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("exp"));
                            }
                        }
                        case "round" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("round"));
                            }
                        }
                        case "trunc" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathFunc("trunc"));
                            }
                        }
                        case "help" ->
                            code.addInstruction(new MathHelp());
                        default -> {
                            if (!e.args().isEmpty()) {
                                for (ChuckAST.Exp arg : e.args()) {
                                    emitExpression(arg, code);
                                }
                                code.addInstruction(new MathFunc(dot.member()));
                            } else {
                                code.addInstruction(new MathHelp());
                            }
                        }
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Machine")) {
                    String machMember = dot.member();
                    switch (machMember) {
                        case "realtime" -> code.addInstruction(new PushInt(0));
                        case "silent" -> code.addInstruction(new PushInt(1));
                        default -> {
                            for (ChuckAST.Exp arg : e.args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new MachineCall(machMember, e.args().size()));
                        }
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && (dot.base() instanceof ChuckAST.MeExp || (dot.base() instanceof ChuckAST.IdExp idMe && idMe.name().equals("me")))) {
                    switch (dot.member()) {
                        case "yield" -> code.addInstruction(new Yield());
                        case "dir" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                            } else {
                                code.addInstruction(new PushInt(0));
                            }
                            code.addInstruction(new MeDir());
                        }
                        case "args" -> code.addInstruction(new MeArgs());
                        case "arg" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                            } else {
                                code.addInstruction(new PushInt(0));
                            }
                            code.addInstruction(new MeArg());
                        }
                        case "exit" -> code.addInstruction(new MeExit());
                        default -> {
                            code.addInstruction(new PushMe());
                            for (ChuckAST.Exp arg : e.args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new CallMethod(dot.member(), e.args().size()));
                        }
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot) {
                    // super.method(args) — dispatch to parent class method directly
                    if (dot.base() instanceof ChuckAST.IdExp supId && supId.name().equals("super")
                            && currentClass != null) {
                        UserClassDescriptor cd = userClassRegistry.get(currentClass);
                        String parentName = cd != null ? cd.parentName() : null;
                        if (parentName != null) {
                            for (ChuckAST.Exp arg : e.args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new CallSuperMethod(parentName, dot.member(), e.args().size()));
                            return;
                        }
                    }
                    if (dot.base() instanceof ChuckAST.IdExp id) {
                        if (userClassRegistry.containsKey(id.name())) {
                            String staticKey = dot.member() + ":" + e.args().size();
                            ChuckCode target = resolveStaticMethod(id.name(), staticKey);
                            if (target != null) {
                                for (ChuckAST.Exp arg : e.args()) {
                                    emitExpression(arg, code);
                                }
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
                                for (ChuckAST.Exp arg : e.args()) {
                                    emitExpression(arg, code);
                                }
                                code.addInstruction(new CallFunc(target, e.args().size()));
                                return;
                            }
                        }
                    }
                    emitExpression(dot.base(), code);
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new CallMethod(dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.IdExp id) {
                    String key = id.name() + ":" + e.args().size();

                    // Constructor chain: Foo(args) or this(args) from within a method of class Foo
                    if (currentClass != null && (id.name().equals(currentClass) || id.name().equals("this"))) {
                        String ctorKey = currentClass + ":" + e.args().size();
                        UserClassDescriptor cd = userClassRegistry.get(currentClass);
                        if (cd != null && cd.methods().containsKey(ctorKey)) {
                            code.addInstruction(new PushThis());
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new CallMethod(currentClass, e.args().size()));
                            return;
                        }
                    }

                    if (currentClass != null && userClassRegistry.containsKey(currentClass)) {
                        UserClassDescriptor d = userClassRegistry.get(currentClass);
                        ChuckCode target = resolveStaticMethod(currentClass, key);
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new CallFunc(target, e.args().size()));
                            return;
                        }
                    }
                    if (functions.containsKey(key)) {
                        for (ChuckAST.Exp arg : e.args()) {
                            emitExpression(arg, code);
                        }
                        code.addInstruction(new CallFunc(functions.get(key), e.args().size()));
                    }
                }
            }
            case ChuckAST.SporkExp e -> {
                String funcName = null;
                switch (e.call().base()) {
                    case ChuckAST.IdExp id -> {
                        funcName = id.name();
                        String key = funcName + ":" + e.call().args().size();

                        ChuckCode target = resolveStaticMethod(currentClass, key);
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.call().args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new Spork(target, e.call().args().size()));
                            return;
                        }

                        if (functions.containsKey(key)) {
                            for (ChuckAST.Exp arg : e.call().args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new Spork(functions.get(key), e.call().args().size()));
                            return;
                        }
                    }
                    case ChuckAST.DotExp dot -> {
                        if (dot.base() instanceof ChuckAST.IdExp id && userClassRegistry.containsKey(id.name())) {
                            String staticKey = dot.member() + ":" + e.call().args().size();
                            ChuckCode target = resolveStaticMethod(id.name(), staticKey);
                            if (target != null) {
                                for (ChuckAST.Exp arg : e.call().args()) {
                                    emitExpression(arg, code);
                                }
                                code.addInstruction(new Spork(target, e.call().args().size()));
                                return;
                            }
                        }
                        emitExpression(dot.base(), code);
                        for (ChuckAST.Exp arg : e.call().args()) {
                            emitExpression(arg, code);
                        }
                        code.addInstruction(new SporkMethod(dot.member(), e.call().args().size()));
                        return;
                    }
                    default -> {
                    }
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
            case ChuckAST.CastExp e -> {
                emitExpression(e.value(), code);
                switch (e.targetType()) {
                    case "int" -> code.addInstruction(new CastToInt());
                    case "float" -> code.addInstruction(new CastToFloat());
                    case "string" -> code.addInstruction(new CastToString());
                    // other types: leave value as-is (e.g. casting to a class type is a no-op)
                }
            }
        }
    }

    private void emitChuckTarget(ChuckAST.Exp target, ChuckCode code) {
        switch (target) {
            case ChuckAST.IdExp e -> {
                String type = getVarType(target);
                boolean isUGen = type != null && (isKnownUGenType(type) || isSubclassOfUGen(type));

                if (e.name().equals("now")) {
                    code.addInstruction(new AdvanceTime());
                    return;
                }
                if (e.name().equals("dac")) {
                    code.addInstruction(new ConnectToDac());
                    return;
                }
                if (e.name().equals("blackhole")) {
                    code.addInstruction(new ConnectToBlackhole());
                    return;
                }
                if (e.name().equals("adc")) {
                    code.addInstruction(new ConnectToAdc());
                    return;
                }

                if (isUGen) {
                    // If target is a UGen variable, we do a ChuckTo connection
                    emitExpression(target, code);
                    code.addInstruction(new org.chuck.core.ChuckTo());
                } else {
                    // Otherwise it's a value assignment
                    Integer localOffset = getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new StoreLocal(localOffset));
                    } else if (currentClassFields.contains(e.name())) {
                        code.addInstruction(new SetUserField(e.name()));
                    } else {
                        code.addInstruction(new SetGlobalObjectOrInt(e.name()));
                    }
                }
            }
            case ChuckAST.DotExp e -> {
                // Handle static field target: ClassName.staticField
                if (e.base() instanceof ChuckAST.IdExp(String potentialClassName, int line, int column)) {
                    if (userClassRegistry.containsKey(potentialClassName)) {
                        UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                        if (classDesc != null && (classDesc.staticInts().containsKey(e.member()) || classDesc.staticObjects().containsKey(e.member()))) {
                            code.addInstruction(new SetStatic(potentialClassName, e.member()));
                            return;
                        }
                    }
                }
                if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("mtof")) {
                    code.addInstruction(new StdFunc("mtof", 1));
                } else if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("ftom")) {
                    code.addInstruction(new StdFunc("ftom", 1));
                } else if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Math")) {
                    // Math constants are read-only and cannot be assigned to
                    switch (e.member()) {
                        case "PI", "TWO_PI", "HALF_PI", "E", "INFINITY", "NEGATIVE_INFINITY", "NaN", "nan", "infinity", "negative_infinity" ->
                            throw new RuntimeException(currentFile + ": error: 'Math." + e.member() + "' is a constant, and is not assignable");
                        default -> {
                        }
                    }
                    // val => Math.func: apply math function to the value on stack
                    switch (e.member()) {
                        case "isinf", "isnan", "sin", "cos", "sqrt", "abs", "floor", "ceil", "log", "log2", "log10", "exp", "round", "trunc", "dbtolin", "dbtopow", "lintodb", "powtodb", "dbtorms", "rmstodb" ->
                            code.addInstruction(new MathFunc(e.member()));
                        default ->
                            code.addInstruction(new MathFunc(e.member()));
                    }
                } else {
                    // Vector field write: val => v.x / v.y / v.re / v.im etc.
                    int vecIdx = vecFieldIndex(e.member());
                    if (vecIdx >= 0 && e.base() instanceof ChuckAST.IdExp baseId) {
                        String baseType = varTypes.get(baseId.name());
                        if (baseType == null) {
                            baseType = globalVarTypes.get(baseId.name());
                        }
                        if (isVecType(baseType)) {
                            emitExpression(e.base(), code);
                            code.addInstruction(new PushInt(vecIdx));
                            code.addInstruction(new SetArrayInt());
                            return;
                        }
                    }
                    emitExpression(e.base(), code);
                    code.addInstruction(new SetMemberIntByName(e.member()));
                }
            }
            case ChuckAST.ArrayAccessExp e -> {
                emitExpression(e.base(), code);
                for (int i = 0; i < e.indices().size() - 1; i++) {
                    emitExpression(e.indices().get(i), code);
                    code.addInstruction(new GetArrayInt());
                }
                emitExpression(e.indices().get(e.indices().size() - 1), code);
                code.addInstruction(new SetArrayInt());
            }
            case ChuckAST.DeclExp e -> {
                emitExpression(e, code); // Pushes new object 'target'
                // Stack is now: [..., source, target]

                String type = e.type();
                boolean isUGen = isKnownUGenType(type) || isSubclassOfUGen(type);

                if (isUGen) {
                    code.addInstruction(new org.chuck.core.ChuckTo());
                    // ChuckTo pops source & target, pushes target back.
                    // Stack is now: [..., target]
                } else {
                    code.addInstruction(new Pop());
                    // Stack is now: [..., source]
                }

                // Now save the target object to its variable
                Integer localOffset = getLocalOffset(e.name());
                if (localOffset != null) {
                    code.addInstruction(new StoreLocal(localOffset));
                } else {
                    code.addInstruction(new SetGlobalObjectOrInt(e.name()));
                }
                // These instructions leave 'target' on stack for chaining.
            }
            case ChuckAST.BinaryExp e -> {
                if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                    // In a chain: source => target1 => target2
                    // When called with target = (target1 => target2)
                    // We should first process target1, then target2.
                    emitChuckTarget(e.lhs(), code);
                    emitChuckTarget(e.rhs(), code);
                } else {
                    emitExpression(target, code);
                }
            }
            default ->
                emitExpression(target, code);
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
            if (rhs instanceof ChuckAST.IdExp rid) {
                code.addInstruction(new StoreLocalOrGlobal(rid.name()));
            }
            code.addInstruction(new Pop());
            if (lhs instanceof ChuckAST.IdExp lid) {
                code.addInstruction(new StoreLocalOrGlobal(lid.name()));
            }
        }
    }

    private Integer getLocalOffset(String name) {
        if (localScopes.isEmpty()) {
            return null;
        }
        return localScopes.peek().get(name);
    }

    /**
     * Returns the array index for a vec/complex/polar field name, or -1 if not
     * a vec field. x/re/mag -> 0, y/im/phase -> 1, z -> 2, w -> 3.
     */
    private static int vecFieldIndex(String member) {
        return switch (member) {
            case "x", "re", "mag", "magnitude" ->
                0;
            case "y", "im", "phase" ->
                1;
            case "z" ->
                2;
            case "w" ->
                3;
            default ->
                -1;
        };
    }

    /**
     * Returns true if the type name is a vector or complex primitive type.
     */
    private static boolean isVecType(String type) {
        return type != null && (type.equals("vec2") || type.equals("vec3") || type.equals("vec4")
                || type.equals("complex") || type.equals("polar"));
    }

    /**
     * Checks a static initializer's source expression for disallowed references
     * (member/local vars and funcs).
     */
    private void checkStaticInitSource(ChuckAST.Exp exp) {
        switch (exp) {
            case null -> {
            }

            case ChuckAST.IdExp id -> {
                // Member field access
                if (currentClassFields.contains(id.name())) {
                    throw new RuntimeException(currentFile + ": error: cannot access non-static variable '"
                            + currentClass + "." + id.name() + "' to initialize a static variable");
                }
                // Local variable from outer scope (not a builtin)
                if (globalVarTypes.containsKey(id.name()) || varTypes.containsKey(id.name())) {
                    // Check if it's a global var defined outside the class (local to the file)
                    if (!currentClassFields.contains(id.name())) {
                        throw new RuntimeException(currentFile + ": error: cannot access local variable '"
                                + id.name() + "' to initialize a static variable");
                    }
                }
            }
            case ChuckAST.CallExp call -> {
                if (call.base() instanceof ChuckAST.IdExp fid) {
                    // Check if it's a member method (non-static) of the current class
                    for (ChuckAST.FuncDefStmt m : currentClassMethodsList) {
                        if (m.name().equals(fid.name()) && !m.isStatic() && !m.name().equals(currentClass)) {
                            throw new RuntimeException(currentFile + ": error: cannot call non-static function '"
                                    + currentClass + "." + fid.name() + "()' to initialize a static variable");
                        }
                    }
                    // Check if it's a local (file-level) function
                    String key = fid.name() + ":" + call.args().size();
                    if (functions.containsKey(key)) {
                        // Confirm it's not a static method of the current class
                        boolean isClassStatic = false;
                        for (ChuckAST.FuncDefStmt m : currentClassMethodsList) {
                            if (m.name().equals(fid.name()) && m.isStatic()) {
                                isClassStatic = true;
                                break;
                            }
                        }
                        if (!isClassStatic) {
                            throw new RuntimeException(currentFile + ": error: cannot call local function '"
                                    + fid.name() + "()' to initialize a static variable");
                        }
                    }
                }
                for (ChuckAST.Exp arg : call.args()) {
                    checkStaticInitSource(arg);
                }
            }
            case ChuckAST.BinaryExp bin -> {
                checkStaticInitSource(bin.lhs());
                checkStaticInitSource(bin.rhs());
            }
            case ChuckAST.UnaryExp u ->
                checkStaticInitSource(u.exp());
            default -> {
            }
        }
    }

    /**
     * Checks that no static variable is assigned inside a nested block within a
     * class body.
     */
    private void checkNoStaticInBlock(List<ChuckAST.Stmt> stmts) {
        for (ChuckAST.Stmt st : stmts) {
            switch (st) {
                case ChuckAST.ExpStmt es when es.exp() instanceof ChuckAST.BinaryExp be
                && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)
                && be.rhs() instanceof ChuckAST.DeclExp rhs && rhs.isStatic() ->
                    throw new RuntimeException(currentFile + ":" + be.line() + ":" + be.column()
                            + ": error: static variables must be declared at class scope");
                case ChuckAST.BlockStmt inner ->
                    checkNoStaticInBlock(inner.statements());
                default -> {
                }
            }
        }
    }

    // --- Instructions ---
    static class StackSwap implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) {
                return;
            }
            Object r = s.reg.pop();
            Object l = s.reg.pop();
            if (r instanceof ChuckObject) {
                s.reg.pushObject(r);
            } else if (r instanceof Double aDouble) {
                s.reg.push(aDouble);
            } else {
                s.reg.push((Long) r);
            }
            if (l instanceof ChuckObject) {
                s.reg.pushObject(l);
            } else if (l instanceof Double aDouble) {
                s.reg.push(aDouble);
            } else {
                s.reg.push((Long) l);
            }
        }
    }

    static class StoreLocalOrGlobal implements ChuckInstr {

        String name;

        StoreLocalOrGlobal(String n) {
            name = n;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
        }
    }

    static class SwapLocal implements ChuckInstr {

        int o1, o2;

        SwapLocal(int a, int b) {
            o1 = a;
            o2 = b;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer();
            int i1 = fp + o1, i2 = fp + o2;
            // Snapshot slot 1
            long d1 = s.mem.getData(i1);
            Object r1 = s.mem.getRef(i1);
            boolean isObj1 = s.mem.isObjectAt(i1);
            boolean isDbl1 = s.mem.isDoubleAt(i1);
            // Copy slot 2 → slot 1 (preserving type flags)
            if (s.mem.isObjectAt(i2)) {
                s.mem.setRef(i1, s.mem.getRef(i2));
            } else if (s.mem.isDoubleAt(i2)) {
                s.mem.setData(i1, Double.longBitsToDouble(s.mem.getData(i2)));
            } else {
                s.mem.setData(i1, s.mem.getData(i2));
            }
            // Copy snapshot (slot 1) → slot 2 (preserving type flags)
            if (isObj1) {
                s.mem.setRef(i2, r1);
            } else if (isDbl1) {
                s.mem.setData(i2, Double.longBitsToDouble(d1));
            } else {
                s.mem.setData(i2, d1);
            }
            // Push a value (the new value of slot 1) for any expression context
            if (s.mem.isObjectAt(i1)) {
                s.reg.pushObject(s.mem.getRef(i1));
            } else if (s.mem.isDoubleAt(i1)) {
                s.reg.push(Double.longBitsToDouble(s.mem.getData(i1)));
            } else {
                s.reg.push(s.mem.getData(i1));
            }
        }
    }

    static class LogicalNot implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            double v = s.reg.popAsDouble();
            s.reg.push(v == 0.0 ? 1 : 0);
        }
    }

    static class NegateAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            if (s.reg.isDouble(0)) {
                s.reg.push(-s.reg.popAsDouble());
            } else {
                s.reg.push(-s.reg.popLong());
            }
        }
    }

    static class TimesAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop();
                Object l = s.reg.pop();
                if (r instanceof ChuckDuration rd && l instanceof Number ln) {
                    s.reg.pushObject(new ChuckDuration(rd.samples() * ln.doubleValue()));
                } else if (l instanceof ChuckDuration ld && r instanceof Number rn) {
                    s.reg.pushObject(new ChuckDuration(ld.samples() * rn.doubleValue()));
                } else if (r instanceof ChuckDuration rd && l instanceof ChuckDuration ld) {
                    s.reg.pushObject(new ChuckDuration(ld.samples() * rd.samples()));
                } else {
                    s.reg.push(0L); // Default fallback
                }
            } else if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l * r);
            } else {
                long r = s.reg.popLong(), l = s.reg.popLong();
                s.reg.push(l * r);
            }
        }
    }

    static class DivideAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop();
                Object l = s.reg.pop();
                if (r instanceof ChuckDuration rd && l instanceof ChuckDuration ld) {
                    if (rd.samples() == 0) {
                        throw new RuntimeException("DivideByZero");
                    }
                    s.reg.push((double) ld.samples() / rd.samples());
                } else if (l instanceof ChuckDuration ld && r instanceof Number rn) {
                    if (rn.doubleValue() == 0) {
                        throw new RuntimeException("DivideByZero");
                    }
                    s.reg.pushObject(new ChuckDuration(ld.samples() / rn.doubleValue()));
                } else if (r instanceof ChuckDuration rd && l instanceof Number ln) {
                    // samples / dur -> float ratio
                    if (rd.samples() == 0) {
                        throw new RuntimeException("DivideByZero");
                    }
                    s.reg.push(ln.doubleValue() / rd.samples());
                } else {
                    s.reg.push(0.0);
                }
                return;
            }
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                if (r != 0) {
                    s.reg.push(l / r);
                } else {
                    throw new RuntimeException("DivideByZero");
                }
            } else {
                long r = s.reg.popAsLong(), l = s.reg.popAsLong();
                if (r != 0) {
                    s.reg.push((double) l / r);
                } else {
                    throw new RuntimeException("DivideByZero");
                }
            }
        }
    }

    static class ModuloAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                if (r != 0) {
                    s.reg.push(l % r);
                } else {
                    s.reg.push(0.0);
                }
            } else {
                long r = s.reg.popAsLong(), l = s.reg.popAsLong();
                if (r != 0) {
                    s.reg.push(l % r);
                } else {
                    s.reg.push(0L);
                }
            }
        }
    }

    static class BitwiseOrAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popLong(), l = s.reg.popLong();
            s.reg.push(l | r);
        }
    }

    static class BitwiseAndAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popLong(), l = s.reg.popLong();
            s.reg.push(l & r);
        }
    }

    static class NotEqualsAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l == r) {
                    s.reg.push(0);
                } else if (l == null || r == null) {
                    s.reg.push(1);
                } else {
                    s.reg.push(!l.toString().equals(r.toString()) ? 1 : 0);
                }
            } else {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l != r ? 1 : 0);
            }
        }
    }

    static class LogicalAnd implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
            s.reg.push((l != 0.0 && r != 0.0) ? 1 : 0);
        }
    }

    static class LogicalOr implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
            s.reg.push((l != 0.0 || r != 0.0) ? 1 : 0);
        }
    }

    static class CreateEventConjunction implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object r = s.reg.popObject();
            Object l = s.reg.popObject();
            ChuckEventConjunction conj = new ChuckEventConjunction();
            switch (l) {
                case ChuckEventConjunction lc -> {
                    for (ChuckEvent e : lc.getEvents()) {
                        conj.addEvent(e);
                    }
                }
                case ChuckEvent le ->
                    conj.addEvent(le);
                default -> {
                }
            }
            switch (r) {
                case ChuckEventConjunction rc -> {
                    for (ChuckEvent e : rc.getEvents()) {
                        conj.addEvent(e);
                    }
                }
                case ChuckEvent re ->
                    conj.addEvent(re);
                default -> {
                }
            }
            s.reg.pushObject(conj);
        }
    }

    static class CreateEventDisjunction implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object r = s.reg.popObject();
            Object l = s.reg.popObject();
            ChuckEventDisjunction disj = new ChuckEventDisjunction();
            switch (l) {
                case ChuckEventDisjunction ld -> {
                    for (ChuckEvent e : ld.getEvents()) {
                        disj.addEvent(e);
                    }
                }
                case ChuckEvent le ->
                    disj.addEvent(le);
                default -> {
                }
            }
            switch (r) {
                case ChuckEventDisjunction rd -> {
                    for (ChuckEvent e : rd.getEvents()) {
                        disj.addEvent(e);
                    }
                }
                case ChuckEvent re ->
                    disj.addEvent(re);
                default -> {
                }
            }
            s.reg.pushObject(disj);
        }
    }

    static class ConnectToDac implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            if (src instanceof org.chuck.audio.ChuckUGen ugen) {
                ugen.chuckTo(vm.getMultiChannelDac());
            }
        }
    }

    static class ConnectToBlackhole implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            connectAny(src, vm.blackhole, vm);
        }
    }

    static class ConnectToAdc implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object src = s.reg.peekObject(0);
            connectAny(src, vm.adc, vm);
        }
    }

    static class Jump implements ChuckInstr {

        int target;

        Jump(int t) {
            target = t;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.setPc(target - 1);
        }
    }

    static class JumpIfFalseAndPushFalse implements ChuckInstr {

        int target;

        JumpIfFalseAndPushFalse(int t) {
            target = t;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            if (s.reg.popAsDouble() == 0.0) {
                s.reg.push(0);
                s.setPc(target - 1);
            }
        }
    }

    static class JumpIfTrueAndPushTrue implements ChuckInstr {

        int target;

        JumpIfTrueAndPushTrue(int t) {
            target = t;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            if (s.reg.popAsDouble() != 0.0) {
                s.reg.push(1);
                s.setPc(target - 1);
            }
        }
    }

    static class JumpIfFalse implements ChuckInstr {

        int target;

        JumpIfFalse(int t) {
            target = t;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            if (s.reg.popAsDouble() == 0.0) {
                s.setPc(target - 1);
            }
        }
    }

    static class JumpIfTrue implements ChuckInstr {

        int target;

        JumpIfTrue(int t) {
            target = t;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            if (s.reg.popAsDouble() != 0.0) {
                s.setPc(target - 1);
            }
        }
    }

    static class PushInt implements ChuckInstr {

        long val;

        PushInt(long v) {
            val = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(val);
        }
    }

    static class PushNull implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(null);
        }
    }

    static class PushObjectOnly implements ChuckInstr {

        Object val;

        PushObjectOnly(Object v) {
            val = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(val);
        }
    }

    static class PushFloat implements ChuckInstr {

        double val;

        PushFloat(double v) {
            val = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(val);
        }
    }

    static class PushString implements ChuckInstr {

        String val;

        PushString(String v) {
            val = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(new ChuckString(val));
        }
    }

    static class PushNow implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(vm.getCurrentTime());
        }
    }

    static class PushDac implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.getMultiChannelDac());
        }
    }

    static class PushBlackhole implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.blackhole);
        }
    }

    static class PushAdc implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.adc);
        }
    }

    static class PushMe implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(s);
        }
    }

    static class PushMaybe implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(Math.random() < 0.5 ? 1L : 0L);
        }
    }

    static class PushMachine implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(new MachineObject());
        }
    }

    static class PushCherr implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.getGlobalObject("cherr"));
        }
    }

    static class PushChout implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.getGlobalObject("chout"));
        }
    }

    static class ChuckWriteIO implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object r = s.reg.pop();
            Object l = s.reg.pop();
            switch (l) {
                case FileIO fio -> {
                switch (r) {
                    case Long lv -> fio.write(lv);
                    case Double dv -> fio.write(dv);
                    default -> fio.write(String.valueOf(r));
                }
                    s.reg.pushObject(fio); // return target for chaining
                }
                case ChuckIO cio -> {
                    cio.write(r);
                    s.reg.pushObject(cio);
                }
                default ->
                    s.reg.pushObject(l);
            }
        }
    }

    static class Pop implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() > 0) {
                s.reg.popLong();
            }
        }
    }

    static class Dup implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            if (s.reg.isObject(0)) {
                s.reg.pushObject(s.reg.peekObject(0));
            } else if (s.reg.isDouble(0)) {
                s.reg.push(Double.longBitsToDouble(s.reg.peekLong(0)));
            } else {
                s.reg.push(s.reg.peekLong(0));
            }
        }
    }

    static class PeekStack implements ChuckInstr {

        int depth;

        PeekStack(int d) {
            depth = d;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(depth)) {
                s.reg.pushObject(s.reg.peekObject(depth));
            } else if (s.reg.isDouble(depth)) {
                s.reg.push(s.reg.peekAsDouble(depth));
            } else {
                s.reg.push(s.reg.peekLong(depth));
            }
        }
    }

    public static class LoadLocal implements ChuckInstr {

        int offset;

        public LoadLocal(int o) {
            offset = o;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            int idx = s.getFramePointer() + offset;
            if (s.mem.isObjectAt(idx)) {
                Object obj = s.mem.getRef(idx);
                s.reg.pushObject(obj);
            } else if (s.mem.isDoubleAt(idx)) {
                s.reg.push(Double.longBitsToDouble(s.mem.getData(idx)));
            } else {
                s.reg.push(s.mem.getData(idx));
            }
        }
    }

    public static class StoreLocal implements ChuckInstr {

        int offset;

        public StoreLocal(int o) {
            offset = o;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
            int fp = s.getFramePointer();
            int idx = fp + offset;

            // Polymorphic: if top of stack is FileIO, read from it instead of popping it
            if (s.reg.peekObject(0) instanceof FileIO fio) {
                if (s.mem.isObjectAt(idx)) {
                    s.mem.setRef(idx, new ChuckString(fio.readString()));
                } else if (s.mem.isDoubleAt(idx)) {
                    double val = fio.readFloat();
                    s.mem.setData(idx, val);
                } else {
                    long val = fio.readInt();
                    s.mem.setData(idx, val);
                }
                return; // Leave fio on stack for chaining
            }

            if (s.reg.isObject(0)) {
                Object obj = s.reg.popObject();
                s.mem.setRef(idx, obj);
                if (obj instanceof org.chuck.audio.ChuckUGen u) {
                    s.registerUGen(u);
                }
                if (obj instanceof AutoCloseable ac) {
                    s.registerCloseable(ac);
                }
                s.reg.pushObject(obj);
            } else if (s.reg.isDouble(0)) {
                double val = s.reg.popAsDouble();
                s.mem.setData(idx, val);
                s.reg.push(val);
            } else {
                long val = s.reg.popLong();
                s.mem.setData(idx, val);
                s.reg.push(val);
            }
            if (idx >= s.mem.getSp()) {
                s.mem.setSp(idx + 1);
            }
        }
    }

    static class GetGlobalObjectOrInt implements ChuckInstr {

        String name;

        GetGlobalObjectOrInt(String n) {
            name = n;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (vm.isGlobalObject(name)) {
                Object obj = vm.getGlobalObject(name);
                s.reg.pushObject(obj);
            } else if (vm.isGlobalDouble(name)) {
                s.reg.push(Double.longBitsToDouble(vm.getGlobalInt(name)));
            } else {
                s.reg.push(vm.getGlobalInt(name));
            }
        }
    }

    static class SetGlobalObjectOnly implements ChuckInstr {

        String name;

        SetGlobalObjectOnly(String n) {
            name = n;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }
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

        String name;

        SetGlobalObjectOrInt(String n) {
            name = n;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) {
                return;
            }

            // Polymorphic: if top of stack is FileIO, read from it
            if (s.reg.peekObject(0) instanceof FileIO fio) {
                if (vm.isGlobalObject(name)) {
                    vm.setGlobalObject(name, new ChuckString(fio.readString()));
                } else if (vm.isGlobalDouble(name)) {
                    double val = fio.readFloat();
                    vm.setGlobalFloat(name, val);
                } else {
                    long val = fio.readInt();
                    vm.setGlobalInt(name, val);
                }
                return; // Leave fio on stack for chaining
            }

            if (s.reg.isObject(0)) {
                Object obj = s.reg.popObject();
                vm.setGlobalObject(name, obj);
                if (obj instanceof org.chuck.audio.ChuckUGen u) {
                    s.registerUGen(u);
                }
                if (obj instanceof AutoCloseable ac) {
                    s.registerCloseable(ac);
                }
                s.reg.pushObject(obj);
            } else if (s.reg.isDouble(0)) {
                double val = s.reg.popAsDouble();
                vm.setGlobalFloat(name, val);
                s.reg.push(val);
            } else {
                long val = s.reg.popLong();
                vm.setGlobalInt(name, val);
                s.reg.push(val);
            }
        }
    }

    static class AddAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l instanceof ChuckDuration ld && r instanceof ChuckDuration rd) {
                    s.reg.pushObject(new ChuckDuration(ld.samples() + rd.samples()));
                } else {
                    String ls = (l instanceof Double d) ? String.format("%.6f", d) : String.valueOf(l);
                    String rs = (r instanceof Double d) ? String.format("%.6f", d) : String.valueOf(r);
                    s.reg.pushObject(new ChuckString(ls + rs));
                }
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

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l instanceof ChuckDuration ld && r instanceof ChuckDuration rd) {
                    s.reg.pushObject(new ChuckDuration(ld.samples() - rd.samples()));
                } else {
                    s.reg.push(0.0);
                }
                return;
            }
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l - r);
            } else {
                long r = s.reg.popAsLong(), l = s.reg.popAsLong();
                s.reg.push(l - r);
            }
        }
    }

    static class LessThanAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                s.reg.push(l.toString().compareTo(r.toString()) < 0 ? 1 : 0);
            } else {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l < r ? 1 : 0);
            }
        }
    }

    static class GreaterThanAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                s.reg.push(l.toString().compareTo(r.toString()) > 0 ? 1 : 0);
            } else {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l > r ? 1 : 0);
            }
        }
    }

    static class LessOrEqualAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                s.reg.push(l.toString().compareTo(r.toString()) <= 0 ? 1 : 0);
            } else {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l <= r ? 1 : 0);
            }
        }
    }

    static class GreaterOrEqualAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                s.reg.push(l.toString().compareTo(r.toString()) >= 0 ? 1 : 0);
            } else {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l >= r ? 1 : 0);
            }
        }
    }

    static class EqualsAny implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isObject(0) || s.reg.isObject(1)) {
                Object r = s.reg.pop(), l = s.reg.pop();
                if (l == r) {
                    s.reg.push(1);
                } else {
                    switch (l) {
                        case null ->
                            s.reg.push(r == null ? 1 : 0);
                        default -> {
                            switch (r) {
                                case null ->
                                    s.reg.push(0);
                                default ->
                                    s.reg.push(l.toString().equals(r.toString()) ? 1 : 0);
                            }
                        }
                    }
                }
            } else {
                double r = s.reg.popAsDouble(), l = s.reg.popAsDouble();
                s.reg.push(l == r ? 1 : 0);
            }
        }
    }

    static class Yield implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            vm.advanceTime(0);
        }
    }

    static class ChuckUnchuck implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object dest = s.reg.popObject(), src = s.reg.popObject();
            if (src instanceof ChuckUGen su && dest instanceof ChuckUGen du) {
                su.unchuck(du);
            }
            s.reg.pushObject(src);
        }
    }

    static class WriteIO implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() < 2) {
                return;
            }
            Object dest = s.reg.popObject();
            Object val = s.reg.pop();

            switch (dest) {
                case FileIO fio -> {
                switch (val) {
                    case Double d -> fio.write(d);
                    case Long l -> fio.write(l);
                    default -> fio.write(String.valueOf(val));
                }
                }
                case ChuckIO cio -> {
                switch (val) {
                    case Double d -> cio.write(d.doubleValue());
                    case Long l -> cio.write(l.longValue());
                    default -> cio.write(String.valueOf(val));
                }
                }
                default -> {
                    String out;
                    switch (val) {
                        case Double d -> {
                            out = java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
                            if (out.endsWith(".0")) {
                                out = out.substring(0, out.length() - 2);
                            }
                        }
                        case ChuckString cs ->
                            out = cs.toString();
                        default ->
                            out = String.valueOf(val);
                    }
                    vm.print(out);
                }
            }
            s.reg.pushObject(dest);
        }
    }

    static class ArrayZero implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = s.reg.popObject();
            if (obj instanceof ChuckArray arr) {
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.isObjectAt(i)) {
                        Object elem = arr.getObject(i);
                        if (elem instanceof ChuckArray) {
                            arr.setObject(i, elem); // keep reference
                        } else {
                            arr.setObject(i, null);
                        }
                    } else if (arr.isDoubleAt(i)) {
                        arr.setFloat(i, 0.0);
                    } else {
                        arr.setInt(i, 0);
                    }
                }
                s.reg.pushObject(arr);
            } else {
                s.reg.push(0L);
            }
        }
    }

    static class GetArrayInt implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            long idx = s.reg.popLong();
            Object obj = s.reg.popObject();
            if (obj instanceof ChuckArray arr) {
                if (arr.isObjectAt((int) idx)) {
                    s.reg.pushObject(arr.getObject((int) idx));
                } else if (arr.isDoubleAt((int) idx)) {
                    s.reg.push(arr.getFloat((int) idx));
                } else {
                    s.reg.push(arr.getInt((int) idx));
                }
            } else {
                s.reg.push(0L);
            }
        }
    }

    static class SetArrayInt implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            long idx = s.reg.popLong();
            ChuckArray arr = (ChuckArray) s.reg.popObject();

            boolean isObj = s.reg.isObject(0), isDbl = s.reg.isDouble(0);
            Object valObj = isObj ? s.reg.popObject() : null;
            double valDbl = isDbl ? s.reg.popAsDouble() : 0;
            long valLong = (!isObj && !isDbl) ? s.reg.popLong() : 0;

            if (isObj) {
                arr.setObject((int) idx, valObj);
            } else if (isDbl) {
                arr.setFloat((int) idx, valDbl);
            } else {
                arr.setInt((int) idx, valLong);
            }

            if (isObj) {
                s.reg.pushObject(valObj);
            } else if (isDbl) {
                s.reg.push(valDbl);
            } else {
                s.reg.push(valLong);
            }
        }
    }

    static class ShiftLeftOrAppend implements ChuckInstr {

        final String elemType;

        ShiftLeftOrAppend(String t) {
            this.elemType = t;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            boolean rhsIsDouble = s.reg.isDouble(0);
            boolean rhsIsObj = s.reg.isObject(0);
            Object rhsObj = rhsIsObj ? s.reg.popObject() : null;
            double rhsDbl = rhsIsDouble ? s.reg.popAsDouble() : 0;
            long rhsLong = (!rhsIsDouble && !rhsIsObj) ? s.reg.popLong() : 0L;

            if (s.reg.isObject(0)) {
                Object lhs = s.reg.popObject();
                if (lhs instanceof ChuckArray arr) {
                    boolean forceFloat = "float".equals(elemType) || "double".equals(elemType);
                    // Also propagate float if existing elements are doubles
                    if (!forceFloat && arr.size() > 0 && arr.isDoubleAt(arr.size() - 1)) {
                        forceFloat = true;
                    }
                    if (rhsIsObj) {
                        arr.append(rhsObj);
                    } else if (rhsIsDouble) {
                        arr.append(rhsDbl);
                    } else if (forceFloat) {
                        arr.append((double) rhsLong);
                    } else {
                        arr.append(rhsLong);
                    }
                    s.reg.pushObject(arr);
                } else {
                    // Not an array: push lhs back and ignore
                    s.reg.pushObject(lhs);
                }
            } else {
                // Bitwise left shift on integers
                long lhsLong = s.reg.popLong();
                s.reg.push(lhsLong << rhsLong);
            }
        }
    }

    static class EnsureFloat implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (!s.reg.isDouble(0) && !s.reg.isObject(0)) {
                long v = s.reg.popLong();
                s.reg.push((double) v);
            }
        }
    }

    static class NewArray implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(new ChuckArray(ChuckType.ARRAY, (int) s.reg.popLong()));
        }
    }

    static class NewArrayFromStack implements ChuckInstr {

        int size;

        NewArrayFromStack(int sz) {
            size = sz;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
            for (int i = size - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) {
                    Object obj = s.reg.popObject();
                    arr.setObject(i, obj);
                } else if (s.reg.isDouble(0)) {
                    double d = s.reg.popAsDouble();
                    arr.setFloat(i, d);
                } else {
                    long l = s.reg.popLong();
                    arr.setInt(i, l);
                }
            }
            s.reg.pushObject(arr);
        }
    }

    static class CreateDuration implements ChuckInstr {

        String unit;

        CreateDuration(String u) {
            unit = u;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            double v = s.reg.popAsDouble();
            double smp = switch (unit) {
                case "ms" ->
                    v * vm.getSampleRate() / 1000.0;
                case "second" ->
                    v * vm.getSampleRate();
                case "minute" ->
                    v * vm.getSampleRate() * 60.0;
                case "hour" ->
                    v * vm.getSampleRate() * 3600.0;
                case "day" ->
                    v * vm.getSampleRate() * 3600.0 * 24.0;
                case "week" ->
                    v * vm.getSampleRate() * 3600.0 * 24.0 * 7.0;
                case "samp" ->
                    v;
                default ->
                    0.0;
            };
            s.reg.pushObject(new ChuckDuration(smp));
        }
    }

    static class StdFunc implements ChuckInstr {

        String fn;
        int argc;

        StdFunc(String f, int a) {
            fn = f;
            argc = a;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            switch (fn) {
                case "mtof" ->
                    s.reg.push(Std.mtof(s.reg.popAsDouble()));
                case "ftom" ->
                    s.reg.push(Std.ftom(s.reg.popAsDouble()));
                case "powtodb" ->
                    s.reg.push(Std.powtodb(s.reg.popAsDouble()));
                case "rmstodb" ->
                    s.reg.push(Std.rmstodb(s.reg.popAsDouble()));
                case "dbtopow" ->
                    s.reg.push(Std.dbtopow(s.reg.popAsDouble()));
                case "dbtorms" ->
                    s.reg.push(Std.dbtorms(s.reg.popAsDouble()));
                case "dbtolin" ->
                    s.reg.push(Std.dbtolin(s.reg.popAsDouble()));
                case "lintodb" ->
                    s.reg.push(Std.lintodb(s.reg.popAsDouble()));
                case "abs" ->
                    s.reg.push(Std.abs(s.reg.popLong()));
                case "fabs" ->
                    s.reg.push(Std.fabs(s.reg.popAsDouble()));
                case "sgn" ->
                    s.reg.push(Std.sgn(s.reg.popAsDouble()));
                case "rand2" -> {
                    long hi = s.reg.popLong();
                    long lo = s.reg.popLong();
                    s.reg.push(Std.rand2(lo, hi));
                }
                case "rand2f" -> {
                    double hi = s.reg.popAsDouble();
                    double lo = s.reg.popAsDouble();
                    s.reg.push(Std.rand2f(lo, hi));
                }
                case "clamp" -> {
                    long hi = s.reg.popLong();
                    long lo = s.reg.popLong();
                    long val = s.reg.popLong();
                    s.reg.push(Std.clamp(val, lo, hi));
                }
                case "clampf" -> {
                    double hi = s.reg.popAsDouble();
                    double lo = s.reg.popAsDouble();
                    double val = s.reg.popAsDouble();
                    s.reg.push(Std.clampf(val, lo, hi));
                }
                case "scalef" -> {
                    double dHi = s.reg.popAsDouble();
                    double dLo = s.reg.popAsDouble();
                    double sHi = s.reg.popAsDouble();
                    double sLo = s.reg.popAsDouble();
                    double v = s.reg.popAsDouble();
                    s.reg.push(Std.scalef(v, sLo, sHi, dLo, dHi));
                }
                case "atoi" ->
                    s.reg.push(Std.atoi(s.reg.popObject().toString()));
                case "atof" ->
                    s.reg.push(Std.atof(s.reg.popObject().toString()));
                case "itoa" ->
                    s.reg.pushObject(new org.chuck.core.ChuckString(Std.itoa(s.reg.popLong())));
                case "ftoi" ->
                    s.reg.push(Std.ftoi(s.reg.popAsDouble()));
                case "systemTime" ->
                    s.reg.push(Std.systemTime());
                default -> {
                }
            }
        }
    }

    static class MathFunc implements ChuckInstr {

        String fn;

        MathFunc(String f) {
            fn = f;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            switch (fn) {
                case "equal" -> {
                    double b = s.reg.popAsDouble(), a = s.reg.popAsDouble();
                    s.reg.push(Math.abs(a - b) < 1e-6 ? 1 : 0);
                    return;
                }
                case "euclidean" -> {
                    Object b = s.reg.pop(), a = s.reg.pop();
                    if (a instanceof ChuckArray arrA && b instanceof ChuckArray arrB) {
                        double sum = 0;
                        int size = Math.min(arrA.size(), arrB.size());
                        for (int i = 0; i < size; i++) {
                            double da = arrA.getNumeric(i);
                            double db = arrB.getNumeric(i);
                            sum += (da - db) * (da - db);
                        }
                        s.reg.push(Math.sqrt(sum));
                    } else {
                        s.reg.push(0.0);
                    }
                    return;
                }
                case "isinf" -> {
                    s.reg.push(Double.isInfinite(s.reg.popAsDouble()) ? 1L : 0L);
                    return;
                }
                case "isnan" -> {
                    s.reg.push(Double.isNaN(s.reg.popAsDouble()) ? 1L : 0L);
                    return;
                }
                case "pow" -> {
                    double exp2 = s.reg.popAsDouble(), base2 = s.reg.popAsDouble();
                    s.reg.push(Math.pow(base2, exp2));
                    return;
                }
                case "srandom" -> {
                    long seed = (long) s.reg.popAsDouble();
                    Std.rng = new java.util.Random(seed);
                    s.reg.push(0.0);
                    return;
                }
                default -> {
                }
            }

            double v = s.reg.popAsDouble();
            s.reg.push(switch (fn) {
                case "sin" ->
                    Math.sin(v);
                case "cos" ->
                    Math.cos(v);
                case "sqrt" ->
                    Math.sqrt(v);
                case "abs" ->
                    Math.abs(v);
                case "floor" ->
                    Math.floor(v);
                case "ceil" ->
                    Math.ceil(v);
                case "log" ->
                    Math.log(v);
                case "log2" ->
                    Math.log(v) / Math.log(2);
                case "log10" ->
                    Math.log10(v);
                case "exp" ->
                    Math.exp(v);
                case "round" ->
                    (double) Math.round(v);
                case "trunc" ->
                    (double) (long) v;
                case "dbtolin", "dbtopow" ->
                    Math.pow(10.0, v / 20.0);
                case "lintodb", "powtodb" ->
                    (v <= 0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(v));
                case "dbtorms" ->
                    Math.sqrt(Math.pow(10.0, v / 10.0));
                case "rmstodb" ->
                    (v <= 0 ? Double.NEGATIVE_INFINITY : 10.0 * Math.log10(v * v));
                case "tan" ->
                    Math.tan(v);
                case "asin" ->
                    Math.asin(v);
                case "acos" ->
                    Math.acos(v);
                case "atan" ->
                    Math.atan(v);
                case "sinh" ->
                    Math.sinh(v);
                case "cosh" ->
                    Math.cosh(v);
                case "tanh" ->
                    Math.tanh(v);
                default ->
                    v;
            });
        }
    }

    static class MathRandom implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(Math.random());
        }
    }

    static class MathHelp implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
        }
    }

    static class Spork implements ChuckInstr {

        ChuckCode t;
        int a;

        Spork(ChuckCode target, int argCount) {
            t = target;
            a = argCount;
            if (t.getNumInstructions() == 0 || !(t.getInstruction(0) instanceof MoveArgs)) {
                t.prependInstruction(new MoveArgs(a));
            }
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckShred ns = new ChuckShred(t);
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    if (s.reg.isObject(0)) {
                        args[i] = s.reg.popObject();
                    } else if (s.reg.isDouble(0)) {
                        args[i] = s.reg.popAsDouble();
                    } else {
                        args[i] = s.reg.popLong();
                    }
                }
            }
            for (Object arg : args) {
                switch (arg) {
                    case ChuckObject co -> ns.reg.pushObject(co);
                    case Double d -> ns.reg.push(d);
                    case Long l -> ns.reg.push(l);
                    case null, default -> ns.reg.pushObject(arg);
                }
            }
            ns.setParentShred(s);
            vm.spork(ns);
            s.reg.pushObject(ns);
        }
    }

    static class SporkMethod implements ChuckInstr {

        String mName;
        int a;

        SporkMethod(String m, int argCount) {
            mName = m;
            a = argCount;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (s.reg.isDouble(0)) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            Object obj = s.reg.popObject();
            if (!(obj instanceof UserObject uo)) {
                s.reg.pushObject(null);
                return;
            }

            ChuckCode target = uo.methods.get(mName);
            boolean isStatic = false;
            if (target == null) {
                UserClassDescriptor d = vm.getUserClass(uo.className);
                if (d != null) {
                    target = d.staticMethods().get(mName + ":" + a);
                    isStatic = (target != null);
                }
            }
            if (target == null) {
                s.reg.pushObject(null);
                return;
            }
            if (target.getNumInstructions() == 0 || !(target.getInstruction(0) instanceof MoveArgs)) {
                target.prependInstruction(new MoveArgs(a));
            }
            ChuckShred ns = new ChuckShred(target);
            for (Object arg : args) {
                switch (arg) {
                    case ChuckObject co -> ns.reg.pushObject(co);
                    case Double d -> ns.reg.push(d);
                    case Long l -> ns.reg.push(l);
                    case null, default -> ns.reg.pushObject(arg);
                }
            }
            if (!isStatic) {
                ns.thisStack.push(uo);
            }
            ns.setParentShred(s);
            vm.spork(ns);
            s.reg.pushObject(ns);
        }
    }

    static class ReturnMethod implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer();
            if (fp < 4) {
                s.abort();
                return;
            }
            int savedSp = (int) s.mem.getData(fp - 1), savedFp = (int) s.mem.getData(fp - 2), savedPc = (int) s.mem.getData(fp - 3);
            ChuckCode savedCode = (ChuckCode) s.mem.getRef(fp - 4);
            long retP = 0;
            Object retO = null;
            boolean retD = false;
            if (s.reg.getSp() > savedSp && s.reg.getSp() > 0) {
                retD = s.reg.isDouble(0);
                retP = s.reg.peekLong(0);
                retO = s.reg.peekObject(0);
            }
            boolean hasReturn = s.reg.getSp() > savedSp;
            s.reg.setSp(savedSp);
            s.setPc(savedPc);
            s.setCode(savedCode);
            UserObject uo = s.thisStack.isEmpty() ? null : s.thisStack.pop();
            boolean isCtor = (uo != null && savedCode != null && uo.className.equals(savedCode.getName()));
            s.setFramePointer(savedFp);
            s.mem.setSp(fp - 4);
            if (hasReturn) {
                if (retO != null) {
                    s.reg.pushObject(retO);
                } else if (retD) {
                    s.reg.push(Double.longBitsToDouble(retP));
                } else {
                    s.reg.push(retP);
                }
            } else if (isCtor) {
                s.reg.pushObject(uo);
            }
        }
    }

    static class GetFieldByName implements ChuckInstr {

        String n;

        GetFieldByName(String v) {
            n = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object obj = s.reg.popObject();
            if (obj == null) {
                s.reg.push(0L);
                return;
            }
            if (n.equals("size") && obj instanceof ChuckArray arr) {
                s.reg.push((long) arr.size());
            } else if (obj instanceof UserObject uo) {
                ChuckObject fo = uo.getObjectField(n);
                if (fo != null) {
                    s.reg.pushObject(fo);
                } else if (uo.isFloatField(n)) {
                    s.reg.push(uo.getFloatField(n));
                } else {
                    s.reg.push(uo.getPrimitiveField(n));
                }
            } else if (obj instanceof ChuckUGen ugen) {
                if (n.equals("last")) {
                    s.reg.push((double) ugen.getLastOut());
                } else if (n.equals("left") && obj instanceof StereoUGen s_ugen) {
                    s.reg.pushObject(s_ugen.left());
                } else if (n.equals("right") && obj instanceof StereoUGen s_ugen) {
                    s.reg.pushObject(s_ugen.right());
                } else {
                    s.reg.push(0L);
                }
            } else {
                // Reflection fallback for public fields (e.g. MidiMsg.data1)
                try {
                    java.lang.reflect.Field f = obj.getClass().getField(n);
                    Object val = f.get(obj);
                    switch (val) {
                        case Number num -> s.reg.push(num.longValue());
                        case ChuckObject co -> s.reg.pushObject(co);
                        default -> s.reg.push(0L);
                    }
                } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException ignored) {
                    s.reg.push(0L);
                }
            }
        }
    }

    static class GetUserField implements ChuckInstr {

        String n;

        GetUserField(String v) {
            n = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek();
            if (uo == null) {
                s.reg.push(0L);
                return;
            }
            ChuckObject obj = uo.getObjectField(n);
            if (obj != null) {
                s.reg.pushObject(obj);
            } else if (uo.isFloatField(n)) {
                s.reg.push(uo.getFloatField(n));
            } else {
                s.reg.push(uo.getPrimitiveField(n));
            }
        }
    }

    static class SetUserField implements ChuckInstr {

        String n;

        SetUserField(String v) {
            n = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek();
            if (uo == null) {
                return;
            }
            if (s.reg.isObject(0)) {
                ChuckObject val = (ChuckObject) s.reg.popObject();
                uo.setObjectField(n, val);
                s.reg.pushObject(val);
            } else if (uo.isFloatField(n)) {
                double val = s.reg.popAsDouble();
                uo.setFloatField(n, val);
                s.reg.push(val);
            } else {
                long val = s.reg.popLong();
                uo.setPrimitiveField(n, val);
                s.reg.push(val);
            }
        }
    }

    static class InstantiateSetAndPushLocal implements ChuckInstr {

        String t;
        int o, a;
        boolean r, ar;
        Map<String, UserClassDescriptor> rm;

        InstantiateSetAndPushLocal(String type, int off, int arg, boolean ref, boolean arr, Map<String, UserClassDescriptor> m) {
            t = type;
            o = off;
            a = arg;
            r = ref;
            ar = arr;
            rm = m;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (s.reg.isDouble(0)) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            int fp = s.getFramePointer();
            if (r) {
                s.mem.setRef(fp + o, null);
                s.reg.pushObject(null);
                return;
            }
            Object obj;
            if (ar) {
                int sz = ((Number) args[0]).intValue();
                if (sz < 0) {
                    obj = switch (t) {
                        case "vec2" ->
                            new ChuckArray(ChuckType.ARRAY, 2);
                        case "vec3" ->
                            new ChuckArray(ChuckType.ARRAY, 3);
                        case "vec4" ->
                            new ChuckArray(ChuckType.ARRAY, 4);
                        case "complex", "polar" ->
                            new ChuckArray(ChuckType.ARRAY, 2);
                        default ->
                            null;
                    };
                } else if (a > 1) {
                    long[] dims = new long[a];
                    for (int di = 0; di < a; di++) {
                        dims[di] = ((Number) args[di]).longValue();
                    }
                    obj = buildMultiDimArray(dims, 0, t, vm, s, rm);
                } else {
                    ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
                    for (int i = 0; i < sz; i++) {
                        ChuckObject elem = instantiateType(t, 0, null, vm.getSampleRate(), vm, s, rm);
                        if (elem != null) {
                            arr.setObject(i, elem);
                            if (elem instanceof ChuckUGen u) {
                                s.registerUGen(u);
                            }
                        }
                    }
                    obj = arr;
                }
            } else {
                obj = instantiateType(t, a, args, vm.getSampleRate(), vm, s, rm);
            }

            if (obj instanceof ChuckObject co) {
                s.mem.setRef(fp + o, co);
                if (co instanceof ChuckUGen u) {
                    s.registerUGen(u);
                }
                if (co instanceof AutoCloseable ac) {
                    s.registerCloseable(ac);
                }
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
            if (fp + o >= s.mem.getSp()) {
                s.mem.setSp(fp + o + 1);
            }
        }
    }

    static class InstantiateSetAndPushGlobal implements ChuckInstr {

        String t, n;
        int a;
        boolean r, ar;
        Map<String, UserClassDescriptor> rm;

        InstantiateSetAndPushGlobal(String type, String name, int arg, boolean ref, boolean arr, Map<String, UserClassDescriptor> m) {
            t = type;
            n = name;
            a = arg;
            r = ref;
            ar = arr;
            rm = m;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (s.reg.isDouble(0)) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            if (r) {
                vm.setGlobalObject(n, null);
                s.reg.pushObject(null);
                return;
            }
            // If global already exists, reuse it (or throw on type mismatch)
            if (vm.isGlobalObject(n)) {
                Object existing = vm.getGlobalObject(n);
                if (existing != null) {
                    String existingType = existing instanceof UserObject uo2 ? uo2.className : existing.getClass().getSimpleName();
                    if (!existingType.equals(t) && !t.equals("string") && !t.equals("int") && !t.equals("float")) {
                        throw new RuntimeException(n + "': has different type '" + existingType + "' than already existing global Object of the same name");
                    }
                    if (existing instanceof org.chuck.audio.ChuckUGen u) {
                        s.registerUGen(u);
                    }
                    if (existing instanceof AutoCloseable ac) {
                        s.registerCloseable(ac);
                    }
                    s.reg.pushObject(existing);
                    return;
                }
            }
            Object obj;
            if (ar) {
                int sz = ((Number) args[0]).intValue();
                if (sz < 0) {
                    obj = switch (t) {
                        case "vec2" ->
                            new ChuckArray(ChuckType.ARRAY, 2);
                        case "vec3" ->
                            new ChuckArray(ChuckType.ARRAY, 3);
                        case "vec4" ->
                            new ChuckArray(ChuckType.ARRAY, 4);
                        case "complex", "polar" ->
                            new ChuckArray(ChuckType.ARRAY, 2);
                        default ->
                            null;
                    };
                } else if (a > 1) {
                    long[] dims = new long[a];
                    for (int di = 0; di < a; di++) {
                        dims[di] = ((Number) args[di]).longValue();
                    }
                    obj = buildMultiDimArray(dims, 0, t, vm, s, rm);
                } else {
                    ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
                    for (int i = 0; i < sz; i++) {
                        ChuckObject elem = instantiateType(t, 0, null, vm.getSampleRate(), vm, s, rm);
                        if (elem != null) {
                            arr.setObject(i, elem);
                            if (elem instanceof ChuckUGen u) {
                                s.registerUGen(u);
                            }
                        }
                    }
                    obj = arr;
                }
            } else {
                obj = instantiateType(t, a, args, vm.getSampleRate(), vm, s, rm);
            }

            if (obj instanceof ChuckObject co) {
                vm.setGlobalObject(n, co);
                if (co instanceof ChuckUGen u) {
                    s.registerUGen(u);
                }
                if (co instanceof AutoCloseable ac) {
                    s.registerCloseable(ac);
                }
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

    private static ChuckArray buildMultiDimArray(long[] dims, int dimIdx, String elemType, ChuckVM vm, ChuckShred s, Map<String, UserClassDescriptor> rm) {
        int sz = (int) dims[dimIdx];
        ChuckArray arr = new ChuckArray(ChuckType.ARRAY, sz);
        if (dimIdx + 1 < dims.length) {
            for (int i = 0; i < sz; i++) {
                arr.setObject(i, buildMultiDimArray(dims, dimIdx + 1, elemType, vm, s, rm));
            }
        } else {
            for (int i = 0; i < sz; i++) {
                ChuckObject elem = instantiateType(elemType, 0, null, vm.getSampleRate(), vm, s, rm);
                if (elem != null) {
                    arr.setObject(i, elem);
                    if (elem instanceof ChuckUGen u) {
                        s.registerUGen(u);
                    }
                }
            }
        }
        return arr;
    }

    private static Object connectAny(Object src, Object dest, ChuckVM vm) {
        return switch (src) {
            case ChuckUGen su when dest instanceof ChuckUGen du -> {
                if (su.getNumOutputs() > 1 && du.getNumInputs() == 1) {
                    for (int i = 0; i < su.getNumOutputs(); i++) {
                        su.getOutputChannel(i).chuckTo(du);
                    }
                } else if (su.getNumOutputs() > 1 && du.getNumInputs() > 1) {
                    int len = Math.max(su.getNumOutputs(), du.getNumInputs());
                    for (int i = 0; i < len; i++) {
                        su.getOutputChannel(i % su.getNumOutputs()).chuckTo(du.getInputChannel(i % du.getNumInputs()));
                    }
                } else {
                    su.chuckTo(du);
                }
                yield dest;
            }
            case ChuckArray sa when dest instanceof ChuckUGen du -> {
                int len = Math.max(sa.size(), du.getNumInputs());
                if (sa.size() > 0) {
                    for (int i = 0; i < len; i++) {
                        Object e = sa.getObject(i % sa.size());
                        if (e instanceof ChuckUGen su2) {
                            su2.chuckTo(du.getInputChannel(i % du.getNumInputs()));
                        }
                    }
                }
                yield dest;
            }
            case ChuckUGen su when dest instanceof ChuckArray da -> {
                int len = Math.max(su.getNumOutputs(), da.size());
                if (da.size() > 0) {
                    for (int i = 0; i < len; i++) {
                        Object e = da.getObject(i % da.size());
                        if (e instanceof ChuckUGen du2) {
                            su.getOutputChannel(i % su.getNumOutputs()).chuckTo(du2);
                        }
                    }
                }
                yield dest;
            }
            case ChuckArray sa when dest instanceof ChuckArray da -> {
                if (sa.size() > 0 && da.size() > 0) {
                    for (int i = 0; i < Math.min(sa.size(), da.size()); i++) {
                        if (sa.isObjectAt(i)) {
                            da.setObject(i, sa.getObject(i));
                        } else if (sa.isDoubleAt(i)) {
                            da.setFloat(i, sa.getFloat(i));
                        } else {
                            da.setInt(i, sa.getInt(i));
                        }
                    }
                }
                yield dest;
            }
            default ->
                dest;
        };
    }

    static boolean extendsEvent(String t, Map<String, UserClassDescriptor> rm) {
        for (int depth = 0; depth < 16 && t != null; depth++) {
            if ("Event".equals(t)) {
                return true;
            }
            UserClassDescriptor d = rm.get(t);
            if (d == null) {
                return false;
            }
            t = d.parentName();
        }
        return false;
    }

    static ChuckCode findMethod(String className, String methodName, Map<String, UserClassDescriptor> rm, ChuckVM vm) {
        String t = className;
        for (int depth = 0; depth < 16 && t != null; depth++) {
            UserClassDescriptor d = (rm != null && rm.containsKey(t)) ? rm.get(t) : (vm != null ? vm.getUserClass(t) : null);
            if (d == null) {
                break;
            }
            if (d.methods().containsKey(methodName)) {
                return d.methods().get(methodName);
            }
            t = d.parentName();
        }
        return null;
    }

    static ChuckObject instantiateType(String t, int argc, Object[] args, float sr, ChuckVM vm, ChuckShred s, Map<String, UserClassDescriptor> rm) {
        if (t == null) {
            return null;
        }
        UserClassDescriptor d = (rm != null && rm.containsKey(t)) ? rm.get(t) : (vm != null ? vm.getUserClass(t) : null);
        if (d != null) {
            UserObject uo = new UserObject(t, d.fields(), d.methods(), extendsEvent(t, rm));
            uo.setTickCode(findMethod(t, "tick:1", rm, vm), s, vm);
            // Execute pre-ctor body and constructor for each level, parent-first
            if (s != null && vm != null) {
                // Build hierarchy root-first (e.g. [Base, Parent, Child])
                java.util.List<String> hierarchy = new java.util.ArrayList<>();
                for (String cls = t; cls != null; ) {
                    UserClassDescriptor desc = (rm != null && rm.containsKey(cls)) ? rm.get(cls) : vm.getUserClass(cls);
                    if (desc == null) break;
                    hierarchy.add(0, cls);
                    cls = desc.parentName();
                }
                for (String cls : hierarchy) {
                    UserClassDescriptor desc = (rm != null && rm.containsKey(cls)) ? rm.get(cls) : vm.getUserClass(cls);
                    if (desc == null) continue;
                    // Run pre-ctor body (no ReturnMethod at end, so pop thisStack manually)
                    if (desc.preCtorCode() != null) {
                        s.thisStack.push(uo);
                        s.executeSynchronous(vm, desc.preCtorCode());
                        s.thisStack.pop();
                    }
                    // Call constructor method (e.g. "Parent:0") if it exists
                    ChuckCode ctorCode = desc.methods().get(cls + ":0");
                    if (ctorCode != null) {
                        s.thisStack.push(uo); // ReturnMethod will pop it
                        s.executeSynchronous(vm, ctorCode);
                        // Note: ReturnMethod pops thisStack; executeSynchronous restores everything else
                    }
                }
            }
            return uo;
        }

        // 1. Try UGenRegistry (New centralized factory)
        ChuckUGen ugen = UGenRegistry.instantiate(t, sr, args);
        if (ugen != null) {
            return ugen;
        }

        // 2. Try Chugin Loader
        ChuckObject chugin = ChuginLoader.instantiateChugin(t, sr, vm);
        if (chugin != null) {
            return chugin;
        }

        // 3. Fallback switch for core non-UGen types
        return switch (t) {
            case "string" ->
                new ChuckString("");
            case "vec2" ->
                new ChuckArray(ChuckType.ARRAY, 2);
            case "vec3" ->
                new ChuckArray(ChuckType.ARRAY, 3);
            case "vec4" ->
                new ChuckArray(ChuckType.ARRAY, 4);
            case "complex", "polar" ->
                new ChuckArray(ChuckType.ARRAY, 2);
            case "MidiOut" ->
                new org.chuck.midi.MidiOut();
            case "SerialIO" ->
                new SerialIO();
            case "OscBundle" ->
                new org.chuck.network.OscBundle();
            case "RegEx" ->
                new RegEx();
            case "Reflect" ->
                new Reflect();
            case "FileIO" ->
                new FileIO();
            case "StringTokenizer" ->
                new StringTokenizer();
            case "Object" ->
                new ChuckObject(ChuckType.OBJECT);
            case "Event" ->
                new ChuckEvent();
            case "OscIn" ->
                new org.chuck.network.OscIn(vm);
            case "OscOut" ->
                new org.chuck.network.OscOut();
            case "OscMsg" ->
                new org.chuck.network.OscMsg();
            case "MidiMsg" ->
                new org.chuck.midi.MidiMsg();
            case "HidMsg" ->
                new org.chuck.hid.HidMsg();
            // GenX table oscillators (fallback aliases)
            case "GenX" ->
                new Gen7(sr);
            case "Gen5" ->
                new Gen5(sr);
            case "Gen7" ->
                new Gen7(sr);
            case "Gen9" ->
                new Gen9(sr);
            case "Gen10" ->
                new Gen10(sr);
            case "Gen17" ->
                new Gen17(sr);
            default ->
                null;
        };
    }

    public static class MoveArgs implements ChuckInstr {

        public int a;

        public MoveArgs(int v) {
            a = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (s.mem.getSp() == 0) {
                s.mem.pushObject(null);
                s.mem.push(0);
                s.mem.push(0);
                s.mem.push(0);
                s.setFramePointer(4);
            }
            if (s.getFramePointer() > 4) {
                return;
            }
            if (a == 0) {
                return;
            }
            Object[] args = new Object[a];
            for (int i = a - 1; i >= 0; i--) {
                if (s.reg.getSp() > 0) {
                    if (s.reg.isObject(0)) {
                        args[i] = s.reg.popObject();
                    } else if (s.reg.isDouble(0)) {
                        args[i] = s.reg.popAsDouble();
                    } else {
                        args[i] = s.reg.popLong();
                    }
                }
            }
            for (Object arg : args) {
                if (arg instanceof ChuckObject co) {
                    s.mem.pushObject(co);
                } else if (arg instanceof Double d) {
                    s.mem.push(d);
                } else if (arg instanceof Long l) {
                    s.mem.push(l);
                } else if (arg != null) {
                    s.mem.pushObject(arg);
                }
            }
        }
    }

    static class CallBuiltinStatic implements ChuckInstr {

        String className, methodName;
        int argc;

        CallBuiltinStatic(String c, String m, int a) {
            className = c;
            methodName = m;
            argc = a;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[argc];
            for (int i = argc - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (s.reg.isDouble(0)) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            try {
                Class<?> clazz = Class.forName(className);
                // Try matching methods
                java.lang.reflect.Method bestMethod = null;
                Object[] bestArgs = null;
                int bestScore = -1;
                for (java.lang.reflect.Method m : clazz.getMethods()) {
                    if (!m.getName().equals(methodName) || m.getParameterCount() != argc || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        continue;
                    }
                    Class<?>[] pts = m.getParameterTypes();
                    Object[] coe = new Object[argc];
                    int score = 0;
                    boolean valid = true;
                    for (int i = 0; i < argc; i++) {
                        Object val = args[i];
                        switch (val) {
                            case Long l -> {
                                if (pts[i] == long.class || pts[i] == Long.class) {
                                    coe[i] = l;
                                    score += 3;
                                } else if (pts[i] == int.class || pts[i] == Integer.class) {
                                    coe[i] = l.intValue();
                                    score += 2;
                                } else if (pts[i] == double.class || pts[i] == Double.class) {
                                    coe[i] = l.doubleValue();
                                    score += 1;
                                } else if (pts[i] == float.class || pts[i] == Float.class) {
                                    coe[i] = l.floatValue();
                                    score += 1;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            case Double d -> {
                                if (pts[i] == double.class || pts[i] == Double.class) {
                                    coe[i] = d;
                                    score += 3;
                                } else if (pts[i] == float.class || pts[i] == Float.class) {
                                    coe[i] = d.floatValue();
                                    score += 2;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            case ChuckString cs -> {
                                if (pts[i] == String.class) {
                                    coe[i] = cs.toString();
                                    score += 3;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            default -> {
                                if (val != null && pts[i].isInstance(val)) {
                                    coe[i] = val;
                                    score += 2;
                                } else {
                                    coe[i] = val;
                                    score += 0;
                                }
                            }
                        }
                    }
                    if (valid && score > bestScore) {
                        bestScore = score;
                        bestMethod = m;
                        bestArgs = coe;
                    }
                }

                if (bestMethod != null) {
                    Object res = bestMethod.invoke(null, bestArgs);
                    if (bestMethod.getReturnType() == void.class) {
                        s.reg.push(0L); // Push dummy if void
                    } else if (res == null) {
                        s.reg.pushObject(null);
                    } else {
                        Class<?> rt = bestMethod.getReturnType();
                        if (rt == int.class || rt == long.class) {
                            s.reg.push(((Number) res).longValue());
                        } else if (rt == float.class || rt == double.class) {
                            s.reg.push(((Number) res).doubleValue());
                        } else if (res instanceof String str) {
                            s.reg.pushObject(new ChuckString(str));
                        } else {
                            s.reg.pushObject(res);
                        }
                    }
                } else {
                    // Try field if no method found and argc == 0
                    if (argc == 0) {
                        java.lang.reflect.Field f = clazz.getField(methodName);
                        Object val = f.get(null);
                        if (val instanceof Number number) {
                            s.reg.push(number.longValue());
                        } else {
                            s.reg.pushObject(val);
                        }
                    } else {
                        s.reg.push(0L);
                    }
                }
            } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException | InvocationTargetException e) {
                s.reg.push(0L);
            }
        }
    }

    static class GetBuiltinStatic implements ChuckInstr {

        String className, fieldName;

        GetBuiltinStatic(String c, String f) {
            className = c;
            fieldName = f;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            try {
                Class<?> clazz = Class.forName(className);
                java.lang.reflect.Field f = clazz.getField(fieldName);
                Object val = f.get(null);
                if (val instanceof Number number) {
                    s.reg.push(number.longValue());
                } else {
                    s.reg.pushObject(val);
                }
            } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException e) {
                s.reg.push(0L);
            }
        }
    }

    static class GetStatic implements ChuckInstr {

        String cName, fName;

        GetStatic(String c, String f) {
            cName = c;
            fName = f;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            UserClassDescriptor d = vm.getUserClass(cName);
            if (d == null) {
                s.reg.pushObject(null);
                return;
            }
            if (d.staticObjects().containsKey(fName)) {
                Object val = d.staticObjects().get(fName);
                s.reg.pushObject(val);
            } else if (d.staticIsDouble().getOrDefault(fName, false)) {
                s.reg.push(Double.longBitsToDouble(d.staticInts().getOrDefault(fName, 0L)));
            } else if (d.staticInts().containsKey(fName)) {
                s.reg.push(d.staticInts().get(fName));
            } else {
                s.reg.push(0L);
            }
        }
    }

    static class SetStatic implements ChuckInstr {

        String cName, fName;

        SetStatic(String c, String f) {
            cName = c;
            fName = f;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            UserClassDescriptor d = vm.getUserClass(cName);
            if (d == null) {
                throw new RuntimeException("Static field set: class '" + cName + "' not found");
            }

            if (s.reg.isObject(0)) {
                Object o = s.reg.peekObject(0);
                d.staticObjects().put(fName, o);
                if (o instanceof org.chuck.audio.ChuckUGen u) {
                    s.registerUGen(u);
                }
                if (o instanceof AutoCloseable ac) {
                    s.registerCloseable(ac);
                }
            } else if (s.reg.isDouble(0)) {
                double val = s.reg.peekAsDouble(0);
                d.staticInts().put(fName, Double.doubleToRawLongBits(val));
            } else {
                long val = s.reg.peekLong(0);
                d.staticInts().put(fName, val);
            }
        }
    }

    static class RegisterClass implements ChuckInstr {

        String name;
        UserClassDescriptor d;

        RegisterClass(String n, UserClassDescriptor desc) {
            name = n;
            d = desc;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            if (name == null || d == null) {
            }
            vm.registerUserClass(name, d);
        }
    }

    static class CallMethod implements ChuckInstr {

        String mName;
        int a;

        CallMethod(String m, int v) {
            mName = m;
            a = v;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                isD[i] = s.reg.isDouble(0);
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (isD[i]) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            Object obj = s.reg.popObject();
            if (obj == null) {
                // Null-safe: size() on null returns 0 (allows for-each to skip null arrays)
                if (mName.equals("size") && a == 0) {
                    s.reg.push(0L);
                    return;
                }
                throw new RuntimeException("NullPointerException: cannot call method '" + mName + "' on null object");
            }

            // 1. Check for User-defined methods (including static called via instance)
            UserObject uo = (obj instanceof UserObject) ? (UserObject) obj : null;
            if (uo != null) {
                String key = mName + ":" + a;
                ChuckCode target = null;
                String t = uo.className;
                for (int depth = 0; depth < 16 && t != null; depth++) {
                    UserClassDescriptor desc = vm.getUserClass(t);
                    if (desc == null) {
                        break;
                    }
                    if (desc.methods().containsKey(key)) {
                        target = desc.methods().get(key);
                        break;
                    }
                    if (desc.staticMethods().containsKey(key)) {
                        target = desc.staticMethods().get(key);
                        break;
                    }
                    t = desc.parentName();
                }
                if (target != null) {
                    s.mem.pushObject(s.getCode());
                    s.mem.push((long) s.getPc());
                    s.mem.push((long) s.getFramePointer());
                    s.mem.push((long) s.reg.getSp());
                    s.setFramePointer(s.mem.getSp());
                    for (int i = 0; i < a; i++) {
                        Object arg = args[i];
                        if (arg instanceof ChuckObject co) {
                            s.mem.pushObject(co);
                        } else if (isD[i]) {
                            s.mem.push((Double) arg);
                        } else if (arg instanceof Long l) {
                            s.mem.push(l);
                        } else {
                            s.mem.pushObject(arg);
                        }
                    }
                    s.thisStack.push(uo);
                    s.setCode(target);
                    s.setPc(-1);
                    return;
                }
            }

            // 2. Special event methods
            if (obj instanceof ChuckEvent event) {
                if (mName.equals("signal")) {
                    event.signal(vm);
                    s.reg.pushObject(event);
                    return;
                }
                if (mName.equals("broadcast")) {
                    event.broadcast(vm);
                    s.reg.pushObject(event);
                    return;
                }
                if (mName.equals("waiting")) {
                    s.reg.push((long) event.getWaitingCount());
                    return;
                }
            }
            if (uo != null && uo.eventDelegate != null) {
                if (mName.equals("signal")) {
                    uo.eventDelegate.signal(vm);
                    s.reg.pushObject(uo);
                    return;
                }
                if (mName.equals("broadcast")) {
                    uo.eventDelegate.broadcast(vm);
                    s.reg.pushObject(uo);
                    return;
                }
                if (mName.equals("waiting")) {
                    s.reg.push((long) uo.eventDelegate.getWaitingCount());
                    return;
                }
            }

            try {
                // Two-pass method resolution: prefer exact type match over widening
                java.lang.reflect.Method bestMethod = null;
                Object[] bestArgs = null;
                int bestScore = -1;
                for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                    if (!m.getName().equals(mName) || m.getParameterCount() != a) {
                        continue;
                    }
                    Class<?>[] pts = m.getParameterTypes();
                    Object[] coe = new Object[a];
                    int score = 0;
                    boolean valid = true;
                    for (int i = 0; i < a; i++) {
                        Object val = args[i];
                        switch (val) {
                            case Long l -> {
                                if (pts[i] == long.class || pts[i] == Long.class) {
                                    coe[i] = l;
                                    score += 3;
                                } else if (pts[i] == int.class || pts[i] == Integer.class) {
                                    coe[i] = l.intValue();
                                    score += 2;
                                } else if (pts[i] == double.class || pts[i] == Double.class) {
                                    coe[i] = l.doubleValue();
                                    score += 1;
                                } else if (pts[i] == float.class || pts[i] == Float.class) {
                                    coe[i] = l.floatValue();
                                    score += 1;
                                } else if (pts[i] == String.class) {
                                    coe[i] = String.valueOf(l);
                                    score += 0;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            case Double d -> {
                                if (pts[i] == double.class || pts[i] == Double.class) {
                                    coe[i] = d;
                                    score += 3;
                                } else if (pts[i] == float.class || pts[i] == Float.class) {
                                    coe[i] = d.floatValue();
                                    score += 2;
                                } else if (pts[i] == String.class) {
                                    coe[i] = String.valueOf(d);
                                    score += 0;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            case ChuckString cs -> {
                                if (pts[i] == String.class) {
                                    coe[i] = cs.toString();
                                    score += 3;
                                } else if (pts[i].isAssignableFrom(ChuckString.class)) {
                                    coe[i] = cs;
                                    score += 2;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            case ChuckDuration cd -> {
                                if (pts[i] == double.class || pts[i] == Double.class) {
                                    coe[i] = (double) cd.samples();
                                    score += 2;
                                } else if (pts[i] == float.class || pts[i] == Float.class) {
                                    coe[i] = (float) cd.samples();
                                    score += 2;
                                } else if (pts[i] == long.class || pts[i] == Long.class) {
                                    coe[i] = (long) cd.samples();
                                    score += 3;
                                } else {
                                    valid = false;
                                    break;
                                }
                            }
                            default -> {
                                if (val != null && pts[i].isInstance(val)) {
                                    coe[i] = val;
                                    score += 2;
                                } else {
                                    coe[i] = val;
                                    score += 0;
                                }
                            }
                        }
                    }
                    if (valid && score > bestScore) {
                        bestScore = score;
                        bestMethod = m;
                        bestArgs = coe;
                    }
                }
                if (bestMethod != null) {
                    Object res = bestMethod.invoke(obj, bestArgs);
                    if (bestMethod.getReturnType() == void.class) {
                        s.reg.pushObject(obj);
                    } else if (res != null) {
                        Class<?> rt = bestMethod.getReturnType();
                        if (rt == int.class || rt == long.class) {
                            s.reg.push(((Number) res).longValue());
                        } else if (rt == char.class || rt == Character.class) {
                            s.reg.push((long) (Character) res);
                        } else if (rt == boolean.class) {
                            s.reg.push((Boolean) res ? 1L : 0L);
                        } else if (rt == float.class || rt == double.class) {
                            s.reg.push(((Number) res).doubleValue());
                        } else if (res instanceof String str) {
                            s.reg.pushObject(new ChuckString(str));
                        } else {
                            s.reg.pushObject(res);
                        }
                    } else {
                        // non-void method returned null
                        s.reg.pushObject(null);
                    }
                    return;
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            // Method not found: throw for user-defined types and UGens
            if (obj instanceof UserObject uo2) {
                throw new RuntimeException("class '" + uo2.className + "' has no member '" + mName + "'");
            }
            if (obj instanceof ChuckUGen) {
                String typeName = obj.getClass().getSimpleName();
                throw new RuntimeException("class '" + typeName + "' has no member '" + mName + "'");
            }
            s.reg.pushObject(obj);
        }
    }

    static class ShredObject extends ChuckObject {

        ShredObject() {
            super(new ChuckType("Shred", ChuckType.OBJECT, 0, 0));
        }

        public String dir() {
            return "examples/data/";
        }
    }

    static class MeDir implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            long level = s.reg.getSp() > 0 ? s.reg.popAsLong() : 0;
            s.reg.pushObject(new ChuckString(s.dir((int) level)));
        }
    }

    static class MeArgs implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push((long) s.args());
        }
    }

    static class MeArg implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            int idx = (int) s.reg.popLong();
            s.reg.pushObject(new ChuckString(s.arg(idx)));
        }
    }

    static class MeExit implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.abort();
        }
    }

    static class MachineObject extends ChuckObject {

        MachineObject() {
            super(new ChuckType("Machine", ChuckType.OBJECT, 0, 0));
        }

        public int add(String p, ChuckVM v) {
            return v.add(p);
        }

        public void remove(int i, ChuckVM v) {
            v.removeShred(i);
        }

        public int replace(int i, String p, ChuckVM v) {
            return v.replace(i, p);
        }

        public void status(ChuckVM v) {
            v.status();
        }

        public int eval(String s, ChuckVM v) {
            return v.eval(s);
        }

        public void clear(ChuckVM v) {
            v.clear();
        }

        public int refcount(Object o) {
            return 1;
        }
    }

    static class MachineCall implements ChuckInstr {

        String method;
        int argc;

        MachineCall(String m, int a) {
            method = m;
            argc = a;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[argc];
            for (int i = argc - 1; i >= 0; i--) {
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (s.reg.isDouble(0)) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            switch (method) {
                case "add" -> {
                    String path = args.length > 0 ? String.valueOf(args[0]) : "";
                    s.reg.push((long) vm.add(path));
                }
                case "remove" -> {
                    long id = args.length > 0 ? ((Number) args[0]).longValue() : 0;
                    vm.removeShred((int) id);
                    s.reg.push(id);
                }
                case "replace" -> {
                    long id = args.length > 0 ? ((Number) args[0]).longValue() : 0;
                    String path = args.length > 1 ? String.valueOf(args[1]) : "";
                    s.reg.push((long) vm.replace((int) id, path));
                }
                case "status" -> {
                    vm.status();
                    s.reg.push(0L);
                }
                case "clear", "removeAll" -> {
                    vm.clear();
                    s.reg.push(0L);
                }
                case "eval" -> {
                    String src = args.length > 0 ? String.valueOf(args[0]) : "";
                    long id = vm.eval(src);
                    s.reg.push(id);
                    // Yield to give the newly sporked shred a chance to run.
                    // spork() schedules new shreds at currentTime+1, so we advance by 1 sample.
                    vm.advanceTime(1);
                }
                case "numShreds" ->
                    s.reg.push((long) vm.getActiveShredCount());
                case "shredExists" -> {
                    int sid = args.length > 0 ? ((Number) args[0]).intValue() : 0;
                    s.reg.push(vm.shredExists(sid) ? 1L : 0L);
                }
                case "shreds" -> {
                    int[] ids = vm.getActiveShredIds();
                    org.chuck.core.ChuckArray arr = new org.chuck.core.ChuckArray(org.chuck.core.ChuckType.ARRAY, ids.length);
                    for (int i = 0; i < ids.length; i++) {
                        arr.setInt(i, ids[i]);
                    }
                    s.reg.pushObject(arr);
                }
                case "crash" -> {
                    vm.print("[chuck]: (VM) crash! (by request)\n");
                    System.exit(1);
                }
                case "resetID" -> {
                    vm.resetShredId();
                    s.reg.push(0L);
                }
                case "clearVM" -> {
                    vm.clear();
                    s.reg.push(0L);
                }
                case "gc" -> {
                    vm.gc();
                    s.reg.push(0L);
                }
                case "version" ->
                    s.reg.pushObject(new ChuckString(vm.getVersion()));
                case "platform" ->
                    s.reg.pushObject(new ChuckString(vm.getPlatform()));
                case "loglevel" -> {
                    if (argc > 0) {
                        vm.setLogLevel(((Number) args[0]).intValue());
                        s.reg.push(0L);
                    } else {
                        s.reg.push((long) vm.getLogLevel());
                    }
                }
                case "setloglevel" -> {
                    vm.setLogLevel(args.length > 0 ? ((Number) args[0]).intValue() : 1);
                    s.reg.push(0L);
                }
                case "timeofday" ->
                    s.reg.push(Double.doubleToRawLongBits(vm.getTimeOfDay()));
                default ->
                    s.reg.push(0L);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Built-in complex arithmetic instructions
    // -------------------------------------------------------------------------
    /**
     * complex + complex : element-wise (re+re, im+im)
     */
    public static class ComplexAdd implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, lhs.getFloat(0) + rhs.getFloat(0));
            result.setFloat(1, lhs.getFloat(1) + rhs.getFloat(1));
            s.reg.pushObject(result);
        }
    }

    /**
     * complex - complex : element-wise
     */
    public static class ComplexSub implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, lhs.getFloat(0) - rhs.getFloat(0));
            result.setFloat(1, lhs.getFloat(1) - rhs.getFloat(1));
            s.reg.pushObject(result);
        }
    }

    /**
     * complex * complex : (a+bi)(c+di) = (ac-bd) + (ad+bc)i
     */
    public static class ComplexMul implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            double a = lhs.getFloat(0), b = lhs.getFloat(1);
            double c = rhs.getFloat(0), d = rhs.getFloat(1);
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, a * c - b * d);
            result.setFloat(1, a * d + b * c);
            s.reg.pushObject(result);
        }
    }

    /**
     * complex / complex : (a+bi)/(c+di) = ((ac+bd) + (bc-ad)i) / (c²+d²)
     */
    public static class ComplexDiv implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            double a = lhs.getFloat(0), b = lhs.getFloat(1);
            double c = rhs.getFloat(0), d = rhs.getFloat(1);
            double denom = c * c + d * d;
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            if (denom < 1e-300) {
                result.setFloat(0, 0.0);
                result.setFloat(1, 0.0);
            } else {
                result.setFloat(0, (a * c + b * d) / denom);
                result.setFloat(1, (b * c - a * d) / denom);
            }
            s.reg.pushObject(result);
        }
    }

    // -------------------------------------------------------------------------
    // Built-in polar arithmetic instructions
    // -------------------------------------------------------------------------
    /**
     * polar + polar : convert to rectangular, add, convert back
     */
    public static class PolarAdd implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            double r1 = lhs.getFloat(0), t1 = lhs.getFloat(1);
            double r2 = rhs.getFloat(0), t2 = rhs.getFloat(1);
            double re = r1 * Math.cos(t1) + r2 * Math.cos(t2);
            double im = r1 * Math.sin(t1) + r2 * Math.sin(t2);
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, Math.sqrt(re * re + im * im));
            result.setFloat(1, Math.atan2(im, re));
            s.reg.pushObject(result);
        }
    }

    /**
     * polar - polar : convert to rectangular, subtract, convert back
     */
    public static class PolarSub implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            double r1 = lhs.getFloat(0), t1 = lhs.getFloat(1);
            double r2 = rhs.getFloat(0), t2 = rhs.getFloat(1);
            double re = r1 * Math.cos(t1) - r2 * Math.cos(t2);
            double im = r1 * Math.sin(t1) - r2 * Math.sin(t2);
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, Math.sqrt(re * re + im * im));
            result.setFloat(1, Math.atan2(im, re));
            s.reg.pushObject(result);
        }
    }

    /**
     * polar * polar : (r1*r2, θ1+θ2)
     */
    public static class PolarMul implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, lhs.getFloat(0) * rhs.getFloat(0));
            result.setFloat(1, lhs.getFloat(1) + rhs.getFloat(1));
            s.reg.pushObject(result);
        }
    }

    /**
     * polar / polar : (r1/r2, θ1-θ2)
     */
    public static class PolarDiv implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            double mag = rhs.getFloat(0);
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, 2);
            result.setFloat(0, mag < 1e-300 ? 0.0 : lhs.getFloat(0) / mag);
            result.setFloat(1, lhs.getFloat(1) - rhs.getFloat(1));
            s.reg.pushObject(result);
        }
    }

    // -------------------------------------------------------------------------
    // Built-in vec element-wise arithmetic instructions
    // -------------------------------------------------------------------------
    /**
     * vec + vec : element-wise addition
     */
    public static class VecAdd implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            int len = Math.min(lhs.size(), rhs.size());
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, len);
            for (int i = 0; i < len; i++) {
                result.setFloat(i, lhs.getFloat(i) + rhs.getFloat(i));
            }
            s.reg.pushObject(result);
        }
    }

    /**
     * vec - vec : element-wise subtraction
     */
    public static class VecSub implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            int len = Math.min(lhs.size(), rhs.size());
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, len);
            for (int i = 0; i < len; i++) {
                result.setFloat(i, lhs.getFloat(i) - rhs.getFloat(i));
            }
            s.reg.pushObject(result);
        }
    }

    /**
     * vec * scalar (float/int) : scale all elements
     */
    public static class VecScale implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            double scalar = s.reg.popAsDouble();
            ChuckArray vec = (ChuckArray) s.reg.popObject();
            ChuckArray result = new ChuckArray(ChuckType.ARRAY, vec.size());
            for (int i = 0; i < vec.size(); i++) {
                result.setFloat(i, vec.getFloat(i) * scalar);
            }
            s.reg.pushObject(result);
        }
    }

    /** $ int cast: pops a value, pushes it as a long (truncation). */
    static class CastToInt implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(s.reg.popAsLong());
        }
    }

    /** $ float cast: pops a value, pushes it as a double. */
    static class CastToFloat implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(s.reg.popAsDouble());
        }
    }

    /** $ string cast: pops a value, pushes it as a ChuckString. */
    static class CastToString implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object val = s.reg.pop();
            String str = switch (val) {
                case Long l -> String.valueOf(l);
                case Double d -> String.format("%.6f", d);
                case null -> "null";
                default -> val.toString();
            };
            s.reg.pushObject(new ChuckString(str));
        }
    }

    /**
     * Pushes the current 'this' object from thisStack onto the reg stack.
     */
    static class PushThis implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(s.thisStack.isEmpty() ? null : s.thisStack.peek());
        }
    }

    /**
     * Calls a method on 'this' starting lookup from startClass (for super
     * dispatch).
     */
    static class CallSuperMethod implements ChuckInstr {

        String startClass, mName;
        int a;

        CallSuperMethod(String sc, String m, int argc) {
            startClass = sc;
            mName = m;
            a = argc;
        }

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[a];
            boolean[] isD = new boolean[a];
            for (int i = a - 1; i >= 0; i--) {
                isD[i] = s.reg.isDouble(0);
                if (s.reg.isObject(0)) {
                    args[i] = s.reg.popObject();
                } else if (isD[i]) {
                    args[i] = s.reg.popAsDouble();
                } else {
                    args[i] = s.reg.popLong();
                }
            }
            // 'this' comes from thisStack (not reg stack)
            UserObject uo = s.thisStack.isEmpty() ? null : s.thisStack.peek();
            // Resolve method starting from startClass, walking up the hierarchy
            String t = startClass;
            ChuckCode target = null;
            for (int depth = 0; depth < 16 && t != null; depth++) {
                UserClassDescriptor desc = vm.getUserClass(t);
                if (desc == null) {
                    break;
                }
                String key = mName + ":" + a;
                if (desc.methods().containsKey(key)) {
                    target = desc.methods().get(key);
                    break;
                }
                if (desc.staticMethods().containsKey(key)) {
                    target = desc.staticMethods().get(key);
                    break;
                }
                t = desc.parentName();
            }
            if (target == null) {
                return;
            }
            // Call the resolved method (same as CallMethod's user-defined path)
            s.mem.pushObject(s.getCode());
            s.mem.push((long) s.getPc());
            s.mem.push((long) s.getFramePointer());
            s.mem.push((long) s.reg.getSp());
            s.setFramePointer(s.mem.getSp());
            for (int i = 0; i < a; i++) {
                Object arg = args[i];
                if (arg instanceof ChuckObject co) {
                    s.mem.pushObject(co);
                } else if (isD[i]) {
                    s.mem.push((Double) arg);
                } else if (arg instanceof Long l) {
                    s.mem.push(l);
                } else {
                    s.mem.pushObject(arg);
                }
            }
            s.thisStack.push(uo); // always push so ReturnMethod can pop
            s.setCode(target);
            s.setPc(-1);
        }
    }

    /**
     * vec * vec : dot product, returns scalar (float)
     */
    public static class VecDot implements ChuckInstr {

        @Override
        public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray rhs = (ChuckArray) s.reg.popObject();
            ChuckArray lhs = (ChuckArray) s.reg.popObject();
            int len = Math.min(lhs.size(), rhs.size());
            double dot = 0.0;
            for (int i = 0; i < len; i++) {
                dot += lhs.getFloat(i) * rhs.getFloat(i);
            }
            s.reg.push(dot);
        }
    }
}
