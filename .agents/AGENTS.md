# ChucK-Java Agent Customizations

## Safe DSP & Systems Programming Guardrails

To prevent regressions in audio quality, portability, and systems robustness, all agents working on this project must strictly adhere to the following rules:

1. **Mandatory CLI & External Tool Verification:**
   - Whenever invoking, calling, or wrapping an external command-line tool (e.g., `basic-pitch` or Python scripts), you MUST first run the tool with its `--help` flag or check its documentation to verify the exact argument order and syntax. Never guess CLI parameter order.

2. **Strict GUI Threading Separation:**
   - Any heavy computational work, file parser invocation, or filesystem/disk I/O (e.g., preset directory scanning) in the `ui` package must be offloaded from the Event Dispatch Thread (EDT) using a background thread (`SwingWorker`, virtual thread, etc.) to keep the user interface responsive.

3. **Strict OS Process Lifecycle Management:**
   - All external processes (`Process`) and input/output streams must be managed using Java's `try-with-resources` or `try-finally` blocks to ensure they are always destroyed (`process.destroy()`) and closed, preventing background zombie processes and resource leaks.
   - When launching a sub-process, always drain or redirect its stdout/stderr streams to prevent OS pipe-buffer deadlock.

4. **Absolute Portability (Zero Hardcoding):**
   - Never hardcode local absolute paths (such as specific temporary agent directories or home directory usernames). Resolve all file paths dynamically using classpath resources, relative paths, or standard Java system properties (e.g., `user.home` or `java.io.tmpdir`).

5. **Micro-Level DSP Parity Validation (No Silent Passes):**
   - All tests that assert audio signal correctness against a reference wave must perform sample-by-sample ratio parity checks over the active sounding duration of the wave. Asserting only macro-level metrics (RMS, peak, DC offset) is insufficient, as it allows wave-folding and wrapping distortion to pass silently.

6. **Prefer Streams and Byte Arrays over String Copies (Memory Efficiency):**
   - When writing XML/JSON parsers, decompressors, or file processors, process data directly using raw binary or stream representations (`byte[]`, `InputStream`). Avoid converting large datasets into intermediate `String` objects, which cause massive memory allocation and garbage collection overhead.

7. **No Blocking I/O or Heavy Scans in Static Initializers:**
   - Static initializers and field declarations must be fast, lightweight, and side-effect-free. Never execute blocking disk I/O, directory scanning, or hardware discovery during class loading. Use lazy initialization (synchronized getters or initialization-on-demand holder patterns) to defer expensive operations to their first actual use.

8. **Design for Fault Tolerance (Per-Element Resilience):**
   - When processing collections of independent elements (e.g., importing tracks, parsing multiple presets, or reading files), never allow a single corrupt or unsupported element to abort the entire process. Wrap processing loops in localized `try-catch` blocks to log individual failures and proceed with the remaining elements.

9. **Guard Against Auto-Unboxing of Nulls:**
   - Always perform an explicit null check before unboxing wrapper objects (`Integer`, `Double`, `Boolean`) retrieved from maps or nullable sources into primitive types (`int`, `double`, `boolean`). Auto-unboxing a `null` immediately throws a `NullPointerException`.

10. **Secure Resource Ownership Transitions:**
    - When opening system resources (files, sockets, audio streams) to pass them to another manager class (e.g., passing an `AudioInputStream` to an audio `Clip`), wrap the transition in a `try-catch` block. If the receiver fails to take ownership, you must close the resource yourself to prevent file handle/resource leaks.
