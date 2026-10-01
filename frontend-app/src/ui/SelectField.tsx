import { useId, type SelectHTMLAttributes } from 'react'

export interface SelectOption {
  value: string
  label: string
}

export function SelectField({ label, options, hint, ...props }: {
  label: string
  options: readonly SelectOption[]
  hint?: string
} & SelectHTMLAttributes<HTMLSelectElement>) {
  const generatedId = useId()
  const id = props.id ?? generatedId
  const hintId = hint ? `${id}-hint` : undefined
  return (
    <div className="field">
      <label className="field__label" htmlFor={id}>{label}</label>
      <select {...props} id={id} className={`field__input field__select ${props.className ?? ''}`.trim()} aria-describedby={hintId}>
        {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
      {hint ? <span className="field__hint" id={hintId}>{hint}</span> : null}
    </div>
  )
}
