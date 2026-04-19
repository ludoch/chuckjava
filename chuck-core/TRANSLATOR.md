# ChucK to Java DSL Translator

The `ChuckToDSLConverter` is a source-to-source translator that converts ChucK (`.ck`) scripts into pure Java code utilizing the ChucK-Java Fluent DSL.

## Current Capabilities

### 1. Control Flow
-   **Standard Loops**: `while`, `for`, `do-while`, `do-until`.
-   **ChucK Specifics**: `until(cond)` (mapped to `while(!cond)`), `repeat(N)` (mapped to `for`).
-   **Iterators**: `foreach` loops (`for (iter : collection)`) are mapped to Java's enhanced for-loop.
-   **Branching**: `if/else` and `switch/case/default`.
-   **Control**: `break` and `continue`.

### 2. Audio & Connections
-   **The Chuck Operator (`=>`)**: 
    -   UGen to UGen: Mapped to `.chuck(target)`.
    -   Value to UGen Parameter: Mapped to `.member(value)`.
    -   Time Advancement: `dur => now` is mapped to `advance(dur)`.
    -   Event Waiting: `event => now` is mapped to `advance(event)`.
-   **Unchuck (`!=>`)**: Mapped to `.unchuck(target)`.
-   **At-Chuck (`@=>`)**: Mapped to assignment (`=`).

### 3. Types & Variables
-   **Primitives**: 
    -   `int` -> `long`
    -   `float` -> `double`
    -   `dur` -> `ChuckDuration`
    -   `time` -> `long`
-   **Declarations**:
    -   Standard: `SinOsc s;` -> `SinOsc s = new SinOsc(sampleRate());`
    -   Primitive init: `10 => int i;` -> `long i = 10;`
    -   Array: `int data[16];` -> `long[] data = new long[16];`
-   **Global Keyword**: `global int x;` is mapped to `Machine.getGlobalInt("x")` and `Machine.setGlobalObject("x", val)`.
-   **Literals**: Multi-dimensional arrays, vector literals (`#(1,2)`), complex, and polar literals.

### 4. Expressions
-   **Operators**: Full mapping of arithmetic, comparison, and logical operators.
-   **Unary**: Support for `-` and `!`.
-   **Ternary**: Full support for `cond ? then : else`.
-   **Cast**: `(Type) value`.
-   **Built-ins**: `dac`, `adc`, `blackhole`, `now`, `me`, `sampleRate`.
-   **Introspection**: `typeof(x)` and `x instanceof Type`.

### 5. Definitions
-   **Function Definitions**: `fun void foo() { ... }` is mapped to a Java method within the `Shred` class.
-   **Class Definitions**: `class Bar { ... }` is mapped to an inner class within the `Shred` class.

### 6. Concurrency
-   **Sporking**: `spork ~ call()` is mapped to `spork(() -> call())`.

### 7. Comments
-   Preservation of `//`, `/* */`, and `/** */` comments, prepended to the corresponding Java statements.

---

## Missing Features / Limitations

### 1. Events & Polymorphism
-   **Status**: ⚠️ Partial.
-   **Current Behavior**: `event => now` and `e.signal()` work.
-   **Limitation**: Complex event synchronization (conjunctions/disjunctions) and custom event subclassing might need more validation.

### 2. Special UGen Members
-   **Status**: ⚠️ Partial.
-   **Current Behavior**: Maps `s.freq` and `s.gain` correctly.
-   **Limitation**: Some UGens might have members that don't follow the standard getter/setter pattern in Java.

### 3. Multi-variable Declarations with Mixed Init
-   **Status**: ⚠️ Partial.
-   **Limitation**: `int a, b=1, c;` might need more complex splitting logic in the visitor to produce valid Java.

### 4. Operator Overloading
-   **Status**: ⚠️ Partial.
-   **Current Behavior**: Standardizes names to `__op__plus` etc.
-   **Limitation**: Standard Java binary operators (`+`, `-`, etc.) won't automatically call these methods in the generated DSL.

### 5. Smart Assignment (Type Inference)
-   **Status**: ⚠️ Experimental.
-   **Limitation**: The converter uses basic name guessing (`i`, `j`, `val`, etc.) to decide between `=` and `.chuck()` for the `=>` operator when the LHS type is unknown.
