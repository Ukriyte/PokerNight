export type ThemeId = 'midnight' | 'broadcast'
export type CardPackId = 'classic' | 'four-color'

export interface RoomAppearance {
  themeId: ThemeId
  cardPackId: CardPackId
}
