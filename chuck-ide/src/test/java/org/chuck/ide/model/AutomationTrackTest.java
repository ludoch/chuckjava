package org.chuck.ide.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public class AutomationTrackTest {

  @Test
  void testAddPointAndSort() {
    AutomationTrack track = new AutomationTrack();
    track.setLoopDurationSamples(1000.0);
    track.addPoint(500.0, 0.5);
    track.addPoint(100.0, 0.1);
    track.addPoint(800.0, 0.8);

    List<AutomationTrack.Breakpoint> pts = track.getPoints();
    assertEquals(3, pts.size(), "Should have 3 sorted points");
    assertEquals(100.0, pts.get(0).timeSamples(), 0.001);
    assertEquals(500.0, pts.get(1).timeSamples(), 0.001);
    assertEquals(800.0, pts.get(2).timeSamples(), 0.001);
  }

  @Test
  void testLinearInterpolationAndLooping() {
    AutomationTrack track = new AutomationTrack();
    track.setLoopDurationSamples(1000.0);
    track.setLooping(true);
    track.addPoint(0.0, 0.0);
    track.addPoint(500.0, 1.0);

    // Halfway between 0 and 500 should be 0.5
    assertEquals(0.5, track.evaluate(250.0, 0.0), 0.001);
    // At exactly 500 should be 1.0
    assertEquals(1.0, track.evaluate(500.0, 0.0), 0.001);
    // Because loop is 1000 and last point is 500(val=1.0) and first is 0(val=0.0),
    // at time 750 (halfway between 500 and 1000/0) it should interpolate 0.5
    assertEquals(0.5, track.evaluate(750.0, 0.0), 0.001);
    // Looped time beyond 1000 (e.g. 1250 = 250) should be 0.5
    assertEquals(0.5, track.evaluate(1250.0, 0.0), 0.001);
  }

  @Test
  void testPresetGenerators() {
    AutomationTrack track = new AutomationTrack();
    track.setLoopDurationSamples(44100.0);
    track.generateSineLFO(0.0, 1.0, 1.0);
    assertFalse(track.getPoints().isEmpty(), "Sine LFO should generate breakpoints");

    track.generateRampUp(10.0, 20.0);
    assertEquals(2, track.getPoints().size());
    assertEquals(10.0, track.getPoints().get(0).value(), 0.001);
  }
}
