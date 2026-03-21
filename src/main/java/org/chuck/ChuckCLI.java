package org.chuck;

import org.chuck.audio.ChuckAudio;
import org.chuck.compiler.*;
import org.chuck.core.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChuckCLI {
    private int sampleRate = 44100;
    private int bufferSize = 512;
    private int numChannels = 2;
    private boolean silent = false;
    private boolean loop = false;
    private boolean dump = false;
    private boolean useAntlr = false;
    private boolean syntaxOnly = false;
    private boolean forceGui = false;
    private int verbose = 1;
    private int timeoutSeconds = -1;
    private List<String> filesToAdd = new ArrayList<>();
    private List<String> otfCommands = new ArrayList<>();

    public void run(String[] args) {
        if (args.length == 0) {
            org.chuck.ide.ChuckIDE.main(args);
            return;
        }

        parseArgs(args);

        if (forceGui) {
            org.chuck.ide.ChuckIDE.main(args);
            return;
        }

        if (syntaxOnly) {
            checkSyntax();
            return;
        }

        // If we only have OTF commands and no initial files/loop, just send them and exit
        if (filesToAdd.isEmpty() && !loop && !otfCommands.isEmpty()) {
            sendOtfCommands();
            return;
        }

        startVM();
    }

    private void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--help") || arg.equals("-h") || arg.equals("--about")) {
                printUsage();
                System.exit(0);
            } else if (arg.equals("--version")) {
                System.out.println("ChucK-Java version 0.1.0 (JDK 25)");
                System.exit(0);
            } else if (arg.equals("--loop") || arg.equals("-l")) {
                loop = true;
            } else if (arg.equals("--halt")) {
                loop = false;
            } else if (arg.equals("--silent") || arg.equals("-s")) {
                silent = true;
            } else if (arg.equals("--dump")) {
                dump = true;
            } else if (arg.equals("--antlr")) {
                useAntlr = true;
            } else if (arg.equals("--syntax")) {
                syntaxOnly = true;
            } else if (arg.equals("--gui") || arg.equals("--ide")) {
                forceGui = true;
            } else if (arg.startsWith("--srate:")) {
                sampleRate = Integer.parseInt(arg.substring("--srate:".length()));
            } else if (arg.startsWith("--bufsize:")) {
                bufferSize = Integer.parseInt(arg.substring("--bufsize:".length()));
            } else if (arg.startsWith("--chan:") || arg.startsWith("--out:") || arg.startsWith("--in:")) {
                numChannels = Integer.parseInt(arg.substring(arg.indexOf(':') + 1));
            } else if (arg.startsWith("--verbose:")) {
                verbose = Integer.parseInt(arg.substring("--verbose:".length()));
            } else if (arg.startsWith("--timeout:")) {
                timeoutSeconds = Integer.parseInt(arg.substring("--timeout:".length()));
            } else if (arg.equals("+") || arg.equals("--add")) {
                if (i + 1 < args.length) {
                    otfCommands.add("+" + args[++i]);
                }
            } else if (arg.equals("-") || arg.equals("--remove")) {
                if (i + 1 < args.length) {
                    otfCommands.add("-" + args[++i]);
                }
            } else if (arg.equals("=") || arg.equals("--replace")) {
                if (i + 1 < args.length) {
                    otfCommands.add("=" + args[++i]);
                }
            } else if (arg.equals("^") || arg.equals("--status")) {
                otfCommands.add("^");
            } else if (arg.startsWith("-")) {
                System.err.println("Unknown option: " + arg);
            } else {
                filesToAdd.add(arg);
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
            }
            oscOut.send(msg);
            System.out.println("Sent OTF command: " + cmd);
        }
    }

    private void checkSyntax() {
        for (String fileName : filesToAdd) {
            try {
                String source = Files.readString(Paths.get(fileName));
                if (useAntlr) {
                    org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(source);
                    org.chuck.compiler.ChuckANTLRLexer lexer = new org.chuck.compiler.ChuckANTLRLexer(input);
                    org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
                    org.chuck.compiler.ChuckANTLRParser parser = new org.chuck.compiler.ChuckANTLRParser(tokens);
                    parser.program();
                } else {
                    org.chuck.compiler.ChuckLexer lexer = new org.chuck.compiler.ChuckLexer(source);
                    org.chuck.compiler.ChuckParser parser = new org.chuck.compiler.ChuckParser(lexer.tokenize());
                    parser.parse();
                }
                System.out.println("✅ Syntax check passed: " + fileName);
            } catch (Exception e) {
                System.err.println("❌ Syntax error in " + fileName + ": " + e.getMessage());
            }
        }
    }

    private void startVM() {
        try {
            if (verbose > 0) {
                System.out.println("🎸 ChucK-Java (JDK 25) - " + (silent ? "Silent Mode" : "Real-time Audio"));
            }

            ChuckVM vm = new ChuckVM(sampleRate);
            vm.setAntlrEnabled(useAntlr);
            vm.addPrintListener(System.out::println);
            
            if (loop) {
                org.chuck.network.ChuckMachineServer server = new org.chuck.network.ChuckMachineServer(vm);
                server.start();
            }

            ChuckAudio audio = null;
            if (!silent) {
                audio = new ChuckAudio(vm, bufferSize, numChannels, sampleRate);
                audio.setVerbose(verbose);
                audio.start();
            } else {
                // Silent engine: advance time as fast as possible
                Thread.ofVirtual().name("ChucK-Silent-Engine").start(() -> {
                    while (true) {
                        vm.advanceTime(1);
                        
                        // Periodic RMS monitoring if verbose
                        if (verbose >= 2 && vm.getCurrentTime() % 44100 == 0) {
                            double sumSq = 0;
                            for (int c = 0; c < numChannels; c++) {
                                float s = vm.getDacChannel(c).tick(vm.getCurrentTime());
                                sumSq += (double)s * s;
                            }
                            double rms = Math.sqrt(sumSq / numChannels);
                            System.out.printf("[Silent Engine] RMS: %.9f at time %d\n", rms, vm.getCurrentTime());
                        }
                        
                        try { Thread.sleep(0); } catch (InterruptedException e) { break; }
                    }
                });
            }

            List<ChuckShred> initialShreds = new ArrayList<>();

            for (String fileName : filesToAdd) {
                try {
                    String source = Files.readString(Paths.get(fileName));
                    int id = vm.run(source, fileName);
                    ChuckShred shred = vm.getShred(id);
                    if (shred != null) initialShreds.add(shred);
                } catch (Exception e) {
                    System.err.println("❌ Error loading " + fileName + ": " + e.getMessage());
                }
            }

            // OTF commands in same process
            for (String cmd : otfCommands) {
                if (cmd.startsWith("+")) {
                    vm.add(cmd.substring(1));
                } else if (cmd.startsWith("-")) {
                    vm.removeShred(Integer.parseInt(cmd.substring(1)));
                } else if (cmd.equals("^")) {
                    System.out.println("Status: " + initialShreds.size() + " initial shreds sporked.");
                }
            }

            if (loop || !initialShreds.isEmpty()) {
                long startTime = System.currentTimeMillis();
                while (true) {
                    if (timeoutSeconds > 0 && (System.currentTimeMillis() - startTime) > timeoutSeconds * 1000L) {
                        if (verbose > 0) System.out.println("[CLI] Timeout reached, stopping...");
                        break;
                    }
                    boolean anyRunning = false;
                    for (ChuckShred s : initialShreds) {
                        if (!s.isDone()) {
                            anyRunning = true;
                            break;
                        }
                    }
                    if (!loop && !anyRunning) break;
                    Thread.sleep(100);
                }
            }

            if (audio != null) audio.stop();
            if (verbose > 0) System.out.println("✅ Finished.");

        } catch (Exception e) {
            System.err.println("❌ VM Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void printUsage() {
        System.out.println("Usage: chuck [options|commands] [+-=^] file1 file2 ...");
        System.out.println("Options:");
        System.out.println("  --halt / -h      (default) Exit once all shreds finish");
        System.out.println("  --loop / -l      Continue running even if no shreds are active");
        System.out.println("  --silent / -s    Disable audio output");
        System.out.println("  --dump           Dump virtual instructions to console");
        System.out.println("  --syntax         Check syntax only");
        System.out.println("  --srate:<N>      Set sampling rate (default 44100)");
        System.out.println("  --bufsize:<N>    Set audio buffer size (default 512)");
        System.out.println("  --chan:<N>       Set number of channels (default 2)");
        System.out.println("  --timeout:<N>    Exit after N seconds");
        System.out.println("  --gui / --ide    Force launch the JavaFX IDE");
        System.out.println("  --about / --help Print this help message");
        System.out.println("  --version        Display version information");
        System.out.println("  --antlr          Use ANTLR4 parser mode");
        System.out.println("Commands:");
        System.out.println("  + / --add        Add file to running VM");
        System.out.println("  - / --remove     Remove shred from running VM");
        System.out.println("  = / --replace    Replace shred in running VM");
        System.out.println("  ^ / --status     Print VM status");
    }
}
