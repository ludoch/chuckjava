// test @construct with arguments (Foo f(7) syntax)
class Counter
{
    0 => int n;

    fun @construct( int start )
    {
        start => n;
    }

    // overload: two-arg constructor
    fun @construct( int start, int step )
    {
        start * step => n;
    }
}

// zero-arg (pre-ctor only)
Counter c0;
// one-arg constructor
Counter c1(7);
// two-arg constructor
Counter c2(3, 4);
// new syntax
new Counter(10) @=> Counter @ c3;

<<< c0.n, c1.n, c2.n, c3.n >>>;
