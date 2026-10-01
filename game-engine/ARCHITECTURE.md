# Game Engine Boundaries

This module is pure Java. It has no Spring, WebSocket, database, JWT, room, human-player, or bot dependency.

## Packages

| Package | Responsibility | Deliberately does not know about |
|---|---|---|
| `model` | Stable value objects and commands | Turns, sockets, persistence |
| `deck` | Create, shuffle, and draw a 52-card deck | Players and poker streets |
| `table` | Seat occupancy and dealer/blind rotation | Cards, bets, chips |
| `hand` | Private dealing, burn cards, community streets | Betting and winners |
| `betting` | One street's turns, legal actions, and commitments | Whole-hand pots and hand evaluation |
| `pot` | Whole-hand contribution ledger and side-pot layers | Turn order and cards |
| `state` | Legal high-level phase transitions | Transport and UI state |

## Composition

The `GameEngine` aggregate coordinates these components:

```text
TableSeats + PositionRotator ──> TablePositions
TablePositions + HandDealer ───> DealtHand
TablePositions + stacks ───────> BettingRound
BettingActionResult ───────────> ContributionLedger
ContributionLedger + folds ───> PotCalculator
```

The aggregate should remain thin: it sequences components, publishes domain events, and enforces cross-component invariants. Rules stay in the component that owns them.

## Coordinator API

```java
GameEngine game = new GameEngine(GameRules.standard());
game.seatPlayer("p1", "Alice", 0, 1_000);
game.seatPlayer("p2", "Bob", 1, 1_000);

game.startHand();
List<GameEvent> events = game.drainEvents();

game.submit(new PlayerDecision("p1", PlayerAction.CALL, null));
PublicGameState publicState = game.publicState();
PrivatePlayerState aliceState = game.privateState("p1");
```

`publicState()` never contains hole cards. `privateState(playerId)` contains only that player's cards.

`drainEvents()` is a one-time event outbox. The Spring application layer calls it only after an engine command succeeds, then routes:

- public events to the room;
- `CardsDealt` and `ActionRequired` only to the named player;
- bot action requests to a bot adapter.

The engine never calls a WebSocket, sleeps for a bot, signs a token, or stores a room.

## Betting amount convention

`PlayerDecision(RAISE, amount)` uses a **raise-to target** for the current street.

Example: the current bet is 50 and a player sends amount 150. Their resulting street contribution is 150. This convention is used at every adapter boundary to avoid mixing “raise by” and “raise to.”

## Mutation policy

- Stateful domain objects expose immutable snapshots.
- Collections returned from public APIs are immutable copies.
- Tests can inject a deterministic `CardShuffler`; production uses `SecureRandom`.
- Pot calculation is pure and reproducible.
- Timers and bot delays belong outside this module.
