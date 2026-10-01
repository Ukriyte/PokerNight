import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { roomsApi, type RoomsApi } from '../api/roomsApi'
import { ApiClientError } from '../api/httpClient'
import { CommandOutbox } from '../commands/commandOutbox'
import type { LobbyResponse } from '../contracts/api'
import { BootstrapGate } from '../realtime/bootstrapGate'
import { RoomSocket } from '../realtime/roomSocket'
import { initialRoomClientState, roomReducer } from '../room/roomState'
import { createBrowserSessionRepository } from '../session/sessionRepository'

export interface HostRoomController {
  state: ReturnType<typeof roomReducer>
  error: string | null
  busyAction: string | null
  startHand(): void
  startNewGame(): void
  approve(playerId: string, seatNumber: number): Promise<void>
  reject(playerId: string): Promise<void>
  kick(playerId: string): Promise<void>
  loadOlderHistory(): Promise<void>
  closeRoom(): Promise<boolean>
  retryBootstrap(): void
}

export function useHostRoom(roomCode: string, api: RoomsApi = roomsApi): HostRoomController {
  const [state, dispatch] = useReducer(roomReducer, initialRoomClientState)
  const [error, setError] = useState<string | null>(null)
  const [busyAction, setBusyAction] = useState<string | null>(null)
  const [bootstrapAttempt, setBootstrapAttempt] = useState(0)
  const socketRef = useRef<RoomSocket | null>(null)
  const gate = useMemo(() => new BootstrapGate(), [])
  const session = useMemo(() => {
    try { return createBrowserSessionRepository().load(roomCode, 'HOST') }
    catch { return null }
  }, [roomCode])
  const outbox = useMemo(
    () => session ? new CommandOutbox(window.localStorage, roomCode, 'HOST') : null,
    [roomCode, session],
  )

  useEffect(() => {
    if (!session) {
      dispatch({ type: 'BOOTSTRAP_FAILED' })
      setError('This browser does not have the host key for this room.')
      return
    }
    const hostToken = session.token

    let live = true
    const abortControllers = new Set<AbortController>()
    let bootstrapping = false
    let bootstrapQueued = false
    let bootstrapped = false
    setError(null)

    const accept = (action: Parameters<typeof gate.accept>[0]) => {
      for (const ready of gate.accept(action)) dispatch(ready)
    }
    const socket = new RoomSocket(
      { roomCode, token: session.token, profile: 'HOST' },
      {
        onConnection: connection => {
          if (!live) return
          dispatch({ type: 'CONNECTION_CHANGED', payload: connection })
          if (connection === 'connected' && outbox) {
            try { socket.flush(outbox) } catch { /* reconnect will retry */ }
            if (bootstrapped) void bootstrap()
          }
        },
        onPublicState: payload => accept({ type: 'PUBLIC_STATE_RECEIVED', payload }),
        onPublicEvent: payload => {
          accept({ type: 'PUBLIC_EVENT_RECEIVED', payload })
          void bootstrap()
        },
        onLobby: payload => accept({ type: 'LOBBY_RECEIVED', payload }),
        onCards: payload => accept({ type: 'CARDS_DEALT', payload: payload.payload }),
        onTurn: payload => accept({ type: 'ACTION_REQUIRED', payload: payload.payload }),
        onStatus: payload => accept({ type: 'MEMBERSHIP_STATUS', payload: payload.payload }),
        onReceipt: payload => {
          outbox?.remove(payload.commandId)
          dispatch({ type: 'COMMAND_RECEIPT', payload })
        },
        onError: payload => {
          outbox?.clear()
          dispatch({ type: 'COMMANDS_CLEARED' })
          dispatch({ type: 'SOCKET_ERROR', payload })
          void bootstrap()
        },
        onContractError: cause => setError(`Realtime update was rejected: ${cause.message}`),
      },
    )
    socketRef.current = socket
    socket.activate()

    async function bootstrap() {
      if (!live) return
      if (bootstrapping) {
        bootstrapQueued = true
        return
      }
      bootstrapping = true
      if (!bootstrapped) dispatch({ type: 'BOOTSTRAP_STARTED' })
      gate.begin()
      const abort = new AbortController()
      abortControllers.add(abort)
      try {
        const payload = await api.getHostBootstrap(roomCode, { token: hostToken, signal: abort.signal })
        if (!live) return
        dispatch({ type: 'BOOTSTRAP_HOST_SUCCEEDED', payload })
        for (const buffered of gate.complete()) dispatch(buffered)
        bootstrapped = true
        setError(null)
      } catch (cause) {
        if (!live || abort.signal.aborted) return
        gate.reset()
        if (!bootstrapped) dispatch({ type: 'BOOTSTRAP_FAILED' })
        setError(errorMessage(cause, 'Could not restore the host room.'))
      } finally {
        abortControllers.delete(abort)
        bootstrapping = false
        if (bootstrapQueued && live) {
          bootstrapQueued = false
          void bootstrap()
        }
      }
    }

    const resume = () => void bootstrap()
    const onVisibility = () => { if (document.visibilityState === 'visible') resume() }
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', resume)
    window.addEventListener('pageshow', resume)
    void bootstrap()

    return () => {
      live = false
      for (const abort of abortControllers) abort.abort()
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('online', resume)
      window.removeEventListener('pageshow', resume)
      gate.reset()
      socketRef.current = null
      void socket.deactivate()
    }
  }, [api, bootstrapAttempt, gate, outbox, roomCode, session])

  const sendControl = useCallback((kind: 'start-hand' | 'new-game') => {
    if (!outbox || state.connection !== 'connected') {
      setError('Reconnect before sending a game command.')
      return
    }
    const commandId = createCommandId()
    const command = {
      commandId,
      destination: `/app/rooms/${roomCode}/${kind}`,
      payload: { commandId },
    }
    try {
      const pending = socketRef.current?.sendPersisted(outbox, command)
      if (!pending) throw new Error('Socket is not connected')
      dispatch({ type: 'COMMAND_SENT', payload: pending })
      setError(null)
    } catch (cause) {
      setError(errorMessage(cause, 'Could not send the game command.'))
    }
  }, [outbox, roomCode, state.connection])

  const lobbyMutation = useCallback(async (
    label: string,
    operation: (token: string) => Promise<LobbyResponse>,
  ) => {
    if (!session) return
    setBusyAction(label)
    setError(null)
    try {
      dispatch({ type: 'LOBBY_RECEIVED', payload: await operation(session.token) })
      const refreshed = await api.getHostBootstrap(roomCode, { token: session.token })
      dispatch({ type: 'BOOTSTRAP_HOST_SUCCEEDED', payload: refreshed })
    } catch (cause) {
      setError(errorMessage(cause, 'Could not update the lobby.'))
    } finally {
      setBusyAction(null)
    }
  }, [api, roomCode, session])

  const approve = useCallback((playerId: string, seatNumber: number) => lobbyMutation(
    `approve:${playerId}`,
    token => api.approvePlayer(roomCode, playerId, { seatNumber }, { token }),
  ), [api, lobbyMutation, roomCode])

  const reject = useCallback((playerId: string) => lobbyMutation(
    `reject:${playerId}`,
    token => api.rejectPlayer(roomCode, playerId, { token }),
  ), [api, lobbyMutation, roomCode])

  const kick = useCallback((playerId: string) => lobbyMutation(
    `kick:${playerId}`,
    token => api.kickPlayer(roomCode, playerId, { token }),
  ), [api, lobbyMutation, roomCode])

  const loadOlderHistory = useCallback(async () => {
    if (!session || state.historyCursor === null) return
    setBusyAction('history')
    setError(null)
    try {
      const payload = await api.getHistory(roomCode, {
        token: session.token,
        cursor: state.historyCursor,
        limit: 50,
      })
      dispatch({ type: 'HISTORY_APPENDED', payload })
    } catch (cause) {
      setError(errorMessage(cause, 'Could not load older history.'))
    } finally {
      setBusyAction(null)
    }
  }, [api, roomCode, session, state.historyCursor])

  const closeRoom = useCallback(async () => {
    if (!session) return false
    setBusyAction('close')
    setError(null)
    try {
      await api.closeRoom(roomCode, { token: session.token })
      outbox?.clear()
      createBrowserSessionRepository().remove(roomCode, 'HOST')
      return true
    } catch (cause) {
      setError(errorMessage(cause, 'Could not close the room.'))
      return false
    } finally {
      setBusyAction(null)
    }
  }, [api, outbox, roomCode, session])

  return {
    state,
    error,
    busyAction,
    startHand: () => sendControl('start-hand'),
    startNewGame: () => sendControl('new-game'),
    approve,
    reject,
    kick,
    loadOlderHistory,
    closeRoom,
    retryBootstrap: () => setBootstrapAttempt(value => value + 1),
  }
}

function errorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiClientError) return `${cause.detail}${cause.requestId ? ` (request ${cause.requestId})` : ''}`
  return cause instanceof Error && cause.message ? cause.message : fallback
}

function createCommandId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `cmd-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
