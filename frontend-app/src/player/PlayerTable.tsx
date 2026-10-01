import type { PublicGameState } from '../contracts/api'
import { TableBoard } from '../ui'

export function PlayerTable({ game, playerId, botPlayerIds = [] }: {
  game: PublicGameState
  playerId: string
  botPlayerIds?: string[]
}) {
  const me = game.players.find(player => player.playerId === playerId)
  return (
    <section className="player-table" aria-labelledby="player-table-title">
      <header className="panel-heading">
        <div><p className="eyebrow">Hand {game.handNumber}</p><h2 id="player-table-title">{phaseLabel(game.phase)}</h2></div>
        <span>{me ? `${me.chips.toLocaleString()} chips` : 'Waiting for seat'}</span>
      </header>
      <TableBoard players={game.players} communityCards={game.communityCards} revealedHoleCards={game.revealedHoleCards} pot={game.pot} currentPlayerId={game.currentPlayerId} botPlayerIds={botPlayerIds} />
    </section>
  )
}

function phaseLabel(phase: string): string {
  return phase.toLowerCase().split('_').map(word => word[0]?.toUpperCase() + word.slice(1)).join(' ')
}
