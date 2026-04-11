package org.chuck.network;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.core.ChuckVM;

public class ChuckMachineServer {
  private static final Logger logger = Logger.getLogger(ChuckMachineServer.class.getName());
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
    logger.info("Machine Server listening on port " + port);

    Thread.ofVirtual()
        .name("Machine-Command-Handler")
        .start(
            () -> {
              while (true) {
                try {
                  OscMsg msg = new OscMsg();
                  if (oscIn.recv(msg)) {
                    switch (msg.address) {
                      case "/chuck/add" -> {
                        String file = msg.getString(0);
                        logger.info("Received OSC: add " + file);
                        vm.add(file);
                      }
                      case "/chuck/remove" -> {
                        int id = msg.getInt(0);
                        logger.info("Received OSC: remove " + id);
                        vm.removeShred(id);
                      }
                      case "/chuck/replace" -> {
                        int id = msg.getInt(0);
                        String file = msg.getString(1);
                        logger.info("Received OSC: replace " + id + " with " + file);
                        vm.removeShred(id);
                        vm.add(file);
                      }
                      case "/chuck/kill" -> {
                        logger.info("Received OSC: kill");
                        System.exit(0);
                      }
                    }
                  }
                  Thread.sleep(10);
                } catch (Exception e) {
                  if (vm.getLogLevel() >= 2) {
                    logger.log(Level.SEVERE, "Error in Machine Server", e);
                  }
                }
              }
            });
  }

  public void stop() {
    oscIn.close();
  }
}
