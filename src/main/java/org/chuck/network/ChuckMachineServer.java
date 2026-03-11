package org.chuck.network;

import org.chuck.core.ChuckVM;
import java.io.File;

public class ChuckMachineServer {
    private final ChuckVM vm;
    private final OscIn oscIn;
    private final int port = 8888;

    public ChuckMachineServer(ChuckVM vm) {
        this.vm = vm;
        this.oscIn = new OscIn(vm);
    }

    public void start() {
        oscIn.port(port);
        System.out.println("Machine Server listening on port " + port);

        setupHandlers();
    }

    private void setupHandlers() {
        // We use a separate thread to poll for events from OscIn
        Thread.ofVirtual().name("Machine-Command-Handler").start(() -> {
            OscIn.OscEvent addEvent = oscIn.event("/chuck/add");
            OscIn.OscEvent removeEvent = oscIn.event("/chuck/remove");
            OscIn.OscEvent replaceEvent = oscIn.event("/chuck/replace");
            OscIn.OscEvent statusEvent = oscIn.event("/chuck/status");
            OscIn.OscEvent killEvent = oscIn.event("/chuck/kill");

            while (true) {
                try {
                    OscMsg addMsg = addEvent.nextMsg();
                    if (addMsg != null) {
                        String file = (String) addMsg.getArgs().get(0);
                        System.out.println("Received OSC: add " + file);
                        vm.add(file);
                    }

                    OscMsg removeMsg = removeEvent.nextMsg();
                    if (removeMsg != null) {
                        int id = (int) removeMsg.getArgs().get(0);
                        System.out.println("Received OSC: remove " + id);
                        vm.removeShred(id);
                    }

                    OscMsg replaceMsg = replaceEvent.nextMsg();
                    if (replaceMsg != null) {
                        int id = (int) replaceMsg.getArgs().get(0);
                        String file = (String) replaceMsg.getArgs().get(1);
                        System.out.println("Received OSC: replace " + id + " with " + file);
                        vm.removeShred(id);
                        vm.add(file);
                    }
                    
                    OscMsg killMsg = killEvent.nextMsg();
                    if (killMsg != null) {
                        System.out.println("Received OSC: kill");
                        System.exit(0);
                    }

                    Thread.sleep(10);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void stop() {
        oscIn.close();
    }
}
