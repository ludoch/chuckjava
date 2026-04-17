# ChucK-Java: Fluent Java DSL Documentation

ChucK-Java provides a **Fluent Java DSL** that allows you to write ChucK-style audio code directly in pure Java. This DSL leverages modern Java features like **ScopedValues** (JEP 481) to maintain shred-local logical time and **Virtual Threads** (Project Loom) for lightweight concurrency.

## Core Concepts

In the Java DSL, you write standard Java classes that implement the `org.chuck.core.Shred` interface. These files can be loaded dynamically by the IDE or the CLI, or used as a library in your own Java applications.

### Key Syntax Differences

| Feature | ChucK (`.ck`) | Java DSL (`.java`) |
| :--- | :--- | :--- |
| **Imports** | Built-in | `import static org.chuck.core.ChuckDSL.*;` |
| **Connection** | `SinOsc s => dac;` | `s.chuck(dac());` |
| **Parameters** | `440 => s.freq;` | `s.freq(440);` |
| **Time Advancement** | `1::second => now;` | `advance(second(1));` |
| **Durations** | `100::ms` | `ms(100)` |
| **Current Time** | `now` | `now()` |
| **Shredding** | Script-based | Implement `Shred` interface |

---

## Educational Comparison

### 1. Hello World (Sine Wave)

**ChucK Version:**
```chuck
// Simple sine wave for 2 seconds
SinOsc s => dac;
440 => s.freq;
0.5 => s.gain;
2::second => now;
```

**Java DSL Version:**
```java
import static org.chuck.core.ChuckDSL.*;
import org.chuck.audio.osc.SinOsc;
import org.chuck.core.Shred;

public class MyShred implements Shred {
    @Override
    public void shred() {
        SinOsc s = new SinOsc(sampleRate());
        
        // Chaining connections
        s.chuck(dac());
        
        // Setting parameters via method calls
        s.freq(440);
        s.gain(0.5);
        
        // Advancing logical time
        advance(second(2));
    }
}
```

### 2. The Unit Generator (UGen) Chain

In ChucK, you "chuck" things together using `=>`. In the Java DSL, every UGen has a `.chuck(target)` method that returns the target, allowing for fluent chaining.

**ChucK:**
```chuck
SinOsc s => ADSR e => LPF f => dac;
```

**Java DSL:**
```java
s.chuck(e).chuck(f).chuck(dac());
```

### 3. Timing and Durations

ChucK's strongest feature is its sample-accurate timing. The Java DSL preserves this by using `ScopedValues` to track the current logical time for each virtual thread.

- **`advance(duration)`**: This is the equivalent of `duration => now;`. It suspends the current shred until the VM clock reaches the new time.
- **Factory Methods**: Instead of literals like `1::samp`, use `samp(1)`, `ms(100)`, `second(2)`, `day(1)`.

### 4. Concurrency (Sporking)

Concurrency in the Java DSL is handled via the `vm.spork()` method or by calling `advance()` to let other threads run.

**ChucK:**
```chuck
fun void play() { ... }
spork ~ play();
```

**Java DSL:**
```java
// Inside a Shred
ChuckVM vm = ChuckVM.CURRENT_VM.get();
vm.spork(() -> {
    // This runs in a new Virtual Thread with its own logical time
    SinOsc s = new SinOsc(sampleRate()).chuck(dac());
    advance(second(1));
});
```

---

## Advanced Usage: Using the Host API

If you want to embed ChucK-Java into your own application without writing separate `.java` files for shreds, use the `ChuckHost` API:

```java
ChuckHost host = new ChuckHost(44100).withAudio(512, 2);

// Spork a simple Java task
host.spork(() -> {
    SinOsc s = new SinOsc(44100).chuck(ChuckDSL.dac());
    s.freq(880);
    ChuckDSL.advance(ChuckDSL.second(1));
});
```

## Tips for Success

1. **Always import the DSL**: Start every file with `import static org.chuck.core.ChuckDSL.*;` to get access to `dac()`, `now()`, `advance()`, etc.
2. **Implement `Shred`**: The IDE and CLI look for a class implementing `org.chuck.core.Shred`.
3. **Sample Rate**: Always pass `sampleRate()` to UGen constructors to ensure they are initialized correctly for the current VM environment.
4. **No Semi-colons in ChucK?**: Remember that while the logic is the same, this is still **Java**. You must use semi-colons, proper variable declarations, and adhere to Java syntax.
