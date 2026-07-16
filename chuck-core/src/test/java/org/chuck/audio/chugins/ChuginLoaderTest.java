package org.chuck.audio.chugins;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.ChuckVM;
import org.chuck.core.UGenRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ChuginLoaderTest {

  @TempDir File tempDir;

  @Test
  void testBuiltinChuginRegistrationOnVMInit() {
    new ChuckVM(44100, 2);

    assertTrue(UGenRegistry.isRegistered("Bitcrusher"), "Bitcrusher chugin must be registered");
    assertTrue(
        UGenRegistry.isRegistered("FoldbackSaturator"),
        "FoldbackSaturator chugin must be registered");
    assertTrue(UGenRegistry.isRegistered("KasFilter"), "KasFilter chugin must be registered");
    assertTrue(
        UGenRegistry.isRegistered("WPDiodeLadder"), "WPDiodeLadder chugin must be registered");

    ChuckUGen crusher = UGenRegistry.instantiate("Bitcrusher", 44100.0f, null);
    assertNotNull(crusher, "Should instantiate Bitcrusher cleanly from UGenRegistry");
    assertTrue(crusher instanceof Bitcrusher);
  }

  @Test
  void testChuginLoaderDirectoryScan() throws IOException {
    File fakeChug = new File(tempDir, "TestChugin.chug");
    Files.writeString(fakeChug.toPath(), "dummy binary data");

    // Scanning directory should not throw any exceptions on non-FFM/dummy binaries
    int loaded = ChuginLoader.loadChuginsFromPaths(List.of(tempDir.getAbsolutePath()));
    assertTrue(loaded >= 0);
  }
}
