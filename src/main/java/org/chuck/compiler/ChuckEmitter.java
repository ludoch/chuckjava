package org.chuck.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.chuck.core.AdvanceTime;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckCompilerException;
import org.chuck.core.ChuckLanguage;
import org.chuck.core.SetMemberIntByName;
import org.chuck.core.UGenRegistry;
import org.chuck.core.UserClassDescriptor;
import org.chuck.core.instr.ArrayInstrs;
import org.chuck.core.instr.FieldInstrs;
import org.chuck.core.instr.MathInstrs;
import org.chuck.core.instr.MiscInstrs;
import org.chuck.core.instr.StackInstrs;
import org.chuck.core.instr.UgenInstrs;
import org.chuck.core.instr.VarInstrs;

/** Emits executable VM instructions from a parsed AST. */
public class ChuckEmitter {

  private final Map<String, ChuckCode> functions;
  private final java.util.Stack<Map<String, Integer>> localScopes = new java.util.Stack<>();
  private final java.util.Stack<Map<String, String>> localTypeScopes = new java.util.Stack<>();

  /** Tracks variable name -> declared as const. */
  private final java.util.Set<String> constants = new java.util.HashSet<>();

  private int localCount = 0;
  private int maxLocalCount = 0;
  private boolean inPreCtor = false;

  private final Map<String, UserClassDescriptor> userClassRegistry;

  /** Tracks global variable name → declared type for compile-time conflict detection. */
  private final Map<String, String> globalVarTypes = new HashMap<>();

  private final java.util.Set<String> importedFiles = new java.util.HashSet<>();
  private final Map<String, List<ChuckAST.Stmt>> importCache = new HashMap<>();

  private java.nio.file.Path resolveImportPath(String importPath) {
    java.nio.file.Path path = java.nio.file.Paths.get(currentFile).getParent();
    if (path == null) path = java.nio.file.Paths.get(".");

    java.nio.file.Path target = path.resolve(importPath);
    if (!java.nio.file.Files.exists(target)) {
      if (java.nio.file.Files.exists(path.resolve(importPath + ".ck")))
        target = path.resolve(importPath + ".ck");
      else if (java.nio.file.Files.exists(path.resolve(".deps").resolve(importPath + ".ck")))
        target = path.resolve(".deps").resolve(importPath + ".ck");
    }
    return target;
  }

