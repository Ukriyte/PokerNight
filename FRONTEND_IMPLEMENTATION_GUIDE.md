# PokerNight v2 — Frontend Implementation Guide

Status: **authoritative build instructions for the first functional React frontend**  
Backend contract: [`FRONTEND_CONTRACT.md`](FRONTEND_CONTRACT.md)  
Target: desktop host, phone player, and shared TV/table views served by the same Spring Boot application

This guide explains how to build the frontend without moving poker rules into React or repeatedly changing the data layer. It does not promise that defects are impossible; it defines the architecture, state rules, recovery behavior, and acceptance gates required to prevent the failure modes already found in the earlier application.

## 1. Document precedence

When documents disagree, use this order:

1. `FRONTEND_CONTRACT.md` — wire protocol, payloads, errors, authentication, and reconnect order.
2. This guide — frontend architecture, UI states, implementation order, and quality gates.
3. `RECONNECT_FLOW.md` — additional reconnect examples.
4. `API_DESIGN.md` — backend reasoning, not a client contract.
5. Root `DESIGN.md` — historical engineering reference only.

Do not add a field because a mockup needs it. First check the frozen contract. If the backend does not provide information, either derive presentation-only state from existing fields or make an explicit contract change with backend and frontend tests together.

## 2. Product model

PokerNight is a home card-room application, not an online casino lobby.

- The **host** creates one room, approves seats, starts hands, reviews history, and starts a new game after `GAME_COMPLETE`.
- A **player** joins from a phone, waits for approval, sees the public table plus only their own cards, and submits legal actions.
- The **TV** shows public table state only. It never stores or receives a player token or private cards.
- The **backend** owns the deck, cards, turns, legal actions, blinds, dealer, pots, payouts, history, and game transitions.

The frontend's job is to display authoritative state, collect intent, survive unreliable phone networking, and explain what the user can do next.

## 3. Non-negotiable correctness rules

1. Never shuffle, deal, evaluate hands, calculate pots, rotate the dealer, or decide legal actions in React.
2. `/topic/rooms/{code}/state`, `/me`, and `/host-bootstrap` are authoritative snapshots. Replace local server state; do not merge guesses into it.
3. Public events are for animation and readable logs. They never override a newer snapshot.
4. Render action buttons only from `PrivatePlayerState.validActions` or the latest `ACTION_REQUIRED` for the authenticated player.
5. A raise `amount` is the player's **target total contribution for the street**, not chips to add. The legal range is:
   - minimum: `currentBet + minimumRaise`
   - maximum: `playerContribution + playerChips`
6. Never render another player's hole cards from any client-side source.
7. Keep host and player credentials separate and namespace them by room.
8. Never put bearer tokens in URLs, QR codes, analytics, logs, errors, or React render output.
9. Every mutation uses a new UUID `commandId`. A retry uses the exact same ID and exact same payload.
10. Refresh and reconnect must reconstruct the screen from the server without issuing a game command.

## 4. Frontend technology and quality baseline

Keep the current React 19, TypeScript, Vite, React Router, and `@stomp/stompjs` foundation. Add only focused dependencies:

- `zod` — validate untrusted REST/STOMP payloads at the network boundary.
- `@tanstack/react-query` — REST bootstrap, cancellation, loading/error state, and cache invalidation.
- `vitest`, `@testing-library/react`, `@testing-library/user-event` — component and reducer tests.
- `msw` — deterministic REST mocks.
- `playwright` — browser-level host/player/TV tests, including WebKit.

Do not add Redux and Zustand together. For this application, use a room-scoped reducer/context for live game state and TanStack Query for REST requests. This keeps state transitions visible and debuggable.

Required TypeScript settings:

```json
{
  "strict": true,
  "noUncheckedIndexedAccess": true,
  "exactOptionalPropertyTypes": true,
  "useUnknownInCatchVariables": true,
  "noImplicitOverride": true
}
```

No `any`, `@ts-ignore`, or unchecked `JSON.parse` is allowed in API/realtime/session code.

## 5. Source structure and ownership

