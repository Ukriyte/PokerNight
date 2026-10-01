#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_URL="${POKERNIGHT_DEMO_BASE_URL:-http://localhost:8080}"
RESPONSE=""

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Required command not found: $1" >&2; exit 1; }
}

assert_json() {
  local expression="$1"
  local explanation="$2"
  echo "${RESPONSE}" | jq -e "${expression}" >/dev/null || {
    echo "ASSERTION FAILED: ${explanation}" >&2
    exit 1
  }
  echo "  ✓ ${explanation}"
}

request_json() {
  local title="$1"
  local expected_status="$2"
  shift 2
  local raw status
  raw="$(curl --silent --show-error -w $'\n%{http_code}' "$@")"
  status="${raw##*$'\n'}"
  RESPONSE="${raw%$'\n'*}"
  echo
  echo "========== ${title} =========="
  echo "HTTP ${status}"
  echo "${RESPONSE}" | jq .
  if [[ "${status}" != "${expected_status}" ]]; then
    echo "Expected HTTP ${expected_status}, received ${status}" >&2
    exit 1
  fi
}

bearer() { printf 'Authorization: Bearer %s' "$1"; }

require_command curl
require_command jq
require_command node

echo "PokerNight exhaustive frontend-protocol walkthrough: ${BASE_URL}"
echo "Tokens are printed only for learning. A real frontend stores them privately and never puts them in URLs."

request_json "1. Health check" 200 "${BASE_URL}/actuator/health"
assert_json '.status == "UP"' 'frontend may enable room creation because the backend is healthy'

request_json "2. Create a two-player room" 201 \
  -X POST "${BASE_URL}/api/v1/rooms" \
  -H 'Content-Type: application/json' \
  --data '{"hostName":"Protocol Host","startingChips":1000,"smallBlind":25,"bigBlind":50,"maximumPlayers":2,"botCount":0}'
ROOM_CODE="$(echo "${RESPONSE}" | jq -er '.roomCode')"
HOST_TOKEN="$(echo "${RESPONSE}" | jq -er '.hostToken')"
assert_json '.roomCode | length == 6' 'frontend receives a shareable six-character room code'
assert_json '.hostToken | length > 20' 'frontend receives and must persist a private host token'

request_json "3. Public room discovery" 200 "${BASE_URL}/api/v1/rooms/${ROOM_CODE}"
assert_json '.game.phase == "WAITING_FOR_PLAYERS" and .game.players == []' 'public route contains safe room state but no private membership data'

request_json "4A. Alice requests to join" 202 \
  -X POST "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/join-requests" \
  -H 'Content-Type: application/json' --data '{"displayName":"Alice"}'
ALICE_ID="$(echo "${RESPONSE}" | jq -er '.playerId')"
ALICE_TOKEN="$(echo "${RESPONSE}" | jq -er '.playerToken')"
assert_json '.status == "PENDING"' 'Alice UI enters the pending-approval screen'

request_json "4B. Bob requests to join" 202 \
  -X POST "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/join-requests" \
  -H 'Content-Type: application/json' --data '{"displayName":"Bob"}'
BOB_ID="$(echo "${RESPONSE}" | jq -er '.playerId')"
BOB_TOKEN="$(echo "${RESPONSE}" | jq -er '.playerToken')"

request_json "4C. Eve requests to join (rejection example)" 202 \
  -X POST "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/join-requests" \
  -H 'Content-Type: application/json' --data '{"displayName":"Eve"}'
EVE_ID="$(echo "${RESPONSE}" | jq -er '.playerId')"
EVE_TOKEN="$(echo "${RESPONSE}" | jq -er '.playerToken')"

request_json "5. Host sees complete lobby" 200 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/lobby" -H "$(bearer "${HOST_TOKEN}")"
assert_json '.members | length == 3' 'host lobby renders all three pending requests'

request_json "6A. Host approves Alice at seat 0" 200 \
  -X POST "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/members/${ALICE_ID}/approval" \
  -H 'Content-Type: application/json' -H "$(bearer "${HOST_TOKEN}")" --data '{"seatNumber":0}'

request_json "6B. Host approves Bob at seat 1" 200 \
  -X POST "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/members/${BOB_ID}/approval" \
  -H 'Content-Type: application/json' -H "$(bearer "${HOST_TOKEN}")" --data '{"seatNumber":1}'

request_json "6C. Host rejects Eve" 200 \
  -X POST "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/members/${EVE_ID}/rejection" \
  -H "$(bearer "${HOST_TOKEN}")"
