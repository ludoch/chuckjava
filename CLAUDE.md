# CLAUDE.md — persistent guidance for all sessions

## ABSOLUTE RULE: ChucK-Java is a pure Java / JVM Language Compiler & IDE

`chuckjava` is a **100% pure Java / Project Loom (Virtual Threads) / FFM** implementation of the ChucK strongly-timed audio programming language.

## Key constraints & Workflow

- **No C++ or Native Library Dependencies:** All UGens, UAnas, AI/ML models, and language constructs operate cleanly inside the JVM without requiring any precompiled shared libraries (`.dll`, `.dylib`, `.so`).
- **Build + test:** `mvn clean package` | `mvn test`
- **ALWAYS reformat before committing:** run `mvn spotless:apply` so commits land pre-formatted. Verify with `mvn spotless:check`. This avoids churn/merge-noise from later formatter passes.
- **Commit + push:** branch off `main`, commit, and push clean, atomic changes.
