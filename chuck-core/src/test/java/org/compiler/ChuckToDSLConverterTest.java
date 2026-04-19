package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

public class ChuckToDSLConverterTest {

  @Test
  public void testComplexTransformation() throws Exception {
    String source = Files.readString(Paths.get("src/test/resources/complex_test.ck"));

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "ComplexShred");

    System.out.println("--- Generated Java DSL ---");
    System.out.println(javaCode);
    System.out.println("--------------------------");

    assertTrue(javaCode.contains("class MyTest"));
    assertTrue(javaCode.contains("void setX(long val)"));
    assertTrue(javaCode.contains("void notifier()"));
    assertTrue(javaCode.contains("Machine.getGlobalInt(\"g_val\")"));
    assertTrue(javaCode.contains("advance(e)"));
  }

  @Test
  public void testTypeInference() throws Exception {
    String source =
        """
            SinOsc s;
            100 => int myFreq;
            myFreq => s.freq;
            1000 => myFreq;
            s => dac;
            """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "InferenceShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("long myFreq = 100"));
    assertTrue(javaCode.contains("s.freq(myFreq)"));
    assertTrue(javaCode.contains("myFreq = (long)(1000)"));
    assertTrue(javaCode.contains("s.chuck(dac())"));
  }

  @Test
  public void testEventCompound() throws Exception {
    String source =
        """
            Event e1, e2;
            e1 && e2 => now;
            e1 || e2 => now;
            """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "EventShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("advance(eventAnd(e1, e2))"));
    assertTrue(javaCode.contains("advance(eventOr(e1, e2))"));
  }

  @Test
  public void testEventArrayWaiting() throws Exception {
    String source =
        """
            Event e[10];
            e => now;
            """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "EventArrayShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("ChuckEvent[] e = new ChuckEvent[10]"));
    assertTrue(javaCode.contains("advance(e)"));
    // Check for auto-init
    assertTrue(javaCode.contains("e[i_e] = new ChuckEvent()"));
  }

  @Test
  public void testEventTimeout() throws Exception {
    String source =
        """
            Event e;
            100::ms => e.timeout;
            if (e => now) {
                <<< "Signaled" >>>;
            } else {
                <<< "Timeout" >>>;
            }
            """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "TimeoutShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("e.timeout(ms(100))"));
    assertTrue(javaCode.contains("if (advance(e))"));
  }
}
