import { Client } from '@stomp/stompjs'

const baseUrl = required('POKERNIGHT_DEMO_BASE_URL')
const roomCode = required('POKERNIGHT_DEMO_ROOM_CODE')
const hostToken = required('POKERNIGHT_DEMO_HOST_TOKEN')
const aliceToken = required('POKERNIGHT_DEMO_ALICE_TOKEN')
const bobToken = required('POKERNIGHT_DEMO_BOB_TOKEN')
const socketUrl = websocketUrl(baseUrl)

const observed = {
  publicEvents: [],
  publicStates: [],
  cards: new Map(),
  receipts: [],
  errors: [],
}

let handMode = 'PASSIVE'
let firstActionCommandId = null
let firstActionDuplicated = false
let turnSerial = 0
let fatalError = null

function required(name) {
  const value = process.env[name]
  if (!value) throw new Error(`Missing environment variable: ${name}`)
  return value
}

function websocketUrl(httpBase) {
  const url = new URL('/ws', httpBase)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}

function assert(condition, message) {
  if (!condition) throw new Error(`ASSERTION FAILED: ${message}`)
  console.log(`  ✓ ${message}`)
}

function print(label, value) {
  console.log(`\n--- ${label} ---`)
  console.log(typeof value === 'string' ? value : JSON.stringify(value, null, 2))
}

function parse(frame) {
  try { return JSON.parse(frame.body) } catch { return frame.body }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function waitFor(description, predicate, timeoutMs = 12_000) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    if (fatalError) throw fatalError
    const value = predicate()
    if (value) return value
    await sleep(20)
  }
  throw new Error(`Timed out waiting for ${description}`)
}

function eventCount(type) {
  return observed.publicEvents.filter(item => item.actor === 'HOST' && item.message?.type === type).length
}

function latestState() {
  return observed.publicStates.at(-1)?.message ?? null
}

class DemoActor {
  constructor(label, token, role) {
    this.label = label
    this.token = token
    this.role = role
    this.client = null
    this.connected = false
  }

  async connect() {
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error(`${this.label} failed to connect`)), 8_000)
      this.client = new Client({
        brokerURL: socketUrl,
        connectHeaders: { Authorization: `Bearer ${this.token}` },
        reconnectDelay: 0,
        heartbeatIncoming: 0,
        heartbeatOutgoing: 0,
        debug: process.env.POKERNIGHT_STOMP_DEBUG === '1'
          ? message => console.log(`[${this.label} STOMP] ${message}`)
          : () => {},
        onConnect: () => {
          this.subscribe()
          this.connected = true
          clearTimeout(timeout)
          resolve()
        },
        onStompError: frame => {
          const error = new Error(`${this.label} broker error: ${frame.headers.message}\n${frame.body}`)
          fatalError = error
          reject(error)
        },
        onWebSocketError: event => {
          const error = new Error(`${this.label} WebSocket error: ${event?.message ?? 'unknown error'}`)
          fatalError = error
          reject(error)
        },
      })
      this.client.activate()
    })
  }

  subscribe() {
    this.client.subscribe('/user/queue/commands', frame => {
      const message = parse(frame)
      observed.receipts.push({ actor: this.label, message })
      print(`${this.label} PRIVATE command receipt`, message)
    })
    this.client.subscribe('/user/queue/errors', frame => {
      const message = parse(frame)
      observed.errors.push({ actor: this.label, message })
      print(`${this.label} PRIVATE command error`, message)
    })
    this.client.subscribe(`/topic/rooms/${roomCode}/events`, frame => {
      const message = parse(frame)
      observed.publicEvents.push({ actor: this.label, message })
      if (this.role === 'HOST') print('PUBLIC game event', message)
    })
    this.client.subscribe(`/topic/rooms/${roomCode}/state`, frame => {
      const message = parse(frame)
      observed.publicStates.push({ actor: this.label, message })
      if (this.role === 'HOST') print('PUBLIC authoritative state', message)
    })

    if (this.role === 'PLAYER') {
      this.client.subscribe('/user/queue/cards', frame => {
        const message = parse(frame)
        const list = observed.cards.get(this.label) ?? []
        list.push(message)
        observed.cards.set(this.label, list)
        print(`${this.label} PRIVATE cards`, message)
      })
      this.client.subscribe('/user/queue/turn', frame => {
        const message = parse(frame)
        print(`${this.label} PRIVATE turn prompt`, message)
        this.answerTurn(message)
      })
    }
  }

  answerTurn(envelope) {
    const valid = envelope?.payload?.validActions ?? []
    let action
    if (handMode === 'EARLY_FOLD') action = valid.includes('FOLD') ? 'FOLD' : valid[0]
    else if (valid.includes('CHECK')) action = 'CHECK'
    else if (valid.includes('CALL')) action = 'CALL'
    else action = valid.includes('FOLD') ? 'FOLD' : valid[0]
    if (!action) {
      fatalError = new Error(`${this.label} received a turn without a legal action`)
      return
    }

    const command = { commandId: `walkthrough-action-${++turnSerial}`, action }
    print(`${this.label} SEND action`, command)
    this.publish(`/app/rooms/${roomCode}/actions`, command)

    if (handMode === 'PASSIVE' && !firstActionDuplicated) {
      firstActionDuplicated = true
      firstActionCommandId = command.commandId
      print(`${this.label} RETRY exact same action command`, command)
      this.publish(`/app/rooms/${roomCode}/actions`, command)
    }
  }

  publish(destination, body) {
    this.client.publish({ destination, body: JSON.stringify(body) })
  }

  async disconnect() {
    if (this.client) await this.client.deactivate()
    this.connected = false
  }
}

