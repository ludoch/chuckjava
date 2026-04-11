import sys

content = r"""package org.chuck.compiler;

import org.chuck.compiler.ChuckANTLRParser.*;
import org.chuck.core.*;
import org.chuck.core.instr.*;
import org.chuck.core.instr.FieldInstrs.SetMemberIntByName;
import org.chuck.core.instr.ControlInstrs.CallFunc;
import org.chuck.core.instr.ControlInstrs.ReturnFunc;
import org.chuck.core.instr.TimeInstrs.AdvanceTime;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Emits ChuckCode from ChuckAST.
 * Production Grade Emitter.
 */
public class ChuckEmitter {

    private final Map<String, UserClassDescriptor> userClassRegistry;
    private final Map<String, ChuckCode> functions;
    private final Map<String, String> functionReturnTypes = new HashMap<>();
    private final Map<String, String> globalVarTypes = new HashMap<>();
    private final Deque<Map<String, Integer>> localScopes = new ArrayDeque<>();
    private final Deque<Map<String, String>> localTypeScopes = new ArrayDeque<>();
    private int localCount = 0;
    private String currentClass = null;
    private java.util.Set<String> currentClassFields = new HashSet<>();
    private List<ChuckAST.FuncDefStmt> currentClassMethodsList = new ArrayList<>();
    private String currentFuncReturnType = "void";
    private boolean currentFuncHasReturn = false;
    private boolean inStaticFuncContext = false;
    private boolean inPreCtor = false;
    private String currentFile = "unknown";

    private final Deque<List<Integer>> breakJumps = new ArrayDeque<>();
    private final Deque<List<Integer>> continueJumps = new ArrayDeque<>();

    private final java.util.Set<String> constants = java.util.Set.of("pi", "e", "maybe", "true", "false", "null", "now", "dac", "adc", "blackhole");

    private static final java.util.Set<String> CORE_UGENS = java.util.Set.of(
            "SinOsc", "TriOsc", "SawOsc", "PulseOsc", "SqrOsc", "Noise", "Impulse", "Step",
            "Gain", "Envelope", "ADSR", "Delay", "DelayL", "DelayA", "Echo", "JCRev", "NRev", "PRCRev",
            "Chorus", "Modulate", "PitShift", "SubNoise", "BLT", "Blit", "BlitSaw", "BlitSquare", "BlitPulse",
            "OnePole", "TwoPole", "OneZero", "TwoZero", "PoleZero", "BiQuad", "BPF", "BRF", "LPF", "HPF", "ResonZ",
            "Filter", "FilterBasic", "FilterStk", "BiQuadStk", "StkInstrument", "Clarinet", "Flute", "Brass", "Saxofony",
            "BowTable", "JetTable", "ReedTable", "WaveLoop", "Wurley", "Rhodey", "Mandolin", "Sitar", "Shakers",
            "Moog", "VoicForm", "ModalBar", "FMVoices", "BeeThree", "HeP3", "Plucked", "StifKarp", "SndBuf", "SndBuf2", "LiSa",
            "Dac", "Adc", "Blackhole", "Pan2", "Mix2", "Echo", "WvIn", "WvOut", "WaveLoop", "CurveTable", "Envelope", "CurveTable",
            "OscIn", "OscOut", "OscBundle", "MidiIn", "MidiOut", "Hid", "StringTokenizer", "RegEx", "Reflect"
    );

    private static final java.util.Set<String> CORE_DATA_TYPES = java.util.Set.of(
            "int", "float", "string", "time", "dur", "void", "vec2", "vec3", "vec4", "complex", "polar", "Object", "Array", "Type", "Function", "auto",
            "Event", "OscIn", "OscOut", "OscMsg", "MidiMsg", "HidMsg", "MidiIn", "MidiOut", "Hid", "SerialIO", "FileIO", "StringTokenizer", "RegEx", "Reflect"
    );

    private boolean isKnownUGenType(String type) {
        return UGenRegistry.isRegistered(type) || CORE_UGENS.contains(type);
    }

    private String getMethodKey(String name, List<String> argTypes) {
        StringBuilder sb = new StringBuilder(name).append(":");
        if (argTypes == null || argTypes.isEmpty()) return sb.append("0").toString();
        sb.append(argTypes.size());
        for (String t : argTypes) {
            sb.append("_").append(t != null ? t.replace("[]", "Arr") : "Object");
        }
        return sb.toString();
    }

    private void initGlobalTypes() {
        initGlobalTypes(null);
    }

    private void initGlobalTypes(Map<String, String> existing) {
        // Register built-in types as known classes for field resolution
        if (!userClassRegistry.containsKey("Std")) {
            userClassRegistry.put("Std", new UserClassDescriptor("Std", "Object", new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }
        if (!userClassRegistry.containsKey("string")) {
            userClassRegistry.put("string", new UserClassDescriptor("string", "Object", new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }
        if (!userClassRegistry.containsKey("vec2")) {
            List<String[]> fields = new ArrayList<>();
            fields.add(new String[]{"float", "x"}); fields.add(new String[]{"float", "y"});
            userClassRegistry.put("vec2", new UserClassDescriptor("vec2", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }
        if (!userClassRegistry.containsKey("vec3")) {
            List<String[]> fields = new ArrayList<>();
            fields.add(new String[]{"float", "x"}); fields.add(new String[]{"float", "y"}); fields.add(new String[]{"float", "z"});
            userClassRegistry.put("vec3", new UserClassDescriptor("vec3", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }
        if (!userClassRegistry.containsKey("vec4")) {
            List<String[]> fields = new ArrayList<>();
            fields.add(new String[]{"float", "x"}); fields.add(new String[]{"float", "y"}); fields.add(new String[]{"float", "z"}); fields.add(new String[]{"float", "w"});
            userClassRegistry.put("vec4", new UserClassDescriptor("vec4", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }
        if (!userClassRegistry.containsKey("complex")) {
            List<String[]> fields = new ArrayList<>();
            fields.add(new String[]{"float", "re"}); fields.add(new String[]{"float", "im"});
            userClassRegistry.put("complex", new UserClassDescriptor("complex", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }
        if (!userClassRegistry.containsKey("polar")) {
            List<String[]> fields = new ArrayList<>();
            fields.add(new String[]{"float", "mag"}); fields.add(new String[]{"float", "phase"});
            userClassRegistry.put("polar", new UserClassDescriptor("polar", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
        }

        if (existing != null) this.globalVarTypes.putAll(existing);
    }

    public ChuckEmitter(Map<String, UserClassDescriptor> registry) {
        this(registry, new HashMap<>());
    }

    public ChuckEmitter(Map<String, UserClassDescriptor> registry, Map<String, ChuckCode> preloadedFunctions) {
        this.userClassRegistry = registry;
        this.functions = preloadedFunctions;
        initGlobalTypes();
    }

    private List<ChuckAST.Stmt> parseImport(String importPath) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(importPath);
            String source = java.nio.file.Files.readString(p);
            CharStream input = CharStreams.fromString(source);
            ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
            ChuckASTVisitor visitor = new ChuckASTVisitor();
            @SuppressWarnings("unchecked")
            List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());
            return ast;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    private List<ChuckAST.Stmt> getParsedImport(String importPath) {
        return parseImport(importPath);
    }

    private void registerClassNames(ChuckAST.Stmt stmt) {
        if (stmt == null) return;
        switch (stmt) {
            case ChuckAST.ImportStmt s -> {
                List<ChuckAST.Stmt> imported = getParsedImport(s.path());
                for (ChuckAST.Stmt i : imported) registerClassNames(i);
            }
            case ChuckAST.ClassDefStmt s -> {
                userClassRegistry.putIfAbsent(s.name(), new UserClassDescriptor(s.name(), s.parentName(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), null, s.isAbstract(), s.isInterface(), null, new HashMap<>(), new HashMap<>(), s.access(), s.doc(), new HashMap<>()));
                UserClassDescriptor desc = userClassRegistry.get(s.name());
                registerStaticFieldsRecursive(s.body(), desc);
                for (ChuckAST.Stmt inner : s.body()) registerClassNames(inner);
            }
            case ChuckAST.BlockStmt b -> {
                for (ChuckAST.Stmt inner : b.statements()) registerClassNames(inner);
            }
            default -> {
            }
        }
    }

    private void registerStaticFieldsRecursive(List<ChuckAST.Stmt> body, UserClassDescriptor desc) {
        for (ChuckAST.Stmt inner : body) {
            registerStaticFieldFromStmt(inner, desc);
            if (inner instanceof ChuckAST.BlockStmt b) {
                registerStaticFieldsRecursive(b.statements(), desc);
            }
        }
    }

    private void registerStaticFieldFromStmt(ChuckAST.Stmt inner, UserClassDescriptor desc) {
        if (inner instanceof ChuckAST.DeclStmt ds && ds.isStatic()) {
            desc.staticIsDouble().put(ds.name(), "float".equals(ds.type()));
            if ("float".equals(ds.type()) || "int".equals(ds.type()) || "dur".equals(ds.type()) || "time".equals(ds.type())) {
                desc.staticInts().put(ds.name(), 0L);
            } else {
                desc.staticObjects().put(ds.name(), null);
            }
        }
        if (inner instanceof ChuckAST.ExpStmt es && es.exp() instanceof ChuckAST.DeclExp rDecl && rDecl.isStatic()) {
            desc.staticIsDouble().put(rDecl.name(), "float".equals(rDecl.type()));
            if ("float".equals(rDecl.type()) || "int".equals(rDecl.type()) || "dur".equals(rDecl.type()) || "time".equals(rDecl.type())) {
                desc.staticInts().put(rDecl.name(), 0L);
            } else {
                desc.staticObjects().put(rDecl.name(), null);
            }
        }
        if (inner instanceof ChuckAST.ExpStmt es2
                && es2.exp() instanceof ChuckAST.BinaryExp bexp2
                && (bexp2.op() == ChuckAST.Operator.CHUCK || bexp2.op() == ChuckAST.Operator.AT_CHUCK)
                && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl2
                && rDecl2.isStatic()) {
            desc.staticIsDouble().put(rDecl2.name(), "float".equals(rDecl2.type()));
            if ("float".equals(rDecl2.type()) || "int".equals(rDecl2.type()) || "dur".equals(rDecl2.type()) || "time".equals(rDecl2.type())) {
                desc.staticInts().put(rDecl2.name(), 0L);
            } else {
                desc.staticObjects().put(rDecl2.name(), null);
            }
        }
    }

    private void registerSignatures(ChuckAST.Stmt stmt, Map<String, ChuckCode> functions, Map<String, String> functionReturnTypes) {
        if (stmt instanceof ChuckAST.ImportStmt s) {
            List<ChuckAST.Stmt> imported = getParsedImport(s.path());
            for (ChuckAST.Stmt i : imported) registerSignatures(i, functions, functionReturnTypes);
        } else if (stmt instanceof ChuckAST.FuncDefStmt s) {
            String name = s.name();
            String key = getMethodKey(name, s.argTypes());
            if (!functions.containsKey(key)) {
                ChuckCode c = new ChuckCode(s.name());
                c.setSignature(s.argTypes().size(), s.returnType() != null ? s.returnType() : "void");
                functions.put(key, c);
            }
            if (s.returnType() != null && !s.returnType().equals("void")) {
                functionReturnTypes.put(key, s.returnType());
            }
        } else if (stmt instanceof ChuckAST.BlockStmt b) {
            for (ChuckAST.Stmt inner : b.statements()) {
                registerSignatures(inner, functions, functionReturnTypes);
            }
        }
    }

    private void emitBodies(ChuckAST.Stmt stmt) {
        if (stmt == null) return;
        if (stmt instanceof ChuckAST.ImportStmt s) {
            List<ChuckAST.Stmt> imported = getParsedImport(s.path());
            for (ChuckAST.Stmt i : imported) emitBodies(i);
        } else if (stmt instanceof ChuckAST.FuncDefStmt || stmt instanceof ChuckAST.ClassDefStmt) {
            emitStatement(stmt, null);
        }
    }

    private void registerClassesToCode(ChuckAST.Stmt stmt, ChuckCode code) {
        if (stmt instanceof ChuckAST.ImportStmt s) {
            List<ChuckAST.Stmt> imported = getParsedImport(s.path());
            for (ChuckAST.Stmt i : imported) registerClassesToCode(i, code);
        } else if (stmt instanceof ChuckAST.ClassDefStmt s) {
            UserClassDescriptor d = userClassRegistry.get(s.name());
            if (d != null) code.addInstruction(new MiscInstrs.RegisterClass(s.name(), d));
        } else if (stmt instanceof ChuckAST.BlockStmt b) {
            for (ChuckAST.Stmt inner : b.statements()) registerClassesToCode(inner, code);
        }
    }

    public record EmitResult(ChuckCode code, Map<String, String> globalVarTypes, Map<String, String> globalDocs, Map<String, String> functionDocs) {}

    public EmitResult emitWithDocs(List<ChuckAST.Stmt> statements, String programName, Map<String, String> existingGlobals) {
        localScopes.clear();
        localTypeScopes.clear();
        localScopes.push(new HashMap<>());
        localTypeScopes.push(new HashMap<>());
        localCount = 0;

        globalVarTypes.clear();
        initGlobalTypes(existingGlobals);
        currentFile = programName;
        
        boolean hasContent = statements.stream().anyMatch(s
                -> !(s instanceof ChuckAST.BlockStmt bs && bs.statements().isEmpty()));
        if (!hasContent) {
            throw new RuntimeException(programName + ":1:1: syntax error\n(empty file)");
        }

        for (ChuckAST.Stmt stmt : statements) registerClassNames(stmt);

        Map<String, String> functionDocs = new HashMap<>();
        for (ChuckAST.Stmt stmt : statements) {
            registerSignaturesWithDocs(stmt, functions, functionReturnTypes, functionDocs);
        }

        for (ChuckAST.Stmt stmt : statements) {
            emitBodies(stmt);
        }

        localCount = 0;
        ChuckCode code = new ChuckCode(programName);
        code.addInstruction(new VarInstrs.MoveArgs(0));

        for (ChuckAST.Stmt stmt : statements) {
            registerClassesToCode(stmt, code);
        }

        Map<String, String> globalDocsMap = new HashMap<>();
        for (ChuckAST.Stmt stmt : statements) {
            if (!(stmt instanceof ChuckAST.FuncDefStmt) && !(stmt instanceof ChuckAST.ImportStmt) && !(stmt instanceof ChuckAST.ClassDefStmt)) {
                emitStatementWithDocs(stmt, code, globalDocsMap);
            }
        }
        return new EmitResult(code, new HashMap<>(globalVarTypes), globalDocsMap, functionDocs);
    }

    private void registerSignaturesWithDocs(ChuckAST.Stmt stmt, Map<String, ChuckCode> functions, Map<String, String> functionReturnTypes, Map<String, String> functionDocs) {
        if (stmt instanceof ChuckAST.ImportStmt s) {
            List<ChuckAST.Stmt> imported = getParsedImport(s.path());
            for (ChuckAST.Stmt i : imported) registerSignaturesWithDocs(i, functions, functionReturnTypes, functionDocs);
        } else if (stmt instanceof ChuckAST.FuncDefStmt s) {
            String name = s.name();
            String key = getMethodKey(name, s.argTypes());
            if (!functions.containsKey(key)) {
                ChuckCode c = new ChuckCode(s.name());
                c.setSignature(s.argTypes().size(), s.returnType() != null ? s.returnType() : "void");
                functions.put(key, c);
            }
            if (s.returnType() != null && !s.returnType().equals("void")) {
                functionReturnTypes.put(key, s.returnType());
            }
            if (s.doc() != null) {
                functionDocs.put(key, s.doc());
            }
        } else if (stmt instanceof ChuckAST.BlockStmt b) {
            for (ChuckAST.Stmt inner : b.statements()) {
                registerSignaturesWithDocs(inner, functions, functionReturnTypes, functionDocs);
            }
        }
    }

    private void emitStatementWithDocs(ChuckAST.Stmt stmt, ChuckCode code, Map<String, String> globalDocs) {
        if (stmt instanceof ChuckAST.DeclStmt ds && ds.doc() != null && localScopes.size() == 1) {
            globalDocs.put(ds.name(), ds.doc());
        } else if (stmt instanceof ChuckAST.ExpStmt es && es.exp() instanceof ChuckAST.BinaryExp be && be.rhs() instanceof ChuckAST.DeclExp de && de.doc() != null && localScopes.size() == 1) {
             globalDocs.put(de.name(), de.doc());
        } else if (stmt instanceof ChuckAST.BlockStmt b && !b.isScoped()) {
            for (ChuckAST.Stmt inner : b.statements()) emitStatementWithDocs(inner, code, globalDocs);
            return;
        }
        emitStatement(stmt, code);
    }

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName) {
        return emit(statements, programName, null);
    }

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName, Map<String, String> existingGlobals) {
        return emitWithDocs(statements, programName, existingGlobals).code();
    }

    private void emitStatement(ChuckAST.Stmt stmt, ChuckCode code) {
        if (stmt == null) return;
        if (code != null) code.setActiveLineNumber(stmt.line());
        switch (stmt) {
            case ChuckAST.ExpStmt s -> {
                emitExpression(s.exp(), code);
                code.addInstruction(new StackInstrs.Pop());
            }
            case ChuckAST.WhileStmt s -> {
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                emitExpression(s.condition(), code);
                int jumpFalseIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                emitStatement(s.body(), code);
                code.addInstruction(new ControlInstrs.Jump(startPc));

                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpFalseIdx, new ControlInstrs.JumpIfFalse(endPc));

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
                int jumpTrueIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfTrue

                emitStatement(s.body(), code);
                code.addInstruction(new ControlInstrs.Jump(startPc));

                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpTrueIdx, new ControlInstrs.JumpIfTrue(endPc));

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
                }
            }
            case ChuckAST.ForStmt s -> {
                localScopes.push(new HashMap<>());
                localTypeScopes.push(new HashMap<>());
                int oldLocalCount = localCount;

                if (s.init() != null) {
                    emitStatement(s.init(), code);
                }

                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                if (s.condition() != null) {
                    emitStatement(s.condition(), code);
                    // ExpStmt pops, but for loop needs to keep it for jump?
                    // No, ExpStmt in ForStmt is a special case.
                    // Wait, condition is ChuckAST.Stmt (ExpStmt).
                    // We need to NOT pop it.
                    // Let's assume condition is always an ExpStmt.
                    if (s.condition() instanceof ChuckAST.ExpStmt es) {
                        code.removeLastInstruction(); // Remove the Pop from emitStatement
                    }
                } else {
                    code.addInstruction(new PushInstrs.PushInt(1));
                }

                int jumpFalseIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                emitStatement(s.body(), code);

                int updateStart = code.getNumInstructions();
                if (s.update() != null) {
                    emitExpression(s.update(), code);
                    code.addInstruction(new StackInstrs.Pop());
                }
                code.addInstruction(new ControlInstrs.Jump(startPc));

                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpFalseIdx, new ControlInstrs.JumpIfFalse(endPc));

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
                }

                localScopes.pop();
                localTypeScopes.pop();
                localCount = oldLocalCount;
            }
            case ChuckAST.RepeatStmt s -> {
                emitExpression(s.count(), code);
                code.addInstruction(new TypeInstrs.CastToInt());
                
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());
                
                code.addInstruction(new StackInstrs.Dup());
                code.addInstruction(new PushInstrs.PushInt(0));
                code.addInstruction(new LogicInstrs.GreaterThanAny());
                
                int jumpFalseIdx = code.getNumInstructions();
                code.addInstruction(null); // JumpIfFalse to end
                
                emitStatement(s.body(), code);
                
                int updateStart = code.getNumInstructions();
                code.addInstruction(new PushInstrs.PushInt(1));
                code.addInstruction(new ArithmeticInstrs.MinusAny());
                code.addInstruction(new ControlInstrs.Jump(startPc));
                
                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpFalseIdx, new ControlInstrs.JumpIfFalse(endPc));
                code.addInstruction(new StackInstrs.Pop()); // clean up counter
                
                for (int jump : breakJumps.pop()) code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                for (int jump : continueJumps.pop()) code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
            }
            case ChuckAST.DoStmt s -> {
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                emitStatement(s.body(), code);

                int condStart = code.getNumInstructions();

                emitExpression(s.condition(), code);
                if (s.isUntil()) {
                    code.addInstruction(new LogicInstrs.LogicalNot());
                }
                code.addInstruction(new ControlInstrs.JumpIfTrue(startPc));

                int endPc = code.getNumInstructions();
                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(condStart));
                }
            }
            case ChuckAST.LoopStmt s -> {
                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());
                emitStatement(s.body(), code);
                code.addInstruction(new ControlInstrs.Jump(startPc));
                int endPc = code.getNumInstructions();
                for (int jump : breakJumps.pop()) code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                for (int jump : continueJumps.pop()) code.replaceInstruction(jump, new ControlInstrs.Jump(startPc));
            }
            case ChuckAST.ForEachStmt s -> {
                localScopes.push(new HashMap<>());
                localTypeScopes.push(new HashMap<>());
                int oldLocalCount = localCount;

                // ForEach: for(type name : collection)
                emitExpression(s.collection(), code);
                int collOffset = localCount++;
                localScopes.peek().put("@coll_" + s.name(), collOffset);
                code.addInstruction(new VarInstrs.StoreLocal(collOffset));

                int idxOffset = localCount++;
                localScopes.peek().put("@idx_" + s.name(), idxOffset);
                code.addInstruction(new PushInstrs.PushInt(0));
                code.addInstruction(new VarInstrs.StoreLocal(idxOffset));
                code.addInstruction(new StackInstrs.Pop());

                int startPc = code.getNumInstructions();
                continueJumps.push(new ArrayList<>());
                breakJumps.push(new ArrayList<>());

                code.addInstruction(new VarInstrs.LoadLocal(idxOffset));
                code.addInstruction(new VarInstrs.LoadLocal(collOffset));
                code.addInstruction(new ObjectInstrs.CallMethod("size", 0));
                code.addInstruction(new LogicInstrs.LessThanAny());

                int jumpFalseIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                // Get current element and store in loop variable
                code.addInstruction(new VarInstrs.LoadLocal(collOffset));
                code.addInstruction(new VarInstrs.LoadLocal(idxOffset));
                code.addInstruction(new ArrayInstrs.GetArrayInt());
                int varOffset = localCount++;
                localScopes.peek().put(s.name(), varOffset);
                localTypeScopes.peek().put(s.name(), s.type());
                code.addInstruction(new VarInstrs.StoreLocal(varOffset));
                code.addInstruction(new StackInstrs.Pop());

                emitStatement(s.body(), code);

                int updateStart = code.getNumInstructions();
                code.addInstruction(new VarInstrs.LoadLocal(idxOffset));
                code.addInstruction(new PushInstrs.PushInt(1));
                code.addInstruction(new ArithmeticInstrs.AddAny());
                code.addInstruction(new VarInstrs.StoreLocal(idxOffset));
                code.addInstruction(new StackInstrs.Pop());
                code.addInstruction(new ControlInstrs.Jump(startPc));

                int endPc = code.getNumInstructions();
                code.replaceInstruction(jumpFalseIdx, new ControlInstrs.JumpIfFalse(endPc));

                for (int jump : breakJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
                }
                for (int jump : continueJumps.pop()) {
                    code.replaceInstruction(jump, new ControlInstrs.Jump(updateStart));
                }

                localScopes.pop();
                localTypeScopes.pop();
                localCount = oldLocalCount;
            }
            case ChuckAST.IfStmt s -> {
                emitExpression(s.condition(), code);
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null); // placeholder for JumpIfFalse

                emitStatement(s.thenBranch(), code);

                if (s.elseBranch() != null) {
                    int jumpEndIdx = code.getNumInstructions();
                    code.addInstruction(null); // placeholder for Jump to end
                    int elseStartPc = code.getNumInstructions();
                    code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(elseStartPc));
                    emitStatement(s.elseBranch(), code);
                    code.replaceInstruction(jumpEndIdx, new ControlInstrs.Jump(code.getNumInstructions()));
                } else {
                    code.replaceInstruction(jumpIdx, new ControlInstrs.JumpIfFalse(code.getNumInstructions()));
                }
            }
            case ChuckAST.SwitchStmt s -> {
                emitExpression(s.condition(), code);
                int endJumpIdx = -1;
                List<Integer> caseJumps = new ArrayList<>();
                breakJumps.push(new ArrayList<>());

                for (ChuckAST.CaseStmt c : s.cases()) {
                    if (!c.isDefault()) {
                        code.addInstruction(new StackInstrs.Dup());
                        emitExpression(c.condition(), code);
                        code.addInstruction(new LogicInstrs.EqualsAny());
                        int jumpNextIdx = code.getNumInstructions();
                        code.addInstruction(null); // JumpIfFalse to next case
                        for (ChuckAST.Stmt bodyStmt : c.body()) emitStatement(bodyStmt, code);
                        int breakIdx = code.getNumInstructions();
                        code.addInstruction(null); // Jump to end
                        breakJumps.peek().add(breakIdx);
                        code.replaceInstruction(jumpNextIdx, new ControlInstrs.JumpIfFalse(code.getNumInstructions()));
                    } else {
                        for (ChuckAST.Stmt bodyStmt : c.body()) emitStatement(bodyStmt, code);
                    }
                }
                code.addInstruction(new StackInstrs.Pop()); // pop condition
                int endPc = code.getNumInstructions();
                for (int jump : breakJumps.pop()) code.replaceInstruction(jump, new ControlInstrs.Jump(endPc));
            }
            case ChuckAST.ReturnStmt s -> {
                if (s.expression() != null) {
                    emitExpression(s.expression(), code);
                }
                code.addInstruction(inStaticFuncContext ? new ReturnFunc() : new ControlInstrs.ReturnMethod());
                currentFuncHasReturn = true;
            }
            case ChuckAST.BreakStmt _ -> {
                if (breakJumps.isEmpty()) {
                    throw new RuntimeException(currentFile + ":" + stmt.line() + ":" + stmt.column() + ": error: 'break' used outside loop or switch");
                }
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null);
                breakJumps.peek().add(jumpIdx);
            }
            case ChuckAST.ContinueStmt _ -> {
                if (continueJumps.isEmpty()) {
                    throw new RuntimeException(currentFile + ":" + stmt.line() + ":" + stmt.column() + ": error: 'continue' used outside loop");
                }
                int jumpIdx = code.getNumInstructions();
                code.addInstruction(null);
                continueJumps.peek().add(jumpIdx);
            }
            case ChuckAST.PrintStmt s -> {
                for (ChuckAST.Exp e : s.expressions()) {
                    emitExpression(e, code);
                }
                code.addInstruction(new ChuckPrint(s.expressions().size()));
            }
            case ChuckAST.BlockStmt s -> {
                if (s.isScoped()) {
                    localScopes.push(new HashMap<>());
                    localTypeScopes.push(new HashMap<>());
                }
                int oldLocalCount = localCount;
                for (ChuckAST.Stmt inner : s.statements()) {
                    emitStatement(inner, code);
                }
                if (s.isScoped()) {
                    localScopes.pop();
                    localTypeScopes.pop();
                    localCount = oldLocalCount;
                }
            }
            case ChuckAST.ImportStmt _ -> {
                // already processed by VM before emit — skip
            }
            case ChuckAST.DeclStmt s -> {
                // local variable or global
                if (!localScopes.isEmpty()) {
                    localScopes.peek().put(s.name(), localCount++);
                }
                if (!localTypeScopes.isEmpty()) {
                    localTypeScopes.peek().put(s.name(), s.type());
                }
                int argCount = 0;
                boolean isUserClass = userClassRegistry.containsKey(s.type());
                boolean isArrayDecl = s.type().endsWith("[]");
                boolean isObject = isUserClass || isArrayDecl || isObjectType(s.type());

                if (s.callArgs() instanceof ChuckAST.CallExp call) {
                    if (isUserClass) {
                        code.addInstruction(new PushInstrs.PushNull()); // placeholder for 'this'
                    }
                    code.addInstruction(new StackInstrs.Dup());
                    for (ChuckAST.Exp arg : call.args()) {
                        emitExpression(arg, code);
                    }
                    argCount = call.args().size();
                } else if (!s.arraySizes().isEmpty()) {
                    for (ChuckAST.Exp sizeExp : s.arraySizes()) {
                        emitExpression(sizeExp, code);
                    }
                    argCount = s.arraySizes().size();
                }

                if (isObject && !s.isReference() && argCount == 0) {
                    code.addInstruction(new PushInstrs.PushInt(0)); // dummy size for instantiation
                    argCount = 1;
                }

                if (localScopes.size() > 1 || currentClass != null) {
                    int offset = localScopes.peek().get(s.name());
                    code.addInstruction(new ObjectInstrs.InstantiateSetAndPushLocal(getBaseType(s.type()), offset, argCount, s.isReference(), isArrayDecl, userClassRegistry));
                } else {
                    globalVarTypes.put(s.name(), s.type());
                    code.addInstruction(new ObjectInstrs.InstantiateSetAndPushGlobal(getBaseType(s.type()), s.name(), argCount, s.isReference(), isArrayDecl, userClassRegistry));
                }
                code.addInstruction(new StackInstrs.Pop());
            }
            case ChuckAST.FuncDefStmt s -> {
                String key = getMethodKey(s.name(), s.argTypes());
                ChuckCode methodCode = functions.get(key);
                if (methodCode == null) {
                    // This can happen if registerSignatures wasn't called for some reason
                    methodCode = new ChuckCode(s.name());
                    methodCode.setSignature(s.argTypes().size(), currentFuncReturnType);
                    functions.put(key, methodCode);
                }
                methodCode.setSignature(s.argNames().size(), currentFuncReturnType);

                int savedLocalCount = localCount;
                boolean savedInPreCtor = inPreCtor;
                
                Map<String, Integer> scope = new HashMap<>();
                Map<String, String> typeScope = new HashMap<>();
                for (int i = 0; i < s.argNames().size(); i++) {
                    scope.put(s.argNames().get(i), i);
                    typeScope.put(s.argNames().get(i), s.argTypes().get(i));
                }
                localScopes.push(scope);
                localTypeScopes.push(typeScope);
                localCount = s.argNames().size();
                methodCode.addInstruction(new VarInstrs.MoveArgs(s.argNames().size()));

                String prevReturnType = currentFuncReturnType;
                boolean prevHasReturn = currentFuncHasReturn;
                boolean prevStaticCtx = inStaticFuncContext;
                currentFuncReturnType = s.returnType() != null ? s.returnType() : "void";
                currentFuncHasReturn = false;
                inStaticFuncContext = s.isStatic();

                emitStatement(s.body(), methodCode);
                methodCode.addInstruction(s.isStatic() ? new ReturnFunc() : new ControlInstrs.ReturnMethod());

                localScopes.pop();
                localTypeScopes.pop();
                localCount = savedLocalCount;
                inPreCtor = savedInPreCtor;

                if (!currentFuncReturnType.equals("void") && !currentFuncHasReturn) {
                    throw new RuntimeException(currentFile + ":" + s.line() + ":" + s.column()
                            + ": error: not all control paths in 'fun " + currentFuncReturnType + " " + s.name() + "()' return a value");
                }

                currentFuncReturnType = prevReturnType;
                currentFuncHasReturn = prevHasReturn;
                inStaticFuncContext = prevStaticCtx;
            }
            case ChuckAST.ClassDefStmt s -> {
                List<String[]> fieldDefs = new ArrayList<>();
                List<String[]> staticFieldDefs = new ArrayList<>();
                java.util.Set<String> fieldNames = new HashSet<>();
                java.util.Set<String> staticFieldNames = new HashSet<>();
                Map<String, ChuckAST.AccessModifier> fieldAccess = new HashMap<>();
                Map<String, ChuckAST.AccessModifier> methodAccess = new HashMap<>();
                List<ChuckAST.FuncDefStmt> methods = new ArrayList<>();
                List<ChuckAST.Stmt> flattenedBody = new ArrayList<>();

                for (ChuckAST.Stmt bodyStmt : s.body()) {
                    if (bodyStmt instanceof ChuckAST.BlockStmt bs) {
                        flattenedBody.addAll(bs.statements());
                    } else {
                        flattenedBody.add(bodyStmt);
                    }
                }

                for (ChuckAST.Stmt bodyStmt : flattenedBody) {
                    switch (bodyStmt) {
                        case ChuckAST.DeclStmt f -> {
                            if (f.isStatic()) {
                                staticFieldDefs.add(new String[]{f.type(), f.name()});
                                staticFieldNames.add(f.name());
                            } else {
                                fieldDefs.add(new String[]{f.type(), f.name()});
                                fieldNames.add(f.name());
                            }
                            fieldAccess.put(f.name(), f.access());
                        }
                        case ChuckAST.FuncDefStmt m -> {
                            methods.add(m);
                        }
                        case ChuckAST.ExpStmt es when es.exp() instanceof ChuckAST.DeclExp rDecl -> {
                            if (rDecl.isStatic()) {
                                staticFieldDefs.add(new String[]{rDecl.type(), rDecl.name()});
                                staticFieldNames.add(rDecl.name());
                            } else {
                                fieldDefs.add(new String[]{rDecl.type(), rDecl.name()});
                                fieldNames.add(rDecl.name());
                            }
                            fieldAccess.put(rDecl.name(), rDecl.access());
                        }
                        case ChuckAST.ExpStmt es2
                                && es2.exp() instanceof ChuckAST.BinaryExp bexp2
                                && (bexp2.op() == ChuckAST.Operator.CHUCK || bexp2.op() == ChuckAST.Operator.AT_CHUCK)
                                && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl -> {
                            if (rDecl.isStatic()) {
                                staticFieldDefs.add(new String[]{rDecl.type(), rDecl.name()});
                                staticFieldNames.add(rDecl.name());
                            } else {
                                fieldDefs.add(new String[]{rDecl.type(), rDecl.name()});
                                fieldNames.add(rDecl.name());
                            }
                            fieldAccess.put(rDecl.name(), rDecl.access());
                        }
                        default -> {
                        }
                    }
                }

                Map<String, Long> staticInts = new HashMap<>();
                Map<String, Boolean> staticIsDouble = new HashMap<>();
                Map<String, Object> staticObjects = new HashMap<>();
                for (String[] f : staticFieldDefs) {
                    staticIsDouble.put(f[1], "float".equals(f[0]));
                    if ("float".equals(f[0]) || "int".equals(f[0]) || "dur".equals(f[0]) || "time".equals(f[0])) {
                        staticInts.put(f[1], 0L);
                    } else {
                        staticObjects.put(f[1], null);
                    }
                }

                Map<String, ChuckCode> methodCodes = new HashMap<>();
                Map<String, ChuckCode> staticMethodCodes = new HashMap<>();
                Map<String, String> methodDocs = new HashMap<>();
                String prevClass = currentClass;
                java.util.Set<String> prevFields = currentClassFields;
                currentClass = s.name();
                currentClassFields = fieldNames;

                // Pre-register with actual field maps so static-field references in method bodies resolve.
                // methodCodes/staticMethodCodes are mutable maps; stubs added in pass 1 below become visible here.
                userClassRegistry.put(s.name(), new UserClassDescriptor(
                        s.name(), s.parentName(), fieldDefs, staticFieldDefs, methodCodes, staticMethodCodes,
                        staticInts, staticIsDouble, staticObjects, null, s.isAbstract(), s.isInterface(), null, fieldAccess, methodAccess, s.access(), s.doc(), methodDocs));

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
                localScopes.push(new HashMap<>());
                localTypeScopes.push(new HashMap<>());
                int savedLocalCount = localCount;
                boolean savedInPreCtor = inPreCtor;
                localCount = 0;
                inPreCtor = true;
                
                for (ChuckAST.Stmt bodyStmt : s.body()) {
                    if (bodyStmt instanceof ChuckAST.FuncDefStmt) {
                        continue;
                    }
                    if (bodyStmt instanceof ChuckAST.DeclStmt ds && ds.isStatic()) {
                        continue;
                    }
                    if (bodyStmt instanceof ChuckAST.ExpStmt es
                            && es.exp() instanceof ChuckAST.DeclExp rDecl
                            && rDecl.isStatic()) {
                        continue;
                    }
                    if (bodyStmt instanceof ChuckAST.ExpStmt es2
                            && es2.exp() instanceof ChuckAST.BinaryExp bexp2
                            && (bexp2.op() == ChuckAST.Operator.CHUCK || bexp2.op() == ChuckAST.Operator.AT_CHUCK)
                            && bexp2.rhs() instanceof ChuckAST.DeclExp rDecl2
                            && rDecl2.isStatic()) {
                        continue;
                    }
                    emitStatement(bodyStmt, preCtorCodeLocal);
                }
                
                localScopes.pop();
                localTypeScopes.pop();
                localCount = savedLocalCount;
                inPreCtor = savedInPreCtor;

                // Update descriptor with compiled pre-constructor
                userClassRegistry.put(s.name(), new UserClassDescriptor(
                        s.name(), s.parentName(), fieldDefs, staticFieldDefs, methodCodes, staticMethodCodes,
                        staticInts, staticIsDouble, staticObjects, preCtorCodeLocal, s.isAbstract(), s.isInterface(), null, fieldAccess, methodAccess, s.access(), s.doc(), methodDocs));

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
                    Map<String, String> typeScope = new HashMap<>();
                    for (int i = 0; i < m.argNames().size(); i++) {
                        scope.put(m.argNames().get(i), i);
                        typeScope.put(m.argNames().get(i), m.argTypes().get(i));
                    }
                    localScopes.push(scope);
                    localTypeScopes.push(typeScope);
                    localCount = m.argNames().size();
                    methodCode.addInstruction(new VarInstrs.MoveArgs(m.argNames().size()));

                    emitStatement(m.body(), methodCode);
                    methodCode.addInstruction(s.isStatic() ? new ReturnFunc() : new ControlInstrs.ReturnMethod());
                    localScopes.pop();
                    localTypeScopes.pop();
                    localCount = 0; // Reset for next method

                    if (!currentFuncReturnType.equals("void") && !currentFuncHasReturn) {
                        throw new RuntimeException(currentFile + ":" + m.line() + ":" + m.column()
                                + ": error: not all control paths in 'fun " + currentFuncReturnType + " "
                                + s.name() + "." + m.name() + "()' return a value");
                    }

                    currentFuncReturnType = prevReturnType;
                    currentFuncHasReturn = prevHasReturn;
                    inStaticFuncContext = prevStaticCtx;
                }
                currentClass = prevClass;
                currentClassFields = prevFields;
                ChuckCode finalPreCtorCode = preCtorCodeLocal.getNumInstructions() > 0 ? preCtorCodeLocal : null;

                // Compile static initializers for ALL static fields
                ChuckCode staticInitCodeLocal = new ChuckCode("__staticInit__" + s.name());
                String savedClass = currentClass;
                currentClass = s.name();
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
                            emitExpression(bexp2.lhs(), staticInitCodeLocal);
                            staticInitCodeLocal.addInstruction(new FieldInstrs.SetStatic(s.name(), fName));
                            staticInitCodeLocal.addInstruction(new StackInstrs.Pop()); // clean up
                        } else if (bodyStmt instanceof ChuckAST.DeclStmt ds) {
                            // e.g. static int S; or static SinOsc S;
                            ChuckAST.DeclExp declExp = new ChuckAST.DeclExp(ds.type(), ds.name(), ds.arraySizes(), ds.callArgs(), ds.isReference(), false, false, ds.isConst(), ChuckAST.AccessModifier.PUBLIC, null, ds.line(), ds.column());
                            localScopes.push(new java.util.HashMap<>());
                            localTypeScopes.push(new java.util.HashMap<>());
                            emitExpression(declExp, staticInitCodeLocal);
                            localScopes.pop();
                            localTypeScopes.pop();
                            staticInitCodeLocal.addInstruction(new FieldInstrs.SetStatic(s.name(), fName));
                            staticInitCodeLocal.addInstruction(new StackInstrs.Pop()); // clean up
                        } else if (bodyStmt instanceof ChuckAST.ExpStmt es3
                                && es3.exp() instanceof ChuckAST.DeclExp rDecl3) {
                            ChuckAST.DeclExp rDecl3m = new ChuckAST.DeclExp(rDecl3.type(), rDecl3.name(), rDecl3.arraySizes(), rDecl3.callArgs(), rDecl3.isReference(), false, false, rDecl3.isConst(), ChuckAST.AccessModifier.PUBLIC, null, rDecl3.line(), rDecl3.column());
                            localScopes.push(new java.util.HashMap<>());
                            localTypeScopes.push(new java.util.HashMap<>());
                            emitExpression(rDecl3m, staticInitCodeLocal);
                            localScopes.pop();
                            localTypeScopes.pop();
                            staticInitCodeLocal.addInstruction(new FieldInstrs.SetStatic(s.name(), fName));
                            staticInitCodeLocal.addInstruction(new StackInstrs.Pop()); // clean up
                        }
                    }
                }
                currentClass = savedClass;
                ChuckCode finalStaticInitCode = staticInitCodeLocal.getNumInstructions() > 0 ? staticInitCodeLocal : null;

                UserClassDescriptor descriptor = new UserClassDescriptor(
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
                        methodDocs);

                // Add static methods to the main methods map too, for resolution on instances
                methodCodes.putAll(staticMethodCodes);
                
                userClassRegistry.put(s.name(), descriptor);
            }
        }
    }

    private String getVarType(ChuckAST.Exp exp) {
        if (exp instanceof ChuckAST.IdExp e) {
            return getVarTypeByName(e.name());
        }
        return getExprType(exp);
    }

    private String getVarTypeByName(String name) {
        for (int i = localTypeScopes.size() - 1; i >= 0; i--) {
            String type = localTypeScopes.get(i).get(name);
            if (type != null) return type;
        }
        return globalVarTypes.get(name);
    }

    private String getExprType(ChuckAST.Exp exp) {
        if (exp == null) return null;
        return switch (exp) {
            case ChuckAST.IntExp _ -> "int";
            case ChuckAST.FloatExp _ -> "float";
            case ChuckAST.StringExp _ -> "string";
            case ChuckAST.IdExp e -> getVarTypeByName(e.name());
            case ChuckAST.BinaryExp e -> {
                if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                    yield getExprType(e.rhs());
                }
                String lt = getExprType(e.lhs());
                String rt = getExprType(e.rhs());
                if (isVecType(lt)) yield lt;
                if (isVecType(rt)) yield rt;
                if ("float".equals(lt) || "float".equals(rt)) yield "float";
                yield "int";
            }
            case ChuckAST.UnaryExp e -> getExprType(e.exp());
            case ChuckAST.CallExp e -> {
                if (e.base() instanceof ChuckAST.IdExp id) {
                    String name = id.name();
                    if (name.equals("mtof") || name.equals("ftom") || name.equals("powtodb") || name.equals("rmstodb") || name.equals("dbtopow") || name.equals("dbtorms") || name.equals("dbtolin") || name.equals("lintodb")) {
                        yield "float";
                    }
                    List<String> argTypes = e.args().stream().map(this::getExprType).toList();
                    String key = getMethodKey(name, argTypes);
                    String retType = functionReturnTypes.get(key);
                    if (retType == null) retType = functionReturnTypes.get(name + ":" + e.args().size());
                    yield (retType != null) ? retType : "void";
                }
                if (e.base() instanceof ChuckAST.DotExp dot) {
                    String baseType = getExprType(dot.base());
                    if (baseType == null && dot.base() instanceof ChuckAST.IdExp id && userClassRegistry.containsKey(id.name())) {
                        baseType = id.name();
                    }
                    if (baseType != null) {
                        List<String> argTypes = e.args().stream().map(this::getExprType).toList();
                        String key = resolveMethodKey(baseType, dot.member(), argTypes);
                        UserClassDescriptor d = userClassRegistry.get(baseType);
                        if (d != null) {
                            ChuckCode method = d.methods().get(key);
                            if (method == null) method = d.staticMethods().get(key);
                            if (method != null) yield method.getReturnType();
                        }
                    }
                }
                yield "void";
            }
            case ChuckAST.DotExp e -> {
                String baseType = getExprType(e.base());
                if (baseType != null) {
                    return getFieldType(baseType, e.member());
                }
                yield null;
            }
            case ChuckAST.DeclExp e -> e.type();
            case ChuckAST.ArrayAccessExp e -> {
                String bt = getExprType(e.base());
                if (bt != null && bt.endsWith("[]")) yield bt.substring(0, bt.length() - 2);
                if (isVecType(bt)) yield "float";
                yield null;
            }
            case ChuckAST.VectorLitExp e -> {
                yield switch (e.elements().size()) {
                    case 2 -> "vec2";
                    case 3 -> "vec3";
                    case 4 -> "vec4";
                    default -> "Array";
                };
            }
            case ChuckAST.ComplexLit _ -> "complex";
            case ChuckAST.PolarLit _ -> "polar";
            case ChuckAST.CastExp e -> e.targetType();
            case ChuckAST.TypeofExp _ -> "Type";
            case ChuckAST.InstanceofExp _ -> "int";
            case ChuckAST.SporkExp e -> "Shred";
            case ChuckAST.TernaryExp e -> getExprType(e.thenExp());
            default -> null;
        };
    }

    private String getFieldType(String className, String fieldName) {
        for (int depth = 0; depth < 16 && className != null; depth++) {
            UserClassDescriptor d = userClassRegistry.get(className);
            if (d == null) break;
            for (String[] f : d.fields()) {
                if (f[1].equals(fieldName)) return f[0];
            }
            for (String[] f : d.staticFields()) {
                if (f[1].equals(fieldName)) return f[0];
            }
            className = d.parentName();
        }
        return null;
    }

    private String resolveMethodKey(String className, String methodName, List<String> argTypes) {
        String key = getMethodKey(methodName, argTypes);
        for (int depth = 0; depth < 16 && className != null; depth++) {
            UserClassDescriptor d = userClassRegistry.get(className);
            if (d == null) break;
            if (d.methods().containsKey(key) || d.staticMethods().containsKey(key)) return key;
            
            String fallbackKey = methodName + ":" + argTypes.size();
            if (d.methods().containsKey(fallbackKey) || d.staticMethods().containsKey(fallbackKey)) return fallbackKey;
            
            className = d.parentName();
        }
        return key;
    }

    private ChuckCode resolveStaticMethod(String className, String key) {
        for (int depth = 0; depth < 16 && className != null; depth++) {
            UserClassDescriptor d = userClassRegistry.get(className);
            if (d == null) break;
            if (d.staticMethods().containsKey(key)) return d.staticMethods().get(key);
            className = d.parentName();
        }
        return null;
    }

    private boolean hasInstanceField(String className, String fieldName) {
        for (int depth = 0; depth < 16 && className != null; depth++) {
            UserClassDescriptor d = userClassRegistry.get(className);
            if (d == null) break;
            for (String[] f : d.fields()) {
                if (f[1].equals(fieldName)) return true;
            }
            className = d.parentName();
        }
        return false;
    }

    private boolean hasStaticField(String className, String fieldName) {
        for (int depth = 0; depth < 16 && className != null; depth++) {
            UserClassDescriptor d = userClassRegistry.get(className);
            if (d == null) break;
            for (String[] f : d.staticFields()) {
                if (f[1].equals(fieldName)) return true;
            }
            className = d.parentName();
        }
        return false;
    }

    private String resolveClassName(ChuckAST.Exp exp) {
        if (exp == null) return null;
        if (exp instanceof ChuckAST.IdExp e && userClassRegistry.containsKey(e.name())) return e.name();
        String type = getExprType(exp);
        if (type != null && userClassRegistry.containsKey(type)) return type;
        return null;
    }

    private boolean isObjectType(String type) {
        if (type == null) return false;
        if (CORE_DATA_TYPES.contains(type)) {
            return !type.equals("int") && !type.equals("float") && !type.equals("dur") && !type.equals("time") && !type.equals("void") && !type.equals("auto");
        }
        return true;
    }

    private boolean isSubclassOfUGen(String type) {
        if (type == null) return false;
        UserClassDescriptor d = userClassRegistry.get(type);
        while (d != null) {
            if ("ChuckUGen".equals(d.parentName()) || "ChuGen".equals(d.parentName()) || "Chubgraph".equals(d.parentName())) return true;
            if (d.parentName() != null && isKnownUGenType(d.parentName())) return true;
            d = userClassRegistry.get(d.parentName());
        }
        return false;
    }

    private boolean isIOType(String type) {
        return "IO".equals(type) || "FileIO".equals(type) || "SerialIO".equals(type);
    }

    private String getBaseType(String type) {
        if (type == null) return null;
        return type.replaceAll("\\[\\]", "");
    }

    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        if (exp == null) return;
        switch (exp) {
            case ChuckAST.IntExp e ->
                code.addInstruction(new PushInstrs.PushInt(e.value()));
            case ChuckAST.FloatExp e ->
                code.addInstruction(new PushInstrs.PushFloat(e.value()));
            case ChuckAST.StringExp e ->
                code.addInstruction(new PushInstrs.PushString(e.value()));
            case ChuckAST.BinaryExp e -> {
                if (e.op() == ChuckAST.Operator.CHUCK) {
                    if (e.rhs() instanceof ChuckAST.BlockStmt) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot '=>' from/to a multi-variable declaration");
                    }
                    if (e.rhs() instanceof ChuckAST.DeclExp rDecl) {
                        // Resolve 'auto' type from the LHS expression
                        ChuckAST.DeclExp resolvedDecl = rDecl;
                        if ("auto".equals(rDecl.type())) {
                            String inferredType = getExprType(e.lhs());
                            if (inferredType == null) inferredType = "int";
                            resolvedDecl = new ChuckAST.DeclExp(inferredType, rDecl.name(), rDecl.arraySizes(),
                                    rDecl.callArgs(), rDecl.isReference(), rDecl.isStatic(), rDecl.isGlobal(),
                                    rDecl.isConst(), rDecl.access(), rDecl.doc(), rDecl.line(), rDecl.column());
                        }
                        
                        // Pass e.op() to emitChuckTarget
                        boolean resolvedUseGlobal = resolvedDecl.isGlobal() || (localScopes.size() <= 1 && currentClass == null);
                        if (!resolvedUseGlobal) {
                            emitExpression(resolvedDecl, code);
                            code.addInstruction(new StackInstrs.Pop());
                        }
                        emitExpression(e.lhs(), code);
                        emitChuckTarget(resolvedDecl, code, e.op());
                    } else {
                        emitExpression(e.lhs(), code);
                        emitChuckTarget(e.rhs(), code, e.op());
                    }
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
                    emitChuckTarget(e.rhs(), code, e.op());
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
                        String rhsType = getVarTypeByName(rhsId.name());
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
                    emitChuckTarget(e.rhs(), code, e.op());
                } else if (e.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS || e.op() == ChuckAST.Operator.POSTFIX_MINUS_MINUS) {
                    // Postfix ++ / -- : dispatch to user-class operator, or return old value for primitives
                    if (e.rhs() instanceof ChuckAST.DeclExp) {
                        throw new RuntimeException(currentFile + ":" + e.line() + ":" + e.column()
                                + ": error: cannot use compound assignment operator with a variable declaration");
                    }
                    boolean isPostfixPlus = e.op() == ChuckAST.Operator.POSTFIX_PLUS_PLUS;
                    String rhsType = getVarType(e.rhs());
                    if (rhsType != null && userClassRegistry.containsKey(rhsType)) {
                        // Call postfix operator: __pub_op__++:Type or __op__++:Type
                        String opSym = isPostfixPlus ? "++" : "--";
                        List<String> pArgTypes = List.of(rhsType);
                        String fullKey = getMethodKey("__pub_op__" + opSym, pArgTypes);
                        String privKey = getMethodKey("__op__" + opSym, pArgTypes);
                        
                        ChuckCode opCode = functions.get(fullKey);
                        if (opCode == null) opCode = functions.get(privKey);
                        
                        if (opCode == null) {
                            // Fallback to argc-based keys
                            String opKey = opSym + ":1";
                            opCode = functions.get("__pub_op__" + opKey);
                            if (opCode == null) opCode = functions.get("__op__" + opKey);
                        }
                        
                        if (opCode != null) {
                            emitExpression(e.rhs(), code);
                            code.addInstruction(new CallFunc(opCode, 1));
                            return;
                        }
                    }
                    // Primitive postfix: push old value, push current value again, add/sub 1, store back
                    if (e.rhs() instanceof ChuckAST.DotExp dot) {
                        String potentialClassName = resolveClassName(dot.base());
                        if (potentialClassName != null) {
                            UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                            if (classDesc != null && (classDesc.staticInts().containsKey(dot.member()) || classDesc.staticObjects().containsKey(dot.member()))) {
                                code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, dot.member())); // push old
                                code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, dot.member())); // push again for arithmetic
                                code.addInstruction(new PushInstrs.PushInt(1));
                                if (isPostfixPlus) {
                                    code.addInstruction(new ArithmeticInstrs.AddAny());
                                } else {
                                    code.addInstruction(new ArithmeticInstrs.MinusAny());
                                }
                                code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, dot.member()));
                                return;
                            }
                        }
                    }
                    emitExpression(e.rhs(), code);  // push old value (for return)
                    emitExpression(e.rhs(), code);  // push current value again (for arithmetic)
                    emitExpression(e.lhs(), code);  // push 1
                    if (isPostfixPlus) {
                        code.addInstruction(new ArithmeticInstrs.AddAny());
                    } else {
                        code.addInstruction(new ArithmeticInstrs.MinusAny());
                    }
                    emitChuckTarget(e.rhs(), code, e.op()); // store new value, emitChuckTarget already pops for primitives
                } else if (e.op() == ChuckAST.Operator.WRITE_IO || (e.op() == ChuckAST.Operator.LE && isIOType(getExprType(e.lhs())))) {
                    emitExpression(e.lhs(), code);
                    emitExpression(e.rhs(), code);
                    code.addInstruction(new ArrayInstrs.ShiftLeftOrAppend());
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
                                emitExpression(ae.indices().get(ae.indices().size() - 1), code);
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
                                String rhsType = getExprType(e.rhs());
                                List<String> bArgTypes = (rhsType != null) ? List.of(lhsType, rhsType) : List.of(lhsType);
                                String fullKey = getMethodKey("__pub_op__" + opSymbol, bArgTypes);
                                String privKey = getMethodKey("__op__" + opSymbol, bArgTypes);
                                
                                ChuckCode opFunc = functions.get(fullKey);
                                if (opFunc == null) opFunc = functions.get(privKey);
                                
                                if (opFunc == null) {
                                    // Fallback to argc-based keys
                                    opFunc = functions.get("__pub_op__" + opSymbol + ":2");
                                    if (opFunc == null) {
                                        opFunc = functions.get("__op__" + opSymbol + ":2");
                                    }
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
                                    List<String> mArgTypes = (rhsType != null) ? List.of(rhsType) : new ArrayList<>();
                                    String mPubKey = resolveMethodKey(lhsType, "__pub_op__" + opSymbol, mArgTypes);
                                    String mPrivKey = resolveMethodKey(lhsType, "__op__" + opSymbol, mArgTypes);
                                    
                                    if (desc.methods().containsKey(mPubKey) || desc.staticMethods().containsKey(mPubKey)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new ObjectInstrs.CallMethod("__pub_op__" + opSymbol, 1, mPubKey));
                                        return;
                                    }
                                    if (desc.methods().containsKey(mPrivKey) || desc.staticMethods().containsKey(mPrivKey)) {
                                        emitExpression(e.lhs(), code);
                                        emitExpression(e.rhs(), code);
                                        code.addInstruction(new ObjectInstrs.CallMethod("__op__" + opSymbol, 1, mPrivKey));
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
                
                boolean isField = currentClass != null && (currentClassFields.contains(e.name()) || hasInstanceField(currentClass, e.name()));
                if (isField) {
                    code.addInstruction(new StackInstrs.PushThis());
                    code.addInstruction(new FieldInstrs.GetUserField(e.name()));
                } else if (localOffset != null) {
                    code.addInstruction(new VarInstrs.LoadLocal(localOffset));
                } else if (currentClass != null && hasStaticField(currentClass, e.name())) {
                    code.addInstruction(new FieldInstrs.GetStatic(currentClass, e.name()));
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
                String potentialClassName = resolveClassName(e.base());
                if (potentialClassName != null) {
                    boolean inRegistry = userClassRegistry.containsKey(potentialClassName);
                    UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                    if (classDesc != null) {
                        boolean hasSInt = classDesc.staticInts().containsKey(e.member());
                        boolean hasSObj = classDesc.staticObjects().containsKey(e.member());
                        if (hasSInt || hasSObj) {
                            checkAccess(potentialClassName, e.member(), false, e.line(), e.column());
                            code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, e.member()));
                            return;
                        }
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
                    if (id.name().equals("Machine")) {
                        // Property-style read: Machine.realtime, Machine.silent, Machine.intsize, etc.
                        switch (e.member()) {
                            case "realtime"  -> code.addInstruction(new PushInstrs.PushInt(0));
                            case "silent"    -> code.addInstruction(new PushInstrs.PushInt(1));
                            case "intsize"   -> code.addInstruction(new PushInstrs.PushInt(64));
                            case "version"   -> code.addInstruction(new org.chuck.core.instr.MachineCall("version", 0));
                            case "platform", "os" -> code.addInstruction(new org.chuck.core.instr.MachineCall("platform", 0));
                            case "loglevel"  -> code.addInstruction(new org.chuck.core.instr.MachineCall("loglevel", 0));
                            case "timeofday" -> code.addInstruction(new org.chuck.core.instr.MachineCall("timeofday", 0));
                            default          -> code.addInstruction(new org.chuck.core.instr.MachineCall(e.member(), 0));
                        }
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
                    if (vecIdx >= 0) {
                        String baseType = getExprType(e.base());
                        if (isVecType(baseType)) {
                            emitExpression(e.base(), code);
                            code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
                            return;
                        }
                    }
                }
                emitExpression(e.base(), code);
                String baseType = getExprType(e.base());
                if (baseType != null) {
                    if (userClassRegistry.containsKey(baseType)) {
                        checkAccess(baseType, e.member(), false, e.line(), e.column());
                    }
                    
                    boolean isUGenMember = isKnownUGenType(baseType) || isSubclassOfUGen(baseType);
                    if (isUGenMember) {
                        code.addInstruction(new ObjectInstrs.CallMethod(e.member(), 0));
                    } else {
                        code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
                    }
                } else {
                    code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));
                }
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
                // 1. Resolve arguments types first
                List<String> argTypes = e.args().stream().map(this::getExprType).toList();
                int argc = e.args().size();

                // 2. Handle built-in static calls: Std, Math, Machine, RegEx, etc.
                if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp baseId) {
                    String baseName = baseId.name();
                    String memberName = dot.member();

                    if (baseName.equals("IO") && (memberName.equals("nl") || memberName.equals("newline"))) {
                        code.addInstruction(new PushInstrs.PushString("\n"));
                        return;
                    }
                    if (baseName.equals("Std")) {
                        java.util.Set<String> builtinStd = java.util.Set.of(
                                "mtof", "ftom", "powtodb", "rmstodb", "dbtopow", "dbtorms", "dbtolin", "lintodb",
                                "abs", "fabs", "sgn", "rand2", "rand2f", "clamp", "clampf", "scalef", "atoi", "atof", "itoa", "ftoi", "systemTime", "range",
                                "getenv", "setenv"
                        );
                        if (builtinStd.contains(memberName)) {
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new MathInstrs.StdFunc(memberName, argc));
                            return;
                        }
                    }
                    if (baseName.equals("Math")) {
                        if (memberName.equals("random") || memberName.equals("randf")) {
                            code.addInstruction(new MathInstrs.MathRandom());
                            return;
                        }
                        if (memberName.equals("help")) {
                            code.addInstruction(new MathInstrs.MathHelp());
                            return;
                        }
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new MathInstrs.MathFunc(memberName));
                        return;
                    }
                    if (baseName.equals("Machine")) {
                        if (memberName.equals("realtime")) { code.addInstruction(new PushInstrs.PushInt(0)); return; }
                        if (memberName.equals("silent")) { code.addInstruction(new PushInstrs.PushInt(1)); return; }
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new org.chuck.core.instr.MachineCall(memberName, argc));
                        return;
                    }
                    if (java.util.Set.of("RegEx", "Reflect", "SerialIO").contains(baseName)) {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new ObjectInstrs.CallBuiltinStatic("org.chuck.core." + baseName, memberName, argc));
                        return;
                    }
                }

                // 3. Handle 'me' calls
                if (e.base() instanceof ChuckAST.DotExp dot && (dot.base() instanceof ChuckAST.MeExp || (dot.base() instanceof ChuckAST.IdExp idMe && idMe.name().equals("me")))) {
                    String memberName = dot.member();
                    switch (memberName) {
                        case "yield" -> { code.addInstruction(new MiscInstrs.Yield()); return; }
                        case "dir" -> {
                            if (!e.args().isEmpty()) emitExpression(e.args().get(0), code);
                            else code.addInstruction(new PushInstrs.PushInt(0));
                            code.addInstruction(new MeInstrs.MeDir());
                            return;
                        }
                        case "args" -> { code.addInstruction(new MeInstrs.MeArgs()); return; }
                        case "arg" -> {
                            if (!e.args().isEmpty()) emitExpression(e.args().get(0), code);
                            else code.addInstruction(new PushInstrs.PushInt(0));
                            code.addInstruction(new MeInstrs.MeArg());
                            return;
                        }
                        case "id" -> { code.addInstruction(new MeInstrs.MeId()); return; }
                        case "exit" -> { code.addInstruction(new MeInstrs.MeExit()); return; }
                    }
                    // Fall back to reflection-based CallMethod on 'me'
                    code.addInstruction(new PushInstrs.PushMe());
                    for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                    String fullKey = resolveMethodKey("Shred", memberName, argTypes);
                    code.addInstruction(new ObjectInstrs.CallMethod(memberName, argc, fullKey));
                    return;
                }

                // 4. Handle super.method()
                if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp supId && supId.name().equals("super")) {
                    if (currentClass != null) {
                        UserClassDescriptor cd = userClassRegistry.get(currentClass);
                        if (cd != null && cd.parentName() != null) {
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new ObjectInstrs.CallSuperMethod(cd.parentName(), dot.member(), argc));
                            return;
                        }
                    }
                }

                // 5. General Method/Function Dispatch
                if (e.base() instanceof ChuckAST.DotExp dot) {
                    // Method call: base.member(args)
                    String baseType = getExprType(dot.base());
                    if (baseType == null && dot.base() instanceof ChuckAST.IdExp id && userClassRegistry.containsKey(id.name())) {
                        baseType = id.name();
                    }

                    if (baseType != null) {
                        // a. Check for built-in type methods (string, array, ugen.last)
                        if (baseType.equals("string") || baseType.endsWith("[]")) {
                            emitExpression(dot.base(), code);
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            String fullKey = getMethodKey(dot.member(), argTypes);
                            code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc, fullKey));
                            return;
                        }
                        if (dot.member().equals("last") && (isKnownUGenType(baseType) || isSubclassOfUGen(baseType))) {
                            emitExpression(dot.base(), code);
                            code.addInstruction(new UgenInstrs.GetLastOut());
                            return;
                        }
                        if (dot.member().equals("dot") && isVecType(baseType)) {
                            emitExpression(dot.base(), code);
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new VecInstrs.Dot());
                            return;
                        }

                        // b. Try static resolution (ClassName.staticMethod or instance.staticMethod)
                        if (baseType != null && userClassRegistry.containsKey(baseType)) {
                            String fullKey = getMethodKey(dot.member(), argTypes);
                            ChuckCode target = resolveStaticMethod(baseType, fullKey);
                            if (target == null) {
                                target = resolveStaticMethod(baseType, dot.member() + ":" + argc);
                            }
                            
                            if (target != null) {
                                String finalKey = (target.getName().equals(dot.member())) ? fullKey : dot.member() + ":" + argc;
                                checkAccess(baseType, finalKey, true, e.line(), e.column());
                                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                                code.addInstruction(new CallFunc(target, argc));
                                return;
                            }
                        }

                        // c. Instance method call
                        emitExpression(dot.base(), code);
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        String fullKey = resolveMethodKey(baseType, dot.member(), argTypes);
                        if (userClassRegistry.containsKey(baseType)) {
                            checkAccess(baseType, fullKey, true, e.line(), e.column());
                        }
                        code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc, fullKey));
                        return;
                    }
                } else if (e.base() instanceof ChuckAST.IdExp id) {
                    // Bare call: member(args)
                    String name = id.name();
                    
                    // a. Constructor?
                    if (currentClass != null && (name.equals(currentClass) || name.equals("this"))) {
                        String ctorKey = getMethodKey(currentClass, argTypes);
                        code.addInstruction(new StackInstrs.PushThis());
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new ObjectInstrs.CallMethod(currentClass, argc, ctorKey));
                        return;
                    }

                    // b. Static method in current class?
                    if (currentClass != null) {
                        String fullKey = getMethodKey(name, argTypes);
                        ChuckCode target = resolveStaticMethod(currentClass, fullKey);
                        if (target == null) target = resolveStaticMethod(currentClass, name + ":" + argc);
                        if (target != null) {
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new CallFunc(target, argc));
                            return;
                        }
                        
                        // Check for instance method in current class
                        UserClassDescriptor desc = userClassRegistry.get(currentClass);
                        if (desc != null && desc.methods().containsKey(fullKey)) {
                            code.addInstruction(new StackInstrs.PushThis());
                            for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                            code.addInstruction(new ObjectInstrs.CallMethod(name, argc, fullKey));
                            return;
                        }
                    }

                    // c. Global function?
                    String fullKey = getMethodKey(name, argTypes);
                    if (functions.containsKey(fullKey)) {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new CallFunc(functions.get(fullKey), argc));
                        return;
                    }
                    String fallbackKey = name + ":" + argc;
                    if (functions.containsKey(fallbackKey)) {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new CallFunc(functions.get(fallbackKey), argc));
                        return;
                    }

                    // d. Standard library function (print, etc.)
                    if (java.util.Set.of("print", "chout", "cherr").contains(name)) {
                        for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                        code.addInstruction(new ChuckPrint(argc));
                        return;
                    }
                }
                
                // Final fallback: emit base and hope CallMethod handles it at runtime (reflection)
                emitExpression(e.base(), code);
                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                code.addInstruction(new ObjectInstrs.CallMethod("unknown", argc));
            }
            case ChuckAST.SporkExp e -> {
                String funcName = null;
                switch (e.call().base()) {
                    case ChuckAST.IdExp id -> {
                        funcName = id.name();
                        List<String> argTypes = new ArrayList<>();
                        for (ChuckAST.Exp arg : e.call().args()) {
                            String t = getExprType(arg);
                            argTypes.add(t);
                        }
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

    private void emitChuckTarget(Object target, ChuckCode code, ChuckAST.Operator op) {
        if (target instanceof List<?> list) {
            if (list.size() > 1) {
                // Find first element to get line/column
                int line = 0, col = 0;
                if (!list.isEmpty() && list.get(0) instanceof ChuckAST.DeclExp de) {
                    line = de.line(); col = de.column();
                }
                throw new RuntimeException(currentFile + ":" + line + ":" + col + ": error: cannot '=>' from/to a multi-variable declaration");
            }
            if (!list.isEmpty()) emitChuckTarget(list.get(0), code, op);
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

                if (isUGen && op == ChuckAST.Operator.CHUCK) {
                    // => Connection
                    emitExpression(exp, code);
                    code.addInstruction(new org.chuck.core.ChuckTo());
                } else {
                    // @=> Assignment (or => value assignment for non-UGens)
                    Integer localOffset = getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new VarInstrs.StoreLocal(localOffset));
                    } else if (currentClass != null && (currentClassFields.contains(e.name()) || hasInstanceField(currentClass, e.name()))) {
                        code.addInstruction(new FieldInstrs.SetUserField(e.name()));
                    } else {
                        code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(e.name()));
                    }
                }
            }
            case ChuckAST.DotExp e -> {
                // Handle static field target: ClassName.staticField
                String potentialClassName = resolveClassName(e.base());
                if (potentialClassName != null) {
                    UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
                    if (classDesc != null && (classDesc.staticInts().containsKey(e.member()) || classDesc.staticObjects().containsKey(e.member()))) {
                        checkAccess(potentialClassName, e.member(), false, e.line(), e.column());
                        code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, e.member()));
                        return;
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
                    // Vector or Object field write: val => v.x / v.y / obj.member
                    emitExpression(e.base(), code);
                    String baseType = getExprType(e.base());
                    if (baseType != null && userClassRegistry.containsKey(baseType)) {
                        checkAccess(baseType, e.member(), false, e.line(), e.column());
                    }
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
                if (inPreCtor) {
                    code.addInstruction(new StackInstrs.PushThis());
                    code.addInstruction(new SetMemberIntByName(e.name()));
                    return;
                }
                String type = e.type();
                boolean isUGen = isKnownUGenType(type) || isSubclassOfUGen(type);
                boolean isPrimitive = type.equals("int") || type.equals("float") || type.equals("complex") || type.equals("polar");

                if (isPrimitive) {
                    // source => int N;
                    // 1. emitExpression(e) pushes N (value 0)
                    // 2. Pop N(0)
                    // 3. Store source into N (leaves source on stack)
                    emitExpression(e, code);
                    code.addInstruction(new StackInstrs.Pop());
                    Integer localOffset = getLocalOffset(e.name());
                    if (localOffset != null) {
                        code.addInstruction(new VarInstrs.StoreLocal(localOffset));
                    } else {
                        code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(e.name()));
                    }
                } else {
                    emitExpression(e, code); // Pushes new object 'target'
                    // Stack is now: [..., source, target]

                    if (isUGen && op == ChuckAST.Operator.CHUCK) {
                        code.addInstruction(new org.chuck.core.ChuckTo());
                    } else {
                        code.addInstruction(new StackInstrs.Pop());
                        // Now save the source object to its variable (overwriting the newly created one)
                        // Wait! If it was source => Mesh2D m;
                        // ChucK creates a Mesh2D m, THEN connects source to it.
                        // BUT if it was source @=> Mesh2D m;
                        // ChucK should probably just set m = source.
                        Integer localOffset = getLocalOffset(e.name());
                        if (localOffset != null) {
                            code.addInstruction(new VarInstrs.StoreLocal(localOffset));
                        } else {
                            code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(e.name()));
                        }
                    }
                }
            }
            case ChuckAST.BinaryExp e -> {
                if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                    // In a chain: source => target1 => target2
                    // When called with target = (target1 => target2)
                    // We should first process target1, then target2.
                    emitChuckTarget(e.lhs(), code, e.op());
                    emitChuckTarget(e.rhs(), code, e.op());
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

    
    private void checkAccess(String className, String memberName, boolean isMethod, int line, int col) {
        UserClassDescriptor desc = userClassRegistry.get(className);
        if (desc == null) return;

        org.chuck.compiler.ChuckAST.AccessModifier access = isMethod ? desc.methodAccess().get(memberName) : desc.fieldAccess().get(memberName);
        if (access == null) access = org.chuck.compiler.ChuckAST.AccessModifier.PUBLIC;

        if (access == org.chuck.compiler.ChuckAST.AccessModifier.PUBLIC) return;

        if (access == org.chuck.compiler.ChuckAST.AccessModifier.PRIVATE) {
            if (currentClass == null || !currentClass.equals(className)) {
                throw new RuntimeException(currentFile + ":" + line + ":" + col
                        + ": error: cannot access private " + (isMethod ? "method" : "field") + " '" + memberName + "' of class '" + className + "'");
            }
        }

        if (access == org.chuck.compiler.ChuckAST.AccessModifier.PROTECTED) {
            if (currentClass == null || !isSubclassOf(currentClass, className)) {
                throw new RuntimeException(currentFile + ":" + line + ":" + col
                        + ": error: cannot access protected " + (isMethod ? "method" : "field") + " '" + memberName + "' of class '" + className + "'");
            }
        }
    }

    private boolean isSubclassOf(String child, String parent) {
        if (child == null) return false;
        if (child.equals(parent)) return true;
        UserClassDescriptor desc = userClassRegistry.get(child);
        if (desc == null) return false;
        return isSubclassOf(desc.parentName(), parent);
    }

    private Integer getLocalOffset(String name) {
        // Search scopes from innermost to outermost
        for (int i = localScopes.size() - 1; i >= 0; i--) {
            Integer offset = localScopes.get(i).get(name);
            if (offset != null) {
                return offset;
            }
        }
        return null;
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
                if (globalVarTypes.containsKey(id.name()) || getVarTypeByName(id.name()) != null) {
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
"""

with open("src/main/java/org/chuck/compiler/ChuckEmitter.java", "w") as f:
    f.write(content)
