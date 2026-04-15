package org.chuck.core;

import org.chuck.audio.ChuckUGen;
import org.chuck.midi.MidiFileIn;
import org.chuck.midi.MidiIn;
import org.chuck.midi.MidiPlayer;
import org.chuck.midi.MidiPoly;

/** Instruction to connect two UGens or MIDI objects (lhs => rhs). */
public class ChuckTo implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    if (shred.reg.getSp() < 2) {
      return;
    }
    Object rawRhs = shred.reg.popObject();
    Object rawLhs = shred.reg.popObject();

    if (rawRhs instanceof ChuckUGen rhs && rawLhs instanceof ChuckUGen lhs) {
      lhs.chuckTo(rhs);
    } else if (rawRhs instanceof MidiPoly poly) {
      if (rawLhs instanceof MidiPlayer player) {
        player.connect(poly);
      } else if (rawLhs instanceof MidiIn min) {
        // Support direct connection for MidiIn => MidiPoly
        min.connect(poly);
      }
    } else if (rawRhs instanceof MidiPlayer player) {
      if (rawLhs instanceof MidiFileIn file) {
        player.source(file);
      }
    }

    // Leave rhs on stack for chaining (e.g. a => b => c)
    shred.reg.pushObject(rawRhs);
  }
}
