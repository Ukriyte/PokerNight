import { z } from 'zod'

const safeInteger = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER)
const positiveSafeInteger = safeInteger.min(1)
const identifier = z.string().min(1)

export const roomCodeSchema = z.string().regex(/^[A-Z0-9]{6}$/)
export const opaqueTokenSchema = z.string().regex(/^[a-f0-9]{64}$/)
export const memberStatusSchema = z.enum(['PENDING', 'APPROVED', 'REJECTED', 'KICKED'])
export const gamePhaseSchema = z.enum([
  'WAITING_FOR_PLAYERS', 'STARTING_HAND', 'PRE_FLOP', 'FLOP', 'TURN', 'RIVER',
  'SHOWDOWN', 'HAND_COMPLETE', 'GAME_COMPLETE',
])
export const playerActionSchema = z.enum(['FOLD', 'CHECK', 'CALL', 'RAISE', 'ALL_IN'])
export const bettingStatusSchema = z.enum(['ACTIVE', 'FOLDED', 'ALL_IN'])
export const rankSchema = z.enum([
  'TWO', 'THREE', 'FOUR', 'FIVE', 'SIX', 'SEVEN', 'EIGHT',
  'NINE', 'TEN', 'JACK', 'QUEEN', 'KING', 'ACE',
])
export const suitSchema = z.enum(['CLUBS', 'DIAMONDS', 'HEARTS', 'SPADES'])
export const handCategorySchema = z.enum([
  'HIGH_CARD', 'ONE_PAIR', 'TWO_PAIR', 'THREE_OF_A_KIND', 'STRAIGHT',
  'FLUSH', 'FULL_HOUSE', 'FOUR_OF_A_KIND', 'STRAIGHT_FLUSH',
])
export const themeIdSchema = z.enum(['midnight', 'broadcast'])
export const cardPackIdSchema = z.enum(['classic', 'four-color'])
export const roomAppearanceSchema = z.object({
  themeId: themeIdSchema,
  cardPackId: cardPackIdSchema,
}).strict()

export const cardSchema = z.object({ rank: rankSchema, suit: suitSchema }).strict()
export const holeCardsSchema = z.object({ first: cardSchema, second: cardSchema }).strict()
  .refine(({ first, second }) => first.rank !== second.rank || first.suit !== second.suit, {
    message: 'Hole cards must be different',
  })
export const blindStructureSchema = z.object({
  smallBlind: positiveSafeInteger,
  bigBlind: positiveSafeInteger,
}).strict().refine(({ smallBlind, bigBlind }) => smallBlind < bigBlind, {
  message: 'Small blind must be less than big blind',
})
export const roomConfigSchema = z.object({
  startingChips: positiveSafeInteger,
  blinds: blindStructureSchema,
  maximumPlayers: z.number().int().min(2).max(6),
  botCount: z.number().int().min(0).max(5),
  appearance: roomAppearanceSchema,
}).strict().superRefine((value, context) => {
  if (value.startingChips < value.blinds.bigBlind) {
    context.addIssue({ code: 'custom', path: ['startingChips'], message: 'Must cover the big blind' })
  }
  if (value.botCount >= value.maximumPlayers) {
    context.addIssue({ code: 'custom', path: ['botCount'], message: 'Must leave a human seat' })
  }
})

export const publicPlayerStateSchema = z.object({
  playerId: identifier,
  displayName: z.string().min(1).max(40),
  seatNumber: z.number().int().min(0).max(5),
  chips: safeInteger,
  streetContribution: safeInteger,
  bettingStatus: bettingStatusSchema,
  dealer: z.boolean(),
  smallBlind: z.boolean(),
  bigBlind: z.boolean(),
}).strict()
export const publicGameStateSchema = z.object({
  phase: gamePhaseSchema,
  handNumber: safeInteger,
  handInProgress: z.boolean(),
  players: z.array(publicPlayerStateSchema).max(6),
  communityCards: z.array(cardSchema).max(5),
  revealedHoleCards: z.record(z.string(), holeCardsSchema),
  pot: safeInteger,
  currentBet: safeInteger,
  minimumRaise: positiveSafeInteger,
  currentPlayerId: identifier.nullable(),
}).strict()
export const privatePlayerStateSchema = z.object({
  game: publicGameStateSchema,
  playerId: identifier,
  holeCards: holeCardsSchema.nullable(),
  validActions: z.array(playerActionSchema),
}).strict()

