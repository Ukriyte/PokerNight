# PokerNight v2 frontend-app

Fresh functional frontend started from `FRONTEND_IMPLEMENTATION_GUIDE.md`. The older sibling `frontend/` remains unchanged until this app is ready to replace it in Spring Boot packaging.

## Phase 1 scope

- strict React/TypeScript/Vite foundation;
- frozen REST/STOMP TypeScript interfaces;
- strict Zod runtime validation;
- valid contract fixtures;
- exhaustive 52-card path mapping;
- Vitest, Testing Library, MSW, and Playwright configuration.

## Phase 2 scope

- versioned, role-separated host/player sessions in `localStorage`;
- strict corruption recovery and storage failure reporting;
- same-origin HTTP client with Zod response validation;
- stable `ApiClientError` and `ContractValidationError` types;
- safe GET retry policy with jitter and abort support;
- typed adapters for every frozen room REST endpoint;
- MSW coverage for methods, bodies, authentication, pagination, retries, errors, and malformed responses.

No product UI or live backend wiring is included yet. Phase 3 adds STOMP and central room state.

## Phase 3 scope

- idempotent STOMP activation and complete role-authorized subscription profiles;
- runtime validation for every incoming public/private frame;
- capped reconnect delay and full subscription replacement after reconnect;
- REST-first bootstrap race gate with event/private-frame replay and latest-state authority;
- central room reducer for host/player bootstrap, public state, cards, turns, history, and terminal states;
- persistent room/subject command outbox with exact-payload retry and receipt removal;
- unit coverage for duplicate activation, subscription promotion, corrupt frames, reconnect state, reducer authority, and outbox recovery.

No product UI is included yet. Phase 4 adds accessible visual primitives; Phase 5 begins complete room flows.

## Phase 4 scope

- offline-safe ink/felt/paper/brass design tokens, locally bundled fonts, and responsive CSS foundation;
- accessible button, field, banner, live-region, connection, and focus-trapped dialog primitives;
- exhaustive card artwork bundled locally plus waiting, concealed, and atomic two-card reveal states;
- semantic player seats with textual dealer/blind/fold/all-in/bot labels;
- responsive six-seat public table and community board primitive;
- development-only `/_components` gallery for manual keyboard and phone-width checks;
- reduced-motion behavior, visible keyboard focus, 44px touch targets, and safe responsive wrapping.

Phase 5 uses these primitives to implement room creation and the complete host flow.

## Phase 5 scope

- validated room creation with configurable stacks, blinds, seats, and bots;
- durable host credentials and refresh-safe REST-first bootstrap;
- same-origin join link and locally generated QR code;
- realtime lobby updates with approve, reject, seat assignment, and kick controls;
- authoritative public table, start-hand/new-game commands, persistent command retry, and receipts;
- newest-first paginated history, reconnect-safe controls, and room closure.

Phase 6 adds the complete joining and approved-player experience.

## Phase 6 scope

- room lookup, player name registration, pending approval, and automatic approved transition;
- durable player credentials and authoritative refresh/reconnect bootstrap;
- compact public table plus private two-card atomic reveal;
- legal-action-only controls with server-defined call values and raise target limits;
- idempotent one-command-at-a-time action delivery and exact retry after reconnect;
- player history, rejected/kicked/closed/expired cleanup, and visibility resume recovery.

Phase 7 adds the public TV route and final network/WebKit resilience pass.

## Phase 7 scope

- anonymous `/room/:code/tv` display with one-second authoritative state polling;
- polling paused while hidden and resumed on visibility, online, and page-cache restoration;
- last confirmed TV state retained during temporary network loss;
- room-not-found, waiting, active-hand, hand-complete, and game-complete TV states;
- host/player refresh on reconnect, iPhone `pageshow`, visibility, and online recovery;
- safe-area viewport handling and portrait/landscape WebKit layout safeguards.

Phase 8 packages this production frontend into Spring Boot and runs real-backend browser acceptance tests against an isolated jar on port 18080.

```bash
npm run test:e2e
```

The suite creates isolated in-memory rooms and covers approval, private-card reveal and refresh recovery, public TV privacy, history, next-hand continuation, reject/kick revocation, and iPhone page resume. `npm run test:e2e:update` intentionally regenerates visual baselines.

## Commands

```bash
npm install
npm run typecheck
npm run test:run
npm run build
npm run test:e2e
```

Playwright browsers may need to be installed once with `npx playwright install`.
