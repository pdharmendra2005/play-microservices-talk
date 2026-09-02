#!/usr/bin/env bash
# Starts both services in the background. Ctrl-C stops both.
set -euo pipefail
cd "$(dirname "$0")"

(cd user-service  && sbt run) &
(cd order-service && sbt run) &

trap 'kill 0' SIGINT SIGTERM EXIT
wait
