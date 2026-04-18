// Pan4 test
SinOsc s => Pan4 p => dac;
.5 => s.gain;

while( true )
{
    // Cycle through channels
    for( -1.0 => float i; i <= 1.0; 0.1 +=> i )
    {
        i => p.pan;
        10::ms => now;
    }
    for( 1.0 => float i; i >= -1.0; 0.1 -=> i )
    {
        i => p.pan;
        10::ms => now;
    }
}
