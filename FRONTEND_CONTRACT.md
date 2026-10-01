# PokerNight v2 — Frozen Frontend Contract

Status: **backend contract frozen for the first functional frontend**  
Base URL: same origin as the React application  
REST prefix: `/api/v1`  
STOMP endpoint: `/ws` (native WebSocket, no SockJS)  
Contract date: 2026-08-13

This is the frontend's authoritative integration document. `API_DESIGN.md` explains design reasoning; this file defines what the client may rely on.

## 1. Authentication and identifiers

- Room codes are six uppercase alphanumeric characters.
- Creating a room returns a 64-character opaque host token.
- Requesting to join returns a 64-character opaque player token immediately, while membership is `PENDING`.
- REST sends `Authorization: Bearer <token>`.
- STOMP `CONNECT` sends the same value as the native `Authorization` header.
- Tokens are scoped to one room and one role (`HOST` or `PLAYER`), expire after seven days by default, and survive restart in the `file` profile.
- Rejecting/kicking a player revokes that player's token. Closing a room revokes every room token.
- Tokens are secrets. Never put them in URLs, logs, room topics, or browser-shared state.
- `commandId` is a client-generated UUID (maximum 100 characters). Retrying the identical command with the same ID is safe.

## 2. REST endpoints

| Method and path | Authentication | Success | Purpose |
|---|---|---:|---|
| `GET /api/v1/network-info` | Public | `200` | Resolve the phone-reachable LAN origin used in join links and QR codes |
| `POST /api/v1/rooms` | Public | `201` | Create room and host session |
| `GET /api/v1/rooms/{code}` | Public | `200` | Public room bootstrap |
| `GET /api/v1/rooms/{code}/state` | Public | `200` | Recover latest public game state |
| `POST /api/v1/rooms/{code}/join-requests` | Public | `202` | Register a pending player |
| `GET /api/v1/rooms/{code}/me` | Player | `200` | Recover membership, private cards, and legal actions |
| `GET /api/v1/rooms/{code}/lobby` | Host | `200` | Recover complete lobby membership |
| `GET /api/v1/rooms/{code}/host-bootstrap` | Host | `200` | Recover room, lobby, and first history page together |
| `POST /api/v1/rooms/{code}/members/{playerId}/approval` | Host | `200` | Assign a pending player to a seat |
| `POST /api/v1/rooms/{code}/members/{playerId}/rejection` | Host | `200` | Reject and revoke a pending player |
| `DELETE /api/v1/rooms/{code}/members/{playerId}` | Host | `200` | Kick and revoke a player |
| `GET /api/v1/rooms/{code}/history?limit=50&cursor=N` | Host or player | `200` | Newest-first history pagination |
| `DELETE /api/v1/rooms/{code}` | Host | `204` | Close room and revoke all sessions |

`limit` is 1–100. `nextCursor: null` means no older history remains. Otherwise, use `nextCursor` as the next request's exclusive `cursor`.

## 3. STOMP commands and receipts

Subscribe to `/user/queue/commands` before sending commands. Every command returns one receipt to that queue.

| SEND destination | Role | Body |
|---|---|---|
| `/app/rooms/{code}/start-hand` | Host | `GameControlCommand` |
| `/app/rooms/{code}/new-game` | Host | `GameControlCommand` |
| `/app/rooms/{code}/actions` | Approved player | `PlayerActionCommand` |

```ts
export interface GameControlCommand {
  commandId: string;
}

export interface PlayerActionCommand {
  commandId: string;
  action: PlayerAction;
  /** For RAISE, the target total contribution on this street; omit for other actions. */
  amount?: number;
}

export interface CommandReceipt {
  commandId: string;
  /** true means the server recognized a retry and did not execute it again. */
  replayed: boolean;
  phase: GamePhase;
  handNumber: number;
}
```

Keep an unacknowledged command in client storage and resend it after reconnect with the same `commandId` and payload. Never reuse an ID for different content.

For a legal raise, the target range is `currentBet + minimumRaise` through `playerContribution + playerChips`. `amount` is the chosen target, not “chips to add.”

## 4. Subscriptions

### Public room subscriptions

Approved players and the host may subscribe only to their own room:

| Destination | Payload | Meaning |
|---|---|---|
| `/topic/rooms/{code}/state` | `PublicGameState` | Authoritative snapshot after every accepted command |
| `/topic/rooms/{code}/events` | `PublicGameEvent` | Ordered animation/log events; no private cards |

The state topic is authoritative. Events are transient presentation hints. On disagreement, render the latest state.

### Private user subscriptions