```text
frontend-app/src/
├── app/
│   ├── App.tsx
│   ├── routes.tsx
│   └── providers.tsx
├── contracts/
│   ├── api.ts              # TypeScript types from FRONTEND_CONTRACT.md
│   ├── schemas.ts          # matching Zod runtime schemas
│   └── contractFixtures.ts # valid payload fixtures used by tests
├── api/
│   ├── httpClient.ts       # fetch wrapper, bearer header, ApiProblem parsing
│   └── roomsApi.ts         # one function per REST endpoint
├── realtime/
│   ├── stompClient.ts      # connection lifecycle only
│   ├── roomSubscriptions.ts
│   ├── commandOutbox.ts
│   └── realtimeTypes.ts
├── session/
│   ├── sessionRepository.ts
│   ├── sessionTypes.ts
│   └── useRoomSession.ts
├── state/
│   ├── roomReducer.ts
│   ├── roomState.ts
│   ├── roomActions.ts
│   └── RoomStateProvider.tsx
├── features/
│   ├── create-room/
│   ├── lobby/
│   ├── table/
│   ├── player-actions/
│   ├── history/
│   └── connection-status/
├── pages/
│   ├── HomePage.tsx
│   ├── HostRoomPage.tsx
│   ├── PlayerRoomPage.tsx
│   ├── TvRoomPage.tsx
│   └── NotFoundPage.tsx
├── components/
│   ├── Card.tsx
│   ├── CardPair.tsx
│   ├── Seat.tsx
│   ├── PokerTable.tsx
│   ├── Dialog.tsx
│   └── ...
├── styles/
│   ├── tokens.css
│   ├── global.css
│   └── utilities.css
└── test/
    ├── server.ts
    ├── render.tsx
    └── fixtures/
```

Dependency direction:

```text
pages/features/components → state → api/realtime/session → contracts
```

- Components never call `fetch`, access STOMP, or read `localStorage`.
- REST and STOMP adapters never import React.
- Contract schemas never import UI code.
- The room reducer is pure and has no timers, sockets, storage, or DOM access.

## 6. Routes and screen state machines

### `/` — create room

States:

```text
editing → creating → created/navigate
                 ↘ recoverable error
```

Fields: host name, starting chips, blinds, maximum players, bot count. Apply the same limits as the contract before submission, but still display backend validation errors.

On success, save the host session and navigate to the returned `hostUrl`. Never synthesize room URLs when the backend already returned them.

### `/room/:code` — host

States:

```text
no token → recovery choice
connecting → bootstrapping → ready
                         ↘ room closed/session invalid
ready: lobby | hand active | hand complete | game complete
```

Host capabilities:

- copy join URL and show a QR containing only the join URL;
- view pending/approved/rejected/kicked members;
- assign an available human seat;
- reject or kick;
- start a hand only when backend state allows it;
- open complete newest-first history;
- start a new game only after `GAME_COMPLETE`.

The host never receives or displays hole cards.

The backend returns relative room URLs. Resolve them with `new URL(joinUrl, location.origin)`. If the resulting hostname is `localhost`, `127.0.0.1`, or `::1`, do not label the QR as shareable: show “Open this host page using the Mac’s Wi-Fi address to invite phones.” This prevents a valid-looking QR that points every phone back to itself. Never guess a LAN address in browser code and never substitute a VPN address.

### `/room/:code/play` — player phone

States:

```text
room lookup → name form → pending approval
pending → approved/bootstrap → observing | acting | folded | all-in
pending/approved → rejected | kicked | room closed | token expired
any connected state ↔ reconnecting/offline
```

The player page shows:

- compact public table and community cards;
- all seats, stacks, bets, dealer/blind markers, and current turn;
- own stack and own cards in a stable bottom area;
- action controls only during the player's turn;
- expandable history/activity;
- persistent connection status without covering controls.

### `/room/:code/tv` — shared table

The current backend has no anonymous spectator STOMP role. Therefore the public TV route must use `GET /api/v1/rooms/{code}/state` polling (recommended every 1 second while visible, paused while hidden) unless a host-authenticated local display intentionally supplies a host session. Never place a host token in the TV URL.

TV states: loading, room not found, waiting for players, hand active, hand complete, game complete, temporarily offline.

## 7. Session storage rules

Use `localStorage` through `sessionRepository.ts` because iPhone Safari can destroy `sessionStorage` when a tab is suspended or killed.

Suggested versioned structure:

```ts
interface StoredRoomSessionV1 {
  version: 1;
  roomCode: string;
  role: "HOST" | "PLAYER";
  subjectId?: string;
  token: string;
  savedAt: string;
}
```

