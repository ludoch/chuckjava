# ChucK-Java E2E Testing Skill

This guide documents the End-to-End (E2E) testing process for ChucK-Java, enabling future agents to verify the engine's stability and language parity with the reference C++ implementation.

## 1. Testing Infrastructure

We use a two-tier testing system designed for massive parallelization and isolation:

*   **`org.chuck.BatchTester`**: Scans the entire `src/test` and `examples` directories (1,100+ files), executing them in parallel using isolated `java` processes.
*   **`org.chuck.SingleTestRunner`**: A specialized launcher for a single `.ck` file. It handles:
    *   **Negative Testing**: Matches compiler/runtime errors against `.txt` expectation files.
    *   **Output Verification**: Validates printed output against `.txt` files.
    *   **Fuzzy Matching**: Normalizes float precision to 4 decimal places to avoid false failures.
    *   **Automatic Tagging**: Enables type tags (`-Dchuck.print.tags=true`) if the expectation file uses them.
    *   **Smoke Testing**: Marks infinite loops (musical scripts) as "Passed" if they run for 5 seconds without error.

## 2. Command Line Usage

### Full Batch Run
Run this to generate a comprehensive report of the entire engine state:

```bash
mvn compile -Dspotless.check.skip=true && \
java --enable-preview --add-modules jdk.incubator.vector \
-cp "target/classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tr '\n' ' ')" \
org.chuck.BatchTester
```

*Resulting Report:* `test-report.txt` (lists all Failures, Timeouts, and Successes with stack traces).

### Single Test Debugging
Run a specific file with full debug logging enabled:

```bash
java --enable-preview --add-modules jdk.incubator.vector \
-Dchuck.debug.static=true -Dchuck.debug.array=true \
-cp "target/classes:$(mvn dependency:build-classpath | grep -v '\[INFO\]' | tr '\n' ' ')" \
org.chuck.SingleTestRunner src/test/01-Basic/116.ck
```

## 3. Interpreting Results

| Signal | Meaning | Action |
|---|---|---|
| **SUCCESS** | Code ran to completion or passed smoke test. | None. |
| **ERROR NOT CAUGHT** | A negative test expected an error, but the VM was too permissive. | Stiffen compiler checks in `ExpressionEmitter` or `ChuckASTVisitor`. |
| **OUTPUT MISMATCH** | The script ran, but the text output (formatting/values) differed. | Check `ChuckPrint.java` or floating-point logic. |
| **VM Timed out** | The script hung or deadlocked the VM. | Check `advanceTime` and `shredulerLock` in `ChuckVM`. |
| **Process Timed out** | The child JVM crashed or entered an un-interruptible state. | High-priority engine bug investigation. |

## 4. Key JVM Flags

*   `--enable-preview`: Required for Project Loom (Virtual Threads).
*   `--add-modules jdk.incubator.vector`: Required for SIMD-accelerated audio summing.
*   `-Dchuck.print.tags=true`: Forces type-tagged output (e.g., `1 :(int)`).
*   `-Dchuck.debug.static=true`: Logs all static field read/writes.
*   `-Dchuck.debug.array=true`: Logs all `ChuckArray` instantiations and size changes.

## 5. Roadmap Priorities
1.  **Static Array References**: Debugging cross-shred visibility of array assignments.
2.  **Output Normalization**: Harmonizing scientific notation and large float formatting.
3.  **Physical Model Parity**: Fine-tuning STK algorithms to match C++ output exactly.