let host = new DemoActor('HOST', hostToken, 'HOST')
let alice = new DemoActor('ALICE', aliceToken, 'PLAYER')
let bob = new DemoActor('BOB', bobToken, 'PLAYER')

try {
  console.log('\n========== SOCKET BOOTSTRAP ==========')
  await Promise.all([host.connect(), alice.connect(), bob.connect()])
  await sleep(150)
  assert(host.connected && alice.connected && bob.connected, 'host and both approved players connect with bearer tokens')

  console.log('\n========== HAND 1: COMPLETE ALL STREETS ==========')
  const startId = 'walkthrough-start-hand-1'
  const startCommand = { commandId: startId }
  handMode = 'PASSIVE'
  host.publish(`/app/rooms/${roomCode}/start-hand`, startCommand)
  host.publish(`/app/rooms/${roomCode}/start-hand`, startCommand)

  await waitFor('first completed hand', () => eventCount('HAND_COMPLETED') >= 1)
  await waitFor('start replay receipt', () => observed.receipts.some(r => r.message.commandId === startId && r.message.replayed === true))
  await waitFor('action replay receipt', () => observed.receipts.some(r => r.message.commandId === firstActionCommandId && r.message.replayed === true))

  const firstHandEvents = observed.publicEvents.filter(item => item.actor === 'HOST').map(item => item.message)
  const streets = new Set(firstHandEvents.filter(e => e.type === 'STREET_DEALT').map(e => e.payload.street))
  const handOneStart = firstHandEvents.find(e => e.type === 'HAND_STARTED')
  const handOneDealer = handOneStart.payload.positions.dealerId

  console.log('\nExpected frontend interpretation after hand 1:')
  assert(streets.has('FLOP') && streets.has('TURN') && streets.has('RIVER'), 'passive CHECK/CALL decisions advance through flop, turn, and river')
  assert((observed.cards.get('ALICE') ?? []).length >= 1, 'Alice receives cards only on her private queue')
  assert((observed.cards.get('BOB') ?? []).length >= 1, 'Bob receives cards only on his private queue')
  assert(!JSON.stringify(observed.publicStates).includes('holeCards'), 'public state never leaks hole cards')
  assert(observed.receipts.some(r => r.message.commandId === startId && r.message.replayed === false), 'first start command executes normally')
  assert(observed.receipts.some(r => r.message.commandId === startId && r.message.replayed === true), 'identical start retry is acknowledged as replayed')
  assert(observed.receipts.some(r => r.message.commandId === firstActionCommandId && r.message.replayed === true), 'identical player-action retry is replayed without acting twice')
  assert(latestState()?.handInProgress === false, 'authoritative state marks the hand complete')

  console.log('\n========== REFRESH/RECONNECT BETWEEN HANDS ==========')
  await bob.disconnect()
  const recovered = await fetch(`${baseUrl}/api/v1/rooms/${roomCode}/me`, {
    headers: { Authorization: `Bearer ${bobToken}` },
  }).then(async response => ({ status: response.status, body: await response.json() }))
  print('BOB REST /me while socket is disconnected', recovered)
  assert(recovered.status === 200 && recovered.body.status === 'APPROVED', 'REST /me reconstructs Bob without an active socket')
  bob = new DemoActor('BOB', bobToken, 'PLAYER')
  await bob.connect()
  await sleep(150)
  assert(bob.connected, 'Bob reconnects using the same stored token')

  console.log('\n========== HAND 2: EARLY FOLD ==========')
  handMode = 'EARLY_FOLD'
  host.publish(`/app/rooms/${roomCode}/start-hand`, { commandId: 'walkthrough-start-hand-2' })
  await waitFor('second completed hand', () => eventCount('HAND_COMPLETED') >= 2)

  const starts = observed.publicEvents.filter(item => item.actor === 'HOST').map(item => item.message).filter(e => e.type === 'HAND_STARTED')
  const secondDealer = starts.at(-1).payload.positions.dealerId
  console.log('\nExpected frontend interpretation after hand 2:')
  assert(secondDealer !== handOneDealer, 'dealer rotates for the next hand')
  assert(latestState()?.handNumber === 2, 'hand number increments to 2')
  assert(latestState()?.handInProgress === false, 'a fold can complete a hand without dealing all community cards')

  console.log('\n========== STOMP SUMMARY ==========')
  print('Observed message counts', {
    publicEvents: observed.publicEvents.length,
    publicStates: observed.publicStates.length,
    commandReceipts: observed.receipts.length,
    privateErrors: observed.errors.length,
    aliceCardMessages: (observed.cards.get('ALICE') ?? []).length,
    bobCardMessages: (observed.cards.get('BOB') ?? []).length,
  })
  assert(observed.errors.length === 0, 'no unexpected private command errors occurred')
  console.log('\nSOCKET WALKTHROUGH PASSED')
} finally {
  await Promise.allSettled([host.disconnect(), alice.disconnect(), bob.disconnect()])
}
