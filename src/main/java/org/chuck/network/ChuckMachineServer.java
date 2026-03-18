package org.chuck.network;

import org.chuck.core.ChuckVM;

public class ChuckMachineServer {
    private final ChuckVM vm;
    private final OscIn oscIn;
    private final int port = 8888;

    public ChuckMachineServer(ChuckVM vm) {
        this.vm = vm;
        this.oscIn = new OscIn(vm);
    }

    public void start() {
        oscIn.addAddress("/chuck/add");
        oscIn.addAddress("/chuck/remove");
        oscIn.addAddress("/chuck/replace");
        oscIn.addAddress("/chuck/status");
        oscIn.addAddress("/chuck/kill");
        oscIn.port(port);
        System.out.println("Machine Server listening on port " + port);

        Thread.ofVirtual().name("Machine-Command-Handler").start(() -> {
            while (true) {
                try {
                    OscMsg msg = new OscMsg();
                    if (oscIn.recv(msg)) {
                        switch (msg.address) {
                            case "/chuck/add" -> {
                                String file = msg.getString(0);
                                System.out.println("Received OSC: add " + file);
                                vm.add(file);
                            }
                            case "/chuck/remove" -> {
                                int id = msg.getInt(0);
                                System.out.println("Received OSC: remove " + id);
                                vm.removeShred(id);
                            }
                            case "/chuck/replace" -> {
                                int id = msg.getInt(0);
                                String file = msg.getString(1);
                                System.out.println("Received OSC: replace " + id + " with " + file);
                                vm.removeShred(id);
                                vm.add(file);
                            }
                            case "/chuck/kill" -> {
                                System.out.println("Received OSC: kill");
                                System.exit(0);
                            }
                        }
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
