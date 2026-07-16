package org.chuck.ide.dsp;

import static org.junit.jupiter.api.Assertions.*;

import javafx.application.Platform;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class FaustLiveCodingTabTest {

  @BeforeAll
  static void initJavaFX() {
    try {
      Platform.startup(() -> {});
    } catch (IllegalStateException ignored) {
      // Toolkit already started
    }
  }

  @Test
  void testFaustTabInitAndTemplateLoading() {
    ChuckVM vm = new ChuckVM(44100, 2);
    FaustLiveCodingTab tab = new FaustLiveCodingTab();
    tab.setVm(vm);

    assertNotNull(tab.getTop(), "Header toolbar should be created");
    assertNotNull(tab.getCenter(), "Center split view should be created");
  }
}
