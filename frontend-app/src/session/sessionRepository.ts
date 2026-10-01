import { z } from 'zod'
import { opaqueTokenSchema, roomCodeSchema } from '../contracts/schemas'
import type { RoomSessionRole, SaveRoomSession, StoredRoomSessionV1 } from './sessionTypes'

const storedSessionSchema = z.discriminatedUnion('role', [
  z.object({
    version: z.literal(1),
    roomCode: roomCodeSchema,
    role: z.literal('HOST'),
    token: opaqueTokenSchema,
    savedAt: z.string().datetime({ offset: true }),
  }).strict(),
  z.object({
    version: z.literal(1),
    roomCode: roomCodeSchema,
    role: z.literal('PLAYER'),
    subjectId: z.string().min(1),
    token: opaqueTokenSchema,
    savedAt: z.string().datetime({ offset: true }),
  }).strict(),
])

export class SessionStorageError extends Error {
  constructor(operation: string, options?: ErrorOptions) {
    super(`Could not ${operation} the room session on this device`, options)
    this.name = 'SessionStorageError'
  }
}

export class SessionRepository {
  constructor(
    private readonly storage: Storage,
    private readonly now: () => Date = () => new Date(),
  ) {}

  save(session: SaveRoomSession): StoredRoomSessionV1 {
    const roomCode = roomCodeSchema.parse(session.roomCode)
    const stored = storedSessionSchema.parse({
      ...session,
      roomCode,
      version: 1,
      savedAt: this.now().toISOString(),
    })
    try {
      this.storage.setItem(this.key(roomCode, stored.role), JSON.stringify(stored))
    } catch (cause) {
      throw new SessionStorageError('save', { cause })
    }
    return stored
  }

  load(roomCode: string, role: RoomSessionRole): StoredRoomSessionV1 | null {
    const validRoomCode = roomCodeSchema.parse(roomCode)
    const key = this.key(validRoomCode, role)
    let raw: string | null
    try {
      raw = this.storage.getItem(key)
    } catch (cause) {
      throw new SessionStorageError('read', { cause })
    }
    if (raw === null) return null

    try {
      const parsed: unknown = JSON.parse(raw)
      const session = storedSessionSchema.parse(parsed)
      if (session.roomCode !== validRoomCode || session.role !== role) {
        this.removeKey(key)
        return null
      }
      return session
    } catch (cause) {
      if (cause instanceof SessionStorageError) throw cause
      this.removeKey(key)
      return null
    }
  }

  remove(roomCode: string, role: RoomSessionRole): void {
    this.removeKey(this.key(roomCodeSchema.parse(roomCode), role))
  }

  removeRoom(roomCode: string): void {
    const validRoomCode = roomCodeSchema.parse(roomCode)
    this.removeKey(this.key(validRoomCode, 'HOST'))
    this.removeKey(this.key(validRoomCode, 'PLAYER'))
  }

  private key(roomCode: string, role: RoomSessionRole): string {
    return `pokernight:v1:${role.toLowerCase()}:${roomCode}`
  }

  private removeKey(key: string): void {
    try {
      this.storage.removeItem(key)
    } catch (cause) {
      throw new SessionStorageError('remove', { cause })
    }
  }
}

export function createBrowserSessionRepository(): SessionRepository {
  try {
    return new SessionRepository(window.localStorage)
  } catch (cause) {
    throw new SessionStorageError('access', { cause })
  }
}
