import { BootstrapGate } from './bootstrapGate'
import { publicEventFixtures, publicGameStateFixture } from '../contracts/contractFixtures'

describe('BootstrapGate', () => {
  it('uses REST as baseline while preserving events and only the newest buffered state', () => {
    const gate = new BootstrapGate()
    gate.begin()

    expect(gate.accept({ type: 'PUBLIC_STATE_RECEIVED', payload: publicGameStateFixture })).toEqual([])
    expect(gate.accept({ type: 'PUBLIC_EVENT_RECEIVED', payload: publicEventFixtures[0]! })).toEqual([])
    const newest = { ...publicGameStateFixture, phase: 'FLOP' as const, handNumber: 2 }
    gate.accept({ type: 'PUBLIC_STATE_RECEIVED', payload: newest })

    expect(gate.complete()).toEqual([
      { type: 'PUBLIC_EVENT_RECEIVED', payload: publicEventFixtures[0] },
      { type: 'PUBLIC_STATE_RECEIVED', payload: newest },
    ])
  })

  it('passes live actions through after bootstrap', () => {
    const gate = new BootstrapGate()
    const action = { type: 'PUBLIC_STATE_RECEIVED' as const, payload: publicGameStateFixture }
    expect(gate.accept(action)).toEqual([action])
  })
})