Spring resolves `/user/queue/...` to the authenticated subject; never add player IDs to destinations.

| Destination | Who | Payload |
|---|---|---|
| `/user/queue/cards` | Approved player | `CardsDealtEvent` |
| `/user/queue/turn` | Approved player | `ActionRequiredEvent` |
| `/user/queue/lobby` | Host | `LobbyResponse` |
| `/user/queue/status` | Host/player | `StatusEvent` |
| `/user/queue/commands` | Host/player | `CommandReceipt` |
| `/user/queue/errors` | Host/player | `SocketError` |

All polymorphic events use `{ type, payload }`. Do not infer event type from the presence of fields.

## 5. TypeScript-ready schemas

```ts
export interface NetworkInfoResponse {
  /** Absolute origin such as http://192.168.1.9:8080. */
  origin: string;
  address: string;
  lanAvailable: boolean;
}

export type RoomCode = string;
export type MemberStatus = "PENDING" | "APPROVED" | "REJECTED" | "KICKED";
export type GamePhase =
  | "WAITING_FOR_PLAYERS" | "STARTING_HAND" | "PRE_FLOP"
  | "FLOP" | "TURN" | "RIVER" | "SHOWDOWN"
  | "HAND_COMPLETE" | "GAME_COMPLETE";
export type PlayerAction = "FOLD" | "CHECK" | "CALL" | "RAISE" | "ALL_IN";
export type BettingStatus = "ACTIVE" | "FOLDED" | "ALL_IN";
export type Rank = "TWO" | "THREE" | "FOUR" | "FIVE" | "SIX" | "SEVEN"
  | "EIGHT" | "NINE" | "TEN" | "JACK" | "QUEEN" | "KING" | "ACE";
export type Suit = "CLUBS" | "DIAMONDS" | "HEARTS" | "SPADES";
export type HandCategory = "HIGH_CARD" | "ONE_PAIR" | "TWO_PAIR" | "THREE_OF_A_KIND"
  | "STRAIGHT" | "FLUSH" | "FULL_HOUSE" | "FOUR_OF_A_KIND" | "STRAIGHT_FLUSH";

export interface Card { rank: Rank; suit: Suit; }
export interface HoleCards { first: Card; second: Card; }
export interface BlindStructure { smallBlind: number; bigBlind: number; }
export type ThemeId = "midnight" | "broadcast";
export type CardPackId = "classic" | "four-color";
export interface RoomAppearance { themeId: ThemeId; cardPackId: CardPackId; }
export interface RoomConfig {
  startingChips: number;
  blinds: BlindStructure;
  maximumPlayers: number;
  botCount: number;
  appearance: RoomAppearance;
}

export interface PublicPlayerState {
  playerId: string;
  displayName: string;
  seatNumber: number;
  chips: number;
  streetContribution: number;
  bettingStatus: BettingStatus;
  dealer: boolean;
  smallBlind: boolean;
  bigBlind: boolean;
}

export interface PublicGameState {
  phase: GamePhase;
  handNumber: number;
  handInProgress: boolean;
  players: PublicPlayerState[];
  communityCards: Card[];
  /** Empty during a hand; contains every participant's two cards after completion. */
  revealedHoleCards: Record<string, HoleCards>;
  pot: number;
  currentBet: number;
  minimumRaise: number;
  currentPlayerId: string | null;
}

export interface PrivatePlayerState {
  game: PublicGameState;
  playerId: string;
  holeCards: HoleCards | null;
  validActions: PlayerAction[];
}

export interface CreateRoomRequest {
  hostName: string;
  startingChips: number;
  smallBlind: number;
  bigBlind: number;
  maximumPlayers: number;
  botCount: number;
  themeId: ThemeId;
  cardPackId: CardPackId;
}
export interface CreateRoomResponse {
  roomCode: RoomCode;
  hostToken: string;
  hostUrl: string;
  joinUrl: string;
  tvUrl: string;
}
export interface JoinRoomRequest { displayName: string; }
export interface JoinRoomResponse { playerId: string; playerToken: string; status: "PENDING"; }
export interface ApprovePlayerRequest { seatNumber: number; }
export interface MemberResponse {
  playerId: string;
  displayName: string;
  status: MemberStatus;
  seatNumber: number | null;
}
export interface PublicRoomResponse {
  roomCode: RoomCode;
  config: RoomConfig;
  botPlayerIds: string[];
  game: PublicGameState;
}
export interface LobbyResponse { roomCode: RoomCode; members: MemberResponse[]; }
export interface MeResponse {
  playerId: string;
  displayName: string;
  status: MemberStatus;
  seatNumber: number | null;
  /** Authoritative room appearance; apply it after every bootstrap/reconnect. */
  config: RoomConfig;
  game: PublicGameState;
  /** null while pending; present only after approval. */
  privateState: PrivatePlayerState | null;
}
export interface HistoryEntry {
  sequence: number;
  handNumber: number;
  eventType: string;
  message: string;
  occurredAt: string; // ISO-8601 instant
}
export interface HistoryPage { entries: HistoryEntry[]; nextCursor: number | null; }
export interface HostBootstrapResponse {
  room: PublicRoomResponse;
  lobby: LobbyResponse;
  history: HistoryPage;
}

export interface TablePositions {
  dealerId: string;
  smallBlindId: string;
  bigBlindId: string;
  firstToActPreFlopId: string;
  firstToActPostFlopId: string;
  clockwisePlayerIds: string[];
}
export interface HandValue { category: HandCategory; tieBreakers: number[]; }
export interface EvaluatedHand { value: HandValue; bestFiveCards: Card[]; }

export type PublicGameEvent =
  | Envelope<"HAND_STARTED", { handNumber: number; positions: TablePositions }>
  | Envelope<"PLAYER_ACTED", { playerId: string; action: PlayerAction; amount: number | null; committedChips: number }>
  | Envelope<"STREET_DEALT", { street: GamePhase; communityCards: Card[] }>
  | Envelope<"HAND_COMPLETED", {
      payouts: Record<string, number>;
      shownHands: Record<string, EvaluatedHand>;
      totalPot: number;
      communityCards: Card[];
    }>
  | Envelope<"GAME_COMPLETED", { winnerId: string }>
  | Envelope<"NEW_GAME_STARTED", { state: PublicGameState }>;

export type CardsDealtEvent = Envelope<"CARDS_DEALT", {
  playerId: string;
  cards: HoleCards;
}>;
export type ActionRequiredEvent = Envelope<"ACTION_REQUIRED", {
  playerId: string;
  validActions: PlayerAction[];
  currentBet: number;
  playerContribution: number;
  minimumRaise: number;
  pot: number;
  playerChips: number;
}>;
export type StatusEvent =
  | Envelope<"MEMBERSHIP_STATUS", {
      roomCode: RoomCode;
      playerId: string;
      status: MemberStatus;
      seatNumber: number | null;
      message: string;
    }>
  | Envelope<"ROOM_CLOSED", { roomCode: RoomCode; message: string }>;
export interface Envelope<T extends string, P> { type: T; payload: P; }

export interface SocketError { code: SocketErrorCode; message: string; }
export interface ApiProblem {
  type: string;
  title: string;
  status: number;
  detail: string;
  code: RestErrorCode;
  instance: string;
}
```

