import type { Card, HoleCards, PublicPlayerState } from '../contracts/api'
import { PlayingCard } from './PlayingCard'
import { Seat } from './Seat'

export function TableBoard({ players, communityCards, pot, currentPlayerId, botPlayerIds = [], revealedHoleCards = {} }: {
  players: PublicPlayerState[]
  communityCards: Card[]
  pot: number
  currentPlayerId: string | null
  botPlayerIds?: string[]
  revealedHoleCards?: Record<string, HoleCards>
}) {
  const seats = Array.from({ length: 6 }, (_, seatNumber) => players.find(player => player.seatNumber === seatNumber))
  return (
    <section className="table-board" aria-label="Poker table">
      <div className="table-board__seats">
        {seats.map((player, index) => (
          <Seat
            key={player?.playerId ?? `empty-${index}`}
            player={player}
            active={player?.playerId === currentPlayerId}
            bot={player ? botPlayerIds.includes(player.playerId) : false}
            holeCards={player ? revealedHoleCards[player.playerId] : undefined}
          />
        ))}
      </div>
      <div className="table-board__felt">
        <div className="board-cards" aria-label="Community cards">
          {Array.from({ length: 5 }, (_, index) => {
            const card = communityCards[index]
            return <PlayingCard key={index} card={card} presentation={card ? 'face' : 'empty'} size="board" motion={card ? 'deal' : 'none'} />
          })}
        </div>
        <div className="pot"><span>Pot</span><strong>{pot.toLocaleString()}</strong></div>
      </div>
    </section>
  )
}
