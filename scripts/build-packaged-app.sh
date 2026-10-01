#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_DIR}/frontend-app"
if [[ -d node_modules ]]; then
  npm run build
else
  npm ci
  npm run build
fi

cd "${PROJECT_DIR}"
mvn clean -Pbundle-frontend -pl web -am package "${@}"

echo "Packaged application: ${PROJECT_DIR}/web/target/web-0.1.0-SNAPSHOT.jar"
