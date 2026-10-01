import { render, screen } from '@testing-library/react'
import { HistoryPanel } from './HistoryPanel'

it('always renders history newest first', () => {
  render(<HistoryPanel entries={[
    { sequence: 1, handNumber: 1, eventType: 'HAND_STARTED', message: 'Hand started', occurredAt: '2026-08-14T00:00:00Z' },
    { sequence: 3, handNumber: 1, eventType: 'PLAYER_ACTED', message: 'Ravi called', occurredAt: '2026-08-14T00:00:02Z' },
    { sequence: 2, handNumber: 1, eventType: 'PLAYER_ACTED', message: 'Mina checked', occurredAt: '2026-08-14T00:00:01Z' },
  ]} hasOlder={false} loading={false} onLoadOlder={async () => {}} />)

  expect(screen.getAllByRole('listitem').map(item => item.textContent)).toEqual([
    expect.stringContaining('#3'), expect.stringContaining('#2'), expect.stringContaining('#1'),
  ])
})
