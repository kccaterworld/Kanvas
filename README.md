# Kanvas

![Build](https://github.com/kccaterworld/Kanvas/actions/workflows/build.yml/badge.svg)&nbsp;&nbsp;
![Version](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fkccaterworld%2FKanvas%2Fmain%2F.github%2Fbadges%2Fversion.yml&query=%24.version&label=version&color=blue)&nbsp;&nbsp;

Kanvas is a creative coding and rendering toolkit for Java. You can create, configure, build, and package visual projects using a simple CLI, project templates, and a preprocessor for sketch syntax. It's intended to be easy to learn while offering customizability and power for more complex projects.

Kanvas is officially in alpha stage, which is a big milestone for me, but users should still expect bugs, missing features, and breaking changes as I continue development. The core features are in place, but there's a lot more to build out before it's ready for production use. Feedback and contributions are very welcome! Please feel free to open issues or submit pull requests on GitHub.

## Features

- Create new sketch/app/library projects from templates
- `kanvas.toml` for project metadata, compiler settings, dependencies, and packaging
- Preprocess `.kvs` files into Java source code
- Run sketches with the JVM
- AWT-backed window with hardware-accelerated rendering via `BufferStrategy`

## Upcoming features

- [ ] More drawing features
- [ ] Math libraries
- [ ] GPU acceleration (maybe)
- [ ] Proper test suite and CI workflow
- [ ] `DependencyResolver` to auto-download JARs from Maven Central
- [ ] Dependency management and integration
- [ ] Fully custom build system
- [ ] Packaging projects into native executables
- [ ] GUI app with text editor, project management, and live preview
- [ ] Template projects for games, data visualization, generative art, etc.
- [ ] Community-contributed projects and libraries

## Installation

Kanvas can be built from the source or downloaded from GitHub. Once downloaded or builts, you can run the JAR to set it up and get working!

### 1. Build from source

**Windows (PowerShell):**

```powershell
git clone https://github.com/kccaterworld/Kanvas.git
cd Kanvas
.\compile.ps1
```

**macOS / Linux (Bash):**

```bash
git clone https://github.com/kccaterworld/Kanvas.git
cd Kanvas
chmod +x compile.sh kanvas
./compile.sh
```

Then run `build/kanvas.jar` with whatever flags you want

### 2. Set up the `kanvas` command

Run the built-in install command, which generates the appropriate wrapper for your platform and prints PATH instructions:

```sh
java -jar build/kanvas.jar install
```

To install to a custom directory instead of the default:

```sh
java -jar build/kanvas.jar install --dir /your/bin/directory
```

Follow the printed instructions to add the directory to your PATH, then restart your terminal.

### 3. Verify the install

```sh
kanvas --help
```

### Alternative: run directly with Java

If you don't want to set up PATH, you can always invoke the JAR directly:

```sh
java -jar /path/to/Kanvas/build/kanvas.jar <command>
```

Or from inside the repo:

```sh
java -jar build/kanvas.jar <command>
```

## Usage

### Create a project

```sh
kanvas create my-sketch --type kanvas-sketch
```

Available template types: `kanvas-sketch`, `java-app`, `java-lib`, `mixed-project`, `empty`

### Build and run

```sh
cd my-sketch
kanvas run
```

`kanvas run` preprocesses `.kvs` files, compiles, and launches the sketch in a window. Use `kanvas build` to compile without running.

### Edit your sketch

Open `src/main.kvs`. The default sketch looks like:

```java
void setup() {
    size(800, 600);
}

void draw() {
    background(30);
    fill(255, 80, 80);
    noStroke();
    ellipse(mouseX, mouseY, 60, 60);
}
```

`setup()` runs once on start. `draw()` runs every frame. Close the window to exit.

### kanvas.toml

The `mainClass` field in `kanvas.toml` accepts either a short name or a fully qualified name. For `.kvs` sketch projects, the preprocessor puts generated classes in the `kanvas.generated` package, so both of these work:

```toml
mainClass = "Main"
```

## Build Stack

- Preprocessor: Java
- CLI: Java
- Standard Libraries: Java
- Config: TOML
- Runtime: Java (AWT / BufferStrategy)
- Build system: Java