Keys:

```text
pokernight:v1:host:{ROOM_CODE}
pokernight:v1:player:{ROOM_CODE}
pokernight:v1:outbox:{ROOM_CODE}:{SUBJECT_ID}
```

Rules:

- Validate stored JSON before using it.
- Never silently switch a host token into a player session.
- Clear credentials on `INVALID_TOKEN`, `TOKEN_EXPIRED`, `REJECTED`, `KICKED`, or `ROOM_CLOSED`.
- A generic network error does **not** clear credentials.
- Provide an explicit “Leave room on this device” control.

## 8. REST client rules

`httpClient.ts` must:

- use same-origin relative paths;
- attach a bearer token only when requested by the endpoint wrapper;
- set `Accept: application/json` and `Content-Type: application/json` when a body exists;
- use `AbortSignal` for page changes and superseded bootstrap calls;
- parse non-2xx responses as `ApiProblem` through Zod;
- preserve `status`, `code`, and `detail` in a typed error;
- read `X-Request-ID` and include it in a collapsible diagnostic detail;
- never retry 4xx responses automatically;
- retry safe GET requests at most twice for transport/5xx failures with jitter.

Do not retry room creation or join registration automatically: a timeout is ambiguous and could create duplicate rooms/players. Show a clear retry/recovery choice.

## 9. STOMP connection lifecycle

Create exactly one STOMP client per active room session.

Recommended settings:

```ts
{
  brokerURL: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
  connectHeaders: { Authorization: `Bearer ${token}` },
  reconnectDelay: 1000,
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000
}
```

Use capped exponential reconnect with jitter if wrapping the library: 0.5s, 1s, 2s, 4s, then 10s maximum. Only one reconnect timer may exist.

Lifecycle rules:

1. Connect.
2. Register role-appropriate subscriptions exactly once.
3. Bootstrap through REST after subscriptions are active.
4. Mark the screen `ready` only after bootstrap succeeds.
5. On disconnect, keep the last state visible but disable mutation controls and show “Reconnecting…”.
6. On reconnect, resubscribe, bootstrap again, then flush unacknowledged commands.
7. On `visibilitychange` to visible or `pageshow`, verify STOMP is connected; iPhone Safari may freeze network timers.
8. On unmount or room/role change, unsubscribe and deactivate the old client.

React Strict Mode mounts effects twice in development. STOMP creation must be idempotent and cleanup must fully deactivate the first client; otherwise duplicate subscriptions will produce duplicated logs and actions.

## 10. Bootstrap race handling

Subscriptions are installed before REST bootstrap so no later events are missed. During bootstrap:

- buffer incoming STOMP frames;
- apply the REST snapshot as the baseline;
- replay buffered **events** for presentation only;
- use the most recently received buffered public `state` as the final public state;
- apply buffered private cards/turn/status after the baseline;
- then switch to live processing.

The current contract does not expose a server state revision. Therefore never try to sort snapshots using client timestamps. Keep this bootstrap gate isolated in one tested module. If multi-server ordering or higher concurrency is introduced, add a monotonic `revision` to the backend contract before changing this algorithm.

## 11. Room reducer and authority rules

Suggested state:

```ts
interface RoomClientState {
  bootstrap: "idle" | "loading" | "ready" | "failed";
  connection: "offline" | "connecting" | "connected" | "reconnecting";
  room: PublicRoomResponse | null;
  game: PublicGameState | null;
  lobby: LobbyResponse | null;
  me: MeResponse | null;
  history: HistoryEntry[];
  historyCursor: number | null;
  activity: PublicGameEvent[];
  pendingTurn: ActionRequiredEvent["payload"] | null;
  pendingCommands: Record<string, PendingCommand>;
  terminalReason: "rejected" | "kicked" | "closed" | "expired" | null;
}
```

Reducer behavior:

