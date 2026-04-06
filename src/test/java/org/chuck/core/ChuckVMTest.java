package org.chuck.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckVMTest {

    @Test
    public void testInterpreterAndTiming() throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        
        ChuckCode code = new ChuckCode("TestProgram");
        code.addInstruction(new PushInt(10));
        code.addInstruction(new AdvanceTime());
        code.addInstruction(new PushInt(5));
        code.addInstruction(new PushInt(7));
        code.addInstruction(new AddInt());
        code.addInstruction(new AdvanceTime());
        
        ChuckShred shred = new ChuckShred(code);
        vm.spork(shred);
        
        Thread.sleep(100);
        vm.advanceTime(5);
        assertEquals(5, vm.getCurrentTime());
        assertFalse(shred.isDone());
        
        vm.advanceTime(10); // T=15
        assertEquals(15, vm.getCurrentTime());
        assertEquals(22, shred.getWakeTime());
        
        vm.advanceTime(10); // T=25
        assertEquals(25, vm.getCurrentTime());
        assertTrue(shred.isDone());
    }

    @Test
    public void testObjectAndMemberAccess() throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        ChuckType pointType = new ChuckType("Point", ChuckType.OBJECT, 2, 0);
        
        ChuckCode code = new ChuckCode("ObjectTest");
        code.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                ChuckObject obj = new ChuckObject(pointType);
                shred.reg.push(42); // value
                shred.reg.pushObject(obj); // target
                new SetMemberInt(0).execute(vm, shred); 
                
                shred.reg.pushObject(obj);
                new GetMemberInt(0).execute(vm, shred);
                
                long result = shred.reg.popLong();
                assertEquals(42, result);
            }
        });
        
        ChuckShred shred = new ChuckShred(code);
        vm.spork(shred);
        Thread.sleep(100);
        vm.advanceTime(1);
        assertTrue(shred.isDone());
    }

    @Test
    public void testEvents() throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        ChuckEvent event = new ChuckEvent();
        
        ChuckCode code1 = new ChuckCode("Waiter");
        code1.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                shred.reg.pushObject(event);
            }
        });
        code1.addInstruction(new WaitEvent());
        
        ChuckShred waiter = new ChuckShred(code1);
        vm.spork(waiter);
        
        Thread.sleep(100);
        vm.advanceTime(100);
        assertEquals(100, vm.getCurrentTime());
        assertFalse(waiter.isDone());
        
        ChuckCode code2 = new ChuckCode("Signaler");
        code2.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                event.signal(vm);
            }
        });
        
        ChuckShred signaler = new ChuckShred(code2);
        vm.spork(signaler);
        Thread.sleep(100);
        
        vm.advanceTime(1);
        
        assertTrue(signaler.isDone());
        assertTrue(waiter.isDone());
        assertEquals(101, vm.getCurrentTime());
    }

    @Test
    public void testArraysAndStrings() throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        ChuckCode code = new ChuckCode("ArrayStringTest");
        
        code.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                ChuckArray arr = new ChuckArray(ChuckType.ARRAY, 10);
                shred.reg.push(999); // value
                shred.reg.pushObject(arr); // array
                shred.reg.push(3); // index
                new SetArrayInt().execute(vm, shred);
                
                shred.reg.pushObject(arr);
                shred.reg.push(3); // index
                new GetArrayInt().execute(vm, shred);
                
                long val = shred.reg.popLong();
                assertEquals(999, val);
            }
        });
        
        code.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                new PushString("hello").execute(vm, shred);
                ChuckString s = (ChuckString) shred.reg.popObject();
                assertEquals("hello", s.toString());
            }
        });

        code.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                ChuckString s = new ChuckString("abc");
                shred.reg.pushObject(s);
                shred.reg.push(0L); // index
                // Manually trigger CallMethod logic (since it's internal to Emitter, we can't easily new it here)
                // But we can test ChuckString directly
                assertEquals(97, s.charAt(0));
                assertEquals(3, s.length());
            }
        });
        
        ChuckShred shred = new ChuckShred(code);
        vm.spork(shred);
        
        Thread.sleep(100);
        vm.advanceTime(1);
        assertTrue(shred.isDone());
    }

    @Test
    public void testGlobalVariables() throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        
        ChuckCode code1 = new ChuckCode("Setter");
        code1.addInstruction(new PushInt(777));
        code1.addInstruction(new SetGlobalInt("myGlobal"));
        
        ChuckCode code2 = new ChuckCode("Getter");
        code2.addInstruction(new PushInt(10));
        code2.addInstruction(new AdvanceTime());
        code2.addInstruction(new GetGlobalInt("myGlobal"));
        code2.addInstruction(new ChuckInstr() {
            @Override
            public void execute(ChuckVM vm, ChuckShred shred) {
                long val = shred.reg.popLong();
                assertEquals(777, val);
            }
        });
        
        vm.spork(new ChuckShred(code1));
        vm.spork(new ChuckShred(code2));
        
        Thread.sleep(100);
        vm.advanceTime(20);
        
        assertEquals(777, vm.getGlobalInt("myGlobal"));
    }

    @Test
    public void testFunctionCalls() throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);

        // Args are in mem stack at fp+0, fp+1 with the new calling convention.
        ChuckCode funcCode = new ChuckCode("AddFunction");
        funcCode.addInstruction(new org.chuck.core.instr.VarInstrs.MoveArgs(2));
        funcCode.addInstruction((vm2, shred) -> {
            int fp = shred.getFramePointer();
            long a = shred.mem.getData(fp);
            long b = shred.mem.getData(fp + 1);
            shred.reg.push(a + b);
        });
        funcCode.addInstruction(new ReturnFunc());
        
        ChuckCode mainCode = new ChuckCode("MainProgram");
        mainCode.addInstruction(new PushInt(10));
        mainCode.addInstruction(new PushInt(20));
        mainCode.addInstruction(new CallFunc(funcCode, 2));
        mainCode.addInstruction(new PushInt(5)); 
        mainCode.addInstruction(new AddInt()); 
        
        ChuckShred shred = new ChuckShred(mainCode);
        vm.spork(shred);
        
        Thread.sleep(100);
        vm.advanceTime(1);
        
        assertTrue(shred.isDone());
        assertEquals(35, shred.reg.popLong());
    }
}
