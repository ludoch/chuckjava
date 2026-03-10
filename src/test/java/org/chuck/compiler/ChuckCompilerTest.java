package org.chuck.compiler;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckCompilerTest {

    @Test
    public void testLexerAndParser() {
        String code = """
            SinOsc s => dac;
            10 => s.freq;
            while (true) {
                1.0 => now;
            }
            """;
        
        ChuckLexer lexer = new ChuckLexer(code);
        List<ChuckLexer.Token> tokens = lexer.tokenize();
        
        assertNotNull(tokens);
        
        ChuckParser parser = new ChuckParser(tokens);
        List<ChuckAST.Stmt> ast = parser.parse();
        
        assertNotNull(ast);
        // Expecting at least 3 top-level structures
        assertTrue(ast.size() >= 3);
        
        // Check first statement: SinOsc s => dac;
        // In our current parser, this is a BlockStmt containing [Decl, ExpStmt]
        assertTrue(ast.get(0) instanceof ChuckAST.BlockStmt || ast.get(0) instanceof ChuckAST.ExpStmt);
        
        // Check while loop
        assertTrue(ast.stream().anyMatch(s -> s instanceof ChuckAST.WhileStmt));
    }
}
