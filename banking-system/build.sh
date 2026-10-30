#!/usr/bin/env bash
# Compiles every .java file under src/ into out/
set -e
rm -rf out && mkdir out
javac -Xlint:all -d out $(find src -name "*.java")
echo "Build OK -> out/"
