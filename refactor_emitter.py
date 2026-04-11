import re
import sys

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # 1. Inject checkAccess and isSubclassOf
    if "private void checkAccess" not in content:
        helper_methods = """
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
"""
        content = content.replace("private Integer getLocalOffset(String name) {", helper_methods + "\n    private Integer getLocalOffset(String name) {")

    # 2. Add AccessModifier logic to UserClassDescriptor creations in constructor
    content = content.replace('new HashMap<>()));', 'new HashMap<>(), null, false, false, null, new HashMap<>(), new HashMap<>(), ChuckAST.AccessModifier.PUBLIC));')
    
    # 3. Add fieldAccess and methodAccess maps in ClassDefStmt handling
    class_def_setup = """                UserClassDescriptor existing = userClassRegistry.get(s.name());
                Map<String, Long> staticInts = existing != null ? existing.staticInts() : new java.util.concurrent.ConcurrentHashMap<>();
                Map<String, Boolean> staticIsDouble = existing != null ? existing.staticIsDouble() : new java.util.concurrent.ConcurrentHashMap<>();
                Map<String, Object> staticObjects = existing != null ? existing.staticObjects() : new java.util.concurrent.ConcurrentHashMap<>();"""
    
    new_class_def_setup = class_def_setup + """
                Map<String, ChuckAST.AccessModifier> fieldAccess = existing != null ? existing.fieldAccess() : new HashMap<>();
                Map<String, ChuckAST.AccessModifier> methodAccess = existing != null ? existing.methodAccess() : new HashMap<>();"""
    content = content.replace(class_def_setup, new_class_def_setup)

    # 4. In class body loop, capture access modifiers
    content = content.replace("fieldDefs.add(new String[]{f.type(), f.name()});", "fieldDefs.add(new String[]{f.type(), f.name()});\n                                fieldAccess.put(f.name(), f.access());")
    content = content.replace("staticFieldDefs.add(new String[]{f.type(), f.name()});", "staticFieldDefs.add(new String[]{f.type(), f.name()});\n                                fieldAccess.put(f.name(), f.access());")
    content = content.replace("fieldDefs.add(new String[]{rDecl.type(), rDecl.name(), initStr});", "fieldDefs.add(new String[]{rDecl.type(), rDecl.name(), initStr});\n                                    fieldAccess.put(rDecl.name(), rDecl.access());")
    content = content.replace("fieldDefs.add(new String[]{rDecl.type(), rDecl.name()});", "fieldDefs.add(new String[]{rDecl.type(), rDecl.name()});\n                                fieldAccess.put(rDecl.name(), rDecl.access());")
    content = content.replace("staticFieldDefs.add(new String[]{rDecl.type(), rDecl.name()});", "staticFieldDefs.add(new String[]{rDecl.type(), rDecl.name()});\n                                fieldAccess.put(rDecl.name(), rDecl.access());")
    
    # Pre-register logic methodAccess
    content = content.replace("methodCodes.put(methodKey, stub);", "methodCodes.put(methodKey, stub);\n                    methodAccess.put(methodKey, m.access());")
    
    # 5. Update userClassRegistry.put to include new args
    content = content.replace(", null, s.isAbstract(), s.isInterface()));", ", null, s.isAbstract(), s.isInterface(), null, fieldAccess, methodAccess, s.access()));")
    content = content.replace(", finalStaticInitCode);", ", finalStaticInitCode, fieldAccess, methodAccess, s.access());")

    # 6. Fix DeclExp constructor calls
    content = re.sub(r'new ChuckAST\.DeclExp\(([^,]+), ([^,]+),\s*([^,]+), ([^,]+), ([^,]+), ([^,]+),\s*([^,]+), ([^,]+), ([^,]+), ([^)]+)\)', 
                     r'new ChuckAST.DeclExp(\1, \2, \3, \4, \5, \6, \7, \8, ChuckAST.AccessModifier.PUBLIC, \9, \10)', content)

    # 7. Add checkAccess for member field read
    field_get_orig = """                emitExpression(e.base(), code);
                code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));"""
    field_get_new = """                emitExpression(e.base(), code);
                String baseType = getExprType(e.base());
                if (baseType != null && userClassRegistry.containsKey(baseType)) {
                    checkAccess(baseType, e.member(), false, e.line(), e.column());
                }
                code.addInstruction(new FieldInstrs.GetFieldByName(e.member()));"""
    content = content.replace(field_get_orig, field_get_new)

    # 8. Add checkAccess for static field read
    static_get_orig = """                            code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, e.member()));
                            return;"""
    static_get_new = """                            checkAccess(potentialClassName, e.member(), false, e.line(), e.column());
                            code.addInstruction(new FieldInstrs.GetStatic(potentialClassName, e.member()));
                            return;"""
    content = content.replace(static_get_orig, static_get_new)

    # 9. Add checkAccess for method call
    method_call_orig = """                        code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc, fullKey));
                        return;"""
    method_call_new = """                        if (userClassRegistry.containsKey(baseType)) {
                            checkAccess(baseType, fullKey, true, e.line(), e.column());
                        }
                        code.addInstruction(new ObjectInstrs.CallMethod(dot.member(), argc, fullKey));
                        return;"""
    content = content.replace(method_call_orig, method_call_new)

    # 10. Add checkAccess for static method call
    static_method_orig = """                            if (target != null) {
                                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                                code.addInstruction(new CallFunc(target, argc));
                                return;
                            }"""
    static_method_new = """                            if (target != null) {
                                String finalKey = (target.getName().equals(dot.member())) ? fullKey : dot.member() + ":" + argc;
                                checkAccess(baseType, finalKey, true, e.line(), e.column());
                                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                                code.addInstruction(new CallFunc(target, argc));
                                return;
                            }"""
    content = content.replace(static_method_orig, static_method_new)

    # 11. Add checkAccess for static field write
    static_set_orig = """                        code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, e.member()));
                        return;"""
    static_set_new = """                        checkAccess(potentialClassName, e.member(), false, e.line(), e.column());
                        code.addInstruction(new FieldInstrs.SetStatic(potentialClassName, e.member()));
                        return;"""
    content = content.replace(static_set_orig, static_set_new)

    # 12. Add checkAccess for member field write
    field_set_orig = """                    emitExpression(e.base(), code);
                    code.addInstruction(new FieldInstrs.SetFieldByName(e.member()));"""
    field_set_new = """                    emitExpression(e.base(), code);
                    String baseType = getExprType(e.base());
                    if (baseType != null && userClassRegistry.containsKey(baseType)) {
                        checkAccess(baseType, e.member(), false, e.line(), e.column());
                    }
                    code.addInstruction(new FieldInstrs.SetFieldByName(e.member()));"""
    content = content.replace(field_set_orig, field_set_new)

    with open(filepath, 'w') as f:
        f.write(content)

process_file('src/main/java/org/chuck/compiler/ChuckEmitter.java')
