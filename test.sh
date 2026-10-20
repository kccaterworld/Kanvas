#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

JUNIT_VERSION="1.11.4"
JUNIT_SHA256="b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc"
JUNIT_URL="https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"
JUNIT_JAR="build/lib/junit-platform-console-standalone-$JUNIT_VERSION.jar"
TEST_CLASSES_DIR="build/test-classes"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac

bash compile.sh

if [[ ! -f "$JUNIT_JAR" ]]; then
  mkdir -p "$(dirname "$JUNIT_JAR")"
  curl -fsSL -o "$JUNIT_JAR.download" "$JUNIT_URL"
  if command -v sha256sum > /dev/null; then actual="$(sha256sum "$JUNIT_JAR.download" | cut -d' ' -f1)"
  else actual="$(shasum -a 256 "$JUNIT_JAR.download" | cut -d' ' -f1)"; fi
  if [[ "$actual" != "$JUNIT_SHA256" ]]; then
    rm -f "$JUNIT_JAR.download"
    echo "Checksum mismatch for $JUNIT_URL (expected $JUNIT_SHA256, got $actual)" >&2
    exit 1
  fi
  mv "$JUNIT_JAR.download" "$JUNIT_JAR"
fi

rm -rf "$TEST_CLASSES_DIR"
mkdir -p "$TEST_CLASSES_DIR"
mapfile -d '' TEST_SOURCES < <(find ./tests/unit -type f -name '*.java' -print0)
javac --release 21 -cp "build/classes$SEP$JUNIT_JAR" -d "$TEST_CLASSES_DIR" "${TEST_SOURCES[@]}"

java -jar "$JUNIT_JAR" execute \
  --class-path "$TEST_CLASSES_DIR${SEP}build/classes" \
  --scan-class-path \
  --fail-if-no-tests \
  --disable-banner
