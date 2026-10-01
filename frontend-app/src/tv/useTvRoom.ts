import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiClientError } from '../api/httpClient'
import { roomsApi, type RoomsApi } from '../api/roomsApi'
import type { PublicRoomResponse } from '../contracts/api'

export type TvStatus = 'loading' | 'ready' | 'offline' | 'not-found'

export interface TvRoomState {
  status: TvStatus
  room: PublicRoomResponse | null
  lastUpdatedAt: Date | null
  refresh(): void
}

export function useTvRoom(roomCode: string, api: RoomsApi = roomsApi, pollInterval = 1_000): TvRoomState {
  const [room, setRoom] = useState<PublicRoomResponse | null>(null)
  const [status, setStatus] = useState<TvStatus>('loading')
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const refreshRef = useRef<() => void>(() => {})

  useEffect(() => {
    let live = true
    let timer: number | null = null
    let controller: AbortController | null = null
    let polling = false

    const clearTimer = () => {
      if (timer !== null) window.clearTimeout(timer)
      timer = null
    }
    const schedule = () => {
      clearTimer()
      if (live && document.visibilityState === 'visible') timer = window.setTimeout(() => void poll(), pollInterval)
    }
    const poll = async () => {
      if (!live || polling || document.visibilityState !== 'visible') return
      polling = true
      controller = new AbortController()
      try {
        const snapshot = await api.getState(roomCode, { signal: controller.signal })
        if (!live) return
        setRoom(snapshot)
        setStatus('ready')
        setLastUpdatedAt(new Date())
      } catch (cause) {
        if (!live || controller.signal.aborted) return
        if (cause instanceof ApiClientError && cause.status === 404) {
          setRoom(null)
          setStatus('not-found')
        } else {
          setStatus('offline')
        }
      } finally {
        polling = false
        controller = null
        schedule()
      }
    }
    const refresh = () => {
      clearTimer()
      void poll()
    }
    refreshRef.current = refresh
    const onVisibility = () => {
      if (document.visibilityState === 'visible') refresh()
      else clearTimer()
    }
    const onPageShow = () => refresh()
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('online', refresh)
    window.addEventListener('pageshow', onPageShow)
    void poll()

    return () => {
      live = false
      clearTimer()
      controller?.abort()
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('online', refresh)
      window.removeEventListener('pageshow', onPageShow)
      refreshRef.current = () => {}
    }
  }, [api, pollInterval, roomCode])

  const refresh = useCallback(() => refreshRef.current(), [])
  return { status, room, lastUpdatedAt, refresh }
}