  private List<ChuckAST.Stmt> parseImport(String importPath) {
    try {
      java.nio.file.Path path = resolveImportPath(importPath);
      String pathStr = path.toString();
      if (importCache.containsKey(pathStr)) return importCache.get(pathStr);
      // if (importedFiles.contains(pathStr)) return java.util.Collections.emptyList();
      importedFiles.add(pathStr);

      String source = java.nio.file.Files.readString(path);
      org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(source);
      ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
      org.antlr.v4.runtime.CommonTokenStream tokens =
          new org.antlr.v4.runtime.CommonTokenStream(lexer);
      ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
      ChuckASTVisitor visitor = new ChuckASTVisitor();
      @SuppressWarnings("unchecked")
      List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());
      importCache.put(pathStr, ast);
      return ast;
    } catch (Exception e) {
      return java.util.Collections.emptyList();
    }
  }

  private List<ChuckAST.Stmt> getParsedImport(String importPath) {
    return parseImport(importPath);
  }

  /** Tracks operator function return types for expression type inference. */
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
  private static final java.util.Set<String> CORE_UGENS =
      java.util.Set.of(
          "OscIn",
          "OscOut",
          "OscMsg",
          "FileIO",
          "IO",
          "Std",
          "Math",
          "Machine",
          "UGen",
          "UGen_Multi",
          "UGen_Stereo",
          "UAna",
          "Shred",
          "Thread",
          "ChucK",
          "Event");

  // Core non-UGen data types
  private static final java.util.Set<String> CORE_DATA_TYPES =
      java.util.Set.of(
          "int",
          "float",
          "string",
          "time",
          "dur",
          "void",
          "vec2",
          "vec3",
          "vec4",
          "complex",
          "polar",
          "Object",
          "Array",
          "Type",
          "Function",
          "auto",
          "MidiMsg",
          "HidMsg",
          "OscMsg",
          "FileIO",
          "IO",
          "SerialIO",
          "OscIn",
          "OscOut",
          "OscBundle",
          "MidiIn",
          "MidiOut",
          "MidiFileIn",
          "MidiFileOut",
          "MidiPlayer",
          "Hid",
          "StringTokenizer",
          "RegEx",
          "Reflect");

  public Map<String, ChuckCode> getFunctions() {
    return functions;
  }

  public java.util.Stack<Map<String, Integer>> getLocalScopes() {
    return localScopes;
  }

  public java.util.Stack<Map<String, String>> getLocalTypeScopes() {
    return localTypeScopes;
  }

  public java.util.Set<String> getConstants() {
    return constants;
  }

  public int getLocalCount() {
    return localCount;
  }

  public void setLocalCount(int localCount) {
    this.localCount = localCount;
    if (this.localCount > maxLocalCount) maxLocalCount = this.localCount;
  }

  public int getMaxLocalCount() {
    return maxLocalCount;
  }

  public void resetMaxLocalCount() {
    this.maxLocalCount = 0;
  }

  public boolean isInPreCtor() {
    return inPreCtor;
  }

  public void setInPreCtor(boolean inPreCtor) {
    this.inPreCtor = inPreCtor;
  }

  public Map<String, UserClassDescriptor> getUserClassRegistry() {
    return userClassRegistry;
  }

  public Map<String, String> getGlobalVarTypes() {
    return globalVarTypes;
  }

  public String getCurrentClass() {
    return currentClass;
  }

  public void setCurrentClass(String currentClass) {
    this.currentClass = currentClass;
  }

  public java.util.Set<String> getCurrentClassFields() {
    return currentClassFields;
  }

  public void setCurrentClassFields(java.util.Set<String> currentClassFields) {
    this.currentClassFields = currentClassFields;
  }

  public String getCurrentFile() {
    return currentFile;
  }

  public java.util.Stack<List<Integer>> getBreakJumps() {
    return breakJumps;
  }

  public java.util.Stack<List<Integer>> getContinueJumps() {
    return continueJumps;
  }

  public String getCurrentFuncReturnType() {
    return currentFuncReturnType;
  }

  public void setCurrentFuncReturnType(String currentFuncReturnType) {
    this.currentFuncReturnType = currentFuncReturnType;
  }

  public boolean isCurrentFuncHasReturn() {
    return currentFuncHasReturn;
  }

  public void setCurrentFuncHasReturn(boolean currentFuncHasReturn) {
    this.currentFuncHasReturn = currentFuncHasReturn;
  }

  public boolean isInStaticFuncContext() {
    return inStaticFuncContext;
  }

  public void setInStaticFuncContext(boolean inStaticFuncContext) {
    this.inStaticFuncContext = inStaticFuncContext;
  }

  public java.util.Set<String> getEmptyArrayVars() {
    return emptyArrayVars;
  }

  public Map<String, String> getFunctionReturnTypes() {
    return functionReturnTypes;
  }

  public void setCurrentClassMethodsList(List<ChuckAST.FuncDefStmt> currentClassMethodsList) {
    this.currentClassMethodsList = currentClassMethodsList;
  }

  private void error(int line, int col, String msg) {
    throw new ChuckCompilerException(msg, currentFile, line, col);
  }

  boolean isObjectType(String type) {
    return ChuckLanguage.isObjectType(type) || userClassRegistry.containsKey(type);
  }

  boolean isKnownType(String type) {
    if (type == null) return false;
    String baseType = getBaseType(type);
    return ChuckLanguage.CORE_DATA_TYPES.contains(baseType)
        || ChuckLanguage.CORE_UGENS.contains(baseType)
        || UGenRegistry.isRegistered(baseType)
        || userClassRegistry.containsKey(baseType);
  }

  boolean isIOType(String type) {
    if (type == null) return false;
    return type.equals("IO")
        || type.equals("FileIO")
        || type.equals("OscOut")
        || type.equals("OscIn");
  }

  boolean isKnownUGenType(String type) {
    return UGenRegistry.isRegistered(type) || ChuckLanguage.CORE_UGENS.contains(type);
  }

  String getMethodKey(String name, List<String> argTypes) {
    StringBuilder sb = new StringBuilder(name).append(":");
    if (argTypes == null || argTypes.isEmpty()) return sb.append("0").toString();
    for (int i = 0; i < argTypes.size(); i++) {
      sb.append(argTypes.get(i));
      if (i < argTypes.size() - 1) sb.append(",");
    }
    return sb.toString();
  }

  String resolveMethodKey(String className, String mName, List<String> callArgTypes) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    if (desc == null) {
      return mName + ":" + (callArgTypes != null ? callArgTypes.size() : 0);
    }

    // 1. Try exact match with types
    String fullKey = getMethodKey(mName, callArgTypes);
    String t = className;
    while (t != null) {
      UserClassDescriptor d = userClassRegistry.get(t);
      if (d == null) break;
      if (d.methods().containsKey(fullKey) || d.staticMethods().containsKey(fullKey))
        return fullKey;
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

  public ChuckEmitter(
      Map<String, UserClassDescriptor> registry, Map<String, ChuckCode> preloadedFunctions) {
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
    this.globalVarTypes.put("Event", "Event");
    this.globalVarTypes.put("me", "Shred");
    this.globalVarTypes.put("Machine", "Machine");

    // Add all user classes from registry as known global types
    for (String className : userClassRegistry.keySet()) {
      this.globalVarTypes.put(className, className);
    }

    // Register built-in types as known classes for field resolution
    if (!userClassRegistry.containsKey("Std")) {
      userClassRegistry.put(
          "Std",
          new UserClassDescriptor(
              "Std",
              "Object",
              new ArrayList<>(),
              new ArrayList<>(),
              new HashMap<>(),
              new HashMap<>()));
    }
    if (!userClassRegistry.containsKey("string")) {
      userClassRegistry.put(
          "string",
          new UserClassDescriptor(
              "string",
              "Object",
              new ArrayList<>(),
              new ArrayList<>(),
              new HashMap<>(),
              new HashMap<>()));
    }
    if (!userClassRegistry.containsKey("vec2")) {
      List<String[]> fields = new ArrayList<>();
      fields.add(new String[] {"float", "x"});
      fields.add(new String[] {"float", "y"});
      userClassRegistry.put(
          "vec2",
          new UserClassDescriptor(
              "vec2", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
    }
    if (!userClassRegistry.containsKey("vec3")) {
      List<String[]> fields = new ArrayList<>();
      fields.add(new String[] {"float", "x"});
      fields.add(new String[] {"float", "y"});
      fields.add(new String[] {"float", "z"});
      userClassRegistry.put(
          "vec3",
          new UserClassDescriptor(
              "vec3", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
    }
    if (!userClassRegistry.containsKey("vec4")) {
      List<String[]> fields = new ArrayList<>();
      fields.add(new String[] {"float", "x"});
      fields.add(new String[] {"float", "y"});
      fields.add(new String[] {"float", "z"});
      fields.add(new String[] {"float", "w"});
      userClassRegistry.put(
          "vec4",
          new UserClassDescriptor(
              "vec4", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
    }
    if (!userClassRegistry.containsKey("complex")) {
      List<String[]> fields = new ArrayList<>();
      fields.add(new String[] {"float", "re"});
      fields.add(new String[] {"float", "im"});
      userClassRegistry.put(
          "complex",
          new UserClassDescriptor(
              "complex", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
    }
    if (!userClassRegistry.containsKey("polar")) {
      List<String[]> fields = new ArrayList<>();
      fields.add(new String[] {"float", "mag"});
      fields.add(new String[] {"float", "phase"});
      userClassRegistry.put(
          "polar",
          new UserClassDescriptor(
              "polar", "Object", fields, new ArrayList<>(), new HashMap<>(), new HashMap<>()));
    }

    if (existing != null) this.globalVarTypes.putAll(existing);
  }

  /** Returns all classes this emitter has registered (including from imports). */

  /** Returns public operator functions (keys starting with __pub_op__). */
  public Map<String, ChuckCode> getPublicFunctions() {
    Map<String, ChuckCode> result = new HashMap<>();
    for (var e : functions.entrySet()) {
      if (e.getKey().startsWith("__pub_op__")) {
        result.put(e.getKey(), e.getValue());
      }
    }
    return result;
  }

  String getVarTypeByName(String name) {
    if (name == null) return null;
    for (int i = localTypeScopes.size() - 1; i >= 0; i--) {
      String type = localTypeScopes.get(i).get(name);
      if (type != null) return type;
    }
    return globalVarTypes.get(name);
  }

  String getVarType(ChuckAST.Exp exp) {
    return switch (exp) {
      case ChuckAST.IdExp id -> {
        String type = null;
        for (int i = localTypeScopes.size() - 1; i >= 0; i--) {
          type = localTypeScopes.get(i).get(id.name());
          if (type != null) break;
        }
        if (type == null && currentClass != null) {
          type = getFieldType(currentClass, id.name());
        }
        if (type == null) {
          type = globalVarTypes.get(id.name());
        }
        yield type;
      }
      default -> null;
    };
  }

  String getFieldType(String className, String fieldName) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    while (desc != null) {
      for (String[] field : desc.fields()) {
        if (field[1].equals(fieldName)) return field[0];
      }
      if (desc.staticInts().containsKey(fieldName)
          || desc.staticIsDouble().containsKey(fieldName)
          || desc.staticObjects().containsKey(fieldName)) {
        // We'd need to track the types of static fields too if we wanted exact types here
        // For now, let's assume it's one of the types that exists in the maps
        return "Object"; // Fallback, but should ideally be tracked
      }
      desc = desc.parentName() != null ? userClassRegistry.get(desc.parentName()) : null;
    }
    return null;
  }

  String getExprType(ChuckAST.Exp exp) {
    if (exp == null) return null;
    TypeInferenceEngine engine =
        new TypeInferenceEngine(
            userClassRegistry, globalVarTypes, localTypeScopes, functionReturnTypes, currentClass);
    return engine.getExprType(exp);
  }

  void flattenStmts(List<ChuckAST.Stmt> input, List<ChuckAST.Stmt> output) {
    for (ChuckAST.Stmt s : input) {
      if (s instanceof ChuckAST.BlockStmt b) {
        flattenStmts(b.statements(), output);
      } else {
        output.add(s);
      }
    }
  }

  boolean isSubclassOfUGen(String className) {
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

  ChuckCode resolveStaticMethod(String className, String methodKey) {
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

  String getBaseType(String type) {
    if (type == null) return null;
    return type.replaceAll("\\[\\]", "");
  }

  boolean hasInstanceField(String className, String fieldName) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    while (desc != null) {
      for (String[] field : desc.fields()) {
        if (field[1].equals(fieldName)) return true;
      }
      desc = desc.parentName() != null ? userClassRegistry.get(desc.parentName()) : null;
    }
    return false;
  }

  boolean hasStaticField(String className, String fieldName) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    while (desc != null) {
      for (String[] field : desc.staticFields()) {
        if (field[1].equals(fieldName)) return true;
      }
      desc = desc.parentName() != null ? userClassRegistry.get(desc.parentName()) : null;
    }
    return false;
  }

  String resolveClassName(ChuckAST.Exp base) {
    if (base instanceof ChuckAST.IdExp id) {
      if (userClassRegistry.containsKey(id.name())) return id.name();
      if (CORE_DATA_TYPES.contains(id.name()) || isKnownUGenType(id.name())) return id.name();
    }
    return null;
  }

  String getOpSymbol(ChuckAST.Operator op) {
    return switch (op) {
      case PLUS -> "+";
      case MINUS -> "-";
      case TIMES -> "*";
      case DIVIDE -> "/";
      case PERCENT -> "%";
      case LT -> "<";
      case LE -> "<=";
      case GT -> ">";
      case GE -> ">=";
      case EQ -> "==";
      case NEQ -> "!=";
      default -> null;
    };
  }

  private void registerClassNames(ChuckAST.Stmt stmt) {
    if (stmt == null) return;
    switch (stmt) {
      case ChuckAST.ImportStmt s -> {
        List<ChuckAST.Stmt> imported = parseImport(s.path());
        for (ChuckAST.Stmt i : imported) registerClassNames(i);
      }
      case ChuckAST.ClassDefStmt s -> {
        userClassRegistry.putIfAbsent(
            s.name(),
            new UserClassDescriptor(
                s.name(),
                s.parentName(),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                null,
                s.isAbstract(),
                s.isInterface(),
                null,
                new HashMap<>(),
                new HashMap<>(),
                s.access(),
                s.doc(),
                new HashMap<>(),
                new HashMap<>()));
        UserClassDescriptor desc = userClassRegistry.get(s.name());
        registerStaticFieldsRecursive(s.body(), desc);
        for (ChuckAST.Stmt inner : s.body()) registerClassNames(inner);
      }
      case ChuckAST.BlockStmt b -> {
        for (ChuckAST.Stmt inner : b.statements()) registerClassNames(inner);
      }
      default -> {}
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
    String staticName = null;
    String type = null;
    if (inner instanceof ChuckAST.DeclStmt ds && ds.isStatic()) {
      staticName = ds.name();
      type = ds.type();
    } else if (inner instanceof ChuckAST.ExpStmt es) {
      if (es.exp() instanceof ChuckAST.DeclExp de && de.isStatic()) {
        staticName = de.name();
        type = de.type();
      } else if (es.exp() instanceof ChuckAST.BinaryExp be
          && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)
          && be.rhs() instanceof ChuckAST.DeclExp de
          && de.isStatic()) {
        staticName = de.name();
        type = de.type();
      }
    }
    if (staticName != null) {
      if (type.equals("float")) {
        desc.staticIsDouble().put(staticName, true);
        desc.staticInts().putIfAbsent(staticName, 0L);
      } else if (type.equals("int")) {
        desc.staticInts().putIfAbsent(staticName, 0L);
      } else {
        desc.staticObjects().putIfAbsent(staticName, null);
      }
    }
  }

  private void registerSignatures(
      ChuckAST.Stmt stmt,
      Map<String, ChuckCode> functions,
      Map<String, String> functionReturnTypes) {
    if (stmt instanceof ChuckAST.ImportStmt s) {
      // Need to reload/reparse but we've already done it in registerClassNames.
      // Actually, we should probably cache the parsed ASTs.
      List<ChuckAST.Stmt> imported = getParsedImport(s.path());
      for (ChuckAST.Stmt i : imported) registerSignatures(i, functions, functionReturnTypes);
    } else if (stmt instanceof ChuckAST.FuncDefStmt s) {
      String name = s.name();
      boolean isPublic =
          false; // FuncDefStmt doesn't track public yet but we check prefix in visitor

      // Standardize operator names if they weren't already standardized by visitor
      if (name.startsWith("@operator") || name.startsWith("operator")) {
        String opSym =
            name.startsWith("@operator")
                ? name.substring("@operator".length()).trim()
                : name.substring("operator".length()).trim();
        if (opSym.startsWith("(") && opSym.endsWith(")")) {
          opSym = opSym.substring(1, opSym.length() - 1).trim();
        }
        name = "__op__" + opSym;
      }

      String key = getMethodKey(name, s.argTypes());
      if (!functions.containsKey(key)) {
        ChuckCode c = new ChuckCode(s.name());
        c.setSignature(s.argTypes().size(), s.returnType() != null ? s.returnType() : "void");
        functions.put(key, c);
      }
      if (s.returnType() != null && !s.returnType().equals("void")) {
        functionReturnTypes.put(key, s.returnType());
      }
    } else if (stmt instanceof ChuckAST.ClassDefStmt s) {
      // Do NOT register signatures of class methods as global functions
    } else if (stmt instanceof ChuckAST.BlockStmt b) {
      for (ChuckAST.Stmt inner : b.statements()) {
        registerSignatures(inner, functions, functionReturnTypes);
      }
    }
  }

  private void emitBodies(ChuckAST.Stmt stmt) {
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
      UserClassDescriptor desc = userClassRegistry.get(s.name());
      if (desc != null) {
        code.addInstruction(new MiscInstrs.RegisterClass(s.name(), desc));
      }
      for (ChuckAST.Stmt inner : s.body()) {
        registerClassesToCode(inner, code);
      }
    }
  }

  public record EmitResult(
      ChuckCode code,
      Map<String, String> globalVarTypes,
      Map<String, String> globalDocs,
      Map<String, String> functionDocs) {}

  public EmitResult emitWithDocs(
      List<ChuckAST.Stmt> statements, String programName, Map<String, String> existingGlobals) {
    localScopes.clear();
    localTypeScopes.clear();
    localScopes.push(new HashMap<>());
    localTypeScopes.push(new HashMap<>());
    localCount = 0;

    globalVarTypes.clear();
    initGlobalTypes(existingGlobals);
    currentFile = programName;

    boolean hasContent =
        statements.stream()
            .anyMatch(s -> !(s instanceof ChuckAST.BlockStmt bs && bs.statements().isEmpty()));
    if (!hasContent) {
      throw new ChuckCompilerException("syntax error\n(empty file)", programName, 1, 1);
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
    localScopes.clear();
    localTypeScopes.clear();
    localScopes.push(new HashMap<>());
    localTypeScopes.push(new HashMap<>());
    constants.clear();
    emptyArrayVars.clear();

    ChuckCode code = new ChuckCode(programName);
    code.addInstruction(new VarInstrs.MoveArgs(0));

    for (ChuckAST.Stmt stmt : statements) {
      registerClassesToCode(stmt, code);
    }

    resetMaxLocalCount();
    Map<String, String> globalDocsMap = new HashMap<>();
    for (ChuckAST.Stmt stmt : statements) {
      if (!(stmt instanceof ChuckAST.FuncDefStmt)
          && !(stmt instanceof ChuckAST.ImportStmt)
          && !(stmt instanceof ChuckAST.ClassDefStmt)) {
        emitStatementWithDocs(stmt, code, globalDocsMap);
      }
    }
    code.setStackSize(getMaxLocalCount());

    // Optimize all generated code objects
    Optimizer.optimize(code);
    for (ChuckCode func : functions.values()) {
      Optimizer.optimize(func);
    }
    for (UserClassDescriptor desc : userClassRegistry.values()) {
      Optimizer.optimize(desc.preCtorCode());
      Optimizer.optimize(desc.staticInitCode());
    }

    return new EmitResult(code, new HashMap<>(globalVarTypes), globalDocsMap, functionDocs);
  }

  private void registerSignaturesWithDocs(
      ChuckAST.Stmt stmt,
      Map<String, ChuckCode> functions,
      Map<String, String> functionReturnTypes,
      Map<String, String> functionDocs) {
    if (stmt instanceof ChuckAST.ImportStmt s) {
      List<ChuckAST.Stmt> imported = getParsedImport(s.path());
      for (ChuckAST.Stmt i : imported)
        registerSignaturesWithDocs(i, functions, functionReturnTypes, functionDocs);
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

  private void emitStatementWithDocs(
      ChuckAST.Stmt stmt, ChuckCode code, Map<String, String> globalDocs) {
    if (stmt instanceof ChuckAST.DeclStmt ds && ds.doc() != null && localScopes.size() == 1) {
      globalDocs.put(ds.name(), ds.doc());
    } else if (stmt instanceof ChuckAST.ExpStmt es
        && es.exp() instanceof ChuckAST.BinaryExp be
        && be.rhs() instanceof ChuckAST.DeclExp de
        && de.doc() != null
        && localScopes.size() == 1) {
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

  public ChuckCode emit(
      List<ChuckAST.Stmt> statements, String programName, Map<String, String> existingGlobals) {
    return emitWithDocs(statements, programName, existingGlobals).code();
  }

  private void emitStatement(ChuckAST.Stmt stmt, ChuckCode code) {
    new StatementEmitter(this).emitStatement(stmt, code);
  }

  void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
    new ExpressionEmitter(this).emitExpression(exp, code);
  }

  /**
   * Emits instructions for the target of a ChucK operator (=> or @=>).
   *
   * <p><b>Stack Protocol:</b>
   *
   * <ul>
   *   <li>[Before]: The value to be assigned/connected is on the stack.
   *   <li>[After]: The value remains on the stack (for chaining), unless it was a primitive
   *       assignment which may pop depending on instruction.
   * </ul>
   */
  void emitChuckTarget(Object target, ChuckCode code, ChuckAST.Operator op) {
    if (target instanceof List<?> list) {
      if (list.size() > 1) {
        // Find first element to get line/column
        int line = 0, col = 0;
        if (!list.isEmpty() && list.get(0) instanceof ChuckAST.DeclExp de) {
          line = de.line();
          col = de.column();
        }
        error(line, col, "cannot '=>' from/to a multi-variable declaration");
      }
      if (!list.isEmpty()) emitChuckTarget(list.get(0), code, op);
      return;
    }

    if (!(target instanceof ChuckAST.Exp)) return;
    ChuckAST.Exp exp = (ChuckAST.Exp) target;

    switch (exp) {
      case ChuckAST.IdExp e -> {
        if (e.name().equals("pi")
            || e.name().equals("e")
            || e.name().equals("maybe")
            || e.name().equals("true")
            || e.name().equals("false")
            || e.name().equals("null")
            || constants.contains(e.name())) {
          error(e.line(), e.column(), "cannot assign to read-only value '" + e.name() + "'");
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
            if ("int".equals(type)) code.addInstruction(new VarInstrs.StoreLocalInt(localOffset));
            else if ("float".equals(type))
              code.addInstruction(new VarInstrs.StoreLocalFloat(localOffset));
            else code.addInstruction(new VarInstrs.StoreLocal(localOffset));
          } else if (currentClass != null
              && (currentClassFields.contains(e.name())
                  || hasInstanceField(currentClass, e.name()))) {
            code.addInstruction(new FieldInstrs.SetUserField(e.name()));
          } else {
            if ("int".equals(type)) code.addInstruction(new VarInstrs.SetGlobalInt(e.name()));
            else if ("float".equals(type))
              code.addInstruction(new VarInstrs.SetGlobalFloat(e.name()));
            else code.addInstruction(new VarInstrs.SetGlobalObjectOrInt(e.name()));
          }
        }
      }
      case ChuckAST.DotExp e -> {
        // Handle static field target: ClassName.staticField
        String potentialClassName = resolveClassName(e.base());
        if (potentialClassName != null) {
          UserClassDescriptor classDesc = userClassRegistry.get(potentialClassName);
          if (classDesc != null
              && (classDesc.staticInts().containsKey(e.member())
                  || classDesc.staticObjects().containsKey(e.member()))) {
            checkAccess(potentialClassName, e.member(), false, e.line(), e.column());
            code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, e.member()));
            return;
          }
        }
        if (e.base() instanceof ChuckAST.IdExp baseId
            && baseId.name().equals("Std")
            && e.member().equals("mtof")) {
          code.addInstruction(new MathInstrs.StdFunc("mtof", 1));
        } else if (e.base() instanceof ChuckAST.IdExp baseId
            && baseId.name().equals("Std")
            && e.member().equals("ftom")) {
          code.addInstruction(new MathInstrs.StdFunc("ftom", 1));
        } else if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Math")) {
          // Math constants are read-only and cannot be assigned to
          switch (e.member()) {
            case "PI",
                "TWO_PI",
                "HALF_PI",
                "E",
                "INFINITY",
                "NEGATIVE_INFINITY",
                "NaN",
                "nan",
                "infinity",
                "negative_infinity" ->
                error(
                    e.line(),
                    e.column(),
                    "'Math." + e.member() + "' is a constant, and is not assignable");
            default -> {}
          }
          // val => Math.func: apply math function to the value on stack
          switch (e.member()) {
            case "isinf",
                "isnan",
                "sin",
                "cos",
                "sqrt",
                "abs",
                "floor",
                "ceil",
                "log",
                "log2",
                "log10",
                "exp",
                "round",
                "trunc",
                "dbtolin",
                "dbtopow",
                "lintodb",
                "powtodb",
                "dbtorms",
                "rmstodb" ->
                code.addInstruction(new MathInstrs.MathFunc(e.member()));
            default -> code.addInstruction(new MathInstrs.MathFunc(e.member()));
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
        boolean isPrimitive =
            type.equals("int")
                || type.equals("float")
                || type.equals("complex")
                || type.equals("polar");

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
      default -> emitExpression(exp, code);
    }
  }

  /**
   * Emits instructions for the swap operator (<=>).
   *
   * <p><b>Stack Protocol:</b>
   *
   * <ul>
   *   <li>[Before]: (Empty or current context)
   *   <li>[After]: Net stack change is zero.
   * </ul>
   */
  void emitSwapTarget(ChuckAST.Exp lhs, ChuckAST.Exp rhs, ChuckCode code) {
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
        code.addInstruction(
            new VarInstrs.StoreLocalOrGlobal(rid.name(), getLocalOffset(rid.name())));
      }
      code.addInstruction(new StackInstrs.Pop());
      if (lhs instanceof ChuckAST.IdExp lid) {
        code.addInstruction(
            new VarInstrs.StoreLocalOrGlobal(lid.name(), getLocalOffset(lid.name())));
      }
    }
  }

  void checkAccess(String className, String memberName, boolean isMethod, int line, int col) {
    UserClassDescriptor desc = userClassRegistry.get(className);
    if (desc == null) return;

    org.chuck.compiler.ChuckAST.AccessModifier access =
        isMethod ? desc.methodAccess().get(memberName) : desc.fieldAccess().get(memberName);
    if (access == null) access = org.chuck.compiler.ChuckAST.AccessModifier.PUBLIC;

    if (access == org.chuck.compiler.ChuckAST.AccessModifier.PUBLIC) return;

    if (access == org.chuck.compiler.ChuckAST.AccessModifier.PRIVATE) {
      if (currentClass == null || !currentClass.equals(className)) {
        error(
            line,
            col,
            "cannot access private "
                + (isMethod ? "method" : "field")
                + " '"
                + memberName
                + "' of class '"
                + className
                + "'");
      }
    }

    if (access == org.chuck.compiler.ChuckAST.AccessModifier.PROTECTED) {
      if (currentClass == null || !isSubclassOf(currentClass, className)) {
        error(
            line,
            col,
            "cannot access protected "
                + (isMethod ? "method" : "field")
                + " '"
                + memberName
                + "' of class '"
                + className
                + "'");
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

  Integer getLocalOffset(String name) {
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
   * Returns the array index for a vec/complex/polar field name, or -1 if not a vec field. x/re/mag
   * -> 0, y/im/phase -> 1, z -> 2, w -> 3.
   */
  static int vecFieldIndex(String member) {
    return switch (member) {
      case "x", "re", "mag", "magnitude" -> 0;
      case "y", "im", "phase" -> 1;
      case "z" -> 2;
      case "w" -> 3;
      default -> -1;
    };
  }

  /** Returns true if the type name is a vector or complex primitive type. */
  static boolean isVecType(String type) {
    return ChuckLanguage.isVectorType(type) || "complex".equals(type) || "polar".equals(type);
  }

  /**
   * Checks a static initializer's source expression for disallowed references (member/local vars
   * and funcs).
   */
  void checkStaticInitSource(ChuckAST.Exp exp) {
    switch (exp) {
      case null -> {}

      case ChuckAST.IdExp id -> {
        // Member field access
        if (currentClassFields.contains(id.name())) {
          error(
              id.line(),
              id.column(),
              "cannot access non-static variable '"
                  + currentClass
                  + "."
                  + id.name()
                  + "' to initialize a static variable");
        }
        // Local variable from outer scope (not a builtin)
        if (globalVarTypes.containsKey(id.name()) || getVarTypeByName(id.name()) != null) {
          // Check if it's a global var defined outside the class (local to the file)
          if (!currentClassFields.contains(id.name())) {
            error(
                id.line(),
                id.column(),
                "cannot access local variable '" + id.name() + "' to initialize a static variable");
          }
        }
      }
      case ChuckAST.CallExp call -> {
        if (call.base() instanceof ChuckAST.IdExp fid) {
          // Check if it's a member method (non-static) of the current class
          for (ChuckAST.FuncDefStmt m : currentClassMethodsList) {
            if (m.name().equals(fid.name()) && !m.isStatic() && !m.name().equals(currentClass)) {
              error(
                  fid.line(),
                  fid.column(),
                  "cannot call non-static function '"
                      + currentClass
                      + "."
                      + fid.name()
                      + "()' to initialize a static variable");
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
              error(
                  fid.line(),
                  fid.column(),
                  "cannot call local function '"
                      + fid.name()
                      + "()' to initialize a static variable");
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
      case ChuckAST.UnaryExp u -> checkStaticInitSource(u.exp());
      default -> {}
    }
  }

  /** Checks that no static variable is assigned inside a nested block within a class body. */
  void checkNoStaticInBlock(List<ChuckAST.Stmt> stmts) {
    for (ChuckAST.Stmt st : stmts) {
      switch (st) {
        case ChuckAST.ExpStmt es
            when es.exp() instanceof ChuckAST.BinaryExp be
                && (be.op() == ChuckAST.Operator.CHUCK || be.op() == ChuckAST.Operator.AT_CHUCK)
                && be.rhs() instanceof ChuckAST.DeclExp rhs
                && rhs.isStatic() ->
            error(be.line(), be.column(), "static variables must be declared at class scope");
        case ChuckAST.BlockStmt inner -> checkNoStaticInBlock(inner.statements());
        default -> {}
      }
    }
  }
}
