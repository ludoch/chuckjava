# JDK Upgrade Roadmap

Analysis of JDK 26 and JDK 27 features relevant to this project.
Current baseline: **JDK 25** (September 2025 LTS).

> **Key clarification up front:** JDK 27 is **not** an LTS release.
> The next LTS after JDK 25 is **JDK 29 (September 2027)**.
> JDK 26 and 27 each get only 6 months of support.
> Plan your upgrade target as JDK 25 → 29, using 26/27 for early-adopter testing.

---

## 1. Vector API — still incubating, graduation not imminent

**Status:** JDK 26 = 11th incubation (JEP 529). No graduation expected in JDK 27 either.

**Why it is taking so long:** graduation is blocked on Project Valhalla. The final Vector API
design depends on value types so that lane elements can be flattened in memory without boxing.
The current estimate for graduation is JDK 28–30 (2027–2028).

**Impact on this project:**
- `VectorAudio.java`, all compiler flags (`--add-modules jdk.incubator.vector`), surefire args,
  and the `native-image.properties` `--add-modules` flag all stay as-is through JDK 27.
- No risk of breakage — incubator APIs are stable between releases even while not final.
- When the API *does* graduate, all of the `--add-modules=jdk.incubator.vector` flags disappear
  from `pom.xml`, `native-image.properties`, and CLAUDE.md. That is the main simplification.

**What to watch:** JEP 529 release notes for API changes between incubations (historically minor).

---

## 2. Project Valhalla: Value Classes — first preview in JDK 26

**Status:** JDK 26 = first preview (JEP 401). JDK 27 likely advances to second preview.
Full standardization expected JDK 28–29.

**What it is:** Value classes have no object identity — the JVM can flatten them into arrays
and stack frames, eliminating heap allocation and GC pressure entirely for small immutable types.

**High-impact opportunities in this codebase:**

| Type | Current cost | With value class |
|------|-------------|-----------------|
| `ChuckDuration` (record, 1 double) | heap allocation per creation | stack-allocated or array-flattened |
| `ChuckString` (thin wrapper) | heap allocation + GC | potentially inlined |
| Complex/polar numbers in FFT | boxed in `Object[]` | unboxed in value arrays |
| Per-sample UGen outputs (float) | already primitive | no change |

The tightest loop in the VM is `ChuckVM.advanceTime()` → `DacChannel.tick()` → full UGen graph.
Each sample currently involves zero small-object allocations in the hot path (UGens are long-lived).
The allocation pressure is mostly in the shred instruction execution (stack pushes of `Long`/`Double`
boxed objects). Value classes could eliminate that boxing.

**Concrete change when stable:**
```java
// Today
public record ChuckDuration(double samples) { ... }

// With value classes (JDK 28+)
public value class ChuckDuration {
    double samples;
    // identity-free; JVM flattens into arrays and caller stack frames
}
```

**GraalVM caveat:** native-image support for Valhalla value types will lag the JDK by at least
one release cycle. Do not use value classes in code paths that must compile to `chuck.exe` until
GraalVM explicitly supports them.

---

## 3. Scoped Values — already final in JDK 25

**Status:** Finalized via JEP 506 in JDK 25. Already stable in this project.

The `ScopedValue<ChuckVM>` and `ScopedValue<ChuckShred>` used in `ChuckVM.java` and
`ChuckShred.java` are no longer preview features. The `--enable-preview` flag is **not** needed
for Scoped Values on JDK 25. It is still needed for other preview APIs used in this project
(check with `javap -verbose` on compiled classes to confirm which ones remain).

---

## 4. Structured Concurrency — approaching final

**Status:** JDK 26 = 6th preview (JEP 525). Expected to finalize in JDK 27 or 28.

**What it offers this project:** `StructuredTaskScope` provides a clean, failure-propagating
scope for spawning a group of Virtual Threads where all must complete (or any failure
cancels the rest).

**Current pattern (raw Virtual Threads):**
```java
// ChuckVM — spawning shreds today
Thread.ofVirtual().name("shred-" + id).start(() -> shred.execute(vm));
```

**Potential pattern with StructuredTaskScope:**
```java
try (var scope = StructuredTaskScope.open()) {
    for (ChuckShred shred : pendingShreds) {
        scope.fork(() -> { shred.execute(vm); return null; });
    }
    scope.join();  // waits for all, propagates first failure
}
```

For ChucK's model this is a mixed fit — shreds are intentionally independent and
long-lived, so a scope that cancels siblings on failure is not always the right semantic.
It could be useful for the `Machine.removeAll()` path or batch-sporking in tests.
Not a must-have, but worth adopting once final for cleaner shred lifecycle management.

---

## 5. Project Leyden: AOT Object Caching — finalized in JDK 26

**Status:** JEP 516 finalized in JDK 26. Works with all GC variants including ZGC.

**What it is:** Extends CDS (Class Data Sharing) to cache heap objects at build time.
The JVM writes a pre-initialized heap snapshot; at startup it memory-maps that snapshot
instead of running static initializers and class loading.

**Impact on this project:**

- **IDE startup time:** `ChuckIDE` today likely takes 1–3 seconds before the first window
  appears (JavaFX initialization, ANTLR grammar loading, font rendering). AOT object caching
  can cut this to under 200 ms for `jpackage`-bundled apps.
- **ANTLR initialization:** `ChuckANTLRLexer` and `ChuckANTLRParser` build their ATN (Augmented
  Transition Network) on first use — an expensive reflective operation. With Leyden, this
  gets cached in the AOT archive.
- **Usage:** `java -XX:AOTCache=chuck.aot -jar chuck-java.jar` (training run creates the cache;
  subsequent runs reuse it). `jpackage` can embed the cache for zero-configuration fast start.

