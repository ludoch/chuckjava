package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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
    code.addInstruction(
        new ChuckInstr() {
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
    code1.addInstruction(
        new ChuckInstr() {
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
    code2.addInstruction(
        new ChuckInstr() {
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

    code.addInstruction(
        new ChuckInstr() {
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

    code.addInstruction(
        new ChuckInstr() {
          @Override
          public void execute(ChuckVM vm, ChuckShred shred) {
            new PushString("hello").execute(vm, shred);
            ChuckString s = (ChuckString) shred.reg.popObject();
            assertEquals("hello", s.toString());
          }
        });

    code.addInstruction(
        new ChuckInstr() {
          @Override
          public void execute(ChuckVM vm, ChuckShred shred) {
            ChuckString s = new ChuckString("abc");
            shred.reg.pushObject(s);
            shred.reg.push(0L); // index
            // Manually trigger CallMethod logic (since it's internal to Emitter, we can't easily
            // new it here)
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
    code2.addInstruction(
        new ChuckInstr() {
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

  /**
   * Regression test for the "IDE silence" bug: vm.advanceTime() ticks DAC channels directly.
   * getDacChannel(c).getLastOut() must return non-zero after a SinOsc shred runs. Any intermediary
   * UGen that is NOT ticked by the VM (e.g. a Gain wired as a DAC sink) will always return 0 — the
   * audio path must read from the DAC channels themselves.
   */
  @Test
  public void testDacOutputNonZeroAfterSinOsc() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    String src = "SinOsc s => dac; 440 => s.freq; 0.5 => s.gain; 256::samp => now;";
    org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(src);
    org.chuck.compiler.ChuckANTLRLexer lexer = new org.chuck.compiler.ChuckANTLRLexer(input);
    org.antlr.v4.runtime.CommonTokenStream tokens =
        new org.antlr.v4.runtime.CommonTokenStream(lexer);
    org.chuck.compiler.ChuckANTLRParser parser = new org.chuck.compiler.ChuckANTLRParser(tokens);
    org.chuck.compiler.ChuckASTVisitor visitor = new org.chuck.compiler.ChuckASTVisitor();
    @SuppressWarnings("unchecked")
    java.util.List<org.chuck.compiler.ChuckAST.Stmt> ast =
        (java.util.List<org.chuck.compiler.ChuckAST.Stmt>) visitor.visit(parser.program());
    ChuckCode code = new org.chuck.compiler.ChuckEmitter().emit(ast, "test.ck");
    vm.spork(new ChuckShred(code));

    Thread.sleep(50); // let shred start

    // Advance 256 samples — the SinOsc shred runs and ticks the DAC
    vm.advanceTime(256);

    float maxSample = 0f;
    for (int c = 0; c < 2; c++) {
      float s = vm.getDacChannel(c).getLastOut();
      maxSample = Math.max(maxSample, Math.abs(s));
    }
    assertTrue(
        maxSample > 0.001f,
        "DAC output must be non-zero after SinOsc shred runs; got "
            + maxSample
            + ". A UGen wired as a DAC sink (not ticked by VM) would stay at 0.");
  }

  /** Helper: compile a ChucK source string into a ChuckCode object. */
  private static ChuckCode compile(String src) throws Exception {
    org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(src);
    org.chuck.compiler.ChuckANTLRLexer lexer = new org.chuck.compiler.ChuckANTLRLexer(input);
    org.antlr.v4.runtime.CommonTokenStream tokens =
        new org.antlr.v4.runtime.CommonTokenStream(lexer);
    org.chuck.compiler.ChuckANTLRParser parser = new org.chuck.compiler.ChuckANTLRParser(tokens);
    org.chuck.compiler.ChuckASTVisitor visitor = new org.chuck.compiler.ChuckASTVisitor();
    @SuppressWarnings("unchecked")
    java.util.List<org.chuck.compiler.ChuckAST.Stmt> ast =
        (java.util.List<org.chuck.compiler.ChuckAST.Stmt>) visitor.visit(parser.program());
    return new org.chuck.compiler.ChuckEmitter().emit(ast, "test.ck");
  }

  /**
   * Regression test: after vm.clear(), DAC output must drop to zero within a few samples. This
   * verifies that cleanup() disconnects UGens synchronously so the audio engine goes silent.
   */
  @Test
  public void testDacSilentAfterClear() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    // Infinite SinOsc loop — keeps outputting sound until stopped
    String src = "SinOsc s => dac; 440 => s.freq; 0.5 => s.gain; while(true) { 128::samp => now; }";
    vm.spork(new ChuckShred(compile(src)));

    Thread.sleep(50); // let shred start

    // Advance 256 samples — shred runs and ticks DAC, should be non-zero
    vm.advanceTime(256);
    float maxBefore = 0f;
    for (int c = 0; c < 2; c++) {
      maxBefore = Math.max(maxBefore, Math.abs(vm.getDacChannel(c).getLastOut()));
    }
    assertTrue(maxBefore > 0.001f, "DAC must be non-zero before clear; got " + maxBefore);

    // Stop all shreds — UGens must be disconnected synchronously
    vm.clear();

    // Advance a few more samples — no sources on DAC, must be silent
    vm.advanceTime(64);
    float maxAfter = 0f;
    for (int c = 0; c < 2; c++) {
      maxAfter = Math.max(maxAfter, Math.abs(vm.getDacChannel(c).getLastOut()));
    }
    assertEquals(
        0.0f,
        maxAfter,
        0.0f,
        "DAC must be exactly 0 after vm.clear(); got "
            + maxAfter
            + ". UGens were not disconnected from DAC.");
  }

  /**
   * Regression test: after vm.removeShred(), the specific shred's UGens are disconnected and DAC
   * goes silent.
   */
  @Test
  public void testDacSilentAfterRemoveShred() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    String src = "SinOsc s => dac; 440 => s.freq; 0.5 => s.gain; while(true) { 128::samp => now; }";
    int shredId = vm.spork(new ChuckShred(compile(src)));

    Thread.sleep(50);
    vm.advanceTime(256);

    float maxBefore = 0f;
    for (int c = 0; c < 2; c++) {
      maxBefore = Math.max(maxBefore, Math.abs(vm.getDacChannel(c).getLastOut()));
    }
    assertTrue(maxBefore > 0.001f, "DAC must be non-zero before removeShred; got " + maxBefore);

    vm.removeShred(shredId);

    vm.advanceTime(64);
    float maxAfter = 0f;
    for (int c = 0; c < 2; c++) {
      maxAfter = Math.max(maxAfter, Math.abs(vm.getDacChannel(c).getLastOut()));
    }
    assertEquals(
        0.0f,
        maxAfter,
        0.0f,
        "DAC must be exactly 0 after vm.removeShred(); got "
            + maxAfter
            + ". UGens were not disconnected.");
  }

  /**
   * Concurrent version: audio thread drives advanceTime() continuously (like ChuckAudio), while a
   * separate thread calls vm.clear() — mirrors the real IDE threading model. DAC must go to zero
   * within a short window after clear().
   */
  @Test
  public void testDacSilentAfterClearConcurrent() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    String src = "SinOsc s => dac; 440 => s.freq; 0.5 => s.gain; while(true) { 128::samp => now; }";
    vm.spork(new ChuckShred(compile(src)));
    Thread.sleep(50);

    // Simulate audio thread driving advanceTime continuously
    java.util.concurrent.atomic.AtomicBoolean audioRunning =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    java.util.concurrent.atomic.AtomicInteger maxAfterClear =
        new java.util.concurrent.atomic.AtomicInteger(0);
    java.util.concurrent.atomic.AtomicBoolean cleared =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicInteger samplesAfterClear =
        new java.util.concurrent.atomic.AtomicInteger(0);

    Thread audioThread =
        Thread.ofPlatform()
            .name("TestAudioThread")
            .start(
                () -> {
                  while (audioRunning.get()) {
                    vm.advanceTime(1);
                    if (cleared.get()) {
                      int cnt = samplesAfterClear.incrementAndGet();
                      float s = Math.abs(vm.getDacChannel(0).getLastOut());
                      if (s > 0.001f) {
                        // Still non-zero after clear — record it
                        maxAfterClear.set(Math.max(maxAfterClear.get(), (int) (s * 1_000_000)));
                      }
                      if (cnt >= 512) audioRunning.set(false); // 512 samples is enough to check
                    }
                  }
                });

    // Let audio thread advance 2048 samples with the SinOsc running
    Thread.sleep(50);

    // Now call clear() from a separate thread (like the JavaFX UI thread)
    vm.clear();
    cleared.set(true);

    // Wait for the audio thread to finish its 512-sample check window
    audioThread.join(5000);
    assertFalse(audioThread.isAlive(), "Audio thread did not finish");

    // After 512 samples post-clear, DAC should be silent
    // (allow 1 buffer ≈ 512 samples of drain time for in-flight audio)
    assertEquals(
        0,
        maxAfterClear.get(),
        "DAC must be 0 within 512 samples after vm.clear() from concurrent thread; "
            + "got non-zero samples. UGens not properly disconnected.");
  }

  /**
   * Regression: shred sporked when the VM clock is already far ahead must still play for the full
   * requested duration. Before the fix, wakeTime started at 0 so yield(256) set wakeTime=256 which
   * was already in the past, causing the shred to exit immediately ("first click: nothing").
   */
  @Test
  public void testShredSporkedLateStillPlaysFullDuration() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    // Fast-forward the VM 10000 samples (simulates IDE running for ~0.2s before user clicks)
    vm.advanceTime(10000);
    assertEquals(10000, vm.getCurrentTime());

    // Spork a SinOsc shred that plays for exactly 256 samples
    String src = "SinOsc s => dac; 440 => s.freq; 0.5 => s.gain; 256::samp => now;";
    ChuckShred shred = new ChuckShred(compile(src));
    vm.spork(shred);
    Thread.sleep(50); // give virtual thread time to start

    // Advance sample-by-sample up to 2048 samples, tracking shred state per sample.
    // We collect the max DAC value while the shred is alive, then wait for cleanup to finish
    // (cleanup runs in the virtual thread, slightly after isDone is set), then verify silence.
    float maxWhileAlive = 0f;
    boolean cleanupWaited = false;
    float maxAfterCleanup = 0f;
    for (int i = 0; i < 2048; i++) {
      boolean wasDone = shred.isDone;
      vm.advanceTime(1);
      float s = 0f;
      for (int c = 0; c < 2; c++) {
        s = Math.max(s, Math.abs(vm.getDacChannel(c).getLastOut()));
      }
      if (!wasDone) {
        maxWhileAlive = Math.max(maxWhileAlive, s);
      } else if (!cleanupWaited) {
        // isDone just became true — give the virtual thread time to finish cleanup()
        Thread.sleep(30);
        cleanupWaited = true;
      } else {
        maxAfterCleanup = Math.max(maxAfterCleanup, s);
      }
    }
    assertTrue(
        maxWhileAlive > 0.001f,
        "DAC must be non-zero DURING shred lifetime after late spork; got "
            + maxWhileAlive
            + ". wakeTime was not initialized to now, causing yield() to expire immediately.");
    assertEquals(
        0.0f,
        maxAfterCleanup,
        0.001f,
        "DAC must be silent after shred finishes and cleanup runs; got " + maxAfterCleanup);
  }

  /**
   * Regression: the default IDE script ("SinOsc s => dac; ... 1::second => now;") must stop
   * producing sound after ~44100 samples, even with a concurrent audio thread. Before the wakeTime
   * and self-scheduling fixes, the sound would play forever because the shred never terminated.
   */
  @Test
  public void testDefaultScriptStopsAfterOneSecond() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    // Simulate audio thread already running for 10000 samples (IDE has been open a while)
    vm.advanceTime(10000);

    // Compile and spork the default IDE script
    String defaultScript = "SinOsc s => dac; 0.5 => s.gain; 440 => s.freq; 1::second => now;";
    ChuckShred shred = new ChuckShred(compile(defaultScript));
    vm.spork(shred);
    Thread.sleep(50); // let virtual thread start

    // Run a background "audio thread" for 2.5 seconds (110250 samples)
    java.util.concurrent.atomic.AtomicBoolean audioRunning =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    float[] maxWhileAlive = {0f};
    float[] maxAfterDone = {0f};
    boolean[] cleanupWaited = {false};

    Thread audioThread =
        Thread.ofPlatform()
            .name("TestAudio")
            .start(
                () -> {
                  for (int i = 0; i < 110250 && audioRunning.get(); i++) {
                    boolean wasDone = shred.isDone;
                    vm.advanceTime(1);
                    float s = Math.abs(vm.getDacChannel(0).getLastOut());
                    if (!wasDone) {
                      if (s > maxWhileAlive[0]) maxWhileAlive[0] = s;
                    } else if (!cleanupWaited[0]) {
                      cleanupWaited[0] = true;
                      try {
                        Thread.sleep(30); // let virtual thread finish cleanup()
                      } catch (InterruptedException ignored) {
                      }
                    } else {
                      if (s > maxAfterDone[0]) maxAfterDone[0] = s;
                    }
                  }
                });

    audioThread.join(10000);
    assertFalse(audioThread.isAlive(), "Audio thread did not finish in time");

    assertTrue(
        maxWhileAlive[0] > 0.001f,
        "DAC must be non-zero while script is playing; got " + maxWhileAlive[0]);
    assertEquals(
        0.0f,
        maxAfterDone[0],
        0.001f,
        "DAC must go silent after 1::second => now; script ends; got " + maxAfterDone[0]);
  }

  /**
   * Regression: sporking the SAME script twice must not leave UGens orphaned. The second shred
   * reuses the global SinOsc object; it must still register it in ownedUGens so cleanup()
   * disconnects it. Before the fix, the second click caused sound to play forever because the
   * reused UGen was not re-registered with the new shred.
   */
  @Test
  public void testSecondSporkDisconnectsUGen() throws Exception {
    int sampleRate = 44100;
    ChuckVM vm = new ChuckVM(sampleRate);

    String src = "SinOsc s => dac; 440 => s.freq; 0.5 => s.gain; while(true) { 128::samp => now; }";

    // First spork — plays forever
    vm.spork(new ChuckShred(compile(src)));
    Thread.sleep(30);
    vm.advanceTime(512);
    assertTrue(vm.getDacChannel(0).getLastOut() != 0f, "DAC must be non-zero after first spork");

    // Stop the first shred
    vm.clear();
    Thread.sleep(30);
    vm.advanceTime(64);
    assertEquals(0f, vm.getDacChannel(0).getLastOut(), 0.001f, "DAC must be silent after clear()");

    // Second spork — same source, SinOsc 's' is already in vm.globalObjects
    vm.spork(new ChuckShred(compile(src)));
    Thread.sleep(30);
    vm.advanceTime(512);
    assertTrue(vm.getDacChannel(0).getLastOut() != 0f, "DAC must be non-zero after second spork");

    // Stop again — must go silent (bug: second-spork UGen not owned by shred, so not disconnected)
    vm.clear();
    Thread.sleep(30);
    vm.advanceTime(64);
    assertEquals(
        0f,
        vm.getDacChannel(0).getLastOut(),
        0.001f,
        "DAC must be silent after second clear() — UGen reuse bug");
  }

  @Test
  public void testFunctionCalls() throws InterruptedException {
    ChuckVM vm = new ChuckVM(44100);

    // Args are in mem stack at fp+0, fp+1 with the new calling convention.
    ChuckCode funcCode = new ChuckCode("AddFunction");
    funcCode.addInstruction(new org.chuck.core.instr.VarInstrs.MoveArgs(2));
    funcCode.addInstruction(
        (vm2, shred) -> {
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
