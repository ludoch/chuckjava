import org.chuck.core.ChuckVM;

public class Test77 {
  public static void main(String[] args) throws Exception {
    String source = java.nio.file.Files.readString(java.nio.file.Paths.get("test/05-Global/77.ck"));
    System.out.println("Path used: test\05-Global\77.ck");
    ChuckVM vm = new ChuckVM(44100);
    StringBuilder output = new StringBuilder();
    vm.addPrintListener(
        text -> {
          output.append(text);
          System.out.print("[PRINT] " + text);
        });
    int shredId = vm.run(source, "test\05-Global\77.ck");
    System.out.println("ShredId=" + shredId);
    for (int i = 0; i < 30 && vm.getActiveShredCount() > 0; i++) {
      vm.advanceTime(4410);
      Thread.sleep(1);
    }
    System.out.println("Final: " + output.toString().stripTrailing());
  }
}
