import type { CardPackId } from '../appearance/types'

export const cardPacks = {
  classic: {
    id: 'classic',
    name: 'Classic two-colour',
    description: 'Traditional black suits and red suits with jumbo indices.',
  },
  'four-color': {
    id: 'four-color',
    name: 'High-contrast four-colour',
    description: 'Black spades, red hearts, blue diamonds, and green clubs.',
  },
} as const satisfies Record<CardPackId, {
  readonly id: CardPackId
  readonly name: string
  readonly description: string
}>

export type { CardPackId } from '../appearance/types'

export function isCardPackId(value: unknown): value is CardPackId {
  return typeof value === 'string' && Object.hasOwn(cardPacks, value)
}
