import { SessionRepository, SessionStorageError } from './sessionRepository'

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>()
  get length(): number { return this.values.size }
  clear(): void { this.values.clear() }
  getItem(key: string): string | null { return this.values.get(key) ?? null }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null }
  removeItem(key: string): void { this.values.delete(key) }
  setItem(key: string, value: string): void { this.values.set(key, value) }
}

const now = () => new Date('2026-08-14T00:00:00Z')

describe('SessionRepository', () => {
  it('keeps host and player credentials separate for the same room', () => {
    const storage = new MemoryStorage()
    const sessions = new SessionRepository(storage, now)
    sessions.save({ roomCode: 'ABC123', role: 'HOST', token: 'a'.repeat(64) })
    sessions.save({ roomCode: 'ABC123', role: 'PLAYER', subjectId: 'p1', token: 'b'.repeat(64) })

    expect(sessions.load('ABC123', 'HOST')).toEqual({
      version: 1, roomCode: 'ABC123', role: 'HOST', token: 'a'.repeat(64),
      savedAt: '2026-08-14T00:00:00.000Z',
    })
    expect(sessions.load('ABC123', 'PLAYER')).toEqual({
      version: 1, roomCode: 'ABC123', role: 'PLAYER', subjectId: 'p1', token: 'b'.repeat(64),
      savedAt: '2026-08-14T00:00:00.000Z',
    })
  })

  it('removes corrupted or wrong-role data instead of returning it', () => {
    const storage = new MemoryStorage()
    const sessions = new SessionRepository(storage, now)
    storage.setItem('pokernight:v1:player:ABC123', '{not-json')
    expect(sessions.load('ABC123', 'PLAYER')).toBeNull()
    expect(storage.getItem('pokernight:v1:player:ABC123')).toBeNull()

    storage.setItem('pokernight:v1:host:ABC123', JSON.stringify({
      version: 1, roomCode: 'ABC123', role: 'PLAYER', subjectId: 'p1',
      token: 'b'.repeat(64), savedAt: '2026-08-14T00:00:00.000Z',
    }))
    expect(sessions.load('ABC123', 'HOST')).toBeNull()
  })

  it('clears one role or the whole room deliberately', () => {
    const storage = new MemoryStorage()
    const sessions = new SessionRepository(storage, now)
    sessions.save({ roomCode: 'ABC123', role: 'HOST', token: 'a'.repeat(64) })
    sessions.save({ roomCode: 'ABC123', role: 'PLAYER', subjectId: 'p1', token: 'b'.repeat(64) })
    sessions.remove('ABC123', 'HOST')
    expect(sessions.load('ABC123', 'HOST')).toBeNull()
    expect(sessions.load('ABC123', 'PLAYER')).not.toBeNull()
    sessions.removeRoom('ABC123')
    expect(sessions.load('ABC123', 'PLAYER')).toBeNull()
  })

  it('reports unavailable device storage without discarding the cause', () => {
    const broken = new MemoryStorage()
    broken.setItem = () => { throw new DOMException('denied', 'SecurityError') }
    const sessions = new SessionRepository(broken, now)
    expect(() => sessions.save({
      roomCode: 'ABC123', role: 'HOST', token: 'a'.repeat(64),
    })).toThrow(SessionStorageError)
  })
})
