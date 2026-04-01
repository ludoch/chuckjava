0 => int count;

fun void inc(int depth) {
    <<< "inc called with depth", depth >>>;
    count++;
    if (depth > 1) {
        <<< "about to spork, depth=", depth >>>;
        spork~ inc(depth-1);
        <<< "sporked!" >>>;
    }
    samp => now;
}

spork~ inc(3);
5::samp => now;
<<< "final count:", count >>>;