- `BOOTSTRAP_HOST_SUCCEEDED`: replace room, game, lobby, and initial history.
- `BOOTSTRAP_PLAYER_SUCCEEDED`: replace `me`, game, own private state.
- `PUBLIC_STATE_RECEIVED`: replace game; never patch individual chip amounts.
- `HAND_STARTED`: clear previous hand animation state, pending turn, and local card reveal.
- `CARDS_DEALT`: atomically replace both own cards.
- `ACTION_REQUIRED`: replace pending turn only if `playerId` is the authenticated player.
- `COMMAND_SENT`: add outbox item and disable only the submitted control group.
- `COMMAND_RECEIPT`: remove the matching outbox item, regardless of `replayed`.
- `SOCKET_ERROR`: keep the command available for correction; do not invent state.
- `MEMBERSHIP_STATUS`: update membership and trigger `/me` bootstrap after approval.
- `ROOM_CLOSED`/kick/reject/token expiry: enter terminal state, disconnect, clear credential.
- `NEW_GAME_STARTED`: clear history, cards, reveal state, action state, and previous winners.

Never append `/topic/.../state` messages to history. History comes from the history REST endpoint; activity events may be displayed temporarily until the next history fetch.

## 12. Command outbox and action safety

Before SEND:

1. Validate that connection is ready.
2. Validate the action exists in the latest `validActions`.
3. Generate a UUID.
4. Persist `{ destination, payload, createdAt }` in the outbox.
5. SEND.
6. Keep it until the matching `CommandReceipt` arrives.

On reconnect, resend persisted commands unchanged. If the receipt says `replayed: true`, treat it as success. On `COMMAND_ID_REUSED`, stop retrying that item and surface a diagnostic error.

Disable action controls after submission until receipt/error or authoritative state changes the turn. This prevents accidental double taps while still allowing the idempotent retry mechanism to handle ambiguous disconnects.

## 13. Card rendering and reveal behavior

This section is mandatory because the previous UI sometimes showed one face card, card-back files, or stale cards.

Use a typed, exhaustive card mapper:

```ts
function cardAsset(card: Card): string {
  return `/cards/${rankCode[card.rank]}${suitCode[card.suit]}.svg`;
}
```

Test all 52 combinations. Never construct a path from `[object Object]`, enum ordinal, or an unchecked string.

`CardPair` has exactly four states:

1. `waiting`: no cards delivered — two neutral empty slots and “Waiting for deal”.
2. `concealed`: both cards delivered but not revealed — two identical card backs.
3. `revealed`: both cards delivered and reveal is enabled — two face cards committed in one render.
4. `showdown`: public shown cards supplied by a `HAND_COMPLETED` event — face cards only for players present in `shownHands`.

Rules:

- Store hole cards as one `HoleCards | null`, never two independent state variables.
- “Reveal cards” performs one boolean state update; it cannot reveal only one card.
- Disable Reveal until both `first` and `second` validate.
- Reset reveal when `handNumber` changes, `HAND_STARTED` arrives, new game starts, player is kicked, or session changes.
- A missing card payload shows `waiting`, not a fallback face or a broken image.
- `1B.svg`/`2B.svg` are presentation assets only; they never represent actual card data.
- Host and TV components must not accept a `holeCards` prop.
- Use `alt="Ace of spades"` for face cards and `alt="Face-down playing card"` for backs.

## 14. Visual system

### Direction

The interface should feel like a well-kept neighborhood card club: tactile, legible across a room, and calm under pressure. Avoid glossy casino gradients, neon gambling imagery, and generic dashboard cards.

### Palette

```css
:root {
  --ink-950: #111820;       /* page and TV surround */
  --felt-800: #164C46;      /* table */
  --felt-650: #237066;      /* active felt detail */
  --paper-100: #F3EBDD;     /* cards and primary text surfaces */
  --brass-400: #C7A35A;     /* dealer/current-turn signal */
  --signal-500: #C94F43;    /* destructive/error only */
}
```

Meet WCAG AA contrast for all text and controls. Never communicate turn, fold, all-in, or connection state by color alone.

### Type

- Display/room identity: **Archivo Black**, bundled locally.
- Interface/body: **Atkinson Hyperlegible**, bundled locally.
- Chips, pot, timers, and room codes: **IBM Plex Mono**, bundled locally with tabular numerals.

Do not depend on Google Fonts at runtime; the game must work on an offline hotspot.

### Signature element

Use a restrained “action orbit”: the active seat receives a brass edge marker connected visually to the pot/dealer path. It encodes whose turn it is and dealer movement, rather than acting as decoration. Only this element pulses, and only while action is required.

### Motion

