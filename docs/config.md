# Project Configuration

Each Kanvas project has a `kanvas.toml` file in its root directory. Paths in the
file are relative to that project directory unless noted otherwise.

## Example

```toml
name = "test-sketch"
version = "0.1.0"
author = "Developer Name"
description = "A Kanvas sketch project"

[build]
mainClass = "Main"
classpath = []
dependencies = [] // Dependencies not yet supported

[modules]
srcDirs = ["src"]
exclude = [] // Exclude not added yet
outputDir = "build/classes"

[compiler]
target = "21"
encoding = "UTF-8" // Separate encodings not yet supported

[packaging]
jarName = "test-sketch.jar"
version = "0.1.0"
icon = "icon.png" // Icon support not yet implemented
nativeTargets = ["windows", "macos", "linux"] // Native packaging not yet supported
```

## Fields

The loader validates the core fields needed to create an effective config.
A runnable application should also define `mainClass`.

| Field | Required | Default | Description |
| ----- | -------- | ------- | ----------- |
| `name` | Yes | None | Project name. |
| `version` | Yes | None | Project version. |
| `author` | No  | Empty string | Project author or organization. |
| `description` | No  | Empty string | Short description of the project. |
| `mainClass` | No* | None | Fully qualified Java entry class, such as `com.example.App`. |
| `classpath` | No  | `[]` | Extra classpath entries, resolved relative to the project root. |
| `dependencies` | No  | `[]` | Dependency filenames. The current loader resolves each entry inside the project's `lib/` directory. |
| `modules.srcDirs` | Yes | None | Directories containing project source files. |
| `modules.outputDir` | Yes | None | Directory for compiled classes. |
| `compiler.target` | Yes | None | Java compiler target version. |
| `compiler.encoding` | Yes | None | Source file encoding. |
| `packaging.jarName` | Yes | None | Filename used when packaging the project. |
| `packaging.version` | No  | Project `version` | Version used for packaged output. |
| `packaging.icon` | No  | None | Icon path used by packaging tools. |
| `packaging.nativeTargets` | No  | `[]` | Requested native package targets, such as `windows`, `macos`, or `linux`. |

* `mainClass` is not strictly required for all project types, but it is required for runnable applications like sketches.

## CLI Overrides

Commands that load config can apply one-off overrides. CLI values win over
`kanvas.toml`, and `kanvas.toml` wins over built-in defaults.

```powershell
kanvas config . --target 17 --jar-name demo.jar --native-targets windows,linux
```

## Main Class

Use a fully qualified Java class name:

```toml
mainClass = "com.kanvas.ParticleSketch"
```

This corresponds to:

```text
src/com/kanvas/ParticleSketch.java
```

## Dependencies

Place dependency JARs in the project's `lib/` directory and list their
filenames:

```toml
[dependencies]
values = [
  "guava-31.1-jre.jar",
  "gson-2.10.1.jar"
  ]
```

Maven coordinate resolution will be added later. The current loader ignores anything in the `dependencies` field.
