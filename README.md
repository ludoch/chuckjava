# ChucK-Java

ChucK-Java is a powerful port of the ChucK audio programming language to the Java platform, leveraging modern JDK 27 features like Project Loom (Virtual Threads) and the Vector API for high-performance audio synthesis.

## 🎵 Quick Start (For End Users)

If you just want to run the interactive ChucK-Java IDE and Workstation:

1. **Download the Release**: Go to the [Releases](https://github.com/ludoch/chuckjava/releases) page and download the latest `chuck-java-workstation.zip` release package.
2. **Unzip the Package**: Extract the contents of the ZIP file to any folder on your machine.
3. **Launch the Workstation**:
   - **macOS / Linux**: Open your terminal inside the extracted directory and run:
     ```bash
     ./run.sh
     ```
   - **Windows**: Run the batch script:
     ```cmd
     run.bat
     ```
4. **Zero-Configuration Setup**: The launch scripts will automatically check for a local JDK 27 installation. If one is not found, they will safely download a sandboxed, local **JDK 27 (early-access)** environment directly into the directory so everything runs instantly out-of-the-box.
5. **Learn the Workstation**: Check out the comprehensive, illustrated [End-User Workstation Guidebook](docs/USER_GUIDE.md) for full details on navigating the IDE, monitoring live threads (Shreds), mapping MIDI controllers, and sporking custom audio DSP.

---

## 🛠️ Developer Setup & Getting Started

### Prerequisites
- **JDK 27** (early-access; with preview features enabled)
- **Maven**

### Building the Project
To build all modules and install them to your local repository:
```bash
mvn install -DskipTests
```

### Running the Application
You can run ChucK-Java in different modes:

- **Command Line Interface (CLI)**:
  ```bash
  java --enable-preview --add-modules jdk.incubator.vector \
       --enable-native-access=ALL-UNNAMED \
       -jar chuck-cli/target/chuck-cli-1.0-SNAPSHOT-shaded.jar your_script.ck
  ```

- **JavaFX IDE**:
  ```bash
  mvn -pl chuck-ide javafx:run
  ```

## 🎹 Features

- **100% Java DSL Pass Rate**: Full batch translation and `javac` compilation pass rate across all 636 `.ck` example scripts (`ChuckToDSLConverter.java`).
- **High-Fidelity Audio Parity**: Bit-exact and high-fidelity STK / UGen parity vs native C++ ChucK (`SndBuf` special resources, `VisualizerPanel` trigger lock).
- **Native RtMidiJava Support**: Ultra-low latency MIDI drivers via pure Java FFM (Panama).
- **High-Performance Audio**: Optimized Vector API paths and Project Loom virtual threads concurrency (`JDK 27-ea`).
- **Modern IDE Workstation**: Interactive JavaFX IDE with live coding, CRT oscilloscope trigger locking, logarithmic FFT spectrum analyzer ($20\text{ Hz}$–$20\text{ kHz}$), live WAV recording, detachable Virtual Console (`miniAudicle` parity), and interactive Shred Inspector.

## 📚 Documentation

Explore the detailed guides and documentation files available in this repository:

### Core Guides
- [End-User Workstation Guidebook](docs/USER_GUIDE.md): **NEW** Comprehensive illustrated manual covering all IDE screens, visualizer graphs, Virtual Console filtering, Shred Inspector, and live audio recording.
- [Java DSL Guide](chuck-core/JAVA_DSL.md): Learn how to write ChucK code in pure Java.
- [Language Specification](chuck-core/LANGUAGE.md): Deep dive into the ChucK-Java language features.
- [Hosting Guide](chuck-core/HOSTING.md): Embed the ChucK engine into your own Java apps.

### Reference
- [UGen Reference](chuck-core/UGEN_REFERENCE.md): Detailed list of available Unit Generators and their parameters.
- [MIDI Guide](chuck-core/MIDI_GUIDE.md): How to use MIDI input, output, and polyphony.
- [Compatibility Report](COMPATIBILITY.md): Mathematical and empirical parity report vs native C++ ChucK.

### Project & Developer Notes
- [JDK Roadmap](chuck-core/JDK_ROADMAP.md): Analysis of future JDK features for ChucK-Java.
- [Maven Guide](MAVEN_GUIDE.md): How to use ChucK-Java as a dependency and publish artifacts.
- [TODO List](TODO.md): Current task list and completed milestones.
- [DSP Guidelines](DSP_GUIDELINES.md): Best practices for DSP development in Java.
- [Project Guidelines](GEMINI.md): Foundational mandates for working on the project.
