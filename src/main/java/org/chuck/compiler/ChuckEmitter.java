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

    // User-defined class registry — populated at compile time, referenced by instructions at runtime
    record UserClassDescriptor(List<String[]> fields, Map<String, ChuckCode> methods) {}
    private final Map<String, UserClassDescriptor> userClassRegistry = new HashMap<>();

    // Context when compiling inside a class method (null at top level)
    private String currentClass = null;
    private java.util.Set<String> currentClassFields = java.util.Collections.emptySet();

    public ChuckCode emit(List<ChuckAST.Stmt> statements, String programName) {
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
            code.addInstruction(null); // Placeholder
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
            code.addInstruction(null); // Placeholder
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
            if (s.arraySize() != null) {
                if (s.arraySize() instanceof ChuckAST.IntExp ie && ie.value() == -1) {
                    code.addInstruction(new PushInt(0));
                } else {
                    emitExpression(s.arraySize(), code);
                }
                code.addInstruction(new CreateAndSetArray(s.name()));
            } else {
                code.addInstruction(new InstantiateAndSetGlobal(s.type(), s.name(), userClassRegistry));
            }
        } else if (stmt instanceof ChuckAST.FuncDefStmt s) {
            ChuckCode funcCode = new ChuckCode(s.name());
            emitStatement(s.body(), funcCode);
            funcCode.addInstruction(new ReturnFunc());
            functions.put(s.name(), funcCode);
        } else if (stmt instanceof ChuckAST.ReturnStmt s) {
            if (s.exp() != null) emitExpression(s.exp(), code);
            code.addInstruction(currentClass != null ? new ReturnMethod() : new ReturnFunc());
        } else if (stmt instanceof ChuckAST.ClassDefStmt s) {
            // Collect field descriptors
            List<String[]> fieldDefs = new ArrayList<>();
            java.util.Set<String> fieldNames = new java.util.LinkedHashSet<>();
            for (ChuckAST.DeclStmt f : s.fields()) {
                fieldDefs.add(new String[]{f.type(), f.name()});
                fieldNames.add(f.name());
            }
            // Compile each method body with class context active
            Map<String, ChuckCode> methodCodes = new HashMap<>();
            String prevClass = currentClass;
            java.util.Set<String> prevFields = currentClassFields;
            currentClass = s.name();
            currentClassFields = fieldNames;
            for (ChuckAST.FuncDefStmt m : s.methods()) {
                ChuckCode methodCode = new ChuckCode(s.name() + "." + m.name());
                emitStatement(m.body(), methodCode);
                methodCode.addInstruction(new ReturnMethod());
                methodCodes.put(m.name(), methodCode);
            }
            currentClass = prevClass;
            currentClassFields = prevFields;
            userClassRegistry.put(s.name(), new UserClassDescriptor(fieldDefs, methodCodes));
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
                case S_OR  -> code.addInstruction(new LogicalNot()); // ! operator
                case PLUS  -> code.addInstruction(new IncrementGlobal(e.exp(), +1, functions, this));
                default    -> {} // no-op for unknown unary
            }
        } else if (exp instanceof ChuckAST.BinaryExp e) {
            if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                emitExpression(e.lhs(), code);
                emitChuckTarget(e.rhs(), code);
            } else if (e.op() == ChuckAST.Operator.ASSIGN) {
                // x = value  (plain Java-style assignment; sets the global variable)
                emitExpression(e.rhs(), code);
                if (e.lhs() instanceof ChuckAST.IdExp id) {
                    code.addInstruction(new SetGlobalObjectOrInt(id.name()));
                } else if (e.lhs() instanceof ChuckAST.DotExp dot) {
                    emitExpression(dot.base(), code);
                    code.addInstruction(new SetMemberIntByName(dot.member()));
                } else if (e.lhs() instanceof ChuckAST.ArrayAccessExp arr) {
                    emitExpression(arr.base(), code);
                    emitExpression(arr.index(), code);
                    code.addInstruction(new SetArrayInt());
                }
            } else if (e.op() == ChuckAST.Operator.NONE && e.rhs() instanceof ChuckAST.IdExp id) {
                // Duration literal: 1::second, 500::ms, 2::samp, etc.
                // The unit name is the instruction parameter — do NOT emit rhs as a value.
                emitExpression(e.lhs(), code);       // push multiplier (e.g. 1, 500)
                code.addInstruction(new CreateDuration(id.name())); // pop multiplier, push samples
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
            if (e.name().equals("now")) code.addInstruction(new PushNow());
            else if (e.name().equals("dac")) code.addInstruction(new PushDac());
            else if (e.name().equals("blackhole")) code.addInstruction(new PushBlackhole());
            else if (e.name().equals("adc")) code.addInstruction(new PushAdc());
            else if (currentClassFields.contains(e.name())) {
                code.addInstruction(new GetUserField(e.name()));
            } else {
                code.addInstruction(new GetGlobalObjectOrInt(e.name()));
            }
        } else if (exp instanceof ChuckAST.DotExp e) {
            // Plain field read: f.x  (not a method call — those are wrapped in CallExp)
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
                }
            } else if (e.base() instanceof ChuckAST.DotExp dot && dot.member().equals("last")) {
                emitExpression(dot.base(), code);
                code.addInstruction(new GetLastOut());
            } else if (e.base() instanceof ChuckAST.DotExp dot
                    && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                switch (dot.member()) {
                    case "random", "randf" -> code.addInstruction(new MathRandom());
                    case "sin"  -> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("sin")); }
                    case "cos"  -> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("cos")); }
                    case "pow"  -> { emitExpression(e.args().get(0), code); emitExpression(e.args().get(1), code); code.addInstruction(new MathFunc("pow")); }
                    case "sqrt" -> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("sqrt")); }
                    case "abs"  -> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("abs")); }
                    case "floor"-> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("floor")); }
                    case "ceil" -> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc("ceil")); }
                    default     -> { emitExpression(e.args().get(0), code); code.addInstruction(new MathFunc(dot.member())); }
                }
            } else if (e.base() instanceof ChuckAST.DotExp dot) {
                // Generic method call: obj.method(args)
                emitExpression(dot.base(), code);
                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                code.addInstruction(new CallMethod(dot.member(), e.args().size()));
            } else if (e.base() instanceof ChuckAST.IdExp id && functions.containsKey(id.name())) {
                for (ChuckAST.Exp arg : e.args()) emitExpression(arg, code);
                code.addInstruction(new CallFunc(functions.get(id.name())));
            }
        } else if (exp instanceof ChuckAST.SporkExp e) {
            String funcName = ((ChuckAST.IdExp) e.call().base()).name();
            if (functions.containsKey(funcName)) {
                for (ChuckAST.Exp arg : e.call().args()) emitExpression(arg, code);
                code.addInstruction(new Spork(functions.get(funcName)));
            }
        }
    }

    private void emitChuckTarget(ChuckAST.Exp target, ChuckCode code) {
        if (target instanceof ChuckAST.IdExp e) {
            if (e.name().equals("now")) code.addInstruction(new AdvanceTime());
            else if (e.name().equals("dac")) code.addInstruction(new ConnectToDac());
            else if (e.name().equals("blackhole")) code.addInstruction(new ConnectToBlackhole());
            else if (e.name().equals("adc")) code.addInstruction(new ConnectToAdc());
            else if (currentClassFields.contains(e.name())) code.addInstruction(new SetUserField(e.name()));
            else code.addInstruction(new SetGlobalObjectOrInt(e.name()));
        } else if (target instanceof ChuckAST.DotExp e) {
            if (e.base() instanceof ChuckAST.IdExp baseId && baseId.name().equals("Std") && e.member().equals("mtof")) {
                code.addInstruction(new StdMtof());
            } else if (e.member().equals("noteOn")) {
                emitExpression(e.base(), code);
                code.addInstruction(new NoteOn());
            } else {
                emitExpression(e.base(), code);
                code.addInstruction(new SetMemberIntByName(e.member()));
            }
        } else if (target instanceof ChuckAST.ArrayAccessExp e) {
            emitExpression(e.base(), code);
            emitExpression(e.index(), code);
            code.addInstruction(new SetArrayInt());
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
            ChuckUGen src = (ChuckUGen) s.reg.popObject();
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
            ChuckUGen src = (ChuckUGen) s.reg.popObject();
            src.chuckTo(vm.blackhole);
            s.reg.pushObject(src);
        }
    }

    /** Connect a UGen to the ADC — e.g. for loopback or signal injection. */
    static class ConnectToAdc implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckUGen src = (ChuckUGen) s.reg.popObject();
            src.chuckTo(vm.adc);
            s.reg.pushObject(src);
        }
    }

    static class AddAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.peekLong(0); long l = s.reg.peekLong(1);
            if (Math.abs(r) < 2000000 && Math.abs(l) < 2000000) {
                s.reg.popLong(); s.reg.popLong();
                s.reg.push(l + r);
            } else {
                double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
                s.reg.push(Double.doubleToRawLongBits(dl + dr));
            }
        }
    }

    static class MinusAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.peekLong(0); long l = s.reg.peekLong(1);
            if (Math.abs(r) < 2000000 && Math.abs(l) < 2000000) {
                s.reg.popLong(); s.reg.popLong();
                s.reg.push(l - r);
            } else {
                double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
                s.reg.push(Double.doubleToRawLongBits(dl - dr));
            }
        }
    }

    static class TimesAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
            s.reg.push(Double.doubleToRawLongBits(dl * dr));
        }
    }

    static class DivideAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double dr = s.reg.popAsDouble(); double dl = s.reg.popAsDouble();
            s.reg.push(Double.doubleToRawLongBits(dl / dr));
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

    static class CreateDuration implements ChuckInstr {
        String unit;
        CreateDuration(String u) { unit = u; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double val = s.reg.popAsDouble();
            long samples = 0;
            if (unit.equals("ms")) samples = (long)(val * vm.getSampleRate() / 1000.0);
            else if (unit.equals("second")) samples = (long)(val * vm.getSampleRate());
            else if (unit.equals("samp")) samples = (long) val;
            s.reg.push(samples);
        }
    }

    static class MathRandom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(Double.doubleToRawLongBits(Math.random()));
        }
    }

    static class NewArrayFromStack implements ChuckInstr {
        int size;
        NewArrayFromStack(int s) { size = s; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
            for (int i = size - 1; i >= 0; i--) {
                arr.setInt(i, s.reg.popLong());
            }
            s.reg.pushObject(arr);
        }
    }

    static class CreateAndSetArray implements ChuckInstr {
        String name;
        CreateAndSetArray(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            int size = (int) s.reg.popLong();
            ChuckArray arr = new ChuckArray(ChuckType.ARRAY, size);
            vm.setGlobalObject(name, arr);
        }
    }

    static class InstantiateAndSetGlobal implements ChuckInstr {
        String type, name;
        Map<String, UserClassDescriptor> userClassRegistry;
        InstantiateAndSetGlobal(String t, String n, Map<String, UserClassDescriptor> r) {
            type = t; name = n; userClassRegistry = r;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckObject obj = null;
            float sr = vm.getSampleRate();
            // Check user-defined class registry first
            UserClassDescriptor desc = userClassRegistry.get(type);
            if (desc != null) {
                obj = new UserObject(type, desc.fields(), desc.methods());
                vm.setGlobalObject(name, obj);
                return;
            }
            switch (type) {
                case "SinOsc"   -> obj = new SinOsc(sr);
                case "SawOsc"   -> obj = new SawOsc(sr);
                case "TriOsc"   -> obj = new TriOsc(sr);
                case "SqrOsc"   -> obj = new SqrOsc(sr);
                case "PulseOsc" -> obj = new PulseOsc(sr);
                case "Phasor"   -> obj = new Phasor(sr);
                case "Noise"    -> obj = new Noise();
                case "Impulse"  -> obj = new Impulse();
                case "Mandolin" -> obj = new Mandolin(100.0f, sr);
                case "Clarinet" -> obj = new Clarinet(100.0f, sr);
                case "Plucked"  -> obj = new Plucked(100.0f, sr);
                case "Rhodey"   -> obj = new Rhodey(sr);
                case "ADSR", "Adsr" -> obj = new Adsr(sr);
                case "Gain"     -> obj = new Gain();
                case "Pan2"     -> obj = new Pan2();
                case "Echo"     -> obj = new Echo((int)(sr * 2));  // 2-second max
                case "Delay"    -> obj = new Delay((int)(sr * 2));
                case "DelayL"   -> obj = new DelayL((int)(sr * 2));
                case "JCRev"    -> obj = new JCRev(sr);
                case "Chorus"   -> obj = new Chorus(sr);
                case "ResonZ"   -> obj = new ResonZ(sr);
                case "Lpf"      -> obj = new Lpf(sr);
                case "OnePole"  -> obj = new OnePole();
                case "OneZero"  -> obj = new OneZero();
                case "Step"     -> obj = new Step();
                case "FFT"      -> obj = new FFT(1024);
                case "MidiIn"   -> obj = new org.chuck.midi.MidiIn(vm);
            }
            if (obj != null) vm.setGlobalObject(name, obj);
        }
    }

    static class GetGlobalObjectOrInt implements ChuckInstr {
        String name;
        GetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckObject obj = vm.getGlobalObject(name);
            if (obj != null) s.reg.pushObject(obj);
            else s.reg.push(vm.getGlobalInt(name));
        }
    }

    static class SetGlobalObjectOrInt implements ChuckInstr {
        String name;
        SetGlobalObjectOrInt(String n) { name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (s.reg.peekObject(0) != null) {
                ChuckObject obj = (ChuckObject) s.reg.popObject();
                vm.setGlobalObject(name, obj);
                s.reg.pushObject(obj); 
            } else {
                long val = s.reg.popLong();
                vm.setGlobalInt(name, val);
                s.reg.push(val); 
            }
        }
    }

    static class Spork implements ChuckInstr {
        ChuckCode target;
        Spork(ChuckCode t) { target = t; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckShred newShred = new ChuckShred(target);
            vm.spork(newShred);
            s.reg.push(0);
        }
    }

    static class NegateAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long raw = s.reg.popLong();
            if (Math.abs(raw) < 2_000_000L) s.reg.push(-raw);
            else s.reg.push(Double.doubleToRawLongBits(-Double.longBitsToDouble(raw)));
        }
    }

    static class LogicalNot implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            s.reg.push(s.reg.popLong() == 0 ? 1L : 0L);
        }
    }

    static class ModuloAny implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long r = s.reg.popLong(); long l = s.reg.popLong();
            if (Math.abs(r) < 2_000_000L && Math.abs(l) < 2_000_000L) s.reg.push(l % r);
            else s.reg.push(Double.doubleToRawLongBits(s.reg.popAsDouble() % s.reg.popAsDouble()));
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

    static class MathFunc implements ChuckInstr {
        String fn;
        MathFunc(String fn) { this.fn = fn; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            if (fn.equals("pow")) {
                double b = s.reg.popAsDouble(); double a = s.reg.popAsDouble();
                s.reg.push(Double.doubleToRawLongBits(Math.pow(a, b)));
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
                    case "log"   -> Math.log(v);
                    case "log2"  -> Math.log(v) / Math.log(2);
                    case "exp"   -> Math.exp(v);
                    default      -> v;
                };
                s.reg.push(Double.doubleToRawLongBits(result));
            }
        }
    }

    static class StdFtom implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            double freq = s.reg.popAsDouble();
            double midi = 69.0 + 12.0 * Math.log(freq / 440.0) / Math.log(2.0);
            s.reg.push(Double.doubleToRawLongBits(midi));
        }
    }

    /** Generic method call on a ChucK object using reflection. */
    static class CallMethod implements ChuckInstr {
        String methodName;
        int argCount;
        CallMethod(String m, int a) { methodName = m; argCount = a; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            // Stack: [obj, arg0, arg1, ...] with args pushed after obj
            // We need to pop args first (LIFO), then obj
            Object[] args = new Object[argCount];
            for (int i = argCount - 1; i >= 0; i--) {
                long raw = s.reg.popLong();
                args[i] = Math.abs(raw) < 2_000_000L ? (double) raw : Double.longBitsToDouble(raw);
            }
            ChuckObject obj = (ChuckObject) s.reg.popObject();
            if (obj == null) { s.reg.push(0L); return; }
            // User-defined method dispatch
            if (obj instanceof UserObject uo && uo.methods.containsKey(methodName)) {
                s.thisStack.push(uo);
                s.mem.pushObject(s.getCode());
                s.mem.push(s.getPc());
                s.setCode(uo.methods.get(methodName));
                s.setPc(-1); // main loop will increment to 0
                return;
            }
            // Try to find a matching method
            for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (m.getParameterCount() != argCount) continue;
                try {
                    Object[] coerced = new Object[argCount];
                    Class<?>[] pts = m.getParameterTypes();
                    for (int i = 0; i < argCount; i++) {
                        double dv = (Double) args[i];
                        coerced[i] = pts[i] == float.class ? (float) dv
                                   : pts[i] == int.class   ? (int) dv
                                   : pts[i] == long.class  ? (long) dv
                                   : dv;
                    }
                    Object result = m.invoke(obj, coerced);
                    // Push return value if non-void, else push obj back for chaining
                    if (result != null && m.getReturnType() != void.class) {
                        s.reg.pushObject(result);
                    } else {
                        s.reg.pushObject(obj);
                    }
                    return;
                } catch (Exception ignored) {}
            }
            // No-arg fallback
            for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(methodName) || m.getParameterCount() != 0) continue;
                try { m.invoke(obj); } catch (Exception ignored) {}
                break;
            }
            s.reg.pushObject(obj);
        }
    }

    /**
     * Placeholder used internally by UnaryExp for prefix ++ / --.
     * Simply adds or subtracts 1 from the top of stack.
     */
    static class IncrementGlobal implements ChuckInstr {
        int delta;
        IncrementGlobal(ChuckAST.Exp target, int delta,
                         Map<String, ChuckCode> functions, ChuckEmitter emitter) {
            this.delta = delta;
        }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            long raw = s.reg.popLong();
            s.reg.push(raw + delta);
        }
    }
}
