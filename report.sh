#!/usr/bin/env bash
# Generates the static Allure report and serves it on all interfaces
# so it can be opened from another machine by IP.
#
# Usage: ./report.sh [port]   (default port: 8085)

set -euo pipefail

PORT="${1:-8085}"
cd "$(dirname "${BASH_SOURCE[0]}")"

mvn allure:report

REPORT_DIR="target/site/allure-maven-plugin"
if [ ! -d "$REPORT_DIR" ]; then
  echo "Report directory not found: $REPORT_DIR" >&2
  exit 1
fi

IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo "Serving Allure report on:"
echo "  http://localhost:${PORT}"
[ -n "$IP" ] && echo "  http://${IP}:${PORT}"

cd "$REPORT_DIR"
python3 -m http.server "$PORT" --bind 0.0.0.0
