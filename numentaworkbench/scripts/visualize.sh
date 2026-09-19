#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
mvn -q compile exec:java -Dexec.mainClass=org.numenta.workbench.AnomalySwingVisualizer
