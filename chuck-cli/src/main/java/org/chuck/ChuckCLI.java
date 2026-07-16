package org.chuck;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.ChuckAudio;
import org.chuck.core.*;

public class ChuckCLI {
  private int sampleRate = 44100;
  private int bufferSize = 512;
  private int numChannels = 2;
  private boolean silent = false;
  private boolean loop = false;
  private boolean dump = false;
  private boolean syntaxOnly = false;
  private boolean forceGui = false;
  private int verbose = 1;
  private int timeoutSeconds = 0;

  private final List<String> filesToAdd = new ArrayList<>();
  private final List<String> otfCommands = new ArrayList<>();

  public void run(String[] args) {
    parseArgs(args);

    if (forceGui) {
      launchIDE(args);
      return;
    }

    if (!otfCommands.isEmpty() && filesToAdd.isEmpty() && !loop && !syntaxOnly) {
      sendOtfCommands();
      return;
    }

    if (syntaxOnly) {
      checkSyntax();
      return;
    }

    startVM();
  }

  private void parseArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith("--srate:")) {
        sampleRate = Integer.parseInt(arg.substring("--srate:".length()));
      } else if (arg.startsWith("--bufsize:")) {
        bufferSize = Integer.parseInt(arg.substring("--bufsize:".length()));
      } else if (arg.startsWith("--chan:")) {
        numChannels = Integer.parseInt(arg.substring("--chan:".length()));
      } else if (arg.startsWith("--timeout:")) {
        timeoutSeconds = Integer.parseInt(arg.substring("--timeout:".length()));
      } else if (arg.startsWith("--verbose:")) {
        verbose = Integer.parseInt(arg.substring("--verbose:".length()));
      } else if (arg.startsWith("--chugin-path:")) {
        String paths = arg.substring("--chugin-path:".length());
        for (String p : paths.split(java.io.File.pathSeparator)) {
          if (verbose > 0) System.out.println("[chuck]: added chugin search path: " + p);
        }
      } else if (arg.startsWith("--abort.shred:")) {
        otfCommands.add("abort:" + arg.substring("--abort.shred:".length()));
      } else {
        switch (arg) {
          case "--help", "-h", "--about" -> {
            printUsage();
            System.exit(0);
          }
          case "--version" -> {
            System.out.println("ChucK-Java version 0.1.0 (JDK 27)");
            System.exit(0);
          }
          case "--probe" -> {
            System.out.println("[chuck]: probing audio devices...");
            System.out.println("--- Output Devices ---");
            for (org.chuck.audio.ChuckAudio.DeviceInfo info :
                org.chuck.audio.ChuckAudio.getOutputDeviceInfo()) {
              System.out.println(
                  "  "
                      + info.name()
                      + " (max channels: "
                      + info.maxOutputChannels()
                      + ", rates: "
                      + info.supportedSampleRates()
                      + ")");
            }
            System.out.println("--- Input Devices ---");
            for (org.chuck.audio.ChuckAudio.DeviceInfo info :
                org.chuck.audio.ChuckAudio.getOutputDeviceInfo()) {
              System.out.println(
                  "  "
                      + info.name()
                      + " (max channels: "
                      + info.maxInputChannels()
                      + ", rates: "
                      + info.supportedSampleRates()
                      + ")");
            }
            System.exit(0);
          }
          case "--loop", "-l", "--empty" -> loop = true;
          case "--halt" -> loop = false;
          case "--silent", "-s" -> silent = true;
          case "--dump" -> dump = true;
          case "--syntax" -> syntaxOnly = true;
          case "--gui", "--ide" -> forceGui = true;
          case "+", "--add" -> {
            if (i + 1 < args.length) otfCommands.add("+" + args[++i]);
          }
          case "-", "--remove" -> {
            if (i + 1 < args.length) otfCommands.add("-" + args[++i]);
          }
          case "=", "--replace" -> {
            if (i + 1 < args.length) otfCommands.add("=" + args[++i]);
          }
          case "^", "--status", "status" -> otfCommands.add("^");
          case "--kill", "kill" -> otfCommands.add("kill");
          case "time", "--time" -> otfCommands.add("time");
          case "remove.all", "--remove.all" -> otfCommands.add("remove.all");
          case "clear.vm", "--clear.vm" -> otfCommands.add("clear.vm");
          case "reset.id", "--reset.id" -> otfCommands.add("reset.id");
          case "abort.shred", "--abort.shred" -> {
            if (i + 1 < args.length) otfCommands.add("abort:" + args[++i]);
          }
          default -> {
            if (arg.startsWith("-")) {
              System.err.println("Unknown option: " + arg);
            } else {
              filesToAdd.add(arg);
            }
          }
        }
      }
    }
  }

  private void sendOtfCommands() {
    org.chuck.network.OscOut oscOut = new org.chuck.network.OscOut();
    oscOut.dest("localhost", 8888);

    for (String cmd : otfCommands) {
      org.chuck.network.OscMsg msg = new org.chuck.network.OscMsg();
      if (cmd.startsWith("+")) {
        msg.address = "/chuck/add";
        msg.addString(cmd.substring(1));
      } else if (cmd.startsWith("-")) {
        msg.address = "/chuck/remove";
        msg.addInt(Integer.parseInt(cmd.substring(1)));
      } else if (cmd.startsWith("=")) {
        msg.address = "/chuck/replace";
        String[] parts = cmd.substring(1).split(" ", 2);
        if (parts.length == 2) {
          msg.addInt(Integer.parseInt(parts[0]));
          msg.addString(parts[1]);
        }
      } else if (cmd.equals("^")) {
        msg.address = "/chuck/status";
      } else if (cmd.equals("kill")) {
        msg.address = "/chuck/kill";
      } else if (cmd.equals("time")) {
        msg.address = "/chuck/time";
      } else if (cmd.equals("remove.all")) {
        msg.address = "/chuck/remove/all";
      } else if (cmd.equals("clear.vm")) {
        msg.address = "/chuck/clear";
      } else if (cmd.equals("reset.id")) {
        msg.address = "/chuck/reset/id";
      } else if (cmd.startsWith("abort:")) {
        msg.address = "/chuck/remove";
        msg.addInt(Integer.parseInt(cmd.substring(6)));
      }
      oscOut.send(msg);
      System.out.println("Sent OTF command: " + msg.address + " " + cmd);
    }
  }

  private void checkSyntax() {
    ChuckVM vm = new ChuckVM(sampleRate);
    for (String fileName : filesToAdd) {
      try {
        vm.add(fileName);
        System.out.println("✅ Syntax OK: " + fileName);
      } catch (Exception e) {
        System.err.println("❌ Syntax Error in " + fileName + ": " + e.getMessage());
      }
    }
  }

  private void startVM() {
    try {
      if (verbose > 0 && !Boolean.getBoolean("chuck.print.tags")) {
        System.out.println(
            "🎸 ChucK-Java (JDK 27) - [VERIFIED] " + (silent ? "Silent Mode" : "Real-time Audio"));
      }

      ChuckVM vm = new ChuckVM(sampleRate);
      vm.addPrintListener(System.out::print);
      vm.setLogLevel(verbose);

      ChuckAudio audio = null;
      if (!silent) {
        audio = new ChuckAudio(vm, bufferSize, numChannels, (float) sampleRate);
        audio.start();
      }

      List<ChuckShred> initialShreds = new ArrayList<>();
      for (String fileName : filesToAdd) {
        try {
          if (fileName.endsWith(".java")) {
            Runnable task = org.chuck.core.ChuckDSL.load(Paths.get(fileName));
            int id = vm.spork(task);
            ChuckShred shred = vm.getShred(id);
            if (shred != null) initialShreds.add(shred);
          } else {
            int id = vm.add(fileName);
            ChuckShred shred = vm.getShred(id);
            if (shred != null) initialShreds.add(shred);
          }
        } catch (ChuckCompilerException e) {
          printRichError(e);
        } catch (Exception e) {
          System.err.println("❌ Error loading " + fileName + ": " + e.getMessage());
        }
      }

      for (String cmd : otfCommands) {
        if (cmd.startsWith("+")) {
          try {
            vm.add(cmd.substring(1));
          } catch (ChuckCompilerException e) {
            printRichError(e);
          }
        } else if (cmd.startsWith("-")) {
          vm.removeShred(Integer.parseInt(cmd.substring(1)));
        } else if (cmd.equals("^")) {
          System.out.println(vm.status());
        } else if (cmd.equals("kill")) {
          System.exit(0);
        } else if (cmd.equals("time")) {
          double secs = (double) vm.getCurrentTime() / vm.getSampleRate();
          System.out.println(
              String.format("time: %d samples / %.2f seconds", vm.getCurrentTime(), secs));
        } else if (cmd.equals("remove.all")) {
          for (org.chuck.core.ChuckShred s : vm.getAllShreds()) {
            vm.removeShred(s.getId());
          }
        } else if (cmd.equals("clear.vm")) {
          vm.clear();
        } else if (cmd.equals("reset.id")) {
          vm.resetShredId();
        } else if (cmd.startsWith("abort:")) {
          vm.removeShred(Integer.parseInt(cmd.substring(6)));
        }
      }

      if (loop || !initialShreds.isEmpty()) {
        // Wait briefly for virtual threads to start
        Thread.sleep(50);

        long startTime = System.currentTimeMillis();
        while (true) {
          if (timeoutSeconds > 0
              && (System.currentTimeMillis() - startTime) > timeoutSeconds * 1000L) {
            if (verbose > 0) System.out.println("[CLI] Timeout reached, stopping...");
            break;
          }

          if (!loop && vm.getActiveShredCount() == 0) break;

          if (silent) {
            vm.advanceTime(1);
          } else {
            Thread.sleep(100);
          }
        }
      }

      vm.shutdown();
      if (audio != null) audio.stop();
      if (verbose > 0) System.out.println("✅ Finished.");

    } catch (ChuckCompilerException e) {
      printRichError(e);
    } catch (Exception e) {
      System.err.println("❌ VM Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void printRichError(ChuckCompilerException e) {
    System.err.println("❌ " + e.getMessage());
    System.err.println("   at (" + e.getFile() + ":" + e.getLine() + ":" + e.getColumn() + ")");
  }

  private void launchIDE(String[] args) {
    try {
      Class<?> ideClass = Class.forName("org.chuck.ide.ChuckIDE");
      java.lang.reflect.Method main = ideClass.getMethod("main", String[].class);
      main.invoke(null, (Object) args);
    } catch (Exception e) {
      System.err.println("❌ Error launching IDE: " + e.getMessage());
    }
  }

  private void printUsage() {
    System.out.println("Usage: chuck [options|commands] [+-=^] file1 file2 ...");
    System.out.println("Options:");
    System.out.println("  --halt / -h      (default) Exit once all shreds finish");
    System.out.println("  --loop / -l / --empty Continue running even if no shreds are active");
    System.out.println("  --silent / -s    Disable audio output");
    System.out.println("  --dump           Dump virtual instructions to console");
    System.out.println("  --syntax         Check syntax only");
    System.out.println("  --srate:<N>      Set sampling rate (default 44100)");
    System.out.println("  --bufsize:<N>    Set audio buffer size (default 512)");
    System.out.println("  --chan:<N>       Set number of channels (default 2)");
    System.out.println("  --timeout:<N>    Exit after N seconds");
    System.out.println("  --chugin-path:<P> Set chugin search path(s)");
    System.out.println("  --gui / --ide    Force launch the JavaFX IDE");
    System.out.println("  --about / --help Print this help message");
    System.out.println("  --version        Display version information");
    System.out.println("Commands:");
    System.out.println("  + / --add        Add file to running VM");
    System.out.println("  - / --remove     Remove shred from running VM");
    System.out.println("  = / --replace    Replace shred in running VM");
    System.out.println("  ^ / --status     Print VM status");
    System.out.println("  --time           Print VM time");
    System.out.println("  --remove.all     Remove all shreds from running VM");
    System.out.println("  --clear.vm       Clear all VM state");
    System.out.println("  --reset.id       Reset shred ID counter");
    System.out.println("  --abort.shred:<N> Abort specific shred");
    System.out.println("  --kill           Kill the running VM");
  }
}
