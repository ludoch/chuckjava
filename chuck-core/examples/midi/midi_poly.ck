//-----------------------------------------------------------------------------
// name: midi_poly.ck
// desc: Demonstrates high-level automatic voice management with MidiPoly
//       No manual voice tracking or sporking needed!
//-----------------------------------------------------------------------------

MidiIn min;
MidiMsg msg;

// Open the first available MIDI input
if( !min.open(0) ) me.exit();

// Setup the polyphonic manager
MidiPoly poly => dac;

// Configure the instrument and polyphony
poly.setInstrument("Rhodey"); // Switch to "Wurley", "Mandolin", "SinOsc", etc.
poly.voices(12);              // Set maximum simultaneous voices

<<< "MidiPoly ready using:", poly.instrument(), "[", poly.voices(), "voices]" >>>;

// Main loop: simply pass messages to the poly manager
while( true )
{
    min => now;
    while( min.recv(msg) )
    {
        poly.onMessage(msg);
    }
}
