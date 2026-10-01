# API Audit — Before Frontend

Audit date: 2026-08-13

## Result

The implemented backend is internally consistent, but the previous API document mixed implemented and future contracts. `API_DESIGN.md` now labels every REST and STOMP surface by status.

## Confirmed implemented

- Room creation, public lookup, join request, host lobby, approval, rejection, kick, and room deletion
- Opaque room-scoped host/player bearer sessions
- Player `/me` recovery with private cards and legal actions when approved
- Authenticated STOMP CONNECT
- Start-hand, new-game, and player-action commands
- Required command IDs, duplicate suppression, command receipts, and conflict errors
- Public state/event publishing and private card/turn routing
- Optional safe-boundary JSON persistence

## Documentation claims that were not yet implemented

- State, paginated history, and host bootstrap were missing at audit time and were implemented in step 2.
- Lobby and private membership events were missing at audit time and were implemented in step 3 using private user queues.
- Room-topic subscription authorization

These are assigned to steps 2, 3, and 7 of the pre-frontend plan rather than being presented as available behavior.

## Intentional current constraints

- Single Spring Boot process
- No PostgreSQL, JWT, or Redis
- Opaque LAN bearer tokens
- In-process idempotency cache
- Optional JSON recovery only at safe between-hand boundaries
