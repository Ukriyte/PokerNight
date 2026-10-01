import { CommandOutbox } from './commandOutbox'
import { roomCode } from '../contracts/contractFixtures'

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>()
  get length(): number { return this.values.size }
  clear(): void { this.values.clear() }
  getItem(key: string): string | null { return this.values.get(key) ?? null }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null }
  removeItem(key: string): void { this.values.delete(key) }
  setItem(key: string, value: string): void { this.values.set(key, value) }
}

describe('CommandOutbox', () => {
  it('persists a command unchanged until its receipt removes it', () => {
    const storage = new MemoryStorage()
    const outbox = new CommandOutbox(storage, roomCode, 'p1', () => new Date('2026-08-16T00:00:00Z'))
    const command = outbox.add({
      commandId: 'action-1',
      destination: `/app/rooms/${roomCode}/actions`,
      payload: { commandId: 'action-1', action: 'FOLD' },
    })

    expect(command.createdAt).toBe('2026-08-16T00:00:00.000Z')
    expect(new CommandOutbox(storage, roomCode, 'p1').list()).toEqual([command])

    outbox.remove('action-1')
    expect(outbox.list()).toEqual([])
  })

  it('does not duplicate an exact retry and rejects command ID reuse', () => {
    const outbox = new CommandOutbox(new MemoryStorage(), roomCode, 'p1')
    const input = {
      commandId: 'same-id',
      destination: `/app/rooms/${roomCode}/actions`,
      payload: { commandId: 'same-id', action: 'CHECK' },
    }
    outbox.add(input)
    outbox.add(input)
    expect(outbox.list()).toHaveLength(1)

    expect(() => outbox.add({ ...input, payload: { ...input.payload, action: 'FOLD' } }))
      .toThrow('already exists with another payload')
  })

  it('fails closed and removes corrupt persisted data', () => {
    const storage = new MemoryStorage()
    storage.setItem(`pokernight:v1:outbox:${roomCode}:p1`, '{bad json')
    const outbox = new CommandOutbox(storage, roomCode, 'p1')
    expect(outbox.list()).toEqual([])
    expect(storage.length).toBe(0)
  })
})
