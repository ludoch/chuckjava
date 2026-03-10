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
                code.addInstruction(new InstantiateAndSetGlobal(s.type(), s.name()));
            }
        } else if (stmt instanceof ChuckAST.FuncDefStmt s) {
            ChuckCode funcCode = new ChuckCode(s.name());
            emitStatement(s.body(), funcCode);
            funcCode.addInstruction(new ReturnFunc());
            functions.put(s.name(), funcCode);
        }
    }

    private void emitExpression(ChuckAST.Exp exp, ChuckCode code) {
        if (exp instanceof ChuckAST.IntExp e) {
            code.addInstruction(new PushInt(e.value()));
        } else if (exp instanceof ChuckAST.FloatExp e) {
            code.addInstruction(new PushFloat(e.value()));
        } else if (exp instanceof ChuckAST.BinaryExp e) {
            if (e.op() == ChuckAST.Operator.CHUCK || e.op() == ChuckAST.Operator.AT_CHUCK) {
                emitExpression(e.lhs(), code);
                emitChuckTarget(e.rhs(), code);
            } else {
                emitExpression(e.lhs(), code);
                emitExpression(e.rhs(), code);
                switch (e.op()) {
                    case PLUS -> code.addInstruction(new AddAny());
                    case MINUS -> code.addInstruction(new MinusAny());
                    case TIMES -> code.addInstruction(new TimesAny());
                    case DIVIDE -> code.addInstruction(new DivideAny());
                    case LT -> code.addInstruction(new LessThanAny());
                    case GT -> code.addInstruction(new GreaterThanAny());
                    case EQ -> code.addInstruction(new EqualsAny());
                    case NONE -> {
                        if (e.rhs() instanceof ChuckAST.IdExp id) {
                            code.addInstruction(new CreateDuration(id.name()));
                        }
                    }
                }
            }
        } else if (exp instanceof ChuckAST.IdExp e) {
            if (e.name().equals("now")) code.addInstruction(new PushNow());
            else if (e.name().equals("dac")) code.addInstruction(new PushDac());
            else if (e.name().equals("blackhole")) code.addInstruction(new PushBlackhole());
            else {
                code.addInstruction(new GetGlobalObjectOrInt(e.name()));
            }
        } else if (exp instanceof ChuckAST.ArrayLitExp e) {
            for (ChuckAST.Exp el : e.elements()) emitExpression(el, code);
            code.addInstruction(new NewArrayFromStack(e.elements().size()));
        } else if (exp instanceof ChuckAST.ArrayAccessExp e) {
            emitExpression(e.base(), code);
            emitExpression(e.index(), code);
            code.addInstruction(new GetArrayInt());
        } else if (exp instanceof ChuckAST.CallExp e) {
            if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Std")) {
                if (dot.member().equals("mtof")) {
                    emitExpression(e.args().get(0), code);
                    code.addInstruction(new StdMtof());
                }
            } else if (e.base() instanceof ChuckAST.DotExp dot && dot.member().equals("last")) {
                emitExpression(dot.base(), code);
                code.addInstruction(new GetLastOut());
            } else if (e.base() instanceof ChuckAST.DotExp dot && dot.base() instanceof ChuckAST.IdExp id && id.name().equals("Math")) {
                if (dot.member().equals("random")) {
                    code.addInstruction(new MathRandom());
                }
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

    static class ConnectToBlackhole implements ChuckInstr {
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckUGen src = (ChuckUGen) s.reg.popObject();
            src.chuckTo(vm.blackhole);
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
        InstantiateAndSetGlobal(String t, String n) { type = t; name = n; }
        @Override public void execute(ChuckVM vm, ChuckShred s) {
            ChuckObject obj = null;
            float sr = vm.getSampleRate();
            switch (type) {
                case "SinOsc" -> obj = new SinOsc(sr);
                case "SawOsc" -> obj = new SawOsc(sr);
                case "Mandolin" -> obj = new Mandolin(100.0f, sr);
                case "Clarinet" -> obj = new Clarinet(100.0f, sr);
                case "ADSR", "Adsr" -> obj = new Adsr(sr);
                case "Gain" -> obj = new Gain();
                case "Phasor" -> obj = new Phasor(sr);
                case "PulseOsc" -> obj = new PulseOsc(sr);
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
}
