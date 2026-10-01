#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${POKERNIGHT_E2E_PORT:-8080}"

export POKERNIGHT_PORT="${PORT}"
exec "${SCRIPT_DIR}/start-backend.sh" memory
