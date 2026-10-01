import { cardPacks, isCardPackId } from './cardPacks'

describe('card packs', () => {
  it('registers classic and high-contrast four-colour packs', () => {
    expect(cardPacks.classic.name).toBe('Classic two-colour')
    expect(cardPacks['four-color'].name).toBe('High-contrast four-colour')
    expect(isCardPackId('classic')).toBe(true)
    expect(isCardPackId('four-color')).toBe(true)
    expect(isCardPackId('unknown')).toBe(false)
  })
})
