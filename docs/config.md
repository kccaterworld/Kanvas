# Project Configuration

Each Kanvas project has a `kanvas.json` file in its root directory. Paths in the
file are relative to that project directory unless noted otherwise.

## Example

```json
{
  "name": "particle-sketch",
  "version": "2.1.0",
  "author": "Artist Name",
  "description": "A particle animation",
  "mainClass": "com.kanvas.ParticleSketch",
  "modules": {
    "srcDirs": ["src"],
    "outputDir": "build/classes"
  },
  "dependencies": [
    "guava-31.1-jre.jar"
  ],
  "compiler": {
    "target": "11",
    "encoding": "UTF-8"
  },
  "packaging": {
    "jarName": "particle-sketch.jar",
    "nativeTargets": ["windows", "macos", "linux"]
  }
}
```

## Fields

The current loader provides defaults for every field, so no field is strictly
required to parse a config file. A runnable application should define
`mainClass`.

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `name` | No | Project directory name | Project name. |
| `version` | No | `0.1.0` | Project version. |
| `author` | No | Empty string | Project author or organization. |
| `description` | No | Empty string | Short description of the project. |
| `mainClass` | For runnable apps | None | Fully qualified Java entry class, such as `com.example.App`. |
| `modules.srcDirs` | No | `["src"]` | Directories containing project source files. |
| `modules.outputDir` | No | `build/classes` | Directory for compiled classes. |
| `dependencies` | No | `[]` | Dependency filenames. The current loader resolves each entry inside the project's `lib/` directory. |
| `compiler.target` | No | `11` | Java compiler target version. |
| `compiler.encoding` | No | `UTF-8` | Source file encoding. |
| `packaging.jarName` | No | `<name>.jar` | Filename used when packaging the project. |
| `packaging.nativeTargets` | No | `[]` | Requested native package targets, such as `windows`, `macos`, or `linux`. |

## Main Class

Use a fully qualified Java class name:

```json
{
  "mainClass": "com.kanvas.ParticleSketch"
}
```

This corresponds to:

```text
src/com/kanvas/ParticleSketch.java
```

## Dependencies

Place dependency JARs in the project's `lib/` directory and list their
filenames:

```json
{
  "dependencies": [
    "guava-31.1-jre.jar",
    "gson-2.10.1.jar"
  ]
}
```

Maven coordinate resolution may be added later. The current loader treats each
entry as a file beneath `lib/`.
