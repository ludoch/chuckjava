// power up!
// - philipd, stereo gewang

0.0 => float t;
Noise n;

// sweep shred
fun void sweep( float st, float inc, float durationSec, int c )
{
    n => TwoPole z => Pan2 p => dac; 
    1  => z.norm;
    0.1 => z.gain;
    
    st => float frq;
    Math.random2f( -1, 1 ) => float s;
    Math.random2f( -1, 1 ) => float e;
    
    if (durationSec < 0.1) 0.1 => durationSec;
    ( e - s ) / ( durationSec / 0.01 ) => float i; // Incremental pan per 10ms
    s => p.pan;
    Math.random2f( 0.94, 0.99 ) => z.radius;
    
    0.0 => float elapsed;
    while( elapsed < durationSec ) {
        Math.max( elapsed * 4.0, 1.0 ) * 0.1 => z.gain; 
        frq + inc * -0.02  => frq; 
        frq => z.freq;
        p.pan() + i => p.pan;
        10::ms => now;
        elapsed + 0.01 => elapsed;
    }

    n =< z;
    z =< p;
    p =< dac;
}

0 => int c;
dur durVal;
// time loop
while( true ) { 
    500::ms => durVal;
    if( Math.random2( 0, 10 ) > 3 ) durVal * 2.0 => durVal;
    if( Math.random2( 0, 10 ) > 6 ) durVal * 3.0 => durVal;
    
    1.0 => float durSec;
    spork ~ sweep( 220.0 * Math.random2( 1, 8 ), 
                   880.0 + Math.random2f( 100.0, 880.0 ),
                   durSec, c);
    1 + c => c; 
    durSec::second => now;
}
