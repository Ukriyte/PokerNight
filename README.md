# PokerNight v2

Frontend integration is frozen in [`FRONTEND_CONTRACT.md`](FRONTEND_CONTRACT.md). Use that document for REST, STOMP, TypeScript payloads, errors, and reconnect behavior.

Frontend implementation order, UI state machines, mobile safeguards, and acceptance criteria are defined in [`FRONTEND_IMPLEMENTATION_GUIDE.md`](FRONTEND_IMPLEMENTATION_GUIDE.md).

The active React implementation lives in [`frontend-app/`](frontend-app/) and is the frontend bundled into Spring Boot. The older `frontend/` directory is retained only as migration history and is not packaged.

Multi-room, server-authoritative Texas Hold'em written with Java 21 and Spring Boot.

## Current milestone

The framework-free `game-engine` now plays complete hands: seating, position rotation, secure dealing, betting streets, folds, raises, all-ins, hand evaluation, side pots, showdown payouts, and game completion. It exposes public/private snapshots and a transport-neutral domain-event outbox.

The `application` module adds isolated multi-room lobby management, host authorization, player approval/rejection/kicking, seat assignment, secure room codes, and stale-room cleanup.

The local-first backend now includes bots, reconnect snapshots, command idempotency, and optional atomic JSON persistence. PostgreSQL, JWT, and Redis are intentionally deferred.

The `web` module now provides the first Spring Boot REST slice for room creation, public recovery, join requests, authenticated lobby administration, and player approval recovery. It binds to `0.0.0.0:8080` for LAN development.

Gameplay commands are exposed through authenticated STOMP at `/ws`. Public state/events use room topics while hole cards and turn prompts use private user queues.

## Build

```bash
mvn test
```

Run the API after building:

```bash
mvn -pl web -am package
java -jar web/target/web-0.1.0-SNAPSHOT.jar
```

## Backend protocol demo

The helper scripts show the exact JSON returned to a frontend. In terminal 1, build and start the backend with in-memory data:

```bash
./scripts/start-backend.sh memory
```

The backend requires Java 21 or newer. The launcher prefers Homebrew OpenJDK when macOS's default `java` is older; set `POKERNIGHT_JAVA=/path/to/java` to choose another runtime explicitly.

In terminal 2, run the exhaustive frontend-protocol walkthrough:

```bash
./scripts/demo-backend.sh
```

The demo covers pending/approved/rejected/kicked members, host and player refresh, two complete hands, all betting streets, early fold completion, dealer rotation, duplicate-command replay, socket reconnect, private cards/turns, public state, history pagination, bots, and room isolation. It includes executable assertions and stops at the first contract mismatch.

The walkthrough deliberately uses `curl` for REST operations and a Node STOMP client for gameplay. Starting hands and submitting player actions are STOMP commands, not HTTP endpoints, so they cannot be represented accurately by `curl` alone. The script prints host/player bearer tokens for learning; treat those values as secrets.

To test restart persistence instead, start with `./scripts/start-backend.sh file`. Set `POKERNIGHT_PORT` to change the server port, or `POKERNIGHT_DEMO_BASE_URL` when the demo should call a different backend URL.

Maven may run with a newer JDK, but the project compiles to Java 21 bytecode.

## Frontend and same-origin packaging

During frontend development, run Spring on port 8080 and Vite separately. Vite proxies `/api` and `/ws`, so frontend code always uses relative same-origin URLs:

```bash
cd frontend-app
npm install
npm run dev
```

For one deployable Spring Boot jar:

```bash
cd frontend-app
npm ci
npm run build
cd ..
mvn clean -Pbundle-frontend -pl web -am package
java -jar web/target/web-0.1.0-SNAPSHOT.jar
```

The repeatable packaged build is also available as `./scripts/build-packaged-app.sh`. Run the real-backend browser acceptance suite with:

```bash
cd frontend-app
npx playwright install chromium webkit   # once per machine
npm run test:e2e
```

Playwright builds and starts the packaged Spring Boot jar itself. It tests Chromium, iPhone WebKit portrait, and the phone resume flow in landscape; it never targets the Vite mock environment.

The jar serves `/`, `/room/{code}`, `/room/{code}/play`, and `/room/{code}/tv`. Entry HTML is never cached; Vite's hashed `/assets/*` files are cached immutably for one year.

## Optional local persistence

Memory remains authoritative while the process is running. To preserve rooms, safe-boundary chip stacks, lobby membership, hand history, and opaque LAN sessions across restarts:

```bash
java -jar web/target/web-0.1.0-SNAPSHOT.jar \
  --pokernight.persistence.enabled=true \
  --pokernight.persistence.directory=./data
```

Files are written via temporary files and atomic replacement. Active hands are never serialized. If the process stops during a hand, restart restores the previous safe boundary, avoiding a partially applied bet or pot.

Idempotency receipts are retained for 24 hours by default and share the same optional persistence directory. Override with `--pokernight.idempotency.retention=PT12H` or adjust `--pokernight.idempotency.maximum-entries=10000`.

Room-scoped sessions expire after seven days by default. Override this with `--pokernight.sessions.retention=PT72H`. Kicking or rejecting a player immediately revokes that player's token; closing a room revokes every token belonging to it. The same checks apply to REST requests and every WebSocket SEND/SUBSCRIBE frame.

## Runtime profiles and operations

- `--spring.profiles.active=memory`: ephemeral rooms and sessions.
- `--spring.profiles.active=file`: atomic JSON persistence under `POKERNIGHT_DATA_DIR` or `./data`.
- `/actuator/health`: application and snapshot-directory health.
- `/actuator/info`: build/application identity.
- `/actuator/metrics`: JVM, HTTP, and `pokernight.rooms.active` metrics.
- Health component details are intentionally hidden from unauthenticated LAN clients; the overall `UP`/`DOWN` status remains available.
- Every HTTP response includes `X-Request-ID`; logs include the same ID as a structured key.
- Shutdown is graceful with a 20-second drain window.

Container build:

```bash
docker build -t pokernight-v2 .
docker run --rm -p 8080:8080 -v pokernight-data:/app/data pokernight-v2
```