- 160–220ms for UI transitions.
- One clear chip/pot movement after authoritative events; never animate a guessed result.
- Respect `prefers-reduced-motion` by removing movement and retaining static state changes.
- Do not use perpetual ambient animation.

## 15. Responsive composition

### Host desktop

```text
┌ room code / connection / copy join link ┐
│                                         │
│ seats       public poker table    seats │
│             board + pot                 │
│ seats                            seats   │
│                                         │
├ activity/history ───── lobby/controls ──┤
└─────────────────────────────────────────┘
```

- Design first for 1280×720 and 1440×900.
- Table remains the dominant region.
- Lobby/history are drawers or bounded side panels, not overlays that hide the table unexpectedly.
- Start-hand/new-game controls must be distinct and phase-aware.

### Player phone

```text
┌ room / connection / hand number ┐
│ board + pot                     │
│ compact scrollable seats        │
│ activity summary                │
├─────────────────────────────────┤
│ own two cards + chips           │
│ Fold | Check/Call | Raise       │
└ safe-area action dock ──────────┘
```

- Use `min-height: 100dvh`, not fixed `100vh`.
- Add `padding-bottom: env(safe-area-inset-bottom)`.
- The public table may scroll; own cards and action dock remain reachable.
- Minimum touch target is 44×44 CSS pixels with at least 8px spacing.
- Never put Fold directly beside the highest-frequency action without visual separation.
- Landscape must remain usable but portrait is primary.

### TV

- Optimize for 1920×1080 viewed from 2–4 metres.
- Pot, board, active player, and stack values are readable without interaction.
- No hover-only information, form controls, join names, private status, or tokens.

## 16. Copy and feedback

Use specific, stable language:

- “Create room”, not “Submit”.
- “Request to join”, then “Waiting for host approval”.
- “Start hand” for another hand in the same game.
- “Start new game” only after the game winner is declared.
- “Reconnecting… Actions are paused.” for transport loss.
- “Session expired. Join the room again.” for `TOKEN_EXPIRED`.
- “This action is no longer available.” for `ILLEGAL_ACTION`, followed by `/me` recovery.

Errors explain the next action. Do not show raw stack traces, exception class names, or vague “Something went wrong” messages. Put `X-Request-ID` behind a “Technical details” disclosure.

## 17. Accessibility

- All functionality is keyboard accessible on host screens.
- Use semantic buttons, forms, headings, lists, and dialogs.
- Dialogs trap focus, close on Escape where safe, and restore focus to their trigger.
- Announce turn changes, approval, reconnect, errors, and winners through a polite `aria-live` region.
- Do not repeatedly announce every pot animation or polling refresh.
- Seat and card order in the DOM must match the visual clockwise order.
- Provide visible `:focus-visible` treatment.
- Support 200% zoom without losing action controls.
- Card suits include textual accessible names; color is secondary.

## 18. Performance and offline-LAN behavior

- Bundle fonts, icons, and all 52 card faces locally.
- Lazy-load host history/lobby panels and nonessential routes, but not the player's action controls.
- Pause TV polling and nonessential animations while `document.hidden`.
- Keep the last authoritative state visible during reconnect.
- Do not add a service worker in the first frontend milestone; stale cached JS can make backend contract changes harder to diagnose.
- Do not contact analytics, font CDNs, image CDNs, or third-party APIs. The app must work when the hotspot has no internet.
- Avoid large rerenders: memoize seats by stable player/seat identity and keep connection state separate from card animation state.

## 19. Implementation order

Do not build all screens at once.

### Phase 1 — contracts and test harness

- Copy TypeScript definitions from `FRONTEND_CONTRACT.md`.
- Create matching Zod schemas and valid fixtures.
- Add Vitest, Testing Library, MSW, and Playwright.
- Add exhaustive card mapping test.

Exit gate: invalid payloads fail closed; all contract fixtures parse.

### Phase 2 — session and REST foundation

- Implement versioned session repository.
- Implement typed HTTP client and every REST endpoint.
- Implement `ApiProblem` mapping and request ID diagnostics.

Exit gate: create/join/bootstrap/history work with MSW; no component calls `fetch`.

### Phase 3 — realtime and reducer

- Implement singleton STOMP lifecycle.
- Implement authorized subscription sets by role.
- Implement bootstrap gate and room reducer.
- Implement persistent command outbox.

