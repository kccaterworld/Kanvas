# Project Configuration

Each Kanvas project has a `kanvas.toml` file in its root directory. Paths in the
file are relative to that directory.

Kanvas commands find the project by looking for `kanvas.toml` in the current
directory and then in each parent directory, so `kanvas build` and `kanvas run`
work from anywhere inside a project, such as its `src/` folder.

## Example

```toml
configVersion = 1
name = "my-sketch"
version = "0.1.0"
author = "Developer Name"
description = "A Kanvas sketch project"
mainClass = "Main"
classpath = []
dependencies = []

[modules]
srcDirs = ["src"]
buildDir = "build"

[compiler]
target = 21
encoding = "UTF-8"
```

`name`, `version`, `author`, `description`, `mainClass`, `classpath`, and
`dependencies` are top-level keys. They must come before the first `[table]`
header; anything after a header belongs to that table.

The file is checked when it's loaded. Kanvas reports every problem it finds at
once, including keys it doesn't recognize, so a typo such as `srcDir` is an
error rather than being silently ignored:

```text
kanvas.toml has 2 problems:
  - compiler.target must be a whole number, found "twenty-one"
  - Unknown key 'modules.srcDir'
```

## Fields

| Field | Required | Default | Description |
| ----- | -------- | ------- | ----------- |
| `configVersion` | No | `1` | Version of this file's format. Kanvas refuses versions it doesn't know. |
| `name` | Yes | — | Project name. |
| `version` | Yes | — | Project version. |
| `author` | No | `""` | Project author or organization. |
| `description` | No | `""` | Short description of the project. |
| `mainClass` | No* | — | The class `kanvas run` starts. See [Main Class](#main-class). |
| `classpath` | No | `[]` | Extra JARs or class directories to compile and run against. |
| `dependencies` | No | `[]` | JAR filenames inside the project's `lib/` directory. See [Dependencies](#dependencies). |
| `modules.srcDirs` | Yes | — | Directories containing `.kvs` and `.java` source files. Must list at least one. |
| `modules.resourceDirs` | No | `["assets"]` | Directories containing images and other assets. Not processed by the build yet. |
| `modules.buildDir` | No | `"build"` | Directory for everything the build produces. See [Build Output](#build-output). |
| `compiler.target` | No | `21` | Java release to compile for, passed to `javac --release`. Sketches need `21` or newer. |
| `compiler.encoding` | No | `"UTF-8"` | Encoding of `.kvs` and `.java` source files. |
| `compiler.flags` | No | `[]` | Extra `javac` options, such as `["-Xlint:all"]`. |
| `runtime.jvmArgs` | No | `[]` | Options for the JVM that `kanvas run` starts, such as `["-Xmx1g"]`. |
| `runtime.programArgs` | No | `[]` | Arguments passed to the program's `main` method. |
| `runtime.env` | No | `{}` | Environment variables for the program, such as `{ MODE = "debug" }`. |
| `packaging.jarName` | No | `"<name>.jar"` | JAR filename for packaging. |
| `packaging.icon` | No | — | Icon file for packaging. |
| `packaging.nativeTargets` | No | `[]` | Native package targets, such as `"windows"`, `"macos"` or `"linux"`. |
| `packaging.format` | No | `"thin"` | `"thin"`, `"fat"`, `"jar-and-deps"` or `"launcher"`. |

\* `mainClass` is required to use `kanvas run`. Libraries can leave it out.

`compiler.target` may be written as a number (`21`) or a string (`"21"`).

The `[packaging]` table is accepted but not used yet, because there is no
`kanvas package` command. New projects leave it out.

Build profiles (a `[profiles]` table) are not supported yet and are reported as
an error.

## Build Output

`kanvas build` writes everything under `modules.buildDir`:

```text
build/
  generated/   Java generated from .kvs files
  classes/     compiled .class files
  resources/   processed assets (empty until the resource pipeline exists)
  output/      packaged artifacts (empty until `kanvas package` exists)
```

## Main Class

For a sketch, use the class name of the `.kvs` file. `src/main.kvs` becomes the
class `Main` (also written as `kanvas.generated.Main`), and `my-sketch.kvs`
becomes `MySketch`:

```toml
mainClass = "Main"
```

For Java code, use the fully qualified class name:

```toml
mainClass = "com.example.ParticleSketch"
```

This corresponds to `src/com/example/ParticleSketch.java`. A Java class with no
package, such as `src/App.java`, is written as just `"App"`.

## Dependencies

Place dependency JARs in the project's `lib/` directory and list their
filenames in the top-level `dependencies` array:

```toml
dependencies = [
  "guava-31.1-jre.jar",
  "gson-2.10.1.jar"
]
```

Each entry is a filename inside `lib/`. Listed JARs are on the classpath when the
project is compiled and when it runs, and the build fails with a clear message
if one is missing.

There is no group:artifact:version syntax, no Maven repository resolution, no
transitive dependencies, and no lockfile yet.

## Printing the Effective Configuration

`kanvas config` prints the project's full configuration, with every default
filled in. The output is a valid `kanvas.toml` that describes the same project,
so it can replace the original file:

```powershell
kanvas config            # the project containing the current directory
kanvas config path/to/project
```

## CLI Overrides

`kanvas config` accepts one-off overrides. CLI values win over `kanvas.toml`,
and `kanvas.toml` wins over built-in defaults. List values are comma-separated,
and an empty value clears a field:

```powershell
kanvas config . --target 17 --jar-name demo.jar --native-targets windows,linux
```

| Option | Overrides |
| ------ | --------- |
| `--name`, `--version`, `--author`, `--description` | the matching top-level key |
| `--main-class` | `mainClass` |
| `--classpath`, `--dependencies` | `classpath`, `dependencies` |
| `--src-dirs`, `--resource-dirs`, `--build-dir` | `modules.srcDirs`, `modules.resourceDirs`, `modules.buildDir` |
| `--target`, `--encoding` | `compiler.target`, `compiler.encoding` |
| `--jar-name`, `--icon`, `--native-targets`, `--format` | the matching `packaging` key |

## Older `kanvas.toml` Files

Files written before `configVersion` existed still load, and are treated as
`configVersion = 1`. They may contain two keys that are now ignored:

- `modules.outputDir`: replaced by `modules.buildDir`. Output now goes to
  `build/classes` and `build/generated` instead of `build/classes/classes`.
- `packaging.version`: packaging uses the project `version`.
