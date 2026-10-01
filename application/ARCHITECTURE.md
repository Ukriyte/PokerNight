# Application Module

The application module coordinates use cases around the pure game engine. It has no Spring, HTTP, WebSocket, JWT, Redis, or database dependency.

## RoomManager boundary

`RoomManager` is the public lobby API:

```java
RoomView room = manager.createRoom(hostId, hostName, config);
manager.requestToJoin(room.code(), playerId, displayName);
manager.approvePlayer(room.code(), hostId, playerId, seatNumber);
manager.rejectPlayer(room.code(), hostId, playerId);
manager.kickPlayer(room.code(), hostId, playerId);
manager.destroyRoom(room.code(), hostId);
manager.reapStaleRooms(Duration.ofHours(6));
```

Spring adapters will translate request payloads into these method calls and map `RoomOperationException.failure()` to stable client errors.

## Responsibility split

| Component | Owns | Does not own |
|---|---|---|
| `RoomManager` | Use-case lookup, room creation, stale cleanup | Poker rules, sockets |
| `Room` | One room's synchronized lobby mutation | Storage and transport |
| `RoomRepository` | Atomic room storage operations | Room business rules |
| `GameEngine` | Poker state and rules | Host approval and room codes |
| `RoomCodeGenerator` | Collision-resistant candidate codes | Uniqueness storage |
| `BotRosterFactory` | Bot identities and reserved seats | Decisions and poker state |
| `BotDecisionStrategy` | One decision from one immutable turn context | Rooms, engine mutation, transport, timers |
| `BotTurnProcessor` | Submit consecutive bot decisions through the normal command path | Bot intelligence and poker rules |
| `RoomSnapshotStore` | Save/load transport-neutral safe-boundary snapshots | JSON, SQL, filesystem details |

`RoomView` and `RoomMemberView` are immutable snapshots. No caller receives the mutable `Room` aggregate or its `GameEngine` reference.

## Concurrency

- Different rooms can mutate independently.
- Mutations inside one room are synchronized.
- Room creation uses repository-level atomic `putIfAbsent` semantics.
- Code collisions are retried rather than overwriting an existing room.

## Current scope

Implemented:

- Multiple isolated rooms
- Six-character secure room codes
- Host authorization
- Pending, approved, rejected, and kicked membership states
- Explicit seat assignment
- Configurable bot count, stable identities, and reserved bot seats
- Pure, replaceable bot decision strategy with injectable randomness
- Automatic bot turns using the same `PlayerDecision` command path as humans
- Optional room/lobby/stack/history recovery through a replaceable snapshot port
- Stale-room cleanup

Deferred to the next application milestones:

- Session/JWT identities
- Durable or distributed room storage

## Safe-boundary persistence

`RoomSnapshotStore` is an application port. The Spring module provides an atomic JSON-file adapter; the application module contains no Jackson or filesystem dependency.

Snapshots are emitted only while no hand is active. They include room configuration, bot identities derived from configuration, members and seats, chip stacks, dealer rotation metadata, completed hand number, and history. A crash during a hand restores the last safe state rather than guessing how much of an action reached disk.

## Bot boundary

Bots are deliberately split into three concerns:

1. `BotRosterFactory` creates room-scoped identities and seats the configured number of bots.
2. `BotDecisionStrategy` receives a `BotDecisionContext` containing only read-only facts for one turn. It returns a `BotDecision`; it cannot access or mutate a room or engine.
3. `BotTurnProcessor` notices a bot `ActionRequired` event, asks the strategy for a decision, and sends it through `Room.submitAction`, exactly like a human command.

No bot delay exists in these classes. A future UI-like delay belongs in a scheduler/transport adapter, keeping application tests and simulations fast.

## Gameplay service

`RoomGameService` is now the transport-independent command boundary:

```java
games.startHand(roomCode, hostId);
games.submitAction(roomCode, playerId, PlayerAction.CALL, null);
games.startNewGame(roomCode, hostId);
games.getPrivateState(roomCode, playerId);
games.getHistory(roomCode);
```

Commands mutate one synchronized `Room`, drain engine events exactly once, update a newest-first history projection, and return `GameCommandResult`. Spring decides how to route those events.

`GameplayEvent.Engine` wraps engine events. `GameplayEvent.NewGameStarted` is an application event because resetting a configured room is not a poker rule.
