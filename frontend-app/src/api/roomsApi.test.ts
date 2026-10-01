import { http, HttpResponse } from 'msw'
import {
  createRoomResponseFixture,
  hostBootstrapFixture,
  hostToken,
  meResponseFixture,
  playerToken,
  roomCode,
} from '../contracts/contractFixtures'
import { mockServer } from '../test/server'
import { ApiClientError, ContractValidationError, HttpClient } from './httpClient'
import { RoomsApi } from './roomsApi'

const origin = 'http://frontend.test'

function createApi(): RoomsApi {
  return new RoomsApi(new HttpClient({
    baseUrl: origin,
    sleep: async () => undefined,
    random: () => 0,
  }))
}

describe('RoomsApi', () => {
  it('exposes every frozen REST endpoint with correct methods, auth, bodies, and query values', async () => {
    const observed = new Set<string>()
    const lobby = hostBootstrapFixture.lobby
    const room = hostBootstrapFixture.room

    mockServer.use(
      http.post(`${origin}/api/v1/rooms`, async ({ request }) => {
        observed.add('create')
        expect(request.headers.get('Authorization')).toBeNull()
        expect(await request.json()).toEqual({
          hostName: 'Host', startingChips: 1000, smallBlind: 25, bigBlind: 50,
          maximumPlayers: 2, botCount: 1, themeId: 'midnight', cardPackId: 'classic',
        })
        return HttpResponse.json(createRoomResponseFixture, { status: 201 })
      }),
      http.get(`${origin}/api/v1/rooms/${roomCode}`, () => {
        observed.add('public')
        return HttpResponse.json(room)
      }),
      http.get(`${origin}/api/v1/rooms/${roomCode}/state`, () => {
        observed.add('state')
        return HttpResponse.json(room)
      }),
      http.post(`${origin}/api/v1/rooms/${roomCode}/join-requests`, async ({ request }) => {
        observed.add('join')
        expect(request.headers.get('Authorization')).toBeNull()
        expect(await request.json()).toEqual({ displayName: 'Alice' })
        return HttpResponse.json({ playerId: 'p1', playerToken, status: 'PENDING' }, { status: 202 })
      }),
      http.get(`${origin}/api/v1/rooms/${roomCode}/me`, ({ request }) => {
        observed.add('me')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${playerToken}`)
        return HttpResponse.json(meResponseFixture)
      }),
      http.get(`${origin}/api/v1/rooms/${roomCode}/lobby`, ({ request }) => {
        observed.add('lobby')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${hostToken}`)
        return HttpResponse.json(lobby)
      }),
      http.get(`${origin}/api/v1/rooms/${roomCode}/host-bootstrap`, ({ request }) => {
        observed.add('bootstrap')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${hostToken}`)
        return HttpResponse.json(hostBootstrapFixture)
      }),
      http.post(`${origin}/api/v1/rooms/${roomCode}/members/p1/approval`, async ({ request }) => {
        observed.add('approve')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${hostToken}`)
        expect(await request.json()).toEqual({ seatNumber: 0 })
        return HttpResponse.json(lobby)
      }),
      http.post(`${origin}/api/v1/rooms/${roomCode}/members/p1/rejection`, ({ request }) => {
        observed.add('reject')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${hostToken}`)
        return HttpResponse.json(lobby)
      }),
      http.delete(`${origin}/api/v1/rooms/${roomCode}/members/p1`, ({ request }) => {
        observed.add('kick')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${hostToken}`)
        return HttpResponse.json(lobby)
      }),
      http.get(`${origin}/api/v1/rooms/${roomCode}/history`, ({ request }) => {
        observed.add('history')
        const url = new URL(request.url)
        expect(url.searchParams.get('limit')).toBe('25')
        expect(url.searchParams.get('cursor')).toBe('50')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${playerToken}`)
        return HttpResponse.json(hostBootstrapFixture.history)
      }),
      http.delete(`${origin}/api/v1/rooms/${roomCode}`, ({ request }) => {
        observed.add('close')
        expect(request.headers.get('Authorization')).toBe(`Bearer ${hostToken}`)
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const api = createApi()
    await api.createRoom({
      hostName: 'Host', startingChips: 1000, smallBlind: 25, bigBlind: 50,
      maximumPlayers: 2, botCount: 1, themeId: 'midnight', cardPackId: 'classic',
    })
    await api.getPublicRoom(roomCode)
    await api.getState(roomCode)
    await api.requestToJoin(roomCode, { displayName: 'Alice' })
    await api.getMe(roomCode, { token: playerToken })
    await api.getLobby(roomCode, { token: hostToken })
    await api.getHostBootstrap(roomCode, { token: hostToken })
    await api.approvePlayer(roomCode, 'p1', { seatNumber: 0 }, { token: hostToken })
    await api.rejectPlayer(roomCode, 'p1', { token: hostToken })
    await api.kickPlayer(roomCode, 'p1', { token: hostToken })
    await api.getHistory(roomCode, { token: playerToken, cursor: 50, limit: 25 })
    await api.closeRoom(roomCode, { token: hostToken })

    expect([...observed].sort()).toEqual([
      'approve', 'bootstrap', 'close', 'create', 'history', 'join', 'kick',
      'lobby', 'me', 'public', 'reject', 'state',
    ])
  })

  it('preserves backend problem codes and request IDs without retrying a POST', async () => {
    let attempts = 0
    mockServer.use(http.post(`${origin}/api/v1/rooms`, () => {
      attempts += 1
      return HttpResponse.json({
        type: 'https://pokernight.dev/problems/validation-error',
        title: 'Bad Request', status: 400, detail: 'hostName: must not be blank',
        code: 'VALIDATION_ERROR', instance: '/api/v1/rooms',
      }, { status: 400, headers: { 'X-Request-ID': 'request-123' } })
    }))

    const request = createApi().createRoom({
      hostName: 'Host', startingChips: 1000, smallBlind: 25, bigBlind: 50,
      maximumPlayers: 2, botCount: 0, themeId: 'midnight', cardPackId: 'classic',
    })
    await expect(request).rejects.toMatchObject({
      name: 'ApiClientError', status: 400, code: 'VALIDATION_ERROR',
      detail: 'hostName: must not be blank', requestId: 'request-123',
    } satisfies Partial<ApiClientError>)
    expect(attempts).toBe(1)
  })

  it('retries safe GET transport/server failures at most twice', async () => {
    let attempts = 0
    mockServer.use(http.get(`${origin}/api/v1/rooms/${roomCode}/state`, () => {
      attempts += 1
      if (attempts < 3) return new HttpResponse(null, { status: 503 })
      return HttpResponse.json(hostBootstrapFixture.room)
    }))

    await expect(createApi().getState(roomCode)).resolves.toEqual(hostBootstrapFixture.room)
    expect(attempts).toBe(3)
  })

  it('rejects malformed successful responses before they reach feature code', async () => {
    mockServer.use(http.get(`${origin}/api/v1/rooms/${roomCode}/state`, () =>
      HttpResponse.json({ ...hostBootstrapFixture.room, leakedPrivateCards: ['AS', 'KH'] }),
    ))

    await expect(createApi().getState(roomCode)).rejects.toBeInstanceOf(ContractValidationError)
  })

  it('validates request input before contacting the backend', async () => {
    let contacted = false
    mockServer.use(http.get(`${origin}/api/v1/rooms/invalid/state`, () => {
      contacted = true
      return HttpResponse.json(hostBootstrapFixture.room)
    }))

    expect(() => createApi().getState('invalid')).toThrow()
    expect(contacted).toBe(false)
  })
})
