package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.chuck.audio.filter.BPF;
import org.chuck.audio.filter.BRF;
import org.chuck.audio.filter.HPF;
import org.chuck.audio.osc.BlitSaw;
import org.chuck.audio.osc.BlitSquare;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/**
 * ANTLR-based tests for high-priority new features: ternary ?:, switch/case, HPF/BPF/BRF filters,
 * BlitSaw/BlitSquare oscillators.
 */
public class ChuckAntlrNewFeaturesTest {

  /** Compile + run a ChucK snippet via the ANTLR pipeline. Returns print output. */
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
            throw new RuntimeException("Lexer error " + line + ":" + col + " – " + msg);
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
            throw new RuntimeException("Parser error " + line + ":" + col + " – " + msg);
          }
        });

    ChuckASTVisitor visitor = new ChuckASTVisitor();
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    ChuckEmitter emitter = new ChuckEmitter();
    ChuckCode code = emitter.emit(ast, "Test");

    ChuckVM vm = new ChuckVM(44100);
    List<String> output = Collections.synchronizedList(new ArrayList<>());
    vm.addPrintListener(output::add);

    ChuckShred shred = new ChuckShred(code);
    vm.spork(shred);
    Thread.sleep(200);
    vm.advanceTime(samples);

    return output;
  }

  // -------------------------------------------------------------------------
  // auto type inference
  // -------------------------------------------------------------------------

  @Test
  public void testAutoInt() throws InterruptedException {
    List<String> out = runChuck("42 => auto x; <<< x >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("42"), "Expected 42, got: " + out.get(0));
  }

  @Test
  public void testAutoFloat() throws InterruptedException {
    List<String> out = runChuck("3.14 => auto y; <<< y >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("3.14"), "Expected 3.14, got: " + out.get(0));
  }

  @Test
  public void testAutoString() throws InterruptedException {
    List<String> out = runChuck("\"hello\" => auto s; <<< s >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("hello"), "Expected hello, got: " + out.get(0));
  }

  @Test
  public void testAutoObject() throws InterruptedException {
    List<String> out = runChuck("new SinOsc @=> auto z; 440 => z.freq; <<< z.freq()$int >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("440"), "Expected 440, got: " + out.get(0));
  }

  // -------------------------------------------------------------------------
  // Ternary operator  ?:
  // -------------------------------------------------------------------------

  @Test
  public void testTernaryTrue() throws InterruptedException {
    List<String> out = runChuck("1 == 1 ? 42 : 0 => int x; <<< x >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("42"), "Expected 42, got: " + out.get(0));
  }

  @Test
  public void testTernaryFalse() throws InterruptedException {
    List<String> out = runChuck("1 == 2 ? 42 : 99 => int x; <<< x >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("99"), "Expected 99, got: " + out.get(0));
  }

  @Test
  public void testTernaryNested() throws InterruptedException {
    // (5 > 3) ? ((2 > 1) ? 10 : 20) : 30  =>  10
    List<String> out = runChuck("5 > 3 ? (2 > 1 ? 10 : 20) : 30 => int x; <<< x >>>;", 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("10"), "Expected 10, got: " + out.get(0));
  }

  // -------------------------------------------------------------------------
  // switch / case
  // -------------------------------------------------------------------------

  @Test
  public void testSwitchMatchFirst() throws InterruptedException {
    String src =
        """
        1 => int v;
        switch (v) {
          case 1: <<< "one" >>>;
          case 2: <<< "two" >>>;
        }
        """;
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("one"), "Expected 'one', got: " + out.get(0));
  }

  @Test
  public void testSwitchMatchSecond() throws InterruptedException {
    String src =
        """
        2 => int v;
        switch (v) {
          case 1: <<< "one" >>>;
          case 2: <<< "two" >>>;
        }
        """;
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("two"), "Expected 'two', got: " + out.get(0));
  }

  @Test
  public void testSwitchDefault() throws InterruptedException {
    String src =
        """
        99 => int v;
        switch (v) {
          case 1: <<< "one" >>>;
          default: <<< "other" >>>;
        }
        """;
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("other"), "Expected 'other', got: " + out.get(0));
  }

  @Test
  public void testSwitchNoMatch() throws InterruptedException {
    String src =
        """
        5 => int v;
        switch (v) {
          case 1: <<< "one" >>>;
          case 2: <<< "two" >>>;
        }
        """;
    List<String> out = runChuck(src, 10);
    assertEquals(0, out.size()); // nothing printed
  }

  // -------------------------------------------------------------------------
  // HPF / BPF / BRF filters — instantiation and basic signal processing
  // -------------------------------------------------------------------------

  @Test
  public void testHpfInstantiation() {
    HPF hpf = new HPF(44100f);
    assertEquals(1000.0, hpf.freq(), 0.001);
    assertEquals(0.707, hpf.Q(), 0.001);
    hpf.freq(500.0);
    assertEquals(500.0, hpf.freq(), 0.001);
  }

  @Test
  public void testHpfBlocksLowFreq() {
    HPF hpf = new HPF(44100f);
    hpf.freq(10000.0); // Very high cutoff — pass almost nothing at DC
    hpf.Q(0.707);

    // Feed a constant (DC) signal — HPF should suppress it to near zero
    float sumOut = 0f;
    for (int i = 0; i < 4096; i++) {
      sumOut += Math.abs(hpf.tick(1.0f, i));
    }
    float avg = sumOut / 4096;
    assertTrue(avg < 0.5f, "HPF at 10kHz should suppress DC; avg output = " + avg);
  }

  @Test
  public void testBpfInstantiation() {
    BPF bpf = new BPF(44100f);
    assertEquals(1000.0, bpf.freq(), 0.001);
    bpf.freq(2000.0);
    bpf.Q(5.0);
    assertEquals(2000.0, bpf.freq(), 0.001);
    assertEquals(5.0, bpf.Q(), 0.001);
  }

  @Test
  public void testBpfBlocksDC() {
    BPF bpf = new BPF(44100f);
    bpf.freq(1000.0);
    bpf.Q(1.0);

    // DC (constant 1) should be suppressed by BPF
    float sumOut = 0f;
    for (int i = 0; i < 4096; i++) {
      sumOut += Math.abs(bpf.tick(1.0f, i));
    }
    float avg = sumOut / 4096;
    assertTrue(avg < 0.5f, "BPF should suppress DC; avg output = " + avg);
  }

  @Test
  public void testBrfInstantiation() {
    BRF brf = new BRF(44100f);
    assertEquals(1000.0, brf.freq(), 0.001);
    assertEquals(1.0, brf.Q(), 0.001);
  }

  @Test
  public void testBrfPassesLowFreq() {
    BRF brf = new BRF(44100f);
    brf.freq(10000.0); // Notch at 10kHz — DC should pass through
    brf.Q(1.0);

    // DC signal — should mostly pass through
    float sumOut = 0f;
    for (int i = 0; i < 4096; i++) {
      sumOut += brf.tick(1.0f, i);
    }
    float avg = sumOut / 4096;
    // After settling, avg should be close to 1.0
    assertTrue(avg > 0.5f, "BRF at 10kHz should pass DC; avg = " + avg);
  }

  // -------------------------------------------------------------------------
  // BlitSaw / BlitSquare oscillators
  // -------------------------------------------------------------------------

  @Test
  public void testBlitSawInstantiation() {
    BlitSaw saw = new BlitSaw(44100f);
    assertEquals(220.0, saw.freq(), 0.001);
    saw.freq(440.0);
    assertEquals(440.0, saw.freq(), 0.001);
  }

  @Test
  public void testBlitSawProducesSignal() {
    BlitSaw saw = new BlitSaw(44100f);
    saw.freq(440.0);
    float max = 0f;
    for (int i = 0; i < 1000; i++) {
      float s = saw.tick(i);
      max = Math.max(max, Math.abs(s));
    }
    assertTrue(max > 0.1f, "BlitSaw should produce a non-trivial signal");
  }

  @Test
  public void testBlitSawOutput_BoundedAmplitude() {
    BlitSaw saw = new BlitSaw(44100f);
    saw.freq(440.0);
    for (int i = 0; i < 4410; i++) {
      float s = saw.tick(i);
      assertTrue(Math.abs(s) <= 2.0f, "BlitSaw sample out of range at i=" + i + ": " + s);
    }
  }

  @Test
  public void testBlitSquareInstantiation() {
    BlitSquare sq = new BlitSquare(44100f);
    assertEquals(220.0, sq.freq(), 0.001);
    assertEquals(0.5, sq.width(), 0.001);
    sq.freq(880.0);
    sq.width(0.25);
    assertEquals(880.0, sq.freq(), 0.001);
    assertEquals(0.25, sq.width(), 0.001);
  }

  @Test
  public void testBlitSquareProducesSignal() {
    BlitSquare sq = new BlitSquare(44100f);
    sq.freq(440.0);
    float max = 0f;
    for (int i = 0; i < 1000; i++) {
      float s = sq.tick(i);
      max = Math.max(max, Math.abs(s));
    }
    assertTrue(max > 0.1f, "BlitSquare should produce a non-trivial signal");
  }

  @Test
  public void testBlitSquareOutput_BoundedAmplitude() {
    BlitSquare sq = new BlitSquare(44100f);
    sq.freq(440.0);
    for (int i = 0; i < 4410; i++) {
      float s = sq.tick(i);
      assertTrue(Math.abs(s) <= 3.0f, "BlitSquare sample out of range at i=" + i + ": " + s);
    }
  }

  // -------------------------------------------------------------------------
  // ANTLR parse-only smoke tests for new filter/oscillator UGens in ChucK code
  // -------------------------------------------------------------------------

  @Test
  public void testAntlrParsesHPFCode() {
    String src = "HPF hpf;\n500.0 => hpf.freq;\n1000.0 => now;\n";
    assertDoesNotThrow(
        () -> {
          CharStream input = CharStreams.fromString(src);
          ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
          CommonTokenStream tokens = new CommonTokenStream(lexer);
          ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
          ChuckASTVisitor visitor = new ChuckASTVisitor();
          visitor.visit(parser.program());
        },
        "ANTLR should parse HPF code without errors");
  }

  @Test
  public void testAntlrParsesBPFCode() {
    String src = "BPF bpf;\n2000.0 => bpf.freq;\n5.0 => bpf.Q;\n1000.0 => now;\n";
    assertDoesNotThrow(
        () -> {
          CharStream input = CharStreams.fromString(src);
          ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
          CommonTokenStream tokens = new CommonTokenStream(lexer);
          ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
          ChuckASTVisitor visitor = new ChuckASTVisitor();
          visitor.visit(parser.program());
        },
        "ANTLR should parse BPF code without errors");
  }

  @Test
  public void testAntlrParsesBlitSawCode() {
    String src = "BlitSaw saw;\n440.0 => saw.freq;\nsaw => dac;\n1000.0 => now;\n";
    assertDoesNotThrow(
        () -> {
          CharStream input = CharStreams.fromString(src);
          ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
          CommonTokenStream tokens = new CommonTokenStream(lexer);
          ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
          ChuckASTVisitor visitor = new ChuckASTVisitor();
          visitor.visit(parser.program());
        },
        "ANTLR should parse BlitSaw code without errors");
  }

  // -------------------------------------------------------------------------
  // static member variables
  // -------------------------------------------------------------------------

  @Test
  public void testStaticIntLiteral() throws InterruptedException {
    String src =
        """
                 class Foo { 42 => static int S; }
                 <<< Foo.S >>>;""";
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("42"), "Expected 42, got: " + out.get(0));
  }

  @Test
  public void testStaticFloatLiteral() throws InterruptedException {
    String src =
        """
                 class Bar { 3.14 => static float F; }
                 <<< Bar.F >>>;""";
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("3.14"), "Expected 3.14, got: " + out.get(0));
  }

  @Test
  public void testStaticUGenInit() throws InterruptedException {
    // Test that static UGen is instantiated (non-null) and methods work
    String src =
        """
        class Baz { static SinOsc OSC; }
        440 => Baz.OSC.freq;
        <<< Baz.OSC.freq()$int >>>;""";
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("440"), "Expected 440, got: " + out.get(0));
  }

  @Test
  public void testStaticStringCtor() throws InterruptedException {
    String src =
        """
                 class Foo { static string S("hello"); }
                 <<< Foo.S >>>;""";
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("hello"), "Expected hello, got: " + out.get(0));
  }

  @Test
  public void testStaticDurInit() throws InterruptedException {
    // 3::second stored as dur (samples)
    String src =
        """
                 class D { 3::second => static dur T; }
                 <<< (D.T / second)$int >>>;""";
    List<String> out = runChuck(src, 10);
    assertEquals(1, out.size());
    assertTrue(out.get(0).contains("3"), "Expected 3, got: " + out.get(0));
  }

  @Test
  public void testAntlrParsesBlitSquareCode() {
    String src =
        "BlitSquare sq;\n220.0 => sq.freq;\n0.3 => sq.width;\nsq => dac;\n1000.0 => now;\n";
    assertDoesNotThrow(
        () -> {
          CharStream input = CharStreams.fromString(src);
          ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
          CommonTokenStream tokens = new CommonTokenStream(lexer);
          ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
          ChuckASTVisitor visitor = new ChuckASTVisitor();
          visitor.visit(parser.program());
        },
        "ANTLR should parse BlitSquare code without errors");
  }
}