Exit gate: duplicate mounts do not duplicate subscriptions; disconnect/reconnect restores state and resends one command once.

### Phase 4 — UI foundation

- Add tokens, bundled fonts, buttons, fields, dialogs, banners, cards, seats, and table primitives.
- Build Storybook only if it will be maintained; otherwise use a private component-gallery route in development.

Exit gate: primitives pass keyboard, contrast, reduced-motion, and phone-width checks.

### Phase 5 — create and host flows

- Create room form.
- Host bootstrap, join QR/link, lobby approvals, controls, history pagination.

Exit gate: host can create, approve, start, continue hands, finish, and start a new game without refresh.

### Phase 6 — player flow

- Join/pending states.
- Compact table, atomic two-card reveal, turn controls, raise target control, history.

Exit gate: refresh during the player's turn preserves both cards and legal actions; a double tap executes once.

### Phase 7 — TV and resilience

- Public state polling TV.
- Offline/reconnect/terminal states.
- iPhone `visibilitychange`, safe-area, and WebKit fixes.

Exit gate: host, two phone contexts, and TV complete a game on a throttled local network.

### Phase 8 — package and polish

- Production build copied into Spring Boot.
- Real-backend Playwright acceptance suite.
- Visual regression at desktop, iPhone portrait/landscape, and TV sizes.

Exit gate: all quality gates below pass against the packaged jar, not only Vite dev mode.

## 20. Required tests

### Unit

- Every Zod contract schema.
- Every room reducer action.
- Every 52-card asset mapping.
- Raise min/max/target presentation.
- command outbox replay/receipt/conflict.
- session migration and corrupted storage.
- card reveal is atomic and resets on hand change.

### Component/integration

- pending → approved transition;
- host lobby approve/reject/kick;
- action controls for every legal-action set;
- history pagination and descending display;
- reconnect banner and disabled controls;
- error-code-to-copy mapping;
- no private-card prop or request in host/TV components.

### Browser E2E against the real Spring Boot backend

1. Host creates room; two players request and are approved.
2. Host starts a mixed human/bot hand and completes it.
3. Both phone players reveal exactly two correct cards.
4. Refresh one player during their turn; cards and valid actions are unchanged.
5. Interrupt the socket after SEND but before receipt; retry executes once.
6. Suspend/resume a WebKit phone page; it reconnects and bootstraps.
7. Reject pending player; kick approved player; credentials clear correctly.
8. History loads newest first and paginates without duplication.
9. Two rooms run simultaneously without state or token leakage.
10. Complete game; host starts new game; cards/reveal/history/winners reset.
11. Restart file-profile server between hands; host/player recover.
12. TV never displays or receives hole cards.

Run Playwright with Chromium and WebKit. Include mobile projects approximating a current iPhone portrait and landscape viewport.

## 21. Manual device checklist

Test on the actual network used for play:

- Mac and all phones connected to the same Wi-Fi/iPhone hotspot.
- Join URL uses the Mac's reachable LAN IP, not `localhost` or a VPN interface.
- iPhone can load the page with cellular data still enabled.
- Lock/unlock phone for 30 seconds and 5 minutes.
- Switch apps, return, refresh, close/reopen tab.
- Rotate during a turn.
- Tap actions rapidly and with poor signal.
- Verify both cards reveal together every hand.
- Verify no old cards appear at the next hand.
- Verify host and TV never expose cards in DOM, logs, or network payloads.
- Verify all actions remain reachable above the iOS home indicator and browser toolbar.

## 22. Definition of done

Frontend milestone 1 is complete only when:

- all four routes work from direct navigation and refresh;
- every received payload is runtime validated;
- no poker rules exist in frontend code;
- no REST/STOMP/storage access exists in presentation components;
- refresh and reconnect recover host/player state;
- commands are idempotently retried;
- private cards are atomically rendered only for their player;
- host/player/TV data boundaries are tested;
- phone UI works in WebKit with safe areas and suspended tabs;
- accessibility and reduced-motion checks pass;
- unit, integration, real-backend E2E, and packaged-jar smoke tests pass;
- `FRONTEND_CONTRACT.md` remains unchanged, or any intentional contract change includes backend and frontend tests in the same commit.

At that point, UI polish can continue without redesigning the data layer.
