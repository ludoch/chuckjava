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
-   **Testing:**
    -   Always update or add tests for any change.
    -   Use `mvn test` for JVM tests and `mvn -Pnative -DskipNativeTests=false test` for native tests.
    -   **Validation is mandatory.** A task is not complete until verified by tests and project-specific build/linting commands.
-   **ANTLR Parser:** When changing the grammar, verify against all examples using `mvn test -Dtest=ParseAllExamplesTest`.
-   **CI/CD:**
    -   `unit-tests.yml`: For fast JVM unit tests (on-demand).
    -   `native-build.yml`: For expensive GraalVM native builds (on-demand or on release).

## Project-Specific Commands

```bash
# Compile
mvn compile

# Run all JVM tests
mvn test

# Run ANTLR parser test against all examples
mvn test -Dtest=ParseAllExamplesTest

# Build GraalVM native image
mvn -Pnative package -DskipTests

# Build IDE bundle
mvn -Pide-bundle package -DskipTests
```

## Critical Files

-   **Grammar:** `src/main/antlr4/org/chuck/compiler/ChuckANTLR.g4`
-   **Emitter:** `src/main/java/org/chuck/compiler/ChuckEmitter.java`
-   **VM Core:** `src/main/java/org/chuck/core/ChuckVM.java`
-   **Shred:** `src/main/java/org/chuck/core/ChuckShred.java`
-   **Roadmap:** `JDK_ROADMAP.md`
