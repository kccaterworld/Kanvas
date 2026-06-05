#!/usr/bin/env bash
set -e

mkdir -p build/classes

javac -d build/classes $(find kanvas -type f -name "*.java" | grep -v "/assets/templates/")

jar --create \
  --file build/kanvas.jar \
  --main-class kanvas.cli.Main \
  -C build/classes . \
  -C . kanvas
