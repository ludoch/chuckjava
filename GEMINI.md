# ChucK-Java Project Guidelines

This document provides foundational mandates for working on the ChucK-Java project. Adhere to these standards to ensure consistency, safety, and technical integrity.

## Development Lifecycle

Operate using a **Research -> Strategy -> Execution** lifecycle.

1.  **Research:** Map the codebase and validate assumptions using `grep_search`, `glob`, and `read_file`. For bugs, prioritize empirical reproduction.
2.  **Strategy:** Share a concise summary of your plan before proceeding.
3.  **Execution:** Iterate through **Plan -> Act -> Validate** for each sub-task. Use surgical changes, follow project idioms, and include automated tests.

## Technology Stack & Environment

-   **Runtime:** JDK 27 (early-access) (requires `--enable-preview` and `jdk.incubator.vector`). Auto-provisioned by `run.sh` / `run.bat`.
-   **Build Tool:** Maven.
-   **Parser:** ANTLR4 (grammar in `src/main/antlr4/org/chuck/compiler/ChuckANTLR.g4`).
-   **Concurrency:** Java Virtual Threads (Project Loom).
-   **GUI:** JavaFX (profile `ide-bundle`).

## Engineering Standards

-   **Code Style:** Follow existing patterns in the codebase. Maintain the surgical nature of updates.
-   **Types & Safety:** Rigorously adhere to the type system. Do not bypass or suppress warnings unless explicitly instructed.
- **Zero Hardcoding Policy**: NEVER hardcode resource paths (e.g., sample wave names like "808 Kick.wav", external URLs, or local directory strings) within the production source code (`src/main/java`). All resources MUST be resolved dynamically via the **Object Model** (loaded from XML/JSON files) or identified through runtime discovery logic. This ensures the application remains strictly data-driven and portable across different environments.
- **Testing:**

    -   Always update or add tests for any change.
    -   **Always run `mvn spotless:apply`** to format the code before committing.
    -   **Always run `mvn clean package`** to trigger all regressions, including code style verification (Spotless) and full unit test suites.
    -   Use `mvn test` for fast JVM tests across modules.
    -   **Validation is mandatory.** A task is not complete until verified by tests and project-specific build/linting commands.
-   **ANTLR Parser:** When changing the grammar, verify against all examples using `mvn test -Dtest=ParseAllExamplesTest`.
-   **CI/CD:**
    -   `unit-tests.yml`: For JVM unit tests across all modules.
    -   `release.yml`: For building and packaging self-contained ChucK-Java workstation releases (`run.sh`, `run.bat`, `ensure-jdk27.sh`, `chuck-ide.jar`).
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
