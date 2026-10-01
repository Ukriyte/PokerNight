import type { RoomCode } from '../contracts/api'

interface StoredRoomSessionBase {
  version: 1
  roomCode: RoomCode
  token: string
  savedAt: string
}

export interface StoredHostSessionV1 extends StoredRoomSessionBase {
  role: 'HOST'
}

export interface StoredPlayerSessionV1 extends StoredRoomSessionBase {
  role: 'PLAYER'
  subjectId: string
}

export type StoredRoomSessionV1 = StoredHostSessionV1 | StoredPlayerSessionV1
export type RoomSessionRole = StoredRoomSessionV1['role']
export type SaveRoomSession =
  | { roomCode: RoomCode; role: 'HOST'; token: string }
  | { roomCode: RoomCode; role: 'PLAYER'; subjectId: string; token: string }
