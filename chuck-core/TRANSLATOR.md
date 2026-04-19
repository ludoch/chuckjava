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
-   **Literals**: Multi-dimensional arrays, vector literals (`#(1,2)`), complex, and polar literals.

### 4. Expressions
-   **Operators**: Full mapping of arithmetic, comparison, and logical operators.
-   **Unary**: Support for `-` and `!`.
-   **Ternary**: Full support for `cond ? then : else`.
-   **Cast**: `(Type) value`.
-   **Built-ins**: `dac`, `adc`, `blackhole`, `now`, `me`, `sampleRate`.
-   **Introspection**: `typeof(x)` and `x instanceof Type`.

### 5. Concurrency
-   **Sporking**: `spork ~ call()` is mapped to `spork(() -> call())`.

### 6. Comments
-   Preservation of `//`, `/* */`, and `/** */` comments, prepended to the corresponding Java statements.

---

## Missing Features / Limitations

### 1. Function Definitions
-   **Status**: ❌ Missing implementation.
-   **Current Behavior**: Emits a comment `// function definition: name`.
-   **Needed**: Mapping ChucK `fun` to Java methods within the `Shred` class.

### 2. Class Definitions
-   **Status**: ❌ Missing implementation.
-   **Current Behavior**: Emits a comment `// class definition: name`.
-   **Needed**: Mapping ChucK classes to nested or separate Java classes.

### 3. Events & Polymorphism
-   **Status**: ⚠️ Partial.
-   **Current Behavior**: Basic `Event` usage works via method calls, but custom event classes and complex polymorphism are not yet handled.

### 4. Special UGen Members
-   **Status**: ⚠️ Partial.
-   **Current Behavior**: Maps `s.freq` and `s.gain` correctly.
-   **Limitation**: Some UGens might have members that don't follow the standard getter/setter pattern in Java.

### 5. Multi-variable Declarations with Mixed Init
-   **Status**: ⚠️ Partial.
-   **Limitation**: `int a, b=1, c;` might need more complex splitting logic in the visitor to produce valid Java.

### 6. Operator Overloading
-   **Status**: ❌ Missing.
-   **Limitation**: ChucK's `@operator+` cannot be directly translated to Java. It must be mapped to specific method names (e.g., `__op_plus`).

### 7. Global Variable Scope
-   **Status**: ⚠️ Partial.
-   **Current Behavior**: Handles `Machine.getGlobalObject("name")` when explicitly written in ChucK.
-   **Improvement**: Automatic detection of `global` keyword to map to `Machine.getGlobal*` in the DSL.
