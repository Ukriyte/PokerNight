import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { HttpClient } from '../api/httpClient'
import { RoomsApi } from '../api/roomsApi'
import { hostBootstrapFixture, playerToken } from '../contracts/contractFixtures'
import { JoinRoomForm } from './PlayerRoomPage'
import { ThemeProvider } from '../theme/ThemeProvider'

function apiWith(fetcher: typeof fetch): RoomsApi {
  return new RoomsApi(new HttpClient({ baseUrl: 'http://frontend.test', fetcher, sleep: async () => undefined }))
}

it('looks up the room and persists the pending player identity through the join callback', async () => {
  const onJoined = vi.fn()
  const fetcher = vi.fn<typeof fetch>()
    .mockResolvedValueOnce(new Response(JSON.stringify(hostBootstrapFixture.room), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ playerId: 'p-new', playerToken, status: 'PENDING' }), { status: 202, headers: { 'Content-Type': 'application/json' } }))
  const user = userEvent.setup()
  render(<ThemeProvider><MemoryRouter><JoinRoomForm roomCode="ABC123" notice={null} onJoined={onJoined} api={apiWith(fetcher)} /></MemoryRouter></ThemeProvider>)

  expect(await screen.findByRole('heading', { name: 'Take a seat' })).toBeInTheDocument()
  await user.type(screen.getByLabelText('Your table name'), 'Maya')
  await user.click(screen.getByRole('button', { name: 'Ask to join' }))

  expect(onJoined).toHaveBeenCalledWith(expect.objectContaining({
    roomCode: 'ABC123', role: 'PLAYER', subjectId: 'p-new', token: playerToken,
  }))
  expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({ displayName: 'Maya' })
})

it('requires a recognizable name before creating a join request', async () => {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValueOnce(new Response(JSON.stringify(hostBootstrapFixture.room), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  const user = userEvent.setup()
  render(<ThemeProvider><MemoryRouter><JoinRoomForm roomCode="ABC123" notice={null} onJoined={() => {}} api={apiWith(fetcher)} /></MemoryRouter></ThemeProvider>)
  await screen.findByRole('heading', { name: 'Take a seat' })
  await user.click(screen.getByRole('button', { name: 'Ask to join' }))
  expect(screen.getByText('Enter the name your friends will recognize.')).toBeInTheDocument()
  expect(fetcher).toHaveBeenCalledTimes(1)
})
