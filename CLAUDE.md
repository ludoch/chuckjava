# CLAUDE.md — persistent guidance for all sessions

## ABSOLUTE RULE: firmware2 is a faithful C→Java port

`org.chuck.deluge.firmware2` is a **100% line-for-line translation** of the Deluge C firmware
at `~/a/DelugeFirmware/src/deluge/`. Translate the C — do not reconstruct, paraphrase, or hack.

**The full rule, pre-edit protocol, and numeric-type mapping are in:**
[`docs/FIRMWARE2_FAITHFUL_PORT.md`](docs/FIRMWARE2_FAITHFUL_PORT.md)

**The prioritized test-mapped roadmap is in:**
[`docs/FIRMWARE2_PORT_ROADMAP.md`](docs/FIRMWARE2_PORT_ROADMAP.md)

## Key constraints

- **No `useFirmware2 = false` bypass hacks.** A failing test means a missing C subsystem —
  port it. Never fall back to the old engine.
- **No approximations.** If the C uses fixed-point/tables, the Java uses the same.
- **Every firmware2 edit cites the C file:line it ports.**
- **Before writing any firmware2 code:** open the exact C function, read it, mirror its structure.
- **Build + test:** `mvn -pl deluge compile` | `mvn -pl deluge test`
- **ALWAYS reformat before committing:** run `mvn -pl deluge spotless:apply` (Spotless +
  googleJavaFormat) so commits land pre-formatted. Verify with `mvn -pl deluge spotless:check`.
  This avoids churn/merge-noise from a later formatter pass reflowing your code.
- **Commit + push:** branch off `main`, commit, merge back.