JSON numbers represent Java `long`. Poker values remain safely below JavaScript's `Number.MAX_SAFE_INTEGER`; the frontend must not permit values above that limit.

## 6. Error codes

### REST `ApiProblem.code`

```ts
export type RestErrorCode =
  | "AUTHENTICATION_REQUIRED" | "INVALID_TOKEN" | "TOKEN_EXPIRED"
  | "WRONG_ROOM" | "NOT_HOST" | "NOT_PLAYER"
  | "ROOM_NOT_FOUND" | "PLAYER_ALREADY_REGISTERED" | "PLAYER_NOT_PENDING"
  | "PLAYER_NOT_APPROVED" | "ROOM_FULL" | "SEAT_UNAVAILABLE" | "GAME_IN_PROGRESS"
  | "CODE_GENERATION_EXHAUSTED" | "VALIDATION_ERROR" | "INVALID_REQUEST";
```

- `400`: invalid input or pagination.
- `401`: missing, invalid, expired, or revoked token.
- `403`: valid token with wrong room/role.
- `404`: room not found.
- `409`: current room/lobby state conflicts with the request.

Player removal is host-only and permitted only between hands. A successful removal marks the member `KICKED`, frees the seat, revokes all of that player's sessions, and sends a private membership-status notification. During a hand the API returns `409 GAME_IN_PROGRESS` and leaves the player and game unchanged.
- `413`: request body exceeds 64 KiB.
- `503`: no unique room code could be allocated.

### STOMP `/user/queue/errors`

```ts
export type SocketErrorCode =
  | "NOT_HOST" | "PLAYER_NOT_APPROVED" | "GAME_NOT_READY" | "ILLEGAL_ACTION"
  | "COMMAND_ID_REUSED" | "VALIDATION_ERROR";
```

