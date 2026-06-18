# ChucK-Java Agent Customizations

## C++ Parity Reference
The native C++ Deluge firmware codebase is located at `/Users/ludo/a/DelugeFirmware`.
You have read-only access to this directory. If you need to verify C++ implementation details or perform gap audits, you should directly inspect the C++ source files (e.g., in `/Users/ludo/a/DelugeFirmware/src/`) using `grep_search` and `view_file` instead of guessing or relying solely on summaries.

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
