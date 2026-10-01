import { useEffect, useRef } from 'react'
import type { Card, HoleCards } from '../contracts/api'
import { cardAsset, cardLabel, rankDisplay, suitGlyphs } from '../cards/cardAssets'
import type { CardPackId } from '../cards/cardPacks'
import { Button } from './Button'
import { useCardPackId } from '../theme/ThemeProvider'

export type CardPresentation = 'empty' | 'back' | 'face'
type CardSize = 'compact' | 'regular' | 'board'
type CardMotion = 'none' | 'deal' | 'reveal'

function cardClass(size: CardSize, kind: string, motion: CardMotion): string {
  return `playing-card playing-card--${size} playing-card--${kind}${motion === 'none' ? '' : ` playing-card--motion-${motion}`}`
}

function CardBack({ size, packId, motion }: { size: CardSize; packId: CardPackId; motion: CardMotion }) {
  if (packId === 'classic') {
    return <img className={cardClass(size, 'back', motion)} src="/cards/1B.svg" alt="Face-down playing card" data-card-pack={packId} />
  }
  return (
    <svg
      className={cardClass(size, 'back', motion)}
      data-card-pack={packId}
      viewBox="0 0 250 350"
      role="img"
      aria-label="Face-down playing card"
    >
      <title>Face-down playing card</title>
      <rect className="playing-card__surface" x="2" y="2" width="246" height="346" rx="17" />
      <rect className="playing-card__back-field" x="14" y="14" width="222" height="322" rx="11" />
      <path className="playing-card__back-line" d="M28 42 222 308M222 42 28 308M28 175h194M125 28v294" />
      <circle className="playing-card__back-medallion" cx="125" cy="175" r="57" />
      <text className="playing-card__back-mark" x="125" y="196" textAnchor="middle">♠</text>
    </svg>
  )
}

function CardFace({ card, size, packId, motion }: { card: Card; size: CardSize; packId: CardPackId; motion: CardMotion }) {
  const rank = rankDisplay[card.rank]
  const suit = suitGlyphs[card.suit]
  const label = cardLabel(card)
  if (packId === 'classic') {
    return <img className={`${cardClass(size, 'face', motion)} playing-card--${card.suit.toLowerCase()}`} src={cardAsset(card)} alt={label} data-card-pack={packId} />
  }
  return (
    <svg
      className={`${cardClass(size, 'face', motion)} playing-card--${card.suit.toLowerCase()}`}
      data-card-pack={packId}
      viewBox="0 0 250 350"
      role="img"
      aria-label={label}
    >
      <title>{label}</title>
      <rect className="playing-card__surface" x="2" y="2" width="246" height="346" rx="17" />
      <g className="playing-card__ink">
        <g className="playing-card__index">
          <text x="28" y="48">{rank}</text>
          <text className="playing-card__index-suit" x="28" y="78">{suit}</text>
        </g>
        <g className="playing-card__index playing-card__index--bottom" transform="rotate(180 125 175)">
          <text x="28" y="48">{rank}</text>
          <text className="playing-card__index-suit" x="28" y="78">{suit}</text>
        </g>
        <text className="playing-card__hero-rank" x="125" y="178" textAnchor="middle">{rank}</text>
        <text className="playing-card__hero-suit" x="125" y="265" textAnchor="middle">{suit}</text>
      </g>
    </svg>
  )
}

export function PlayingCard({ card, presentation, size = 'regular', packId, motion = 'none' }: {
  card?: Card | undefined
  presentation: CardPresentation
  size?: CardSize
  packId?: CardPackId | undefined
  motion?: CardMotion
}) {
  const roomCardPackId = useCardPackId()
  const effectivePackId = packId ?? roomCardPackId
  if (presentation === 'empty') {
    return <span className={`playing-card playing-card--${size} playing-card--empty`} aria-label="Empty card slot" />
  }
  if (presentation === 'back') return <CardBack size={size} packId={effectivePackId} motion={motion} />
  if (!card) throw new Error('Face card presentation requires card data')
  return <CardFace card={card} size={size} packId={effectivePackId} motion={motion} />
}

export function CardPair({ cards, revealed, onReveal, onHide, packId }: {
  cards: HoleCards | null
  revealed: boolean
  onReveal?(): void
  onHide?(): void
  packId?: CardPackId | undefined
}) {
  const previousCards = useRef<HoleCards | null>(null)
  const newlyDealt = cards !== null && previousCards.current === null
  useEffect(() => { previousCards.current = cards }, [cards])
  const presentation: CardPresentation = cards === null ? 'empty' : revealed ? 'face' : 'back'
  const motion: CardMotion = cards === null ? 'none' : revealed ? 'reveal' : newlyDealt ? 'deal' : 'reveal'
  return (
    <section className="card-pair" aria-label="Your hole cards">
      <div className="card-pair__cards">
        <PlayingCard card={cards?.first} presentation={presentation} packId={packId} motion={motion} />
        <PlayingCard card={cards?.second} presentation={presentation} packId={packId} motion={motion} />
      </div>
      {cards === null ? <p className="card-pair__status">Waiting for deal</p> : null}
      {cards !== null && !revealed && onReveal ? <Button variant="secondary" onClick={onReveal}>Reveal both cards</Button> : null}
      {cards !== null && revealed && onHide ? <Button variant="secondary" onClick={onHide}>Hide both cards</Button> : null}
    </section>
  )
}
