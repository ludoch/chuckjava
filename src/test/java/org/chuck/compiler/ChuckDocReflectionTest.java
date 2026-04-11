package org.chuck.compiler;

import org.chuck.core.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChuckDocReflectionTest {

    private List<String> runAndGetOutput(String source) throws InterruptedException {
        ChuckVM vm = new ChuckVM(44100);
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        vm.addPrintListener(output::add);
        vm.run(source, "test");
        vm.advanceTime(100);
        return output;
    }

    @Test
    public void testClassDocReflection() throws InterruptedException {
        String src = 
            "/** My Foo Class */\n" +
            "class Foo {}\n" +
            "new Foo @=> Foo f;\n" +
            "<<< Reflect.doc(f) >>>;";
        List<String> out = runAndGetOutput(src);
        assertFalse(out.isEmpty());
        assertEquals("My Foo Class", out.get(0).trim());
    }

    @Test
    public void testMethodDocReflection() throws InterruptedException {
        String src = 
            "class Bar {\n" +
            "    /** My Method */\n" +
            "    fun void hello() {}\n" +
            "}\n" +
            "new Bar @=> Bar b;\n" +
            "<<< Reflect.doc(b, \"hello\") >>>;";
        List<String> out = runAndGetOutput(src);
        assertFalse(out.isEmpty());
        assertEquals("My Method", out.get(0).trim());
    }

    @Test
    public void testGlobalVarDocReflection() throws InterruptedException {
        String src = 
            "/** My Global */\n" +
            "int x;\n" +
            "<<< Reflect.docGlobal(\"x\") >>>;";
        List<String> out = runAndGetOutput(src);
        assertFalse(out.isEmpty());
        assertEquals("My Global", out.get(0).trim());
    }

    @Test
    public void testGlobalFuncDocReflection() throws InterruptedException {
        String src = 
            "/** My Global Func */\n" +
            "fun void testFunc() {}\n" +
            "<<< Reflect.docFunc(\"testFunc\") >>>;";
        List<String> out = runAndGetOutput(src);
        assertFalse(out.isEmpty());
        assertEquals("My Global Func", out.get(0).trim());
    }

    @Test
    public void testAtDocDirectiveReflection() throws InterruptedException {
        String src = 
            "@doc \"Directly documented\"\n" +
            "int y;\n" +
            "<<< Reflect.docGlobal(\"y\") >>>;";
        List<String> out = runAndGetOutput(src);
        assertFalse(out.isEmpty());
        assertEquals("Directly documented", out.get(0).trim());
    }

    @Test
    public void testAtDocOnMemberReflection() throws InterruptedException {
        String src = 
            "class Baz {\n" +
            "    @doc \"Member doc\"\n" +
            "    int m;\n" +
            "}\n" +
            "new Baz @=> Baz b;\n" +
            "<<< Reflect.doc(b, \"m\") >>>;";
        List<String> out = runAndGetOutput(src);
        assertFalse(out.isEmpty());
        assertEquals("Member doc", out.get(0).trim());
    }
}
