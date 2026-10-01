import { act, render, screen } from '@testing-library/react'
import { HistoryNotifications } from './HistoryNotifications'

const entry = (sequence: number, message: string) => ({
  sequence, handNumber: 1, eventType: 'PLAYER_ACTED', message,
  occurredAt: '2026-08-17T00:00:00Z',
})

it('notifies only for history added after the component is mounted', () => {
  const { rerender } = render(<HistoryNotifications entries={[entry(1, 'Old move')]} />)
  expect(screen.queryByText('Old move')).not.toBeInTheDocument()

  rerender(<HistoryNotifications entries={[entry(2, 'Alice called'), entry(1, 'Old move')]} />)
  expect(screen.getByText('Alice called')).toBeInTheDocument()
  expect(screen.queryByText('Old move')).not.toBeInTheDocument()
})

it('dismisses a notification automatically', () => {
  vi.useFakeTimers()
  const { rerender } = render(<HistoryNotifications entries={[]} />)
  rerender(<HistoryNotifications entries={[entry(1, 'Bob checked')]} />)
  expect(screen.getByText('Bob checked')).toBeInTheDocument()
  act(() => vi.advanceTimersByTime(6_000))
  expect(screen.queryByText('Bob checked')).not.toBeInTheDocument()
  vi.useRealTimers()
})