export const createRoomRequestSchema = z.object({
  hostName: z.string().trim().min(1).max(40),
  startingChips: z.number().int().min(50).max(Number.MAX_SAFE_INTEGER),
  smallBlind: positiveSafeInteger,
  bigBlind: z.number().int().min(2).max(Number.MAX_SAFE_INTEGER),
  maximumPlayers: z.number().int().min(2).max(6),
  botCount: z.number().int().min(0).max(5),
  themeId: themeIdSchema,
  cardPackId: cardPackIdSchema,
}).strict().superRefine((value, context) => {
  if (value.smallBlind >= value.bigBlind) {
    context.addIssue({ code: 'custom', path: ['smallBlind'], message: 'Must be less than big blind' })
  }
  if (value.startingChips < value.bigBlind) {
    context.addIssue({ code: 'custom', path: ['startingChips'], message: 'Must cover the big blind' })
  }
  if (value.botCount >= value.maximumPlayers) {
    context.addIssue({ code: 'custom', path: ['botCount'], message: 'Must leave a human seat' })
  }
})
export const createRoomResponseSchema = z.object({
  roomCode: roomCodeSchema,
  hostToken: opaqueTokenSchema,
  hostUrl: z.string().startsWith('/room/'),
  joinUrl: z.string().startsWith('/room/'),
  tvUrl: z.string().startsWith('/room/'),
}).strict()
export const networkInfoResponseSchema = z.object({
  origin: z.string().url(),
  address: z.string().min(1),
  lanAvailable: z.boolean(),
}).strict()
export const joinRoomRequestSchema = z.object({ displayName: z.string().trim().min(1).max(40) }).strict()
export const joinRoomResponseSchema = z.object({
  playerId: identifier,
  playerToken: opaqueTokenSchema,
  status: z.literal('PENDING'),
}).strict()
export const approvePlayerRequestSchema = z.object({
  seatNumber: z.number().int().min(0).max(5),
}).strict()
export const memberResponseSchema = z.object({
  playerId: identifier,
  displayName: z.string().min(1).max(40),
  status: memberStatusSchema,
  seatNumber: z.number().int().min(0).max(5).nullable(),
}).strict()
export const publicRoomResponseSchema = z.object({
  roomCode: roomCodeSchema,
  config: roomConfigSchema,
  botPlayerIds: z.array(identifier),
  game: publicGameStateSchema,
}).strict()
export const lobbyResponseSchema = z.object({
  roomCode: roomCodeSchema,
  members: z.array(memberResponseSchema),
}).strict()
export const meResponseSchema = z.object({
  playerId: identifier,
  displayName: z.string().min(1).max(40),
  status: memberStatusSchema,
  seatNumber: z.number().int().min(0).max(5).nullable(),
  config: roomConfigSchema,
  game: publicGameStateSchema,
  privateState: privatePlayerStateSchema.nullable(),
}).strict()
export const historyEntrySchema = z.object({
  sequence: positiveSafeInteger,
  handNumber: safeInteger,
  eventType: z.string().min(1),
  message: z.string(),
  occurredAt: z.string().datetime({ offset: true }),
}).strict()
export const historyPageSchema = z.object({
  entries: z.array(historyEntrySchema),
  nextCursor: positiveSafeInteger.nullable(),
}).strict()
export const hostBootstrapResponseSchema = z.object({
  room: publicRoomResponseSchema,
  lobby: lobbyResponseSchema,
  history: historyPageSchema,
}).strict()

export const tablePositionsSchema = z.object({
  dealerId: identifier,
  smallBlindId: identifier,
  bigBlindId: identifier,
  firstToActPreFlopId: identifier,
  firstToActPostFlopId: identifier,
  clockwisePlayerIds: z.array(identifier).min(2).max(6),
}).strict()
export const handValueSchema = z.object({
  category: handCategorySchema,
  tieBreakers: z.array(z.number().int()),
}).strict()
export const evaluatedHandSchema = z.object({
  value: handValueSchema,
  bestFiveCards: z.array(cardSchema).length(5),
}).strict()

