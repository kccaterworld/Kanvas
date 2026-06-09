#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

BUILD_DIR="build"
CLASSES_DIR="$BUILD_DIR/classes"
JAR_FILE="$BUILD_DIR/kanvas.jar"

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

mapfile -d '' JAVA_SOURCES < <(
  find ./src/kanvas \
    -type f \
    -name '*.java' \
    ! -path '*/assets/templates/*' \
    -print0
)

if [[ ${#JAVA_SOURCES[@]} -eq 0 ]]; then
  echo "No Java sources found." >&2
  exit 1
fi

javac --release 21 -d "$CLASSES_DIR" "${JAVA_SOURCES[@]}"

cp -r src/kanvas/assets "$CLASSES_DIR/kanvas/"

jar --create \
  --file "$JAR_FILE" \
  --main-class kanvas.cli.Main \
  -C "$CLASSES_DIR" .

echo "Built $JAR_FILE"
