import { http, HttpResponse } from 'msw'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { createRoomResponseFixture } from '../contracts/contractFixtures'
import { SessionRepository } from '../session/sessionRepository'
import { mockServer } from '../test/server'
import { CreateRoomPage } from './CreateRoomPage'
import { ThemeProvider } from '../theme/ThemeProvider'

it('creates a room, stores the host key, and follows the backend host URL', async () => {
  let requestBody: unknown
  window.localStorage.clear()
  mockServer.use(http.post(/\/api\/v1\/rooms$/, async ({ request }) => {
    requestBody = await request.json()
    return HttpResponse.json(createRoomResponseFixture, { status: 201 })
  }))
  const user = userEvent.setup()
  render(
    <ThemeProvider><MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<CreateRoomPage />} />
        <Route path="/room/:roomCode" element={<h1>Host room opened</h1>} />
      </Routes>
    </MemoryRouter></ThemeProvider>,
  )

  await user.type(screen.getByLabelText('Host name'), 'Nina')
  await user.click(screen.getByRole('button', { name: 'Create room' }))

  expect(await screen.findByRole('heading', { name: 'Host room opened' })).toBeInTheDocument()
  expect(requestBody).toEqual({
    hostName: 'Nina', startingChips: 1000, smallBlind: 10,
    bigBlind: 20, maximumPlayers: 6, botCount: 0,
    themeId: 'midnight', cardPackId: 'classic',
  })
  expect(new SessionRepository(window.localStorage).load('ABC123', 'HOST')).toMatchObject({
    roomCode: 'ABC123', role: 'HOST', token: createRoomResponseFixture.hostToken,
  })
})

it('shows field-level validation without contacting the backend', async () => {
  const user = userEvent.setup()
  render(<ThemeProvider><MemoryRouter><CreateRoomPage /></MemoryRouter></ThemeProvider>)
  await user.click(screen.getByRole('button', { name: 'Create room' }))
  expect(screen.getByText('Enter a host name.')).toBeInTheDocument()
})

it('submits the host-selected room theme and card pack', async () => {
  let requestBody: unknown
  window.localStorage.clear()
  mockServer.use(http.post(/\/api\/v1\/rooms$/, async ({ request }) => {
    requestBody = await request.json()
    return HttpResponse.json(createRoomResponseFixture, { status: 201 })
  }))
  const user = userEvent.setup()
  render(
    <ThemeProvider><MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<CreateRoomPage />} />
        <Route path="/room/:roomCode" element={<h1>Host room opened</h1>} />
      </Routes>
    </MemoryRouter></ThemeProvider>,
  )

  await user.type(screen.getByLabelText('Host name'), 'Nina')
  await user.selectOptions(screen.getByLabelText('Table theme'), 'broadcast')
  await user.selectOptions(screen.getByLabelText('Card style'), 'four-color')
  await user.click(screen.getByRole('button', { name: 'Create room' }))

  expect(await screen.findByRole('heading', { name: 'Host room opened' })).toBeInTheDocument()
  expect(requestBody).toMatchObject({ themeId: 'broadcast', cardPackId: 'four-color' })
})
