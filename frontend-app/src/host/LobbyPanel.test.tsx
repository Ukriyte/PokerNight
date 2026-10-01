import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import { LobbyPanel } from './LobbyPanel'
import type { LobbyResponse, PublicPlayerState } from '../contracts/api'

const lobby: LobbyResponse = {
  roomCode: 'ABC123',
  members: [{ playerId: 'p1', displayName: 'Alice', status: 'APPROVED', seatNumber: 0 }],
}
const players: PublicPlayerState[] = [{
  playerId: 'p1', displayName: 'Alice', seatNumber: 0, chips: 1_000,
  streetContribution: 0, bettingStatus: 'ACTIVE', dealer: false, smallBlind: false, bigBlind: false,
}]

function panel(canRemovePlayers: boolean, onKick = vi.fn()) {
  return <LobbyPanel lobby={lobby} players={players} maximumPlayers={6} busyAction={null}
    canRemovePlayers={canRemovePlayers} onApprove={vi.fn()} onReject={vi.fn()} onKick={onKick} />
}

describe('LobbyPanel removal', () => {
  it('sends removal for an approved player between hands', async () => {
    const onKick = vi.fn().mockResolvedValue(undefined)
    render(panel(true, onKick))
    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))
    expect(onKick).toHaveBeenCalledWith('p1')
  })

  it('defers removal while a hand is active', () => {
    render(panel(false))
    expect(screen.getByRole('button', { name: 'Remove after hand' })).toBeDisabled()
    expect(screen.getByText('Players can be removed after the current hand finishes.')).toBeVisible()
  })
})