CONNECT or destination authorization failures are STOMP `ERROR` frames and should trigger reconnect/bootstrap, not optimistic retries.

## 7. Bootstrap and reconnect sequences

### Host

1. `POST /api/v1/rooms`; persist `roomCode` and `hostToken` locally.
2. Connect `/ws` with `Authorization: Bearer <hostToken>`.
3. Subscribe to room `state`, room `events`, and user `lobby`, `status`, `commands`, `errors`.
4. `GET /host-bootstrap` after subscriptions are active.
5. Replace local room/lobby/history state with the response.
6. Send start/new-game commands using new UUID command IDs.

### Player

1. Open `/room/{code}/play`; `GET /api/v1/rooms/{code}` to validate the room.
2. `POST /join-requests`; persist `roomCode`, `playerId`, and `playerToken` locally.
3. Connect `/ws` using the player token.
4. While pending, subscribe to `status`, `commands`, and `errors` only.
5. On `MEMBERSHIP_STATUS` with `APPROVED`, subscribe to room `state`, room `events`, private `cards`, and private `turn`.
6. `GET /me`; replace all local membership/private state with the response.
7. Send actions only when the player is current and the action appears in `validActions`.

### Refresh/disconnect recovery

1. Reuse the stored token; do not register again.
2. Reconnect STOMP and restore subscriptions.
3. Fetch `/host-bootstrap` (host) or `/me` (player) after subscriptions are registered.
4. Replace local state rather than merging stale state.
5. If a command lacked a receipt before disconnect, resend exactly the same payload and `commandId`.
6. `401 INVALID_TOKEN/TOKEN_EXPIRED`, `REJECTED`, `KICKED`, or `ROOM_CLOSED` clears local credentials and returns to join/setup.

## 8. Example complete game timeline

1. Host creates `ABC123`; receives host token and shares `/room/ABC123/play`.
2. Alice and Bob submit join requests and receive player tokens.
3. Host receives two lobby snapshots and approves Alice at seat 0 and Bob at seat 1.
4. Players receive `MEMBERSHIP_STATUS`; each reconnects/subscribes if necessary and calls `/me`.
5. Host sends `start-hand` with `commandId=h1-start`.
6. Host receives `CommandReceipt`; all approved clients receive `HAND_STARTED`, then authoritative `state`.
7. Alice and Bob each receive only their own `CARDS_DEALT`. The current player receives `ACTION_REQUIRED`.
8. Current player sends an action UUID. The server validates turn, amount, and legal actions; returns a receipt; broadcasts `PLAYER_ACTED` and state.
9. Bots, if present, use the same application command path and generate the same events as humans.
10. Streets emit `STREET_DEALT`; state follows every accepted command.
11. Showdown emits `HAND_COMPLETED` with payouts/shown hands, followed by final state. That final public state exposes every participant's two cards in `revealedHoleCards`; the field is empty before completion and is cleared by the next hand. History becomes available newest first.
12. If at least two players still have chips, host sends another `start-hand`. Dealer rotation and a newly shuffled deck are engine-owned.
13. When one player remains, `GAME_COMPLETED` is emitted and phase becomes `GAME_COMPLETE`.
14. Host sends `new-game`; `NEW_GAME_STARTED` resets configured stacks/history and state returns to `WAITING_FOR_PLAYERS`.

## 9. Acceptance coverage that freezes this contract

| Scenario | Automated coverage |
|---|---|
| Create → join → approve → start → complete | `BackendAcceptanceTest` |
| Humans mixed with bots | `BackendAcceptanceTest`, `BotLifecycleTest` |
| Refresh during a turn with same cards/actions | `RoomApiIntegrationTest.refreshDuringAPlayerTurn...` |
| Duplicate/concurrent/restarted commands | `GameSocketControllerTest`, `IdempotentCommandExecutorTest` |
| Disconnect and reconnect | `StompAuthenticationInterceptorTest.sameBearerToken...` |
| Kick and reject revocation | `RoomApiIntegrationTest` |
| Complete file-backed restart recovery | `JsonSnapshotStoreTest.completeRoomSessionsHistory...` |
| History pagination | `RoomApiIntegrationTest.supportsPublicState...` |
| Multiple isolated rooms | `BackendAcceptanceTest`, authorization tests |
| Complete game → new game | `RoomGameServiceTest.startsFreshGame...` |
| Private/public event routing | `GameplayEventRouterTest`, `LobbyNotificationRouterTest` |

Changes to any endpoint, destination, event discriminator, field, enum, nullability rule, or error code require updating this document and its corresponding acceptance test in the same change.
