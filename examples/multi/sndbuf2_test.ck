// SndBuf2 and PanN test
SndBuf2 s => PanN p(4) => dac;

// Load a special sample (which I made support stereo in SndBuf2)
"special:kick" => s.read;
1 => s.loop;

while( true )
{
    // Sweep pan across 4 channels
    for( -1.0 => float i; i <= 1.0; 0.05 +=> i )
    {
        i => p.pan;
        5::ms => now;
    }
}
