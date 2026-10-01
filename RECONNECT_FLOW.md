# Frontend Reconnect Flow

The REST snapshot is authoritative; WebSocket events keep an already-bootstrapped screen current.

## Player page

1. Read `roomCode` from the route and the room-scoped player bearer token from durable browser storage.
2. Call `GET /api/v1/rooms/{code}/me` with `Authorization: Bearer ...`.
3. Render membership immediately:
   - `PENDING`: waiting screen (`privateState` is null).
   - `APPROVED`: table, stack, cards, community cards, and legal actions from `privateState`.
   - `REJECTED` or `KICKED`: terminal status screen.
4. Connect STOMP to `/ws` using the same Authorization header.
5. Subscribe to private queues before public topics: `/user/queue/status`, `/user/queue/cards`, `/user/queue/turn`, `/user/queue/commands`, `/user/queue/errors`.
6. Subscribe to `/topic/rooms/{code}/state` and `/topic/rooms/{code}/events`.
7. If the socket drops, keep rendering the last snapshot, reconnect with backoff, resubscribe, then call `/me` again to close any event gap.

## Host page

1. Read the host token and room code from durable browser storage.
2. Call `GET /api/v1/rooms/{code}/host-bootstrap`.
3. Render room state, lobby, and the first history page from that single response.
4. Connect STOMP and subscribe to `/user/queue/lobby`, `/user/queue/status`, `/user/queue/commands`, `/user/queue/errors`, plus room state/events.
5. After reconnect, repeat host bootstrap to close the event gap.

## Commands

- Generate one stable `commandId` before sending a command.
- Persist the command locally until `/user/queue/commands` acknowledges that ID.
- A disconnect before acknowledgement is ambiguous; resend the same ID and identical payload.
- `replayed: true` means the server had already applied it.
- Never generate a new ID merely because the socket reconnected.
- Remove pending commands on acknowledgement or a terminal command error.

## Server restart

With JSON persistence enabled, room/session recovery resumes from the last safe between-hand boundary. An interrupted active hand is intentionally rolled back rather than partially reconstructed.

Command receipts are also persisted when file persistence is enabled. A retry after restart is suppressed for the configured retention period (24 hours by default), even though full game events are deliberately not stored in the receipt cache.
