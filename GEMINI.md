# ChucK-Java Project Guidelines

This document provides foundational mandates for working on the ChucK-Java project. Adhere to these standards to ensure consistency, safety, and technical integrity.

## Development Lifecycle

Operate using a **Research -> Strategy -> Execution** lifecycle.

1.  **Research:** Map the codebase and validate assumptions using `grep_search`, `glob`, and `read_file`. For bugs, prioritize empirical reproduction.
2.  **Strategy:** Share a concise summary of your plan before proceeding.
3.  **Execution:** Iterate through **Plan -> Act -> Validate** for each sub-task. Use surgical changes, follow project idioms, and include automated tests.

## Technology Stack & Environment

-   **Runtime:** JDK 25 (requires `--enable-preview` and `jdk.incubator.vector`).
-   **Build Tool:** Maven.
-   **Parser:** ANTLR4 (grammar in `src/main/antlr4/org/chuck/compiler/ChuckANTLR.g4`).
-   **Concurrency:** Java Virtual Threads (Project Loom).
-   **GUI:** JavaFX (profile `ide-bundle`).
-   **Native:** GraalVM Native Image (profile `native`).

## Engineering Standards

-   **Code Style:** Follow existing patterns in the codebase. Maintain the surgical nature of updates.
-   **Types & Safety:** Rigorously adhere to the type system. Do not bypass or suppress warnings unless explicitly instructed.
- **Zero Hardcoding Policy**: NEVER hardcode resource paths (e.g., sample wave names like "808 Kick.wav", external URLs, or local directory strings) within the production source code (`src/main/java`). All resources MUST be resolved dynamically via the **Object Model** (loaded from XML/JSON files) or identified through runtime discovery logic. This ensures the application remains strictly data-driven and portable across different environments.
- **Testing:**

    -   Always update or add tests for any change.
    -   **Always run `mvn spotless:apply`** to format the code before committing.
    -   **Always run `mvn clean package`** to trigger all regressions, including code style verification (Spotless) and full unit test suites.
    -   Use `mvn test` for fast JVM tests and `mvn -Pnative -DskipNativeTests=false test` for native tests.
    -   **Validation is mandatory.** A task is not complete until verified by tests and project-specific build/linting commands.
-   **🔊 Audio DSP & Wave Parity Safeguards:**
    -   **Automated Wave Parity Assertion:** Every core playhead, voice mixer, or DSP engine modification MUST pass the direct sequential comparative check inside [DigitalAudioFidelityTest.java](file:///Users/ludo/a/chuckjava/deluge/src/test/java/org/chuck/deluge/DigitalAudioFidelityTest.java). We sequentially assert wave frame shape linear alignment (scale factors matching target track gain levels), infinite decay energy curves, and organic DC offset tolerances ($\le 0.01$).
    -   **Fixed-Point DSP Stability Mandates:**
        -   *Unipolar Envelope Mapping:* The main volume envelope (Envelope 0) must map directly and unipolar (no bi-polar offsets or $+0.5$ shifts) to the voice's master gain loop, ensuring synth/subtractive waves fade completely to absolute silence on release.
        -   *Tangent High-Frequency Safety:* Never add trailing scaling left-shifts inside `instantTan` or filter cutoffs that can exceed $2^{30}$ and wrap signed variables to negative values. High-frequency equations must use safe 64-bit direct long multiplications/shifts to prevent division-by-zero filter blowouts and capacitor charging pop/click transients.
        -   *Default Drum Slot Mutes:* All unconfigured drum kit instrument slots must initialize with operator A volume, operator B volume, and noise volumes set to **`0`** in the constructor to keep background synth channels fully silent. Active volumes are strictly limited to parsed XML row configurations.
        -   *Double-Overflow Saturation Guard:* Summing paths (such as delay lines and feedback loops) must utilize double-overflow-guarded functions (like `Q31.signedSaturate`) instead of simple bit-shifts to protect signals from integer wrap distortion.
-   **ANTLR Parser:** When changing the grammar, verify against all examples using `mvn test -Dtest=ParseAllExamplesTest`.
-   **CI/CD:**
    -   `unit-tests.yml`: For fast JVM unit tests (on-demand).
    -   `native-build.yml`: For expensive GraalVM native builds (on-demand or on release).
    -   `maven-publish.yml`: For publishing the Maven artifact to GitHub Packages (on-demand).

## Project-Specific Commands

```bash
# Full validation (regressions + code style + tests)
mvn clean package
```

## Critical Files

-   **Grammar:** `src/main/antlr4/org/chuck/compiler/ChuckANTLR.g4`
-   **Emitter:** `src/main/java/org/chuck/compiler/ChuckEmitter.java`
-   **VM Core:** `src/main/java/org/chuck/core/ChuckVM.java`
-   **Shred:** `src/main/java/org/chuck/core/ChuckShred.java`
-   **Roadmap:** `JDK_ROADMAP.md`
