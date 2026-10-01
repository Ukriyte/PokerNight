import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { HistoryPanel } from '../host/HistoryPanel'
import { JoinAccess } from '../host/JoinAccess'
import { LobbyPanel } from '../host/LobbyPanel'
import { useHostRoom } from '../host/useHostRoom'
import { Banner, Button, ConnectionBadge, Dialog, HistoryNotifications, TableBoard } from '../ui'
import { useRoomAppearance } from '../theme/useRoomAppearance'

export function HostRoomPage() {
  const { roomCode = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const host = useHostRoom(roomCode)
  const [confirmClose, setConfirmClose] = useState(false)
  const { state } = host
  const room = state.room
  useRoomAppearance(room?.config.appearance)
  const game = state.game
  const joinPath = (location.state as { joinUrl?: string } | null)?.joinUrl ?? `/room/${roomCode}/play`
  const connected = state.connection === 'connected'
  const canStart = Boolean(game && !game.handInProgress && game.phase !== 'GAME_COMPLETE' && game.players.length >= 2)

  if (state.bootstrap === 'loading' || state.bootstrap === 'idle') {
    return <main className="status-page"><span className="large-spinner" aria-hidden="true" /><h1>Restoring room {roomCode}</h1><p>Loading the table, lobby, and history.</p></main>
  }
  if (state.bootstrap === 'failed' || !room || !game) {
    return <main className="status-page"><h1>Host access unavailable</h1><Banner tone="error" title="Room could not be restored">{host.error}</Banner><div className="button-row"><Button onClick={host.retryBootstrap}>Try again</Button><Link className="text-link" to="/">Create another room</Link></div></main>
  }

  const close = async () => {
    if (await host.closeRoom()) navigate('/', { replace: true })
  }

  return (
    <main className="host-page">
      <HistoryNotifications entries={state.history} />
      <header className="host-topbar">
        <div><p className="eyebrow">Host table</p><h1>Room {roomCode}</h1></div>
        <div className="host-topbar__actions"><ConnectionBadge status={state.connection} /><Button variant="quiet" onClick={() => setConfirmClose(true)}>Close room</Button></div>
      </header>
      {state.connection !== 'connected' ? <Banner tone="warning" title="Controls are paused">The last confirmed table remains visible while the connection recovers.</Banner> : null}
      {host.error ? <Banner tone="error" title="Action needs attention">{host.error}</Banner> : null}
      {state.lastSocketError ? <Banner tone="error" title={state.lastSocketError.code}>{state.lastSocketError.message}</Banner> : null}

      <section className="host-game" aria-labelledby="table-title">
        <header className="panel-heading"><div><p className="eyebrow">Hand {game.handNumber}</p><h2 id="table-title">{phaseLabel(game.phase)}</h2></div><span>{room.config.blinds.smallBlind}/{room.config.blinds.bigBlind} blinds</span></header>
        <TableBoard players={game.players} communityCards={game.communityCards} revealedHoleCards={game.revealedHoleCards} pot={game.pot} currentPlayerId={game.currentPlayerId} botPlayerIds={room.botPlayerIds} />
        <div className="game-controls">
          {game.phase === 'GAME_COMPLETE'
            ? <Button disabled={!connected} onClick={host.startNewGame}>Start new game</Button>
            : <Button disabled={!connected || !canStart} onClick={host.startHand}>{game.handNumber === 0 ? 'Start first hand' : 'Deal next hand'}</Button>}
          {!canStart && !game.handInProgress && game.phase !== 'GAME_COMPLETE' ? <span>At least two seated players or bots are required.</span> : null}
          {game.handInProgress ? <span>Hand in progress · turns advance from player actions.</span> : null}
        </div>
      </section>

      <div className="host-grid">
        <JoinAccess roomCode={roomCode} joinPath={joinPath} />
        <LobbyPanel lobby={state.lobby ?? { roomCode, members: [] }} players={game.players} maximumPlayers={room.config.maximumPlayers} busyAction={host.busyAction} canRemovePlayers={connected && !game.handInProgress} onApprove={host.approve} onReject={host.reject} onKick={host.kick} />
        <HistoryPanel entries={state.history} hasOlder={state.historyCursor !== null} loading={host.busyAction === 'history'} onLoadOlder={host.loadOlderHistory} />
      </div>

      <Dialog open={confirmClose} title="Close this room?" onClose={() => setConfirmClose(false)}><p>Players will be disconnected and this host key will stop working.</p><div className="button-row"><Button variant="danger" busy={host.busyAction === 'close'} onClick={() => void close()}>Close room</Button><Button variant="secondary" onClick={() => setConfirmClose(false)}>Keep room open</Button></div></Dialog>
    </main>
  )
}

function phaseLabel(phase: string): string {
  return phase.toLowerCase().split('_').map(word => word[0]?.toUpperCase() + word.slice(1)).join(' ')
}
