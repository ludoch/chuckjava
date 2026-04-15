package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Quick probe to identify missing array/class API features. */
public class ArrayApiProbeTest {

  private String run(String code) {
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder out = new StringBuilder();
    vm.addPrintListener(out::append);
    try {
      vm.run(code, "test");
      vm.advanceTime(10);
    } catch (Exception e) {
      return "EXCEPTION: " + e.getMessage();
    }
    return out.toString().trim();
  }

  @Test
  void probe_append() {
    String r = run("int a[0]; a << 1 << 2 << 3; <<< a.size() >>>;");
    System.out.println("[append] " + r);
    assertTrue(r.contains("3"), "append << : " + r);
  }

  @Test
  void probe_popBack() {
    String r = run("int a[0]; a << 1 << 2; a.popBack(); <<< a.size() >>>;");
    System.out.println("[popBack] " + r);
    assertTrue(r.contains("1"), "popBack: " + r);
  }

  @Test
  void probe_popFront() {
    String r = run("int a[0]; a << 1 << 2; a.popFront(); <<< a.size() >>>;");
    System.out.println("[popFront] " + r);
    assertTrue(r.contains("1"), "popFront: " + r);
  }

  @Test
  void probe_popOut() {
    String r = run("int a[0]; a << 1 << 2 << 3; a.popOut(1); <<< a.size() >>>;");
    System.out.println("[popOut] " + r);
    assertTrue(r.contains("2"), "popOut: " + r);
  }

  @Test
  void probe_erase1() {
    String r = run("int a[0]; a << 1 << 2 << 3; a.erase(1); <<< a.size() >>>;");
    System.out.println("[erase1] " + r);
    assertTrue(r.contains("2"), "erase(n): " + r);
  }

  @Test
  void probe_erase2() {
    String r = run("int a[0]; a << 1 << 2 << 3 << 4 << 5; a.erase(1, 3); <<< a.size() >>>;");
    System.out.println("[erase2] " + r);
    assertTrue(r.contains("3"), "erase(begin,end): " + r);
  }

  @Test
  void probe_getKeys() {
    String r =
        run(
            "float f[4]; 2.5 => f[\"x\"]; 3.0 => f[\"y\"];\n"
                + "string k[0]; f.getKeys(k); <<< k.size() >>>;");
    System.out.println("[getKeys] " + r);
    assertTrue(r.contains("2"), "getKeys: " + r);
  }

  @Test
  void probe_isInMap() {
    String r = run("float f[4]; 2.5 => f[\"x\"]; <<< f.isInMap(\"x\"), f.isInMap(\"y\") >>>;");
    System.out.println("[isInMap] " + r);
    assertTrue(r.contains("1") && r.contains("0"), "isInMap: " + r);
  }

  @Test
  void probe_forEachAuto() {
    String r = run("int a[3]; 10 => a[0]; 20 => a[1]; 30 => a[2]; for(auto x : a) <<< x >>>;");
    System.out.println("[forAuto] " + r);
    assertTrue(r.contains("10") && r.contains("20") && r.contains("30"), "for-auto: " + r);
  }

  @Test
  void probe_constructorArg() {
    String r =
        run("class Foo { int n; fun @construct(int x){ x => n; } }\n" + "Foo f(7); <<< f.n >>>;");
    System.out.println("[ctorArg] " + r);
    assertTrue(r.contains("7"), "ctor with arg: " + r);
  }

  @Test
  void probe_newAtRef() {
    String r = run("class Bar { int v; } new Bar @=> Bar @ b; 5 => b.v; <<< b.v >>>;");
    System.out.println("[newAtRef] " + r);
    assertTrue(r.contains("5"), "new @=> Foo @ b: " + r);
  }

  @Test
  void probe_staticVar() {
    String r = run("class S { 42 => static int X; } <<< S.X >>>;");
    System.out.println("[staticVar] " + r);
    assertTrue(r.contains("42"), "static int: " + r);
  }

  @Test
  void probe_multiDimLiteral() {
    String r = run("[ [1,2], [3,4] ] @=> int c[][]; <<< c[0][1], c[1][0] >>>;");
    System.out.println("[mdimLit] " + r);
    assertTrue(r.contains("2") && r.contains("3"), "2D literal init: " + r);
  }
}
