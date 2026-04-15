//-----------------------------------------------------------------------------
// name: midi_learn.ck
// desc: Demonstrates the IDE MIDI Learn feature
//-----------------------------------------------------------------------------

// 1. Declare a global variable. This automatically appears in the IDE's
//    "Control Surface" tab.
global float filterFreq;
500.0 => filterFreq;

// 2. Setup a simple patch
SawOsc osc => LPF filter => dac;
0.2 => osc.gain;

<<< "MIDI Learn Demo", "" >>>;
<<< "-----------------", "" >>>;
<<< "1. Open the 'Control' tab on the left panel of the IDE.", "" >>>;
<<< "2. You will see a slider for 'filterFreq'.", "" >>>;
<<< "3. Click the 'L' (Learn) button next to the slider.", "" >>>;
<<< "4. Move any knob or fader on your connected MIDI keyboard.", "" >>>;
<<< "5. The slider is now mapped! Move your MIDI knob to hear the filter sweep.", "" >>>;

// Main loop: apply the global variable to the filter
while( true )
{
    filterFreq => filter.freq;
    10::ms => now;
}
