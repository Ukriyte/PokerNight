import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import type {
  ActionRequiredEvent,
  CardsDealtEvent,
  CommandReceipt,
  LobbyResponse,
  PublicGameEvent,
  PublicGameState,
  SocketError,
  StatusEvent,
} from '../contracts/api'
import {
  actionRequiredEventSchema,
  cardsDealtEventSchema,
  commandReceiptSchema,
  lobbyResponseSchema,
  publicGameEventSchema,
  publicGameStateSchema,
  roomCodeSchema,
  socketErrorSchema,
  statusEventSchema,
} from '../contracts/schemas'
import type { CommandOutbox, PendingCommand } from '../commands/commandOutbox'

export type SubscriptionProfile = 'HOST' | 'PENDING_PLAYER' | 'APPROVED_PLAYER'
export type SocketConnection = 'connecting' | 'connected' | 'reconnecting' | 'offline'

export interface RoomSocketHandlers {
  onConnection(connection: SocketConnection): void
  onPublicState(state: PublicGameState): void
  onPublicEvent(event: PublicGameEvent): void
  onLobby(lobby: LobbyResponse): void
  onCards(event: CardsDealtEvent): void
  onTurn(event: ActionRequiredEvent): void
  onStatus(event: StatusEvent): void
  onReceipt(receipt: CommandReceipt): void
  onError(error: SocketError): void
  onContractError(error: Error): void
}

export interface RoomSocketConfig {
  roomCode: string
  token: string
  profile: SubscriptionProfile
  brokerURL?: string
}

type ClientFactory = (config: ConstructorParameters<typeof Client>[0]) => Client

export class RoomSocket {
  private client: Client | null = null
  private subscriptions: StompSubscription[] = []
  private active = false
  private reconnectAttempt = 0

  constructor(
    private config: RoomSocketConfig,
    private readonly handlers: RoomSocketHandlers,
    private readonly clientFactory: ClientFactory = options => new Client(options),
  ) {
    roomCodeSchema.parse(config.roomCode)
  }

  activate(): void {
    if (this.active) return
    this.active = true
    this.handlers.onConnection('connecting')
    this.client = this.clientFactory({
      brokerURL: this.config.brokerURL ?? defaultBrokerUrl(),
      connectHeaders: { Authorization: `Bearer ${this.config.token}` },
      reconnectDelay: 500,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => this.connected(),
      onDisconnect: () => this.handlers.onConnection('offline'),
      onWebSocketClose: () => this.closed(),
      onStompError: frame => this.handlers.onContractError(
        new Error(frame.headers.message ?? 'STOMP broker error'),
      ),
    })
    this.client.activate()
  }

  async deactivate(): Promise<void> {
    if (!this.active) return
    this.active = false
    this.unsubscribeAll()
    const client = this.client
    this.client = null
    if (client) await client.deactivate()
    this.handlers.onConnection('offline')
  }

  setProfile(profile: SubscriptionProfile): void {
    if (profile === this.config.profile) return
    this.config = { ...this.config, profile }
    if (this.client?.connected) this.installSubscriptions()
  }

  send(command: PendingCommand): void {
    if (!this.client?.connected) throw new Error('Socket is not connected')
    this.client.publish({ destination: command.destination, body: JSON.stringify(command.payload) })
  }

  sendPersisted(
    outbox: CommandOutbox,
    command: Omit<PendingCommand, 'createdAt'>,
  ): PendingCommand {
    const pending = outbox.add(command)
    this.send(pending)
    return pending
  }

  flush(outbox: CommandOutbox): void {
    for (const pending of outbox.list()) this.send(pending)
  }

  private connected(): void {
    this.reconnectAttempt = 0
    if (this.client) this.client.reconnectDelay = 500
    this.installSubscriptions()
    this.handlers.onConnection('connected')
  }

  private closed(): void {
    if (!this.active) {
      this.handlers.onConnection('offline')
      return
    }
    this.reconnectAttempt += 1
    if (this.client) this.client.reconnectDelay = reconnectDelay(this.reconnectAttempt)
    this.handlers.onConnection('reconnecting')
  }

  private installSubscriptions(): void {
    this.unsubscribeAll()
    const client = this.client
    if (!client?.connected) return
    const room = this.config.roomCode

    this.subscribe('/user/queue/status', statusEventSchema, this.handlers.onStatus)
    this.subscribe('/user/queue/commands', commandReceiptSchema, this.handlers.onReceipt)
    this.subscribe('/user/queue/errors', socketErrorSchema, this.handlers.onError)

    if (this.config.profile === 'HOST') {
      this.subscribe('/user/queue/lobby', lobbyResponseSchema, this.handlers.onLobby)
    }
    if (this.config.profile !== 'PENDING_PLAYER') {
      this.subscribe(`/topic/rooms/${room}/state`, publicGameStateSchema, this.handlers.onPublicState)
      this.subscribe(`/topic/rooms/${room}/events`, publicGameEventSchema, this.handlers.onPublicEvent)
    }
    if (this.config.profile === 'APPROVED_PLAYER') {
      this.subscribe('/user/queue/cards', cardsDealtEventSchema, this.handlers.onCards)
      this.subscribe('/user/queue/turn', actionRequiredEventSchema, this.handlers.onTurn)
    }
  }

  private subscribe<T>(
    destination: string,
    schema: { parse(value: unknown): T },
    receiver: (value: T) => void,
  ): void {
    const subscription = this.client?.subscribe(destination, frame => {
      try {
        receiver(schema.parse(parseFrame(frame)))
      } catch (cause) {
        this.handlers.onContractError(cause instanceof Error ? cause : new Error(String(cause)))
      }
    })
    if (subscription) this.subscriptions.push(subscription)
  }

  private unsubscribeAll(): void {
    for (const subscription of this.subscriptions.splice(0)) subscription.unsubscribe()
  }
}

function parseFrame(frame: IMessage): unknown {
  return JSON.parse(frame.body) as unknown
}

function reconnectDelay(attempt: number): number {
  const steps = [500, 1_000, 2_000, 4_000, 10_000] as const
  return steps[Math.min(attempt - 1, steps.length - 1)] ?? 10_000
}

function defaultBrokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}
