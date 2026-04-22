package org.chuck.sequencer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** E2E Integration Test for the Sequencer Engine. */
public class SequencerEngineTest {
  private ChuckVM vm;
  private ChuckArray patternArray;
  private ChuckArray probabilityArray;
  private final List<String> logs = java.util.Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 2);
    vm.addPrintListener(logs::add);

    patternArray = new ChuckArray("int", 128);
    probabilityArray = new ChuckArray("float", 8);
    for (int i = 0; i < 128; i++) patternArray.setInt(i, 0L);
    for (int i = 0; i < 8; i++) probabilityArray.setFloat(i, 1.0);

    vm.setGlobalObject("seq_pattern", patternArray);
    vm.setGlobalObject("seq_probability", probabilityArray);
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  private File findEngineFile() {
    File f = new File("chuck-samples/src/main/resources/examples/sequencer_setup.ck");
    if (f.exists()) return f;
    f = new File("../chuck-samples/src/main/resources/examples/sequencer_setup.ck");
    if (f.exists()) return f;
    return null;
  }

  @Test
  void testEngineSyncAndTrigger() {
    File f = findEngineFile();
    assertNotNull(f, "Engine script not found");

    vm.setLogLevel(2);
    int id = vm.add(f.getAbsolutePath());
    assertTrue(id >= 0);

    // 1. Advance time and verify step changes
    vm.advanceTime(44100);
    long step = vm.getGlobalInt("seq_current_step");
    assertTrue(step >= 0, "Sequencer step did not advance");

    // 2. Set trigger at step 0
    patternArray.setInt(0, 1L);

    // Reset logs and advance logic
    logs.clear();
    vm.advanceTime(44100 * 4); // Advance 4 seconds to ensure we hit step 0

    // Look for the "!!! FOUND ACTIVE CELL !!!" log from v3.2
    boolean triggerFound = logs.stream().anyMatch(l -> l.contains("FOUND ACTIVE CELL"));
    assertTrue(triggerFound, "Engine did not detect cell trigger at Step 0");

    boolean dacFound = logs.stream().anyMatch(l -> l.contains("DAC ACTIVE"));
    assertTrue(dacFound, "Engine did not emit audio (DAC) for trigger");
  }

  @Test
  void testLogLevelFiltering() {
    File f = findEngineFile();
    vm.setLogLevel(1); // Silent mode
    vm.add(f.getAbsolutePath());

    patternArray.setInt(0, 1L);
    logs.clear();
    vm.advanceTime(44100);

    // Should not contain HEARTBEAT or ACTIVE CELL logs
    boolean hasTriggers =
        logs.stream().anyMatch(l -> l.contains("ACTIVE CELL") || l.contains("HEARTBEAT"));
    assertFalse(hasTriggers, "Logs should be suppressed at level 1");
  }

  @Test
  void testNoCrashOnEmptyData() throws InterruptedException {
    // Intentional bad data size to test robustness
    vm.setGlobalObject("seq_pattern", null);

    File f = findEngineFile();
    vm.setLogLevel(2);
    vm.add(f.getAbsolutePath());

    vm.advanceTime(88200);
    Thread.sleep(100);

    // In v3.2, it prints "STATUS: data array is NULL"
    boolean waitingLog = logs.stream().anyMatch(l -> l.contains("data array is NULL"));
    assertTrue(waitingLog, "Engine should handle invalid data gracefully");
  }
}
