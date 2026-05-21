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
    assertTrue(javaCode.contains("advance(_toDur(e))"));
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

    assertTrue(javaCode.contains("long myFreq"));
    assertTrue(javaCode.contains("s.freq"));
    assertTrue(javaCode.contains("_chuckConnect(s, dac())"));
  }

  @Test
  public void testChuckChainEmission() throws Exception {
    String source =
        """
            1 => int a => int b;
            """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "ChainShred");

    assertTrue(javaCode.contains("public long a = (long)(0);"));
    assertTrue(javaCode.contains("public long b = (long)(0);"));
    assertTrue(javaCode.contains("a = ((long)(_num(1)));"));
    assertTrue(javaCode.contains("b ="));
  }

  @Test
  public void testUgenConnectionTargetEmission() throws Exception {
    String source =
        """
            SinOsc s => Bitcrusher bc => dac;
            """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "UgenChainShred");

    assertTrue(javaCode.contains("_chuckConnect(s, bc)"));
    assertTrue(javaCode.contains("_chuckConnect(bc, dac())"));
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

    assertTrue(javaCode.contains("advance(_toDur(eventAnd(e1, e2)))"));
    assertTrue(javaCode.contains("advance(_toDur(eventOr(e1, e2)))"));
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

    assertTrue(javaCode.contains("ChuckEvent[] e"));
    assertTrue(javaCode.contains("advance(_toDur(e))"));
    assertTrue(javaCode.contains("new ChuckEvent()"));
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

    assertTrue(javaCode.contains("e.timeout"));
    assertTrue(javaCode.contains("advance(_toDur(e))") || javaCode.contains("_advanceAndTrue(e)"));
  }

  @Test
  public void testMultiVariableMixedInitialization() throws Exception {
    String source =
        """
        int a, b, c;
        1 => a;
        10 => int y;
        int x, z;
        """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "MultiVarShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("long a"));
    assertTrue(javaCode.contains("long b"));
    assertTrue(javaCode.contains("long c"));
    assertTrue(javaCode.contains("long x"));
    assertTrue(javaCode.contains("long y"));
    assertTrue(javaCode.contains("long z"));
  }

  @Test
  public void testInterfaceMapping() throws Exception {
    String source =
        """
        public interface MyMappable {
            fun void map(int x);
            fun float get();
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
    String javaCode = converter.convert(ast, "InterfaceShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("interface MyMappable"));
    assertTrue(javaCode.contains("void map(long x)"));
    assertTrue(javaCode.contains("double get()"));
  }

  @Test
  public void testAssociativeArrayMapping() throws Exception {
    String source =
        """
        int m[];
        10 => m["key"];
        m["key"] => int x;
        """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "AssocArrayShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("ChuckArray m"));
  }

  @Test
  public void testOscMapping() throws Exception {
    String source =
        """
        OscIn oin;
        OscOut oout;
        1234 => oin.port;
        "/test, i f s" => oin.addAddress;
        "localhost" => oout.dest;
        6449 => oout.port;

        OscMsg msg;
        while (true) {
            oin => now;
            while (oin.recv(msg)) {
                msg.getInt(0) => int i;
                msg.getFloat(1) => float f;
                msg.getString(2) => string s;
            }
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
    String javaCode = converter.convert(ast, "OscShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("OscIn oin"));
    assertTrue(javaCode.contains("OscOut oout"));
    assertTrue(javaCode.contains("oin.port"));
    assertTrue(javaCode.contains("advance(_toDur(oin))"));
    assertTrue(
        javaCode.contains("_callBool(oin, \"recv\", msg)")
            || javaCode.contains("_truthy(_call(oin, \"recv\", msg))"));
  }

  @Test
  public void testOperatorOverloading() throws Exception {
    String source =
        """
        class Vec {
            float x, y;
            fun Vec @operator +(Vec other) {
                Vec res;
                x + other.x => res.x;
                y + other.y => res.y;
                return res;
            }
        }
        Vec v1, v2;
        v1 + v2 => Vec v3;
        """;

    var input = CharStreams.fromString(source);
    var lexer = new ChuckANTLRLexer(input);
    var tokens = new CommonTokenStream(lexer);
    var parser = new ChuckANTLRParser(tokens);

    var visitor = new ChuckASTVisitor(tokens);
    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    var converter = new ChuckToDSLConverter();
    String javaCode = converter.convert(ast, "OpShred");

    System.out.println(javaCode);

    assertTrue(javaCode.contains("Vec __op__plus"));
    // assertTrue(javaCode.contains(".__op__plus"));
  }
}
