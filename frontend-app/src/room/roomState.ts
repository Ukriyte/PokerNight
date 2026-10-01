import type {
  ActionRequiredEvent,
  CardsDealtEvent,
  CommandReceipt,
  HistoryEntry,
  HostBootstrapResponse,
  LobbyResponse,
  MeResponse,
  PublicGameEvent,
  PublicGameState,
  PublicRoomResponse,
  SocketError,
  StatusEvent,
} from '../contracts/api'
import type { PendingCommand } from '../commands/commandOutbox'

export type BootstrapStatus = 'idle' | 'loading' | 'ready' | 'failed'
export type ConnectionStatus = 'offline' | 'connecting' | 'connected' | 'reconnecting'
export type TerminalReason = 'rejected' | 'kicked' | 'closed' | 'expired' | null

export interface RoomClientState {
  bootstrap: BootstrapStatus
  connection: ConnectionStatus
  room: PublicRoomResponse | null
  game: PublicGameState | null
  lobby: LobbyResponse | null
  me: MeResponse | null
  history: HistoryEntry[]
  historyCursor: number | null
  activity: PublicGameEvent[]
  holeCards: CardsDealtEvent['payload']['cards'] | null
  pendingTurn: ActionRequiredEvent['payload'] | null
  pendingCommands: Record<string, PendingCommand>
  lastSocketError: SocketError | null
  terminalReason: TerminalReason
}

export const initialRoomClientState: RoomClientState = {
  bootstrap: 'idle',
  connection: 'offline',
  room: null,
  game: null,
  lobby: null,
  me: null,
  history: [],
  historyCursor: null,
  activity: [],
  holeCards: null,
  pendingTurn: null,
  pendingCommands: {},
  lastSocketError: null,
  terminalReason: null,
}

export type RoomAction =
  | { type: 'BOOTSTRAP_STARTED' }
  | { type: 'BOOTSTRAP_FAILED' }
  | { type: 'BOOTSTRAP_HOST_SUCCEEDED'; payload: HostBootstrapResponse }
  | { type: 'BOOTSTRAP_PLAYER_SUCCEEDED'; payload: MeResponse }
  | { type: 'CONNECTION_CHANGED'; payload: ConnectionStatus }
  | { type: 'PUBLIC_STATE_RECEIVED'; payload: PublicGameState }
  | { type: 'PUBLIC_EVENT_RECEIVED'; payload: PublicGameEvent }
  | { type: 'LOBBY_RECEIVED'; payload: LobbyResponse }
  | { type: 'CARDS_DEALT'; payload: CardsDealtEvent['payload'] }
  | { type: 'ACTION_REQUIRED'; payload: ActionRequiredEvent['payload'] }
  | { type: 'COMMAND_SENT'; payload: PendingCommand }
  | { type: 'COMMAND_RECEIPT'; payload: CommandReceipt }
  | { type: 'COMMANDS_CLEARED' }
  | { type: 'SOCKET_ERROR'; payload: SocketError }
  | { type: 'MEMBERSHIP_STATUS'; payload: StatusEvent['payload'] }
  | { type: 'TERMINATED'; payload: Exclude<TerminalReason, null> }
  | { type: 'HISTORY_APPENDED'; payload: { entries: HistoryEntry[]; nextCursor: number | null } }

