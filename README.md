# Kanvas

Kanvas is a creative coding and rendering toolkit for Java. You can create, configure, build, and package visual projects using a simple CLI, project templates, and a preprocessor for sketch syntax. It's intended to be easy to learn while offering customizability and power for more complex projects.

Kanvas is currently still in early development, and many features may not be implemented yet.

## Status

![Build](https://github.com/kccaterworld/Kanvas/actions/workflows/build.yml/badge.svg)&nbsp;&nbsp;
![Version](https://img.shields.io/badge/dynamic/yaml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fkccaterworld%2FKanvas%2Fmain%2F.github%2Fbadges%2Fversion.yml&query=%24.version&label=version&color=blue)&nbsp;&nbsp;

Kanvas is still in its early stages. The CLI, config format, templates, and preprocessing pipeline are being built out.

TODO before alpha release:

- [X] `kanvas create` with template selection
- [X] `kanvas.toml` config format and `ConfigLoader`
- [X] Project templates (`kanvas-sketch`, `java-lib`, `mixed-project`)
- [X] File-by-file preprocessor
- [X] Entire project preprocessor
- [X] `BuildManager` to run preprocessing, compilation, and packaging steps
- [X] `kanvas run`, connecting to `KanvasRunner`
- [X] End-to-end `kanvas build` and `kanvas run` commands
- [ ] Finish standard library and implement drawing features
- [ ] `JavaCompiler` to wrap javac and report errors (not necessary for alpha)
- [ ] `DependencyResolver` to auto-download JARs from Maven Central (not necessary for alpha)
- [ ] Proper test suite and CI workflow (not necessary for alpha)

## Features

- Create new sketch/app/library projects from templates
- `kanvas.toml` for project metadata, compiler settings, dependencies, and packaging
- Preprocess `.kvs` files into Java source code

## Usage

```java
java -jar Kanvas.jar create my-project --type=sketch - Create a new project from a template

java -jar Kanvas.jar build - Build the current project
```

## Installation

Kanvas is currently in early alpha and is built from source.

### Requirements

- Java 21 or newer
- PowerShell or Bash
- Git

### Build From Source

Clone the repository:

```powershell
git clone https://github.com/kccaterworld/Kanvas.git
cd Kanvas
./compile.ps1
```

```bash
git clone https://github.com/kccaterworld/Kanvas.git
cd Kanvas
./compile.sh
```

### Run JAR

```shell
java -jar build/Kanvas.jar
```

## Planned Features

- Packaging projects into runnable JARs and eventually native executables
- Dependency management and integration
- Custom build system
- Runnable `.kvs` files
- GUI app with text editor, project management, and live preview
- More drawing features
- Graphics libraries
- Math libraries
- GPU acceleration (maybe)
- Template projects for games, data visualization, generative art, etc.
- Community-contributed projects and libraries

## Build Stack

- Preprocessor: Java
- CLI: Java
- Standard Libraries: Java
- Config: JSON (for now)
- Runtime: Java (planned)
- Build system: Java, Kotlin (planned)
