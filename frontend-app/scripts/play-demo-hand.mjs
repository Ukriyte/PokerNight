import { Client } from '@stomp/stompjs'

const baseUrl = required('POKERNIGHT_DEMO_BASE_URL')
const roomCode = required('POKERNIGHT_DEMO_ROOM_CODE')
const hostToken = required('POKERNIGHT_DEMO_HOST_TOKEN')
const playerToken = required('POKERNIGHT_DEMO_PLAYER_TOKEN')

const socketUrl = new URL('/ws', baseUrl)
socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:'

let handStarted = false
let actionSent = false
let finished = false
let resolveFinished
let rejectFinished

const handFinished = new Promise((resolve, reject) => {
  resolveFinished = resolve
  rejectFinished = reject
})

function required(name) {
  const value = process.env[name]
  if (!value) throw new Error(`Missing environment variable: ${name}`)
  return value
}

function parse(frame) {
  try {
    return JSON.parse(frame.body)
  } catch {
    return frame.body
  }
}

function print(label, value) {
  console.log(`\n--- ${label} ---`)
  console.log(typeof value === 'string' ? value : JSON.stringify(value, null, 2))
}

function clientFor(label, token, onConnect) {
  const client = new Client({
    brokerURL: socketUrl.toString(),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 0,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    debug: process.env.POKERNIGHT_STOMP_DEBUG === '1'
      ? message => console.log(`[${label} STOMP] ${message}`)
      : () => {},
    onConnect: () => onConnect(client),
    onStompError: frame => rejectFinished(new Error(`${label} broker error: ${frame.headers.message}\n${frame.body}`)),
    onWebSocketError: event => rejectFinished(new Error(`${label} WebSocket error: ${event?.message ?? 'unknown error'}`)),
  })
  return client
}

function subscribeCommon(client, label) {
  client.subscribe('/user/queue/commands', frame => print(`${label} command receipt`, parse(frame)))
  client.subscribe('/user/queue/errors', frame => {
    const error = parse(frame)
    print(`${label} private error`, error)
    rejectFinished(new Error(`${label} command failed: ${JSON.stringify(error)}`))
  })
  client.subscribe(`/topic/rooms/${roomCode}/state`, frame => {
    const state = parse(frame)
    print(`${label} public state`, state)
    if (handStarted && state.handInProgress === false && Number(state.handNumber) >= 1) finish()
  })
  client.subscribe(`/topic/rooms/${roomCode}/events`, frame => {
    const event = parse(frame)
    print(`${label} public event`, event)
    if (event?.type === 'HAND_COMPLETED' || event?.type === 'GAME_COMPLETED') finish()
  })
}

let hostReady = false
let playerReady = false

const host = clientFor('HOST', hostToken, client => {
  subscribeCommon(client, 'HOST')
  hostReady = true
  startWhenReady()
})

const player = clientFor('ALICE', playerToken, client => {
  subscribeCommon(client, 'ALICE')
  client.subscribe('/user/queue/cards', frame => print('ALICE private cards (not broadcast to host)', parse(frame)))
  client.subscribe('/user/queue/turn', frame => {
    const turn = parse(frame)
    print('ALICE private turn prompt', turn)
    if (actionSent) return
    actionSent = true
    const validActions = turn?.payload?.validActions ?? []
    const action = validActions.includes('FOLD') ? 'FOLD' : validActions[0]
    if (!action) {
      rejectFinished(new Error('Turn prompt did not contain a legal action'))
      return
    }
    const command = { commandId: crypto.randomUUID(), action }
    print('ALICE sends move', command)
    client.publish({
      destination: `/app/rooms/${roomCode}/actions`,
      body: JSON.stringify(command),
    })
  })
  playerReady = true
  startWhenReady()
})

function startWhenReady() {
  if (!hostReady || !playerReady || handStarted) return
  handStarted = true
  const command = { commandId: crypto.randomUUID() }
  print('HOST sends start-hand', command)
  host.publish({
    destination: `/app/rooms/${roomCode}/start-hand`,
    body: JSON.stringify(command),
  })
}

function finish() {
  if (finished) return
  finished = true
  resolveFinished()
}

const timeout = setTimeout(() => {
  rejectFinished(new Error('Timed out waiting for the demo hand to finish'))
}, 15_000)

host.activate()
player.activate()

try {
  await handFinished
  print('RESULT', 'The hand completed successfully.')
} finally {
  clearTimeout(timeout)
  await Promise.allSettled([host.deactivate(), player.deactivate()])
}
