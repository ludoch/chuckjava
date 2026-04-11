package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.*;
import org.chuck.core.*;
import org.junit.jupiter.api.Test;

/** Tests for the full Machine shred API and me.* shred API. All tests use the ANTLR pipeline. */
public class ChuckMachineApiTest {

  private List<String> runChuck(String source, int samples) throws InterruptedException {
    CharStream input = CharStreams.fromString(source);
    ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
    lexer.removeErrorListeners();
    lexer.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> r,
              Object sym,
              int line,
              int col,
              String msg,
              RecognitionException e) {
            throw new RuntimeException("Lexer: " + line + ":" + col + " – " + msg);
          }
        });
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> r,
              Object sym,
              int line,
              int col,
              String msg,
              RecognitionException e) {
            throw new RuntimeException("Parser: " + line + ":" + col + " – " + msg);
          }
        });
    ChuckASTVisitor visitor = new ChuckASTVisitor();
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    ChuckEmitter emitter = new ChuckEmitter();
    ChuckCode code = emitter.emit(ast, "MachineApiTest");

    ChuckVM vm = new ChuckVM(44100);
    List<String> output = Collections.synchronizedList(new ArrayList<>());
    vm.addPrintListener(s1 -> output.add(s1.stripTrailing()));

    ChuckShred shred = new ChuckShred(code);
    vm.spork(shred);
    Thread.sleep(200);
    vm.advanceTime(samples);
    return output;
  }

  // -------------------------------------------------------------------------
  // me.id()
  // -------------------------------------------------------------------------

  @Test
  public void testMeId() throws InterruptedException {
    List<String> out = runChuck("me.id() => int id; <<< id >>>;", 10);
    assertEquals(1, out.size());
    // ID should be a positive integer
    long id = Long.parseLong(out.get(0).trim());
    assertTrue(id > 0, "me.id() should return a positive shred ID, got: " + id);
  }

  // -------------------------------------------------------------------------
  // me.args() / me.numArgs()
  // -------------------------------------------------------------------------

  @Test
  public void testMeArgs() throws InterruptedException {
    List<String> out = runChuck("<<< me.args() >>>;", 10);
    assertEquals(1, out.size());
    assertEquals("0", out.get(0).trim());
  }

  @Test
  public void testMeNumArgs() throws InterruptedException {
    List<String> out = runChuck("<<< me.numArgs() >>>;", 10);
    assertEquals(1, out.size());
    assertEquals("0", out.get(0).trim());
  }

  // -------------------------------------------------------------------------
  // me.path() / me.source()
  // -------------------------------------------------------------------------

  @Test
  public void testMePath() throws InterruptedException {
    // Should return a string (may be empty for in-memory code, but not throw)
    List<String> out = runChuck("<<< me.path() >>>;", 10);
    assertEquals(1, out.size());
    assertNotNull(out.get(0));
  }

  @Test
  public void testMeSource() throws InterruptedException {
    // me.source() is an alias for me.path()
    List<String> out1 = runChuck("<<< me.path() >>>;", 10);
    List<String> out2 = runChuck("<<< me.source() >>>;", 10);
    assertEquals(1, out1.size());
    assertEquals(1, out2.size());
    assertEquals(out1.get(0), out2.get(0));
  }

  // -------------------------------------------------------------------------
  // me.running()
  // -------------------------------------------------------------------------

  @Test
  public void testMeRunning() throws InterruptedException {
    // While executing, me.running() should return 1
    List<String> out = runChuck("<<< me.running() >>>;", 10);
    assertEquals(1, out.size());
    assertEquals("1", out.get(0).trim());
  }

  // -------------------------------------------------------------------------
  // me.dir()
  // -------------------------------------------------------------------------

  @Test
  public void testMeDir() throws InterruptedException {
    List<String> out = runChuck("<<< me.dir() >>>;", 10);
    assertEquals(1, out.size());
    assertNotNull(out.get(0));
    // Should end with /
    String dir = out.get(0).trim();
    // dir may be "./" for in-memory code
    assertFalse(dir.isEmpty(), "me.dir() should not be empty");
  }

  // -------------------------------------------------------------------------
  // Machine.numShreds()
  // -------------------------------------------------------------------------

  @Test
  public void testMachineNumShreds() throws InterruptedException {
    // At the time the shred executes, there should be at least 1 shred active
    List<String> out = runChuck("<<< Machine.numShreds() >>>;", 10);
    assertEquals(1, out.size());
    long n = Long.parseLong(out.get(0).trim());
    assertTrue(n >= 1, "Machine.numShreds() should be >= 1, got: " + n);
  }

  // -------------------------------------------------------------------------
  // Machine.shredExists(id)
  // -------------------------------------------------------------------------

  @Test
  public void testMachineShredExists_self() throws InterruptedException {
    // The current shred should exist
    List<String> out = runChuck("me.id() => int myId; <<< Machine.shredExists(myId) >>>;", 10);
    assertEquals(1, out.size());
    assertEquals("1", out.get(0).trim());
  }

  @Test
  public void testMachineShredExists_invalid() throws InterruptedException {
    // A large invalid ID should not exist
    List<String> out = runChuck("<<< Machine.shredExists(99999) >>>;", 10);
    assertEquals(1, out.size());
    assertEquals("0", out.get(0).trim());
  }

  // -------------------------------------------------------------------------
  // Machine.shreds() — returns array of active shred IDs
  // -------------------------------------------------------------------------

  @Test
  public void testMachineShreds() throws InterruptedException {
    // Machine.shreds() returns a ChuckArray; at minimum current shred is in it
    // We verify by checking the returned value is non-null (prints @(...))
    List<String> out = runChuck("Machine.shreds() => auto ids; <<< ids >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("["), "Expected ChuckArray output [...], got: " + out.get(0));
  }

  // -------------------------------------------------------------------------
  // Machine.eval() — compile and spork a snippet inline
  // -------------------------------------------------------------------------

  @Test
  public void testMachineEval() throws InterruptedException {
    // Machine.eval() returns a shred ID (> 0 on success).
    // The VM also logs a "sporking" message, so there may be multiple output lines.
    List<String> out = runChuck("Machine.eval(\"1 => now;\") => int sid; <<< sid >>>;", 10);
    assertFalse(out.isEmpty(), "Expected at least one output line");
    // Find the line that contains only the numeric shred ID
    long sid = -1;
    for (String line : out) {
      try {
        sid = Long.parseLong(line.trim());
        break;
      } catch (NumberFormatException ignored) {
      }
    }
    assertTrue(sid > 0, "Machine.eval() should return a positive shred ID, got: " + sid);
  }

  // -------------------------------------------------------------------------
  // Machine.status() — should not throw
  // -------------------------------------------------------------------------

  @Test
  public void testMachineStatus() throws InterruptedException {
    // Machine.status() prints the VM status; check it produces some output
    List<String> out = runChuck("Machine.status();", 1000);
    // Status prints directly to VM output
    assertFalse(out.isEmpty(), "Machine.status() should produce output");
  }

  // -------------------------------------------------------------------------
  // me.exit() — terminates the shred early
  // -------------------------------------------------------------------------

  @Test
  public void testMeExit() throws InterruptedException {
    // After me.exit(), subsequent statements should not run
    List<String> out = runChuck("me.exit(); <<< \"after\" >>>;", 10);
    assertEquals(0, out.size(), "me.exit() should prevent further execution");
  }

  // -------------------------------------------------------------------------
  // Machine.realtime / Machine.silent (read-only properties)
  // -------------------------------------------------------------------------

  @Test
  public void testMachineRealtime() throws InterruptedException {
    List<String> out = runChuck("<<< Machine.realtime >>>;", 10);
    assertEquals(1, out.size());
  }

  @Test
  public void testMachineSilent() throws InterruptedException {
    List<String> out = runChuck("<<< Machine.silent >>>;", 10);
    assertEquals(1, out.size());
  }
}
