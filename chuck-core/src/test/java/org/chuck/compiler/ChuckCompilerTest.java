package org.chuck.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;

public class ChuckCompilerTest {

  @Test
  public void testLexerAndParser() {
    String code =
        """
            SinOsc s => dac;
            10 => s.freq;
            while (true) {
                1.0 => now;
            }
            """;

    CharStream input = CharStreams.fromString(code);
    ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
    ChuckASTVisitor visitor = new ChuckASTVisitor();

    @SuppressWarnings("unchecked")
    List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

    assertNotNull(ast);
    // Expecting at least 3 top-level structures
    assertTrue(ast.size() >= 3);

    // Check while loop
    assertTrue(ast.stream().anyMatch(s -> s instanceof ChuckAST.WhileStmt));
  }
}
