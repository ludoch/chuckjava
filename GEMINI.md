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
-   **ANTLR Parser:** When changing the grammar, verify against all examples using `mvn test -Dtest=ParseAllExamplesTest`.
-   **CI/CD:**
    -   `unit-tests.yml`: For fast JVM unit tests (on-demand).
    -   `native-build.yml`: For expensive GraalVM native builds (on-demand or on release).
    -   `maven-publish.yml`: For publishing the Maven artifact to GitHub Packages (on-demand).

## Project-Specific Commands

```bash
# Full validation (regressions + code style + tests)
mvn clean package

# Run Sequencer E2E Regression Tests (Headless/Virtual Time)
mvn -pl sequencer test -Dtest=SequencerEngineTest

# Run Standalone Sequencer (Normal)
mvn -pl sequencer javafx:run

# Run Standalone Sequencer with full Diagnostics (Level 2)
mvn -pl sequencer javafx:run -Dchuck.loglevel=2
```

## Sequencer Stability & Regression

The Sequencer relies on a delicate Java-to-ChucK synchronization layer. To prevent regressions:
1.  **ALWAYS run `SequencerEngineTest`** after any change to the VM, `SndBuf`, or `sequencer_setup.ck`.
2.  **Audio Dummy Mode:** Tests use `-Dchuck.audio.dummy=true` to simulate the audio path without physical hardware. This is essential for CI and headless environments.
3.  **Log Levels:** The engine script (`sequencer_setup.ck`) respects `Machine.loglevel()`. 
    - Level 1 (Default): Silent.
    - Level 2: Trigger logs and DAC output monitoring.
    - Level 3: Debug beeps for every trigger.
4.  **Array Linking:** The engine re-links Java arrays *inside* the loop via `Machine.getGlobalObject`. Never revert to static `global` array declarations which can cause `ArrayOutOfBounds` during engine reloads.

## Critical Files

-   **Grammar:** `src/main/antlr4/org/chuck/compiler/ChuckANTLR.g4`
-   **Emitter:** `src/main/java/org/chuck/compiler/ChuckEmitter.java`
-   **VM Core:** `src/main/java/org/chuck/core/ChuckVM.java`
-   **Shred:** `src/main/java/org/chuck/core/ChuckShred.java`
-   **Roadmap:** `JDK_ROADMAP.md`
