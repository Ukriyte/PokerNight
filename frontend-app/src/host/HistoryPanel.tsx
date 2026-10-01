import type { HistoryEntry } from '../contracts/api'
import { Button } from '../ui'

export function HistoryPanel({ entries, hasOlder, loading, onLoadOlder }: {
  entries: HistoryEntry[]
  hasOlder: boolean
  loading: boolean
  onLoadOlder(): Promise<void>
}) {
  const newestFirst = [...entries].sort((a, b) => b.sequence - a.sequence)
  return (
    <section className="host-panel history-panel" aria-labelledby="history-title">
      <header className="panel-heading"><div><p className="eyebrow">Table record</p><h2 id="history-title">History</h2></div><span>{entries.length} events</span></header>
      {newestFirst.length === 0 ? <p className="empty-copy">Actions and completed hands will be recorded here.</p> : (
        <ol className="history-list">
          {newestFirst.map(entry => <li key={entry.sequence}>
            <span className="history-list__sequence">#{entry.sequence}</span>
            <div><strong>{entry.message}</strong><span>Hand {entry.handNumber} · {new Date(entry.occurredAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span></div>
          </li>)}
        </ol>
      )}
      {hasOlder ? <Button type="button" variant="secondary" busy={loading} onClick={() => void onLoadOlder()}>Load older events</Button> : null}
    </section>
  )
}
