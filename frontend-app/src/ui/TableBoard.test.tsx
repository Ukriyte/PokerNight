import { render, screen, within } from '@testing-library/react'
import { publicGameStateFixture } from '../contracts/contractFixtures'
import { TableBoard } from './TableBoard'

it('renders seats in clockwise DOM order with textual turn and role states', () => {
  render(<TableBoard
    players={publicGameStateFixture.players}
    communityCards={[]}
    pot={75}
    currentPlayerId="p1"
    botPlayerIds={['p2']}
  />)
  const table = screen.getByRole('region', { name: 'Poker table' })
  expect(table).toHaveTextContent('Alice')
  expect(table).toHaveTextContent('Bot')
  expect(screen.getByRole('article', { name: /Alice, 975 chips, current turn/ })).toBeInTheDocument()
  expect(screen.getByText('To act')).toBeVisible()
  expect(screen.getAllByLabelText('Empty card slot')).toHaveLength(5)
})

it('shows both public showdown cards for every revealed player', () => {
  render(<TableBoard
    players={publicGameStateFixture.players}
    communityCards={[]}
    revealedHoleCards={{
      p1: {
        first: { rank: 'ACE', suit: 'SPADES' },
        second: { rank: 'KING', suit: 'HEARTS' },
      },
    }}
    pot={75}
    currentPlayerId={null}
  />)
  expect(within(screen.getByLabelText('Alice revealed cards')).getAllByRole('img')).toHaveLength(2)
  expect(screen.getByRole('img', { name: 'Ace of spades' })).toBeInTheDocument()
  expect(screen.getByRole('img', { name: 'King of hearts' })).toBeInTheDocument()
})
