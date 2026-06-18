package org.chuck.audio.osc;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DownloadImageTest {

  @Disabled("Requires external network connectivity which is not available in the sandbox")
  @Test
  public void testDownload() {
    String spec = "https://forums.synthstrom.com/uploads/editor/ck/2tzng73w68mz.png";
    String dest =
        java.nio.file.Path.of("target", "deluge_shortcuts.png").toAbsolutePath().toString();
    try (BufferedInputStream in = new BufferedInputStream(new URL(spec).openStream());
        FileOutputStream out = new FileOutputStream(dest)) {
      byte[] data = new byte[1024];
      int count;
      while ((count = in.read(data, 0, 1024)) != -1) {
        out.write(data, 0, count);
      }
      System.out.println("=== DOWNLOAD SUCCESSFUL ===");
    } catch (Exception e) {
      System.err.println("Download failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
