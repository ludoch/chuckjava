package org.chuck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Global pool of Shred-Local Linear Allocators. Provides unsynchronized object reuse during a
 * shred's lifetime, and recycles the entire block of objects when the shred terminates.
 */
public class ChuckObjectPool {

  public static class ShredAllocator {
    private static final int BLOCK_SIZE = 1024;

    private final List<ChuckDuration[]> durBlocks = new ArrayList<>();
    private final List<ChuckString[]> strBlocks = new ArrayList<>();

    private int durBlockIdx = 0;
    private int durIdx = BLOCK_SIZE; // force alloc on first use

    private int strBlockIdx = 0;
    private int strIdx = BLOCK_SIZE;

    public ChuckDuration allocDuration(double samples) {
      if (durIdx >= BLOCK_SIZE) {
        if (durBlockIdx >= durBlocks.size()) {
          ChuckDuration[] block = new ChuckDuration[BLOCK_SIZE];
          for (int i = 0; i < BLOCK_SIZE; i++) {
            block[i] = new ChuckDuration(0);
          }
          durBlocks.add(block);
        }
        durBlockIdx++;
        durIdx = 0;
      }
      ChuckDuration d = durBlocks.get(durBlockIdx - 1)[durIdx++];
      d.setSamples(samples);
      return d;
    }

    public ChuckString allocString(String val) {
      return new ChuckString(val);
    }

    public void reset() {
      durBlockIdx = 0;
      durIdx = BLOCK_SIZE;
      strBlockIdx = 0;
      strIdx = BLOCK_SIZE;
    }
  }

  private static final ConcurrentLinkedQueue<ShredAllocator> allocatorPool =
      new ConcurrentLinkedQueue<>();

  public static ShredAllocator acquireAllocator() {
    ShredAllocator alloc = allocatorPool.poll();
    if (alloc == null) {
      alloc = new ShredAllocator();
    }
    return alloc;
  }

  public static void releaseAllocator(ShredAllocator alloc) {
    if (alloc != null) {
      alloc.reset();
      allocatorPool.offer(alloc);
    }
  }

  // Legacy static methods, delegates to current shred if bound
  public static ChuckDuration getDuration(double samples) {
    if (ChuckShred.CURRENT_SHRED.isBound()) {
      return ChuckShred.CURRENT_SHRED.get().getAllocator().allocDuration(samples);
    }
    return new ChuckDuration(samples);
  }

  private static final java.util.concurrent.ConcurrentHashMap<String, ChuckString> STRING_INTERN_MAP = new java.util.concurrent.ConcurrentHashMap<>();

  public static ChuckString getString(String val) {
    if (val == null) val = "";
    return STRING_INTERN_MAP.computeIfAbsent(val, ChuckString::new);
  }

  public static void releaseDuration(ChuckDuration d) {
    // No-op, managed by ShredAllocator
  }

  public static void releaseString(ChuckString s) {
    // No-op, managed by ShredAllocator
  }
}
