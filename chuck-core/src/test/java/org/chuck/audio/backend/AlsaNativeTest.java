package org.chuck.audio.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link AlsaNative} resolves libasound and its device-hint enumeration round-trips.
 * Self-skips on any platform/environment without a usable ALSA install (macOS, Windows, a container
 * without alsa-lib) rather than failing — {@link AlsaNative#AVAILABLE} is the same gate {@link
 * AlsaBackend#isAvailable()} uses in production.
 */
public class AlsaNativeTest {

  @Test
  public void testAvailabilityFlagDoesNotThrow() {
    // Merely referencing AVAILABLE must never throw, on any platform - this is the whole point
    // of the try/catch in AlsaNative's static initializer.
    boolean available = AlsaNative.AVAILABLE;
    System.out.println("[AlsaNativeTest] AlsaNative.AVAILABLE=" + available);
  }

  @Test
  public void testDeviceNameHintRoundTrip() throws Throwable {
    assumeTrue(AlsaNative.AVAILABLE, "libasound.so.2 not available on this platform");

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment hintsPtr = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment iface = arena.allocateFrom("pcm");
      int rc = (int) AlsaNative.snd_device_name_hint.invokeExact(-1, iface, hintsPtr);
      assertEquals(0, rc, "snd_device_name_hint failed: " + AlsaNative.strerror(rc));

      MemorySegment hints = hintsPtr.get(ValueLayout.ADDRESS, 0);
      assertNotNull(hints);

      int count = 0;
      MemorySegment idName = arena.allocateFrom("NAME");
      for (long i = 0; ; i++) {
        MemorySegment hint =
            hints
                .reinterpret((i + 1) * ValueLayout.ADDRESS.byteSize())
                .get(ValueLayout.ADDRESS, i * ValueLayout.ADDRESS.byteSize());
        if (hint == null || hint.address() == 0) break;
        MemorySegment namePtr =
            (MemorySegment) AlsaNative.snd_device_name_get_hint.invokeExact(hint, idName);
        String name = AlsaNative.readCString(namePtr);
        if (namePtr != null && namePtr.address() != 0) {
          AlsaNative.libc_free.invokeExact(namePtr);
        }
        if (name != null) count++;
      }
      int freeRc = (int) AlsaNative.snd_device_name_free_hint.invokeExact(hints);
      assertEquals(0, freeRc);

      System.out.println("[AlsaNativeTest] Found " + count + " ALSA PCM device hint(s)");
      assertTrue(count >= 0);
    }
  }
}
