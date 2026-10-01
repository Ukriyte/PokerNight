#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROFILE="${1:-memory}"
PORT="${POKERNIGHT_PORT:-8080}"

select_java() {
  local candidates=()
  if [[ -n "${POKERNIGHT_JAVA:-}" ]]; then
    candidates+=("${POKERNIGHT_JAVA}")
  fi
  candidates+=("/opt/homebrew/opt/openjdk/bin/java" "$(command -v java || true)")

  local candidate version
  for candidate in "${candidates[@]}"; do
    [[ -x "${candidate}" ]] || continue
    version="$("${candidate}" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    if [[ "${version}" =~ ^[0-9]+$ ]] && (( version >= 21 )); then
      echo "${candidate}"
      return 0
    fi
  done

  echo "PokerNight requires Java 21 or newer. Install it with 'brew install openjdk', or set POKERNIGHT_JAVA to its java executable." >&2
  return 1
}

if [[ "${PROFILE}" != "memory" && "${PROFILE}" != "file" ]]; then
  echo "Usage: $0 [memory|file]" >&2
  exit 2
fi

cd "${PROJECT_DIR}"
JAVA_BIN="$(select_java)"

echo "Building the React frontend and packaged PokerNight application..."
"${SCRIPT_DIR}/build-packaged-app.sh" -DskipTests

echo
echo "Starting backend at http://localhost:${PORT} using the '${PROFILE}' profile"
echo "Health check: http://localhost:${PORT}/actuator/health"
echo "Press Ctrl-C to stop."
echo

JAVA_ARGS=(
  "--spring.profiles.active=${PROFILE}"
  "--server.port=${PORT}"
)

if [[ "${PROFILE}" == "file" ]]; then
  DATA_DIR="${POKERNIGHT_DATA_DIR:-${PROJECT_DIR}/data}"
  JAVA_ARGS+=("--pokernight.persistence.directory=${DATA_DIR}")
  echo "Persistent snapshots: ${DATA_DIR}"
fi

exec "${JAVA_BIN}" -jar "${PROJECT_DIR}/web/target/web-0.1.0-SNAPSHOT.jar" "${JAVA_ARGS[@]}"
