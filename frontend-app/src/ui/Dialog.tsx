import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Button } from './Button'

const focusable = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'

export function Dialog({ open, title, children, closeLabel = 'Close', onClose }: {
  open: boolean
  title: string
  children: ReactNode
  closeLabel?: string
  onClose(): void
}) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const restoreFocusRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    if (!open) return
    restoreFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const panel = panelRef.current
    const first = panel?.querySelector<HTMLElement>(focusable)
    first?.focus()
    return () => restoreFocusRef.current?.focus()
  }, [open])

  if (!open) return null

  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }
    if (event.key !== 'Tab') return
    const controls = [...(panelRef.current?.querySelectorAll<HTMLElement>(focusable) ?? [])]
    if (controls.length === 0) return
    const first = controls[0]!
    const last = controls.at(-1)!
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="dialog-backdrop" role="presentation">
      <div
        ref={panelRef}
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={handleKeyDown}
      >
        <header className="dialog__header">
          <h2 id={titleId}>{title}</h2>
          <Button variant="quiet" onClick={onClose} aria-label={closeLabel}>×</Button>
        </header>
        <div className="dialog__body">{children}</div>
      </div>
    </div>
  )
}
