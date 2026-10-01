import { z } from 'zod'
import type {
  ApprovePlayerRequest,
  CreateRoomRequest,
  CreateRoomResponse,
  HistoryPage,
  HostBootstrapResponse,
  JoinRoomRequest,
  JoinRoomResponse,
  LobbyResponse,
  MeResponse,
  NetworkInfoResponse,
  PublicRoomResponse,
} from '../contracts/api'
import {
  approvePlayerRequestSchema,
  createRoomRequestSchema,
  createRoomResponseSchema,
  historyPageSchema,
  hostBootstrapResponseSchema,
  joinRoomRequestSchema,
  joinRoomResponseSchema,
  lobbyResponseSchema,
  meResponseSchema,
  networkInfoResponseSchema,
  opaqueTokenSchema,
  publicRoomResponseSchema,
  roomCodeSchema,
} from '../contracts/schemas'
import { HttpClient } from './httpClient'

export interface RequestContext { signal?: AbortSignal }
export interface AuthenticatedRequestContext extends RequestContext { token: string }
export interface HistoryRequest extends AuthenticatedRequestContext {
  cursor?: number
  limit?: number
}

const playerIdSchema = z.string().min(1)
const historyRequestSchema = z.object({
  cursor: z.number().int().positive().optional(),
  limit: z.number().int().min(1).max(100).default(50),
}).strict()

export class RoomsApi {
  constructor(private readonly http: HttpClient = new HttpClient()) {}

  createRoom(request: CreateRoomRequest, context: RequestContext = {}): Promise<CreateRoomResponse> {
    return this.http.request({
      path: '/api/v1/rooms', method: 'POST', body: createRoomRequestSchema.parse(request),
      schema: createRoomResponseSchema, ...signalOption(context.signal),
    })
  }

  getNetworkInfo(context: RequestContext = {}): Promise<NetworkInfoResponse> {
    return this.http.request({
      path: '/api/v1/network-info', schema: networkInfoResponseSchema,
      ...signalOption(context.signal),
    })
  }

  getPublicRoom(code: string, context: RequestContext = {}): Promise<PublicRoomResponse> {
    return this.http.request({
      path: `${roomPath(code)}`, schema: publicRoomResponseSchema, ...signalOption(context.signal),
    })
  }

  getState(code: string, context: RequestContext = {}): Promise<PublicRoomResponse> {
    return this.http.request({
      path: `${roomPath(code)}/state`, schema: publicRoomResponseSchema, ...signalOption(context.signal),
    })
  }

  requestToJoin(
    code: string,
    request: JoinRoomRequest,
    context: RequestContext = {},
  ): Promise<JoinRoomResponse> {
    return this.http.request({
      path: `${roomPath(code)}/join-requests`, method: 'POST',
      body: joinRoomRequestSchema.parse(request), schema: joinRoomResponseSchema,
      ...signalOption(context.signal),
    })
  }

  getMe(code: string, context: AuthenticatedRequestContext): Promise<MeResponse> {
    return this.http.request({
      path: `${roomPath(code)}/me`, token: token(context.token), schema: meResponseSchema,
      ...signalOption(context.signal),
    })
  }

  getLobby(code: string, context: AuthenticatedRequestContext): Promise<LobbyResponse> {
    return this.http.request({
      path: `${roomPath(code)}/lobby`, token: token(context.token), schema: lobbyResponseSchema,
      ...signalOption(context.signal),
    })
  }

  getHostBootstrap(code: string, context: AuthenticatedRequestContext): Promise<HostBootstrapResponse> {
    return this.http.request({
      path: `${roomPath(code)}/host-bootstrap`, token: token(context.token),
      schema: hostBootstrapResponseSchema, ...signalOption(context.signal),
    })
  }

  approvePlayer(
    code: string,
    playerId: string,
    request: ApprovePlayerRequest,
    context: AuthenticatedRequestContext,
  ): Promise<LobbyResponse> {
    return this.http.request({
      path: `${memberPath(code, playerId)}/approval`, method: 'POST', token: token(context.token),
      body: approvePlayerRequestSchema.parse(request), schema: lobbyResponseSchema,
      ...signalOption(context.signal),
    })
  }

  rejectPlayer(
    code: string,
    playerId: string,
    context: AuthenticatedRequestContext,
  ): Promise<LobbyResponse> {
    return this.http.request({
      path: `${memberPath(code, playerId)}/rejection`, method: 'POST', token: token(context.token),
      schema: lobbyResponseSchema, ...signalOption(context.signal),
    })
  }

  kickPlayer(
    code: string,
    playerId: string,
    context: AuthenticatedRequestContext,
  ): Promise<LobbyResponse> {
    return this.http.request({
      path: memberPath(code, playerId), method: 'DELETE', token: token(context.token),
      schema: lobbyResponseSchema, ...signalOption(context.signal),
    })
  }

  getHistory(code: string, context: HistoryRequest): Promise<HistoryPage> {
    const query = historyRequestSchema.parse({ cursor: context.cursor, limit: context.limit })
    const search = new URLSearchParams({ limit: String(query.limit) })
    if (query.cursor !== undefined) search.set('cursor', String(query.cursor))
    return this.http.request({
      path: `${roomPath(code)}/history?${search.toString()}`, token: token(context.token),
      schema: historyPageSchema, ...signalOption(context.signal),
    })
  }

  closeRoom(code: string, context: AuthenticatedRequestContext): Promise<void> {
    return this.http.requestVoid({
      path: roomPath(code), method: 'DELETE', token: token(context.token),
      ...signalOption(context.signal),
    })
  }
}

function roomPath(code: string): string {
  return `/api/v1/rooms/${encodeURIComponent(roomCodeSchema.parse(code))}`
}

function memberPath(code: string, playerId: string): string {
  return `${roomPath(code)}/members/${encodeURIComponent(playerIdSchema.parse(playerId))}`
}

function token(value: string): string { return opaqueTokenSchema.parse(value) }

function signalOption(signal: AbortSignal | undefined): { signal?: AbortSignal } {
  return signal === undefined ? {} : { signal }
}

export const roomsApi = new RoomsApi()