const handStartedEventSchema = z.object({
  type: z.literal('HAND_STARTED'),
  payload: z.object({ handNumber: positiveSafeInteger, positions: tablePositionsSchema }).strict(),
}).strict()
const playerActedEventSchema = z.object({
  type: z.literal('PLAYER_ACTED'),
  payload: z.object({
    playerId: identifier,
    action: playerActionSchema,
    amount: positiveSafeInteger.nullable(),
    committedChips: safeInteger,
  }).strict(),
}).strict()
const streetDealtEventSchema = z.object({
  type: z.literal('STREET_DEALT'),
  payload: z.object({ street: gamePhaseSchema, communityCards: z.array(cardSchema).max(5) }).strict(),
}).strict()
const handCompletedEventSchema = z.object({
  type: z.literal('HAND_COMPLETED'),
  payload: z.object({
    payouts: z.record(z.string(), safeInteger),
    shownHands: z.record(z.string(), evaluatedHandSchema),
    totalPot: safeInteger,
    communityCards: z.array(cardSchema).max(5),
  }).strict(),
}).strict()
const gameCompletedEventSchema = z.object({
  type: z.literal('GAME_COMPLETED'),
  payload: z.object({ winnerId: identifier }).strict(),
}).strict()
const newGameStartedEventSchema = z.object({
  type: z.literal('NEW_GAME_STARTED'),
  payload: z.object({ state: publicGameStateSchema }).strict(),
}).strict()
export const publicGameEventSchema = z.discriminatedUnion('type', [
  handStartedEventSchema,
  playerActedEventSchema,
  streetDealtEventSchema,
  handCompletedEventSchema,
  gameCompletedEventSchema,
  newGameStartedEventSchema,
])
export const cardsDealtEventSchema = z.object({
  type: z.literal('CARDS_DEALT'),
  payload: z.object({ playerId: identifier, cards: holeCardsSchema }).strict(),
}).strict()
export const actionRequiredEventSchema = z.object({
  type: z.literal('ACTION_REQUIRED'),
  payload: z.object({
    playerId: identifier,
    validActions: z.array(playerActionSchema).min(1),
    currentBet: safeInteger,
    playerContribution: safeInteger,
    minimumRaise: positiveSafeInteger,
    pot: safeInteger,
    playerChips: safeInteger,
  }).strict(),
}).strict()
export const statusEventSchema = z.discriminatedUnion('type', [
  z.object({
    type: z.literal('MEMBERSHIP_STATUS'),
    payload: z.object({
      roomCode: roomCodeSchema,
      playerId: identifier,
      status: memberStatusSchema,
      seatNumber: z.number().int().min(0).max(5).nullable(),
      message: z.string(),
    }).strict(),
  }).strict(),
  z.object({
    type: z.literal('ROOM_CLOSED'),
    payload: z.object({ roomCode: roomCodeSchema, message: z.string() }).strict(),
  }).strict(),
])

export const gameControlCommandSchema = z.object({ commandId: z.string().min(1).max(100) }).strict()
export const playerActionCommandSchema = z.object({
  commandId: z.string().min(1).max(100),
  action: playerActionSchema,
  amount: positiveSafeInteger.optional(),
}).strict().superRefine((value, context) => {
  if (value.action === 'RAISE' && value.amount === undefined) {
    context.addIssue({ code: 'custom', path: ['amount'], message: 'A raise requires a target amount' })
  }
  if (value.action !== 'RAISE' && value.amount !== undefined) {
    context.addIssue({ code: 'custom', path: ['amount'], message: 'Only a raise accepts an amount' })
  }
})
export const commandReceiptSchema = z.object({
  commandId: z.string().min(1).max(100),
  replayed: z.boolean(),
  phase: gamePhaseSchema,
  handNumber: safeInteger,
}).strict()
export const restErrorCodeSchema = z.enum([
  'AUTHENTICATION_REQUIRED', 'INVALID_TOKEN', 'TOKEN_EXPIRED', 'WRONG_ROOM', 'NOT_HOST',
  'NOT_PLAYER', 'ROOM_NOT_FOUND', 'PLAYER_ALREADY_REGISTERED', 'PLAYER_NOT_PENDING',
  'PLAYER_NOT_APPROVED', 'ROOM_FULL', 'SEAT_UNAVAILABLE', 'GAME_IN_PROGRESS', 'CODE_GENERATION_EXHAUSTED',
  'VALIDATION_ERROR', 'INVALID_REQUEST',
])
export const socketErrorCodeSchema = z.enum([
  'NOT_HOST', 'PLAYER_NOT_APPROVED', 'GAME_NOT_READY', 'ILLEGAL_ACTION',
  'COMMAND_ID_REUSED', 'VALIDATION_ERROR',
])
export const socketErrorSchema = z.object({ code: socketErrorCodeSchema, message: z.string() }).strict()
export const apiProblemSchema = z.object({
  type: z.string().url(),
  title: z.string().min(1),
  status: z.number().int().min(400).max(599),
  detail: z.string(),
  code: restErrorCodeSchema,
  instance: z.string().startsWith('/'),
}).strict()
