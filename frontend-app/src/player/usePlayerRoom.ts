import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { ApiClientError } from '../api/httpClient'
import { roomsApi, type RoomsApi } from '../api/roomsApi'
import { CommandOutbox } from '../commands/commandOutbox'
import type { PlayerAction } from '../contracts/api'
import { BootstrapGate } from '../realtime/bootstrapGate'
import { RoomSocket } from '../realtime/roomSocket'
import { initialRoomClientState, roomReducer } from '../room/roomState'
import { createBrowserSessionRepository } from '../session/sessionRepository'
import type { StoredPlayerSessionV1 } from '../session/sessionTypes'

export function usePlayerRoom(session: StoredPlayerSessionV1, onCredentialsCleared: (reason: string) => void, api: RoomsApi = roomsApi) {
  const [state, dispatch] = useReducer(roomReducer, initialRoomClientState)
  const [error, setError] = useState<string | null>(null)
  const socketRef = useRef<RoomSocket | null>(null)
  const stateRef = useRef(state)
  stateRef.current = state
  const gate = useMemo(() => new BootstrapGate(), [])
  const outbox = useMemo(() => new CommandOutbox(window.localStorage, session.roomCode, session.subjectId), [session])

  useEffect(() => {
    let live = true
    let bootstrapping = false
    let bootstrapQueued = false
    const abortControllers = new Set<AbortController>()
    const accept = (action: Parameters<typeof gate.accept>[0]) => {
      for (const ready of gate.accept(action)) dispatch(ready)
    }
    const clearCredentials = (reason: string, terminal: 'rejected' | 'kicked' | 'closed' | 'expired') => {
      outbox.clear()
      createBrowserSessionRepository().remove(session.roomCode, 'PLAYER')
      dispatch({ type: 'TERMINATED', payload: terminal })
      onCredentialsCleared(reason)
    }
    const socket = new RoomSocket(
      { roomCode: session.roomCode, token: session.token, profile: 'PENDING_PLAYER' },
      {
        onConnection: connection => {
          if (!live) return
          dispatch({ type: 'CONNECTION_CHANGED', payload: connection })
          if (connection === 'connected') {
            try { socket.flush(outbox) } catch { /* next reconnect retries exact commands */ }
            void bootstrap()
          }
        },
        onPublicState: payload => accept({ type: 'PUBLIC_STATE_RECEIVED', payload }),
        onPublicEvent: payload => {
          accept({ type: 'PUBLIC_EVENT_RECEIVED', payload })
          void bootstrap()
        },
        onLobby: payload => accept({ type: 'LOBBY_RECEIVED', payload }),
        onCards: event => accept({ type: 'CARDS_DEALT', payload: event.payload }),
        onTurn: event => accept({ type: 'ACTION_REQUIRED', payload: event.payload }),
        onStatus: event => {
          accept({ type: 'MEMBERSHIP_STATUS', payload: event.payload })
          if (event.type === 'ROOM_CLOSED') {
            clearCredentials(event.payload.message, 'closed')
            return
          }
          if (event.payload.status === 'APPROVED') {
            socket.setProfile('APPROVED_PLAYER')
            void bootstrap()
          } else if (event.payload.status === 'REJECTED') {
            clearCredentials(event.payload.message, 'rejected')
          } else if (event.payload.status === 'KICKED') {
            clearCredentials(event.payload.message, 'kicked')
          }
        },
        onReceipt: receipt => {
          outbox.remove(receipt.commandId)
          dispatch({ type: 'COMMAND_RECEIPT', payload: receipt })
        },
        onError: payload => {
          outbox.clear()
          dispatch({ type: 'COMMANDS_CLEARED' })
          dispatch({ type: 'SOCKET_ERROR', payload })
          void bootstrap()
        },
        onContractError: cause => setError(`Realtime update was rejected: ${cause.message}`),
      },
    )
    socketRef.current = socket

    async function bootstrap() {
      if (!live) return
      if (bootstrapping) {
        bootstrapQueued = true
        return
      }
      bootstrapping = true
      const first = stateRef.current.bootstrap === 'idle'
      if (first) dispatch({ type: 'BOOTSTRAP_STARTED' })
      gate.begin()
      const abort = new AbortController()
      abortControllers.add(abort)
      try {
        const me = await api.getMe(session.roomCode, { token: session.token, signal: abort.signal })
        if (!live) return
        socket.setProfile(me.status === 'APPROVED' ? 'APPROVED_PLAYER' : 'PENDING_PLAYER')
        dispatch({ type: 'BOOTSTRAP_PLAYER_SUCCEEDED', payload: me })
        for (const buffered of gate.complete()) dispatch(buffered)
        if (me.status === 'APPROVED') {
          const history = await api.getHistory(session.roomCode, { token: session.token, limit: 50, signal: abort.signal })
          if (live) dispatch({ type: 'HISTORY_APPENDED', payload: history })
        }
        setError(null)
      } catch (cause) {
        gate.reset()
        if (!live || abort.signal.aborted) return
        if (cause instanceof ApiClientError && [401, 403].includes(cause.status)) {
          clearCredentials(cause.detail, 'expired')
          return
        }
        if (first) dispatch({ type: 'BOOTSTRAP_FAILED' })
        setError(cause instanceof Error ? cause.message : 'Could not restore your table state.')
      } finally {
        abortControllers.delete(abort)
        bootstrapping = false
        if (bootstrapQueued && live) {
          bootstrapQueued = false
          void bootstrap()
        }
      }
    }

    const onVisibility = () => { if (document.visibilityState === 'visible' && socketRef.current) void bootstrap() }
    const resume = () => void bootstrap()
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', resume)
    window.addEventListener('pageshow', resume)
    socket.activate()
    return () => {
      live = false
      for (const controller of abortControllers) controller.abort()
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('online', resume)
      window.removeEventListener('pageshow', resume)
      gate.reset()
      socketRef.current = null
      void socket.deactivate()
    }
  }, [api, gate, onCredentialsCleared, outbox, session])

  const sendAction = useCallback((action: PlayerAction, amount?: number) => {
    const turn = state.pendingTurn
    if (!turn || !turn.validActions.includes(action) || state.connection !== 'connected') {
      setError('That action is no longer available. Wait for the latest table update.')
      return
    }
    const commandId = createCommandId()
    const payload: Record<string, unknown> = { commandId, action, ...(amount === undefined ? {} : { amount }) }
    try {
      const pending = socketRef.current?.sendPersisted(outbox, {
        commandId,
        destination: `/app/rooms/${session.roomCode}/actions`,
        payload,
      })
      if (!pending) throw new Error('Socket is not connected')
      dispatch({ type: 'COMMAND_SENT', payload: pending })
      setError(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not send your action.')
    }
  }, [outbox, session.roomCode, state.connection, state.pendingTurn])

  const loadOlderHistory = useCallback(async () => {
    if (state.historyCursor === null) return
    try {
      dispatch({ type: 'HISTORY_APPENDED', payload: await api.getHistory(session.roomCode, {
        token: session.token, cursor: state.historyCursor, limit: 50,
      }) })
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load older history.')
    }
  }, [api, session, state.historyCursor])

  return { state, error, sendAction, loadOlderHistory }
}

function createCommandId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `cmd-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
