import { useEffect, useMemo, useState } from 'react'
import type { ActionRequiredEvent, PlayerAction } from '../contracts/api'
import { Button } from '../ui'

type Turn = ActionRequiredEvent['payload']

export function PlayerActionPanel({ turn, disabled, onAction }: {
  turn: Turn
  disabled: boolean
  onAction(action: PlayerAction, amount?: number): void
}) {
  const minimumTarget = turn.currentBet + turn.minimumRaise
  const maximumTarget = turn.playerContribution + turn.playerChips
  const [raiseTarget, setRaiseTarget] = useState(Math.min(minimumTarget, maximumTarget))
  const callAmount = Math.max(0, turn.currentBet - turn.playerContribution)
  const actions = useMemo(() => new Set(turn.validActions), [turn.validActions])

  useEffect(() => setRaiseTarget(Math.min(minimumTarget, maximumTarget)), [maximumTarget, minimumTarget])

  return (
    <section className="action-dock" aria-labelledby="action-title">
      <header><div><p className="eyebrow">Your turn</p><h2 id="action-title">Choose one action</h2></div><span>Pot {turn.pot.toLocaleString()}</span></header>
      {actions.has('RAISE') ? <label className="raise-control">
        <span>Raise total to <strong>{raiseTarget.toLocaleString()}</strong></span>
        <input aria-label="Raise target" type="range" min={minimumTarget} max={maximumTarget} step={Math.max(1, turn.minimumRaise)} value={raiseTarget} disabled={disabled || maximumTarget < minimumTarget} onChange={event => setRaiseTarget(Number(event.target.value))} />
        <span className="raise-control__range"><span>{minimumTarget.toLocaleString()}</span><span>{maximumTarget.toLocaleString()}</span></span>
      </label> : null}
      <div className="action-dock__buttons">
        <div className="action-dock__main">
          {actions.has('CHECK') ? <Button disabled={disabled} onClick={() => onAction('CHECK')}>Check</Button> : null}
          {actions.has('CALL') ? <Button disabled={disabled} onClick={() => onAction('CALL')}>Call {callAmount.toLocaleString()}</Button> : null}
          {actions.has('RAISE') ? <Button disabled={disabled || maximumTarget < minimumTarget} onClick={() => onAction('RAISE', raiseTarget)}>Raise to {raiseTarget.toLocaleString()}</Button> : null}
          {actions.has('ALL_IN') ? <Button variant="secondary" disabled={disabled} onClick={() => onAction('ALL_IN')}>All in</Button> : null}
        </div>
        {actions.has('FOLD') ? <Button variant="danger" disabled={disabled} onClick={() => onAction('FOLD')}>Fold</Button> : null}
      </div>
      {disabled ? <p className="action-dock__pending" role="status">Waiting for the server to confirm your action…</p> : null}
    </section>
  )
}
