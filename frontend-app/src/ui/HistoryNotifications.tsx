import { useEffect, useRef, useState } from 'react'
import type { HistoryEntry } from '../contracts/api'

const DISPLAY_MS = 6_000
const MAX_VISIBLE = 3

export function HistoryNotifications({ entries }: { entries: HistoryEntry[] }) {
  const [visible, setVisible] = useState<HistoryEntry[]>([])
  const initialized = useRef(false)
  const highestSeen = useRef(0)
  const timers = useRef(new Map<number, number>())

  useEffect(() => () => {
    timers.current.forEach(window.clearTimeout)
    timers.current.clear()
  }, [])

  useEffect(() => {
    const highest = entries.reduce((value, entry) => Math.max(value, entry.sequence), 0)
    if (!initialized.current) {
      initialized.current = true
      highestSeen.current = highest
      return
    }
    if (entries.length === 0) {
      highestSeen.current = 0
      timers.current.forEach(window.clearTimeout)
      timers.current.clear()
      setVisible([])
      return
    }

    const fresh = entries
      .filter(entry => entry.sequence > highestSeen.current)
      .sort((left, right) => right.sequence - left.sequence)
    highestSeen.current = Math.max(highestSeen.current, highest)
    if (fresh.length === 0) return

    const freshSequences = new Set(fresh.map(entry => entry.sequence))
    setVisible(current => [...fresh, ...current.filter(entry => !freshSequences.has(entry.sequence))].slice(0, MAX_VISIBLE))
    fresh.forEach(entry => {
      const timer = window.setTimeout(() => {
        timers.current.delete(entry.sequence)
        setVisible(current => current.filter(candidate => candidate.sequence !== entry.sequence))
      }, DISPLAY_MS)
      timers.current.set(entry.sequence, timer)
    })
  }, [entries])

  if (visible.length === 0) return null
  return (
    <aside className="history-notifications" aria-label="Recent table activity" aria-live="polite">
      {visible.map(entry => <article className="history-notification" key={entry.sequence}>
        <div>
          <span>Hand {entry.handNumber} · recorded</span>
          <strong>{entry.message}</strong>
        </div>
        <button type="button" aria-label={`Dismiss ${entry.message}`} onClick={() => {
          const timer = timers.current.get(entry.sequence)
          if (timer !== undefined) window.clearTimeout(timer)
          timers.current.delete(entry.sequence)
          setVisible(current => current.filter(candidate => candidate.sequence !== entry.sequence))
        }}>×</button>
      </article>)}
    </aside>
  )
}
