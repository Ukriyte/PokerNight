# PokerNight frontend theming audit

## Scope

The theme work belongs in `pokernight-v2/frontend-app`. This is the functional React client containing the host, player-phone, and TV experiences. The sibling `pokernight-v2/frontend` directory is not the current full UI and is outside this work.

This audit intentionally changes no runtime behaviour.

## Existing foundation

- `src/styles/tokens.css` already defines a single dark cardroom palette, three font roles, radii, a shadow, focus styling, and reduced-motion behaviour.
- `src/styles/components.css` contains all component and responsive layout rules.
- `src/main.tsx` imports the global token and component styles.
- The UI already uses shared components rather than recreating controls on every page.
- Mobile safe-area insets, 44 px control targets, keyboard focus, and reduced-motion support already exist and must remain invariant across themes.

## Theme-sensitive component inventory

### Game primitives

- `src/ui/PlayingCard.tsx`: face, back, empty slot, and private card-pair presentation.
- `src/ui/Seat.tsx`: player identity, chips, bet, dealer/blind/status badges, bot state, active-turn state, and showdown cards.
- `src/ui/TableBoard.tsx`: six-seat layout, table felt, community cards, and pot.
- `src/player/PlayerActionPanel.tsx`: fold/check/call/raise/all-in controls and raise slider.

### Shared interface primitives

- `src/ui/Button.tsx`
- `src/ui/TextField.tsx`
- `src/ui/Banner.tsx`
- `src/ui/Dialog.tsx`
- `src/ui/ConnectionBadge.tsx`
- `src/ui/HistoryNotifications.tsx`

### Product surfaces

- `src/pages/CreateRoomPage.tsx`: landing and room configuration.
- `src/pages/HostRoomPage.tsx`: host table, controls, lobby, access QR, and history.
- `src/pages/PlayerRoomPage.tsx`: phone table, private cards, actions, reconnect states, and history.
- `src/pages/TvRoomPage.tsx`: large public table display.
- `src/pages/ComponentGallery.tsx`: development-only visual verification surface.
- `src/host/LobbyPanel.tsx`, `JoinAccess.tsx`, and `HistoryPanel.tsx`.

## Current theme leaks

The following appearance decisions bypass the token layer and prevent clean theme switching:

- `PlayingCard.tsx` hard-codes `/cards/1B.svg` as the card back.
- Card faces are selected directly by `cardAssets.ts`; there is no card-pack abstraction.
- `components.css` hard-codes the felt rim colour (`#0e3733`).
- Success, error, connection, warning, white, and translucent surface colours appear as raw values.
- Page-specific radial gradients repeat concrete RGB values.
- Active-seat glow, notification surfaces, table shadows, and modal backdrops contain fixed colours.
- Table shape, card dimensions, radii, and motion are mixed with colour rules in one stylesheet.
- No theme identifier or provider exists; the current `:root` values are effectively an unnamed theme.

## Behaviour that themes must not change

- Public and private card visibility.
- Reveal/hide-both-cards behaviour.
- Seat ordering and active-turn calculation.
- Dealer, small-blind, and big-blind state.
- Bot timing and action submission.
- Host, player, TV, refresh, reconnect, and command-outbox flows.
- Backend contracts and WebSocket messages.
- Mobile safe-area handling, minimum touch targets, focus visibility, and reduced-motion preference.

## Recommended boundary

Theme configuration should control:

- Semantic colour tokens.
- Display, body, and data fonts.
- Surface radii, borders, shadows, and table treatment.
- Card-pack identifier and card-back asset.
- Optional table texture and restrained decorative assets.
- Motion intensity where reduced motion is not requested.

Game state, permissions, actions, room configuration, and networking must remain outside the theme layer.

## Step 1 conclusion

The component structure is already suitable for theming. The next step is to replace the current colour-name tokens and raw CSS values with semantic tokens while preserving the existing appearance as the `midnight` baseline theme.

## Step 8 cross-device verification

The theme and card-pack system is covered by packaged-browser tests rather than component tests alone.

- Chromium verifies room creation, host refresh, player approval, private-card reveal, player refresh, history, television state, rejection, removal, and another hand.
- iPhone WebKit portrait verifies the same join, private-card, refresh, action, and credential-revocation paths.
- iPhone WebKit landscape verifies host reload and page-resume recovery.
- Host-selected appearance is asserted on host, player, and television documents and again after host refresh.
- Desktop, iPhone portrait, iPhone landscape, and 1600×900 television visual baselines cover the intentional layouts.
- Dynamic room codes and the television clock are masked in screenshots so baselines compare layout and styling rather than transient values.

The SVG card accessibility contract is tested through `role="img"` and card names, avoiding coupling browser tests to the removed image-file implementation.

## Step 9 generated decorative assets

The supplied ChatGPT contact sheet was split into two felt textures and two card backs. Runtime assets are compressed JPEGs under `public/themes`; provenance and prompt intent are recorded beside them in `ASSET_PROVENANCE.md`.

- Theme metadata owns table texture URLs.
- Card backs use the earlier deterministic SVG design; generated card-back concepts remain unreferenced.
- Table felt combines semantic colour tinting with the texture, preserving contrast in both themes.
- The accessible SVG shell preserves dimensions, rounded clipping, and atomic reveal/hide behavior.
- Card faces remain deterministic code-generated SVG and are never AI-generated.
