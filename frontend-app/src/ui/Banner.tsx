import type { ReactNode } from 'react'

export type BannerTone = 'info' | 'success' | 'warning' | 'error'

export function Banner({ tone = 'info', title, children }: {
  tone?: BannerTone
  title: string
  children?: ReactNode
}) {
  return (
    <section className={`banner banner--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <strong>{title}</strong>
      {children ? <div className="banner__body">{children}</div> : null}
    </section>
  )
}

export function LiveRegion({ message }: { message: string }) {
  return <div className="sr-only" aria-live="polite" aria-atomic="true">{message}</div>
}
