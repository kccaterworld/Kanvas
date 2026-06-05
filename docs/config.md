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
  "classpath": ["lib/local-helper.jar"],
  "dependencies": [
    "guava-31.1-jre.jar"
  ],
  "compiler": {
    "target": "21",
    "encoding": "UTF-8"
  },
  "packaging": {
    "jarName": "particle-sketch.jar",
    "version": "2.1.0",
    "icon": "assets/icon.png",
    "nativeTargets": ["windows", "macos", "linux"]
  }
}
```

## Fields

The loader validates the core fields needed to create an effective config.
A runnable application should also define `mainClass`.

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `name` | Yes | None | Project name. |
| `version` | Yes | None | Project version. |
| `author` | No | Empty string | Project author or organization. |
| `description` | No | Empty string | Short description of the project. |
| `mainClass` | For runnable apps | None | Fully qualified Java entry class, such as `com.example.App`. |
| `modules.srcDirs` | Yes | None | Directories containing project source files. |
| `modules.outputDir` | Yes | None | Directory for compiled classes. |
| `classpath` | No | `[]` | Extra classpath entries, resolved relative to the project root. |
| `dependencies` | No | `[]` | Dependency filenames. The current loader resolves each entry inside the project's `lib/` directory. |
| `compiler.target` | Yes | None | Java compiler target version. |
| `compiler.encoding` | Yes | None | Source file encoding. |
| `packaging.jarName` | Yes | None | Filename used when packaging the project. |
| `packaging.version` | No | Project `version` | Version used for packaged output. |
| `packaging.icon` | No | None | Icon path used by packaging tools. |
| `packaging.nativeTargets` | No | `[]` | Requested native package targets, such as `windows`, `macos`, or `linux`. |

## CLI Overrides

Commands that load config can apply one-off overrides. CLI values win over
`kanvas.json`, and `kanvas.json` wins over built-in defaults.

```powershell
kanvas config . --target 17 --jar-name demo.jar --native-targets windows,linux
```

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
