package org.chuck.audio.osc;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import org.junit.jupiter.api.Test;

public class DownloadImageTest {

  @Test
  public void testDownload() {
    String spec = "https://forums.synthstrom.com/uploads/editor/ck/2tzng73w68mz.png";
    String dest =
        "/Users/ludo/.gemini/jetski/brain/9841caa1-98ab-40d0-a511-4269ef745639/deluge_shortcuts.png";
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
