import type {
  CardsDealtEvent,
  CreateRoomResponse,
  HostBootstrapResponse,
  MeResponse,
  PublicGameEvent,
  PublicGameState,
} from './api'

export const roomCode = 'ABC123'
export const hostToken = 'a'.repeat(64)
export const playerToken = 'b'.repeat(64)

export const publicGameStateFixture: PublicGameState = {
  phase: 'PRE_FLOP',
  handNumber: 1,
  handInProgress: true,
  players: [
    {
      playerId: 'p1', displayName: 'Alice', seatNumber: 0, chips: 975,
      streetContribution: 25, bettingStatus: 'ACTIVE', dealer: true,
      smallBlind: true, bigBlind: false,
    },
    {
      playerId: 'p2', displayName: 'Bot 1', seatNumber: 1, chips: 950,
      streetContribution: 50, bettingStatus: 'ACTIVE', dealer: false,
      smallBlind: false, bigBlind: true,
    },
  ],
  communityCards: [],
  revealedHoleCards: {},
  pot: 75,
  currentBet: 50,
  minimumRaise: 50,
  currentPlayerId: 'p1',
}

export const createRoomResponseFixture: CreateRoomResponse = {
  roomCode,
  hostToken,
  hostUrl: `/room/${roomCode}`,
  joinUrl: `/room/${roomCode}/play`,
  tvUrl: `/room/${roomCode}/tv`,
}

export const meResponseFixture: MeResponse = {
  playerId: 'p1',
  displayName: 'Alice',
  status: 'APPROVED',
  seatNumber: 0,
  config: {
    startingChips: 1000,
    blinds: { smallBlind: 25, bigBlind: 50 },
    maximumPlayers: 2,
    botCount: 1,
    appearance: { themeId: 'midnight', cardPackId: 'classic' },
  },
  game: publicGameStateFixture,
  privateState: {
    game: publicGameStateFixture,
    playerId: 'p1',
    holeCards: {
      first: { rank: 'ACE', suit: 'SPADES' },
      second: { rank: 'KING', suit: 'HEARTS' },
    },
    validActions: ['FOLD', 'CALL', 'RAISE', 'ALL_IN'],
  },
}

export const hostBootstrapFixture: HostBootstrapResponse = {
  room: {
    roomCode,
    config: {
      startingChips: 1000,
      blinds: { smallBlind: 25, bigBlind: 50 },
      maximumPlayers: 2,
      botCount: 1,
      appearance: { themeId: 'midnight', cardPackId: 'classic' },
    },
    botPlayerIds: ['p2'],
    game: publicGameStateFixture,
  },
  lobby: {
    roomCode,
    members: [
      { playerId: 'p1', displayName: 'Alice', status: 'APPROVED', seatNumber: 0 },
    ],
  },
  history: {
    entries: [
      {
        sequence: 1,
        handNumber: 1,
        eventType: 'HAND_STARTED',
        message: 'Hand 1 started',
        occurredAt: '2026-08-14T00:00:00Z',
      },
    ],
    nextCursor: null,
  },
}

export const cardsDealtFixture: CardsDealtEvent = {
  type: 'CARDS_DEALT',
  payload: {
    playerId: 'p1',
    cards: {
      first: { rank: 'ACE', suit: 'SPADES' },
      second: { rank: 'KING', suit: 'HEARTS' },
    },
  },
}

export const publicEventFixtures: PublicGameEvent[] = [
  {
    type: 'HAND_STARTED',
    payload: {
      handNumber: 1,
      positions: {
        dealerId: 'p1', smallBlindId: 'p1', bigBlindId: 'p2',
        firstToActPreFlopId: 'p1', firstToActPostFlopId: 'p2',
        clockwisePlayerIds: ['p1', 'p2'],
      },
    },
  },
  {
    type: 'PLAYER_ACTED',
    payload: { playerId: 'p1', action: 'CALL', amount: null, committedChips: 25 },
  },
  {
    type: 'STREET_DEALT',
    payload: {
      street: 'FLOP',
      communityCards: [
        { rank: 'TWO', suit: 'CLUBS' },
        { rank: 'SEVEN', suit: 'DIAMONDS' },
        { rank: 'QUEEN', suit: 'SPADES' },
      ],
    },
  },
  {
    type: 'HAND_COMPLETED',
    payload: {
      payouts: { p1: 100 },
      shownHands: {},
      totalPot: 100,
      communityCards: [],
    },
  },
  { type: 'GAME_COMPLETED', payload: { winnerId: 'p1' } },
  { type: 'NEW_GAME_STARTED', payload: { state: { ...publicGameStateFixture, handNumber: 0 } } },
]
