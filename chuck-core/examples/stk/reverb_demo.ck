// reverb_demo.ck
// Lush algorithmic reverb with FreeVerb

// Create a short impulsive sound
Impulse i => FreeVerb f => dac;

// Configure FreeVerb
0.8 => f.roomSize;
0.5 => f.damp;
0.4 => f.mix;

while(true) {
    1.0 => i.next; // Trigger impulse
    1::second => now;
}
