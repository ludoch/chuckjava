package org.chuck.core;

import org.chuck.audio.Adc;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.Blackhole;
import java.util.concurrent.atomic.AtomicLong;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Virtual Machine is responsible for managing timing, shreds,
 * and sample-accurate execution.
 */
public class ChuckVM {
    
    // Global logical time in samples
    private final AtomicLong currentTime = new AtomicLong(0);
    
    // Shreduler queue: orders shreds by wake-up time
    private final PriorityQueue<ChuckShred> shreduler = new PriorityQueue<>();
    private final Map<Integer, ChuckShred> activeShreds = new ConcurrentHashMap<>();
    private final ReentrantLock schedulerLock = new ReentrantLock();
    
    // Audio configuration
    private final int sampleRate;
    
    // Global variables
    private final Map<String, Long> globalInts = new ConcurrentHashMap<>();
    private final Map<String, ChuckObject> globalObjects = new ConcurrentHashMap<>();

    // Multi-channel output (dac)
    private final int numChannels = 2;
    private final ChuckUGen[] dacChannels = new ChuckUGen[numChannels];
    public final ChuckObject dac;
    public final Blackhole blackhole;
    public final Adc adc;

    public ChuckVM(int sampleRate) {
        this.sampleRate = sampleRate;
        
        // Initialize dac channels as virtual summing nodes
        for (int i = 0; i < numChannels; i++) {
            dacChannels[i] = new ChuckUGen() {
                @Override protected float compute(float input) { return input; }
            };
        }
        
        this.dac = new ChuckObject(new ChuckType("dac", ChuckType.OBJECT, 0, 0));
        globalObjects.put("dac", dac);

        this.blackhole = new Blackhole();
        globalObjects.put("blackhole", blackhole);

        this.adc = new Adc();
        globalObjects.put("adc", adc);
    }

    public ChuckUGen getDacChannel(int channel) {
        return dacChannels[channel % numChannels];
    }

    public float getChannelLastOut(int channel) {
        return dacChannels[channel % numChannels].getLastOut();
    }

    public void setGlobalInt(String name, long value) {
        globalInts.put(name, value);
    }

    public long getGlobalInt(String name) {
        return globalInts.getOrDefault(name, 0L);
    }

    public void setGlobalObject(String name, ChuckObject obj) {
        globalObjects.put(name, obj);
    }

    public ChuckObject getGlobalObject(String name) {
        return globalObjects.get(name);
    }
    
    public long getCurrentTime() {
        return currentTime.get();
    }
    
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Schedules a shred to wake up based on its wakeTime.
     */
    public void schedule(ChuckShred shred) {
        schedulerLock.lock();
        try {
            shreduler.offer(shred);
        } finally {
            schedulerLock.unlock();
        }
    }

    /**
     * Spawns a new shred and starts its Virtual Thread.
     */
    public void spork(ChuckShred shred) {
        schedulerLock.lock();
        try {
            shred.setWakeTime(currentTime.get());
            shreduler.offer(shred);
            activeShreds.put(shred.getId(), shred);
        } finally {
            schedulerLock.unlock();
        }
        
        Thread.ofVirtual().name("Shred-" + shred.getId()).start(() -> {
            try {
                shred.execute(this);
            } finally {
                activeShreds.remove(shred.getId());
            }
        });
    }

    public void removeShred(int id) {
        ChuckShred shred = activeShreds.get(id);
        if (shred != null) {
            shred.abort();
            activeShreds.remove(id);
        }
    }

    public void clear() {
        activeShreds.values().forEach(ChuckShred::abort);
        activeShreds.clear();
        schedulerLock.lock();
        try {
            shreduler.clear();
        } finally {
            schedulerLock.unlock();
        }
        globalObjects.clear();
        globalInts.clear();
        // Disconnect all UGens from the audio graph so sound stops immediately
        for (int i = 0; i < numChannels; i++) dacChannels[i].clearSources();
        blackhole.clearSources();

        // Re-register defaults
        globalObjects.put("dac", dac);
        globalObjects.put("blackhole", blackhole);
        globalObjects.put("adc", adc);
    }
    
    /**
     * Advance logical time by computing audio samples and waking shreds.
     */
    public void advanceTime(int samplesToCompute) {
        for (int s = 0; s < samplesToCompute; s++) {
            long now = currentTime.get();
            
            while (true) {
                ChuckShred nextShred = null;
                
                schedulerLock.lock();
                try {
                    if (!shreduler.isEmpty() && shreduler.peek().getWakeTime() <= now) {
                        nextShred = shreduler.poll();
                    }
                } finally {
                    schedulerLock.unlock();
                }

                if (nextShred == null) break;

                nextShred.resume(this);
                
                if (!nextShred.isDone() && !nextShred.isWaiting()) {
                    schedule(nextShred);
                }
            }
            
            // DRIVE THE UGEN GRAPH: Pull samples through the dac channels
            for (int i = 0; i < numChannels; i++) {
                dacChannels[i].tick(now);
            }
            // Also tick blackhole
            blackhole.tick(now);
            
            currentTime.incrementAndGet();
        }
    }
}
