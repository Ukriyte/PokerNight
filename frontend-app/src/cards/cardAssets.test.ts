import { cardAsset, cardLabel, ranks, suits } from './cardAssets'

describe('card asset mapping', () => {
  it('maps all 52 cards to unique local SVG paths and readable labels', () => {
    const cards = ranks.flatMap((rank) => suits.map((suit) => ({ rank, suit })))
    const paths = cards.map(cardAsset)

    expect(cards).toHaveLength(52)
    expect(new Set(paths).size).toBe(52)
    paths.forEach((path) => expect(path).toMatch(/^\/cards\/(?:[2-9TJQKA])[CDHS]\.svg$/))
    cards.map(cardLabel).forEach((label) => expect(label).toMatch(/ of /))
  })

  it('uses the backend-compatible ten and suit codes', () => {
    expect(cardAsset({ rank: 'TEN', suit: 'HEARTS' })).toBe('/cards/TH.svg')
    expect(cardAsset({ rank: 'ACE', suit: 'SPADES' })).toBe('/cards/AS.svg')
  })
})