export function roomReducer(state: RoomClientState, action: RoomAction): RoomClientState {
  switch (action.type) {
    case 'BOOTSTRAP_STARTED':
      return { ...state, bootstrap: 'loading' }
    case 'BOOTSTRAP_FAILED':
      return { ...state, bootstrap: 'failed' }
    case 'CONNECTION_CHANGED':
      return { ...state, connection: action.payload }
    case 'BOOTSTRAP_HOST_SUCCEEDED':
      return {
        ...state,
        bootstrap: 'ready',
        room: action.payload.room,
        game: action.payload.room.game,
        lobby: action.payload.lobby,
        history: action.payload.history.entries,
        historyCursor: action.payload.history.nextCursor,
      }
    case 'BOOTSTRAP_PLAYER_SUCCEEDED':
      return {
        ...state,
        bootstrap: 'ready',
        me: action.payload,
        game: action.payload.game,
        holeCards: action.payload.privateState?.holeCards ?? null,
        pendingTurn: privateTurnFrom(action.payload),
      }
    case 'PUBLIC_STATE_RECEIVED':
      return {
        ...state,
        game: action.payload,
        pendingTurn: action.payload.currentPlayerId === state.me?.playerId
          ? state.pendingTurn
          : null,
      }
    case 'PUBLIC_EVENT_RECEIVED':
      return applyPublicEvent(state, action.payload)
    case 'LOBBY_RECEIVED':
      return { ...state, lobby: action.payload }
    case 'CARDS_DEALT':
      if (state.me !== null && action.payload.playerId !== state.me.playerId) return state
      return { ...state, holeCards: action.payload.cards }
    case 'ACTION_REQUIRED':
      if (state.me === null || action.payload.playerId !== state.me.playerId) return state
      return { ...state, pendingTurn: action.payload }
    case 'COMMAND_SENT':
      return {
        ...state,
        lastSocketError: null,
        pendingCommands: { ...state.pendingCommands, [action.payload.commandId]: action.payload },
      }
    case 'COMMAND_RECEIPT': {
      const { [action.payload.commandId]: _completed, ...remaining } = state.pendingCommands
      return { ...state, pendingCommands: remaining }
    }
    case 'COMMANDS_CLEARED':
      return { ...state, pendingCommands: {} }
    case 'SOCKET_ERROR':
      return { ...state, lastSocketError: action.payload }
    case 'MEMBERSHIP_STATUS':
      if ('status' in action.payload) {
        const terminal = terminalFromStatus(action.payload.status)
        return { ...state, terminalReason: terminal ?? state.terminalReason }
      }
      return { ...state, terminalReason: 'closed' }
    case 'TERMINATED':
      return {
        ...state,
        terminalReason: action.payload,
        connection: 'offline',
        pendingTurn: null,
        holeCards: null,
      }
    case 'HISTORY_APPENDED':
      return {
        ...state,
        history: deduplicateHistory([...state.history, ...action.payload.entries]),
        historyCursor: action.payload.nextCursor,
      }
  }
}

function applyPublicEvent(state: RoomClientState, event: PublicGameEvent): RoomClientState {
  if (event.type === 'HAND_STARTED') {
    return {
      ...state,
      game: state.game ? { ...state.game, revealedHoleCards: {} } : null,
      activity: [...state.activity, event],
      holeCards: null,
      pendingTurn: null,
    }
  }
  if (event.type === 'NEW_GAME_STARTED') {
    return {
      ...state,
      game: event.payload.state,
      activity: [event],
      history: [],
      historyCursor: null,
      holeCards: null,
      pendingTurn: null,
      pendingCommands: {},
      lastSocketError: null,
      terminalReason: null,
    }
  }
  return { ...state, activity: [...state.activity, event] }
}

function privateTurnFrom(me: MeResponse): ActionRequiredEvent['payload'] | null {
  const privateState = me.privateState
  if (privateState === null || privateState.validActions.length === 0) return null
  const player = privateState.game.players.find(candidate => candidate.playerId === me.playerId)
  if (!player || privateState.game.currentPlayerId !== me.playerId) return null
  return {
    playerId: me.playerId,
    validActions: privateState.validActions,
    currentBet: privateState.game.currentBet,
    playerContribution: player.streetContribution,
    minimumRaise: privateState.game.minimumRaise,
    pot: privateState.game.pot,
    playerChips: player.chips,
  }
}

function terminalFromStatus(status: string): TerminalReason {
  if (status === 'REJECTED') return 'rejected'
  if (status === 'KICKED') return 'kicked'
  return null
}

function deduplicateHistory(entries: HistoryEntry[]): HistoryEntry[] {
  return [...new Map(entries.map(entry => [entry.sequence, entry])).values()]
    .sort((left, right) => right.sequence - left.sequence)
}
