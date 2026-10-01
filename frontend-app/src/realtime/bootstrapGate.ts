import type { RoomAction } from '../room/roomState'

type BufferedAction = Extract<RoomAction, {
  type: 'PUBLIC_STATE_RECEIVED' | 'PUBLIC_EVENT_RECEIVED' | 'CARDS_DEALT'
    | 'ACTION_REQUIRED' | 'MEMBERSHIP_STATUS' | 'LOBBY_RECEIVED'
}>

/** Buffers socket traffic while REST establishes the authoritative baseline. */
export class BootstrapGate {
  private buffering = false
  private buffer: BufferedAction[] = []

  begin(): void {
    this.buffering = true
    this.buffer = []
  }

  accept(action: BufferedAction): BufferedAction[] {
    if (!this.buffering) return [action]
    this.buffer.push(action)
    return []
  }

  complete(): BufferedAction[] {
    const latestState = [...this.buffer].reverse()
      .find(action => action.type === 'PUBLIC_STATE_RECEIVED')
    const replay: BufferedAction[] = this.buffer.filter(
      action => action.type !== 'PUBLIC_STATE_RECEIVED',
    )
    if (latestState) replay.push(latestState)
    this.buffer = []
    this.buffering = false
    return replay
  }

  reset(): void {
    this.buffer = []
    this.buffering = false
  }
}
