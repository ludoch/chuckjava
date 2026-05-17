package org.chuck.core;

import static org.chuck.core.ChuckDSL.*;

/** ChucK BPM utility class. */
public class BPM extends ChuckObject {
  public static ChuckDuration quarterNote = ms(500);
  public static ChuckDuration eighthNote = ms(250);
  public static ChuckDuration sixteenthNote = ms(125);
  public static ChuckDuration thirtysecondNote = ms(62.5);

  public BPM() {
    super(ChuckType.OBJECT);
  }

  public void tempo(double beat) {
    double spb = 60.0 / beat;
    quarterNote = second().times(spb);
    eighthNote = quarterNote.times(0.5);
    sixteenthNote = eighthNote.times(0.5);
    thirtysecondNote = sixteenthNote.times(0.5);
  }
  
  public ChuckDuration quarterNote() { return quarterNote; }
  public ChuckDuration eighthNote() { return eighthNote; }
  public ChuckDuration sixteenthNote() { return sixteenthNote; }
  public ChuckDuration thirtysecondNote() { return thirtysecondNote; }
}
