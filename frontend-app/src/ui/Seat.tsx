import type { HoleCards, PublicPlayerState } from '../contracts/api'
import { PlayingCard } from './PlayingCard'

export function Seat({ player, active = false, bot = false, holeCards }: {
  player?: PublicPlayerState | undefined
  active?: boolean
  bot?: boolean
  holeCards?: HoleCards | undefined
}) {
  if (!player) return <div className="seat seat--empty"><span>Open seat</span></div>
  const labels = [
    player.dealer ? { text: 'D', title: 'Dealer' } : null,
    player.smallBlind ? { text: 'SB', title: 'Small blind — posted automatically' } : null,
    player.bigBlind ? { text: 'BB', title: 'Big blind — posted automatically' } : null,
    player.bettingStatus === 'FOLDED' ? { text: 'Folded', title: 'Folded' } : null,
    player.bettingStatus === 'ALL_IN' ? { text: 'All in', title: 'All in' } : null,
    bot ? { text: 'Bot', title: 'Computer player' } : null,
  ].filter(Boolean)
  return (
    <article className={`seat ${active ? 'seat--active' : ''}`} aria-label={`${player.displayName}, ${player.chips} chips${active ? ', current turn' : ''}`}>
      {active ? <span className="seat__orbit" aria-hidden="true" /> : null}
      <header className="seat__header">
        <strong>{player.displayName}</strong>
        <span className="seat__number">Seat {player.seatNumber + 1}</span>
      </header>
      <span className="seat__chips">{player.chips.toLocaleString()} chips</span>
      {player.streetContribution > 0 ? <span className="seat__bet">Bet {player.streetContribution}</span> : null}
      {active && bot ? <span className="seat__thinking" role="status">Thinking…</span> : null}
      {active && !bot ? <span className="seat__turn-label">To act</span> : null}
      {holeCards ? <div className="seat__cards" aria-label={`${player.displayName} revealed cards`}>
        <PlayingCard card={holeCards.first} presentation="face" size="compact" motion="reveal" />
        <PlayingCard card={holeCards.second} presentation="face" size="compact" motion="reveal" />
      </div> : null}
      {labels.length > 0 ? <ul className="seat__badges">{labels.map(label => label ? <li key={label.title} title={label.title} aria-label={label.title}>{label.text}</li> : null)}</ul> : null}
    </article>
  )
}
