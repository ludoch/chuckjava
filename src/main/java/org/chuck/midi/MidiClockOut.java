package org.chuck.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** High-precision MIDI Clock generator. Sends 24ppq clock messages to selected outputs. */
public class MidiClockOut {
  private final List<MidiOut> outputs = new ArrayList<>();
  private float bpm = 120.0f;
  private boolean running = false;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "MidiClock-Scheduler");
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
          });
  private ScheduledFuture<?> clockTask;

  public void addOutput(MidiOut out) {
    if (!outputs.contains(out)) outputs.add(out);
  }

  public void removeOutput(MidiOut out) {
    outputs.remove(out);
  }

  public void setBpm(float bpm) {
    this.bpm = Math.max(20.0f, Math.min(300.0f, bpm));
    if (running) {
      stop();
      start();
    }
  }

  public float getBpm() {
    return bpm;
  }

  public void start() {
    if (running) return;

    // Send Start message (0xFA)
    sendRealtime(0xFA);

    long intervalNanos = (long) (60_000_000_000L / (bpm * 24));
    clockTask = scheduler.scheduleAtFixedRate(this::tick, 0, intervalNanos, TimeUnit.NANOSECONDS);
    running = true;
  }

  public void stop() {
    if (!running) return;
    if (clockTask != null) {
      clockTask.cancel(false);
      clockTask = null;
    }
    // Send Stop message (0xFC)
    sendRealtime(0xFC);
    running = false;
  }

  public boolean isRunning() {
    return running;
  }

  private void tick() {
    // Send Clock message (0xF8)
    sendRealtime(0xF8);
  }

  private void sendRealtime(int status) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = status;
    msg.data2 = 0;
    msg.data3 = 0;
    msg.setData(new byte[] {(byte) status});

    for (MidiOut out : outputs) {
      out.send(msg);
    }
  }
}
