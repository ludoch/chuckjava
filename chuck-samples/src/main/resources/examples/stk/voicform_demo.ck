// voicform_demo.ck
// Improved singing voice demo

VoicForm v => dac;
0.5 => v.gain;

// Vowel presets: 0:eee, 1:ihh, 2:ehh, 3:aaa, 4:ahh, 5:aww, 6:ohh, 7:uhh
[0, 4, 6, 3] @=> int vowels[];

while(true) {
    for(0 => int i; i < vowels.size(); i++) {
        vowels[i] => v.phoneme;
        Math.random2f(200, 300) => v.freq;
        
        1.0 => v.noteOn;
        0.5::second => now;
        
        1.0 => v.noteOff;
        0.1::second => now;
    }
}
