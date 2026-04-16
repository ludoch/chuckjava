package org.chuck.examples;

import org.chuck.audio.util.Gain;
import org.chuck.audio.util.SndBuf;
import org.chuck.core.ChuckArray;
import org.chuck.host.ChuckHost;

/**
 * JAVA DSL VERSION: PRO GRID SEQUENCER
 * -----------------------------------
 * This class mirrors the ChucK sequencer logic using pure Java DSL.
 * It demonstrates real-time interaction with the IDE grid.
 */
public class SequencerDSL {
  public static void main(String[] args) throws Exception {
    ChuckHost host = new ChuckHost();
    
    // 1. Setup Drum Kit (8 tracks)
    SndBuf[] kit = new SndBuf[8];
    Gain master = new Gain();
    master.setGain(0.6f);
    host.connect(master, host.dac());

    String[] paths = {
      "examples/data/kick.wav",
      "examples/data/snare.wav",
      "examples/data/hihat.wav",
      "examples/data/hihat-open.wav",
      "examples/book/digital-artists/audio/clap_01.wav",
      "examples/book/digital-artists/audio/cowbell_01.wav",
      "examples/book/digital-artists/audio/click_01.wav",
      "examples/data/snare-hop.wav"
    };

    for (int i = 0; i < 8; i++) {
      kit[i] = new SndBuf();
      kit[i].read(paths[i]);
      host.connect(kit[i], master);
      kit[i].setPos(kit[i].getSamples()); // Silent initially
    }

    // 2. Timing and Logic loop
    long T = (long) (host.getSampleRate() * 0.125); // 125ms steps
    int step = 0;

    System.out.println("Java Sequencer Engine Online. Reading IDE Grid...");

    while (true) {
      // A. Move visual cursor in IDE
      host.getVM().setGlobalInt("seq_current_step", step % 16);

      // B. Read grid data from shared object
      Object obj = host.getVM().getGlobalObject("seq_pattern");
      if (obj instanceof ChuckArray data) {
        for (int r = 0; r < 8; r++) {
          if (data.getInt(r * 16 + (step % 16)) > 0) {
            kit[r].setPos(0); // Trigger!
          }
        }
      }

      host.advanceTime(T);
      step++;
    }
  }
}
