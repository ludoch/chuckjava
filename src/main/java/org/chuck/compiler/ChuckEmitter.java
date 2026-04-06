package org.chuck.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.chuck.core.AdvanceTime;
import org.chuck.core.CallFunc;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckInstr;
import org.chuck.core.ChuckPrint;
import org.chuck.core.ChuckType;
import org.chuck.core.ReturnFunc;
import org.chuck.core.SetMemberIntByName;
import org.chuck.core.UGenRegistry;
import org.chuck.core.UserClassDescriptor;
import org.chuck.core.instr.ArrayInstrs;
import org.chuck.core.instr.ArithmeticInstrs;
import org.chuck.core.instr.MiscInstrs;
import org.chuck.core.instr.LogicInstrs;
import org.chuck.core.instr.StackInstrs;
import org.chuck.core.instr.ControlInstrs;
import org.chuck.core.instr.TypeInstrs;
import org.chuck.core.instr.ObjectInstrs;
import org.chuck.core.instr.PushInstrs;
import org.chuck.core.instr.ComplexInstrs;
import org.chuck.core.instr.PolarInstrs;
import org.chuck.core.instr.VecInstrs;
import org.chuck.core.instr.FieldInstrs;
import org.chuck.core.instr.VarInstrs;
import org.chuck.core.instr.MathInstrs;
import org.chuck.core.instr.MeInstrs;
import org.chuck.core.instr.UgenInstrs;

/**
 * Emits executable VM instructions from a parsed AST.
 */
public class ChuckEmitter {

    private final Map<String, ChuckCode> functions;
    private final java.util.Stack<Map<String, Integer>> localScopes = new java.util.Stack<>();
    /**
     * Tracks variable name → declared type for operator overload dispatch.
     */
    private final Map<String, String> varTypes = new HashMap<>();
    /** Tracks variable name -> declared as const. */
    private final java.util.Set<String> constants = new java.util.HashSet<>();

