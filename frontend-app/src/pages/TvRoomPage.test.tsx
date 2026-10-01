import { render, screen } from '@testing-library/react'
import { hostBootstrapFixture } from '../contracts/contractFixtures'
import { TvRoomView } from './TvRoomPage'

it('renders only public table information at TV scale', () => {
  render(<TvRoomView room={hostBootstrapFixture.room} status="ready" lastUpdatedAt={new Date('2026-08-14T12:00:00Z')} />)
  expect(screen.getByRole('heading', { name: 'Cards are in play' })).toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Poker table' })).toBeInTheDocument()
  expect(screen.getByText('75')).toBeInTheDocument()
  expect(screen.queryByText(/host token/i)).not.toBeInTheDocument()
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})

it('keeps the public table present with a clear offline status', () => {
  render(<TvRoomView room={hostBootstrapFixture.room} status="offline" lastUpdatedAt={null} />)
  expect(screen.getByText('Reconnecting · last table remains on screen')).toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Poker table' })).toBeInTheDocument()
  expect(screen.getByText('Showing the last confirmed table. Polling resumes automatically.')).toBeInTheDocument()
})
