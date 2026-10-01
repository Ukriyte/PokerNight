import type { ButtonHTMLAttributes, ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'quiet'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  busy?: boolean
  children: ReactNode
}

export function Button({ variant = 'primary', busy = false, disabled, children, ...props }: ButtonProps) {
  return (
    <button
      {...props}
      className={`button button--${variant} ${props.className ?? ''}`.trim()}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
    >
      {busy ? <span className="button__spinner" aria-hidden="true" /> : null}
      <span>{children}</span>
    </button>
  )
}
