# ChucK-Java

ChucK-Java is a powerful port of the ChucK audio programming language to the Java platform, leveraging modern JDK 27 features like Project Loom (Virtual Threads) and the Vector API for high-performance audio synthesis.

## 🚀 Getting Started

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

- **Native RtMidiJava Support**: Ultra-low latency MIDI drivers via pure Java FFM (Panama).
- **High-Performance Audio**: Optimized Vector API paths and Project Loom concurrency.
- **Modern IDE Workstation**: Interactive JavaFX IDE with live coding, visualizers, and code editor.

## 📚 Documentation

Explore the detailed guides and documentation files available in this repository:

### Core Guides
- [Java DSL Guide](chuck-core/JAVA_DSL.md): Learn how to write ChucK code in pure Java.
- [Language Specification](chuck-core/LANGUAGE.md): Deep dive into the ChucK-Java language features.
- [Hosting Guide](chuck-core/HOSTING.md): Embed the ChucK engine into your own Java apps.

### Reference
- [UGen Reference](chuck-core/UGEN_REFERENCE.md): Detailed list of available Unit Generators and their parameters.
- [MIDI Guide](chuck-core/MIDI_GUIDE.md): How to use MIDI input, output, and polyphony.

### Project & Developer Notes
- [JDK Roadmap](chuck-core/JDK_ROADMAP.md): Analysis of future JDK features for ChucK-Java.
- [Maven Guide](MAVEN_GUIDE.md): How to use ChucK-Java as a dependency and publish artifacts.
- [TODO List](TODO.md): Current task list and future ideas.
- [DSP Guidelines](DSP_GUIDELINES.md): Best practices for DSP development in Java.
- [Project Guidelines](GEMINI.md): Foundational mandates for working on the project.