This is the upgrade with the most user-visible impact (IDE feels snappier) at zero code cost.

---

## 6. G1 GC: Reduced Write-Barrier Contention — finalized in JDK 26

**Status:** JEP 522 finalized in JDK 26. No code changes required.

**What it is:** Introduces a dual card table that eliminates a synchronization bottleneck
between G1's concurrent GC thread and application threads on write barriers. On x64 the
write-barrier sequence shrinks from ~50 instructions to ~12.

**Impact on this project:**
- The UGen graph `tick()` chain allocates nothing, but the `ChuckStack` push/pop operations
  do update object references. Reduced write-barrier cost benefits every instruction execution.
- Reported: ~10% throughput improvement for allocation-heavy workloads. For audio the
  impact is a modest reduction in worst-case GC pause spikes — relevant for glitch-free playback.
- Free upgrade, JDK 25 → 26.

---

## 7. Lazy Constants (Stable Values) — 2nd preview in JDK 26

**Status:** JEP 526 in JDK 26 (renamed from "Stable Values"). Expected final in JDK 28.

**What it is:** A JVM-guaranteed lazy constant — initialized exactly once, without
double-checked locking, and JIT-treated as a compile-time constant after first access.

**Relevant use case in this project:**

```java
// Today — manual double-checked locking for heavyweight singleton initialization
private static volatile float[] hannWindow;
public static float[] getHannWindow(int size) {
    if (hannWindow == null) {
        synchronized (FFT.class) {
            if (hannWindow == null) hannWindow = computeHann(size);
        }
    }
    return hannWindow;
}

// With Lazy Constants (JDK 28+)
private static final LazyConstant<float[]> HANN_WINDOW =
    LazyConstant.of(() -> computeHann(DEFAULT_FFT_SIZE));
```

The JIT can then inline `HANN_WINDOW.get()` as a true constant after warmup —
potentially hoisting loop-invariant window lookups out of the FFT inner loop.
Relevant to `FFT.java`, `MFCC.java`, and any filter coefficient table.

---

## 8. JavaFX 26: Metal Rendering on macOS

**Status:** Released with JavaFX 26 (March 2026). Update `javafx.version` to `26.0.x` in `pom.xml`.

**What changes:**
- **Metal pipeline replaces OpenGL** on macOS. The JavaFX IDE will render correctly on
  Apple Silicon (M-series) without emulation, and without the occasional frame-drop seen
  on macOS 14+ where OpenGL is deprecated.
- **Headless Glass platform:** enables off-screen rendering without a display. Useful for
  running `DslExamplesTest` with visualization checks in CI (currently the FFT/oscilloscope
  panels are untested in CI).
- **RichTextFX + JavaFX 26:** The `CodeArea` in the IDE will benefit from improved text
  layout performance. Syntax highlighting reflow on large `.ck` files should be noticeably
  faster.

**Migration cost:** Low. Change `<javafx.version>25.0.2</javafx.version>` to `26.0.x` and
re-test the IDE. The public API is backwards-compatible.

---

## 9. Primitive Types in Patterns — nearing final

**Status:** JDK 26 = 4th preview (JEP 530). Expected final in JDK 27 or 28.

**What it enables:**
```java
// Today — explicit unboxing before pattern match
Object val = stack.pop();
if (val instanceof Long l) { ... }
else if (val instanceof Double d) { ... }

// With primitive patterns
switch (stack.pop()) {
    case long l  -> handleLong(l);
    case double d -> handleDouble(d);
    case String s -> handleString(s);
}
```

**Impact:** The `ChuckStack` and instruction `execute()` methods are full of `instanceof Long`
/ `instanceof Double` chains. Primitive patterns would simplify this code significantly
and may allow the JIT to optimize switch dispatch more aggressively than cascaded instanceof.
Not a performance game-changer, but a meaningful code quality improvement.

---

## 10. `--enable-preview` Surface Area Shrinks Over Time

Several features currently requiring `--enable-preview` in this project will finalize
and no longer need the flag. Tracking:

| Feature | Status in JDK 25 | Expected final |
|---------|-----------------|----------------|
| Scoped Values | **Final** (JEP 506) | Already done |
| Unnamed variables (`_`) | **Final** (JDK 22) | Already done |
| String Templates | Removed (redesign in progress) | JDK 27–28 (speculative) |
| Structured Concurrency | Preview | JDK 27–28 |
| Primitive Types in Patterns | Preview | JDK 27–28 |
| Value Classes | Preview | JDK 28–29 |
| Vector API | Incubator | JDK 28–30 |

Once Structured Concurrency and Primitive Types in Patterns finalize, `--enable-preview`
can potentially be dropped from the build — though Vector API's `--add-modules` flag will
remain until that graduates separately.

---

## Summary: What to Do and When

| Action | When | Effort | Impact |
|--------|------|--------|--------|
| Upgrade to JavaFX 26, test Metal rendering on Mac | Now (JDK 26 released) | Low | macOS IDE quality |
| Enable Leyden AOT cache in `jpackage` bundle | JDK 26 | Low | IDE startup speed |
| Remove `--enable-preview` for Scoped Values | Already possible on JDK 25 | Trivial | Cleaner build |
| Adopt Structured Concurrency for shred lifecycle | JDK 27–28 | Medium | Code clarity |
| Prototype Valhalla value classes on `ChuckDuration` | JDK 26 (preview branch) | Medium | DSP GC pressure |
| Migrate `ChuckStack` dispatch to primitive patterns | JDK 27–28 (when final) | Medium | Code clarity |
| Remove all `--add-modules=jdk.incubator.vector` | JDK 28–30 | Low (when the day comes) | Build simplicity |
| Target JDK 29 as next LTS upgrade | September 2027 | High | Long-term stability |
