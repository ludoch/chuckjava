package org.chuck.core;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * ChucK KBHit — non-blocking keyboard press detection.
 * A background virtual thread reads System.in and queues keypresses.
 * Usage in ChucK:
 *   KBHit kb;
 *   while (true) {
 *       kb => now;          // wait for a key
 *       while (kb.kbhit()) {
 *           kb.getchar() => int ch;
 *           <<< "key:", ch >>>;
 *       }
 *   }
 */
public class KBHit extends ChuckObject {
    private static final ConcurrentLinkedDeque<Integer> queue = new ConcurrentLinkedDeque<>();
    private static volatile boolean listenerStarted = false;

    public KBHit() {
        super(ChuckType.OBJECT);
        startListener();
    }

    private static synchronized void startListener() {
        if (listenerStarted) return;
        listenerStarted = true;
        Thread.ofVirtual().name("kbhit-listener").start(() -> {
            try {
                while (true) {
                    if (System.in.available() > 0) {
                        int c = System.in.read();
                        if (c < 0) break;
                        queue.addLast(c);
                    } else {
                        Thread.sleep(10);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    /** Returns 1 if one or more keys are waiting, 0 otherwise. */
    public long kbhit() { return queue.isEmpty() ? 0L : 1L; }

    /** Alias for kbhit(). */
    public long hit() { return kbhit(); }

    /**
     * Return and consume the next pending keypress as its ASCII/Unicode code point,
     * or -1 if no key is waiting.
     */
    public long getchar() {
        Integer c = queue.pollFirst();
        return c != null ? (long) c : -1L;
    }

    /** Always returns 1. */
    public long can_wait() { return 1L; }
}
