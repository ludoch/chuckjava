package org.chuck.audio.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Pure-Java correctness coverage for {@link SpscRingBuffer} — no native/platform dependency, so
 * unlike the JACK/CoreAudio/WASAPI backends that consume it, this runs (and is fully verified)
 * everywhere.
 */
public class SpscRingBufferTest {

  @Test
  public void testBasicWriteReadRoundTrip() {
    SpscRingBuffer ring = new SpscRingBuffer(16, 2);
    float[] src = {1f, 2f, 3f, 4f, 5f, 6f}; // 3 stereo frames
    int written = ring.write(src, 0, 3);
    assertEquals(3, written);
    assertEquals(3, ring.framesAvailable());

    float[] dst = new float[6];
    int read = ring.read(dst, 0, 3);
    assertEquals(3, read);
    assertArrayEquals(src, dst, 0f);
    assertEquals(0, ring.framesAvailable());
  }

  @Test
  public void testUnderrunZeroFillsMissingFrames() {
    SpscRingBuffer ring = new SpscRingBuffer(16, 2);
    float[] src = {1f, 1f, 2f, 2f}; // 2 stereo frames available
    ring.write(src, 0, 2);

    float[] dst = new float[10]; // request 5 frames, only 2 available
    java.util.Arrays.fill(dst, -9f); // sentinel to prove zero-fill actually happens
    int read = ring.read(dst, 0, 5);

    assertEquals(2, read, "should report only the genuinely-available frames as read");
    assertArrayEquals(new float[] {1f, 1f, 2f, 2f}, java.util.Arrays.copyOfRange(dst, 0, 4), 0f);
    assertArrayEquals(
        new float[] {0f, 0f, 0f, 0f, 0f, 0f},
        java.util.Arrays.copyOfRange(dst, 4, 10),
        0f,
        "missing frames must be zero-filled, not left as garbage/sentinel");
  }

  @Test
  public void testOverflowReturnsPartialWriteWithoutCorruption() {
    SpscRingBuffer ring = new SpscRingBuffer(4, 1); // tiny ring, capacity 4 frames
    float[] src = {1f, 2f, 3f, 4f, 5f, 6f}; // 6 frames requested, only 4 fit
    int written = ring.write(src, 0, 6);
    assertEquals(4, written, "write must not exceed capacity when nothing has been drained yet");

    float[] dst = new float[4];
    int read = ring.read(dst, 0, 4);
    assertEquals(4, read);
    assertArrayEquals(new float[] {1f, 2f, 3f, 4f}, dst, 0f);
  }

  @Test
  public void testConcurrentProducerConsumerNoCorruptionOrLoss() throws Exception {
    int channels = 2;
    int totalFrames = 200_000;
    SpscRingBuffer ring = new SpscRingBuffer(1024, channels);
    AtomicBoolean failed = new AtomicBoolean(false);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    CountDownLatch done = new CountDownLatch(2);

    // Producer writes an ascending counter pattern (frame i -> {i, i} for stereo) in small
    // random-ish chunks, mimicking writeOutput() being called once per audio buffer.
    Thread producer =
        new Thread(
            () -> {
              try {
                int chunk = 37; // deliberately not a divisor of the ring capacity
                float[] buf = new float[chunk * channels];
                int written = 0;
                while (written < totalFrames) {
                  int n = Math.min(chunk, totalFrames - written);
                  for (int i = 0; i < n; i++) {
                    for (int c = 0; c < channels; c++) {
                      buf[i * channels + c] = written + i;
                    }
                  }
                  int actual = 0;
                  while (actual == 0) {
                    actual = ring.write(buf, 0, n);
                    if (actual == 0) Thread.onSpinWait();
                  }
                  written += actual;
                }
              } catch (Throwable t) {
                failed.set(true);
                failure.set(t);
              } finally {
                done.countDown();
              }
            },
            "ring-producer");

    // Consumer reads in different-sized chunks, mimicking a native callback's own nframes cadence,
    // and verifies every frame's value matches the expected ascending counter with no gaps/dupes.
    Thread consumer =
        new Thread(
            () -> {
              try {
                int chunk = 61; // different size than the producer, on purpose
                float[] buf = new float[chunk * channels];
                long nextExpected = 0;
                while (nextExpected < totalFrames) {
                  int got = ring.read(buf, 0, chunk);
                  for (int i = 0; i < got; i++) {
                    float expected = nextExpected + i;
                    for (int c = 0; c < channels; c++) {
                      if (buf[i * channels + c] != expected) {
                        throw new AssertionError(
                            "corruption at frame "
                                + (nextExpected + i)
                                + " ch "
                                + c
                                + ": expected "
                                + expected
                                + " got "
                                + buf[i * channels + c]);
                      }
                    }
                  }
                  nextExpected += got;
                  if (got == 0) Thread.onSpinWait();
                }
              } catch (Throwable t) {
                failed.set(true);
                failure.set(t);
              } finally {
                done.countDown();
              }
            },
            "ring-consumer");

    producer.start();
    consumer.start();
    boolean finished = done.await(30, java.util.concurrent.TimeUnit.SECONDS);
    assertTrue(finished, "producer/consumer threads did not finish within timeout");
    if (failed.get()) {
      fail("concurrent producer/consumer test failed: " + failure.get(), failure.get());
    }
  }
}
