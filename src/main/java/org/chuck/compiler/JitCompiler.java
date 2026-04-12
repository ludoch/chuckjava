package org.chuck.compiler;

import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckInstr;
import org.chuck.core.instr.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Translates ChucK bytecode into native Java JVM bytecode using ASM. */
public class JitCompiler implements Opcodes {

  public static class JitClassLoader extends ClassLoader {
    public JitClassLoader(ClassLoader parent) {
      super(parent);
    }

    public Class<?> defineClass(String name, byte[] b) {
      return defineClass(name, b, 0, b.length);
    }
  }

  public static long getArrayInt(org.chuck.core.ChuckArray a, long idx) {
    return a.getInt(a.resolveIndex(idx));
  }

  public static void setArrayInt(org.chuck.core.ChuckArray a, long idx, long val) {
    a.setInt(a.resolveIndex(idx), val);
  }

  public static Class<?> compile(ChuckCode code, String className) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String internalName = "org/chuck/jit/" + className;
    cw.visit(
        V25,
        ACC_PUBLIC | ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        new String[] {"org/chuck/compiler/JitExecutable"});

    // Empty constructor
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    mv.visitCode();
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();

    // execute(ChuckVM vm, ChuckShred shred)
    mv =
        cw.visitMethod(
            ACC_PUBLIC,
            "execute",
            "(Lorg/chuck/core/ChuckVM;Lorg/chuck/core/ChuckShred;)V",
            null,
            null);
    mv.visitCode();

    java.util.List<ChuckInstr> instrs = code.getInstructions();
    Label[] labels = new Label[instrs.size() + 1];
    for (int i = 0; i < labels.length; i++) labels[i] = new Label();

    // Store initial code reference for Fallback calls
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(
        INVOKEVIRTUAL,
        "org/chuck/core/ChuckShred",
        "getCode",
        "()Lorg/chuck/core/ChuckCode;",
        false);
    mv.visitVarInsn(ASTORE, 3);

    for (int i = 0; i < instrs.size(); i++) {
      mv.visitLabel(labels[i]);

      // shred.pc = i
      mv.visitVarInsn(ALOAD, 2);
      mv.visitLdcInsn(i);
      mv.visitFieldInsn(PUTFIELD, "org/chuck/core/ChuckShred", "pc", "I");

      ChuckInstr instr = instrs.get(i);
      if (instr == null) continue;

      // Specialization for common instructions
      if (instr instanceof PushInstrs.PushInt p) {
        mv.visitVarInsn(ALOAD, 2);
        mv.visitFieldInsn(
            GETFIELD, "org/chuck/core/ChuckShred", "reg", "Lorg/chuck/core/ChuckStack;");
        mv.visitLdcInsn(p.getVal());
        mv.visitMethodInsn(INVOKEVIRTUAL, "org/chuck/core/ChuckStack", "push", "(J)V", false);
      } else {
        // Generic call: instr.execute(vm, shred)
        mv.visitVarInsn(ALOAD, 3); // initialCode
        mv.visitLdcInsn(i);
        mv.visitMethodInsn(
            INVOKEVIRTUAL,
            "org/chuck/core/ChuckCode",
            "getInstruction",
            "(I)Lorg/chuck/core/ChuckInstr;",
            false);
        mv.visitVarInsn(ALOAD, 1); // vm
        mv.visitVarInsn(ALOAD, 2); // shred
        mv.visitMethodInsn(
            INVOKEINTERFACE,
            "org/chuck/core/ChuckInstr",
            "execute",
            "(Lorg/chuck/core/ChuckVM;Lorg/chuck/core/ChuckShred;)V",
            true);
      }

      // Check if PC or Code changed (terminal for JIT fragment)
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETFIELD, "org/chuck/core/ChuckShred", "pc", "I");
      mv.visitLdcInsn(i);
      mv.visitJumpInsn(IF_ICMPNE, labels[instrs.size()]);

      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "org/chuck/core/ChuckShred",
          "getCode",
          "()Lorg/chuck/core/ChuckCode;",
          false);
      mv.visitVarInsn(ALOAD, 3); // initialCode
      mv.visitJumpInsn(IF_ACMPNE, labels[instrs.size()]);

      // Check if yielded or done
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETFIELD, "org/chuck/core/ChuckShred", "isDone", "Z");
      mv.visitJumpInsn(IFNE, labels[instrs.size()]);

      // Advance PC manually for next instruction within JIT fragment
      mv.visitVarInsn(ALOAD, 2);
      mv.visitLdcInsn(i + 1);
      mv.visitFieldInsn(PUTFIELD, "org/chuck/core/ChuckShred", "pc", "I");
    }

    mv.visitLabel(labels[instrs.size()]);
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 0);
    mv.visitEnd();

    cw.visitEnd();
    byte[] classBytes = cw.toByteArray();

    JitClassLoader loader = new JitClassLoader(JitCompiler.class.getClassLoader());
    return loader.defineClass("org.chuck.jit." + className, classBytes);
  }
}
