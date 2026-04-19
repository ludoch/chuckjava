/** My Test Class */
class MyTest {
    int x;
    fun void setX(int val) { val => x; }
}

global int g_val;
Event e;

fun void notifier() {
    <<< "Notifying..." >>>;
    e.signal();
}

MyTest t;
10 => t.setX;
20 => g_val;

spork ~ notifier();
e => now;

<<< "Received!", g_val >>>;
