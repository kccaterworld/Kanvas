# Kanvas Syntax

Kanvas source files use the `.kvs` extension. The language is designed as a
small, Java-compatible format for sketches: imports, variables, and functions
look like Java, while the Kanvas preprocessor wraps top-level declarations in a
generated Java class.

> Note: the `.kvs` preprocessor is not implemented yet. This document defines
> the syntax that the preprocessor will support.

## Imports

Use Java import statements at the top of the file. Standard Java libraries and
Kanvas libraries can be used together.

```kanvas
import java.util.*;
import com.kanvas.graphics.*;
```

## Variables

Variables use Java types and Java initialization syntax. Variables declared at
the top level become fields of the generated class.

```kanvas
int particleCount = 100;
float speed = 2.5f;
ArrayList<String> labels = new ArrayList<>();
```

## Functions

Functions use Java parameter types, return types, and block syntax.

```kanvas
int add(int a, int b) {
    return a + b;
}

void setup() {
    println("Sketch initialized");
}

void draw() {
    background(30);
}
```

`setup()` and `draw()` are conventional sketch lifecycle functions. Runtime
support for calling them will be added with the Kanvas graphics runtime.

## Java Interop

Kanvas code can use Java classes directly after importing them. Generic types,
constructors, method calls, and ordinary Java expressions retain their Java
syntax.

```kanvas
import java.util.ArrayList;

ArrayList<String> messages = new ArrayList<>();

void setup() {
    messages.add("Hello from Java");
    System.out.println(messages.get(0));
}
```

## Example Sketches

### Option 1

```kanvas
import java.util.*;
import com.kanvas.graphics::*;

ArrayList<Point> particles = new ArrayList<>();

void setup() {
    size(800, 600);
    background(30);
    println("Sketch initialized!");
}

void draw() {
    background(30);
}
```

### Option 2

```kanvas
package com.kanvas;

import java.util.*;
import com.kanvas.graphics::*;

public class MySketch {
    private ArrayList<Point> particles = new ArrayList<>();

    public void setup() {
        size(800, 600);
        background(30);
        println("Sketch initialized!");
    }

    public void draw() {
        background(30);
    }
}
```