assert_json '.members[] | select(.playerId == "'"${EVE_ID}"'") | .status == "REJECTED"' 'host lobby records Eve as rejected'

request_json "6D. Rejected token can no longer bootstrap" 401 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/me" -H "$(bearer "${EVE_TOKEN}")"
assert_json '.code == "INVALID_TOKEN"' 'rejected player frontend must clear credentials and return to join screen'

request_json "7A. Host refresh bootstrap" 200 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/host-bootstrap" -H "$(bearer "${HOST_TOKEN}")"
assert_json '.room.game.players | length == 2' 'host refresh rebuilds both approved seats from one response'

request_json "7B. Alice refresh bootstrap before a hand" 200 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/me" -H "$(bearer "${ALICE_TOKEN}")"
assert_json '.status == "APPROVED" and .privateState.holeCards == null' 'approved player is seated but has no cards before a hand starts'

echo
echo "========== 8. REAL-TIME GAMEPLAY WALKTHROUGH =========="
echo "curl cannot speak STOMP. The following helper prints exactly what the frontend receives on public and private subscriptions."
POKERNIGHT_DEMO_BASE_URL="${BASE_URL}" \
POKERNIGHT_DEMO_ROOM_CODE="${ROOM_CODE}" \
POKERNIGHT_DEMO_HOST_TOKEN="${HOST_TOKEN}" \
POKERNIGHT_DEMO_ALICE_TOKEN="${ALICE_TOKEN}" \
POKERNIGHT_DEMO_BOB_TOKEN="${BOB_TOKEN}" \
  node "${PROJECT_DIR}/frontend-app/scripts/protocol-walkthrough.mjs"

request_json "9A. Public recovery after two hands" 200 "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/state"
assert_json '.game.handNumber == 2 and .game.handInProgress == false' 'public/TV frontend can recover the completed second hand without a token'

request_json "9B. Player refresh after gameplay" 200 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/me" -H "$(bearer "${ALICE_TOKEN}")"
assert_json '.privateState.validActions == []' 'player action controls are disabled between hands'

request_json "10A. First history page" 200 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/history?limit=2" -H "$(bearer "${HOST_TOKEN}")"
NEXT_CURSOR="$(echo "${RESPONSE}" | jq -er '.nextCursor')"
NEWEST_SEQUENCE="$(echo "${RESPONSE}" | jq -er '.entries[0].sequence')"
assert_json '.entries | length == 2' 'history UI receives a bounded newest-first page'
assert_json '.entries[0].sequence > .entries[1].sequence' 'history entries are ordered newest first'

request_json "10B. Older history page using cursor" 200 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/history?limit=2&cursor=${NEXT_CURSOR}" -H "$(bearer "${HOST_TOKEN}")"
assert_json ".entries[0].sequence < ${NEWEST_SEQUENCE}" 'load-more uses nextCursor and does not repeat the newest entry'

request_json "11A. Host kicks Bob" 200 \
  -X DELETE "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/members/${BOB_ID}" -H "$(bearer "${HOST_TOKEN}")"
assert_json '.members[] | select(.playerId == "'"${BOB_ID}"'") | .status == "KICKED"' 'host lobby marks Bob as kicked'

request_json "11B. Kicked token is revoked" 401 \
  "${BASE_URL}/api/v1/rooms/${ROOM_CODE}/me" -H "$(bearer "${BOB_TOKEN}")"
assert_json '.code == "INVALID_TOKEN"' 'kicked player frontend clears its local session'

request_json "12A. Create an isolated second room with one bot" 201 \
  -X POST "${BASE_URL}/api/v1/rooms" -H 'Content-Type: application/json' \
  --data '{"hostName":"Bot Room Host","startingChips":500,"smallBlind":10,"bigBlind":20,"maximumPlayers":2,"botCount":1}'
SECOND_CODE="$(echo "${RESPONSE}" | jq -er '.roomCode')"
assert_json '.roomCode != "'"${ROOM_CODE}"'"' 'second room receives a different isolation key'

request_json "12B. Second room starts independently" 200 "${BASE_URL}/api/v1/rooms/${SECOND_CODE}/state"
assert_json '.game.handNumber == 0 and (.botPlayerIds | length == 1)' 'bot lifecycle is room-scoped and the first room cannot change this state'

echo
echo "========== WALKTHROUGH PASSED =========="
echo "Room ${ROOM_CODE}: two human hands, reconnect, retries, history, rejection, and kick verified."
echo "Room ${SECOND_CODE}: independent bot roster verified."
echo "Use FRONTEND_CONTRACT.md as the field-by-field source of truth."
