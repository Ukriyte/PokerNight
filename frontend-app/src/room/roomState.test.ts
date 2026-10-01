import { cardsDealtFixture, hostBootstrapFixture, meResponseFixture, publicEventFixtures, publicGameStateFixture } from '../contracts/contractFixtures'
import { initialRoomClientState, roomReducer } from './roomState'

describe('roomReducer', () => {
  it('clears pending commands after a socket rejection so they are not replayed forever', () => {
    const pending = {
      commandId: 'action-1', destination: '/app/rooms/ABC123/actions',
      payload: { commandId: 'action-1', action: 'CALL' }, createdAt: '2026-08-14T00:00:00Z',
    }
    const withCommand = roomReducer(initialRoomClientState, { type: 'COMMAND_SENT', payload: pending })
    expect(roomReducer(withCommand, { type: 'COMMANDS_CLEARED' }).pendingCommands).toEqual({})
  })
  it('replaces host baseline atomically', () => {
    const state = roomReducer(initialRoomClientState, {
      type: 'BOOTSTRAP_HOST_SUCCEEDED', payload: hostBootstrapFixture,
    })
    expect(state.bootstrap).toBe('ready')
    expect(state.game).toEqual(hostBootstrapFixture.room.game)
    expect(state.lobby).toEqual(hostBootstrapFixture.lobby)
    expect(state.history).toEqual(hostBootstrapFixture.history.entries)
  })

  it('restores player cards and legal turn from /me', () => {
    const state = roomReducer(initialRoomClientState, {
      type: 'BOOTSTRAP_PLAYER_SUCCEEDED', payload: meResponseFixture,
    })
    expect(state.holeCards).toEqual(meResponseFixture.privateState?.holeCards)
    expect(state.pendingTurn?.validActions).toEqual(meResponseFixture.privateState?.validActions)
  })

  it('clears stale private hand state when a new hand starts', () => {
    const withPlayer = { ...initialRoomClientState, me: meResponseFixture, holeCards: cardsDealtFixture.payload.cards }
    const state = roomReducer(withPlayer, {
      type: 'PUBLIC_EVENT_RECEIVED', payload: publicEventFixtures[0]!,
    })
    expect(state.holeCards).toBeNull()
    expect(state.pendingTurn).toBeNull()
  })

  it('replaces public state instead of merging chip values', () => {
    const old = { ...initialRoomClientState, game: publicGameStateFixture }
    const replacement = {
      ...publicGameStateFixture,
      players: publicGameStateFixture.players.map(player => ({ ...player, chips: 123 })),
    }
    expect(roomReducer(old, { type: 'PUBLIC_STATE_RECEIVED', payload: replacement }).game)
      .toBe(replacement)
  })

  it('tracks commands until receipts and preserves them on socket errors', () => {
    const command = {
      commandId: 'c1', destination: '/app/rooms/ABC123/actions',
      payload: { commandId: 'c1', action: 'FOLD' }, createdAt: '2026-08-16T00:00:00Z',
    }
    let state = roomReducer(initialRoomClientState, { type: 'COMMAND_SENT', payload: command })
    state = roomReducer(state, {
      type: 'SOCKET_ERROR', payload: { code: 'ILLEGAL_ACTION', message: 'not your turn' },
    })
    expect(state.pendingCommands.c1).toEqual(command)
    state = roomReducer(state, {
      type: 'COMMAND_RECEIPT', payload: { commandId: 'c1', replayed: true, phase: 'PRE_FLOP', handNumber: 1 },
    })
    expect(state.pendingCommands).toEqual({})
  })
})
