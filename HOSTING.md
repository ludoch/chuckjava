# ChucK-Java Hosting & Embedding Guide

This guide explains how to integrate the ChucK-Java Virtual Machine into your own Java applications as a powerful, strongly-timed audio engine.

## 🏗️ Architecture Overview

The ChucK-Java hosting architecture consists of four main components orchestrated by the **`ChuckHost`** class:

1.  **`ChuckVM`**: The core execution engine. It manages "shreds" (Virtual Threads), logical time (`now`), and the UGen graph.
2.  **`ChuckCompiler`**: Translates ChucK source code into bytecode instructions for the VM.
3.  **`ChuckAudio`**: (Optional) The real-time audio driver (JavaSound) that pulls samples from the VM.
4.  **`ChuckMachineServer`**: (Optional) An OSC server that allows external processes to control the VM.

---

## 🔌 Using `ChuckHost`

The `ChuckHost` class is the recommended entry point for developers. It simplifies the setup and provides a unified API.

### 1. The Minimal Host
Used for non-real-time logic, batch processing, or testing. No audio hardware is initialized.

```java
import org.chuck.host.ChuckHost;

public class MyMinimalApp {
    public static void main(String[] args) {
        // Initialize at 44.1kHz
        ChuckHost host = new ChuckHost(44100);
        
        // Capture print output
        host.onPrint(msg -> System.out.println("ChucK says: " + msg));

        // Run code from a string
        host.run("<<< \"Logic Engine Started\" >>>;");

        // Manually advance time by 1 second
        host.advance(44100);
    }
}
```

### 2. The Real-time Host
Initializes the default audio output device. Perfect for standalone apps or creative coding environments.

```java
ChuckHost host = new ChuckHost(44100)
    .withAudio(512, 2); // 512 sample buffer, 2 channels

host.add("myscript.ck");
host.setMasterGain(0.7f);
```

### 3. The Callback Host (Engine Integration)
Use this pattern to drive ChucK from an external audio loop (e.g., inside a Game Engine's audio thread or a VST wrapper).

```java
ChuckHost host = new ChuckHost(44100);
host.run("SinOsc s => dac; 440 => s.freq;");

// This would be called by your engine's audio callback
public void onAudioFrame(float[] buffer) {
    for (int i = 0; i < buffer.length; i++) {
        host.advance(1); // Advance VM by exactly 1 sample
        buffer[i] = host.getLastOut(0); // Get computed sample for channel 0
    }
}
```

---

## 📡 Global Interoperability

You can pass data between Java and ChucK at runtime using shared global variables.

**Java Side:**
```java
host.setGlobalInt("score", 1250);
```

**ChucK Side:**
```chuck
Machine.getGlobalInt("score") => int myScore;
<<< "Java told me the score is:", myScore >>>;
```

---

## 🎹 Command Line Interface (CLI)

The `ChuckCLI` class can be used to add standard ChucK command-line behavior to your app.

| Flag | Description |
|---|---|
| `--loop` | Starts the OSC `ChuckMachineServer` on port 8888. |
| `--silent` | Disables real-time audio output. |
| `--srate:<N>` | Sets the sampling rate. |
| `--syntax` | Only performs a syntax check on files. |

---

## 🛠️ OSC Protocol (Machine Server)

When running in `--loop` mode, the host listens for OSC messages on port **8888** for inter-process control:

| Address | Argument | Description |
|---|---|---|
| `/chuck/add` | `String` (path) | Sporks a new file. |
| `/chuck/remove` | `int` (id) | Kills a running shred. |
| `/chuck/replace` | `int`, `String` | Replaces a shred with a new file. |
| `/chuck/status` | (none) | Prints VM status to the host console. |
| `/chuck/kill` | (none) | Shuts down the VM process. |

---

## 🧪 Examples

Complete hosting examples are provided in the `src/main/java/org/chuck/examples/host/` directory.
