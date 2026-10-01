import { act, renderHook } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import type { RoomsApi } from '../api/roomsApi'
import { hostBootstrapFixture } from '../contracts/contractFixtures'
import { useTvRoom } from './useTvRoom'

let visibility: DocumentVisibilityState = 'visible'
const originalVisibility = Object.getOwnPropertyDescriptor(document, 'visibilityState')

beforeEach(() => {
  visibility = 'visible'
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility })
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  if (originalVisibility) Object.defineProperty(document, 'visibilityState', originalVisibility)
})

it('polls while visible, pauses while hidden, and refreshes immediately when visible again', async () => {
  const getState = vi.fn().mockResolvedValue(hostBootstrapFixture.room)
  const api = { getState } as unknown as RoomsApi
  const { result, unmount } = renderHook(() => useTvRoom('ABC123', api, 1_000))

  await act(async () => { await Promise.resolve() })
  expect(result.current.status).toBe('ready')
  expect(getState).toHaveBeenCalledTimes(1)

  visibility = 'hidden'
  document.dispatchEvent(new Event('visibilitychange'))
  await act(async () => { await vi.advanceTimersByTimeAsync(3_000) })
  expect(getState).toHaveBeenCalledTimes(1)

  visibility = 'visible'
  document.dispatchEvent(new Event('visibilitychange'))
  await act(async () => { await Promise.resolve() })
  expect(getState).toHaveBeenCalledTimes(2)
  unmount()
})

it('keeps the last confirmed table visible when a later poll fails', async () => {
  const getState = vi.fn()
    .mockResolvedValueOnce(hostBootstrapFixture.room)
    .mockRejectedValueOnce(new TypeError('network unavailable'))
  const api = { getState } as unknown as RoomsApi
  const { result, unmount } = renderHook(() => useTvRoom('ABC123', api, 1_000))

    await act(async () => {
      await Promise.resolve()
    })
    expect(result.current.status).toBe('ready')
  await act(async () => { await vi.advanceTimersByTimeAsync(1_000) })
  expect(result.current.status).toBe('offline')
  expect(result.current.room).toEqual(hostBootstrapFixture.room)
  unmount()
})
