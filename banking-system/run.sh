#!/usr/bin/env bash
# Runs the app. Data files are read/written in ./data relative to where you run this.
set -e
[ -d out ] || ./build.sh
java -cp out com.bank.Main
