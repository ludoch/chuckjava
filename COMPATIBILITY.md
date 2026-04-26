# ChucK-Java Compatibility Report (v1.5 Parity)

This document tracks the mathematical and functional compatibility level of ChucK-Java compared to the native C++ ChucK runtime (v1.5.x).

## 1. Compatibility Milestones

| Feature | Level | Status | Details |
| :--- | :--- | :--- | :--- |
| **Scheduler** | **Bit-Exact** | ✅ | Uses global `insertionOrder` to break ties for identical `now` times. |
| **Event Sync** | **Bit-Exact** | ✅ | Aligned `broadcast()` and `spork` synchronization order. |
| **Core RNG** | **Bit-Exact** | ✅ | Custom MT19937 implementation matching native `ck_random_f`. |
| **Filter (LPF/ResonZ)** | **Bit-Exact** | ✅ | Matches native SuperCollider-derived biquad formulas. |
| **Filter (TwoPole/Zero)** | **Bit-Exact** | ✅ | Matches native STK-style implementations and normalization. |
| **Oscillators** | **High Parity** | ✅ | Aligned sample-then-increment phase accumulation. |
| **STK Instruments** | **Acceptable** | ✅ | Close parity ($< 0.1$ RMS), limited by float precision accumulation. |

## 2. On-Demand Regression Testing

A Java-based regression suite is provided to verify bit-exact parity and prevent future divergence.

### Prerequisites
- Native `chuck` installed and available in the `$PATH`.
- JDK 25 with the vector incubator module enabled.

### Running the Suite
1. **Build ChucK-Java**:
   ```bash
   mvn clean install -DskipTests
   ```

2. **Execute Comparisons**:
   ```bash
   java --add-modules jdk.incubator.vector -cp chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar org.chuck.ParityTester
   ```

### Output Interpretation
The utility generates `.wav` files for both native and Java implementations and calculates the Root Mean Square (RMS) difference.
- **BIT-EXACT**: 0.0 RMS. The outputs are identical.
- **NEAR-EXACT**: $< 10^{-6}$ RMS. Minor floating point differences.
- **HIGH PARITY**: $< 0.05$ RMS. Audibly identical.
- **ACCEPTABLE**: $< 0.2$ RMS. Functionally correct, minor precision drift.
- **DIVERGED**: $\ge 0.2$ RMS. Regression likely; investigate implementation.

## 3. Backported Features (ChucK 2026)
ChucK-Java currently supports the following "future" language features:
- **Object Truthiness**: `if (obj)` evaluates to false if null.
- **Vector Negation**: `-v` where `v` is a complex or polar vector.
- **`Std.range`**: Native support for Python-style ranges: `Std.range(10)`, `Std.range(1, 10, 2)`.
- **Bitwise Operators**: `^`, `<<`, `>>` available for integer types.
