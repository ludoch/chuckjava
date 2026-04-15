package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public class TempExampleTest {
  String run(String file) {
    try {
      String code = new String(Files.readAllBytes(Paths.get(file)));
      ChuckVM vm = new ChuckVM(44100);
      StringBuilder out = new StringBuilder();
      vm.addPrintListener(out::append);
      vm.addPrintListener(s -> System.err.print("[vm] " + s));
      try {
        vm.run(code, file);
        vm.advanceTime(100);
      } catch (Exception e) {
        e.printStackTrace();
        return "ERR: " + e.getMessage();
      }
      String result = out.toString().trim();
      return result.isEmpty() ? "(no output)" : result;
    } catch (Exception e) {
      e.printStackTrace();
      return "FILE_ERR: " + e.getMessage();
    }
  }

  @Test
  void ctors_dtor() {
    System.out.println("[ctors-dtor]\n" + run("examples/class/ctors-dtor.ck"));
  }

  @Test
  void destructor() {
    System.out.println("[destructor]\n" + run("examples/class/destructor.ck"));
  }

  @Test
  void super1() {
    System.out.println("[super-1]\n" + run("examples/class/super-1-simple.ck"));
  }

  @Test
  void super2() {
    System.out.println("[super-2]\n" + run("examples/class/super-2-skiplevel.ck"));
  }

  @Test
  void super3() {
    System.out.println("[super-3]\n" + run("examples/class/super-3-singlelevel.ck"));
  }

  @Test
  void super4() {
    System.out.println("[super-4]\n" + run("examples/class/super-4-multilevel.ck"));
  }

  @Test
  void static_init() {
    System.out.println("[static-init]\n" + run("examples/class/static-init.ck"));
  }
}
