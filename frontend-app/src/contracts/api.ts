import type { CardPackId, RoomAppearance, ThemeId } from '../appearance/types'

export type RoomCode = string
export type MemberStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'KICKED'
export type GamePhase =
  | 'WAITING_FOR_PLAYERS' | 'STARTING_HAND' | 'PRE_FLOP'
  | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN'
  | 'HAND_COMPLETE' | 'GAME_COMPLETE'
export type PlayerAction = 'FOLD' | 'CHECK' | 'CALL' | 'RAISE' | 'ALL_IN'
export type BettingStatus = 'ACTIVE' | 'FOLDED' | 'ALL_IN'
export type Rank =
  | 'TWO' | 'THREE' | 'FOUR' | 'FIVE' | 'SIX' | 'SEVEN'
  | 'EIGHT' | 'NINE' | 'TEN' | 'JACK' | 'QUEEN' | 'KING' | 'ACE'
export type Suit = 'CLUBS' | 'DIAMONDS' | 'HEARTS' | 'SPADES'
export type HandCategory =
  | 'HIGH_CARD' | 'ONE_PAIR' | 'TWO_PAIR' | 'THREE_OF_A_KIND'
  | 'STRAIGHT' | 'FLUSH' | 'FULL_HOUSE' | 'FOUR_OF_A_KIND' | 'STRAIGHT_FLUSH'

export interface Card { rank: Rank; suit: Suit }
export interface HoleCards { first: Card; second: Card }
export interface BlindStructure { smallBlind: number; bigBlind: number }
export interface RoomConfig {
  startingChips: number
  blinds: BlindStructure
  maximumPlayers: number
  botCount: number
  appearance: RoomAppearance
}
export interface PublicPlayerState {
  playerId: string
  displayName: string
  seatNumber: number
  chips: number
  streetContribution: number
  bettingStatus: BettingStatus
  dealer: boolean
  smallBlind: boolean
  bigBlind: boolean
}
export interface PublicGameState {
  phase: GamePhase
  handNumber: number
  handInProgress: boolean
  players: PublicPlayerState[]
  communityCards: Card[]
  revealedHoleCards: Record<string, HoleCards>
  pot: number
  currentBet: number
  minimumRaise: number
  currentPlayerId: string | null
}
export interface PrivatePlayerState {
  game: PublicGameState
  playerId: string
  holeCards: HoleCards | null
  validActions: PlayerAction[]
}
export interface CreateRoomRequest {
  hostName: string
  startingChips: number
  smallBlind: number
  bigBlind: number
  maximumPlayers: number
  botCount: number
  themeId: ThemeId
  cardPackId: CardPackId
}
export interface CreateRoomResponse {
  roomCode: RoomCode
  hostToken: string
  hostUrl: string
  joinUrl: string
  tvUrl: string
}
export interface NetworkInfoResponse { origin: string; address: string; lanAvailable: boolean }
export interface JoinRoomRequest { displayName: string }
export interface JoinRoomResponse { playerId: string; playerToken: string; status: 'PENDING' }
export interface ApprovePlayerRequest { seatNumber: number }
export interface MemberResponse {
  playerId: string
  displayName: string
  status: MemberStatus
  seatNumber: number | null
}
export interface PublicRoomResponse {
  roomCode: RoomCode
  config: RoomConfig
  botPlayerIds: string[]
  game: PublicGameState
}
export interface LobbyResponse { roomCode: RoomCode; members: MemberResponse[] }
export interface MeResponse {
  playerId: string
  displayName: string
  status: MemberStatus
  seatNumber: number | null
  config: RoomConfig
  game: PublicGameState
  privateState: PrivatePlayerState | null
}
export interface HistoryEntry {
  sequence: number
  handNumber: number
  eventType: string
  message: string
  occurredAt: string
}
export interface HistoryPage { entries: HistoryEntry[]; nextCursor: number | null }
export interface HostBootstrapResponse {
  room: PublicRoomResponse
  lobby: LobbyResponse
  history: HistoryPage
}
export interface TablePositions {
  dealerId: string
  smallBlindId: string
  bigBlindId: string
  firstToActPreFlopId: string
  firstToActPostFlopId: string
  clockwisePlayerIds: string[]
}
export interface HandValue { category: HandCategory; tieBreakers: number[] }
export interface EvaluatedHand { value: HandValue; bestFiveCards: Card[] }
export interface Envelope<T extends string, P> { type: T; payload: P }

export type PublicGameEvent =
  | Envelope<'HAND_STARTED', { handNumber: number; positions: TablePositions }>
  | Envelope<'PLAYER_ACTED', {
      playerId: string
      action: PlayerAction
      amount: number | null
      committedChips: number
    }>
  | Envelope<'STREET_DEALT', { street: GamePhase; communityCards: Card[] }>
  | Envelope<'HAND_COMPLETED', {
      payouts: Record<string, number>
      shownHands: Record<string, EvaluatedHand>
      totalPot: number
      communityCards: Card[]
    }>
  | Envelope<'GAME_COMPLETED', { winnerId: string }>
  | Envelope<'NEW_GAME_STARTED', { state: PublicGameState }>
export type CardsDealtEvent = Envelope<'CARDS_DEALT', { playerId: string; cards: HoleCards }>
export type ActionRequiredEvent = Envelope<'ACTION_REQUIRED', {
  playerId: string
  validActions: PlayerAction[]
  currentBet: number
  playerContribution: number
  minimumRaise: number
  pot: number
  playerChips: number
}>
export type StatusEvent =
  | Envelope<'MEMBERSHIP_STATUS', {
      roomCode: RoomCode
      playerId: string
      status: MemberStatus
      seatNumber: number | null
      message: string
    }>
  | Envelope<'ROOM_CLOSED', { roomCode: RoomCode; message: string }>

export interface GameControlCommand { commandId: string }
export interface PlayerActionCommand { commandId: string; action: PlayerAction; amount?: number }
export interface CommandReceipt {
  commandId: string
  replayed: boolean
  phase: GamePhase
  handNumber: number
}
export type RestErrorCode =
  | 'AUTHENTICATION_REQUIRED' | 'INVALID_TOKEN' | 'TOKEN_EXPIRED'
  | 'WRONG_ROOM' | 'NOT_HOST' | 'NOT_PLAYER'
  | 'ROOM_NOT_FOUND' | 'PLAYER_ALREADY_REGISTERED' | 'PLAYER_NOT_PENDING'
  | 'PLAYER_NOT_APPROVED' | 'ROOM_FULL' | 'SEAT_UNAVAILABLE'
  | 'GAME_IN_PROGRESS'
  | 'CODE_GENERATION_EXHAUSTED' | 'VALIDATION_ERROR' | 'INVALID_REQUEST'
export type SocketErrorCode =
  | 'NOT_HOST' | 'PLAYER_NOT_APPROVED' | 'GAME_NOT_READY' | 'ILLEGAL_ACTION'
  | 'COMMAND_ID_REUSED' | 'VALIDATION_ERROR'
export interface SocketError { code: SocketErrorCode; message: string }
export interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  code: RestErrorCode
  instance: string
}
