import { useParams } from 'react-router-dom'
import type { PublicRoomResponse } from '../contracts/api'
import { useTvRoom, type TvStatus } from '../tv/useTvRoom'
import { Banner, Button, TableBoard } from '../ui'
import { useRoomAppearance } from '../theme/useRoomAppearance'

export function TvRoomPage() {
  const { roomCode = '' } = useParams()
  const tv = useTvRoom(roomCode)
  useRoomAppearance(tv.room?.config.appearance)

  if (tv.status === 'loading') return <main className="tv-status"><span className="large-spinner" aria-hidden="true" /><h1>Opening table {roomCode}</h1></main>
  if (tv.status === 'not-found') return <main className="tv-status"><div className="tv-mark" aria-hidden="true">♠</div><h1>Room {roomCode} is not available</h1><p>Check the room code or ask the host to create a new table.</p><Button onClick={tv.refresh}>Try again</Button></main>
  if (!tv.room) return <main className="tv-status"><h1>Table temporarily offline</h1><p>The display will recover automatically when the server is reachable.</p><Button onClick={tv.refresh}>Try now</Button></main>
  return <TvRoomView room={tv.room} status={tv.status} lastUpdatedAt={tv.lastUpdatedAt} />
}

export function TvRoomView({ room, status, lastUpdatedAt }: {
  room: PublicRoomResponse
  status: Exclude<TvStatus, 'loading' | 'not-found'>
  lastUpdatedAt: Date | null
}) {
  const game = room.game
  return <main className="tv-page">
    <header className="tv-topbar">
      <div><p className="eyebrow">PokerNight · room {room.roomCode}</p><h1>{tvPhaseCopy(game.phase, game.players.length)}</h1></div>
      <div className={`tv-signal tv-signal--${status}`} role="status">
        <span aria-hidden="true" />
        {status === 'offline' ? 'Reconnecting · last table remains on screen' : lastUpdatedAt ? `Live · ${lastUpdatedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : 'Live'}
      </div>
    </header>
    {status === 'offline' ? <Banner tone="warning" title="Server temporarily unreachable">Showing the last confirmed table. Polling resumes automatically.</Banner> : null}
    <section className="tv-table-wrap" aria-label={`Room ${room.roomCode} public table`}>
      <TableBoard players={game.players} communityCards={game.communityCards} revealedHoleCards={game.revealedHoleCards} pot={game.pot} currentPlayerId={game.currentPlayerId} botPlayerIds={room.botPlayerIds} />
    </section>
    <footer className="tv-footer">
      <span>Hand {game.handNumber}</span>
      <strong>{phaseLabel(game.phase)}</strong>
      <span>{room.config.blinds.smallBlind}/{room.config.blinds.bigBlind} blinds</span>
    </footer>
  </main>
}

function tvPhaseCopy(phase: string, playerCount: number): string {
  if (phase === 'WAITING_FOR_PLAYERS') return playerCount < 2 ? 'Waiting for players' : 'Ready for the first deal'
  if (phase === 'HAND_COMPLETE') return 'Hand complete'
  if (phase === 'GAME_COMPLETE') return 'Game complete'
  return 'Cards are in play'
}

function phaseLabel(phase: string): string {
  return phase.toLowerCase().split('_').map(word => word[0]?.toUpperCase() + word.slice(1)).join(' ')
}
