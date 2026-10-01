import type { CardPackId, ThemeId } from '../appearance/types'

export interface PokerTheme {
  readonly id: string
  readonly name: string
  readonly description: string
  readonly colorScheme: 'dark' | 'light'
  readonly cardPackId: CardPackId
  readonly tableTextureUrl: string
}

export const pokerThemes = {
  midnight: {
    id: 'midnight',
    name: 'Midnight Casino',
    description: 'Deep green felt, warm brass accents, and classic cards.',
    colorScheme: 'dark',
    cardPackId: 'classic',
    tableTextureUrl: '/themes/midnight-felt.jpg',
  },
  broadcast: {
    id: 'broadcast',
    name: 'Poker Broadcast',
    description: 'Cool navy surfaces and crisp cyan signals for shared screens.',
    colorScheme: 'dark',
    cardPackId: 'four-color',
    tableTextureUrl: '/themes/broadcast-felt.jpg',
  },
} as const satisfies Record<ThemeId, PokerTheme>

export type { ThemeId } from '../appearance/types'

export const defaultThemeId: ThemeId = 'midnight'
export const availableThemes: readonly PokerTheme[] = Object.values(pokerThemes)

export function isThemeId(value: unknown): value is ThemeId {
  return typeof value === 'string' && Object.hasOwn(pokerThemes, value)
}

export function getTheme(themeId: ThemeId): PokerTheme {
  return pokerThemes[themeId]
}
