import type { Card, Rank, Suit } from '../contracts/api'

export const ranks: readonly Rank[] = [
  'TWO', 'THREE', 'FOUR', 'FIVE', 'SIX', 'SEVEN', 'EIGHT',
  'NINE', 'TEN', 'JACK', 'QUEEN', 'KING', 'ACE',
]
export const suits: readonly Suit[] = ['CLUBS', 'DIAMONDS', 'HEARTS', 'SPADES']

export const rankCodes: Record<Rank, string> = {
  TWO: '2', THREE: '3', FOUR: '4', FIVE: '5', SIX: '6', SEVEN: '7',
  EIGHT: '8', NINE: '9', TEN: 'T', JACK: 'J', QUEEN: 'Q', KING: 'K', ACE: 'A',
}
export const rankDisplay: Record<Rank, string> = {
  ...rankCodes,
  TEN: '10',
}
export const suitCodes: Record<Suit, string> = {
  CLUBS: 'C', DIAMONDS: 'D', HEARTS: 'H', SPADES: 'S',
}
const rankLabels: Record<Rank, string> = {
  TWO: 'Two', THREE: 'Three', FOUR: 'Four', FIVE: 'Five', SIX: 'Six', SEVEN: 'Seven',
  EIGHT: 'Eight', NINE: 'Nine', TEN: 'Ten', JACK: 'Jack', QUEEN: 'Queen', KING: 'King', ACE: 'Ace',
}
const suitLabels: Record<Suit, string> = {
  CLUBS: 'clubs', DIAMONDS: 'diamonds', HEARTS: 'hearts', SPADES: 'spades',
}

export const suitGlyphs: Record<Suit, string> = {
  CLUBS: '♣', DIAMONDS: '♦', HEARTS: '♥', SPADES: '♠',
}

export function cardAsset(card: Card): string {
  return `/cards/${rankCodes[card.rank]}${suitCodes[card.suit]}.svg`
}

export function cardLabel(card: Card): string {
  return `${rankLabels[card.rank]} of ${suitLabels[card.suit]}`
}
