import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CardPair, PlayingCard } from './PlayingCard'

const cards = {
  first: { rank: 'ACE' as const, suit: 'SPADES' as const },
  second: { rank: 'KING' as const, suit: 'HEARTS' as const },
}

function AtomicReveal() {
  const [revealed, setRevealed] = React.useState(false)
  return <CardPair cards={cards} revealed={revealed} onReveal={() => setRevealed(true)} onHide={() => setRevealed(false)} />
}

import React from 'react'

describe('CardPair', () => {
  it('shows two neutral slots while waiting instead of fake card data', () => {
    render(<CardPair cards={null} revealed={false} />)
    expect(screen.getAllByLabelText('Empty card slot')).toHaveLength(2)
    expect(screen.getByText('Waiting for deal')).toBeInTheDocument()
  })

  it('reveals both validated cards in one interaction', async () => {
    const user = userEvent.setup()
    render(<AtomicReveal />)
    expect(screen.getAllByRole('img', { name: 'Face-down playing card' })).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: 'Reveal both cards' }))
    expect(screen.getByRole('img', { name: 'Ace of spades' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'King of hearts' })).toBeInTheDocument()
    expect(screen.queryByRole('img', { name: 'Face-down playing card' })).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Ace of spades' })).toHaveClass('playing-card--motion-reveal')
    await user.click(screen.getByRole('button', { name: 'Hide both cards' }))
    expect(screen.getAllByRole('img', { name: 'Face-down playing card' })).toHaveLength(2)
    expect(screen.queryByRole('img', { name: 'Ace of spades' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reveal both cards' })).toBeVisible()
  })

  it('supports an explicit high-contrast four-colour pack', () => {
    render(<PlayingCard card={cards.first} presentation="face" packId="four-color" />)
    expect(screen.getByRole('img', { name: 'Ace of spades' })).toHaveAttribute('data-card-pack', 'four-color')
  })

  it('uses the original classic SVG asset for the classic pack', () => {
    render(<PlayingCard card={{ rank: 'TEN', suit: 'DIAMONDS' }} presentation="face" packId="classic" />)
    const card = screen.getByRole('img', { name: 'Ten of diamonds' })
    expect(card).toHaveAttribute('src', '/cards/TD.svg')
  })

  it('displays ten as 10 in the generated four-colour pack', () => {
    render(<PlayingCard card={{ rank: 'TEN', suit: 'DIAMONDS' }} presentation="face" packId="four-color" />)
    expect(screen.getByRole('img', { name: 'Ten of diamonds' })).toHaveTextContent('10')
  })

})
