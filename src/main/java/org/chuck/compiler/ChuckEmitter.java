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

    // User-defined class registry
    record UserClassDescriptor(List<String[]> fields, Map<String, ChuckCode> methods) {}
    private final Map<String, UserClassDescriptor> userClassRegistry = new HashMap<>();

    private String currentClass = null;
    private java.util.Set<String> currentClassFields = java.util.Collections.emptySet();

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName) {
        // Pass 1: Collect all global function signatures
        for (ChuckAST.Stmt stmt : statements) {
            if (stmt instanceof ChuckAST.FuncDefStmt s) {
                String key = s.name() + ":" + s.argNames().size();
                functions.put(key, new ChuckCode(s.name()));
            }
        }

        ChuckCode code = new ChuckCode(programName);
        for (ChuckAST.Stmt stmt : statements) {
            emitStatement(stmt, code);
        }
        return code;
    }

    private void emitStatement(ChuckAST.Stmt stmt, ChuckCode code) {
        if (stmt instanceof ChuckAST.ExpStmt s) {
            emitExpression(s.exp(), code);
            code.addInstruction(new Pop());
        } else if (stmt instanceof ChuckAST.WhileStmt s) {
            int startPc = code.getNumInstructions();
            emitExpression(s.condition(), code); 
            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null); 
            emitStatement(s.body(), code);
            code.addInstruction(new Jump(startPc));
            code.replaceInstruction(jumpIdx, new JumpIfFalse(code.getNumInstructions()));
        } else if (stmt instanceof ChuckAST.ForStmt s) {
            emitStatement(s.init(), code);
            int startPc = code.getNumInstructions();
            if (s.condition() instanceof ChuckAST.ExpStmt es) {
                emitExpression(es.exp(), code);
            } else {
                emitStatement(s.condition(), code);
            }
            int jumpIdx = code.getNumInstructions();
            code.addInstruction(null);
            emitStatement(s.body(), code);
            emitExpression(s.update(), code);
            code.addInstruction(new Pop()); 
            code.addInstruction(new Jump(startPc));
            code.replaceInstruction(jumpIdx, new JumpIfFalse(code.getNumInstructions()));
        } else if (stmt instanceof ChuckAST.BlockStmt s) {
            for (ChuckAST.Stmt inner : s.statements()) {
                emitStatement(inner, code);
            }
        } else if (stmt instanceof ChuckAST.DeclStmt s) {
            if (localScopes.isEmpty()) {
                if (s.arraySize() != null) {
                    if (s.arraySize() instanceof ChuckAST.IntExp ie && ie.value() == -1) {
                        code.addInstruction(new PushInt(0));
                    } else {
                        emitExpression(s.arraySize(), code);
                    }
                    code.addInstruction(new CreateAndSetArray(s.type(), s.name(), userClassRegistry));
                } else {
                    code.addInstruction(new InstantiateAndSetGlobal(s.type(), s.name(), userClassRegistry));
                }
            } else {
                Map<String, Integer> scope = localScopes.peek();
                int offset = scope.size();
                scope.put(s.name(), offset);
                code.addInstruction(new PushInt(0));
                code.addInstruction(new StoreLocal(offset));
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
            for (ChuckAST.Stmt bodyStmt : s.body()) {
                if (bodyStmt instanceof ChuckAST.DeclStmt f) {
                    fieldDefs.add(new String[]{f.type(), f.name()});
                    fieldNames.add(f.name());
                } else if (bodyStmt instanceof ChuckAST.FuncDefStmt m) {
                    methods.add(m);
                }
            }
            Map<String, ChuckCode> methodCodes = new HashMap<>();
            String prevClass = currentClass;
            java.util.Set<String> prevFields = currentClassFields;
            currentClass = s.name();
            currentClassFields = fieldNames;
            for (ChuckAST.FuncDefStmt m : methods) {
                ChuckCode methodCode = new ChuckCode(s.name() + "." + m.name());
                emitStatement(m.body(), methodCode);
                methodCode.addInstruction(new ReturnMethod());
                methodCodes.put(m.name(), methodCode);
            }
            currentClass = prevClass;
            currentClassFields = prevFields;
            userClassRegistry.put(s.name(), new UserClassDescriptor(fieldDefs, methodCodes));
        } else if (stmt instanceof ChuckAST.RepeatStmt s) {
            emitStatement(s.body(), code);
        } else if (stmt instanceof ChuckAST.ForEachStmt s) {
            emitStatement(s.body(), code);
        }
    }

    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        if (exp instanceof ChuckAST.IntExp e) {
            code.addInstruction(new PushInt(e.value()));
        } else if (exp instanceof ChuckAST.FloatExp e) {
            code.addInstruction(new PushFloat(e.value()));
        } else if (exp instanceof ChuckAST.StringExp e) {
            code.addInstruction(new PushString(e.value()));
        } else if (exp instanceof ChuckAST.UnaryExp e) {
            emitExpression(e.exp(), code);
            switch (e.op()) {
                case MINUS -> code.addInstruction(new NegateAny());
                case S_OR  -> code.addInstruction(new LogicalNot()); 
                case PLUS  -> {} 
                default    -> {}
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
                emitExpression(e.lhs(), code);
                emitChuckTarget(e.rhs(), code);
            } else if (e.op() == ChuckAST.Operator.APPEND) {
                emitExpression(e.lhs(), code);
                emitExpression(e.rhs(), code);
                code.addInstruction(new CallMethod("append", 1));
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
                emitExpression(e.lhs(), code); 
                emitExpression(e.rhs(), code); 
                code.addInstruction(new CallMethod("write", 1));
            } else if (e.op() == ChuckAST.Operator.SWAP) {
                emitSwapTarget(e.lhs(), e.rhs(), code);
            } else if (e.op() == ChuckAST.Operator.ASSIGN) {
                emitExpression(e.rhs(), code);
                if (e.lhs() instanceof ChuckAST.IdExp id) {
                    Integer localOffset = getLocalOffset(id.name());
                    if (localOffset != null) code.addInstruction(new StoreLocal(localOffset));
                    else code.addInstruction(new SetGlobalObjectOrInt(id.name()));
                } else if (e.lhs() instanceof ChuckAST.DotExp dot) {
                    emitExpression(dot.base(), code);
                    code.addInstruction(new SetMemberIntByName(dot.member()));
                } else if (e.lhs() instanceof ChuckAST.ArrayAccessExp arr) {
                    emitExpression(arr.base(), code);
                    emitExpression(arr.index(), code);
                    code.addInstruction(new SetArrayInt());
                }
            } else if (e.op() == ChuckAST.Operator.NONE && e.rhs() instanceof ChuckAST.IdExp id) {
                emitExpression(e.lhs(), code);       
                code.addInstruction(new CreateDuration(id.name())); 
            } else {
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
                    case AND     -> code.addInstruction(new LogicalAnd());
                    case OR      -> code.addInstruction(new LogicalOr());
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
            emitExpression(e.base(), code);
            code.addInstruction(new GetFieldByName(e.member()));
        } else if (exp instanceof ChuckAST.ArrayLitExp e) {
            for (ChuckAST.Exp el : e.elements()) emitExpression(el, code);
            code.addInstruction(new NewArrayFromStack(e.elements().size()));
        } else if (exp instanceof ChuckAST.ArrayAccessExp e) {
            emitExpression(e.base(), code);
            emitExpression(e.index(), code);
            code.addInstruction(new GetArrayInt());
        } else if (exp instanceof ChuckAST.CallExp e) {
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
                        || dot.member().equals("rmstodb") || dot.member().equals("dbtorms")) {
                    emitExpression(e.args().get(0), code);
                    code.addInstruction(new MathFunc(dot.member()));
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
                    case "help" -> code.addInstruction(new MathHelp());
                    default     -> {
                        if (!e.args().isEmpty()) {
                            emitExpression(e.args().get(0), code);
                            code.addInstruction(new MathFunc(dot.member()));
                        } else {
                            code.addInstruction(new MathHelp());
                        }
                    }
                }
            } else if (e.base() instanceof ChuckAST.DotExp dot) {
                emitExpression(dot.base(), code);
                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                code.addInstruction(new CallMethod(dot.member(), e.args().size()));
            } else if (e.base() instanceof ChuckAST.IdExp id) {
                String key = id.name() + ":" + e.args().size();
                if (functions.containsKey(key)) {
                    for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                    code.addInstruction(new CallFunc(functions.get(key), e.args().size()));
                }
            }
        } else if (exp instanceof ChuckAST.SporkExp e) {
            String funcName = null;
            if (e.call().base() instanceof ChuckAST.IdExp id) {
                funcName = id.name();
            } else if (e.call().base() instanceof ChuckAST.DotExp dot) {
                funcName = dot.member();
            }
            
            if (funcName != null) {
                String key = funcName + ":" + e.call().args().size();
                if (functions.containsKey(key)) {
                    for (ChuckAST.Exp arg : e.call().args()) emitExpression(arg, code);
                    code.addInstruction(new Spork(functions.get(key)));
                } else {
                    emitExpression(e.call(), code);
                }
            } else {
                emitExpression(e.call(), code);
            }
        }
    }

    private Integer getLocalOffset(String name) {
        if (localScopes.isEmpty()) return null;
        return localScopes.peek().get(name);
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
            } else {
                emitExpression(e.base(), code);
                code.addInstruction(new SetMemberIntByName(e.member()));
            }
        } else if (target instanceof ChuckAST.ArrayAccessExp e) {
            emitExpression(e.base(), code);
            emitExpression(e.index(), code);
            code.addInstruction(new SetArrayInt());
        } else if (target instanceof ChuckAST.DeclExp e) {
            // e.g. "3 => int N" or "[0,1,2] @=> int arr[]"
            Integer localOffset = getLocalOffset(e.name());
            if (localOffset != null) {
                code.addInstruction(new StoreLocal(localOffset));
            } else {
                code.addInstruction(new SetGlobalObjectOrInt(e.name()));
            }
        }
    }

    private void emitSwapTarget(ChuckAST.Exp lhs, ChuckAST.Exp rhs, ChuckCode code) {
        if (lhs instanceof ChuckAST.IdExp l && rhs instanceof ChuckAST.IdExp r) {
            code.addInstruction(new ChuckSwap(l.name(), r.name(), false));
        } else if (lhs instanceof ChuckAST.DotExp l && rhs instanceof ChuckAST.DotExp r) {
            emitExpression(l.base(), code);
            code.addInstruction(new ChuckSwap(l.member(), r.member(), true));
        }
    }

    // Helper Instructions
    static class Jump implements ChuckInstr {
        int target;
        Jump(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.setPc(target - 1); }
    }

    static class JumpIfFalse implements ChuckInstr {
        int target;
        JumpIfFalse(int t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return; 
            if (s.reg.popLong() == 0) s.setPc(target - 1);
        }
    }

    static class PushDac implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { 
            s.reg.pushObject(vm.getDacChannel(0)); 
        }
    }

    static class ConnectToDac implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) { s.reg.push(0L); return; }
            Object raw = s.reg.popObject();
            if (!(raw instanceof ChuckUGen src)) { s.reg.push(0L); return; }
            src.chuckTo(vm.getDacChannel(0));
            src.chuckTo(vm.getDacChannel(1));
            s.reg.pushObject(src);
        }
    }

    static class PushBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.blackhole);
        }
    }

    static class PushAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.pushObject(vm.adc);
        }
    }

    static class ConnectToBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) { s.reg.push(0L); return; }
            Object raw = s.reg.popObject();
            if (!(raw instanceof ChuckUGen src)) { s.reg.push(0L); return; }
            src.chuckTo(vm.blackhole);
            s.reg.pushObject(src);
        }
    }

    static class ConnectToAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) { s.reg.push(0L); return; }
            Object raw = s.reg.popObject();
            if (!(raw instanceof ChuckUGen src)) { s.reg.push(0L); return; }
            src.chuckTo(vm.adc);
            s.reg.pushObject(src);
        }
    }

    static class LoadLocal implements ChuckInstr {
        int offset;
        LoadLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer();
            if (s.mem.isDoubleAt(fp + offset)) {
                s.reg.push(Double.longBitsToDouble(s.mem.getData(fp + offset)));
            } else {
                s.reg.push(s.mem.getData(fp + offset));
            }
        }
    }

    static class StoreLocal implements ChuckInstr {
        int offset;
        StoreLocal(int o) { offset = o; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int fp = s.getFramePointer();
            if (s.reg.isDouble(0)) {
                s.mem.setData(fp + offset, s.reg.popAsDouble());
            } else {
                s.mem.setData(fp + offset, s.reg.popLong());
            }
        }
    }

    static class Pop implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.popLong(); }
    }

    static class PushString implements ChuckInstr {
        String val; PushString(String v) { val = v; }
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(val); }
    }

    static class PushNow implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.push(vm.getCurrentTime()); }
    }

    static class ChuckPrint implements ChuckInstr {
        int count; ChuckPrint(int c) { count = c; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            // Pop in reverse so we print in declaration order
            Object[] vals = new Object[count];
            for (int i = count - 1; i >= 0; i--) {
                vals[i] = s.reg.pop();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (i > 0) sb.append(" ");
                sb.append(vals[i]);
            }
            vm.print(sb.toString());
        }
    }

    static class ReturnMethod implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long retPrim = 0; Object retObj = null; boolean retIsDouble = false;
            int savedSp = (int) s.mem.popLong();
            if (s.reg.getSp() > savedSp) {
                retIsDouble = s.reg.isDouble(0);
                retPrim = s.reg.peekLong(0);
                retObj = s.reg.peekObject(0);
            }
            s.reg.setSp(savedSp);
            s.setPc((int) s.mem.popLong());
            s.setCode((ChuckCode) s.mem.popObject());
            s.thisStack.pop();
            if (retObj != null) s.reg.pushObject(retObj);
            else if (retIsDouble) s.reg.push(Double.longBitsToDouble(retPrim));
            else s.reg.push(retPrim);
        }
    }

    static class ChuckUnchuck implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckUGen dest = s.reg.popObject();
            ChuckUGen src = s.reg.popObject();
            if (src != null && dest != null) src.unchuck(dest);
            s.reg.pushObject(src);
        }
    }

    static class SetArrayInt implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = (int) s.reg.popLong();
            ChuckArray arr = s.reg.popObject();
            if (s.reg.isDouble(0)) {
                double v = s.reg.popAsDouble();
                if (arr != null) arr.setData(idx, v);
                s.reg.push(v);
            } else {
                long v = s.reg.popLong();
                if (arr != null) arr.setInt(idx, v);
                s.reg.push(v);
            }
        }
    }

    static class GetFieldByName implements ChuckInstr {
        String name; GetFieldByName(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckObject obj = s.reg.popObject();
            if (obj == null) { s.reg.push(0L); return; }
            if (obj instanceof UserObject uo) {
                s.reg.push(uo.getPrimitiveField(name));
            } else {
                Integer idx = SetMemberIntByName.MEMBER_OFFSETS.get(name);
                if (idx != null) s.reg.push(obj.getData(idx));
                else s.reg.push(0L);
            }
        }
    }

    static class GetUserField implements ChuckInstr {
        String name; GetUserField(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek();
            if (uo != null) s.reg.push(uo.getPrimitiveField(name));
            else s.reg.push(0L);
        }
    }

    static class SetUserField implements ChuckInstr {
        String name; SetUserField(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            UserObject uo = s.thisStack.peek();
            long val = s.reg.popLong();
            if (uo != null) uo.setPrimitiveField(name, val);
            s.reg.push(val);
        }
    }

    static class GetLastOut implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckUGen ugen = s.reg.popObject();
            s.reg.push(ugen != null ? (double) ugen.getLastOut() : 0.0);
        }
    }

    static class NoteOn implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double vel = s.reg.popAsDouble();
            ChuckUGen ugen = s.reg.popObject();
            if (ugen != null) {
                try { ugen.getClass().getMethod("noteOn", float.class).invoke(ugen, (float)vel); }
                catch (Exception ignored) {}
            }
            s.reg.push(vel);
        }
    }

    static class ChuckSwap implements ChuckInstr {
        String name1, name2; boolean isMember;
        ChuckSwap(String n1, String n2, boolean m) { name1 = n1; name2 = n2; isMember = m; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (!isMember) {
                long v1 = vm.getGlobalInt(name1);
                long v2 = vm.getGlobalInt(name2);
                vm.setGlobalInt(name1, v2);
                vm.setGlobalInt(name2, v1);
            }
            s.reg.push(0L); // push dummy result so ExpStmt Pop doesn't underflow
        }
    }

    static class PushMe implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(s); }
    }

    static class PushMachine implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) { s.reg.pushObject(new MachineObject()); }
    }

    static class MachineObject extends ChuckObject {
        MachineObject() { super(new ChuckType("Machine", ChuckType.OBJECT, 0, 0)); }
        public int add(String path, ChuckVM vm) { return vm.add(path); }
        public void remove(int id, ChuckVM vm) { vm.removeShred(id); }
        public void clear(ChuckVM vm) { vm.clear(); }
    }

    static class InstantiateAndSetGlobal implements ChuckInstr {
        String type, name;
        Map<String, UserClassDescriptor> userClassRegistry;
        InstantiateAndSetGlobal(String t, String n, Map<String, UserClassDescriptor> r) {
            type = t; name = n; userClassRegistry = r;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckObject obj = instantiateType(type, vm.getSampleRate(), vm, userClassRegistry);
            if (obj != null) {
                vm.setGlobalObject(name, obj);
                if (obj instanceof ChuckUGen ugen) s.registerUGen(ugen);
            }
        }
    }

    static class CreateAndSetArray implements ChuckInstr {
        String elementType, name;
        Map<String, UserClassDescriptor> userClassRegistry;
        CreateAndSetArray(String t, String n, Map<String, UserClassDescriptor> r) {
            elementType = t; name = n; userClassRegistry = r;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int size = (int) s.reg.popLong();
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
            float sr = vm.getSampleRate();
            for (int i = 0; i < size; i++) {
                ChuckObject elem = instantiateType(elementType, sr, vm, userClassRegistry);
                if (elem != null) {
                    arr.setObject(i, elem);
                    if (elem instanceof ChuckUGen ugen) s.registerUGen(ugen);
                }
            }
            vm.setGlobalObject(name, arr);
        }
    }

    static class GetGlobalObjectOrInt implements ChuckInstr {
        String name;
        GetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckObject obj = vm.getGlobalObject(name);
            if (obj != null) {
                s.reg.pushObject(obj);
            } else if (vm.isGlobalDouble(name)) {
                s.reg.push(Double.longBitsToDouble(vm.getGlobalInt(name)));
            } else {
                s.reg.push(vm.getGlobalInt(name));
            }
        }
    }

    static class SetGlobalObjectOrInt implements ChuckInstr {
        String name;
        SetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.getSp() == 0) return; 
            if (s.reg.isObject(0)) {
                ChuckObject obj = (ChuckObject) s.reg.popObject();
                ChuckObject existing = vm.getGlobalObject(name);
                if (obj instanceof org.chuck.audio.ChuckUGen srcUgen) {
                    if (name.equals("dac")) {
                        srcUgen.chuckTo(vm.getDacChannel(0));
                        srcUgen.chuckTo(vm.getDacChannel(1));
                        s.reg.pushObject(srcUgen);
                        return;
                    } else if (name.equals("blackhole")) {
                        vm.blackhole.addSource(srcUgen);
                        s.reg.pushObject(srcUgen);
                        return;
                    } else if (existing instanceof org.chuck.audio.ChuckUGen destUgen) {
                        srcUgen.chuckTo(destUgen);
                        s.reg.pushObject(srcUgen);
                        return;
                    } else if (existing instanceof ChuckArray arr) {
                        for (int i = 0; i < arr.size(); i++) {
                            Object elem = arr.getObject(i);
                            if (elem instanceof org.chuck.audio.ChuckUGen dest) srcUgen.chuckTo(dest);
                        }
                        s.reg.pushObject(srcUgen);
                        return;
                    }
                }
                vm.setGlobalObject(name, obj);
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
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
                s.reg.push(dl + dr);
            } else {
                long r = s.reg.popLong(); long l = s.reg.popLong();
                s.reg.push(l + r);
            }
        }
    }

    static class MinusAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
                s.reg.push(dl - dr);
            } else {
                long r = s.reg.popLong(); long l = s.reg.popLong();
                s.reg.push(l - r);
            }
        }
    }

    static class TimesAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
                s.reg.push(dl * dr);
            } else {
                long r = s.reg.popLong(); long l = s.reg.popLong();
                s.reg.push(l * r);
            }
        }
    }

    static class DivideAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
            s.reg.push(dl / dr);
        }
    }

    static class ModuloAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0) || s.reg.isDouble(1)) {
                double r = s.reg.popAsDouble(); double l = s.reg.popAsDouble();
                s.reg.push(l % r);
            } else {
                long r = s.reg.popLong(); long l = s.reg.popLong();
                if (r == 0) s.reg.push(0L);
                else s.reg.push(l % r);
            }
        }
    }

    static class NegateAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.isDouble(0)) {
                s.reg.push(-s.reg.popAsDouble());
            } else {
                s.reg.push(-s.reg.popLong());
            }
        }
    }

    static class LogicalNot implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(s.reg.popLong() == 0 ? 1L : 0L);
        }
    }

    static class LessThanAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
            s.reg.push(dl < dr ? 1 : 0);
        }
    }

    static class GreaterThanAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
            s.reg.push(dl > dr ? 1 : 0);
        }
    }

    static class EqualsAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
            s.reg.push(dl == dr ? 1 : 0);
        }
    }

    static class NotEqualsAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double r = s.reg.popAsDouble(); double l = s.reg.popAsDouble();
            s.reg.push(l != r ? 1L : 0L);
        }
    }

    static class LogicalAnd implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popLong(); long l = s.reg.popLong();
            s.reg.push((l != 0 && r != 0) ? 1L : 0L);
        }
    }

    static class LogicalOr implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popLong(); long l = s.reg.popLong();
            s.reg.push((l != 0 || r != 0) ? 1L : 0L);
        }
    }

    static class CreateDuration implements ChuckInstr {
        String unit;
        CreateDuration(String u) { unit = u; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double val = s.reg.popAsDouble();
            long samples = 0;
            if (unit.equals("ms")) samples = (long)(val * vm.getSampleRate() / 1000.0);
            else if (unit.equals("second")) samples = (long)(val * vm.getSampleRate());
            else if (unit.equals("samp")) samples = (long) val;
            else if (unit.equals("minute")) samples = (long)(val * vm.getSampleRate() * 60.0);
            else if (unit.equals("hour")) samples = (long)(val * vm.getSampleRate() * 3600.0);
            s.reg.push(samples);
        }
    }

    static class StdMtof implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred shred) {
            double midi = shred.reg.popAsDouble();
            shred.reg.push(Std.mtof(midi));
        }
    }

    static class StdFtom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double freq = s.reg.popAsDouble();
            s.reg.push(Std.ftom(freq));
        }
    }

    static class MathFunc implements ChuckInstr {
        String fn;
        MathFunc(String fn) { this.fn = fn; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (fn.equals("pow")) {
                double b = s.reg.popAsDouble(); double a = s.reg.popAsDouble();
                s.reg.push(Math.pow(a, b));
            } else {
                double v = s.reg.popAsDouble();
                double result = switch (fn) {
                    case "sin"   -> Math.sin(v);
                    case "cos"   -> Math.cos(v);
                    case "tan"   -> Math.tan(v);
                    case "sqrt"  -> Math.sqrt(v);
                    case "abs"   -> Math.abs(v);
                    case "floor" -> Math.floor(v);
                    case "ceil"  -> Math.ceil(v);
                    case "log"       -> Math.log(v);
                    case "log2"      -> Math.log(v) / Math.log(2);
                    case "exp"       -> Math.exp(v);
                    case "powtodb"   -> (v <= 0) ? -999.0 : 20.0 * Math.log10(Math.sqrt(v));
                    case "dbtopow"   -> Math.pow(10.0, v / 10.0);
                    case "rmstodb"   -> (v <= 0) ? -999.0 : 20.0 * Math.log10(v);
                    case "dbtorms"   -> Math.pow(10.0, v / 20.0);
                    default          -> v;
                };
                s.reg.push(result);
            }
        }
    }

    static class Std2RandF implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double max = s.reg.popAsDouble();
            double min = s.reg.popAsDouble();
            s.reg.push(min + Math.random() * (max - min));
        }
    }

    static class Std2RandI implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long max = s.reg.popLong();
            long min = s.reg.popLong();
            s.reg.push(min + (long)(Math.random() * (max - min + 1)));
        }
    }

    static class MathRandom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(Math.random());
        }
    }

    static class MathHelp implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            System.out.println("[Chuck] Math API: sin cos tan sqrt pow abs floor ceil log log2 exp random randf help");
        }
    }

    static class Spork implements ChuckInstr {
        ChuckCode target;
        Spork(ChuckCode t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckShred newShred = new ChuckShred(target);
            s.reg.push(vm.spork(newShred));
        }
    }

    static class NewArrayFromStack implements ChuckInstr {
        int size;
        NewArrayFromStack(int s) { size = s; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
            for (int i = size - 1; i >= 0; i--) {
                if (s.reg.getSp() == 0) break;
                if (s.reg.isObject(0)) arr.setObject(i, s.reg.popObject());
                else if (s.reg.isDouble(0)) arr.setFloat(i, s.reg.popAsDouble());
                else arr.setInt(i, s.reg.popLong());
            }
            s.reg.pushObject(arr);
        }
    }

    static class GetArrayInt implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int idx = (int) s.reg.popLong();
            ChuckArray arr = s.reg.popObject();
            if (arr != null) {
                if (arr.isDoubleAt(idx)) s.reg.push(arr.getFloat(idx));
                else s.reg.push(arr.getInt(idx));
            } else s.reg.push(0L);
        }
    }

    /** Shared factory: create a new instance of a named ChucK type. Returns null for primitives. */
    static ChuckObject instantiateType(String type, float sr, ChuckVM vm,
                                       Map<String, UserClassDescriptor> userClassRegistry) {
        if (type == null) return null;
        UserClassDescriptor desc = userClassRegistry.get(type);
        if (desc != null) return new UserObject(type, desc.fields(), desc.methods());
        return switch (type) {
            case "SinOsc"   -> new SinOsc(sr);
            case "SawOsc"   -> new SawOsc(sr);
            case "TriOsc"   -> new TriOsc(sr);
            case "SqrOsc"   -> new SqrOsc(sr);
            case "PulseOsc" -> new PulseOsc(sr);
            case "Phasor"   -> new Phasor(sr);
            case "Noise"    -> new Noise();
            case "Impulse"  -> new Impulse();
            case "Mandolin" -> new Mandolin(100.0f, sr);
            case "Clarinet" -> new Clarinet(100.0f, sr);
            case "Plucked"  -> new Plucked(100.0f, sr);
            case "Rhodey"   -> new Rhodey(sr);
            case "Bowed"    -> new Bowed(sr);
            case "StifKarp" -> new StifKarp(sr);
            case "Moog"     -> new Moog(sr);
            case "Flute"    -> new Flute(sr);
            case "Sitar"    -> new Sitar(sr);
            case "Brass"    -> new Brass(sr);
            case "Saxofony" -> new Saxofony(sr);
            case "Shakers"  -> new Shakers(sr);
            case "ADSR", "Adsr" -> new Adsr(sr);
            case "Gain"     -> new Gain();
            case "Pan2"     -> new Pan2();
            case "Echo"     -> new Echo((int)(sr * 2));
            case "Delay"    -> new Delay((int)(sr * 2));
            case "DelayL"   -> new DelayL((int)(sr * 2));
            case "JCRev"    -> new JCRev(sr);
            case "Chorus"   -> new Chorus(sr);
            case "ResonZ"   -> new ResonZ(sr);
            case "Lpf"      -> new Lpf(sr);
            case "OnePole"  -> new OnePole();
            case "OneZero"  -> new OneZero();
            case "LiSa"     -> new LiSa(sr);
            case "Step"     -> new Step();
            case "FFT"      -> new FFT(1024);
            case "IFFT"     -> new IFFT(1024);
            case "Gen5"     -> new Gen5(sr);
            case "Gen7"     -> new Gen7(sr);
            case "Gen10"    -> new Gen10(sr);
            case "RMS"      -> new RMS(1024);
            case "Centroid" -> new Centroid();
            case "MidiIn"   -> new org.chuck.midi.MidiIn(vm);
            case "OscIn"    -> new org.chuck.network.OscIn(vm);
            case "OscOut"   -> new org.chuck.network.OscOut();
            case "OscMsg"   -> new org.chuck.network.OscMsg();
            case "Hid"      -> new org.chuck.hid.Hid();
            case "HidMsg"   -> new org.chuck.hid.HidMsg();
            case "Event"    -> new org.chuck.core.ChuckEvent();
            case "SndBuf"   -> new SndBuf();
            case "Envelope" -> new Envelope(sr);
            case "AllPass"  -> new AllPass((int)(sr * 0.1f));
            case "BiQuad"   -> new BiQuad(sr);
            case "GainDB"   -> new GainDB();
            case "HevyMetl", "Wurley", "TubeBell", "FrencHrn" -> new Rhodey(sr);
            case "HnkyTonk", "JacoBass"   -> new StifKarp(sr);
            case "ModalBar", "BandedWG"   -> new Mandolin(100.0f, sr);
            case "BlowBotl", "BlowHole"   -> new Flute(sr);
            case "VoicForm"               -> new Clarinet(100.0f, sr);
            case "SubNoise", "Modulate"   -> new Noise();
            case "NRev"                   -> new JCRev(sr);
            case "FoldbackSaturator"      -> new Gain();
            case "ExpDelay", "PitchShift" -> new Echo((int)(sr * 2));
            case "ExpEnv"                 -> new Envelope(sr);
            case "WinFuncEnv"             -> new Envelope(sr);
            case "PowerADSR"              -> new Adsr(sr);
            case "Hpf"                    -> new Lpf(sr);
            case "BPF", "Notch"           -> new BiQuad(sr);
            default         -> null;
        };
    }

    static class CallMethod implements ChuckInstr {
        String methodName; int argCount;
        CallMethod(String m, int a) { methodName = m; argCount = a; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            Object[] args = new Object[argCount];
            for (int i = argCount - 1; i >= 0; i--) {
                args[i] = s.reg.isDouble(0) ? s.reg.popAsDouble() : s.reg.popLong();
            }
            ChuckObject obj = (ChuckObject) s.reg.popObject();
            if (obj == null) { s.reg.push(0L); return; }
            if (obj instanceof UserObject uo && uo.methods.containsKey(methodName)) {
                s.thisStack.push(uo); s.mem.pushObject(s.getCode()); s.mem.push(s.getPc());
                s.setCode(uo.methods.get(methodName)); s.setPc(-1); return;
            }
            for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == argCount) {
                    try {
                        Object[] coerced = new Object[argCount];
                        Class<?>[] pts = m.getParameterTypes();
                        for (int i = 0; i < argCount; i++) {
                            Object val = args[i];
                            if (val instanceof Double dv) {
                                coerced[i] = pts[i] == float.class ? dv.floatValue() : pts[i] == int.class ? dv.intValue() : pts[i] == long.class ? dv.longValue() : dv;
                            } else if (val instanceof Long lv) {
                                coerced[i] = pts[i] == float.class ? lv.floatValue() : pts[i] == double.class ? lv.doubleValue() : pts[i] == int.class ? lv.intValue() : lv;
                            }
                        }
                        Object result = m.invoke(obj, coerced);
                        if (result != null && m.getReturnType() != void.class) {
                            Class<?> rt = m.getReturnType();
                            if (rt == int.class || rt == long.class) s.reg.push(((Number) result).longValue());
                            else if (rt == boolean.class) s.reg.push((Boolean) result ? 1L : 0L);
                            else if (rt == float.class || rt == double.class) s.reg.push(((Number) result).doubleValue());
                            else s.reg.pushObject(result);
                        } else s.reg.pushObject(obj);
                        return;
                    } catch (Exception ignored) {}
                }
            }
            s.reg.pushObject(obj);
        }
    }
}
