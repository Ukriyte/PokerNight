import { useMemo, useState } from 'react'
import type { LobbyResponse, PublicPlayerState } from '../contracts/api'
import { Button } from '../ui'

export function LobbyPanel({ lobby, players, maximumPlayers, busyAction, canRemovePlayers, onApprove, onReject, onKick }: {
  lobby: LobbyResponse
  players: PublicPlayerState[]
  maximumPlayers: number
  busyAction: string | null
  canRemovePlayers: boolean
  onApprove(playerId: string, seatNumber: number): Promise<void>
  onReject(playerId: string): Promise<void>
  onKick(playerId: string): Promise<void>
}) {
  const occupied = useMemo(() => new Set(players.map(player => player.seatNumber)), [players])
  const openSeats = Array.from({ length: maximumPlayers }, (_, index) => index).filter(index => !occupied.has(index))
  const [selections, setSelections] = useState<Record<string, number>>({})
  const pending = lobby.members.filter(member => member.status === 'PENDING')
  const seated = lobby.members.filter(member => member.status === 'APPROVED')

  return (
    <section className="host-panel lobby-panel" aria-labelledby="lobby-title">
      <header className="panel-heading">
        <div><p className="eyebrow">Host desk</p><h2 id="lobby-title">Players</h2></div>
        <span>{seated.length}/{maximumPlayers} human seats</span>
      </header>
      <h3>Waiting for approval <span className="count-badge">{pending.length}</span></h3>
      {pending.length === 0 ? <p className="empty-copy">New join requests appear here automatically.</p> : (
        <ul className="member-list">
          {pending.map(member => {
            const seat = selections[member.playerId] ?? openSeats[0]
            return (
              <li key={member.playerId}>
                <strong>{member.displayName}</strong>
                <label>Seat <select value={seat ?? ''} onChange={event => setSelections(current => ({ ...current, [member.playerId]: Number(event.target.value) }))}>
                  {openSeats.map(number => <option key={number} value={number}>{number + 1}</option>)}
                </select></label>
                <div className="member-actions">
                  <Button type="button" disabled={seat === undefined} busy={busyAction === `approve:${member.playerId}`} onClick={() => seat !== undefined && void onApprove(member.playerId, seat)}>Approve</Button>
                  <Button type="button" variant="quiet" busy={busyAction === `reject:${member.playerId}`} onClick={() => void onReject(member.playerId)}>Reject</Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
      <h3>At the table</h3>
      {!canRemovePlayers && seated.length > 0 ? <p className="empty-copy">Players can be removed after the current hand finishes.</p> : null}
      {seated.length === 0 ? <p className="empty-copy">No human players are seated yet.</p> : (
        <ul className="member-list member-list--seated">
          {seated.map(member => <li key={member.playerId}>
            <div><strong>{member.displayName}</strong><span>Seat {(member.seatNumber ?? 0) + 1}</span></div>
            <Button type="button" variant="danger" disabled={!canRemovePlayers} busy={busyAction === `kick:${member.playerId}`} onClick={() => void onKick(member.playerId)}>{canRemovePlayers ? 'Remove' : 'Remove after hand'}</Button>
          </li>)}
        </ul>
      )}
    </section>
  )
}
