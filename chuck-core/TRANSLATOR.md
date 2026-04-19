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
    -   Event Waiting: `event => now` is mapped to `advance(event)`. Supports array waiting: `event_array => now`.
-   **Unchuck (`!=>`)**: Mapped to `.unchuck(target)`.
-   **At-Chuck (`@=>`)**: Mapped to assignment (`=`).

### 3. Types & Variables
-   **Primitives**: 
    -   `int` -> `long`
    -   `float` -> `double`
    -   `dur` -> `ChuckDuration`
    -   `time` -> `long`
-   **Declarations & Scoping**:
    -   **Field Promotion**: All variable declarations (including those nested in `if` or `while` blocks) are promoted to `public` class fields to ensure cross-shred and cross-method visibility.
    -   **UGen Auto-Init**: `SinOsc s[10];` automatically generates a loop in `shred()` to instantiate all 10 oscillators.
    -   **Standard**: `SinOsc s;` -> `SinOsc s = new SinOsc();` (using current sample rate).
-   **Global Keyword**: `global int x;` is mapped to `Machine.getGlobalInt("x")` and `Machine.setGlobalInt("x", val)`.
-   **Literals**: Multi-dimensional arrays, vector literals (`#(1,2)`), complex, and polar literals.

### 4. Expressions
-   **High-Fidelity Duration Arithmetic**: `now % T` and `T1 + T2` are correctly mapped to `samp(now()).percent(T)` and `T1.plus(T2)` only when durations are involved, falling back to primitive arithmetic otherwise.
-   **Operators**: Full mapping of arithmetic, comparison (including duration-to-sample comparisons), and logical operators.
-   **Unary**: Support for `-` and `!`.
-   **Ternary**: Full support for `cond ? then : else`.
-   **Cast**: `(Type) value`.
-   **Built-ins**: `dac`, `adc`, `blackhole`, `now`, `me`, `sampleRate`.
-   **Introspection**: `typeof(x)` and `x instanceof Type`.

### 5. Definitions
-   **Function Definitions**: `fun void foo() { ... }` is mapped to a Java method within the `Shred` class.
-   **Class Definitions**: `class Bar { ... }` is mapped to an inner class within the `Shred` class.
-   **Interface Definitions**: `interface MyInt { fun void x(); }` is mapped to Java interfaces with abstract method signatures.

### 6. Concurrency
-   **Sporking**: `spork ~ call()` is mapped to `spork(() -> call())`.

### 7. Documentation & Comments
-   Preservation of `//`, `/* */`, and `/** */` comments, prepended to the corresponding Java statements.
-   Support for `@doc` tags in AST nodes.

---

## Technical Infrastructure

### Machine API
A `Machine` helper class provides static parity for ChucK's system calls:
- `Machine.loglevel()`
- `Machine.getGlobalInt/Float/Object(name)`
- `Machine.spork(Runnable)`
- `Machine.add(path)`

### UGen Consistency
Core UGens like `SinOsc`, `SndBuf`, and `Gain` have been extended with ChucK-idiomatic method overloads (`pos()`, `read()`, `gain()`, `last()`) and default constructors to ensure the translated Java code is clean and functional.

---

## Missing Features / Limitations

### 1. Events & Polymorphism
-   **Status**: ✅ Supported.
-   **Note**: Conjunctions (`e1 && e2 => now`) and Disjunctions (`e1 || e2 => now`) are now fully supported.

### 2. Multi-variable Declarations with Mixed Init
-   **Status**: ✅ Supported.
-   **Note**: `int a, b, c;` and `10 => int x;` patterns are correctly mapped as class fields.

### 3. Operator Overloading
-   **Status**: ⚠️ Partial.
-   **Limitation**: While operator methods (e.g., `__op__plus`) are correctly emitted, standard Java binary operators (`+`, `-`, etc.) won't automatically call these methods in the generated DSL.
