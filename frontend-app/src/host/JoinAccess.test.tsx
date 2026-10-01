import { render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { JoinAccess } from './JoinAccess'
import type { RoomsApi } from '../api/roomsApi'

vi.mock('qrcode', () => ({
  default: { toDataURL: vi.fn(async (value: string) => `data:image/png,${encodeURIComponent(value)}`) },
}))

it('replaces localhost with the backend detected LAN origin in the link and QR', async () => {
  const api = {
    getNetworkInfo: vi.fn().mockResolvedValue({
      origin: 'http://192.168.1.9:8080', address: '192.168.1.9', lanAvailable: true,
    }),
  } as unknown as RoomsApi
  render(<JoinAccess roomCode="ABC123" joinPath="/room/ABC123/play" api={api} />)

  const link = await screen.findByRole('link', { name: 'http://192.168.1.9:8080/room/ABC123/play' })
  expect(link).toHaveAttribute('href', 'http://192.168.1.9:8080/room/ABC123/play')
  await waitFor(() => expect(screen.getByRole('img', { name: /Scannable Wi-Fi QR/ })).toHaveAttribute(
    'src', expect.stringContaining(encodeURIComponent('http://192.168.1.9:8080/room/ABC123/play')),
  ))
})
