import { z } from 'zod'
import { roomCodeSchema } from '../contracts/schemas'

const pendingCommandSchema = z.object({
  commandId: z.string().min(1).max(100),
  destination: z.string().startsWith('/app/rooms/'),
  payload: z.record(z.string(), z.unknown()),
  createdAt: z.string().datetime({ offset: true }),
}).strict()

const storedOutboxSchema = z.object({
  version: z.literal(1),
  commands: z.array(pendingCommandSchema),
}).strict()

export interface PendingCommand {
  commandId: string
  destination: string
  payload: Record<string, unknown>
  createdAt: string
}

export class CommandOutbox {
  constructor(
    private readonly storage: Storage,
    private readonly roomCode: string,
    private readonly subjectId: string,
    private readonly now: () => Date = () => new Date(),
  ) {
    roomCodeSchema.parse(roomCode)
    if (subjectId.length === 0) throw new Error('subjectId is required')
  }

  list(): PendingCommand[] {
    const raw = this.storage.getItem(this.key())
    if (raw === null) return []
    try {
      return storedOutboxSchema.parse(JSON.parse(raw)).commands
    } catch {
      this.storage.removeItem(this.key())
      return []
    }
  }

  add(command: Omit<PendingCommand, 'createdAt'>): PendingCommand {
    const pending = pendingCommandSchema.parse({ ...command, createdAt: this.now().toISOString() })
    const existing = this.list()
    const sameId = existing.find(item => item.commandId === pending.commandId)
    if (sameId && JSON.stringify(sameId.payload) !== JSON.stringify(pending.payload)) {
      throw new Error(`Command ${pending.commandId} already exists with another payload`)
    }
    if (sameId) return sameId
    this.write([...existing, pending])
    return pending
  }

  remove(commandId: string): void {
    this.write(this.list().filter(command => command.commandId !== commandId))
  }

  clear(): void {
    this.storage.removeItem(this.key())
  }

  private write(commands: PendingCommand[]): void {
    if (commands.length === 0) {
      this.storage.removeItem(this.key())
      return
    }
    this.storage.setItem(this.key(), JSON.stringify({ version: 1, commands }))
  }

  private key(): string {
    return `pokernight:v1:outbox:${this.roomCode}:${this.subjectId}`
  }
}
