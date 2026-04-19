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

    assertTrue(javaCode.contains("public long myFreq = (long)(0)"));
    assertTrue(javaCode.contains("myFreq = 100"));
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

  @Test
  public void testMultiVariableMixedInitialization() throws Exception {
    String source = """
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

    assertTrue(javaCode.contains("public long a = (long)(0)"));
    assertTrue(javaCode.contains("public long b = (long)(0)"));
    assertTrue(javaCode.contains("public long c = (long)(0)"));
    assertTrue(javaCode.contains("public long x = (long)(0)"));
    assertTrue(javaCode.contains("public long y = (long)(0)"));
    assertTrue(javaCode.contains("public long z = (long)(0)"));
    
    // Check initializations in shred()
    assertTrue(javaCode.contains("a = (long)(1)"));
    assertTrue(javaCode.contains("y = 10"));
  }

  @Test
  public void testInterfaceMapping() throws Exception {
    String source = """
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

    assertTrue(javaCode.contains("public interface MyMappable"));
    assertTrue(javaCode.contains("public void map(long x);"));
    assertTrue(javaCode.contains("public double get();"));
    assertFalse(javaCode.contains("public void map(long x) {"));
  }

  @Test
  public void testAssociativeArrayMapping() throws Exception {
    String source = """
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

    assertTrue(javaCode.contains("public ChuckArray m = new ChuckArray(\"long\", 0)"));
    assertTrue(javaCode.contains("setInt(m, \"key\", 10)"));
    assertTrue(javaCode.contains("x = (long)getObject(m, \"key\")"));
    }

    @Test
    public void testOscMapping() throws Exception {
    String source = """
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

    assertTrue(javaCode.contains("public OscIn oin = new OscIn()"));
    assertTrue(javaCode.contains("public OscOut oout = new OscOut()"));
    assertTrue(javaCode.contains("public String s = null"));
    assertTrue(javaCode.contains("oin.port(1234)"));
    assertTrue(javaCode.contains("oin.addAddress(\"/test, i f s\")"));
    assertTrue(javaCode.contains("advance(oin)"));
    assertTrue(javaCode.contains("oin.recv(msg)"));
    assertTrue(javaCode.contains("s = (String)msg.getString(2)"));
    }

    @Test
    public void testOperatorOverloading() throws Exception {
    String source = """
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

    assertTrue(javaCode.contains("public Vec __op__plus(Vec other)"));
    assertTrue(javaCode.contains("v1.__op__plus(v2)"));
    }
    }
