import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiClientError } from '../api/httpClient'
import { roomsApi, type RoomsApi } from '../api/roomsApi'
import type { PublicRoomResponse } from '../contracts/api'
import { HistoryPanel } from '../host/HistoryPanel'
import { PlayerActionPanel } from '../player/PlayerActionPanel'
import { PlayerTable } from '../player/PlayerTable'
import { usePlayerRoom } from '../player/usePlayerRoom'
import { createBrowserSessionRepository } from '../session/sessionRepository'
import type { StoredPlayerSessionV1 } from '../session/sessionTypes'
import { Banner, Button, CardPair, ConnectionBadge, HistoryNotifications, TextField } from '../ui'
import { useRoomAppearance } from '../theme/useRoomAppearance'

export function PlayerRoomPage() {
  const { roomCode = '' } = useParams()
  const [session, setSession] = useState<StoredPlayerSessionV1 | null>(() => loadPlayerSession(roomCode))
  const [notice, setNotice] = useState<string | null>(null)

  if (session) return <PlayerExperience session={session} onCredentialsCleared={reason => { setNotice(reason); setSession(null) }} />
  return <JoinRoomForm roomCode={roomCode} notice={notice} onJoined={setSession} />
}

export function JoinRoomForm({ roomCode, notice, onJoined, api = roomsApi }: {
  roomCode: string
  notice: string | null
  onJoined(session: StoredPlayerSessionV1): void
  api?: RoomsApi
}) {
  const [room, setRoom] = useState<PublicRoomResponse | null>(null)
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useRoomAppearance(room?.config.appearance)

  useEffect(() => {
    const abort = new AbortController()
    setLoading(true)
    void api.getPublicRoom(roomCode, { signal: abort.signal })
      .then(setRoom)
      .catch(cause => { if (!abort.signal.aborted) setError(cause instanceof ApiClientError ? cause.detail : 'This room could not be reached.') })
      .finally(() => { if (!abort.signal.aborted) setLoading(false) })
    return () => abort.abort()
  }, [api, roomCode])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const displayName = name.trim()
    if (!displayName) { setError('Enter the name your friends will recognize.'); return }
    setBusy(true)
    setError(null)
    try {
      const joined = await api.requestToJoin(roomCode, { displayName })
      const stored = createBrowserSessionRepository().save({
        roomCode, role: 'PLAYER', subjectId: joined.playerId, token: joined.playerToken,
      })
      if (stored.role !== 'PLAYER') throw new Error('Player session was not stored')
      onJoined(stored)
    } catch (cause) {
      setError(cause instanceof ApiClientError ? cause.detail : 'The join request did not finish. Check this browser before submitting again.')
    } finally { setBusy(false) }
  }

  if (loading) return <main className="status-page"><span className="large-spinner" aria-hidden="true" /><h1>Finding room {roomCode}</h1></main>
  if (!room) return <main className="status-page"><h1>Room unavailable</h1>{error ? <Banner tone="error" title="Could not open the room">{error}</Banner> : null}<Link className="text-link" to="/">Return home</Link></main>

  return <main className="join-page">
    <section className="join-room-card">
      <p className="eyebrow">Room {room.roomCode}</p>
      <h1>Take a seat</h1>
      <p>{room.game.players.length} of {room.config.maximumPlayers} seats currently occupied · {room.config.blinds.smallBlind}/{room.config.blinds.bigBlind} blinds</p>
      {notice ? <Banner tone="warning" title="Previous access ended">{notice}</Banner> : null}
      {error ? <Banner tone="error" title="Could not request a seat">{error}</Banner> : null}
      <form onSubmit={event => void submit(event)}>
        <TextField label="Your table name" autoComplete="name" maxLength={40} value={name} onChange={event => setName(event.target.value)} />
        <Button type="submit" busy={busy}>Ask to join</Button>
      </form>
    </section>
  </main>
}

function PlayerExperience({ session, onCredentialsCleared }: { session: StoredPlayerSessionV1; onCredentialsCleared(reason: string): void }) {
  const stableCleared = useCallback(onCredentialsCleared, [onCredentialsCleared])
  const player = usePlayerRoom(session, stableCleared)
  const { state } = player
  const [revealed, setRevealed] = useState(false)
  useRoomAppearance(state.me?.config.appearance)
  useEffect(() => setRevealed(false), [state.game?.handNumber, state.holeCards])

  if (state.bootstrap === 'idle' || state.bootstrap === 'loading') return <main className="status-page"><span className="large-spinner" aria-hidden="true" /><h1>Restoring your seat</h1></main>
  if (state.bootstrap === 'failed') return <main className="status-page"><h1>Seat could not be restored</h1>{player.error ? <Banner tone="error" title="Refresh did not finish">{player.error}</Banner> : null}</main>
  if (!state.me || !state.game) return <main className="status-page"><h1>Waiting for table state</h1></main>

  if (state.me.status === 'PENDING') return <main className="pending-page">
    <div className="pending-token" aria-hidden="true">{session.roomCode}</div>
    <p className="eyebrow">Request sent</p><h1>Waiting for the host</h1><p>Your name is on the host’s approval list. Keep this page open; approval appears automatically.</p>
    <ConnectionBadge status={state.connection} />
    {player.error ? <Banner tone="error" title="Connection needs attention">{player.error}</Banner> : null}
  </main>

  const commandPending = Object.keys(state.pendingCommands).length > 0
  return <main className="player-page">
    <HistoryNotifications entries={state.history} />
    <header className="player-topbar"><div><p className="eyebrow">Room {session.roomCode}</p><h1>{state.me.displayName}</h1></div><ConnectionBadge status={state.connection} /></header>
    {state.connection !== 'connected' ? <Banner tone="warning" title="Actions are paused">Your last confirmed cards and table remain visible while reconnecting.</Banner> : null}
    {player.error ? <Banner tone="error" title="Table update failed">{player.error}</Banner> : null}
    {state.lastSocketError ? <Banner tone="error" title={state.lastSocketError.code}>{state.lastSocketError.message}</Banner> : null}
    <PlayerTable game={state.game} playerId={state.me.playerId} />
    <details className="player-history"><summary>History ({state.history.length})</summary><HistoryPanel entries={state.history} hasOlder={state.historyCursor !== null} loading={false} onLoadOlder={player.loadOlderHistory} /></details>
    <section className="player-private" aria-label="Your private cards and controls">
      <div className="player-private__identity"><span>Your cards</span><strong>{state.game.players.find(candidate => candidate.playerId === state.me?.playerId)?.chips.toLocaleString() ?? '—'} chips</strong></div>
      <CardPair cards={state.holeCards} revealed={revealed} onReveal={() => setRevealed(true)} onHide={() => setRevealed(false)} />
      {state.pendingTurn ? <PlayerActionPanel turn={state.pendingTurn} disabled={commandPending || state.connection !== 'connected'} onAction={player.sendAction} /> : <p className="waiting-turn" role="status">{state.game.handInProgress ? 'Watching the current turn…' : 'Waiting for the host to deal the next hand.'}</p>}
    </section>
  </main>
}

function loadPlayerSession(roomCode: string): StoredPlayerSessionV1 | null {
  try {
    const session = createBrowserSessionRepository().load(roomCode, 'PLAYER')
    return session?.role === 'PLAYER' ? session : null
  } catch { return null }
}
