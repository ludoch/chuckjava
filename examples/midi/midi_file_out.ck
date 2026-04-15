//-----------------------------------------------------------------------------
// name: midi_file_out.ck
// desc: Demonstrates recording multiple tracks to a standard MIDI file (SMF)
//       with custom tempo and time signature.
//-----------------------------------------------------------------------------

MidiFileOut mfo;
mfo.open("multi_track_test.mid");

// Set tempo (BPM) and time signature on the main track (Track 0)
mfo.setBpm(120.0);
mfo.setTimeSig(4, 4);

// Track 1: Bassline
mfo.addTrack("Bassline");
MidiMsg msg;
0.0 => msg.when;
0x90 => msg.data1; 36 => msg.data2; 100 => msg.data3; // Note On C2
mfo.write(msg);

0.5 => msg.when;
0x80 => msg.data1; 36 => msg.data2; 0 => msg.data3;   // Note Off
mfo.write(msg);

// Track 2: Melody
int melodyTrack => mfo.addTrack("Melody"); // You can also get track index

0.5 => msg.when;
0x90 => msg.data1; 60 => msg.data2; 110 => msg.data3; // Note On C4
mfo.write(melodyTrack, msg); // Write directly to specific track

1.0 => msg.when;
0x80 => msg.data1; 60 => msg.data2; 0 => msg.data3;   // Note Off
mfo.write(melodyTrack, msg);

// Close and save
mfo.close();
<<< "Saved multi_track_test.mid", "" >>>;
