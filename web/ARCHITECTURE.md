# Web API Module

The `web` module is an adapter around the application layer. It contains Spring Boot, JSON DTOs, HTTP validation, bearer-session handling, and error mapping. It contains no poker rules.

## Same-origin frontend delivery

Spring Boot is the production origin for the React SPA, REST API, and STOMP endpoint. `SpaController` serves non-cacheable entry HTML for supported host/player/TV routes. `StaticResourceConfig` gives Vite's content-hashed `/assets/**` files a one-year immutable cache policy.

The `bundle-frontend` Maven profile copies `frontend-app/dist` into `BOOT-INF/classes/static`. During development, Vite runs separately and proxies relative `/api` and `/ws` requests to Spring on port 8080.

## Runtime safeguards

- Opaque sessions expire, persist optionally, and are revoked on rejection, kick, or room closure.
- STOMP CONNECT, SEND, and SUBSCRIBE frames are authorized by actor, room, role, and approval state.
- HTTP and STOMP payloads are bounded.
- Actuator exposes health, info, and metrics, including active room count.
- Correlation IDs connect HTTP responses to structured logs.
- `memory` and `file` profiles select ephemeral or atomic JSON persistence.

## Implemented REST endpoints

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/v1/rooms` | Public |
| `GET` | `/api/v1/rooms/{code}` | Public safe snapshot |
| `POST` | `/api/v1/rooms/{code}/join-requests` | Public |
| `GET` | `/api/v1/rooms/{code}/me` | Player token |
| `GET` | `/api/v1/rooms/{code}/lobby` | Host token |
| `POST` | `/api/v1/rooms/{code}/members/{id}/approval` | Host token |
| `POST` | `/api/v1/rooms/{code}/members/{id}/rejection` | Host token |
| `DELETE` | `/api/v1/rooms/{code}/members/{id}` | Host token |
| `DELETE` | `/api/v1/rooms/{code}` | Host token |

## Privacy boundary

- Public room responses omit host ID and lobby members.
- Lobby responses require the matching room's host token.
- Player tokens cannot call host operations.
- A token issued for one room is rejected in another room.
- No REST response currently exposes hole cards.

## Initial session implementation

`InMemorySessionService` issues 256-bit opaque random tokens. This is appropriate for local/LAN development and proves the authorization flow. Sessions disappear when the server restarts and currently have no expiry.

The production milestone replaces this adapter with signed JWT access tokens and durable refresh sessions without changing `RoomManager` or `GameEngine`.

## Error contract

All expected failures use stable problem JSON with a machine-readable `code`. Controllers do not leak stack traces or mutable application objects.

## WebSocket/STOMP

Implemented endpoint: `/ws`. Clients send the same bearer token in the STOMP `CONNECT` `Authorization` header.

- `/topic/rooms/{code}/state` for public room state;
- `/topic/rooms/{code}/events` for public game events;
- `/user/queue/cards` for private cards;
- `/user/queue/turn` for private action requests;
- `/user/queue/errors` for command failures.

Client command destinations:

- `/app/rooms/{code}/start-hand` — host only;
- `/app/rooms/{code}/new-game` — host only;
- `/app/rooms/{code}/actions` — player only.

`GameplayEventRouter` is the privacy boundary. It never broadcasts `CardsDealt` or `ActionRequired` to room topics.
Every polymorphic event is wrapped in `SocketEnvelope(type, payload)`, giving TypeScript clients a stable discriminator.

## Deferred production slice

- Replace room-scoped opaque sessions with account-backed JWT access/refresh tokens.
- Replace file persistence and the local broker with PostgreSQL/Redis for multi-instance deployment.
- Add CI/CD deployment automation.