    private final Map<String, UserClassDescriptor> userClassRegistry;
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
            "UAna", "Shred", "Thread", "ChucK", "Event"
    );

    // Core non-UGen data types
    private static final java.util.Set<String> CORE_DATA_TYPES = java.util.Set.of(
            "int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", "complex", "polar", "Object", "Array", "Type", "auto",
            "MidiMsg", "HidMsg", "OscMsg", "FileIO", "IO", "SerialIO", "OscIn", "OscOut", "OscBundle", "MidiIn", "MidiOut", "Hid", "StringTokenizer", "RegEx", "Reflect"
    );

    private boolean isKnownUGenType(String type) {
        return UGenRegistry.isRegistered(type) || CORE_UGENS.contains(type);
    }

    private boolean isKnownType(String type) {
        return isKnownUGenType(type) || CORE_DATA_TYPES.contains(type) || userClassRegistry.containsKey(type);
    }

    private String getMethodKey(String name, List<String> argTypes) {
        StringBuilder sb = new StringBuilder(name).append(":");
        if (argTypes == null || argTypes.isEmpty()) return sb.append("0").toString();
        for (int i = 0; i < argTypes.size(); i++) {
            sb.append(argTypes.get(i));
            if (i < argTypes.size() - 1) sb.append(",");
        }
        return sb.toString();
    }

    private String resolveMethodKey(String className, String mName, List<String> callArgTypes) {
        UserClassDescriptor desc = userClassRegistry.get(className);
        if (desc == null) return mName + ":" + (callArgTypes != null ? callArgTypes.size() : 0);

        // 1. Try exact match with types
        String fullKey = getMethodKey(mName, callArgTypes);
        String t = className;
        while (t != null) {
            UserClassDescriptor d = userClassRegistry.get(t);
            if (d == null) break;
            if (d.methods().containsKey(fullKey) || d.staticMethods().containsKey(fullKey)) return fullKey;
            t = d.parentName();
        }

        // 2. Fallback to name:argc
        return mName + ":" + (callArgTypes != null ? callArgTypes.size() : 0);
    }

    public ChuckEmitter() {
        this(new HashMap<>(), new HashMap<>());
    }

    public ChuckEmitter(Map<String, UserClassDescriptor> registry) {
        this(registry, new HashMap<>());
    }

    public ChuckEmitter(Map<String, UserClassDescriptor> registry, Map<String, ChuckCode> preloadedFunctions) {
        this.userClassRegistry = registry;
        this.functions = preloadedFunctions;
        initGlobalTypes();
    }

    private void initGlobalTypes() {
        initGlobalTypes(null);
    }

    private void initGlobalTypes(Map<String, String> existing) {
        this.globalVarTypes.put("dac", "UGen");
        this.globalVarTypes.put("adc", "UGen");
        this.globalVarTypes.put("blackhole", "UGen");
        this.globalVarTypes.put("chout", "IO");
        this.globalVarTypes.put("cherr", "IO");
        this.globalVarTypes.put("me", "Shred");
        this.globalVarTypes.put("Machine", "Machine");
        if (existing != null) this.globalVarTypes.putAll(existing);
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
            case ChuckAST.IdExp id -> {
                String type = varTypes.get(id.name());
                if (type == null) {
                    type = globalVarTypes.get(id.name());
                }
                yield type;
            }
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
            case ChuckAST.ComplexLit _ ->
                "complex";
            case ChuckAST.PolarLit _ ->
                "polar";
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
                if (bin.op() == ChuckAST.Operator.SHIFT_LEFT || bin.op() == ChuckAST.Operator.CHUCK
                        || bin.op() == ChuckAST.Operator.AT_CHUCK || bin.op() == ChuckAST.Operator.WRITE_IO
                        || bin.op() == ChuckAST.Operator.UNCHUCK || bin.op() == ChuckAST.Operator.UPCHUCK
                        || bin.op() == ChuckAST.Operator.PLUS_CHUCK || bin.op() == ChuckAST.Operator.MINUS_CHUCK
                        || bin.op() == ChuckAST.Operator.TIMES_CHUCK || bin.op() == ChuckAST.Operator.DIVIDE_CHUCK
                        || bin.op() == ChuckAST.Operator.PERCENT_CHUCK
                        || (bin.op() == ChuckAST.Operator.LE && ("IO".equals(lhsType) || "FileIO".equals(lhsType)))) {
                    yield lhsType;
                }
                yield null;
            }
            case ChuckAST.DotExp dot -> {
                String baseType = getExprType(dot.base());
                if (baseType == null) yield null;
                if (baseType.equals("complex") || baseType.equals("polar") || baseType.startsWith("vec")) {
                    yield "float";
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
        return emit(statements, programName, null);
    }

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName, Map<String, String> existingGlobals) {
        localScopes.clear();
        // Push a top-level local scope so script variables are shred-local
        localScopes.push(new HashMap<>());

        varTypes.clear();
        globalVarTypes.clear();
        initGlobalTypes(existingGlobals);
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
                userClassRegistry.put(s.name(), new UserClassDescriptor(s.name(), s.parentName(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), null, s.isAbstract(), s.isInterface()));
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
        code.addInstruction(new VarInstrs.MoveArgs(0));

        // First, register classes so they are available for static access in the script
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.ClassDefStmt s) {
                UserClassDescriptor desc = userClassRegistry.get(s.name());
                if (desc != null) {
                    code.addInstruction(new MiscInstrs.RegisterClass(s.name(), desc));
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
        if (stmt == null) return;
        if (code != null) code.setActiveLineNumber(stmt.line());
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
                code.addInstruction(new StackInstrs.Pop());
            }
            case ChuckAST.WhileStmt s -> {
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                emitExpression(s.condition(), code);
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse
                emitStatement(s.body(), code);
                code.addInstruction(new ControlInstrs.Jump(startPc));
                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
                }
            }
            case ChuckAST.UntilStmt s -> {
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                emitExpression(s.condition(), code);
                code.addInstruction(new LogicInstrs.LogicalNot());
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse
                emitStatement(s.body(), code);
                code.addInstruction(new ControlInstrs.Jump(startPc));
                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
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
                    code.addInstruction(new LogicInstrs.LogicalNot());
                }
                code.addInstruction(new ControlInstrs.JumpIfTrue(bodyStart));
                int endPc = code.getNumInstructions();

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(condStart));
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
                        code.addInstruction(new StackInstrs.Dup());
                        emitExpression(c.match(), code);
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
                        code.replaceInstruction(caseConditionJumps.get(caseIdx++), new ControlInstrs.JumpIfTrue(code.getNumInstructions()));
                        code.addInstruction(new StackInstrs.Pop()); // pop switch value before body executes
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
                    code.replaceInstruction(jumpToDefaultOrEnd, new ControlInstrs.Jump(defaultBodyStartIndex));
                } else {
                    code.replaceInstruction(jumpToDefaultOrEnd, new ControlInstrs.Jump(endPc));
                }

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
            }
            case ChuckAST.ForStmt s -> {
                if (s.init() != null) emitStatement(s.init(), code);
                int startPc = code.getNumInstructions();
                breakJumps.push(new ArrayList<>());
                continueJumps.push(new ArrayList<>());

                if (s.condition() instanceof ChuckAST.ExpStmt es) {
                    emitExpression(es.exp(), code);
                } else {
                    code.addInstruction(new PushInstrs.PushInt(1)); // truthy
                }
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                emitStatement(s.body(), code);

                int updateStart = code.getNumInstructions();
                if (s.update() != null) {
                    emitExpression(s.update(), code);
                    code.addInstruction(new StackInstrs.Pop()); // pop update result
                }
                code.addInstruction(new ControlInstrs.Jump(startPc));

                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endPc));
                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
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
                if (s.isStatic() && (currentClass == null || localScopes.size() > 1)) {
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
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushGlobal(s.type(), s.name(), 0, s.isReference(), false, userClassRegistry));
                    } else {
                        Map<String, Integer> scope = localScopes.peek();
                        int offset = scope.size();
                        scope.put(s.name(), offset);
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(s.type(), offset, 0, s.isReference(), false, userClassRegistry));
                    }
                    code.addInstruction(new StackInstrs.Dup());
                    for (ChuckAST.Exp arg : call.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new ObjectInstrs.CallMethod(s.type(), call.args().size()));
                    code.addInstruction(new StackInstrs.Pop());
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
                        code.addInstruction(new PushInstrs.PushInt(0)); // dummy size for instantiation
                        argCount = 1;
                    }

                    boolean isArrayDecl = !s.arraySizes().isEmpty();
                    boolean useGlobal = forceGlobal || localScopes.isEmpty() || localScopes.size() == 1;
                    if (useGlobal) {
                        globalVarTypes.put(s.name(), s.type());
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushGlobal(s.type(), s.name(), argCount, s.isReference(), isArrayDecl, userClassRegistry));
                        code.addInstruction(new StackInstrs.Pop());
                    } else {
                        Map<String, Integer> scope = localScopes.peek();
                        Integer offset = scope.get(s.name());
                        if (offset == null) {
                            offset = scope.size();
                            scope.put(s.name(), offset);
                        }
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(s.type(), offset, argCount, s.isReference(), isArrayDecl, userClassRegistry));
                        code.addInstruction(new StackInstrs.Pop());
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
                String key = getMethodKey(s.name(), s.argTypes());
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
                funcCode.addInstruction(new VarInstrs.MoveArgs(s.argNames().size()));
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

                if (functions.containsKey(key)) {
                    throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                            + ": error: cannot overload function with identical arguments -- '"
                            + s.name() + "' already defined");
                }
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
                code.addInstruction(currentClass != null ? new ControlInstrs.ReturnMethod() : new ReturnFunc());
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
                        staticInts, staticIsDouble, staticObjects, null, s.isAbstract(), s.isInterface()));

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
                        preCtorCodeLocal.addInstruction(new StackInstrs.PushThis());
                        preCtorCodeLocal.addInstruction(new SetMemberIntByName(rDecl.name()));
                        preCtorCodeLocal.addInstruction(new StackInstrs.Pop());
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

                    String methodKey = getMethodKey(methodName, m.argTypes());
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
                    methodCode.addInstruction(new VarInstrs.MoveArgs(m.argNames().size()));

                    emitStatement(m.body(), methodCode);
                    methodCode.addInstruction(m.isStatic() ? new ReturnFunc() : new ControlInstrs.ReturnMethod());

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
                UserClassDescriptor descriptor = new UserClassDescriptor(s.name(), s.parentName(), fieldDefs, methodCodes, staticMethodCodes, staticInts, staticIsDouble, staticObjects, finalPreCtorCode, s.isAbstract(), s.isInterface());
                userClassRegistry.put(s.name(), descriptor);
                if (code != null) {
                    code.addInstruction(new MiscInstrs.RegisterClass(s.name(), descriptor));
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
                    code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(elseStartIdx));
                    emitStatement(s.elseBranch(), code);
                    int elseEndIdx = code.getNumInstructions();
                    code.replaceInstruction(thenEndIdx, new ControlInstrs.Jump(elseEndIdx));
                } else {
                    int endIdx = code.getNumInstructions();
                    code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(endIdx));
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
                code.addInstruction(new VarInstrs.StoreLocal(collOffset));
                code.addInstruction(new StackInstrs.Pop());

                // 2. Initialize index local to 0
                String idxName = "__idx_" + s.iterName() + "_" + s.line() + "_" + s.column();
                int idxOffset = scope.size();
                scope.put(idxName, idxOffset);
                code.addInstruction(new PushInstrs.PushInt(0));
                code.addInstruction(new VarInstrs.StoreLocal(idxOffset));
                code.addInstruction(new StackInstrs.Pop());

                // 3. Loop start
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

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
                code.addInstruction(new ArrayInstrs.GetArrayInt());

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
                code.addInstruction(new VarInstrs.StoreLocal(iterOffset));
                code.addInstruction(new StackInstrs.Pop());

                // 7. Loop body
                emitStatement(s.body(), code);

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

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
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
                code.addInstruction(new VarInstrs.StoreLocal(cntOffset));
                code.addInstruction(new StackInstrs.Pop());

                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                // Condition: counter > 0
                code.addInstruction(new VarInstrs.LoadLocal(cntOffset));
                code.addInstruction(new PushInstrs.PushInt(0));
                code.addInstruction(new LogicInstrs.GreaterThanAny());
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                // Body
                emitStatement(s.body(), code);

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

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
                }
            }
            case ChuckAST.LoopStmt s -> {
                // Infinite loop: emit body, jump back — only break exits
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());
                emitStatement(s.body(), code);
                code.addInstruction(new ControlInstrs.Jump(startPc));
                int endPc = code.getNumInstructions();
                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
                }
            }
            default -> {
            }
        }
    }

    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        if (exp == null) return;
        if (code != null) code.setActiveLineNumber(exp.line());
        switch (exp) {
            case ChuckAST.IntExp e ->
                code.addInstruction(new PushInstrs.PushInt(e.value()));
            case ChuckAST.FloatExp e ->
                code.addInstruction(new PushInstrs.PushFloat(e.value()));
            case ChuckAST.StringExp e ->
                code.addInstruction(new PushInstrs.PushString(e.value()));
            case ChuckAST.MeExp _ ->
                code.addInstruction(new PushInstrs.PushMe());
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
                        code.addInstruction(new ArithmeticInstrs.NegateAny());
                    case S_OR ->
                        code.addInstruction(new LogicInstrs.LogicalNot());
                    case PLUS -> {
                    }
                    default -> {
                    }
                }
            }
            case ChuckAST.DeclExp e -> {
                // Check for undefined type (not a known class, UGen, or core type)
                if (!isKnownType(e.type())) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: undefined type '" + e.type() + "'");
                }

                // Check for 'new' on primitive types or undefined types
                if (e.name().startsWith("@new_")) {
                    boolean isArrayNew = e.name().startsWith("@new_array_");
                    java.util.Set<String> primitives = java.util.Set.of("int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", "complex", "polar");
                    if (primitives.contains(e.type()) && !isArrayNew) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot use 'new' on primitive type '" + e.type() + "'");
                    }
                }
                // Check for static variable declared outside class scope
                if (e.isStatic() && (currentClass == null || localScopes.size() > 1)) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: static variables must be declared at class scope");
                }
                // Track empty-array variable declarations
                if (!e.arraySizes().isEmpty() && e.arraySizes().get(0) instanceof ChuckAST.IntExp sz0e && sz0e.value() < 0) {
                    emptyArrayVars.add(e.name());
                }
                varTypes.put(e.name(), e.type()); // track variable type
                if (e.isConst()) {
                    constants.add(e.name());
                }
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
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(e.type(), localOffset, 0, e.isReference(), false, userClassRegistry));
                    } else if (!forceGlobal && !localScopes.isEmpty()) {
                        Map<String, Integer> scope = localScopes.peek();
                        localOffset = scope.size();
                        scope.put(e.name(), localOffset);
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(e.type(), localOffset, 0, e.isReference(), false, userClassRegistry));
                    } else {
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushGlobal(e.type(), e.name(), 0, e.isReference(), false, userClassRegistry));
                    }
                    code.addInstruction(new StackInstrs.Dup());
                    for (ChuckAST.Exp arg : call.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new ObjectInstrs.CallMethod(e.type(), call.args().size()));
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
                    boolean useGlobal = forceGlobal || localScopes.isEmpty() || localScopes.size() == 1;

                    Integer localOffset = (forceGlobal || useGlobal) ? null : getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(e.type(), localOffset, argCount, e.isReference(), isArrayDecl, userClassRegistry));
                    } else if (!forceGlobal && !useGlobal && !localScopes.isEmpty()) {
                        Map<String, Integer> scope = localScopes.peek();
                        localOffset = scope.size();
                        scope.put(e.name(), localOffset);
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(e.type(), localOffset, argCount, e.isReference(), isArrayDecl, userClassRegistry));
                    } else {
                        code.addInstruction(new ObjectInstrs.InstantiateSetAndPushGlobal(e.type(), e.name(), argCount, e.isReference(), isArrayDecl, userClassRegistry));
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
                        if (currentClass == null || localScopes.size() > 1) {
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
                    code.addInstruction(new MiscInstrs.ChuckUnchuck());
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
                    code.addInstruction(new ObjectInstrs.CallMethod("append", 1));
                } else if (e.op() == ChuckAST.Operator.NEW) {
                    // Check for empty brackets: new SinOsc[] (error-empty-bracket)
                    if (e.rhs() instanceof ChuckAST.IntExp szNew && szNew.value() < 0) {
                        String typeName = e.lhs() instanceof ChuckAST.IdExp tid ? tid.name() : "?";
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot use 'new " + typeName + "[]' with empty brackets");
                    }
                    emitExpression(e.rhs(), code); // size
                    code.addInstruction(new ArrayInstrs.NewArray(null, 1));
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
                    String targetType = getExprType(e.rhs());
                    if (isVecType(targetType)) {
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
                                String opType = getExprType(e.lhs());
                                if ("complex".equals(targetType) && "complex".equals(opType)) code.addInstruction(new ComplexInstrs.Mul());
                                else if ("polar".equals(targetType) && "polar".equals(opType)) code.addInstruction(new PolarInstrs.Mul());
                                else if (opType == null || "float".equals(opType) || "int".equals(opType)) code.addInstruction(new VecInstrs.VecScale());
                                else code.addInstruction(new ArithmeticInstrs.TimesAny());
                            }
                            case DIVIDE -> {
                                String opType = getExprType(e.lhs());
                                if ("complex".equals(targetType) && "complex".equals(opType)) code.addInstruction(new ComplexInstrs.Div());
                                else if ("polar".equals(targetType) && "polar".equals(opType)) code.addInstruction(new PolarInstrs.Div());
                                else if (opType == null || "float".equals(opType) || "int".equals(opType)) {
                                    code.addInstruction(new PushInstrs.PushFloat(1.0));
                                    code.addInstruction(new StackInstrs.Swap());
                                    code.addInstruction(new ArithmeticInstrs.DivideAny());
                                    code.addInstruction(new VecInstrs.VecScale());
                                }
                                else code.addInstruction(new ArithmeticInstrs.DivideAny());
                            }
                            default -> code.addInstruction(new ArithmeticInstrs.AddAny());
                        }
                    } else {
                        switch (arith) {
                            case PLUS ->
                                code.addInstruction(new ArithmeticInstrs.AddAny());
                            case MINUS ->
                                code.addInstruction(new ArithmeticInstrs.MinusAny());
                            case TIMES ->
                                code.addInstruction(new ArithmeticInstrs.TimesAny());
                            case DIVIDE ->
                                code.addInstruction(new ArithmeticInstrs.DivideAny());
                            case PERCENT ->
                                code.addInstruction(new ArithmeticInstrs.ModuloAny());
                            default -> {
                            }
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
                        code.addInstruction(new ArithmeticInstrs.AddAny());
                    } else {
                        code.addInstruction(new ArithmeticInstrs.MinusAny());
                    }
                    emitChuckTarget(e.rhs(), code); // store new value, pushes new value back
                    code.addInstruction(new StackInstrs.Pop());  // discard new value, old value remains
                } else if (e.op() == ChuckAST.Operator.WRITE_IO) {
                    emitExpression(e.rhs(), code);
                    emitExpression(e.lhs(), code);
                    code.addInstruction(new MiscInstrs.ChuckWriteIO());
                } else if (e.op() == ChuckAST.Operator.SWAP) {
                    emitSwapTarget(e.lhs(), e.rhs(), code);
                } else if (e.op() == ChuckAST.Operator.AT_CHUCK) {
                    emitExpression(e.lhs(), code);
                    switch (e.rhs()) {
                        case ChuckAST.IdExp id -> {
                            Integer lo = getLocalOffset(id.name());
                            if (lo != null) {
                                code.addInstruction(new VarInstrs.StoreLocal(lo));
                            } else {
                                // For @=>, we want direct assignment to the global
                                code.addInstruction(new VarInstrs.SetGlobalObjectOnly(id.name()));
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
                                    code.addInstruction(new ArrayInstrs.GetArrayInt());
                                } else {
                                    code.addInstruction(new ArrayInstrs.SetArrayInt());
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
                                code.addInstruction(new VarInstrs.StoreLocal(localOffset));
                            } else {
                                code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(id.name()));
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
                                    code.addInstruction(new ArrayInstrs.GetArrayInt());
                                } else {
                                    code.addInstruction(new ArrayInstrs.SetArrayInt());
                                }
                            }
                        }
                        default -> {
                        }
                    }
                } else if (e.op() == ChuckAST.Operator.DUR_MUL) {
                    emitExpression(e.lhs(), code);
                    if (e.rhs() instanceof ChuckAST.IdExp id) {
                        code.addInstruction(new MiscInstrs.CreateDuration(id.name()));
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
                                    List<String> argTypes = List.of(getExprType(e.rhs()));
                                    String pubKey = resolveMethodKey(lhsType, "__pub_op__" + opSymbol, argTypes);
                                    String privKey = resolveMethodKey(lhsType, "__op__" + opSymbol, argTypes);
                                    
                                    if (desc.methods().containsKey(pubKey) || desc.staticMethods().containsKey(pubKey)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new ObjectInstrs.CallMethod("__pub_op__" + opSymbol, 1, pubKey));
                                        return;
                                    }
                                    if (desc.methods().containsKey(privKey) || desc.staticMethods().containsKey(privKey)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new ObjectInstrs.CallMethod("__op__" + opSymbol, 1, privKey));
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
                                    isPolar ? new PolarInstrs.Add() : new ComplexInstrs.Add();
                                case MINUS ->
                                    isPolar ? new PolarInstrs.Sub() : new ComplexInstrs.Sub();
                                case TIMES ->
                                    isPolar ? new PolarInstrs.Mul() : new ComplexInstrs.Mul();
                                case DIVIDE ->
                                    isPolar ? new PolarInstrs.Div() : new ComplexInstrs.Div();
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
                    // Check for scalar * vec (scalar on left, vec on right)
                    {
                        String lhsTypeS = getExprType(e.lhs());
                        String rhsTypeS = getExprType(e.rhs());
                        if (e.op() == ChuckAST.Operator.TIMES
                                && ("float".equals(lhsTypeS) || "int".equals(lhsTypeS))
                                && isVecType(rhsTypeS)) {
                            // Emit: vec first (stack expects vec then scalar for VecScale)
                            emitExpression(e.rhs(), code);
                            emitExpression(e.lhs(), code);
                            code.addInstruction(new VecInstrs.VecScale());
                            return;
                        }
                    }
                    // Check for vec/complex/polar element-wise arithmetic
                    {
                        String lhsType = getExprType(e.lhs());
                        if (isVecType(lhsType)) {
                            switch (e.op()) {
                                case PLUS -> {
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    if ("complex".equals(lhsType)) code.addInstruction(new ComplexInstrs.Add());
                                    else if ("polar".equals(lhsType)) code.addInstruction(new PolarInstrs.Add());
                                    else code.addInstruction(new VecInstrs.Add());
                                    return;
                                }
                                case MINUS -> {
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    if ("complex".equals(lhsType)) code.addInstruction(new ComplexInstrs.Sub());
                                    else if ("polar".equals(lhsType)) code.addInstruction(new PolarInstrs.Sub());
                                    else code.addInstruction(new VecInstrs.Sub());
                                    return;
                                }
                                case TIMES -> {
                                    String rhsType = getExprType(e.rhs());
                                    if ("complex".equals(lhsType) && "complex".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new ComplexInstrs.Mul());
                                        return;
                                    }
                                    if ("polar".equals(lhsType) && "polar".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new PolarInstrs.Mul());
                                        return;
                                    }
                                    // vec * scalar or complex * scalar
                                    if (rhsType == null || "float".equals(rhsType) || "int".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new VecInstrs.VecScale());
                                        return;
                                    }
                                    // vec3/vec4 * vec3/vec4 → cross product
                                    if (("vec3".equals(lhsType) || "vec4".equals(lhsType))
                                            && ("vec3".equals(rhsType) || "vec4".equals(rhsType))) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new VecInstrs.Cross());
                                        return;
                                    }
                                    // vec * vec → dot product (returns scalar)
                                    emitExpression(e.lhs(), code);
                                    emitExpression(e.rhs(), code);
                                    code.addInstruction(new VecInstrs.Dot());
                                    return;
                                }
                                case DIVIDE -> {
                                    String rhsType = getExprType(e.rhs());
                                    if ("complex".equals(lhsType) && "complex".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new ComplexInstrs.Div());
                                        return;
                                    }
                                    if ("polar".equals(lhsType) && "polar".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new PolarInstrs.Div());
                                        return;
                                    }
                                    // complex / scalar
                                    if (rhsType == null || "float".equals(rhsType) || "int".equals(rhsType)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new PushInstrs.PushFloat(1.0));
                                        code.addInstruction(new StackInstrs.Swap());
                                        code.addInstruction(new ArithmeticInstrs.DivideAny());
                                        code.addInstruction(new VecInstrs.VecScale());
                                        return;
                                    }
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
                            code.addInstruction(new ArithmeticInstrs.AddAny());
                        case MINUS ->
                            code.addInstruction(new ArithmeticInstrs.MinusAny());
                        case TIMES ->
                            code.addInstruction(new ArithmeticInstrs.TimesAny());
                        case DIVIDE ->
                            code.addInstruction(new ArithmeticInstrs.DivideAny());
                        case PERCENT ->
                            code.addInstruction(new ArithmeticInstrs.ModuloAny());
                        case S_OR ->
                            code.addInstruction(new ArithmeticInstrs.BitwiseOrAny());
                        case S_AND ->
                            code.addInstruction(new ArithmeticInstrs.BitwiseAndAny());
                        case LT ->
                            code.addInstruction(new LogicInstrs.LessThanAny());
                        case LE -> {
                            String lt = getExprType(e.lhs());
                            if ("IO".equals(lt) || "FileIO".equals(lt)) {
                                code.addInstruction(new MiscInstrs.ChuckWriteIO());
                            } else {
                                code.addInstruction(new LogicInstrs.LessOrEqualAny());
                            }
                        }
                        case GT ->
                            code.addInstruction(new LogicInstrs.GreaterThanAny());                        case GE ->
                            code.addInstruction(new LogicInstrs.GreaterOrEqualAny());
                        case EQ ->
                            code.addInstruction(new LogicInstrs.EqualsAny());
                        case NEQ ->
                            code.addInstruction(new LogicInstrs.NotEqualsAny());
                        case DUR_MUL -> {
                            emitExpression(e.lhs(), code);
                            if (e.rhs() instanceof ChuckAST.IdExp id) {
                                code.addInstruction(new MiscInstrs.CreateDuration(id.name()));
                            } else {
                                emitExpression(e.rhs(), code);
                                // Fallback?
                            }
                        }
                        case WRITE_IO -> {
                            code.addInstruction(new MiscInstrs.ChuckWriteIO());
                        }
                        case AND -> {
                            String lt = getExprType(e.lhs());
                            String rt = getExprType(e.rhs());
                            if ("Event".equals(lt) || "Event".equals(rt)) {
                                emitExpression(e.lhs(), code);
                                emitExpression(e.rhs(), code);
                                code.addInstruction(new MiscInstrs.CreateEventConjunction());
                            } else {
                                emitExpression(e.lhs(), code);
                                int jumpIdx = code.getNumInstructions();
                                code.addInstruction(null); // placeholder for JumpIfFalse
                                emitExpression(e.rhs(), code);
                                int endIdx = code.getNumInstructions();
                                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalseAndPushFalse(endIdx));
                            }
                        }
                        case OR -> {
                            String lt = getExprType(e.lhs());
                            String rt = getExprType(e.rhs());
                            if ("Event".equals(lt) || "Event".equals(rt)) {
                                emitExpression(e.lhs(), code);
                                emitExpression(e.rhs(), code);
                                code.addInstruction(new MiscInstrs.CreateEventDisjunction());
                            } else {
                                emitExpression(e.lhs(), code);
                                int jumpIdx = code.getNumInstructions();
                                code.addInstruction(null); // placeholder for JumpIfTrue
                                emitExpression(e.rhs(), code);
                                int endIdx = code.getNumInstructions();
                                code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfTrueAndPushTrue(endIdx));
                            }
                        }
                        case SHIFT_LEFT -> {
                            String lhsElemType = getExprType(e.lhs());
                            code.addInstruction(new ArrayInstrs.ShiftLeftOrAppend(lhsElemType));
                        }
                        default -> {
                        }
                    }
                }
            }
            case ChuckAST.IdExp e -> {
                Integer localOffset = getLocalOffset(e.name());
                String varType = getVarType(e);
                
                if (e.name().equals("this") && currentClass == null) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: keyword 'this' cannot be used outside class definition");
                }
                if (e.name().equals("super") && inStaticFuncContext) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                            + ": error: keyword 'super' cannot be used inside static functions");
                }
                
                if (localOffset != null) {
                    code.addInstruction(new VarInstrs.LoadLocal(localOffset));
                } else if (varType != null) {
                    // It's a known variable (global), use GetGlobalObjectOrInt
                    code.addInstruction(new VarInstrs.GetGlobalObjectOrInt(e.name()));
                } else if (e.name().equals("null")) {
                    code.addInstruction(new PushInstrs.PushNull());
                } else if (e.name().equals("true")) {
                    code.addInstruction(new PushInstrs.PushInt(1));
                } else if (e.name().equals("false")) {
                    code.addInstruction(new PushInstrs.PushInt(0));
                } else if (e.name().equals("now")) {
                    code.addInstruction(new PushInstrs.PushNow());
                } else if (e.name().equals("dac")) {
                    code.addInstruction(new PushInstrs.PushDac());
                } else if (e.name().equals("blackhole")) {
                    code.addInstruction(new PushInstrs.PushBlackhole());
                } else if (e.name().equals("adc")) {
                    code.addInstruction(new PushInstrs.PushAdc());
                } else if (e.name().equals("me")) {
                    code.addInstruction(new PushInstrs.PushMe());
                } else if (e.name().equals("cherr")) {
                    code.addInstruction(new PushInstrs.PushCherr());
                } else if (e.name().equals("chout")) {
                    code.addInstruction(new PushInstrs.PushChout());
                } else if (e.name().equals("Machine")) {
                    code.addInstruction(new PushInstrs.PushMachine());
                } else if (e.name().equals("maybe")) {
                    code.addInstruction(new PushInstrs.PushMaybe());
                } else if (e.name().equals("this")) {
                    code.addInstruction(new StackInstrs.PushThis());
                } else if (e.name().equals("second") || e.name().equals("ms") || e.name().equals("samp")
                        || e.name().equals("minute") || e.name().equals("hour")) {
                    code.addInstruction(new PushInstrs.PushInt(1));
                    code.addInstruction(new MiscInstrs.CreateDuration(e.name()));
                } else if (e.name().equals("pi")) {
                    code.addInstruction(new PushInstrs.PushFloat(Math.PI));
                } else if (e.name().equals("e")) {
                    code.addInstruction(new PushInstrs.PushFloat(Math.E));
                } else if (currentClassFields.contains(e.name())) {
                    code.addInstruction(new FieldInstrs.GetUserField(e.name()));
                } else if (currentClass != null && userClassRegistry.get(currentClass) != null
                        && (userClassRegistry.get(currentClass).staticInts().containsKey(e.name())
                        || userClassRegistry.get(currentClass).staticObjects().containsKey(e.name()))) {
                    code.addInstruction(new FieldInstrs.GetStatic(currentClass, e.name()));
                } else {
                    code.addInstruction(new VarInstrs.GetGlobalObjectOrInt(e.name()));
                }
            }
            case ChuckAST.DotExp e -> {
                // super.field — access field on 'this' (fields are per-instance, not per-class)
                if (e.base() instanceof ChuckAST.IdExp supId && supId.name().equals("super")) {
                    code.addInstruction(new StackInstrs.PushThis());
                    code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
                    return;
                }
                // Handle static field access: ClassName.staticField
                if (e.base() instanceof ChuckAST.IdExp(String potentialClassName, _, _)) {
                    UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                    if (classDesc != null && (classDesc.staticInts().containsKey(e.member()) || classDesc.staticObjects().containsKey(e.member()))) {
                        code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, e.member()));
                        return;
                    }
                }
                if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("IO")) {
                    if (e.member().equals("nl") || e.member().equals("newline")) {
                        code.addInstruction(new PushInstrs.PushString("\n"));
                    } else {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.ChuckIO", e.member()));
                    }
                    return;
                }
                if (e.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                    switch (e.member()) {
                        case "INFINITY", "infinity" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Double.POSITIVE_INFINITY));
                            return;
                        }
                        case "NEGATIVE_INFINITY" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Double.NEGATIVE_INFINITY));
                            return;
                        }
                        case "NaN", "nan" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Double.NaN));
                            return;
                        }
                        case "PI", "pi" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Math.PI));
                            return;
                        }
                        case "TWO_PI", "two_pi" -> {
                            code.addInstruction(new PushInstrs.PushFloat(2.0 * Math.PI));
                            return;
                        }
                        case "HALF_PI", "half_pi" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Math.PI / 2.0));
                            return;
                        }
                        case "E", "e" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Math.E));
                            return;
                        }
                        case "SQRT2", "sqrt2" -> {
                            code.addInstruction(new PushInstrs.PushFloat(Math.sqrt(2.0)));
                            return;
                        }
                        case "j" -> {
                            code.addInstruction(new PushInstrs.PushFloat(0.0));
                            code.addInstruction(new PushInstrs.PushFloat(1.0));
                            code.addInstruction(new ArrayInstrs.NewArrayFromStack(2, "complex"));
                            return;
                        }
                    }
                }
                if (e.member().equals("size")) {
                    emitExpression(e.base(), code);
                    code.addInstruction(new ObjectInstrs.CallMethod("size", 0));
                    return;
                } else if (e.member().equals("zero")) {
                    emitExpression(e.base(), code);
                    code.addInstruction(new ArrayInstrs.ArrayZero());
                    // ChucK's .zero() returns the array itself for chaining
                    return;
                }
                if (e.base() instanceof ChuckAST.IdExp id) {
                    String baseType = getExprType(e.base());
                    if (baseType != null && userClassRegistry.containsKey(baseType)) {
                        UserClassDescriptor d = userClassRegistry.get(baseType);
                        if (d.staticInts().containsKey(e.member()) || d.staticObjects().containsKey(e.member())) {
                            code.addInstruction(new FieldInstrs.GetStatic(baseType, e.member()));
                            return;
                        }
                    }
                    if (id.name().equals("ADSR") || id.name().equals("Adsr")) {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.audio.Adsr", e.member()));
                        return;
                    }
                    if (id.name().equals("Std")) {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.Std", e.member()));
                        return;
                    }
                    if (id.name().equals("RegEx")) {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.RegEx", e.member()));
                        return;
                    }
                    if (id.name().equals("Reflect")) {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.Reflect", e.member()));
                        return;
                    }
                    if (id.name().equals("SerialIO")) {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.SerialIO", e.member()));
                        return;
                    }
                    if (id.name().equals("FileIO")) {
                        code.addInstruction(new FieldInstrs.GetBuiltinStatic("org.chuck.core.FileIO", e.member()));
                        return;
                    }
                    if (userClassRegistry.containsKey(id.name())) {
                        UserClassDescriptor d = userClassRegistry.get(id.name());
                        if (d.staticInts().containsKey(e.member()) || d.staticObjects().containsKey(e.member())) {
                            code.addInstruction(new FieldInstrs.GetStatic(id.name(), e.member()));
                            return;
                        }
                    }
                    if (currentClass != null && userClassRegistry.containsKey(currentClass)) {
                        UserClassDescriptor d = userClassRegistry.get(currentClass);
                        if (d.staticObjects().containsKey(id.name())) {
                            code.addInstruction(new FieldInstrs.GetStatic(currentClass, id.name()));
                            code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
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
                            code.addInstruction(new PushInstrs.PushInt(vecIdx));
                            code.addInstruction(new ArrayInstrs.GetArrayInt());
                            return;
                        }
                    }
                }
                emitExpression(e.base(), code);
                code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
            }
            case ChuckAST.ArrayLitExp e -> {
                for (ChuckAST.Exp el : e.elements()) {
                    emitExpression(el, code);
                }
                code.addInstruction(new ArrayInstrs.NewArrayFromStack(e.elements().size()));
            }
            case ChuckAST.VectorLitExp e -> {
                for (ChuckAST.Exp el : e.elements()) {
                    emitExpression(el, code);
                    code.addInstruction(new TypeInstrs.EnsureFloat());
                }
                String vTag = switch (e.elements().size()) {
                    case 2 -> "vec2";
                    case 3 -> "vec3";
                    case 4 -> "vec4";
                    default -> null;
                };
                code.addInstruction(new ArrayInstrs.NewArrayFromStack(e.elements().size(), vTag));
            }
            case ChuckAST.ComplexLit e -> {
                emitExpression(e.re(), code);
                emitExpression(e.im(), code);
                code.addInstruction(new ArrayInstrs.NewArrayFromStack(2, "complex"));
            }
            case ChuckAST.PolarLit e -> {
                emitExpression(e.mag(), code);
                emitExpression(e.phase(), code);
                code.addInstruction(new ArrayInstrs.NewArrayFromStack(2, "polar"));
            }
            case ChuckAST.ArrayAccessExp e -> {
                emitExpression(e.base(), code);
                for (ChuckAST.Exp index : e.indices()) {
                    emitExpression(index, code);
                    code.addInstruction(new ArrayInstrs.GetArrayInt());
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
                        code.addInstruction(new PushInstrs.PushString("\n"));
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
                        code.addInstruction(new MathInstrs.StdFunc(member, e.args().size()));
                    } else {
                        // General fallback: try CallBuiltinStatic for Std
                        for (ChuckAST.Exp arg : e.args()) {
                            emitExpression(arg, code);
                        }
                        code.addInstruction(new ObjectInstrs.CallBuiltinStatic("org.chuck.core.Std", dot.member(), e.args().size()));
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("RegEx")) {
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new ObjectInstrs.CallBuiltinStatic("org.chuck.core.RegEx", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Reflect")) {
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new ObjectInstrs.CallBuiltinStatic("org.chuck.core.Reflect", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("SerialIO")) {
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new ObjectInstrs.CallBuiltinStatic("org.chuck.core.SerialIO", dot.member(), e.args().size()));
                } else if (e.base() instanceof ChuckAST.DotExp dot && dot.member().equals("last")) {
                    emitExpression(dot.base(), code);
                    code.addInstruction(new UgenInstrs.GetLastOut());
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                    switch (dot.member()) {
                        case "random", "randf" ->
                            code.addInstruction(new MathInstrs.MathRandom());
                        case "sin" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("sin"));
                            }
                        }
                        case "cos" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("cos"));
                            }
                        }
                        case "pow" -> {
                            if (e.args().size() >= 2) {
                                emitExpression(e.args().get(0), code);
                                emitExpression(e.args().get(1), code);
                                code.addInstruction(new MathInstrs.MathFunc("pow"));
                            }
                        }
                        case "sqrt" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("sqrt"));
                            }
                        }
                        case "abs" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("abs"));
                            }
                        }
                        case "floor" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("floor"));
                            }
                        }
                        case "ceil" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("ceil"));
                            }
                        }
                        case "equal" -> {
                            if (e.args().size() >= 2) {
                                emitExpression(e.args().get(0), code);
                                emitExpression(e.args().get(1), code);
                                code.addInstruction(new MathInstrs.MathFunc("equal"));
                            }
                        }
                        case "euclidean" -> {
                            if (e.args().size() >= 2) {
                                emitExpression(e.args().get(0), code);
                                emitExpression(e.args().get(1), code);
                                code.addInstruction(new MathInstrs.MathFunc("euclidean"));
                            }
                        }
                        case "srandom" -> {
                            emitExpression(e.args().get(0), code);
                            code.addInstruction(new MathInstrs.MathFunc("srandom"));
                        }
                        case "srandom_init", "randomize" ->
                            code.addInstruction(new MathInstrs.MathFunc("srandom"));
                        case "isinf" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("isinf"));
                            }
                        }
                        case "isnan" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("isnan"));
                            }
                        }
                        case "log" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("log"));
                            }
                        }
                        case "log2" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("log2"));
                            }
                        }
                        case "log10" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("log10"));
                            }
                        }
                        case "exp" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("exp"));
                            }
                        }
                        case "round" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("round"));
                            }
                        }
                        case "trunc" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                                code.addInstruction(new MathInstrs.MathFunc("trunc"));
                            }
                        }
                        case "help" ->
                            code.addInstruction(new MathInstrs.MathHelp());
                        default -> {
                            if (!e.args().isEmpty()) {
                                for (ChuckAST.Exp arg : e.args()) {
                                    emitExpression(arg, code);
                                }
                                code.addInstruction(new MathInstrs.MathFunc(dot.member()));
                            } else {
                                code.addInstruction(new MathInstrs.MathHelp());
                            }
                        }
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Machine")) {
                    String machMember = dot.member();
                    switch (machMember) {
                        case "realtime" -> code.addInstruction(new PushInstrs.PushInt(0));
                        case "silent" -> code.addInstruction(new PushInstrs.PushInt(1));
                        default -> {
                            for (ChuckAST.Exp arg : e.args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new org.chuck.core.instr.MachineCall(machMember, e.args().size()));
                        }
                    }
                } else if (e.base() instanceof ChuckAST.DotExp dot
                        && (dot.base() instanceof ChuckAST.MeExp || (dot.base() instanceof ChuckAST.IdExp idMe && idMe.name().equals("me")))) {
                    switch (dot.member()) {
                        case "yield" -> code.addInstruction(new MiscInstrs.Yield());
                        case "dir" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                            } else {
                                code.addInstruction(new PushInstrs.PushInt(0));
                            }
                            code.addInstruction(new MeInstrs.MeDir());
                        }
                        case "args" -> code.addInstruction(new MeInstrs.MeArgs());
                        case "arg" -> {
                            if (!e.args().isEmpty()) {
                                emitExpression(e.args().get(0), code);
                            } else {
                                code.addInstruction(new PushInstrs.PushInt(0));
                            }
                            code.addInstruction(new MeInstrs.MeArg());
                        }
                        case "id" -> code.addInstruction(new MeInstrs.MeId());
                        case "exit" -> code.addInstruction(new MeInstrs.MeExit());
                        default -> {
                            code.addInstruction(new PushInstrs.PushMe());
                            for (ChuckAST.Exp arg : e.args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), e.args().size()));
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
                            code.addInstruction(new ObjectInstrs.CallSuperMethod(parentName, dot.member(), e.args().size()));
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
                    List<String> argTypes = e.args().stream().map(this::getExprType).toList();
                    String baseType = getExprType(dot.base());
                    if (baseType != null && (baseType.equals("vec2") || baseType.equals("vec3") || baseType.equals("vec4")
                            || baseType.equals("complex") || baseType.equals("polar")) && dot.member().equals("dot")) {
                        emitExpression(dot.base(), code);
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new VecInstrs.Dot());
                        return;
                    }
                    
                    if (baseType == null && dot.base() instanceof ChuckAST.IdExp idBase && userClassRegistry.containsKey(idBase.name())) {
                        baseType = idBase.name();
                    }
                    String resolvedKey = (baseType != null) ? resolveMethodKey(baseType, dot.member(), argTypes) : getMethodKey(dot.member(), argTypes);
                    
                    // One last check: if we found a base class name
                    if (baseType != null && userClassRegistry.containsKey(baseType)) {
                        ChuckCode target = resolveStaticMethod(baseType, resolvedKey);
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new CallFunc(target, e.args().size()));
                            return;
                        }
                    }

                    emitExpression(dot.base(), code);
                    for (ChuckAST.Exp arg : e.args()) {
                        emitExpression(arg, code);
                    }
                    code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), e.args().size(), resolvedKey));
                } else if (e.base() instanceof ChuckAST.IdExp id) {
                    List<String> argTypes = e.args().stream().map(this::getExprType).toList();
                    String key = getMethodKey(id.name(), argTypes);

                    // Constructor chain: Foo(args) or this(args) from within a method of class Foo
                    if (currentClass != null && (id.name().equals(currentClass) || id.name().equals("this"))) {
                        String ctorKey = getMethodKey(currentClass, argTypes);
                        UserClassDescriptor cd = userClassRegistry.get(currentClass);
                        if (cd != null && cd.methods().containsKey(ctorKey)) {
                            code.addInstruction(new StackInstrs.PushThis());
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new ObjectInstrs.CallMethod(currentClass, e.args().size(), ctorKey));
                            return;
                        }
                    }

                    if (currentClass != null && userClassRegistry.containsKey(currentClass)) {
                        String resolvedKey = resolveMethodKey(currentClass, id.name(), argTypes);
                        ChuckCode target = resolveStaticMethod(currentClass, resolvedKey);
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
                    } else {
                        // Try fallback to name:argc
                        String fallbackKey = id.name() + ":" + e.args().size();
                        if (functions.containsKey(fallbackKey)) {
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new CallFunc(functions.get(fallbackKey), e.args().size()));
                        }
                    }
                }
            }
            case ChuckAST.SporkExp e -> {
                String funcName = null;
                switch (e.call().base()) {
                    case ChuckAST.IdExp id -> {
                        funcName = id.name();
                        List<String> argTypes = e.call().args().stream().map(this::getExprType).toList();
                        String key = getMethodKey(funcName, argTypes);

                        ChuckCode target = resolveStaticMethod(currentClass, key);
                        if (target == null) {
                            // Try fallback to name:argc
                            String fallbackKey = funcName + ":" + e.call().args().size();
                            target = resolveStaticMethod(currentClass, fallbackKey);
                        }
                        
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.call().args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new ObjectInstrs.Spork(target, e.call().args().size()));
                            return;
                        }

                        if (functions.containsKey(key)) {
                            for (ChuckAST.Exp arg : e.call().args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new ObjectInstrs.Spork(functions.get(key), e.call().args().size()));
                            return;
                        }
                        
                        // Try fallback for regular functions
                        String fallbackKey = funcName + ":" + e.call().args().size();
                        if (functions.containsKey(fallbackKey)) {
                            for (ChuckAST.Exp arg : e.call().args()) {
                                emitExpression(arg, code);
                            }
                            code.addInstruction(new ObjectInstrs.Spork(functions.get(fallbackKey), e.call().args().size()));
                            return;
                        }
                    }
                    case ChuckAST.DotExp dot -> {
                        List<String> argTypes = e.call().args().stream().map(this::getExprType).toList();
                        String baseClassName = null;
                        if (dot.base() instanceof ChuckAST.IdExp id && userClassRegistry.containsKey(id.name())) {
                            baseClassName = id.name();
                        } else {
                            String bt = getExprType(dot.base());
                            if (bt != null && userClassRegistry.containsKey(bt)) {
                                baseClassName = bt;
                            }
                        }

                        if (baseClassName != null) {
                            String resolvedKey = resolveMethodKey(baseClassName, dot.member(), argTypes);
                            ChuckCode target = resolveStaticMethod(baseClassName, resolvedKey);
                            if (target != null) {
                                for (ChuckAST.Exp arg : e.call().args()) {
                                    emitExpression(arg, code);
                                }
                                code.addInstruction(new ObjectInstrs.Spork(target, e.call().args().size()));
                                return;
                            }
                        }
                        
                        emitExpression(dot.base(), code);
                        for (ChuckAST.Exp arg : e.call().args()) {
                            emitExpression(arg, code);
                        }
                        String baseType = getExprType(dot.base());
                        String resolvedKey = (baseType != null) ? resolveMethodKey(baseType, dot.member(), argTypes) : getMethodKey(dot.member(), argTypes);
                        code.addInstruction(new ObjectInstrs.SporkMethod(dot.member(), e.call().args().size(), resolvedKey));
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
                code.replaceInstruction(jumpFalseIdx, new ControlInstrs.JumpIfFalse(code.getNumInstructions()));
                emitExpression(e.elseExp(), code);
                code.replaceInstruction(jumpEndIdx, new ControlInstrs.Jump(code.getNumInstructions()));
            }
            case ChuckAST.CastExp e -> {
                emitExpression(e.value(), code);
                switch (e.targetType()) {
                    case "int" -> code.addInstruction(new TypeInstrs.CastToInt());
                    case "float" -> code.addInstruction(new TypeInstrs.CastToFloat());
                    case "string" -> code.addInstruction(new TypeInstrs.CastToString());
                    case "complex" -> code.addInstruction(new TypeInstrs.CastToComplex());
                    case "polar" -> code.addInstruction(new TypeInstrs.CastToPolar());
                    // other types: leave value as-is (e.g. casting to a class type is a no-op)
                }
            }
            case ChuckAST.TypeofExp e -> {
                emitExpression(e.expr(), code);
                code.addInstruction(new TypeInstrs.TypeofInstr());
            }
            case ChuckAST.InstanceofExp e -> {
                emitExpression(e.expr(), code);
                code.addInstruction(new TypeInstrs.InstanceofInstr(e.typeName()));
            }
        }
    }

    private void emitChuckTarget(Object target, ChuckCode code) {
        if (target instanceof List<?> list) {
            if (list.size() > 1) {
                // Find first element to get line/column
                int line = 0, col = 0;
                if (!list.isEmpty() && list.get(0) instanceof ChuckAST.DeclExp de) {
                    line = de.line(); col = de.column();
                }
                throw new RuntimeException(currentFile + ":" + line + ":" + col + ": error: cannot '=>' from/to a multi-variable declaration");
            }
            if (!list.isEmpty()) emitChuckTarget(list.get(0), code);
            return;
        }
        
        if (!(target instanceof ChuckAST.Exp)) return;
        ChuckAST.Exp exp = (ChuckAST.Exp) target;

        switch (exp) {
            case ChuckAST.IdExp e -> {
                if (e.name().equals("pi") || e.name().equals("e") || e.name().equals("maybe") || 
                    e.name().equals("true") || e.name().equals("false") || e.name().equals("null") ||
                    constants.contains(e.name())) {
                    throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column() + ": error: cannot assign to read-only value '" + e.name() + "'");
                }
                String type = getVarType(exp);
                boolean isUGen = type != null && (isKnownUGenType(type) || isSubclassOfUGen(type));

                if (e.name().equals("now")) {
                    code.addInstruction(new AdvanceTime());
                    return;
                }
                if (e.name().equals("dac")) {
                    code.addInstruction(new UgenInstrs.ConnectToDac());
                    return;
                }
                if (e.name().equals("blackhole")) {
                    code.addInstruction(new UgenInstrs.ConnectToBlackhole());
                    return;
                }
                if (e.name().equals("adc")) {
                    code.addInstruction(new UgenInstrs.ConnectToAdc());
                    return;
                }

                if (isUGen) {
                    // If target is a UGen variable, we do a ChuckTo connection
                    emitExpression(exp, code);
                    code.addInstruction(new org.chuck.core.ChuckTo());
                } else {
                    // Otherwise it's a value assignment
                    Integer localOffset = getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new VarInstrs.StoreLocal(localOffset));
                    } else if (currentClassFields.contains(e.name())) {
                        code.addInstruction(new FieldInstrs.SetUserField(e.name()));
                    } else {
                        code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(e.name()));
                    }
                }
            }
            case ChuckAST.DotExp e -> {
                // Handle static field target: ClassName.staticField
                if (e.base() instanceof ChuckAST.IdExp(String potentialClassName, int line, int column)) {
                    if (userClassRegistry.containsKey(potentialClassName)) {
                        UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                        if (classDesc != null && (classDesc.staticInts().containsKey(e.member()) || classDesc.staticObjects().containsKey(e.member()))) {
                            code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, e.member()));
                            return;
                        }
                    }
                }
                if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("mtof")) {
                    code.addInstruction(new MathInstrs.StdFunc("mtof", 1));
                } else if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("ftom")) {
                    code.addInstruction(new MathInstrs.StdFunc("ftom", 1));
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
                            code.addInstruction(new MathInstrs.MathFunc(e.member()));
                        default ->
                            code.addInstruction(new MathInstrs.MathFunc(e.member()));
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
                            code.addInstruction(new PushInstrs.PushInt(vecIdx));
                            code.addInstruction(new ArrayInstrs.SetArrayInt());
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
                    code.addInstruction(new ArrayInstrs.GetArrayInt());
                }
                emitExpression(e.indices().get(e.indices().size() - 1), code);
                code.addInstruction(new ArrayInstrs.SetArrayInt());
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
                    code.addInstruction(new StackInstrs.Pop());
                    // Stack is now: [..., source]
                }

                // Now save the target object to its variable
                Integer localOffset = getLocalOffset(e.name());
                if (localOffset != null) {
                    code.addInstruction(new VarInstrs.StoreLocal(localOffset));
                } else {
                    code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(e.name()));
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
                    emitExpression(exp, code);
                }
            }
            default ->
                emitExpression(exp, code);
        }
    }

    private void emitSwapTarget(ChuckAST.Exp lhs, ChuckAST.Exp rhs, ChuckCode code) {
        if (lhs instanceof ChuckAST.IdExp l && rhs instanceof ChuckAST.IdExp r) {
            Integer lo = getLocalOffset(l.name()), ro = getLocalOffset(r.name());
            if (lo != null && ro != null) {
                code.addInstruction(new VarInstrs.SwapLocal(lo, ro));
            } else if (lo == null && ro == null) {
                code.addInstruction(new org.chuck.core.ChuckSwap(l.name(), r.name(), false));
            } else {
                emitExpression(lhs, code);
                emitExpression(rhs, code);
                code.addInstruction(new StackInstrs.Swap());
                code.addInstruction(new VarInstrs.StoreLocalOrGlobal(r.name(), ro));
                code.addInstruction(new StackInstrs.Pop());
                code.addInstruction(new VarInstrs.StoreLocalOrGlobal(l.name(), lo));
            }
        } else {
            emitExpression(lhs, code);
            emitExpression(rhs, code);
            code.addInstruction(new StackInstrs.Swap());
            if (rhs instanceof ChuckAST.IdExp rid) {
                code.addInstruction(new VarInstrs.StoreLocalOrGlobal(rid.name(), getLocalOffset(rid.name())));
            }
            code.addInstruction(new StackInstrs.Pop());
            if (lhs instanceof ChuckAST.IdExp lid) {
                code.addInstruction(new VarInstrs.StoreLocalOrGlobal(lid.name(), getLocalOffset(lid.name())));
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
}
