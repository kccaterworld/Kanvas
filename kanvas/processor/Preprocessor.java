package kanvas.processor;

/*
Steps:
Tokenize: Break .kanvas source into tokens (import, class, function, variable, etc.)
Parse: Recognize constructs (global vars, functions, classes)
Generate: Spit out equivalent Java code
Error reporting: Line numbers and clear messages

In:
```
import java.util.*;

ArrayList<Point> particles;

void setup() {
    particles = new ArrayList<>();
}

void draw() {
    for (Point p : particles) {
        // draw
    }
}
```

Out:
```
package com.kanvas;
import java.util.*;

public class Sketch_Generated extends KanvasSketch {
    private ArrayList<Point> particles;
    
    @Override
    public void setup() {
        particles = new ArrayList<>();
    }
    
    @Override
    public void draw() {
        for (Point p : particles) {
            // draw
        }
    }
}
```
*/
public class Preprocessor {
    
}
