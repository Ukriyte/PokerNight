import { useId, type InputHTMLAttributes } from 'react'

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
  error?: string
}

export function TextField({ label, hint, error, id: givenId, ...props }: TextFieldProps) {
  const generatedId = useId()
  const id = givenId ?? generatedId
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>{label}</label>
      <input
        {...props}
        id={id}
        className={`field__input ${error ? 'field__input--error' : ''}`.trim()}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
      />
      {hint ? <span className="field__hint" id={hintId}>{hint}</span> : null}
      {error ? <span className="field__error" id={errorId} role="alert">{error}</span> : null}
    </div>
  )
}
