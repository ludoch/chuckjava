package org.chuck.midi;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/**
 * MidiPlayer: A high-level MIDI sequencer that plays MidiFileIn sources. It handles the timing and
 * dispatches messages to a target (like MidiPoly).
 */
public class MidiPlayer extends ChuckObject {
  private MidiFileIn source;
  private MidiPoly target;
  private int playbackShredId = -1;
  private boolean isPlaying = false;
  private float speed = 1.0f;

  public MidiPlayer() {
    super(ChuckType.OBJECT);
  }

  /** Set the MIDI file source. */
  public void source(MidiFileIn source) {
    this.source = source;
  }

  /** Set the playback speed (1.0 is nominal). */
  public void speed(float s) {
    this.speed = s;
  }

  /** Connect to a target MidiPoly for automatic voice management. */
  public void connect(MidiPoly target) {
    this.target = target;
  }

  /** Starts playback in a new shred. */
  public void play() {
    if (source == null || isPlaying) return;

    ChuckVM vm = ChuckVM.CURRENT_VM.get();
    if (vm == null) return;

    isPlaying = true;
    playbackShredId =
        vm.spork(
            () -> {
              try {
                ChuckShred shred = ChuckShred.CURRENT_SHRED.get();
                MidiMsg msg = new MidiMsg();
                source.rewind();

                while (isPlaying && source.read(msg) > 0) {
                  if (msg.when > 0) {
                    long samples = (long) (msg.when * vm.getSampleRate() / speed);
                    shred.yield(samples);
                  }

                  if (!isPlaying) break;

                  if (target != null) {
                    target.onMessage(msg);
                  }
                }
              } finally {
                isPlaying = false;
                playbackShredId = -1;
              }
            });
  }

  /** Stops playback and kills the sequencer shred. */
  public void stop() {
    isPlaying = false;
    if (playbackShredId != -1) {
      ChuckVM vm = ChuckVM.CURRENT_VM.get();
      if (vm != null) {
        vm.removeShred(playbackShredId);
      }
      playbackShredId = -1;
    }
  }

  public boolean playing() {
    return isPlaying;
  }
}
