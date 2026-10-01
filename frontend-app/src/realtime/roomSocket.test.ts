import { Client, type IFrame, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { roomCode, hostToken, publicGameStateFixture } from '../contracts/contractFixtures'
import { RoomSocket, type RoomSocketHandlers, type SocketConnection } from './roomSocket'

describe('RoomSocket', () => {
  function harness(profile: 'HOST' | 'PENDING_PLAYER' | 'APPROVED_PLAYER') {
    const destinations: string[] = []
    const subscriptions = new Map<string, (message: IMessage) => void>()
    const connection: SocketConnection[] = []
    let options: ConstructorParameters<typeof Client>[0] | undefined
    let activateCount = 0
    let deactivateCount = 0

    const fakeClient = {
      connected: true,
      reconnectDelay: 0,
      activate: () => { activateCount += 1 },
      deactivate: async () => { deactivateCount += 1 },
      subscribe: (destination: string, callback: (message: IMessage) => void) => {
        destinations.push(destination)
        subscriptions.set(destination, callback)
        return { unsubscribe: vi.fn() } as unknown as StompSubscription
      },
      publish: vi.fn(),
    }
    const handlers: RoomSocketHandlers = {
      onConnection: value => connection.push(value),
      onPublicState: vi.fn(), onPublicEvent: vi.fn(), onLobby: vi.fn(),
      onCards: vi.fn(), onTurn: vi.fn(), onStatus: vi.fn(), onReceipt: vi.fn(),
      onError: vi.fn(), onContractError: vi.fn(),
    }
    const socket = new RoomSocket(
      { roomCode, token: hostToken, profile, brokerURL: 'ws://test/ws' }, handlers,
      clientOptions => {
        options = clientOptions
        return fakeClient as unknown as Client
      },
    )
    return {
      socket, handlers, destinations, subscriptions, connection,
      connect: () => options?.onConnect?.({} as IFrame),
      activateCount: () => activateCount,
      deactivateCount: () => deactivateCount,
      fakeClient,
    }
  }

  it('is activation-idempotent and installs the host subscription set once', async () => {
    const test = harness('HOST')
    test.socket.activate()
    test.socket.activate()
    test.connect()

    expect(test.activateCount()).toBe(1)
    expect(test.destinations).toEqual([
      '/user/queue/status', '/user/queue/commands', '/user/queue/errors',
      '/user/queue/lobby', `/topic/rooms/${roomCode}/state`, `/topic/rooms/${roomCode}/events`,
    ])
    await test.socket.deactivate()
    await test.socket.deactivate()
    expect(test.deactivateCount()).toBe(1)
  })

  it('keeps pending players off gameplay topics and adds private queues after approval', () => {
    const test = harness('PENDING_PLAYER')
    test.socket.activate()
    test.connect()
    expect(test.destinations).toEqual([
      '/user/queue/status', '/user/queue/commands', '/user/queue/errors',
    ])

    test.destinations.length = 0
    test.socket.setProfile('APPROVED_PLAYER')
    expect(test.destinations).toEqual([
      '/user/queue/status', '/user/queue/commands', '/user/queue/errors',
      `/topic/rooms/${roomCode}/state`, `/topic/rooms/${roomCode}/events`,
      '/user/queue/cards', '/user/queue/turn',
    ])
  })

  it('validates incoming frames before delivering them', () => {
    const test = harness('HOST')
    test.socket.activate()
    test.connect()
    const callback = test.subscriptions.get(`/topic/rooms/${roomCode}/state`)
    callback?.({ body: JSON.stringify(publicGameStateFixture) } as IMessage)
    callback?.({ body: '{"invalid":true}' } as IMessage)
    expect(test.handlers.onPublicState).toHaveBeenCalledWith(publicGameStateFixture)
    expect(test.handlers.onContractError).toHaveBeenCalledTimes(1)
  })
})
