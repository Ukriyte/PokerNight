import {
  cardsDealtFixture,
  createRoomResponseFixture,
  hostBootstrapFixture,
  meResponseFixture,
  publicEventFixtures,
} from './contractFixtures'
import {
  cardsDealtEventSchema,
  createRoomResponseSchema,
  hostBootstrapResponseSchema,
  meResponseSchema,
  playerActionCommandSchema,
  publicGameEventSchema,
  publicGameStateSchema,
} from './schemas'

describe('frozen backend contract schemas', () => {
  it('accepts representative REST and STOMP payloads', () => {
    expect(createRoomResponseSchema.parse(createRoomResponseFixture)).toEqual(createRoomResponseFixture)
    expect(hostBootstrapResponseSchema.parse(hostBootstrapFixture)).toEqual(hostBootstrapFixture)
    expect(meResponseSchema.parse(meResponseFixture)).toEqual(meResponseFixture)
    expect(cardsDealtEventSchema.parse(cardsDealtFixture)).toEqual(cardsDealtFixture)
    publicEventFixtures.forEach((event) => expect(publicGameEventSchema.parse(event)).toEqual(event))
  })

  it('fails closed for unknown fields and malformed values', () => {
    expect(createRoomResponseSchema.safeParse({
      ...createRoomResponseFixture,
      leakedToken: 'should-not-be-accepted',
    }).success).toBe(false)
    expect(publicGameStateSchema.safeParse({
      ...meResponseFixture.game,
      pot: Number.MAX_SAFE_INTEGER + 1,
    }).success).toBe(false)
    expect(cardsDealtEventSchema.safeParse({
      ...cardsDealtFixture,
      type: 'HAND_STARTED',
    }).success).toBe(false)
  })

  it('enforces raise target semantics at the command boundary', () => {
    expect(playerActionCommandSchema.safeParse({
      commandId: 'raise-1', action: 'RAISE', amount: 150,
    }).success).toBe(true)
    expect(playerActionCommandSchema.safeParse({
      commandId: 'raise-2', action: 'RAISE',
    }).success).toBe(false)
    expect(playerActionCommandSchema.safeParse({
      commandId: 'call-1', action: 'CALL', amount: 50,
    }).success).toBe(false)
  })
})
