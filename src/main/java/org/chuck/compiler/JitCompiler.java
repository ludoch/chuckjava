package org.chuck.compiler;

import org.chuck.core.ChuckCode;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Foundation for translating ChucK bytecode into native Java JVM bytecode using ASM. This class
 * will eventually take a ChuckCode object and generate a dynamic Class implementing ChuckInstr or a
 * similar interface, allowing C2 to heavily optimize it.
 */
public class JitCompiler implements Opcodes {

  // A custom class loader to load our dynamically generated classes
  public static class JitClassLoader extends ClassLoader {
    public JitClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class<?> defineClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }

  /**
   * Compiles the given ChuckCode into a dynamically generated Java class. Currently, this is a
   * skeleton that generates a valid but empty "execute" method.
   *
   * @param code The ChucK bytecode sequence.
   * @param className The desired name of the generated class (e.g., "JitCode_1").
   * @return The dynamically loaded Class object.
   */
  public static Class<?> compile(ChuckCode code, String className) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    // public class [className] implements Runnable (or a custom JitExecutable interface)
    String internalName = "org/chuck/jit/" + className;
    cw.visit(
        V22,
        ACC_PUBLIC | ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        new String[] {"java/lang/Runnable"});

    // Empty constructor
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    // The execution method: public void run()
    // Eventually this will be: public void execute(ChuckVM vm, ChuckShred shred)
    mv = cw.visitMethod(ACC_PUBLIC, "run", "()V", null, null);
    mv.visitCode();

    // Future: iterate over code.getInstructions() and emit equivalent ASM
    // e.g., if (instr instanceof PushInt) { mv.visitLdcInsn(instr.getVal()); ... }

    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 1);
    mv.visitEnd();

    cw.visitEnd();
    byte[] classBytes = cw.toByteArray();

    JitClassLoader loader = new JitClassLoader(JitCompiler.class.getClassLoader());
    return loader.defineClass("org.chuck.jit." + className, classBytes);
  }
}
