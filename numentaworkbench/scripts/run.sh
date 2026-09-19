#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
# IDs and detector names contain no spaces; Maven splits exec.args itself.
case "${1:-all}" in *[!a-zA-Z0-9_-]*) echo "Invalid example ID" >&2; exit 2;; esac
case "${2:-gaussian}" in gaussian|entropy|null|htm) ;; *) echo "Choose gaussian, entropy, null, or htm" >&2; exit 2;; esac
mvn -q compile exec:java "-Dexec.args=${1:-all} ${2:-gaussian}"
