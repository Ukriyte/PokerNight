import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { PlayerActionPanel } from './PlayerActionPanel'

const turn = {
  playerId: 'p1',
  validActions: ['FOLD', 'CALL', 'RAISE', 'ALL_IN'] as const,
  currentBet: 100,
  playerContribution: 25,
  minimumRaise: 50,
  pot: 175,
  playerChips: 475,
}

it('presents call cost and sends a raise as target street contribution', async () => {
  const onAction = vi.fn()
  const user = userEvent.setup()
  render(<PlayerActionPanel turn={{ ...turn, validActions: [...turn.validActions] }} disabled={false} onAction={onAction} />)

  expect(screen.getByRole('button', { name: 'Call 75' })).toBeInTheDocument()
  expect(screen.getByLabelText('Raise target')).toHaveAttribute('min', '150')
  expect(screen.getByLabelText('Raise target')).toHaveAttribute('max', '500')
  await user.click(screen.getByRole('button', { name: 'Raise to 150' }))
  expect(onAction).toHaveBeenCalledWith('RAISE', 150)
})

it('renders only actions authorized by the server and disables duplicate taps', () => {
  render(<PlayerActionPanel turn={{ ...turn, validActions: ['CHECK'] }} disabled onAction={() => {}} />)
  expect(screen.getByRole('button', { name: 'Check' })).toBeDisabled()
  expect(screen.queryByRole('button', { name: /fold/i })).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Raise target')).not.toBeInTheDocument()
})
